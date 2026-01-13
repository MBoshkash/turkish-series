package com.turkish.series.utils

import android.content.Context
import android.util.Log
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
 *
 * ملاحظة: الدومين يتم جلبه من ConfigManager بحيث لو تغير،
 * نقدر نحدثه من GitHub بدون تحديث التطبيق
 */
object AkwamResolver {

    private const val TAG = "AkwamResolver"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val TIMEOUT = 30000

    // الدومين الافتراضي (يتم تحديثه من ConfigManager)
    private var currentDomain = "ak.sv"
    private var referer = "https://ak.sv/"

    /**
     * تحديث الدومين من الـ Config
     */
    suspend fun updateDomainFromConfig(context: Context) {
        try {
            val domain = ConfigManager.getCurrentDomain(context, "akwam")
            if (domain.isNotEmpty()) {
                currentDomain = domain
                referer = "https://$domain/"
                Log.d(TAG, "Updated Akwam domain to: $currentDomain")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update domain from config: ${e.message}")
        }
    }

    /**
     * استبدال الدومين في الرابط بالدومين الحالي
     */
    private fun normalizeUrl(url: String): String {
        // قائمة الدومينات القديمة المحتملة
        val oldDomains = listOf("ak.sv", "akwam.to", "akwam.cx", "akwam.net")
        var normalizedUrl = url

        for (oldDomain in oldDomains) {
            if (normalizedUrl.contains(oldDomain)) {
                normalizedUrl = normalizedUrl.replace(oldDomain, currentDomain)
                break
            }
        }

        return normalizedUrl
    }

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
            // تطبيع الرابط بالدومين الحالي
            val normalizedUrl = normalizeUrl(episodeUrl)
            Log.d(TAG, "Resolving episode URL: $normalizedUrl (original: $episodeUrl)")

            // الخطوة 1: جلب صفحة الحلقة
            val episodePage = fetchPage(normalizedUrl)
            if (episodePage == null) {
                Log.e(TAG, "Failed to fetch episode page")
                return@withContext ResolvedLink(null, null, "فشل تحميل صفحة الحلقة")
            }

            Log.d(TAG, "Episode page fetched, title: ${episodePage.title()}")

            // الخطوة 2: البحث عن رابط المشاهدة
            val watchRedirectUrl = episodePage.select("a[href*='/watch/'], a[href*='go.$currentDomain/watch'], a[href*='go.ak.sv/watch']")
                .firstOrNull()?.attr("href")
            Log.d(TAG, "Watch redirect URL: $watchRedirectUrl")

            // الخطوة 3: البحث عن رابط التحميل
            val downloadRedirectUrl = episodePage.select("a[href*='/download/'], a[href*='go.$currentDomain/link'], a[href*='go.ak.sv/link']")
                .firstOrNull()?.attr("href")
            Log.d(TAG, "Download redirect URL: $downloadRedirectUrl")

            // حل رابط المشاهدة
            val watchUrl = watchRedirectUrl?.let { resolveWatchUrl(normalizeUrl(it)) }
            Log.d(TAG, "Resolved watch URL: $watchUrl")

            // حل رابط التحميل
            val downloadUrl = downloadRedirectUrl?.let { resolveDownloadUrl(normalizeUrl(it)) }
            Log.d(TAG, "Resolved download URL: $downloadUrl")

            ResolvedLink(watchUrl, downloadUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving: ${e.message}", e)
            ResolvedLink(null, null, e.message)
        }
    }

