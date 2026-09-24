// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.victorialauncher.R

/** Every value in edit mode moves a dp at a time; holding is what covers distance. */
const val PADDING_STEP_DP = 1
const val HEIGHT_STEP_DP = 1

/** Widest a padding can be pushed; roughly a phone screen. */
private val PADDING_RANGE = 0..400

/** How long a press waits before it starts repeating, then how fast it repeats. */
private const val REPEAT_DELAY_MS = 450L
private const val REPEAT_INTERVAL_MS = 55L

/** Inset of a stepper row from the screen edge. */
private val STEPPER_ROW_INSET = 12.dp

/** Gap between the last stepper button and the end of its row. */
private val STEPPER_TRAILING_GAP = 4.dp

/**
 * Where every edit-mode control's trailing edge sits, measured from the screen edge.
 *
 * The steppers set it and the drag handles borrow it, so the pluses and the handles read as
 * one column down the screen instead of the handles hanging off on their own.
 */
val EDIT_CONTROL_END_INSET = STEPPER_ROW_INSET + STEPPER_TRAILING_GAP

/** Touch target of an edit-mode control, shared for the same reason as [EDIT_CONTROL_END_INSET]. */
val EDIT_CONTROL_SIZE = 36.dp

/**
 * One adjustable value in edit mode: a label, the value, and a pair of steppers.
 *
 * Three ways to reach a number, because no single one is both exact and quick. A tap moves it
 * by one. Holding repeats, and accelerates the longer it is held, so crossing a screen's worth
 * of padding takes a couple of seconds rather than a hundred taps. Tapping the number itself
 * opens a field to type it outright, for when the value is already known.
 *
 * The drag handles this replaced were quick but never landed where you wanted: they set a
 * value from finger travel that had nothing to do with the thing being moved.
 */
@Composable
fun StepperRow(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    onChange: (Int) -> Unit,
    /** Kept for the dialog this row opens, which is drawn over the wallpaper rather than on a
     *  surface of its own. */
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    // Drawn on a surface of its own rather than in the text color picked for the wallpaper.
    // That color is one flat answer for the whole screen, and a wallpaper that is dark down one
    // half and light down the other has no such answer — the labels went invisible over the
    // half that matched them. These rows are the launcher's own controls, not something laid
    // over the wallpaper to be read against it, so they bring their own background with them.
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    var typing by remember { mutableStateOf(false) }
    val canDecrease = value > range.first
    val canIncrease = value < range.last

    fun nudge(by: Int) = onChange((value + by).coerceIn(range.first, range.last))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = STEPPER_ROW_INSET)
            .background(surface.copy(alpha = 0.92f), RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = onSurface,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        StepButton(
            icon = Icons.Filled.Remove,
            description = stringResource(R.string.settings_less),
            enabled = canDecrease,
            contentColor = onSurface,
        ) { multiplier -> nudge(-step * multiplier) }
        Text(
            "${value}dp",
            color = onSurface,
            fontSize = 12.sp,
            modifier = Modifier
                .clickable { typing = true }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
        StepButton(
            icon = Icons.Filled.Add,
            description = stringResource(R.string.settings_more),
            enabled = canIncrease,
            contentColor = onSurface,
        ) { multiplier -> nudge(step * multiplier) }
        Spacer(Modifier.size(STEPPER_TRAILING_GAP))
    }

    if (typing) {
        ValueEntryDialog(
            label = label,
            value = value,
            range = range,
            onConfirm = { onChange(it); typing = false },
            onDismiss = { typing = false },
        )
    }
}

/**
 * A stepper button that repeats while held, taking bigger strides the longer it is down.
 *
 * Not an IconButton: its click handling fires once per press, and this needs the press itself.
 */
@Composable
private fun StepButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    contentColor: Color,
    onStep: (multiplier: Int) -> Unit,
) {
    val step by rememberUpdatedState(onStep)
    Box(
        modifier = Modifier
            .size(EDIT_CONTROL_SIZE)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    step(1)
                    var held = 0L
                    var wait = REPEAT_DELAY_MS
                    while (true) {
                        // Null means the wait expired with the finger still down; anything
                        // else means it lifted or the gesture was taken away.
                        val lifted = withTimeoutOrNull(wait) { waitForUpOrCancellation(); true } ?: false
                        if (lifted) break
                        held += wait
                        wait = REPEAT_INTERVAL_MS
                        step(
                            when {
                                held > 2400 -> 10
                                held > 1200 -> 4
                                else -> 1
                            }
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = contentColor.copy(alpha = if (enabled) 1f else 0.3f),
        )
    }
}

/** Types a value outright, for when you already know the number you want. */
@Composable
private fun ValueEntryDialog(
    label: String,
    value: Int,
    range: IntRange,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(value.toString()) }
    val parsed = text.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() }.take(4) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.edit_value_range, range.first, range.last),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let { onConfirm(it.coerceIn(range.first, range.last)) } },
                enabled = parsed != null,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * The gap above or below a home block. Out of edit mode it is only that gap; in edit mode the
 * gap doubles as the control that sets it, so the stepper sits exactly where its effect shows.
 */
@Composable
fun PaddingHandle(
    editMode: Boolean,
    @StringRes label: Int,
    value: Int,
    onChange: (Int) -> Unit,
    contentColor: Color,
) {
    if (!editMode) {
        Spacer(Modifier.height(value.dp))
        return
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(maxOf(value, 40).dp),
        contentAlignment = Alignment.Center,
    ) {
        StepperRow(
            label = stringResource(label),
            value = value,
            range = PADDING_RANGE,
            step = PADDING_STEP_DP,
            onChange = onChange,
            contentColor = contentColor,
        )
    }
}
