// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Build
import androidx.annotation.RequiresApi
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsAnimationControlListener
import android.view.WindowInsetsAnimationController
import android.view.animation.LinearInterpolator
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Shows and hides the status bar with a cross-fade instead of the system's slide.
 *
 * The plain `show()`/`hide()` calls always slide. Driving the inset animation ourselves lets
 * us hold the bar at its shown position and animate only its alpha, which reads as a fade.
 * That API is Android 11+, so older versions (and any failure) fall back to the ordinary
 * sliding show/hide.
 *
 * Requesting a second animation cancels the first, and a cancelled controller throws from
 * `setInsetsAndAlpha` — so the in-flight animator has to be torn down with it, and every
 * write to the controller is guarded.
 */
object StatusBarFader {

    private var animator: ValueAnimator? = null
    private var controller: WindowInsetsAnimationController? = null

    fun setVisible(
        window: Window,
        visible: Boolean,
        durationMs: Long = if (visible) 220 else 650,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val faded = runCatching { fade(window, visible, durationMs) }.getOrDefault(false)
            if (faded) return
        }
        fallback(window, visible)
    }

    /** Drops the retained controller and animator. Called when the Activity stops. */
    fun release() = stopInFlight()

    private fun stopInFlight() {
        animator?.let { inFlight ->
            inFlight.removeAllUpdateListeners()
            inFlight.removeAllListeners()
            inFlight.cancel()
        }
        animator = null
        controller = null
    }

    private fun fallback(window: Window, visible: Boolean) {
        stopInFlight()
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        if (visible) {
            insetsController.show(WindowInsetsCompat.Type.statusBars())
        } else {
            insetsController.hide(WindowInsetsCompat.Type.statusBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun fade(window: Window, visible: Boolean, durationMs: Long): Boolean {
        val insetsController = window.insetsController ?: return false
        stopInFlight()

        insetsController.controlWindowInsetsAnimation(
            WindowInsets.Type.statusBars(),
            durationMs,
            LinearInterpolator(),
            null,
            object : WindowInsetsAnimationControlListener {
                override fun onReady(control: WindowInsetsAnimationController, types: Int) {
                    controller = control
                    val from = if (visible) 0f else 1f
                    val to = if (visible) 1f else 0f

                    animator = ValueAnimator.ofFloat(from, to).apply {
                        duration = durationMs
                        addUpdateListener { anim ->
                            // The controller can be cancelled out from under us at any point;
                            // writing to it after that throws.
                            val live = controller
                            if (live == null) {
                                anim.cancel()
                                return@addUpdateListener
                            }
                            val ok = runCatching {
                                live.setInsetsAndAlpha(
                                    live.shownStateInsets,
                                    anim.animatedValue as Float,
                                    anim.animatedFraction,
                                )
                            }.isSuccess
                            if (!ok) {
                                controller = null
                                anim.cancel()
                            }
                        }
                        addListener(
                            object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    runCatching { controller?.finish(visible) }
                                    controller = null
                                    animator = null
                                }
                            }
                        )
                        start()
                    }
                }

                override fun onFinished(control: WindowInsetsAnimationController) {
                    if (controller === control) controller = null
                }

                override fun onCancelled(control: WindowInsetsAnimationController?) {
                    // Kill the animator with it, or its next tick writes to a dead controller.
                    if (controller === control || control == null) {
                        controller = null
                        animator?.cancel()
                        animator = null
                    }
                }
            },
        )
        return true
    }
}