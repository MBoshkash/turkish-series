package com.turkish.series.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.turkish.series.api.ApiClient
import com.turkish.series.models.AppConfig
import com.turkish.series.models.SourceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ConfigManager - يدير إعدادات التطبيق من GitHub
 *
 * الفوائد:
 * 1. تغيير دومينات المصادر بدون تحديث التطبيق
 * 2. تفعيل/تعطيل مصادر
 * 3. إرسال رسائل للمستخدمين
 * 4. التحكم في التحديثات الإجبارية
 */
object ConfigManager {

    private const val TAG = "ConfigManager"
    private const val PREFS_NAME = "app_config_prefs"
    private const val KEY_CONFIG_JSON = "config_json"
    private const val KEY_LAST_FETCH = "last_fetch_time"
    private const val CACHE_DURATION_MS = 6 * 60 * 60 * 1000L // 6 ساعات

    private var cachedConfig: AppConfig? = null
    private val gson = Gson()

    // Default config في حالة فشل التحميل
    private val defaultConfig = AppConfig(
        configVersion = 1,
        lastUpdated = "",
        sources = mapOf(
            "akwam" to SourceConfig(
                id = "akwam",
                name = "أكوام",
                nameEn = "Akwam",
                enabled = true,
                priority = 1,
                icon = null,
                domains = listOf("ak.sv", "akwam.to"),
                currentDomain = "ak.sv",
                patterns = mapOf(
                    "base" to "https://{domain}",
                    "series" to "/series/{id}/{slug}",
                    "episode" to "/episode/{id}/{slug}/الحلقة-{ep}"
                ),
                headers = mapOf("Referer" to "https://ak.sv/"),
                resolver = null,
                qualities = listOf("1080p", "720p", "480p"),
                defaultQuality = "720p",
                supportsDownload = true,
                supportsWatch = true
            )
        ),
        app = com.turkish.series.models.AppInfo(
            minVersionCode = 1,
            latestVersionCode = 1,
            latestVersionName = "1.0.0",
            updateUrl = "",
            forceUpdate = false
        ),
        messages = com.turkish.series.models.Messages(null, null, null),
        settings = com.turkish.series.models.AppSettings(
            defaultSource = "akwam",
            fallbackSources = listOf(),
            cacheDurationHours = 6,
            retryAttempts = 3,
            timeoutSeconds = 30
        ),
        api = com.turkish.series.models.ApiConfig(
            baseUrl = "https://mboshkash.github.io/turkish-series",
            endpoints = mapOf()
        )
    )

    /**
     * جلب الإعدادات (من الكاش أو من الإنترنت)
     */
    suspend fun getConfig(context: Context, forceRefresh: Boolean = false): AppConfig {
        return withContext(Dispatchers.IO) {
            // لو عندنا كاش في الذاكرة ومش محتاجين refresh
            if (cachedConfig != null && !forceRefresh && !isCacheExpired(context)) {
                Log.d(TAG, "Using memory cached config")
                return@withContext cachedConfig!!
            }

            // نحاول نجيب من الإنترنت
            try {
                val config = ApiClient.apiService.getAppConfig()
                Log.d(TAG, "Fetched config from server, version: ${config.configVersion}")

                // نحفظ في الكاش
                cachedConfig = config
                saveConfigToPrefs(context, config)

                return@withContext config
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch config from server: ${e.message}")

                // نحاول نجيب من الـ SharedPreferences
                val savedConfig = loadConfigFromPrefs(context)
                if (savedConfig != null) {
                    Log.d(TAG, "Using saved config from preferences")
                    cachedConfig = savedConfig
                    return@withContext savedConfig
                }

                // نرجع الـ default
                Log.d(TAG, "Using default config")
                return@withContext defaultConfig
            }
        }
    }

    /**
     * جلب إعدادات مصدر معين
     */
    suspend fun getSourceConfig(context: Context, sourceId: String): SourceConfig? {
        val config = getConfig(context)
        return config.sources[sourceId]
    }

    /**
     * جلب الدومين الحالي لمصدر معين
     */
    suspend fun getCurrentDomain(context: Context, sourceId: String): String {
        val sourceConfig = getSourceConfig(context, sourceId)
        return sourceConfig?.currentDomain ?: when(sourceId) {
            "akwam" -> "ak.sv"
            "arabseed" -> "arabseed.ink"
            else -> ""
        }
    }

    /**
     * جلب كل المصادر المفعلة
     */
    suspend fun getEnabledSources(context: Context): List<SourceConfig> {
        val config = getConfig(context)
        return config.sources.values
            .filter { it.enabled }
            .sortedBy { it.priority }
    }

    /**
     * بناء URL كامل لمصدر معين
     */
    suspend fun buildUrl(
        context: Context,
        sourceId: String,
        patternKey: String,
        params: Map<String, String> = emptyMap()
    ): String? {
        val sourceConfig = getSourceConfig(context, sourceId) ?: return null

        val pattern = sourceConfig.patterns[patternKey] ?: return null
        val domain = sourceConfig.currentDomain

        var url = pattern.replace("{domain}", domain)
        params.forEach { (key, value) ->
            url = url.replace("{$key}", value)
        }

        // لو الـ pattern مش بيبدأ بـ http، نضيف الـ base
        if (!url.startsWith("http")) {
            url = "https://$domain$url"
        }

        return url
    }

    /**
     * جلب Headers لمصدر معين
     */
    suspend fun getHeaders(context: Context, sourceId: String): Map<String, String> {
        val sourceConfig = getSourceConfig(context, sourceId)
        return sourceConfig?.headers ?: emptyMap()
    }

    /**
     * التحقق من وجود رسالة صيانة
     */
    suspend fun getMaintenanceMessage(context: Context): String? {
        val config = getConfig(context)
        return config.messages.maintenance
    }

    /**
     * التحقق من وجود إعلان
     */
    suspend fun getAnnouncement(context: Context): String? {
        val config = getConfig(context)
        return config.messages.announcement
    }

    /**
     * التحقق من الحاجة لتحديث إجباري
     */
    suspend fun checkForceUpdate(context: Context, currentVersionCode: Int): Boolean {
        val config = getConfig(context)
        return config.app.forceUpdate && currentVersionCode < config.app.minVersionCode
    }

    /**
     * مسح الكاش
     */
    fun clearCache(context: Context) {
        cachedConfig = null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.d(TAG, "Config cache cleared")
    }

    // ============================================
    // Private Helper Methods
    // ============================================

    private fun isCacheExpired(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastFetch = prefs.getLong(KEY_LAST_FETCH, 0)
        val now = System.currentTimeMillis()
        return (now - lastFetch) > CACHE_DURATION_MS
    }

    private fun saveConfigToPrefs(context: Context, config: AppConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(config)
        prefs.edit()
            .putString(KEY_CONFIG_JSON, json)
            .putLong(KEY_LAST_FETCH, System.currentTimeMillis())
            .apply()
    }

    private fun loadConfigFromPrefs(context: Context): AppConfig? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CONFIG_JSON, null) ?: return null
        return try {
            gson.fromJson(json, AppConfig::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse saved config: ${e.message}")
            null
        }
    }
}
