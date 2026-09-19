// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import android.content.Context
import android.os.Build
import dev.victorialauncher.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the stack trace of the last crash so it can be read and sent with a bug report.
 *
 * A launcher hosts other apps' widgets, which draw with their own code in this process — so a
 * crash here is often not from anything in this repository, and the trace is the only thing
 * that says whose it was. Written to a file rather than held in memory, because the process
 * that would remember it is the one going down.
 *
 * Nothing is sent anywhere. The file is read when someone opens settings and asks for it.
 */
object CrashLog {

    private const val FILE_NAME = "last_crash.txt"

    /**
     * Records the trace, then lets the process die the way it was going to.
     *
     * Chained rather than replacing what was there: the handler ahead of this one is what
     * actually ends the process and tells the system it crashed. Swallowing that would leave a
     * launcher frozen on screen instead of restarting.
     */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(appContext, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val when_ = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        file(context).writeText(
            buildString {
                appendLine("Victoria Launcher ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine(when_)
                appendLine("thread: ${thread.name}")
                appendLine()
                append(stack)
            }
        )
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** The last crash, or null if there has not been one since it was last cleared. */
    fun read(context: Context): String? =
        runCatching { file(context).takeIf { it.isFile }?.readText() }.getOrNull()
            ?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
