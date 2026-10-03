package org.randomdhamma.data.remote

import org.jsoup.Jsoup
import org.randomdhamma.data.Talk
import java.net.URI
import java.net.URLDecoder

/** Pure parsing helpers for dhammatalks.org pages, kept free of I/O so they can be unit tested. */
object DhammaParsers {

    const val BASE_URL = "https://www.dhammatalks.org"

    private val yearLinkRegex = Regex("""/audio/morning/(\d{4})/""")
    private val datePrefixRegex = Regex("""^\d{6}(\(short\))?_?""")
    private val suttaPathRegex = Regex("""^([A-Za-z]+)(\d+)(?:_(\d+(?:-\d+)?))?$""")

    fun resolve(href: String, base: String = BASE_URL): String = URI(base).resolve(href.trim()).toString()

    /** Years that have a morning-talk archive (year pages plus the current year listed inline on the index). */
    fun parseYears(indexHtml: String): List<Int> =
        yearLinkRegex.findAll(indexHtml).map { it.groupValues[1].toInt() }.toSortedSet().toList()

    /** All talks on a morning archive page (a year page or the index, which lists the current year). */
    fun parseTalks(html: String, year: Int? = null): List<Talk> {
        val doc = Jsoup.parse(html, BASE_URL)
        val talks = LinkedHashMap<String, Talk>()
        for (li in doc.select("li.li-audio")) {
            val href = li.selectFirst("button[data-link]")?.attr("data-link")
                ?: li.selectFirst("a[href$=.mp3]")?.attr("href")
                ?: continue
            if (!href.endsWith(".mp3", ignoreCase = true)) continue
            val url = resolve(href)
            val talkYear = yearFromMp3(href) ?: year ?: continue
            if (year != null && talkYear != year) continue
            val title = li.selectFirst("a.audio span")?.text()?.takeIf { it.isNotBlank() }
                ?: titleFromMp3(href)
            talks.putIfAbsent(url, Talk(talkYear, url, title))
        }
        return talks.values.toList()
    }

    /** "/Archive/shorttalks/y2015/151231(short)_To_Have_a_Happy_New_Year.mp3" -> "To Have a Happy New Year" */
    fun titleFromMp3(href: String): String {
        val file = URLDecoder.decode(href.substringAfterLast('/'), "UTF-8").removeSuffix(".mp3")
        return file.replace(datePrefixRegex, "").replace('_', ' ').trim().ifEmpty { file }
    }

    fun yearFromMp3(href: String): Int? =
        Regex("""/y(\d{4})/""").find(href)?.groupValues?.get(1)?.toInt()

    /** "AN 7:51 Undeclared | Abyākata Sutta | sutta on dhammatalks.org" -> "AN 7:51 Undeclared" */
    fun parseSuttaTitle(html: String): String? {
        val title = Jsoup.parse(html).title().trim()
        if (title.isEmpty()) return null
        return title.split(" | ").first().trim().ifEmpty { null }
    }

    /** Fallback title from a sutta URL: ".../suttas/AN/AN7_51.html" -> "AN 7:51". */
    fun suttaTitleFromUrl(url: String): String {
        val name = url.substringAfterLast('/').substringBefore('#').removeSuffix(".html")
        val m = suttaPathRegex.matchEntire(name) ?: return name.replace('_', ' ')
        val (book, num, sub) = m.destructured
        return if (sub.isEmpty()) "$book $num" else "$book $num:$sub"
    }
}
