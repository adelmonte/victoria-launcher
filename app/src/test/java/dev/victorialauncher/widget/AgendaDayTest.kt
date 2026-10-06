// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which date an upcoming event falls on.
 *
 * The provider stores an all-day event as midnight UTC of its date, so the zone it is read in
 * is the whole question — read locally, an all-day event west of UTC names the day before.
 */
class AgendaDayTest {

    private val newYork = ZoneId.of("America/New_York")
    private val berlin = ZoneId.of("Europe/Berlin")

    private fun utcMidnight(date: String): Long =
        LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun localTime(date: String, time: String, zone: ZoneId): Long =
        LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `an all-day event keeps its own date west of UTC`() {
        // Midnight UTC on the 8th is 20:00 on the 7th in New York. Read locally it would be
        // the 7th, and named as a day the owner has nothing on.
        val event = utcMidnight("2026-10-08")
        assertEquals(
            LocalDate.parse("2026-10-08"),
            ClockWidgetAgenda.eventDate(event, allDay = true, local = newYork),
        )
    }

    @Test
    fun `an all-day event keeps its own date east of UTC`() {
        val event = utcMidnight("2026-10-08")
        assertEquals(
            LocalDate.parse("2026-10-08"),
            ClockWidgetAgenda.eventDate(event, allDay = true, local = berlin),
        )
    }

    @Test
    fun `an all-day event tomorrow is one day away, not today`() {
        val now = localTime("2026-10-07", "20:00", newYork)
        val event = utcMidnight("2026-10-08")
        assertEquals(1L, ClockWidgetAgenda.daysAway(event, allDay = true, now = now, local = newYork))
    }

    @Test
    fun `an early hour tomorrow is tomorrow even when hours away`() {
        val now = localTime("2026-10-07", "22:00", newYork)
        val event = localTime("2026-10-08", "01:00", newYork)
        assertEquals(1L, ClockWidgetAgenda.daysAway(event, allDay = false, now = now, local = newYork))
    }

    @Test
    fun `a late hour today is today even when nearly a day away`() {
        val now = localTime("2026-10-07", "00:30", newYork)
        val event = localTime("2026-10-07", "23:00", newYork)
        assertEquals(0L, ClockWidgetAgenda.daysAway(event, allDay = false, now = now, local = newYork))
    }
}
