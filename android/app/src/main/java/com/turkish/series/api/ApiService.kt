package com.turkish.series.api

import com.turkish.series.models.AppConfig
import com.turkish.series.models.EpisodeDetail
import com.turkish.series.models.SeriesDetail
import com.turkish.series.models.SeriesListResponse
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * API Service للتواصل مع GitHub Pages JSON API
 */
interface ApiService {

    /**
     * جلب إعدادات التطبيق (المصادر، الدومينات، الرسائل)
     */
    @GET("data/app_config.json")
    suspend fun getAppConfig(): AppConfig

    /**
     * جلب قائمة كل المسلسلات
     */
    @GET("data/series.json")
    suspend fun getSeriesList(): SeriesListResponse

    /**
     * جلب تفاصيل مسلسل معين
     */
    @GET("data/series/{id}.json")
    suspend fun getSeriesDetail(@Path("id") id: String): SeriesDetail

    /**
     * جلب تفاصيل حلقة معينة
     * الملف: {series_id}_{episode_number}.json
     */
    @GET("data/episodes/{filename}.json")
    suspend fun getEpisodeDetail(@Path("filename") filename: String): EpisodeDetail
}
