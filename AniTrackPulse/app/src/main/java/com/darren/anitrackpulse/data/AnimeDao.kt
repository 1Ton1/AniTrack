package com.darren.anitrackpulse.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimeDao {
    @Query("SELECT * FROM anime_entries ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AnimeEntry>>

    @Query("SELECT * FROM anime_entries ORDER BY updatedAt DESC")
    suspend fun getAll(): List<AnimeEntry>

    @Query("SELECT * FROM anime_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): AnimeEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: AnimeEntry)

    @Query("UPDATE anime_entries SET watchedEpisodes = watchedEpisodes + 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun incrementWatched(id: Int, updatedAt: Long)

    @Delete
    suspend fun delete(entry: AnimeEntry)
}
