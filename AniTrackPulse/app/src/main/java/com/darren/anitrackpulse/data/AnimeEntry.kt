package com.darren.anitrackpulse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "anime_entries")
data class AnimeEntry(
    @PrimaryKey val id: Int,
    val title: String,
    val coverImage: String? = null,
    val status: AnimeStatus = AnimeStatus.WATCHING,
    val watchedEpisodes: Int = 0,
    val totalEpisodes: Int? = null,
    val nextEpisode: Int? = null,
    val nextAiringAt: Long? = null,
    val notes: String = "",
    val lastNotifiedEpisode: Int? = null,
    val isPinned: Boolean = false,
    val isRewatching: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
