package org.randomdhamma.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entry: HistoryEntity): Long

    @Query("SELECT * FROM history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE source = 'DAILY' ORDER BY createdAt DESC LIMIT 1")
    fun observeLatestDaily(): Flow<HistoryEntity?>

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
abstract class TalkDao {
    @Query("SELECT * FROM talks ORDER BY RANDOM() LIMIT 1")
    abstract suspend fun randomTalk(): TalkEntity?

    @Query("SELECT * FROM talks WHERE year = :year ORDER BY RANDOM() LIMIT 1")
    abstract suspend fun randomTalkInYear(year: Int): TalkEntity?

    @Query("SELECT * FROM talk_years")
    abstract suspend fun fetchedYears(): List<TalkYearEntity>

    @Query("SELECT * FROM talk_years WHERE year = :year")
    abstract suspend fun fetchedYear(year: Int): TalkYearEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTalks(talks: List<TalkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertYear(year: TalkYearEntity)

    @Query("DELETE FROM talks WHERE year = :year")
    abstract suspend fun deleteYear(year: Int)

    @Transaction
    open suspend fun replaceYear(year: Int, talks: List<TalkEntity>, fetchedAt: Long) {
        deleteYear(year)
        insertTalks(talks)
        insertYear(TalkYearEntity(year, fetchedAt))
    }
}
