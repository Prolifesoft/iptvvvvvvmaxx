package com.example.ui.screens

import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import org.json.JSONObject

enum class ImdbTimeFrame(val label: String) {
    ALL("Tümü"),
    THIRTY_DAYS("30 Günlük"),
    SIXTY_DAYS("60 Günlük")
}

data class ImdbUpcomingItem(
    val id: String,
    val title: String,
    val type: String,
    val imdbRating: String,
    val posterUrl: String,
    val releaseDate: String,
    val timeFrame: ImdbTimeFrame,
    val genres: List<String>,
    val overview: String,
    val tmdbId: String = "",
    val trailerKey: String? = null
)

data class MatchFixture(
    val id: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeLogo: String,
    val awayLogo: String,
    val league: String,
    val dateText: String,
    val isLive: Boolean,
    val channelKeywords: List<String>
)

fun fetchTrailerKey(tmdbId: String): String? {
    try {
        val apiKey = "15745c1c46c264d1170db38ea66049e0"
        val url = "https://api.themoviedb.org/3/movie/$tmdbId/videos?api_key=$apiKey"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        if (conn.responseCode == 200) {
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val results = root.optJSONArray("results")
            if (results != null) {
                for (i in 0 until results.length()) {
                    val video = results.optJSONObject(i)
                    if (video != null && video.optString("site").equals("YouTube", ignoreCase = true) && video.optString("type").equals("Trailer", ignoreCase = true)) {
                        return video.optString("key")
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

fun parseTmdbList(jsonString: String, timeFrame: ImdbTimeFrame): List<ImdbUpcomingItem> {
    val list = mutableListOf<ImdbUpcomingItem>()
    try {
        val root = JSONObject(jsonString)
        val results = root.optJSONArray("results") ?: return emptyList()
        val genreMap = mapOf(
            28 to "Aksiyon", 12 to "Macera", 16 to "Animasyon", 35 to "Komedi", 80 to "Suç",
            99 to "Belgesel", 18 to "Dram", 10751 to "Aile", 14 to "Fantezi", 36 to "Tarih",
            27 to "Korku", 10402 to "Müzik", 9648 to "Gizem", 10749 to "Romantik", 878 to "Bilim Kurgu",
            10770 to "TV Film", 53 to "Gerilim", 10752 to "Savaş", 37 to "Vahşi Batı",
            10759 to "Aksiyon & Macera", 10765 to "Bilim Kurgu & Fantezi"
        )
        for (i in 0 until minOf(results.length(), 15)) {
            val item = results.optJSONObject(i) ?: continue
            val posterPath = item.optString("poster_path", "")
            if (posterPath.isEmpty() || posterPath == "null") continue

            val id = item.optLong("id").toString()
            val title = if (item.has("title") && item.optString("title").isNotBlank()) item.optString("title") else item.optString("name", "Bilinmeyen")
            val mediaType = item.optString("media_type", "")
            val typeStr = if (mediaType == "tv" || (!item.has("title") && item.has("name"))) "Dizi" else "Film"
            val voteAvg = item.optDouble("vote_average", 0.0)
            val ratingStr = if (voteAvg > 0) String.format(Locale.US, "%.1f", voteAvg) else "7.5"
            val releaseDate = if (item.has("release_date") && item.optString("release_date").isNotBlank()) {
                item.optString("release_date")
            } else {
                item.optString("first_air_date", "Vizyonda")
            }
            val overview = item.optString("overview", "Açıklama bulunmuyor.")

            val genreIds = item.optJSONArray("genre_ids")
            val genresList = mutableListOf<String>()
            if (genreIds != null) {
                for (g in 0 until genreIds.length()) {
                    val gName = genreMap[genreIds.optInt(g)]
                    if (gName != null) genresList.add(gName)
                }
            }
            if (genresList.isEmpty()) genresList.add(typeStr)

            val trailerKey = fetchTrailerKey(id)
            list.add(
                ImdbUpcomingItem(
                    id = "tmdb_${timeFrame.name}_$id",
                    title = title,
                    type = typeStr,
                    imdbRating = ratingStr,
                    posterUrl = "https://image.tmdb.org/t/p/w500$posterPath",
                    releaseDate = releaseDate,
                    timeFrame = timeFrame,
                    genres = genresList,
                    overview = if (overview.isNotBlank()) overview else "$title TMDB detayları.",
                    tmdbId = id,
                    trailerKey = trailerKey
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun parseEspnFixtures(jsonString: String): List<MatchFixture> {
    val list = mutableListOf<MatchFixture>()
    try {
        val root = JSONObject(jsonString)
        val events = root.optJSONArray("events") ?: return emptyList()
        val targetTeams = listOf("Fenerbahce", "Galatasaray", "Besiktas", "Trabzonspor", "Fenerbahçe", "Beşiktaş")

        for (i in 0 until events.length()) {
            val event = events.optJSONObject(i) ?: continue
            val competitions = event.optJSONArray("competitions") ?: continue
            if (competitions.length() == 0) continue
            val comp = competitions.optJSONObject(0) ?: continue
            val competitors = comp.optJSONArray("competitors") ?: continue
            if (competitors.length() < 2) continue

            var homeTeam = ""
            var awayTeam = ""
            var homeLogo = ""
            var awayLogo = ""
            var homeScore = ""
            var awayScore = ""

            for (j in 0 until competitors.length()) {
                val c = competitors.optJSONObject(j) ?: continue
                val isHome = c.optString("homeAway") == "home"
                val team = c.optJSONObject("team") ?: continue
                val name = team.optString("name")
                val logo = team.optString("logo")
                val score = c.optString("score", "")

                if (isHome) {
                    homeTeam = name
                    homeLogo = logo
                    homeScore = score
                } else {
                    awayTeam = name
                    awayLogo = logo
                    awayScore = score
                }
            }

            val isTargetMatch = targetTeams.any { homeTeam.contains(it, ignoreCase = true) || awayTeam.contains(it, ignoreCase = true) }
            if (!isTargetMatch) continue

            val dateStr = event.optString("date")
            var dateText = dateStr
            try {
                val sdfIn = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US)
                sdfIn.timeZone = TimeZone.getTimeZone("UTC")
                val dateObj = sdfIn.parse(dateStr)
                if (dateObj != null) {
                    val sdfOut = SimpleDateFormat("dd MMM HH:mm", Locale("tr"))
                    dateText = sdfOut.format(dateObj)
                }
            } catch (e: Exception) {}

            val status = event.optJSONObject("status")
            val type = status?.optJSONObject("type")
            val state = type?.optString("state")
            val isLive = state == "in"
            val isCompleted = state == "post"

            if (isCompleted) continue

            var displayDate = dateText
            if (isLive) {
                val detail = type?.optString("detail")
                displayDate = if (!detail.isNullOrEmpty()) "CANLI - $detail" else "CANLI"
            }

            val displayLeague = "Trendyol Süper Lig"

            list.add(
                MatchFixture(
                    id = event.optString("id", UUID.randomUUID().toString()),
                    homeTeam = homeTeam,
                    awayTeam = awayTeam,
                    homeLogo = homeLogo,
                    awayLogo = awayLogo,
                    league = displayLeague,
                    dateText = displayDate,
                    isLive = isLive,
                    channelKeywords = listOf("BeIN Sports 1", "BeIN 1", "bein", "Spor")
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}
