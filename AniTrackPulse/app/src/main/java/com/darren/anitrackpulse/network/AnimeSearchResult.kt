package com.darren.anitrackpulse.network

data class AnimeSearchResult(
    val id: Int,
    val title: String,
    val coverImage: String? = null,
    val totalEpisodes: Int? = null,
    val nextEpisode: Int? = null,
    val nextAiringAt: Long? = null,
    val status: String? = null
) {
    /** True when AniList reports this anime as currently airing new episodes. */
    val isOngoing: Boolean
        get() = status.equals("RELEASING", ignoreCase = true)
}
