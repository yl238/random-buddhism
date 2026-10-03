package org.randomdhamma

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.randomdhamma.data.isTalkListStale
import java.time.LocalDate
import java.time.ZoneOffset

class TalkListStaleTest {
    private val utc = ZoneOffset.UTC
    private fun at(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(utc).toInstant().toEpochMilli()

    @Test
    fun currentYearRefreshesWeekly() {
        assertFalse(isTalkListStale(2026, at(2026, 10, 1), at(2026, 10, 5), utc))
        assertTrue(isTalkListStale(2026, at(2026, 10, 1), at(2026, 10, 9), utc))
    }

    @Test
    fun previousYearCatchesUpAfterNewYear() {
        // Fetched on Dec 26 while 2026 was current: the last days of December are missing.
        assertTrue(isTalkListStale(2026, at(2026, 12, 26), at(2027, 1, 10), utc))
    }

    @Test
    fun listFetchedAfterYearEndNeverGoesStale() {
        assertFalse(isTalkListStale(2015, at(2026, 10, 1), at(2030, 1, 1), utc))
        assertFalse(isTalkListStale(2026, at(2027, 1, 9), at(2028, 1, 1), utc))
    }
}
