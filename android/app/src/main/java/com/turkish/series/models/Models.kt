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
    @SerializedName("country") val country: String?,
    @SerializedName("language") val language: String?,
    @SerializedName("rating") val rating: Double?,
    @SerializedName("genres") val genres: List<String>?,
    @SerializedName("tags") val tags: List<String>?,
    @SerializedName("quality") val quality: String?,
    @SerializedName("duration") val duration: String?,
    @SerializedName("episodes_count") val episodesCount: Int?,
    @SerializedName("last_episode") val lastEpisode: Int?,
    @SerializedName("last_episode_date") val lastEpisodeDate: String?,
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
    @SerializedName("language") val language: String?,
    @SerializedName("rating") val rating: Double?,
    @SerializedName("genres") val genres: List<String>?,
    @SerializedName("tags") val tags: List<String>?,
    @SerializedName("quality") val quality: String?,
    @SerializedName("duration") val duration: String?,
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

// ============================================
// App Config Models - إعدادات التطبيق من GitHub
// ============================================

/**
 * إعدادات التطبيق الكاملة
 */
data class AppConfig(
    @SerializedName("config_version") val configVersion: Int,
    @SerializedName("last_updated") val lastUpdated: String,
    @SerializedName("sources") val sources: Map<String, SourceConfig>,
    @SerializedName("app") val app: AppInfo,
    @SerializedName("messages") val messages: Messages,
    @SerializedName("settings") val settings: AppSettings,
    @SerializedName("api") val api: ApiConfig
)

/**
 * إعدادات مصدر واحد (أكوام، عرب سيد، إلخ)
 */
data class SourceConfig(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("name_en") val nameEn: String?,
    @SerializedName("enabled") val enabled: Boolean,
    @SerializedName("priority") val priority: Int,
    @SerializedName("icon") val icon: String?,
    @SerializedName("domains") val domains: List<String>,
    @SerializedName("current_domain") val currentDomain: String,
    @SerializedName("patterns") val patterns: Map<String, String>,
    @SerializedName("headers") val headers: Map<String, String>?,
    @SerializedName("resolver") val resolver: ResolverConfig?,
    @SerializedName("qualities") val qualities: List<String>?,
    @SerializedName("default_quality") val defaultQuality: String?,
    @SerializedName("supports_download") val supportsDownload: Boolean?,
    @SerializedName("supports_watch") val supportsWatch: Boolean?
)

/**
 * إعدادات الـ Resolver لكل مصدر
 */
data class ResolverConfig(
    @SerializedName("version") val version: Int,
    @SerializedName("type") val type: String,  // redirect, iframe, webview, direct
    @SerializedName("needs_webview") val needsWebview: Boolean?,
    @SerializedName("bypass_cloudflare") val bypassCloudflare: Boolean?
)

/**
 * معلومات التطبيق والتحديثات
 */
data class AppInfo(
    @SerializedName("min_version_code") val minVersionCode: Int,
    @SerializedName("latest_version_code") val latestVersionCode: Int,
    @SerializedName("latest_version_name") val latestVersionName: String,
    @SerializedName("update_url") val updateUrl: String,
    @SerializedName("force_update") val forceUpdate: Boolean
)

/**
 * رسائل للمستخدمين
 */
data class Messages(
    @SerializedName("maintenance") val maintenance: String?,
    @SerializedName("announcement") val announcement: String?,
    @SerializedName("news") val news: List<String>?
)

/**
 * إعدادات عامة
 */
data class AppSettings(
    @SerializedName("default_source") val defaultSource: String,
    @SerializedName("fallback_sources") val fallbackSources: List<String>?,
    @SerializedName("cache_duration_hours") val cacheDurationHours: Int?,
    @SerializedName("retry_attempts") val retryAttempts: Int?,
    @SerializedName("timeout_seconds") val timeoutSeconds: Int?
)

/**
 * إعدادات API
 */
data class ApiConfig(
    @SerializedName("base_url") val baseUrl: String,
    @SerializedName("endpoints") val endpoints: Map<String, String>
)
