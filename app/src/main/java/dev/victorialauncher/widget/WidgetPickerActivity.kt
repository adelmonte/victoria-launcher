// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.victorialauncher.VictoriaApp
import dev.victorialauncher.ui.theme.VictoriaTheme
import dev.victorialauncher.R
import androidx.compose.ui.res.stringResource

class WidgetPickerActivity : ComponentActivity() {

    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var widgetHost: VictoriaAppWidgetHost
    private var pendingWidgetId: Int = -1

    private val configureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) finishWithWidgetId(pendingWidgetId) else cancel()
        }

    private val bindLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) proceedAfterBind(pendingWidgetId) else cancel()
        }

    private data class AppGroup(
        val label: String,
        val icon: Drawable?,
        val widgets: List<AppWidgetProviderInfo>,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetManager = AppWidgetManager.getInstance(this)
        widgetHost = (application as VictoriaApp).widgetHost

        setContent {
            VictoriaTheme {
                val providers = remember { appWidgetManager.installedProviders }
                val groups = remember(providers) {
                    providers.groupBy { it.provider.packageName }
                        .map { (pkg, widgets) ->
                            val appInfo = runCatching { packageManager.getApplicationInfo(pkg, 0) }.getOrNull()
                            AppGroup(
                                label = appInfo?.loadLabel(packageManager)?.toString() ?: pkg,
                                icon = appInfo?.loadIcon(packageManager),
                                widgets = widgets.sortedBy { it.loadLabel(packageManager) },
                            )
                        }
                        .sortedBy { it.label.lowercase() }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.surface,
                        topBar = {
                            TopAppBar(
                                title = { Text(stringResource(R.string.widget_add)) },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                            )
                        }
                    ) { padding ->
                        if (groups.isEmpty()) {
                            Text(
                                stringResource(R.string.widget_none_found),
                                modifier = Modifier.padding(padding).padding(20.dp),
                            )
                        } else {
                            LazyColumn(modifier = Modifier.padding(padding)) {
                                groups.forEach { group ->
                                    item {
                                        ListItem(
                                            headlineContent = { Text(group.label, style = MaterialTheme.typography.titleSmall) },
                                            leadingContent = { DrawableIcon(group.icon, 32.dp) },
                                        )
                                    }
                                    items(group.widgets) { info -> WidgetRow(info) { pick(info) } }
                                    item { HorizontalDivider() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun WidgetRow(info: AppWidgetProviderInfo, onClick: () -> Unit) {
        val icon = remember(info) {
            runCatching { info.loadIcon(this, resources.configuration.densityDpi) }.getOrNull()
        }
        ListItem(
            headlineContent = { Text(info.loadLabel(packageManager)) },
            leadingContent = { DrawableIcon(icon, 40.dp) },
            modifier = Modifier.clickable(onClick = onClick),
        )
    }

    private fun pick(info: AppWidgetProviderInfo) {
        val id = widgetHost.allocateAppWidgetId()
        pendingWidgetId = id
        val bound = appWidgetManager.bindAppWidgetIdIfAllowed(id, info.provider)
        if (bound) {
            proceedAfterBind(id)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            }
            bindLauncher.launch(intent)
        }
    }

    private fun proceedAfterBind(id: Int) {
        val info = appWidgetManager.getAppWidgetInfo(id)
        val configure = info?.configure
        if (configure != null) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            }
            try {
                configureLauncher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                finishWithWidgetId(id)
            }
        } else {
            finishWithWidgetId(id)
        }
    }

    private fun finishWithWidgetId(id: Int) {
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        finish()
    }

    private fun cancel() {
        if (pendingWidgetId != -1) widgetHost.deleteAppWidgetId(pendingWidgetId)
        setResult(RESULT_CANCELED)
        finish()
    }
}

@Composable
private fun DrawableIcon(drawable: Drawable?, size: androidx.compose.ui.unit.Dp) {
    AndroidView(
        modifier = Modifier.size(size),
        factory = { ctx -> ImageView(ctx) },
        update = { it.setImageDrawable(drawable) },
    )
}