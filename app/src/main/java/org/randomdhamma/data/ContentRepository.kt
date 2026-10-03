package org.randomdhamma.data

import kotlinx.coroutines.flow.Flow
import org.randomdhamma.data.local.HistoryDao
import org.randomdhamma.data.local.HistoryEntity
import org.randomdhamma.data.local.TalkDao
import org.randomdhamma.data.local.TalkEntity
import org.randomdhamma.data.remote.DhammaTalksClient
import java.io.IOException
import kotlin.random.Random

/** Weighted coin flip: [suttaPercent] chance of a sutta, otherwise a talk. */
fun chooseType(suttaPercent: Int, random: Random = Random.Default): ContentType =
    if (random.nextInt(100) < suttaPercent.coerceIn(0, 100)) ContentType.SUTTA else ContentType.TALK

class ContentRepository(
    private val client: DhammaTalksClient,
    private val historyDao: HistoryDao,
    private val talkDao: TalkDao,
    private val settings: SettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val history: Flow<List<HistoryEntity>> = historyDao.observeAll()
    val latestDaily: Flow<HistoryEntity?> = historyDao.observeLatestDaily()

    /** Picks a random teaching (forced to [type] if given) and records it in history. */
    suspend fun pick(source: PickSource, type: ContentType? = null): Pick {
        val chosen = type ?: chooseType(settings.current().suttaPercent)
        val pick = when (chosen) {
            ContentType.SUTTA -> client.randomSutta()
            ContentType.TALK -> randomTalk()
        }
        record(pick, source)
        return pick
    }

    suspend fun record(pick: Pick, source: PickSource) {
        historyDao.insert(
            HistoryEntity(
                type = pick.type.name,
                url = pick.url,
                title = pick.title,
                source = source.name,
                createdAt = clock(),
            )
        )
    }

    suspend fun clearHistory() = historyDao.clear()

    /**
     * Uniform over every cached talk once the cache is complete; until then, a random year
     * followed by a random talk from that year (fetched and cached on demand).
     */
    private suspend fun randomTalk(): Pick {
        if (settings.current().talkCacheComplete) {
            talkDao.randomTalk()?.let { return it.toPick() }
        }
        val years = client.fetchYears()
        if (years.isEmpty()) throw IOException("No talk years found")
        val latest = years.max()
        val year = years.random()
        val cached = talkDao.fetchedYear(year)?.takeIf { !isStale(year, latest, it.fetchedAt) }
        if (cached == null) cacheYear(year, latest)
        return talkDao.randomTalkInYear(year)?.toPick() ?: throw IOException("No talks found for $year")
    }

    /** Fetches every year's talk list not yet cached (or stale). Returns true when the cache is complete. */
    suspend fun refreshTalkCache(): Boolean {
        val years = client.fetchYears()
        if (years.isEmpty()) return false
        val latest = years.max()
        val fetched = talkDao.fetchedYears().associate { it.year to it.fetchedAt }
        for (year in years) {
            val fetchedAt = fetched[year]
            if (fetchedAt == null || isStale(year, latest, fetchedAt)) {
                cacheYear(year, latest)
                kotlinx.coroutines.delay(POLITE_DELAY_MS)
            }
        }
        settings.setTalkCacheComplete(true)
        return true
    }

    private suspend fun cacheYear(year: Int, latest: Int) {
        val talks = client.fetchTalks(year, latest)
        talkDao.replaceYear(year, talks.map { TalkEntity(it.mp3Url, it.year, it.title) }, clock())
    }

    /** Past years never change; the current year gains a talk a day, so refresh it weekly. */
    private fun isStale(year: Int, latest: Int, fetchedAt: Long): Boolean =
        year >= latest && clock() - fetchedAt > CURRENT_YEAR_MAX_AGE_MS

    private fun TalkEntity.toPick() = Pick(ContentType.TALK, mp3Url, title)

    companion object {
        private const val CURRENT_YEAR_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        private const val POLITE_DELAY_MS = 500L
    }
}
