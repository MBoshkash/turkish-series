package com.turkish.series.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * يحل روابط قصة عشق (3isk) للحصول على روابط المشاهدة
 *
 * الآلية:
 * 1. صفحة الحلقة -> نضيف /see/ للمشاهدة
 * 2. صفحة المشاهدة -> نجيب السيرفرات من data-post و data-nume
 * 3. نبني رابط الـ embed: /embed/{type}/{post_id}/{server_num}/
 * 4. صفحة الـ embed -> نجيب رابط الـ iframe النهائي
 *
 * ملاحظة: قصة عشق يرجع روابط iframe لسيرفرات خارجية (miravd, etc.)
 * مش روابط مباشرة زي أكوام، فلازم نفتحهم في WebView
 */
object QissaResolver {

    private const val TAG = "QissaResolver"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val TIMEOUT = 30000

    // الدومين الافتراضي (يتم تحديثه من ConfigManager)
    private var currentDomain = "aa.3ick.net"
    private var referer = "https://aa.3ick.net/"

    /**
     * تحديث الدومين من الـ Config
     */
    suspend fun updateDomainFromConfig(context: Context) {
        try {
            val domain = ConfigManager.getCurrentDomain(context, "3isk")
            if (domain.isNotEmpty()) {
                currentDomain = domain
                referer = "https://$domain/"
                Log.d(TAG, "Updated 3isk domain to: $currentDomain")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update domain from config: ${e.message}")
        }
    }

    /**
     * استبدال الدومين في الرابط بالدومين الحالي
     */
    private fun normalizeUrl(url: String): String {
        val oldDomains = listOf("aa.3ick.net", "3esk.onl", "3isk.tv", "3isk.co")
        var normalizedUrl = url

        for (oldDomain in oldDomains) {
            if (normalizedUrl.contains(oldDomain)) {
                normalizedUrl = normalizedUrl.replace(oldDomain, currentDomain)
                break
            }
        }

        return normalizedUrl
    }

    /**
     * معلومات سيرفر واحد
     */
    data class ServerInfo(
        val name: String,
        val embedUrl: String,
        val streamUrl: String? = null, // رابط الـ iframe النهائي
        val serverNum: String,
        val postId: String
    )

    /**
     * نتيجة الـ resolve
     */
    data class ResolvedResult(
        val servers: List<ServerInfo> = emptyList(),
        val error: String? = null
    )

    /**
     * يحل رابط صفحة الحلقة للحصول على كل السيرفرات
     */
    suspend fun resolve(episodeUrl: String): ResolvedResult = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = normalizeUrl(episodeUrl)
            Log.d(TAG, "Resolving episode URL: $normalizedUrl")

            // بناء رابط صفحة المشاهدة
            val watchUrl = if (normalizedUrl.endsWith("/")) {
                "${normalizedUrl}see/"
            } else {
                "$normalizedUrl/see/"
            }

            Log.d(TAG, "Watch URL: $watchUrl")

            // جلب صفحة المشاهدة
            val watchPage = fetchPage(watchUrl)
            if (watchPage == null) {
                Log.e(TAG, "Failed to fetch watch page")
                return@withContext ResolvedResult(error = "فشل تحميل صفحة المشاهدة")
            }

            // استخراج السيرفرات
            val servers = extractServers(watchPage)
            Log.d(TAG, "Found ${servers.size} servers")

            if (servers.isEmpty()) {
                return@withContext ResolvedResult(error = "لم يتم العثور على سيرفرات")
            }

            // جلب روابط الـ stream لكل سيرفر
            val resolvedServers = servers.map { server ->
                val streamUrl = resolveEmbedUrl(server.embedUrl)
                server.copy(streamUrl = streamUrl)
            }

            ResolvedResult(servers = resolvedServers)
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving: ${e.message}", e)
            ResolvedResult(error = e.message)
        }
    }

    /**
     * يحل رابط أول سيرفر فقط (للاستخدام السريع)
     */
    suspend fun resolveFirstServer(episodeUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val result = resolve(episodeUrl)
            // نرجع أول سيرفر عنده streamUrl
            result.servers.firstOrNull { it.streamUrl != null }?.streamUrl
                ?: result.servers.firstOrNull()?.embedUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving first server: ${e.message}")
            null
        }
    }

    /**
     * استخراج السيرفرات من صفحة المشاهدة
     */
    private fun extractServers(watchPage: Document): List<ServerInfo> {
        val servers = mutableListOf<ServerInfo>()

        try {
            // البحث عن تابات السيرفرات
            val serverTabs = watchPage.select("#player_servers li.server_nth")
            Log.d(TAG, "Found ${serverTabs.size} server tabs")

            for (tab in serverTabs) {
                val serverName = tab.selectFirst(".server_name")?.text()?.trim() ?: "سيرفر"
                val postId = tab.attr("data-post")
                val serverNum = tab.attr("data-nume")
                val dataType = tab.attr("data-type")

                if (postId.isNotEmpty() && serverNum.isNotEmpty()) {
                    // بناء رابط الـ embed
                    // type: 1 للمسلسلات (tv), 2 للأفلام (movie)
                    val typeNum = if (dataType == "tv") "1" else "2"
                    val embedUrl = "https://$currentDomain/embed/$typeNum/$postId/$serverNum/"

                    servers.add(
                        ServerInfo(
                            name = serverName,
                            embedUrl = embedUrl,
                            serverNum = serverNum,
                            postId = postId
                        )
                    )

                    Log.d(TAG, "Server: $serverName -> $embedUrl")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting servers: ${e.message}")
        }

        return servers
    }

    /**
     * يحل رابط الـ embed للحصول على رابط الـ stream النهائي
     */
    private fun resolveEmbedUrl(embedUrl: String): String? {
        return try {
            Log.d(TAG, "Resolving embed URL: $embedUrl")

            val embedPage = fetchPage(embedUrl)
            if (embedPage == null) {
                Log.e(TAG, "Failed to fetch embed page")
                return null
            }

            // البحث عن الـ iframe
            val iframe = embedPage.selectFirst("iframe")
            val streamUrl = iframe?.attr("src")

            if (streamUrl.isNullOrEmpty()) {
                Log.d(TAG, "No iframe found in embed page")
                return null
            }

            // تأكد إن الرابط كامل
            val fullStreamUrl = if (streamUrl.startsWith("//")) {
                "https:$streamUrl"
            } else if (!streamUrl.startsWith("http")) {
                "https://$currentDomain$streamUrl"
            } else {
                streamUrl
            }

            Log.d(TAG, "Stream URL: $fullStreamUrl")
            fullStreamUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving embed URL: ${e.message}")
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
                .header("Accept-Language", "ar,en-US;q=0.7,en;q=0.3")
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
