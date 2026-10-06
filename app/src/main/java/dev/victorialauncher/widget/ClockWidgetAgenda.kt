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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
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
        // Which day, where it is not today. Looking two days ahead gave two lines that read
        // alike and meant different days, and a clock time on its own says nothing about
        // which one it belongs to.
        val day = dayPrefix(context, begin, allDay, now)
        if (allDay) {
            return if (day == null) {
                context.getString(R.string.widget_clock_event_all_day, title)
            } else {
                context.getString(R.string.widget_clock_event_day_only, day, title)
            }
        }
        if (!relative) {
            val clock = formatClock(context, begin)
            val whenText = if (day == null) clock else context.getString(R.string.widget_clock_event_day_at, day, clock)
            return context.getString(R.string.widget_clock_event_at, whenText, title)
        }
        val minutes = TimeUnit.MILLISECONDS.toMinutes((begin - now).coerceAtLeast(0))
        // Past a day, how long until stops being the useful thing to say: "in 29 hours" is
        // harder to place than the day and the time it actually starts.
        if (day != null) {
            val clock = formatClock(context, begin)
            return context.getString(
                R.string.widget_clock_event_at,
                context.getString(R.string.widget_clock_event_day_at, day, clock),
                title,
            )
        }
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

    /**
     * "Tomorrow", a weekday, or null when it is today and needs no saying.
     *
     * Counted in whole dates rather than hours, so an event at 01:00 is tomorrow even when it
     * is four hours away and one at 23:00 is today even when it is twenty.
     *
     * An all-day event is read in UTC, because that is how the provider stores one: its BEGIN
     * is midnight UTC of the date it falls on, not midnight where the phone is. Read as a local
     * instant, an all-day event anywhere west of UTC lands on the evening before and is named
     * as the wrong day — which is a day the owner has nothing on.
     */
    private fun dayPrefix(context: Context, begin: Long, allDay: Boolean, now: Long): String? {
        val eventDate = eventDate(begin, allDay, ZoneId.systemDefault())
        val daysAway = daysAway(begin, allDay, now, ZoneId.systemDefault())
        return when {
            daysAway <= 0L -> null
            daysAway == 1L -> context.getString(R.string.widget_clock_event_tomorrow)
            // Within the week a weekday names itself; past that it would be ambiguous, so the
            // date is what distinguishes it.
            daysAway < 7L ->
                eventDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            else -> DateFormat.getDateFormat(context).format(
                Date.from(eventDate.atStartOfDay(ZoneId.systemDefault()).toInstant()),
            )
        }
    }

    /**
     * The date an event falls on.
     *
     * [local] for anything with a time of day, UTC for an all-day event: the provider stores
     * one as midnight UTC of its date, so reading it as a local instant puts it on the evening
     * before anywhere west of UTC.
     */
    internal fun eventDate(begin: Long, allDay: Boolean, local: ZoneId): LocalDate =
        Instant.ofEpochMilli(begin)
            .atZone(if (allDay) ZoneOffset.UTC else local)
            .toLocalDate()

    /** Whole dates between today and the event: 0 today, 1 tomorrow, negative for the past. */
    internal fun daysAway(begin: Long, allDay: Boolean, now: Long, local: ZoneId): Long =
        ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(now).atZone(local).toLocalDate(),
            eventDate(begin, allDay, local),
        )

    private fun formatClock(context: Context, at: Long): String =
        DateFormat.getTimeFormat(context).format(Date(at))
}
