// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher

import android.app.Application
import dev.victorialauncher.data.AppRepository
import dev.victorialauncher.data.IconPackRepository
import dev.victorialauncher.data.Prefs
import dev.victorialauncher.widget.VictoriaAppWidgetHost

class VictoriaApp : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var appRepository: AppRepository
        private set
    lateinit var iconPackRepository: IconPackRepository
        private set
    lateinit var widgetHost: VictoriaAppWidgetHost
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        appRepository = AppRepository(this)
        iconPackRepository = IconPackRepository(this)
        widgetHost = VictoriaAppWidgetHost(this, HOST_ID)
    }

    companion object {
        const val HOST_ID = 1024
    }
}