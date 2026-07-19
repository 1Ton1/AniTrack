package com.darren.anitrackpulse.network

data class AnimeSearchResult(
    val id: Int,
    val title: String,
    val coverImage: String? = null,
    val totalEpisodes: Int? = null,
    val nextEpisode: Int? = null,
    val nextAiringAt: Long? = null
)
