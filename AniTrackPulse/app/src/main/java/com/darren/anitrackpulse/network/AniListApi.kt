package com.darren.anitrackpulse.network

import java.time.Instant

class AniListApi {
    suspend fun searchAnime(query: String): List<AnimeSearchResult> {
        if (query.isBlank()) return emptyList()
        val now = Instant.now().epochSecond
        return listOf(
            AnimeSearchResult(
                id = query.hashCode(),
                title = query.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                totalEpisodes = 12,
                nextEpisode = 6,
                nextAiringAt = now + 86400
            )
        )
    }

    suspend fun getAnimeById(id: Int): AnimeSearchResult? {
        val now = Instant.now().epochSecond
        return AnimeSearchResult(id = id, title = "Anime #$id", totalEpisodes = 12, nextEpisode = 7, nextAiringAt = now + 172800)
    }
}
