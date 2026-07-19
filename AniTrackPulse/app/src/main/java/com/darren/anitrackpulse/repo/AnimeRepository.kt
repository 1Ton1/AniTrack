package com.darren.anitrackpulse.repo

import com.darren.anitrackpulse.data.AnimeDao
import com.darren.anitrackpulse.data.AnimeEntry
import com.darren.anitrackpulse.data.AnimeStatus
import com.darren.anitrackpulse.network.AniListApi
import com.darren.anitrackpulse.network.AnimeDetails
import com.darren.anitrackpulse.network.AnimeSearchResult
import com.darren.anitrackpulse.network.AnimeSeason
import com.darren.anitrackpulse.network.SearchFormat
import com.darren.anitrackpulse.network.SearchSort
import com.darren.anitrackpulse.network.SearchStatusFilter
import kotlinx.coroutines.flow.Flow

class AnimeRepository(
    private val dao: AnimeDao,
    private val api: AniListApi
) {
    fun observeSavedAnime(): Flow<List<AnimeEntry>> = dao.observeAll()

    suspend fun searchAnime(
        query: String,
        format: SearchFormat = SearchFormat.ANY,
        status: SearchStatusFilter = SearchStatusFilter.ANY,
        sort: SearchSort = SearchSort.RELEVANCE
    ): List<AnimeSearchResult> =
        if (query.isBlank()) emptyList() else api.searchAnime(query.trim(), format, status, sort)

    suspend fun getSeasonalPopular(season: AnimeSeason, seasonYear: Int): List<AnimeSearchResult> =
        api.getSeasonalPopular(season, seasonYear)

    suspend fun getAnimeDetails(id: Int): AnimeDetails? = api.getAnimeDetails(id)

    suspend fun saveAnime(result: AnimeSearchResult, status: AnimeStatus) {
        val existing = dao.getById(result.id)
        dao.upsert(
            AnimeEntry(
                id = result.id,
                title = result.title,
                coverImage = result.coverImage,
                status = status,
                watchedEpisodes = existing?.watchedEpisodes ?: 0,
                totalEpisodes = result.totalEpisodes,
                nextEpisode = result.nextEpisode,
                nextAiringAt = result.nextAiringAt,
                notes = existing?.notes.orEmpty(),
                lastNotifiedEpisode = existing?.lastNotifiedEpisode,
                isPinned = existing?.isPinned ?: false,
                isRewatching = existing?.isRewatching ?: false,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun moveAnime(id: Int, newStatus: AnimeStatus) {
        val existing = dao.getById(id) ?: return
        dao.upsert(existing.copy(status = newStatus, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setPinned(id: Int, pinned: Boolean) { dao.setPinned(id, pinned, System.currentTimeMillis()) }
    suspend fun startRewatch(id: Int) { dao.startRewatch(id, System.currentTimeMillis()) }
    suspend fun stopRewatch(id: Int) { dao.stopRewatch(id, System.currentTimeMillis()) }
    suspend fun moveMany(ids: List<Int>, newStatus: AnimeStatus) { dao.moveMany(ids, newStatus, System.currentTimeMillis()) }
    suspend fun deleteMany(ids: List<Int>) { dao.deleteMany(ids) }

    suspend fun incrementWatched(id: Int) { dao.incrementWatched(id, System.currentTimeMillis()) }
    suspend fun deleteAnime(entry: AnimeEntry) { dao.delete(entry) }

    suspend fun refreshAnime(id: Int): AnimeEntry? {
        val existing = dao.getById(id) ?: return null
        val latest = api.getAnimeById(id) ?: return null
        val updated = existing.copy(
            title = latest.title,
            coverImage = latest.coverImage,
            totalEpisodes = latest.totalEpisodes,
            nextEpisode = latest.nextEpisode,
            nextAiringAt = latest.nextAiringAt,
            updatedAt = System.currentTimeMillis()
        )
        dao.upsert(updated)
        return updated
    }

    suspend fun refreshAll(): List<AnimeEntry> {
        val changed = mutableListOf<AnimeEntry>()
        dao.getAll().forEach { anime -> refreshAnime(anime.id)?.let { changed += it } }
        return changed
    }
}
