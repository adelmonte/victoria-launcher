// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import android.widget.TextView
import java.util.Collections
import java.util.WeakHashMap

class VictoriaAppWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {

    /**
     * The launcher's font, for our own clock (#123).
     *
     * A RemoteViews has no way to be handed a typeface, let alone a file the user picked. But
     * this host is where the clock is inflated, so its text views are ours to set once each
     * update has been applied. Other apps' widgets are left as their makers drew them.
     */
    var typeface: Typeface = Typeface.DEFAULT
        set(value) {
            if (field == value) return
            field = value
            ownViews.forEach { it.applyTypeface() }
        }

    private val ownViews: MutableSet<FontedHostView> = Collections.newSetFromMap(WeakHashMap())

    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView {
        if (appWidget?.provider?.packageName != context.packageName) return super.onCreateView(context, appWidgetId, appWidget)
        return FontedHostView(context).also { ownViews += it }
    }

    private inner class FontedHostView(context: Context) : AppWidgetHostView(context) {
        override fun updateAppWidget(remoteViews: RemoteViews?) {
            super.updateAppWidget(remoteViews)
            applyTypeface()
        }

        fun applyTypeface() = setTypefaceIn(this)

        private fun setTypefaceIn(view: View) {
            when (view) {
                is TextView -> view.typeface = typeface
                is ViewGroup -> for (i in 0 until view.childCount) setTypefaceIn(view.getChildAt(i))
            }
        }
    }
}
