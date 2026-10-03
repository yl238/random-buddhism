package org.randomdhamma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.randomdhamma.data.ContentType
import org.randomdhamma.data.chooseType
import kotlin.random.Random

class CoinFlipTest {
    private fun suttaShare(percent: Int, n: Int = 20_000): Double {
        val random = Random(7)
        return (1..n).count { chooseType(percent, random) == ContentType.SUTTA } / n.toDouble()
    }

    @Test
    fun fiftyFiftyIsBalanced() {
        val share = suttaShare(50)
        assertTrue("share=$share", share in 0.48..0.52)
    }

    @Test
    fun extremesAreDeterministic() {
        assertEquals(0.0, suttaShare(0), 0.0)
        assertEquals(1.0, suttaShare(100), 0.0)
    }

    @Test
    fun weightedRatio() {
        val share = suttaShare(30)
        assertTrue("share=$share", share in 0.28..0.32)
    }
}
