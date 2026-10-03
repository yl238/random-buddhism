package org.randomdhamma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.randomdhamma.schedule.nextFireTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

class RandomTimeTest {
    private val zone = ZoneId.of("Europe/London")
    private val start = 7 * 60
    private val end = 22 * 60

    private fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 10, 3, h, m, 0, 0, zone)
    private fun minuteOfDay(t: ZonedDateTime) = t.hour * 60 + t.minute

    @Test
    fun alwaysInsideWindow() {
        val random = Random(42)
        repeat(5_000) {
            val now = at(random.nextInt(24), random.nextInt(60))
            val next = nextFireTime(now, start, end, lastFiredEpochDay = -1, random = random)
            assertTrue("$next outside window", minuteOfDay(next) in start until end)
            assertTrue("$next not after $now", next.isAfter(now))
        }
    }

    @Test
    fun beforeWindowSchedulesToday() {
        val now = at(6, 0)
        val next = nextFireTime(now, start, end, lastFiredEpochDay = -1, random = Random(1))
        assertEquals(now.toLocalDate(), next.toLocalDate())
    }

    @Test
    fun midWindowSchedulesLaterToday() {
        val now = at(12, 30)
        repeat(200) {
            val next = nextFireTime(now, start, end, lastFiredEpochDay = -1, random = Random(it))
            assertEquals(now.toLocalDate(), next.toLocalDate())
            assertTrue(minuteOfDay(next) > 12 * 60 + 30)
        }
    }

    @Test
    fun afterWindowRollsToTomorrow() {
        val now = at(22, 15)
        val next = nextFireTime(now, start, end, lastFiredEpochDay = -1, random = Random(1))
        assertEquals(now.toLocalDate().plusDays(1), next.toLocalDate())
    }

    @Test
    fun alreadyFiredTodayRollsToTomorrow() {
        val now = at(9, 0)
        val next = nextFireTime(now, start, end, lastFiredEpochDay = now.toLocalDate().toEpochDay(), random = Random(1))
        assertEquals(now.toLocalDate().plusDays(1), next.toLocalDate())
    }

    @Test
    fun slackKeepsLateDeliveryInsideWindow() {
        val random = Random(3)
        repeat(5_000) {
            val now = at(random.nextInt(24), random.nextInt(60))
            val next = nextFireTime(now, start, end, lastFiredEpochDay = -1, random = random, deliverySlackMinutes = 60)
            assertTrue("$next + 1h past window end", minuteOfDay(next) + 60 <= end)
        }
    }

    @Test
    fun slackNeverEmptiesShortWindow() {
        val next = nextFireTime(at(6, 0), 7 * 60, 7 * 60 + 30, lastFiredEpochDay = -1, random = Random(1), deliverySlackMinutes = 60)
        assertEquals(7 * 60, minuteOfDay(next))
    }
}
