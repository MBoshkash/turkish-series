package com.turkish.series.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * يحل روابط أكوام للحصول على الرابط المباشر للفيديو
 *
 * المسار:
 * 1. صفحة الحلقة (ak.sv/episode/xxx) -> نجيب رابط المشاهدة
 * 2. صفحة go.ak.sv/watch/xxx -> نجيب رابط ak.sv/watch/xxx
 * 3. صفحة ak.sv/watch/xxx -> نجيب رابط الفيديو المباشر من downet.net
 */
object AkwamResolver {

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val TIMEOUT = 15000

    data class ResolvedLink(
        val watchUrl: String?,
        val downloadUrl: String?,
        val error: String? = null
    )

    /**
     * يحل رابط صفحة الحلقة للحصول على الروابط المباشرة
     */
    suspend fun resolve(episodeUrl: String): ResolvedLink = withContext(Dispatchers.IO) {
        try {
            // الخطوة 1: جلب صفحة الحلقة
            val episodePage = fetchPage(episodeUrl)
                ?: return@withContext ResolvedLink(null, null, "فشل تحميل صفحة الحلقة")

            // الخطوة 2: البحث عن رابط المشاهدة
            val watchRedirectUrl = episodePage.select("a[href*='/watch/'], a[href*='go.ak.sv/watch']")
                .firstOrNull()?.attr("href")

            // الخطوة 3: البحث عن رابط التحميل
            val downloadRedirectUrl = episodePage.select("a[href*='/download/'], a[href*='go.ak.sv/link']")
                .firstOrNull()?.attr("href")

            // حل رابط المشاهدة
            val watchUrl = watchRedirectUrl?.let { resolveWatchUrl(it) }

            // حل رابط التحميل
            val downloadUrl = downloadRedirectUrl?.let { resolveDownloadUrl(it) }

            ResolvedLink(watchUrl, downloadUrl)
        } catch (e: Exception) {
            ResolvedLink(null, null, e.message)
        }
    }

    /**
     * يحل رابط المشاهدة فقط
     */
    suspend fun resolveWatch(episodeUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val episodePage = fetchPage(episodeUrl) ?: return@withContext null

            val watchRedirectUrl = episodePage.select("a[href*='/watch/'], a[href*='go.ak.sv/watch']")
                .firstOrNull()?.attr("href") ?: return@withContext null

            resolveWatchUrl(watchRedirectUrl)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * يحل رابط التحميل فقط
     */
    suspend fun resolveDownload(episodeUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val episodePage = fetchPage(episodeUrl) ?: return@withContext null

            val downloadRedirectUrl = episodePage.select("a[href*='/download/'], a[href*='go.ak.sv/link']")
                .firstOrNull()?.attr("href") ?: return@withContext null

            resolveDownloadUrl(downloadRedirectUrl)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * يحل رابط المشاهدة من go.ak.sv إلى الرابط المباشر
     */
    private suspend fun resolveWatchUrl(redirectUrl: String): String? {
        try {
            // الخطوة 1: جلب صفحة go.ak.sv/watch/xxx
            val goPage = fetchPage(redirectUrl) ?: return null

            // الخطوة 2: البحث عن رابط ak.sv/watch/xxx
            val watchPageUrl = goPage.select("a[href*='/watch/']")
                .firstOrNull()?.attr("href")
                ?.let { if (it.startsWith("http")) it else "https://ak.sv$it" }
                ?: return null

            // الخطوة 3: جلب صفحة المشاهدة النهائية
            val watchPage = fetchPage(watchPageUrl) ?: return null

            // الخطوة 4: البحث عن رابط الفيديو المباشر
            // أولاً: نبحث في video tag
            watchPage.select("video source[src], video[src]").firstOrNull()?.let {
                val src = it.attr("src").ifEmpty { it.attr("data-src") }
                if (src.isNotEmpty()) return src
            }

            // ثانياً: نبحث عن رابط downet.net في الصفحة
            val pageHtml = watchPage.html()
            val directUrlRegex = Regex("""https?://[^"'\s<>]+downet\.net/[^"'\s<>]+\.(mp4|m3u8|mkv)[^"'\s<>]*""")
            directUrlRegex.find(pageHtml)?.value?.let { return it }

            // ثالثاً: نبحث عن أي رابط فيديو
            val videoRegex = Regex("""https?://[^"'\s<>]+\.(mp4|m3u8|mkv)[^"'\s<>]*""")
            videoRegex.find(pageHtml)?.value?.let { return it }

            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * يحل رابط التحميل من go.ak.sv إلى الرابط المباشر
     */
    private suspend fun resolveDownloadUrl(redirectUrl: String): String? {
        try {
            // الخطوة 1: جلب صفحة go.ak.sv/link/xxx
            val goPage = fetchPage(redirectUrl) ?: return null

            // الخطوة 2: البحث عن رابط ak.sv/download/xxx
            val downloadPageUrl = goPage.select("a[href*='/download/']")
                .firstOrNull()?.attr("href")
                ?.let { if (it.startsWith("http")) it else "https://ak.sv$it" }
                ?: return null

            // الخطوة 3: جلب صفحة التحميل النهائية
            val downloadPage = fetchPage(downloadPageUrl) ?: return null

            // الخطوة 4: البحث عن رابط التحميل المباشر من downet.net
            downloadPage.select("a[href*='downet.net/download']").firstOrNull()?.let {
                return it.attr("href")
            }

            // البحث في HTML
            val pageHtml = downloadPage.html()
            val directUrlRegex = Regex("""https?://[^"'\s<>]+downet\.net/download/[^"'\s<>]+""")
            directUrlRegex.find(pageHtml)?.value?.let { return it }

            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * جلب صفحة HTML
     */
    private fun fetchPage(url: String): Document? {
        return try {
            Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT)
                .followRedirects(true)
                .get()
        } catch (e: Exception) {
            null
        }
    }
}
