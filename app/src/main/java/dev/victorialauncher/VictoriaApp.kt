// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.app.Application
import dev.victorialauncher.data.AppRepository
import dev.victorialauncher.data.IconPackRepository
import dev.victorialauncher.data.CrashLog
import dev.victorialauncher.data.Prefs
import dev.victorialauncher.widget.VictoriaAppWidgetHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class VictoriaApp : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var appRepository: AppRepository
        private set
    lateinit var iconPackRepository: IconPackRepository
        private set
    lateinit var widgetHost: VictoriaAppWidgetHost
        private set

    /** Outlives any screen, for work that must finish even as the launcher is left behind. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // First, so a crash on the way up is still recorded.
        CrashLog.install(this)
        prefs = Prefs(this)
        appRepository = AppRepository(this, prefs, appScope)
        iconPackRepository = IconPackRepository(this)
        widgetHost = VictoriaAppWidgetHost(this, HOST_ID)
    }

    companion object {
        const val HOST_ID = 1024
    }
}