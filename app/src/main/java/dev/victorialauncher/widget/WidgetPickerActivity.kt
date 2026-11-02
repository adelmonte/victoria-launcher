// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import android.app.ActivityOptions
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
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

private const val REQUEST_CONFIGURE = 1
private const val STATE_PENDING_ID = "pending_widget_id"

class WidgetPickerActivity : ComponentActivity() {

    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var widgetHost: VictoriaAppWidgetHost
    private var pendingWidgetId: Int = -1

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
        // The setup screen can outlast this one; coming back to a fresh instance without the
        // id, a widget set up in full was added as nothing.
        pendingWidgetId = savedInstanceState?.getInt(STATE_PENDING_ID, -1) ?: -1

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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_PENDING_ID, pendingWidgetId)
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
        if (info?.configure == null || configurationIsOptional(info)) {
            finishWithWidgetId(id)
            return
        }
        // Through the host rather than as a plain Intent of our own. A widget's configure
        // screen usually is not exported — it expects to be started by whoever holds the
        // widget id — so starting it directly is refused, and that refusal is a crash rather
        // than a missed widget. This is the call that carries the host's standing to open it.
        val started = runCatching {
            widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQUEST_CONFIGURE, configureOptions())
            true
        }.getOrDefault(false)
        // A widget whose configure screen will not open is still a widget; it gets added with
        // whatever it defaults to rather than being dropped on the floor.
        if (!started) finishWithWidgetId(id)
    }

    /**
     * Lets the setup screen open on the strength of this screen being in front.
     *
     * The host starts it through a sender the system made, and from Android 14 being in front
     * is only lent to such a sender when asked. Without asking, the start is quietly blocked:
     * nothing opens, the result comes back canceled, and the widget is thrown away.
     */
    private fun configureOptions(): Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            .toBundle()
    }

    /** Android 12 let a widget say its configure screen is optional; those can skip it. */
    private fun configurationIsOptional(info: AppWidgetProviderInfo): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0

    @Deprecated("Required by startAppWidgetConfigureActivityForResult, which is requestCode-based")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CONFIGURE) return
        if (resultCode == RESULT_OK) finishWithWidgetId(pendingWidgetId) else cancel()
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