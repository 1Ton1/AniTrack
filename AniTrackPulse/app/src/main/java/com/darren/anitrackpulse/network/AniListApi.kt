package com.darren.anitrackpulse.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AniListApi {

    private val endpoint = "https://graphql.anilist.co"

    suspend fun searchAnime(
        query: String,
        format: SearchFormat = SearchFormat.ANY,
        status: SearchStatusFilter = SearchStatusFilter.ANY,
        sort: SearchSort = SearchSort.POPULARITY
    ): List<AnimeSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val gql = """
            query(${'$'}search: String, ${'$'}format: MediaFormat, ${'$'}status: MediaStatus, ${'$'}sort: [MediaSort]) {
              Page(page: 1, perPage: 25) {
                media(search: ${'$'}search, type: ANIME, format: ${'$'}format, status: ${'$'}status, sort: ${'$'}sort) {
                  id
                  title { romaji english }
                  episodes
                  status
                  coverImage { large }
                  nextAiringEpisode { episode airingAt }
                }
              }
            }
        """.trimIndent()
        val variables = JSONObject()
            .put("search", query)
            .put("sort", JSONArray().put(sort.apiValue))
        format.apiValue?.let { variables.put("format", it) }
        status.apiValue?.let { variables.put("status", it) }
        parseMediaPage(runQuery(gql, variables))
    }

    suspend fun getSeasonalPopular(season: AnimeSeason, seasonYear: Int): List<AnimeSearchResult> = withContext(Dispatchers.IO) {
        val gql = """
            query(${'$'}season: MediaSeason, ${'$'}seasonYear: Int) {
              Page(page: 1, perPage: 25) {
                media(season: ${'$'}season, seasonYear: ${'$'}seasonYear, type: ANIME, sort: POPULARITY_DESC) {
                  id
                  title { romaji english }
                  episodes
                  status
                  coverImage { large }
                  nextAiringEpisode { episode airingAt }
                }
              }
            }
        """.trimIndent()
        val variables = JSONObject()
            .put("season", season.apiValue)
            .put("seasonYear", seasonYear)
        parseMediaPage(runQuery(gql, variables))
    }

    private fun parseMediaPage(data: JSONObject?): List<AnimeSearchResult> {
        val mediaArray = data?.optJSONObject("Page")?.optJSONArray("media") ?: return emptyList()
        return (0 until mediaArray.length()).mapNotNull { i ->
            mediaArray.optJSONObject(i)?.let { parseSearchResult(it) }
        }
    }

    suspend fun getAnimeById(id: Int): AnimeSearchResult? = withContext(Dispatchers.IO) {
        val gql = """
            query(${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                id
                title { romaji english }
                episodes
                status
                coverImage { large }
                nextAiringEpisode { episode airingAt }
              }
            }
        """.trimIndent()
        val variables = JSONObject().put("id", id)
        val data = runQuery(gql, variables) ?: return@withContext null
        data.optJSONObject("Media")?.let { parseSearchResult(it) }
    }

    suspend fun getAnimeDetails(id: Int): AnimeDetails? = withContext(Dispatchers.IO) {
        val gql = """
            query(${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                id
                title { romaji english }
                description(asHtml: false)
                genres
                averageScore
                season
                seasonYear
                format
                status
                episodes
                coverImage { large }
                bannerImage
                studios(isMain: true) { nodes { name } }
                relations {
                  edges {
                    relationType
                    node { id title { romaji english } coverImage { large } type }
                  }
                }
                recommendations(sort: RATING_DESC, perPage: 15) {
                  edges {
                    node {
                      mediaRecommendation {
                        id
                        title { romaji english }
                        coverImage { large }
                        averageScore
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()
        val variables = JSONObject().put("id", id)
        val data = runQuery(gql, variables) ?: return@withContext null
        val media = data.optJSONObject("Media") ?: return@withContext null
        parseDetails(media)
    }

    private fun parseSearchResult(media: JSONObject): AnimeSearchResult? {
        val id = media.optInt("id", -1)
        if (id < 0) return null
        val nextAiring = media.optJSONObject("nextAiringEpisode")
        return AnimeSearchResult(
            id = id,
            title = extractTitle(media),
            coverImage = media.optJSONObject("coverImage")?.optStringOrNull("large"),
            totalEpisodes = media.optIntOrNull("episodes"),
            nextEpisode = nextAiring?.optIntOrNull("episode"),
            nextAiringAt = nextAiring?.optLongOrNull("airingAt"),
            status = media.optStringOrNull("status")
        )
    }

    private fun parseDetails(media: JSONObject): AnimeDetails {
        val relations = mutableListOf<RelatedAnime>()
        media.optJSONObject("relations")?.optJSONArray("edges")?.let { edges ->
            for (i in 0 until edges.length()) {
                val edge = edges.optJSONObject(i) ?: continue
                val node = edge.optJSONObject("node") ?: continue
                if (!node.optString("type").equals("ANIME", ignoreCase = true)) continue
                val relId = node.optInt("id", -1)
                if (relId < 0) continue
                relations += RelatedAnime(
                    id = relId,
                    title = extractTitle(node),
                    coverImage = node.optJSONObject("coverImage")?.optStringOrNull("large"),
                    relationType = formatEnum(edge.optString("relationType"))
                )
            }
        }

        val recommendations = mutableListOf<RecommendedAnime>()
        media.optJSONObject("recommendations")?.optJSONArray("edges")?.let { edges ->
            for (i in 0 until edges.length()) {
                val node = edges.optJSONObject(i)?.optJSONObject("node") ?: continue
                val rec = node.optJSONObject("mediaRecommendation") ?: continue
                val recId = rec.optInt("id", -1)
                if (recId < 0) continue
                recommendations += RecommendedAnime(
                    id = recId,
                    title = extractTitle(rec),
                    coverImage = rec.optJSONObject("coverImage")?.optStringOrNull("large"),
                    averageScore = rec.optIntOrNull("averageScore")
                )
            }
        }

        val genres = media.optJSONArray("genres")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        } ?: emptyList()

        val studio = media.optJSONObject("studios")?.optJSONArray("nodes")
            ?.let { nodes -> if (nodes.length() > 0) nodes.optJSONObject(0)?.optStringOrNull("name") else null }

        return AnimeDetails(
            id = media.optInt("id"),
            title = extractTitle(media),
            description = stripHtml(media.optStringOrNull("description")),
            coverImage = media.optJSONObject("coverImage")?.optStringOrNull("large"),
            bannerImage = media.optStringOrNull("bannerImage"),
            genres = genres,
            averageScore = media.optIntOrNull("averageScore"),
            season = media.optStringOrNull("season")?.let { formatEnum(it) },
            seasonYear = media.optIntOrNull("seasonYear"),
            studio = studio,
            format = media.optStringOrNull("format")?.let { formatEnum(it) },
            status = media.optStringOrNull("status")?.let { formatEnum(it) },
            episodes = media.optIntOrNull("episodes"),
            relations = relations,
            recommendations = recommendations
        )
    }

    private fun extractTitle(media: JSONObject): String {
        val title = media.optJSONObject("title")
        return title?.optStringOrNull("english")
            ?: title?.optStringOrNull("romaji")
            ?: "Untitled"
    }

    private fun runQuery(query: String, variables: JSONObject): JSONObject? {
        var connection: HttpURLConnection? = null
        return try {
            val body = JSONObject().put("query", query).put("variables", variables).toString()
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() } ?: return null
            if (code !in 200..299) return null
            JSONObject(response).optJSONObject("data")
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun stripHtml(input: String?): String? {
        if (input.isNullOrBlank()) return null
        return input
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun formatEnum(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.split("_").joinToString(" ") { part ->
            part.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}

private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val value = optString(key)
    return value.takeIf { it.isNotBlank() && it != "null" }
}

private fun JSONObject.optIntOrNull(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return optInt(key)
}

private fun JSONObject.optLongOrNull(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return optLong(key)
}
