package com.turkish.series

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.google.android.material.tabs.TabLayout
import com.turkish.series.api.ApiClient
import com.turkish.series.databinding.ActivityEpisodePlayerBinding
import com.turkish.series.models.DownloadServer
import com.turkish.series.models.EpisodeDetail
import com.turkish.series.models.WatchServer
import com.turkish.series.utils.AkwamResolver
import com.turkish.series.utils.TDMHelper
import com.turkish.series.utils.UnsafeOkHttpClient
import kotlinx.coroutines.launch

/**
 * صفحة مشاهدة الحلقة
 */
class EpisodePlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SERIES_ID = "series_id"
        const val EXTRA_SERIES_TITLE = "series_title"
        const val EXTRA_EPISODE_NUMBER = "episode_number"
    }

    private lateinit var binding: ActivityEpisodePlayerBinding

    private var exoPlayer: ExoPlayer? = null
    private var episodeDetail: EpisodeDetail? = null
    private var currentServer: WatchServer? = null
    private var seriesTitle: String = ""

    // الرابط المباشر للحلقة الحالية (بعد الـ resolve)
    private var resolvedWatchUrl: String? = null
    private var resolvedDownloadUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEpisodePlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val seriesId = intent.getStringExtra(EXTRA_SERIES_ID) ?: ""
        seriesTitle = intent.getStringExtra(EXTRA_SERIES_TITLE) ?: ""
        val episodeNumber = intent.getIntExtra(EXTRA_EPISODE_NUMBER, 1)

        setupToolbar(seriesTitle, episodeNumber)
        setupButtons()

        loadEpisodeDetail(seriesId, episodeNumber)
    }

    private fun setupToolbar(seriesTitle: String, episodeNumber: Int) {
        binding.toolbar.title = "الحلقة $episodeNumber"
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupButtons() {
        binding.downloadButton.setOnClickListener {
            downloadCurrentEpisode()
        }

        binding.externalPlayerButton.setOnClickListener {
            openInExternalPlayer()
        }
    }

    private fun openInExternalPlayer() {
        val url = resolvedWatchUrl ?: currentServer?.url ?: return

        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), "video/*")
            }
            startActivity(Intent.createChooser(intent, "اختر مشغل الفيديو"))
        } catch (e: Exception) {
            showToast("لا يوجد مشغل فيديو متاح")
        }
    }

    private fun loadEpisodeDetail(seriesId: String, episodeNumber: Int) {
        showLoading(true, "جاري تحميل بيانات الحلقة...")

        lifecycleScope.launch {
            try {
                // Format: seriesId_episodeNumber (e.g., 5127_13)
                val filename = "${seriesId}_${String.format("%02d", episodeNumber)}"
                episodeDetail = ApiClient.apiService.getEpisodeDetail(filename)
                episodeDetail?.let { displayEpisodeDetail(it, episodeNumber) }
            } catch (e: Exception) {
                showError(e.message)
            }
        }
    }

    private fun displayEpisodeDetail(episode: EpisodeDetail, episodeNumber: Int) {
        // Episode number badge
        binding.episodeNumberBadge.text = episodeNumber.toString()

        // Title
        binding.episodeTitleText.text = episode.title ?: "الحلقة $episodeNumber"

        // Series title
        binding.seriesTitleText.text = seriesTitle

        // Setup servers tabs
        setupServersTabs(episode)

        // Auto-play first server
        episode.servers?.watch?.firstOrNull()?.let { server ->
            playServer(server)
        }
    }

    private fun setupServersTabs(episode: EpisodeDetail) {
        binding.serversTabLayout.removeAllTabs()

        episode.servers?.watch?.forEachIndexed { index, server ->
            val tab = binding.serversTabLayout.newTab().apply {
                text = server.name
                tag = server
            }
            binding.serversTabLayout.addTab(tab)
        }

        binding.serversTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val server = tab?.tag as? WatchServer
                server?.let { playServer(it) }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun playServer(server: WatchServer) {
        currentServer = server
        showLoading(true, "جاري تحميل الفيديو...")

        // Reset resolved URLs
        resolvedWatchUrl = null
        resolvedDownloadUrl = null

        // Log للتشخيص
        android.util.Log.d("EpisodePlayer", "playServer: name=${server.name}, type=${server.type}, source=${server.source}, url=${server.url}")

        // حفظ الرابط المباشر لو موجود (للتحميل لاحقاً)
        if (!server.directUrl.isNullOrEmpty()) {
            resolvedWatchUrl = server.directUrl
            android.util.Log.d("EpisodePlayer", "directUrl saved: ${server.directUrl}")
        }

        // التعامل حسب النوع أولاً (الأهم)
        when (server.type) {
            // روابط مباشرة - ExoPlayer
            "direct" -> {
                android.util.Log.d("EpisodePlayer", "Playing direct with ExoPlayer")
                playWithExoPlayer(server.url)
            }
            // أكوام - نحتاج resolve
            "akwam" -> {
                android.util.Log.d("EpisodePlayer", "Resolving Akwam URL")
                resolveAndPlayAkwam(server.url)
            }
            // iframe, webview, أو أي نوع آخر - WebView
            "iframe", "webview", "embed" -> {
                android.util.Log.d("EpisodePlayer", "Opening in WebView (type=${ server.type})")
                openWebViewPlayer(server.url)
            }
            // Default - التحقق من المصدر
            else -> {
                // لو المصدر arabseed أو الرابط يحتوي على reviewrate أو asd.homes - WebView
                if (server.source == "arabseed" ||
                    server.url.contains("reviewrate") ||
                    server.url.contains("asd.homes")) {
                    android.util.Log.d("EpisodePlayer", "Opening in WebView (arabseed source)")
                    openWebViewPlayer(server.url)
                } else {
                    // محاولة التشغيل في embedded WebView
                    android.util.Log.d("EpisodePlayer", "Playing in embedded WebView (fallback)")
                    playInEmbeddedWebView(server.url)
                }
            }
        }
    }

    /**
     * يحل رابط أكوام ويشغل الفيديو
     */
    private fun resolveAndPlayAkwam(episodeUrl: String) {
        showLoading(true, "جاري جلب رابط الفيديو...")

        lifecycleScope.launch {
            try {
                val result = AkwamResolver.resolve(episodeUrl)

                resolvedWatchUrl = result.watchUrl
                resolvedDownloadUrl = result.downloadUrl

                if (result.watchUrl != null) {
                    // نجح! شغل الفيديو مباشرة
                    playWithExoPlayer(result.watchUrl)
                } else {
                    // فشل - نفتح في WebView
                    showToast("جاري فتح صفحة المشاهدة...")
                    playInEmbeddedWebView(episodeUrl)
                }
            } catch (e: Exception) {
                showError("فشل جلب رابط الفيديو: ${e.message}")
                playInEmbeddedWebView(episodeUrl)
            }
        }
    }

    private fun playInEmbeddedWebView(url: String) {
        // Hide ExoPlayer, show WebView
        binding.playerView.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE

        // Release ExoPlayer
        exoPlayer?.release()
        exoPlayer = null

        // Setup WebView
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        binding.webView.webViewClient = android.webkit.WebViewClient()
        binding.webView.loadUrl(url)

        showLoading(false)
    }

    private fun playWithExoPlayer(url: String) {
        // Hide WebView, show PlayerView
        binding.webView.visibility = View.GONE
        binding.playerView.visibility = View.VISIBLE

        // Release previous player
        exoPlayer?.release()

        // Create OkHttp DataSource with SSL bypass for video servers
        val okHttpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient()
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")

        // Create new player with custom DataSource
        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                binding.playerView.player = this

                val mediaItem = MediaItem.fromUri(url)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }

        showLoading(false)
    }

    private fun openWebViewPlayer(url: String) {
        val intent = Intent(this, WebViewPlayerActivity::class.java).apply {
            putExtra(WebViewPlayerActivity.EXTRA_URL, url)
            putExtra(WebViewPlayerActivity.EXTRA_TITLE, episodeDetail?.title ?: "المشاهدة")
        }
        startActivity(intent)
        showLoading(false)
    }

    private fun downloadCurrentEpisode() {
        showLoading(true, "جاري جلب رابط التحميل...")

        lifecycleScope.launch {
            try {
                val fileName = "${seriesTitle}_الحلقة${episodeDetail?.episodeNumber}.mp4"

                // Log للتشخيص
                android.util.Log.d("EpisodePlayer", "downloadCurrentEpisode: currentSource=${currentServer?.source}")

                // الحصول على سيرفرات التحميل
                val downloadServers = episodeDetail?.servers?.download
                android.util.Log.d("EpisodePlayer", "Download servers count: ${downloadServers?.size ?: 0}")

                // لو فيه سيرفرات تحميل
                if (!downloadServers.isNullOrEmpty()) {
                    // نبحث عن سيرفر من نفس المصدر الحالي
                    val currentSource = currentServer?.source
                    val downloadServer = downloadServers.find { it.source == currentSource }
                        ?: downloadServers.firstOrNull()

                    if (downloadServer != null) {
                        android.util.Log.d("EpisodePlayer", "Using download server: ${downloadServer.name}, url=${downloadServer.url}")

                        // TDMHelper يتعامل مع tdm:// links تلقائياً
                        TDMHelper.downloadWithTDM(
                            this@EpisodePlayerActivity,
                            downloadServer.url,
                            fileName
                        )
                        showLoading(false)
                        return@launch
                    }
                }

                // لو مفيش سيرفر تحميل - نستخدم الرابط المباشر المحفوظ
                val directUrl = resolvedWatchUrl ?: resolvedDownloadUrl
                if (!directUrl.isNullOrEmpty()) {
                    android.util.Log.d("EpisodePlayer", "Using resolved URL: $directUrl")
                    TDMHelper.downloadWithTDM(
                        this@EpisodePlayerActivity,
                        directUrl,
                        fileName
                    )
                    showLoading(false)
                    return@launch
                }

                // لو السيرفر الحالي أكوام - نحاول نحل الرابط
                val server = currentServer
                if (server != null && server.type == "akwam") {
                    android.util.Log.d("EpisodePlayer", "Resolving Akwam download URL")
                    val downloadUrl = AkwamResolver.resolveDownload(server.url)
                    if (downloadUrl != null) {
                        resolvedDownloadUrl = downloadUrl
                        TDMHelper.downloadWithTDM(
                            this@EpisodePlayerActivity,
                            downloadUrl,
                            fileName
                        )
                        showLoading(false)
                        return@launch
                    }
                }

                // مفيش رابط متاح
                showToast("لا يوجد رابط تحميل متاح لهذا السيرفر")
                showLoading(false)

            } catch (e: Exception) {
                android.util.Log.e("EpisodePlayer", "Download error", e)
                showError("فشل التحميل: ${e.message}")
                showLoading(false)
            }
        }
    }

    private fun showLoading(show: Boolean, message: String = "جاري التحميل...") {
        binding.loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        binding.loadingText.text = message
    }

    private fun showError(message: String?) {
        showLoading(false)
        Toast.makeText(
            this,
            message ?: getString(R.string.error_loading),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}
