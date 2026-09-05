// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

enum class PaddingSlot {
    NOW_PLAYING_TOP,
    NOW_PLAYING_BOTTOM,
    WIDGET_TOP,
    WIDGET_BOTTOM,
    FAVORITES_TOP,
    FAVORITES_BOTTOM,
}

/** Per-block top/bottom spacing on the home screen, in dp. */
data class HomePaddings(
    val nowPlayingTop: Int,
    val nowPlayingBottom: Int,
    val widgetTop: Int,
    val widgetBottom: Int,
    val favoritesTop: Int,
    val favoritesBottom: Int,
) {
    operator fun get(slot: PaddingSlot): Int = when (slot) {
        PaddingSlot.NOW_PLAYING_TOP -> nowPlayingTop
        PaddingSlot.NOW_PLAYING_BOTTOM -> nowPlayingBottom
        PaddingSlot.WIDGET_TOP -> widgetTop
        PaddingSlot.WIDGET_BOTTOM -> widgetBottom
        PaddingSlot.FAVORITES_TOP -> favoritesTop
        PaddingSlot.FAVORITES_BOTTOM -> favoritesBottom
    }

    companion object {
        val Default = HomePaddings(8, 8, 8, 8, 8, 24)
    }
}