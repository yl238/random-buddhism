package org.randomdhamma

import org.junit.Assert.assertEquals
import org.junit.Test
import org.randomdhamma.schedule.PENDING_GRACE_MS
import org.randomdhamma.schedule.RearmAction
import org.randomdhamma.schedule.rearmAction

class RearmActionTest {
    private val now = 1_800_000_000_000L
    private val minute = 60_000L

    private fun action(nextFireAt: Long, enabled: Boolean = true, alarmsCleared: Boolean = false) =
        rearmAction(enabled, nextFireAt, now, alarmsCleared)

    @Test
    fun disabledCancels() = assertEquals(RearmAction.CANCEL, action(now + minute, enabled = false))

    @Test
    fun futureTimeIsReArmedNotRerolled() = assertEquals(RearmAction.REARM, action(now + 30 * minute))

    @Test
    fun neverScheduledPicksATime() = assertEquals(RearmAction.RESCHEDULE, action(0L))

    @Test
    fun recentlyPassedTimeIsLeftAlone() {
        // The alarm may be firing right now (cold start by the alarm itself) or delivered late.
        assertEquals(RearmAction.LEAVE, action(now - 1_000))
        assertEquals(RearmAction.LEAVE, action(now - 59 * minute))
        assertEquals(RearmAction.LEAVE, action(now - PENDING_GRACE_MS + minute))
    }

    @Test
    fun longPassedTimeIsTreatedAsLost() = assertEquals(RearmAction.RESCHEDULE, action(now - PENDING_GRACE_MS - minute))

    @Test
    fun afterRebootAPassedTimeIsMissed() {
        assertEquals(RearmAction.RESCHEDULE, action(now - 5 * minute, alarmsCleared = true))
        assertEquals(RearmAction.REARM, action(now + 5 * minute, alarmsCleared = true))
    }
}