    /**
     * يحل رابط المشاهدة فقط
     */
    suspend fun resolveWatch(episodeUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = normalizeUrl(episodeUrl)
            val episodePage = fetchPage(normalizedUrl) ?: return@withContext null

            val watchRedirectUrl = episodePage.select("a[href*='/watch/'], a[href*='go.$currentDomain/watch'], a[href*='go.ak.sv/watch']")
                .firstOrNull()?.attr("href") ?: return@withContext null

            resolveWatchUrl(normalizeUrl(watchRedirectUrl))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * يحل رابط التحميل فقط
     */
    suspend fun resolveDownload(episodeUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = normalizeUrl(episodeUrl)
            val episodePage = fetchPage(normalizedUrl) ?: return@withContext null

            val downloadRedirectUrl = episodePage.select("a[href*='/download/'], a[href*='go.$currentDomain/link'], a[href*='go.ak.sv/link']")
                .firstOrNull()?.attr("href") ?: return@withContext null

            resolveDownloadUrl(normalizeUrl(downloadRedirectUrl))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * يحل رابط المشاهدة من go.ak.sv إلى الرابط المباشر
     */
    private fun resolveWatchUrl(redirectUrl: String): String? {
        return try {
            // الخطوة 1: جلب صفحة go.ak.sv/watch/xxx
            val goPage = fetchPage(redirectUrl)
            if (goPage == null) return null

            // الخطوة 2: البحث عن رابط ak.sv/watch/xxx
            val watchPageUrl = goPage.select("a[href*='/watch/']")
                .firstOrNull()?.attr("href")
                ?.let { if (it.startsWith("http")) normalizeUrl(it) else "https://$currentDomain$it" }
            if (watchPageUrl == null) return null

            // الخطوة 3: جلب صفحة المشاهدة النهائية
            val watchPage = fetchPage(watchPageUrl)
            if (watchPage == null) return null

            // الخطوة 4: البحث عن رابط الفيديو المباشر
            // أولاً: نبحث في video tag
            val videoElement = watchPage.select("video source[src], video[src]").firstOrNull()
            if (videoElement != null) {
                val src = videoElement.attr("src").ifEmpty { videoElement.attr("data-src") }
                if (src.isNotEmpty()) return src
            }

            // ثانياً: نبحث عن رابط downet.net في الصفحة
            val pageHtml = watchPage.html()
            val directUrlRegex = Regex("""https?://[^"'\s<>]+downet\.net/[^"'\s<>]+\.(mp4|m3u8|mkv)[^"'\s<>]*""")
            val directMatch = directUrlRegex.find(pageHtml)
            if (directMatch != null) return directMatch.value

            // ثالثاً: نبحث عن أي رابط فيديو
            val videoRegex = Regex("""https?://[^"'\s<>]+\.(mp4|m3u8|mkv)[^"'\s<>]*""")
            val videoMatch = videoRegex.find(pageHtml)
            if (videoMatch != null) return videoMatch.value

            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * يحل رابط التحميل من go.ak.sv إلى الرابط المباشر
     */
    private fun resolveDownloadUrl(redirectUrl: String): String? {
        return try {
            // الخطوة 1: جلب صفحة go.ak.sv/link/xxx
            val goPage = fetchPage(redirectUrl)
            if (goPage == null) return null

            // الخطوة 2: البحث عن رابط ak.sv/download/xxx
            val downloadPageUrl = goPage.select("a[href*='/download/']")
                .firstOrNull()?.attr("href")
                ?.let { if (it.startsWith("http")) normalizeUrl(it) else "https://$currentDomain$it" }
            if (downloadPageUrl == null) return null

            // الخطوة 3: جلب صفحة التحميل النهائية
            val downloadPage = fetchPage(downloadPageUrl)
            if (downloadPage == null) return null

            // الخطوة 4: البحث عن رابط التحميل المباشر من downet.net
            val downloadLink = downloadPage.select("a[href*='downet.net/download']").firstOrNull()
            if (downloadLink != null) {
                return downloadLink.attr("href")
            }

            // البحث في HTML
            val pageHtml = downloadPage.html()
            val directUrlRegex = Regex("""https?://[^"'\s<>]+downet\.net/download/[^"'\s<>]+""")
            val match = directUrlRegex.find(pageHtml)
            if (match != null) return match.value

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
            Log.d(TAG, "Fetching page: $url")
            val doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT)
                .followRedirects(true)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .referrer(referer)
                .get()
            Log.d(TAG, "Page fetched successfully: ${doc.title()}")
            doc
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching page $url: ${e.message}")
            null
        }
    }
}
