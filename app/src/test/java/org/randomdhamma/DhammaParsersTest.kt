package org.randomdhamma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.randomdhamma.data.remote.DhammaParsers

class DhammaParsersTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "Missing fixture $name" }.readText()

    @Test
    fun parsesYearsFromIndex() {
        val years = DhammaParsers.parseYears(fixture("morning_index.html"))
        assertEquals(2010, years.first())
        assertEquals(2026, years.last())
        assertEquals((2010..2026).toList(), years)
    }

    @Test
    fun parsesTalksFromYearPage() {
        val talks = DhammaParsers.parseTalks(fixture("morning_2015.html"), 2015)
        assertTrue("expected ~250 talks, got ${talks.size}", talks.size in 200..400)
        val first = talks.first()
        assertEquals(2015, first.year)
        assertEquals(
            "https://www.dhammatalks.org/Archive/shorttalks/y2015/151231(short)_To_Have_a_Happy_New_Year.mp3",
            first.mp3Url,
        )
        assertEquals("To Have a Happy New Year", first.title)
        assertEquals(talks.size, talks.map { it.mp3Url }.toSet().size)
    }

    @Test
    fun parsesCurrentYearTalksFromIndex() {
        val talks = DhammaParsers.parseTalks(fixture("morning_index.html"), 2026)
        assertTrue(talks.isNotEmpty())
        assertTrue(talks.all { it.year == 2026 && it.mp3Url.contains("/y2026/") })
    }

    @Test
    fun titleFromMp3Filename() {
        assertEquals(
            "The Mind Isn't Sick",
            DhammaParsers.titleFromMp3("/Archive/shorttalks/y2026/260825(short)_The_Mind_Isn't_Sick.mp3"),
        )
        assertEquals(
            "Ayu Vanno Sukham Balam",
            DhammaParsers.titleFromMp3("/Archive/shorttalks/y2026/260101%28short%29_Ayu_Vanno_Sukham_Balam.mp3"),
        )
    }

    @Test
    fun resolvesRedirectLocation() {
        assertEquals(
            "https://www.dhammatalks.org/suttas/AN/AN7_51.html",
            DhammaParsers.resolve("/suttas/AN/AN7_51.html"),
        )
    }

    @Test
    fun suttaTitles() {
        assertEquals("AN 7:51 Undeclared", DhammaParsers.parseSuttaTitle(fixture("sutta_AN7_51.html")))
        assertEquals("AN 7:51", DhammaParsers.suttaTitleFromUrl("https://www.dhammatalks.org/suttas/AN/AN7_51.html"))
        assertEquals("MN 10", DhammaParsers.suttaTitleFromUrl("https://www.dhammatalks.org/suttas/MN/MN10.html"))
        assertEquals("SN 22:59", DhammaParsers.suttaTitleFromUrl("https://www.dhammatalks.org/suttas/SN/SN22_59.html"))
    }
}
