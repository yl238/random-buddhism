package org.randomdhamma.schedule

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.random.Random

/**
 * Picks the next random notification time inside the daily window [startMinute, endMinute).
 * Today is used when it hasn't fired yet and some of the window remains; otherwise tomorrow.
 *
 * [deliverySlackMinutes] keeps the chosen time that far before the window end, for alarms the
 * system may deliver late (inexact alarms can be deferred by up to an hour).
 */
fun nextFireTime(
    now: ZonedDateTime,
    startMinute: Int,
    endMinute: Int,
    lastFiredEpochDay: Long,
    random: Random = Random.Default,
    deliverySlackMinutes: Int = 0,
): ZonedDateTime {
    require(startMinute in 0 until endMinute && endMinute <= 24 * 60) { "Invalid window $startMinute..$endMinute" }
    // Never shrink the window to nothing: very short windows just accept a possibly late delivery.
    val endMinute = maxOf(startMinute + 1, endMinute - deliverySlackMinutes)
    val today = now.toLocalDate()
    val nowMinute = now.hour * 60 + now.minute
    val earliestToday = maxOf(startMinute, nowMinute + 1)
    val (day, lo) = if (lastFiredEpochDay != today.toEpochDay() && earliestToday < endMinute) {
        today to earliestToday
    } else {
        today.plusDays(1) to startMinute
    }
    val minute = lo + random.nextInt(endMinute - lo)
    return at(day, minute, random.nextInt(60), now)
}

private fun at(day: LocalDate, minuteOfDay: Int, second: Int, zoneSource: ZonedDateTime): ZonedDateTime {
    val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60, second)
    return LocalDateTime.of(day, time).atZone(zoneSource.zone)
}
