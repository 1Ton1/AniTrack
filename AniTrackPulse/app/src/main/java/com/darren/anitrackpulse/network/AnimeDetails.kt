package com.darren.anitrackpulse.network

data class RelatedAnime(
    val id: Int,
    val title: String,
    val coverImage: String?,
    val relationType: String
)

data class RecommendedAnime(
    val id: Int,
    val title: String,
    val coverImage: String?,
    val averageScore: Int?
)

data class AnimeDetails(
    val id: Int,
    val title: String,
    val description: String?,
    val coverImage: String?,
    val bannerImage: String?,
    val genres: List<String> = emptyList(),
    val averageScore: Int? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val studio: String? = null,
    val format: String? = null,
    val status: String? = null,
    val episodes: Int? = null,
    val relations: List<RelatedAnime> = emptyList(),
    val recommendations: List<RecommendedAnime> = emptyList()
)
