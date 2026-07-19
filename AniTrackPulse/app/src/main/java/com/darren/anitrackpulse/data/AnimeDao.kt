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

    @Query("UPDATE anime_entries SET isPinned = :pinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: Int, pinned: Boolean, updatedAt: Long)

    @Query("UPDATE anime_entries SET isRewatching = 1, watchedEpisodes = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun startRewatch(id: Int, updatedAt: Long)

    @Query("UPDATE anime_entries SET isRewatching = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun stopRewatch(id: Int, updatedAt: Long)

    @Query("UPDATE anime_entries SET status = :status, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun moveMany(ids: List<Int>, status: AnimeStatus, updatedAt: Long)

    @Query("DELETE FROM anime_entries WHERE id IN (:ids)")
    suspend fun deleteMany(ids: List<Int>)

    @Query("SELECT * FROM anime_entries WHERE nextAiringAt IS NOT NULL ORDER BY nextAiringAt ASC")
    fun getUpcomingBlocking(): List<AnimeEntry>

    @Delete
    suspend fun delete(entry: AnimeEntry)
}
