// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import dev.victorialauncher.R
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * What is coming up: the next calendar events, and the next alarm.
 *
 * The two are read quite differently and only look alike on the widget. An alarm is free — the
 * system will say when the next one is to anyone who asks. Calendar events are not: they need
 * READ_CALENDAR, so nothing here reads anything until that has been granted, and a widget set
 * to show events before it is granted shows none rather than failing.
 */
object ClockWidgetAgenda {

    /** Title and when, already in the form the widget draws. */
    data class Entry(val text: String)

    fun hasCalendarPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * The next [limit] events starting within [lookaheadHours].
     *
     * Instances rather than events: a weekly meeting is one event and many instances, and what
     * is wanted is the next time it actually happens. The query is bounded in time by the URI
     * itself, so the provider expands only the window asked for.
     */
    fun events(
        context: Context,
        limit: Int,
        lookaheadHours: Int,
        relative: Boolean,
    ): List<Entry> {
        if (!hasCalendarPermission(context)) return emptyList()
        val now = System.currentTimeMillis()
        val until = now + TimeUnit.HOURS.toMillis(lookaheadHours.toLong())
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString())
            .appendPath(until.toString())
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY,
        )
        return runCatching {
            context.contentResolver.query(
                uri,
                projection,
                // Declined invitations are not things that are coming up.
                "${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ?",
                arrayOf(CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED.toString()),
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                val out = mutableListOf<Entry>()
                while (cursor.moveToNext() && out.size < limit) {
                    val title = cursor.getString(0)?.trim().orEmpty()
                        .ifEmpty { context.getString(R.string.widget_clock_event_untitled) }
                    val begin = cursor.getLong(1)
                    val allDay = cursor.getInt(2) == 1
                    out += Entry(describe(context, title, begin, allDay, relative, now))
                }
                out
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /** The next alarm, which costs no permission to ask about. */
    fun nextAlarm(context: Context): Entry? = runCatching {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return null
        val next = manager.nextAlarmClock ?: return null
        Entry(
            context.getString(
                R.string.widget_clock_alarm_at,
                formatClock(context, next.triggerTime),
            ),
        )
    }.getOrNull()

    /**
     * An event as one line.
     *
     * Relative reads better and goes stale: a widget is redrawn when something asks, and
     * between those "in 20 minutes" quietly becomes a lie. A clock time cannot drift, which is
     * why it is what this offers unless relative is asked for.
     */
    private fun describe(
        context: Context,
        title: String,
        begin: Long,
        allDay: Boolean,
        relative: Boolean,
        now: Long,
    ): String {
        if (allDay) return context.getString(R.string.widget_clock_event_all_day, title)
        if (!relative) return context.getString(R.string.widget_clock_event_at, formatClock(context, begin), title)
        val minutes = TimeUnit.MILLISECONDS.toMinutes((begin - now).coerceAtLeast(0))
        return when {
            minutes < 1 -> context.getString(R.string.widget_clock_event_now, title)
            minutes < 60 -> context.resources.getQuantityString(
                R.plurals.widget_clock_event_in_minutes, minutes.toInt(), title, minutes.toInt(),
            )
            else -> {
                val hours = (minutes / 60).toInt()
                context.resources.getQuantityString(
                    R.plurals.widget_clock_event_in_hours, hours, title, hours,
                )
            }
        }
    }

    private fun formatClock(context: Context, at: Long): String =
        DateFormat.getTimeFormat(context).format(Date(at))
}
