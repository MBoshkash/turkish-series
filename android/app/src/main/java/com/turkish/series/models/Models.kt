package com.turkish.series.models

import com.google.gson.annotations.SerializedName

/**
 * قائمة المسلسلات الرئيسية
 */
data class SeriesListResponse(
    @SerializedName("last_updated") val lastUpdated: String,
    @SerializedName("total") val total: Int,
    @SerializedName("series") val series: List<SeriesSummary>
)

/**
 * ملخص المسلسل (للعرض في القائمة الرئيسية)
 */
data class SeriesSummary(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("original_title") val originalTitle: String?,
    @SerializedName("poster") val poster: String?,
    @SerializedName("year") val year: String?,
    @SerializedName("rating") val rating: Double?,
    @SerializedName("genres") val genres: List<String>?,
    @SerializedName("episodes_count") val episodesCount: Int?,
    @SerializedName("last_episode") val lastEpisode: Int?,
    @SerializedName("last_updated") val lastUpdated: String?,
    @SerializedName("status") val status: String?
)

/**
 * تفاصيل المسلسل الكاملة
 */
data class SeriesDetail(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("original_title") val originalTitle: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("poster") val poster: String?,
    @SerializedName("backdrop") val backdrop: String?,
    @SerializedName("year") val year: String?,
    @SerializedName("country") val country: String?,
    @SerializedName("rating") val rating: Double?,
    @SerializedName("genres") val genres: List<String>?,
    @SerializedName("quality") val quality: String?,
    @SerializedName("age_rating") val ageRating: String?,
    @SerializedName("cast") val cast: List<CastMember>?,
    @SerializedName("total_episodes") val totalEpisodes: Int?,
    @SerializedName("status") val status: String?,
    @SerializedName("last_updated") val lastUpdated: String?,
    @SerializedName("episodes") val episodes: List<EpisodeSummary>?
)

/**
 * عضو الممثلين
 */
data class CastMember(
    @SerializedName("name") val name: String,
    @SerializedName("role") val role: String?
)

/**
 * ملخص الحلقة (في صفحة المسلسل)
 */
data class EpisodeSummary(
    @SerializedName("number") val number: Int,
    @SerializedName("title") val title: String?,
    @SerializedName("date_added") val dateAdded: String?,
    @SerializedName("servers_count") val serversCount: Int?
)

/**
 * تفاصيل الحلقة الكاملة
 */
data class EpisodeDetail(
    @SerializedName("series_id") val seriesId: String,
    @SerializedName("series_title") val seriesTitle: String?,
    @SerializedName("episode_number") val episodeNumber: Int,
    @SerializedName("title") val title: String?,
    @SerializedName("duration") val duration: String?,
    @SerializedName("quality") val quality: String?,
    @SerializedName("file_size") val fileSize: String?,
    @SerializedName("date_added") val dateAdded: String?,
    @SerializedName("last_updated") val lastUpdated: String?,
    @SerializedName("servers") val servers: Servers?,
    @SerializedName("screenshots") val screenshots: List<String>?
)

/**
 * السيرفرات
 */
data class Servers(
    @SerializedName("watch") val watch: List<WatchServer>?,
    @SerializedName("download") val download: List<DownloadServer>?
)

/**
 * سيرفر مشاهدة
 */
data class WatchServer(
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String,  // direct, iframe, webview
    @SerializedName("url") val url: String,
    @SerializedName("quality") val quality: String?,
    @SerializedName("source") val source: String?  // akwam, qissah
)

/**
 * سيرفر تحميل
 */
data class DownloadServer(
    @SerializedName("name") val name: String,
    @SerializedName("url") val url: String,
    @SerializedName("quality") val quality: String?,
    @SerializedName("size") val size: String?,
    @SerializedName("source") val source: String?
)
