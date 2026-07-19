package com.darren.anitrackpulse.network

/** AniList MediaFormat filter values exposed in the search UI. `apiValue == null` means no filter. */
enum class SearchFormat(val label: String, val apiValue: String?) {
    ANY("Any", null),
    TV("TV", "TV"),
    MOVIE("Movie", "MOVIE"),
    OVA("OVA", "OVA"),
    ONA("ONA", "ONA")
}

/** AniList MediaStatus filter values exposed in the search UI. `apiValue == null` means no filter. */
enum class SearchStatusFilter(val label: String, val apiValue: String?) {
    ANY("Any", null),
    RELEASING("Releasing", "RELEASING"),
    FINISHED("Finished", "FINISHED"),
    NOT_YET_RELEASED("Not Yet Aired", "NOT_YET_RELEASED")
}

/** AniList MediaSort options exposed in the search UI. RELEVANCE (AniList's SEARCH_MATCH) is the default so typing a specific title still surfaces the best text match first, rather than the globally most popular title. */
enum class SearchSort(val label: String, val apiValue: String) {
    RELEVANCE("Relevance", "SEARCH_MATCH"),
    POPULARITY("Popularity", "POPULARITY_DESC"),
    SCORE("Score", "SCORE_DESC"),
    TRENDING("Trending", "TRENDING_DESC")
}

/** AniList MediaSeason values, plus current-season derivation from a calendar month. */
enum class AnimeSeason(val apiValue: String, val label: String) {
    WINTER("WINTER", "Winter"),
    SPRING("SPRING", "Spring"),
    SUMMER("SUMMER", "Summer"),
    FALL("FALL", "Fall");

    companion object {
        fun fromMonth(month: Int): AnimeSeason = when (month) {
            in 1..3 -> WINTER
            in 4..6 -> SPRING
            in 7..9 -> SUMMER
            else -> FALL
        }
    }
}
