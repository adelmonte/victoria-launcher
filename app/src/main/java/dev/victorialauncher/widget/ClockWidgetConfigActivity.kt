// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import dev.victorialauncher.R
import androidx.compose.foundation.shape.CircleShape
import dev.victorialauncher.ui.settings.ColorPickerDialog
import dev.victorialauncher.ui.settings.FilledChip
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.layout.Row as LayoutRow
import dev.victorialauncher.VictoriaApp
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.EntryKind
import dev.victorialauncher.ui.settings.SliderRow
import dev.victorialauncher.ui.theme.VictoriaTheme
import java.util.Date

/**
 * The clock widget's own settings, reached from the widget's menu.
 *
 * Declared as the provider's configure activity, so the system offers it both when the widget
 * is first added and whenever its settings are asked for again. It is cancelled by default:
 * a configure activity that finishes without RESULT_OK on a first add tells the system not to
 * keep the widget, which is what should happen if someone backs out of adding one.
 */
class ClockWidgetConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(Activity.RESULT_CANCELED, resultIntent())

        setContent {
            VictoriaTheme {
                var config by remember { mutableStateOf(ClockWidgetConfig.read(this, widgetId)) }
                var pickingWeatherApp by remember { mutableStateOf(false) }
                // Asked for here rather than at install, and re-read on the way back so the
                // section stops offering what has just been granted.
                var calendarGranted by remember {
                    mutableStateOf(ClockWidgetAgenda.hasCalendarPermission(this))
                }
                val askCalendar = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    calendarGranted = granted
                    // Refused, so nothing would ever appear; better to leave the row off than
                    // to show a widget that silently has nothing in it.
                    if (!granted) config = config.copy(showAgenda = false)
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.surface,
                        topBar = {
                            TopAppBar(
                                title = { Text(stringResource(R.string.widget_clock_label)) },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                            )
                        },
                    ) { padding ->
                        Column(
                            modifier = Modifier
                                .padding(padding)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            HourSection(config) { config = config.copy(hourFormat = it) }
                            DateSection(config) { config = config.copy(datePattern = it) }
                            BatterySection(config) { config = config.copy(showBattery = it) }
                            WeatherSection(
                                config,
                                onShow = { config = config.copy(showWeather = it) },
                                onFahrenheit = { config = config.copy(weatherFahrenheit = it) },
                                onPickApp = { pickingWeatherApp = true },
                            )
                            NotificationsSection(config) {
                                config = config.copy(showNotifications = it)
                            }
                            AgendaSection(
                                config = config,
                                hasPermission = calendarGranted,
                                onShow = { config = config.copy(showAgenda = it) },
                                onRelative = { config = config.copy(agendaRelative = it) },
                                onCount = { config = config.copy(agendaCount = it) },
                                onHours = { config = config.copy(agendaLookaheadHours = it) },
                                onAlarm = { config = config.copy(showAlarm = it) },
                                onAskPermission = {
                                    askCalendar.launch(Manifest.permission.READ_CALENDAR)
                                },
                            )
                            ColorSection(config) { config = config.copy(textColor = it) }

                            TextSection(
                                sizeLabel = stringResource(R.string.widget_clock_size),
                                opacityLabel = stringResource(R.string.widget_clock_time_opacity),
                                preview = stringResource(R.string.widget_clock_size_preview),
                                size = config.timeSizeSp,
                                sizeRange = ClockWidgetConfig.SIZE_RANGE,
                                opacity = config.timeOpacity,
                                color = config.textColor,
                                onSize = { config = config.copy(timeSizeSp = it) },
                                onOpacity = { config = config.copy(timeOpacity = it) },
                            )
                            config.datePattern?.let { pattern ->
                                TextSection(
                                    sizeLabel = stringResource(R.string.widget_clock_date_size),
                                    opacityLabel = stringResource(R.string.widget_clock_date_opacity),
                                    preview = runCatching {
                                        DateFormat.format(pattern, Date()).toString()
                                    }.getOrDefault(pattern),
                                    size = config.dateSizeSp,
                                    sizeRange = ClockWidgetConfig.DATE_SIZE_RANGE,
                                    opacity = config.dateOpacity,
                                    color = config.textColor,
                                    onSize = { config = config.copy(dateSizeSp = it) },
                                    onOpacity = { config = config.copy(dateOpacity = it) },
                                )
                            }
                            if (config.showBattery) {
                                TextSection(
                                    sizeLabel = stringResource(R.string.widget_clock_battery_size),
                                    opacityLabel = stringResource(R.string.widget_clock_battery_opacity),
                                    preview = stringResource(
                                        R.string.widget_clock_battery_percent,
                                        ClockWidgetBattery.percent(this@ClockWidgetConfigActivity) ?: 100,
                                    ),
                                    size = config.batterySizeSp,
                                    sizeRange = ClockWidgetConfig.DATE_SIZE_RANGE,
                                    opacity = config.batteryOpacity,
                                    color = config.textColor,
                                    onSize = { config = config.copy(batterySizeSp = it) },
                                    onOpacity = { config = config.copy(batteryOpacity = it) },
                                )
                            }

                            if (config.showWeather) {
                                TextSection(
                                    sizeLabel = stringResource(R.string.widget_clock_weather_size),
                                    opacityLabel = stringResource(R.string.widget_clock_weather_opacity),
                                    preview = ClockWidgetWeather
                                        .current(this@ClockWidgetConfigActivity)
                                        ?.let { ClockWidgetWeather.formatted(it, config.weatherFahrenheit) }
                                        ?: stringResource(R.string.widget_clock_weather_waiting),
                                    size = config.weatherSizeSp,
                                    sizeRange = ClockWidgetConfig.DATE_SIZE_RANGE,
                                    opacity = config.weatherOpacity,
                                    color = config.textColor,
                                    onSize = { config = config.copy(weatherSizeSp = it) },
                                    onOpacity = { config = config.copy(weatherOpacity = it) },
                                )
                            }
                            if (config.showAgenda || config.showAlarm) {
                                TextSection(
                                    sizeLabel = stringResource(R.string.widget_clock_agenda_size),
                                    opacityLabel = stringResource(R.string.widget_clock_agenda_opacity),
                                    preview = ClockWidgetAgenda
                                        .events(this@ClockWidgetConfigActivity, 1, config.agendaLookaheadHours, config.agendaRelative)
                                        .firstOrNull()?.text
                                        ?: stringResource(R.string.widget_clock_agenda_empty),
                                    size = config.agendaSizeSp,
                                    sizeRange = ClockWidgetConfig.DATE_SIZE_RANGE,
                                    opacity = config.agendaOpacity,
                                    color = config.textColor,
                                    onSize = { config = config.copy(agendaSizeSp = it) },
                                    onOpacity = { config = config.copy(agendaOpacity = it) },
                                )
                            }
                            if (config.showNotifications) {
                                TextSection(
                                    sizeLabel = stringResource(R.string.widget_clock_notifications_size),
                                    opacityLabel = stringResource(R.string.widget_clock_notifications_opacity),
                                    preview = pluralStringResource(
                                        R.plurals.widget_clock_notification_count, 3, 3,
                                    ),
                                    size = config.notificationsSizeSp,
                                    sizeRange = ClockWidgetConfig.DATE_SIZE_RANGE,
                                    opacity = config.notificationsOpacity,
                                    color = config.textColor,
                                    onSize = { config = config.copy(notificationsSizeSp = it) },
                                    onOpacity = { config = config.copy(notificationsOpacity = it) },
                                )
                            }

                            if (pickingWeatherApp) {
                                WeatherAppDialog(
                                    apps = remember { launchableApps() },
                                    selected = config.weatherPackage,
                                    onPick = {
                                        config = config.copy(weatherPackage = it)
                                        pickingWeatherApp = false
                                    },
                                    onDismiss = { pickingWeatherApp = false },
                                )
                            }

                            Row {
                                TextButton(onClick = { finish() }) {
                                    Text(stringResource(R.string.action_cancel))
                                }
                                TextButton(onClick = { save(config) }) {
                                    Text(stringResource(R.string.action_save))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Apps to choose from, read once when the dialog opens.
     *
     * Read on the spot rather than observed: this is a settings screen that is opened, used and
     * dismissed, and an app installed while it is up is not worth a subscription.
     */
    private fun launchableApps(): List<AppInfo> = runCatching {
        (application as VictoriaApp).appRepository.queryAllApps()
            .filter { it.kind == EntryKind.APP }
            .sortedBy { it.label.lowercase() }
    }.getOrDefault(emptyList())

    private fun save(config: ClockWidgetConfig) {
        ClockWidgetConfig.write(this, widgetId, config)
        // Redrawn here rather than waiting for the system to ask: nothing else would, since
        // this widget has no update interval of its own.
        ClockWidgetProvider.render(this, AppWidgetManager.getInstance(this), widgetId)
        // Weather arrives by broadcast, and the receiver for it exists only while something
        // wants it. This is the moment that can have changed.
        ClockWidgetProvider.syncWeatherReceiver(this)
        setResult(Activity.RESULT_OK, resultIntent())
        finish()
    }

    private fun resultIntent() =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}

@Composable
private fun Row(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Chips(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun HourSection(config: ClockWidgetConfig, onSelect: (ClockWidgetConfig.HourFormat) -> Unit) {
    Column {
        Text(stringResource(R.string.widget_clock_hour), style = MaterialTheme.typography.bodyMedium)
        Chips {
            FilledChip(stringResource(R.string.widget_clock_hour_system), config.hourFormat == ClockWidgetConfig.HourFormat.SYSTEM) {
                onSelect(ClockWidgetConfig.HourFormat.SYSTEM)
            }
            FilledChip(stringResource(R.string.widget_clock_hour_12), config.hourFormat == ClockWidgetConfig.HourFormat.TWELVE) {
                onSelect(ClockWidgetConfig.HourFormat.TWELVE)
            }
            FilledChip(stringResource(R.string.widget_clock_hour_24), config.hourFormat == ClockWidgetConfig.HourFormat.TWENTY_FOUR) {
                onSelect(ClockWidgetConfig.HourFormat.TWENTY_FOUR)
            }
        }
    }
}

@Composable
private fun DateSection(config: ClockWidgetConfig, onSelect: (String?) -> Unit) {
    Column {
        Text(stringResource(R.string.widget_clock_date_format), style = MaterialTheme.typography.bodyMedium)
        Chips {
            // Each choice is labelled with today's date in that shape, since a pattern like
            // "EEE, d MMM" tells nobody what they are picking.
            val now = Date()
            ClockWidgetConfig.DATE_CHOICES.forEach { pattern ->
                val label = runCatching { DateFormat.format(pattern, now).toString() }.getOrDefault(pattern)
                FilledChip(label, config.datePattern == pattern) { onSelect(pattern) }
            }
            FilledChip(stringResource(R.string.widget_clock_date_none), config.datePattern == null) {
                onSelect(null)
            }
        }
    }
}

/**
 * One row of the widget: how big it is, how strongly it is drawn, and what that looks like.
 *
 * Size and opacity together rather than as two lists of sliders, because the preview under them
 * answers for both at once and there is otherwise no way to tell what a number means.
 */
@Composable
private fun TextSection(
    sizeLabel: String,
    opacityLabel: String,
    preview: String,
    size: Int,
    sizeRange: IntRange,
    opacity: Int,
    color: Int,
    onSize: (Int) -> Unit,
    onOpacity: (Int) -> Unit,
) {
    val opacityRange = ClockWidgetConfig.OPACITY_RANGE
    Column {
        // The same row the rest of the launcher sets a size with: minus and plus for one step
        // at a time, and the slider for crossing the range.
        SliderRow(
            label = sizeLabel,
            value = size.toFloat(),
            range = sizeRange.first.toFloat()..sizeRange.last.toFloat(),
            valueLabel = size.toString(),
            onValueChange = { onSize(it.roundToInt().coerceIn(sizeRange)) },
        )
        SliderRow(
            label = opacityLabel,
            value = opacity.toFloat(),
            range = opacityRange.first.toFloat()..opacityRange.last.toFloat(),
            valueLabel = "$opacity%",
            onValueChange = { onOpacity(it.roundToInt().coerceIn(opacityRange)) },
        )
        // Boxed at a fixed height so the rest of the screen does not jump around as the slider
        // moves, and scrollable sideways because a large size runs off the edge.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .horizontalScroll(rememberScrollState()),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                preview,
                fontSize = size.sp,
                lineHeight = size.sp,
                maxLines = 1,
                color = Color(ClockWidgetConfig.opacityOf(color, opacity)),
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}

/**
 * Weather, and the unit it is said in.
 *
 * The hint is the whole setup: nothing appears here until another app is actually sending, and
 * without being told that, an empty row reads as broken rather than as waiting.
 */
@Composable
private fun WeatherSection(
    config: ClockWidgetConfig,
    onShow: (Boolean) -> Unit,
    onFahrenheit: (Boolean) -> Unit,
    onPickApp: () -> Unit,
) {
    Column {
        Text(stringResource(R.string.widget_clock_weather), style = MaterialTheme.typography.bodyMedium)
        Chips {
            FilledChip(stringResource(R.string.widget_clock_weather_show), config.showWeather) {
                onShow(true)
            }
            FilledChip(stringResource(R.string.widget_clock_weather_hide), !config.showWeather) {
                onShow(false)
            }
        }
        if (config.showWeather) {
            Chips {
                FilledChip(
                    stringResource(R.string.widget_clock_weather_celsius),
                    !config.weatherFahrenheit,
                ) { onFahrenheit(false) }
                FilledChip(
                    stringResource(R.string.widget_clock_weather_fahrenheit),
                    config.weatherFahrenheit,
                ) { onFahrenheit(true) }
            }
            TextButton(onClick = onPickApp) {
                Text(stringResource(R.string.widget_clock_weather_opens))
            }
            Text(
                stringResource(R.string.widget_clock_weather_hint),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Which app the weather row opens.
 *
 * A list of every app rather than a guess at the weather ones: there is no category the system
 * recognises for weather the way there is for the calendar, and the broadcast that brings the
 * forecast does not say which app sent it.
 */
@Composable
private fun WeatherAppDialog(
    apps: List<AppInfo>,
    selected: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.widget_clock_weather_opens)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                item {
                    LayoutRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(null) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.settings_quick_launch_none),
                            modifier = Modifier.weight(1f),
                        )
                        RadioButton(selected = selected == null, onClick = { onPick(null) })
                    }
                }
                items(apps, key = { it.key }) { app ->
                    LayoutRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(app.componentName.packageName) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(app.label, modifier = Modifier.weight(1f))
                        RadioButton(
                            selected = selected == app.componentName.packageName,
                            onClick = { onPick(app.componentName.packageName) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
    )
}

/**
 * What is coming up, and how much of it.
 *
 * The permission is asked for from here, at the moment it is turned on, so nothing is demanded
 * of someone who only wanted a clock.
 */
@Composable
private fun AgendaSection(
    config: ClockWidgetConfig,
    hasPermission: Boolean,
    onShow: (Boolean) -> Unit,
    onRelative: (Boolean) -> Unit,
    onCount: (Int) -> Unit,
    onHours: (Int) -> Unit,
    onAlarm: (Boolean) -> Unit,
    onAskPermission: () -> Unit,
) {
    Column {
        Text(stringResource(R.string.widget_clock_agenda), style = MaterialTheme.typography.bodyMedium)
        Chips {
            FilledChip(stringResource(R.string.widget_clock_agenda_show), config.showAgenda) {
                if (hasPermission) onShow(true) else onAskPermission()
            }
            FilledChip(stringResource(R.string.widget_clock_agenda_hide), !config.showAgenda) {
                onShow(false)
            }
        }
        if (config.showAgenda && !hasPermission) {
            TextButton(onClick = onAskPermission) {
                Text(stringResource(R.string.widget_clock_agenda_permission))
            }
        }
        if (config.showAgenda) {
            Chips {
                FilledChip(stringResource(R.string.widget_clock_agenda_relative), config.agendaRelative) {
                    onRelative(true)
                }
                FilledChip(stringResource(R.string.widget_clock_agenda_absolute), !config.agendaRelative) {
                    onRelative(false)
                }
            }
            SliderRow(
                label = stringResource(R.string.widget_clock_agenda_count),
                value = config.agendaCount.toFloat(),
                range = ClockWidgetConfig.AGENDA_COUNT_RANGE.first.toFloat()..
                    ClockWidgetConfig.AGENDA_COUNT_RANGE.last.toFloat(),
                valueLabel = config.agendaCount.toString(),
                onValueChange = { onCount(it.toInt()) },
            )
            SliderRow(
                label = stringResource(R.string.widget_clock_agenda_hours),
                value = config.agendaLookaheadHours.toFloat(),
                range = ClockWidgetConfig.AGENDA_HOURS_RANGE.first.toFloat()..
                    ClockWidgetConfig.AGENDA_HOURS_RANGE.last.toFloat(),
                valueLabel = config.agendaLookaheadHours.toString(),
                onValueChange = { onHours(it.toInt()) },
            )
            Text(
                stringResource(R.string.widget_clock_agenda_hint),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Chips {
            FilledChip(stringResource(R.string.widget_clock_alarm_show), config.showAlarm) {
                onAlarm(true)
            }
            FilledChip(stringResource(R.string.widget_clock_alarm_hide), !config.showAlarm) {
                onAlarm(false)
            }
        }
    }
}

@Composable
private fun NotificationsSection(config: ClockWidgetConfig, onSelect: (Boolean) -> Unit) {
    Column {
        Text(
            stringResource(R.string.widget_clock_notifications),
            style = MaterialTheme.typography.bodyMedium,
        )
        Chips {
            FilledChip(
                stringResource(R.string.widget_clock_notifications_show),
                config.showNotifications,
            ) { onSelect(true) }
            FilledChip(
                stringResource(R.string.widget_clock_notifications_hide),
                !config.showNotifications,
            ) { onSelect(false) }
        }
    }
}

@Composable
private fun BatterySection(config: ClockWidgetConfig, onSelect: (Boolean) -> Unit) {
    Column {
        Text(stringResource(R.string.widget_clock_battery), style = MaterialTheme.typography.bodyMedium)
        Chips {
            FilledChip(stringResource(R.string.widget_clock_battery_show), config.showBattery) {
                onSelect(true)
            }
            FilledChip(stringResource(R.string.widget_clock_battery_hide), !config.showBattery) {
                onSelect(false)
            }
        }
    }
}

/**
 * The widget's text color.
 *
 * A color and not a font: RemoteViews can be told a color, but a typeface has to already exist
 * in the package drawing the view, and the host draws this one. A font chosen in settings is a
 * file this app loaded, which the host has no way to use.
 */
@Composable
private fun ColorSection(config: ClockWidgetConfig, onSelect: (Int) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Column {
        Text(stringResource(R.string.widget_clock_color), style = MaterialTheme.typography.bodyMedium)
        Chips {
            FilledChip(stringResource(R.string.widget_clock_color_pick), selected = false) {
                picking = true
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(config.textColor), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), CircleShape)
            )
        }
    }
    if (picking) {
        ColorPickerDialog(
            initial = config.textColor,
            onConfirm = { onSelect(it); picking = false },
            onDismiss = { picking = false },
        )
    }
}
