package com.turkish.series

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.google.android.material.tabs.TabLayout
import com.turkish.series.api.ApiClient
import com.turkish.series.databinding.ActivityEpisodePlayerBinding
import com.turkish.series.models.EpisodeDetail
import com.turkish.series.models.WatchServer
import com.turkish.series.utils.TDMHelper
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEpisodePlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val seriesId = intent.getStringExtra(EXTRA_SERIES_ID) ?: ""
        val seriesTitle = intent.getStringExtra(EXTRA_SERIES_TITLE) ?: ""
        val episodeNumber = intent.getIntExtra(EXTRA_EPISODE_NUMBER, 1)

        setupToolbar(seriesTitle, episodeNumber)
        setupDownloadButton()

        loadEpisodeDetail(seriesId, episodeNumber)
    }

    private fun setupToolbar(seriesTitle: String, episodeNumber: Int) {
        binding.toolbar.title = "$seriesTitle - الحلقة $episodeNumber"
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupDownloadButton() {
        binding.downloadButton.setOnClickListener {
            downloadCurrentEpisode()
        }
    }

    private fun loadEpisodeDetail(seriesId: String, episodeNumber: Int) {
        showLoading(true)

        lifecycleScope.launch {
            try {
                // Format: seriesId_episodeNumber (e.g., 5127_13)
                val filename = "${seriesId}_${String.format("%02d", episodeNumber)}"
                episodeDetail = ApiClient.apiService.getEpisodeDetail(filename)
                episodeDetail?.let { displayEpisodeDetail(it) }
                showLoading(false)
            } catch (e: Exception) {
                showError(e.message)
            }
        }
    }

    private fun displayEpisodeDetail(episode: EpisodeDetail) {
        // Title
        binding.episodeTitleText.text = episode.title ?: "الحلقة ${episode.episodeNumber}"

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
        showLoading(true)

        when (server.type) {
            "direct" -> {
                // Play directly with ExoPlayer
                playWithExoPlayer(server.url)
            }
            "webview" -> {
                // Open in WebView (for qissah eshq, etc.)
                // نضيف /see/ للرابط لفتح صفحة السيرفرات مباشرة
                val watchUrl = if (server.url.endsWith("/")) {
                    "${server.url}see/"
                } else {
                    "${server.url}/see/"
                }
                openWebViewPlayer(watchUrl)
            }
            "iframe" -> {
                // Open in WebView
                openWebViewPlayer(server.url)
            }
            "akwam_page" -> {
                // روابط صفحات أكوام - نفتحها في WebView لأن الروابط المباشرة مؤقتة
                playInEmbeddedWebView(server.url)
            }
            else -> {
                // Default: try embedded WebView
                playInEmbeddedWebView(server.url)
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

        // Create DataSource with headers for Akwam servers
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

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
        // Open WebViewPlayerActivity
        val intent = Intent(this, WebViewPlayerActivity::class.java).apply {
            putExtra(WebViewPlayerActivity.EXTRA_URL, url)
            putExtra(WebViewPlayerActivity.EXTRA_TITLE, episodeDetail?.title ?: "المشاهدة")
        }
        startActivity(intent)
        showLoading(false)
    }

    private fun downloadCurrentEpisode() {
        // Get download URL from servers
        val downloadServer = episodeDetail?.servers?.download?.firstOrNull()

        if (downloadServer != null) {
            TDMHelper.downloadWithTDM(
                this,
                downloadServer.url,
                "${episodeDetail?.seriesTitle}_${episodeDetail?.episodeNumber}.mp4"
            )
        } else {
            // لو مفيش رابط تحميل، نستخدم رابط المشاهدة
            val watchServer = currentServer ?: episodeDetail?.servers?.watch?.firstOrNull()
            if (watchServer != null) {
                TDMHelper.downloadWithTDM(
                    this,
                    watchServer.url,
                    "${episodeDetail?.seriesTitle}_${episodeDetail?.episodeNumber}.mp4"
                )
            } else {
                Toast.makeText(
                    this,
                    R.string.error_video_not_found,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showError(message: String?) {
        showLoading(false)
        Toast.makeText(
            this,
            message ?: getString(R.string.error_loading),
            Toast.LENGTH_SHORT
        ).show()
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
