// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import dev.victorialauncher.R
import dev.victorialauncher.ui.settings.FilledChip
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
                            SizeSection(config) { config = config.copy(timeSizeSp = it) }

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

    private fun save(config: ClockWidgetConfig) {
        ClockWidgetConfig.write(this, widgetId, config)
        // Redrawn here rather than waiting for the system to ask: nothing else would, since
        // this widget has no update interval of its own.
        ClockWidgetProvider.render(this, AppWidgetManager.getInstance(this), widgetId)
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

@Composable
private fun SizeSection(config: ClockWidgetConfig, onSelect: (Int) -> Unit) {
    val range = ClockWidgetConfig.SIZE_RANGE
    Column {
        // The same row the rest of the launcher sets a size with: minus and plus for one step
        // at a time, and the slider for crossing the range.
        SliderRow(
            label = stringResource(R.string.widget_clock_size),
            value = config.timeSizeSp.toFloat(),
            range = range.first.toFloat()..range.last.toFloat(),
            valueLabel = config.timeSizeSp.toString(),
            onValueChange = { onSelect(it.roundToInt().coerceIn(range)) },
        )
        // Shown at the size being chosen, so the number means something before saving.
        Text(
            stringResource(R.string.widget_clock_size_preview),
            fontSize = config.timeSizeSp.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
