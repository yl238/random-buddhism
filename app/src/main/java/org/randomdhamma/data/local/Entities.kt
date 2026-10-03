package org.randomdhamma.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "history", indices = [Index("createdAt")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val url: String,
    val title: String,
    val source: String,
    val createdAt: Long,
)

@Entity(tableName = "talks", indices = [Index("year")])
data class TalkEntity(
    @PrimaryKey val mp3Url: String,
    val year: Int,
    val title: String,
)

/** When each year's talk list was last fetched. */
@Entity(tableName = "talk_years")
data class TalkYearEntity(
    @PrimaryKey val year: Int,
    val fetchedAt: Long,
)
