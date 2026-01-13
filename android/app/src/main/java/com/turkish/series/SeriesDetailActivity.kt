package com.turkish.series

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.turkish.series.adapters.EpisodeAdapter
import com.turkish.series.api.ApiClient
import com.turkish.series.databinding.ActivitySeriesDetailBinding
import com.turkish.series.databinding.DialogEpisodeOptionsBinding
import com.turkish.series.models.EpisodeDetail
import com.turkish.series.models.EpisodeSummary
import com.turkish.series.models.SeriesDetail
import com.turkish.series.models.WatchServer
import com.turkish.series.models.DownloadServer
import com.turkish.series.utils.AkwamResolver
import com.turkish.series.utils.TDMHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * صفحة تفاصيل المسلسل
 */
class SeriesDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SERIES_ID = "series_id"
        const val EXTRA_SERIES_TITLE = "series_title"
    }

    private lateinit var binding: ActivitySeriesDetailBinding
    private lateinit var episodeAdapter: EpisodeAdapter

    private var seriesId: String = ""
    private var seriesDetail: SeriesDetail? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySeriesDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        seriesId = intent.getStringExtra(EXTRA_SERIES_ID) ?: ""
        val seriesTitle = intent.getStringExtra(EXTRA_SERIES_TITLE) ?: ""

        setupToolbar(seriesTitle)
        setupEpisodesRecyclerView()

        loadSeriesDetail()
    }

    private fun setupToolbar(title: String) {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupEpisodesRecyclerView() {
        episodeAdapter = EpisodeAdapter { episode ->
            showEpisodeOptionsDialog(episode)
        }

        binding.episodesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@SeriesDetailActivity)
            adapter = episodeAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun loadSeriesDetail() {
        showLoading(true)

        lifecycleScope.launch {
            try {
                seriesDetail = ApiClient.apiService.getSeriesDetail(seriesId)
                seriesDetail?.let { displaySeriesDetail(it) }
                showLoading(false)
            } catch (e: Exception) {
                showError(e.message)
            }
        }
    }

    private fun displaySeriesDetail(series: SeriesDetail) {
        // Backdrop/Poster
        val imageUrl = series.poster?.takeIf { it.isNotEmpty() }
            ?: series.backdrop?.takeIf { it.isNotEmpty() }

        if (!imageUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.placeholder_poster)
                .error(R.drawable.placeholder_poster)
                .into(binding.backdropImage)
        }

        // Title
        binding.titleText.text = series.title

        // Original Title
        series.originalTitle?.takeIf { it.isNotEmpty() }?.let {
            binding.originalTitleText.text = it
            binding.originalTitleText.visibility = View.VISIBLE
        } ?: run {
            binding.originalTitleText.visibility = View.GONE
        }

        // Rating
        series.rating?.let {
            binding.ratingText.text = String.format("%.1f", it)
        }

        // Year
        binding.yearText.text = series.year ?: ""

        // Episodes Count
        binding.episodesCountText.text = getString(R.string.episode_count, series.totalEpisodes ?: 0)

        // Quality and Duration (Info Row 2)
        val hasQuality = !series.quality.isNullOrEmpty()
        val hasDuration = !series.duration.isNullOrEmpty()

        if (hasQuality || hasDuration) {
            binding.infoRow2.visibility = View.VISIBLE

            if (hasQuality) {
                binding.qualityText.text = series.quality
                binding.qualityText.visibility = View.VISIBLE
            } else {
                binding.qualityText.visibility = View.GONE
            }

            if (hasDuration) {
                binding.durationText.text = series.duration
                binding.durationText.visibility = View.VISIBLE
            } else {
                binding.durationText.visibility = View.GONE
            }
        } else {
            binding.infoRow2.visibility = View.GONE
        }

        // Genres
        binding.genresChipGroup.removeAllViews()
        series.genres?.forEach { genre ->
            val chip = Chip(this).apply {
                text = genre
                isClickable = false
                setChipBackgroundColorResource(R.color.surface)
                setTextColor(getColor(R.color.text_secondary))
            }
            binding.genresChipGroup.addView(chip)
        }

        // Description - Show only if not empty
        val description = series.description?.trim()
        if (!description.isNullOrEmpty()) {
            binding.descriptionSection.visibility = View.VISIBLE
            binding.descriptionText.text = description
        } else {
            binding.descriptionSection.visibility = View.GONE
        }

        // Episodes
        series.episodes?.let { episodes ->
            // Set quality for episodes
            episodeAdapter.setSeriesQuality(series.quality)
            // Sort episodes (newest first)
            val sortedEpisodes = episodes.sortedByDescending { it.number }
            episodeAdapter.submitList(sortedEpisodes)
        }
    }

    private fun showEpisodeOptionsDialog(episode: EpisodeSummary) {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogEpisodeOptionsBinding.inflate(LayoutInflater.from(this))

        dialogBinding.dialogTitle.text = "الحلقة ${episode.number}"

        // Watch button
        dialogBinding.watchButton.setOnClickListener {
            dialogBinding.optionsContainer.visibility = View.GONE
            dialogBinding.loadingProgress.visibility = View.VISIBLE
            loadEpisodeServers(episode, dialog, dialogBinding, isWatch = true)
        }

        // Download button
        dialogBinding.downloadButton.setOnClickListener {
            dialogBinding.optionsContainer.visibility = View.GONE
            dialogBinding.loadingProgress.visibility = View.VISIBLE
            loadEpisodeServers(episode, dialog, dialogBinding, isWatch = false)
        }

        dialog.setContentView(dialogBinding.root)
        dialog.show()
    }

    private fun loadEpisodeServers(
        episode: EpisodeSummary,
        dialog: BottomSheetDialog,
        dialogBinding: DialogEpisodeOptionsBinding,
        isWatch: Boolean
    ) {
        lifecycleScope.launch {
            try {
                val filename = "${seriesId}_${String.format("%02d", episode.number)}"
                val episodeDetail = ApiClient.apiService.getEpisodeDetail(filename)

                dialogBinding.loadingProgress.visibility = View.GONE

                if (isWatch) {
                    showWatchServers(episodeDetail, dialog, dialogBinding)
                } else {
                    showDownloadServers(episodeDetail, dialog, dialogBinding)
                }
            } catch (e: Exception) {
                dialogBinding.loadingProgress.visibility = View.GONE
                Toast.makeText(this@SeriesDetailActivity, "خطأ في تحميل السيرفرات", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
    }

    private fun showWatchServers(
        episodeDetail: EpisodeDetail,
        dialog: BottomSheetDialog,
        dialogBinding: DialogEpisodeOptionsBinding
    ) {
        val servers = episodeDetail.servers?.watch ?: emptyList()

        if (servers.isEmpty()) {
            Toast.makeText(this, "لا توجد سيرفرات متاحة", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            return
        }

        dialogBinding.serversTitle.text = "اختر سيرفر المشاهدة"
        dialogBinding.serversContainer.visibility = View.VISIBLE

        // Create server adapter
        val serverAdapter = ServerAdapter(servers.mapIndexed { index, server ->
            ServerItem(
                name = getServerDisplayName(server.name, index),
                quality = server.quality ?: "720p",
                type = server.type,
                originalName = server.name  // اسم السيرفر الأصلي
            )
        }) { position ->
            val server = servers[position]
            playVideo(server, episodeDetail)
            dialog.dismiss()
        }

        dialogBinding.serversRecyclerView.layoutManager = LinearLayoutManager(this)
        dialogBinding.serversRecyclerView.adapter = serverAdapter
    }

    private fun showDownloadServers(
        episodeDetail: EpisodeDetail,
        dialog: BottomSheetDialog,
        dialogBinding: DialogEpisodeOptionsBinding
    ) {
        val servers = episodeDetail.servers?.download ?: emptyList()

        if (servers.isEmpty()) {
            Toast.makeText(this, "لا توجد روابط تحميل متاحة", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            return
        }

        dialogBinding.serversTitle.text = "اختر سيرفر التحميل"
        dialogBinding.serversContainer.visibility = View.VISIBLE

        // Create server adapter
        val serverAdapter = ServerAdapter(servers.mapIndexed { index, server ->
            ServerItem(
                name = getServerDisplayName(server.name, index),
                quality = server.quality ?: "720p",
                type = "download",
                originalName = server.name  // اسم السيرفر الأصلي
            )
        }) { position ->
            val server = servers[position]
            downloadWithTDM(server, episodeDetail)
            dialog.dismiss()
        }

        dialogBinding.serversRecyclerView.layoutManager = LinearLayoutManager(this)
        dialogBinding.serversRecyclerView.adapter = serverAdapter
    }

    private fun getServerDisplayName(originalName: String, index: Int): String {
        return when {
            originalName.contains("أكوام", ignoreCase = true) -> "سيرفر ${index + 1}"
            originalName.contains("akwam", ignoreCase = true) -> "سيرفر ${index + 1}"
            originalName.contains("قصة عشق", ignoreCase = true) -> "سيرفر بديل ${index + 1}"
            else -> "سيرفر ${index + 1}"
        }
    }

    private fun playVideo(server: WatchServer, episodeDetail: EpisodeDetail) {
        lifecycleScope.launch {
            try {
                android.util.Log.d("SeriesDetail", "playVideo: type=${server.type}, source=${server.source}, url=${server.url}")

                // استبعاد روابط reviewrate.net و asd.homes - مش شغالة كويس
                if (server.url.contains("reviewrate.net") || server.url.contains("asd.homes")) {
                    Toast.makeText(this@SeriesDetailActivity, "هذا السيرفر غير متاح حالياً، جرب سيرفر آخر", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // التعامل حسب نوع السيرفر
                when (server.type) {
                    // iframe أو webview - نفتح في WebView
                    "iframe", "webview", "embed" -> {
                        android.util.Log.d("SeriesDetail", "Opening in WebView (type=${server.type})")
                        val intent = Intent(this@SeriesDetailActivity, WebViewPlayerActivity::class.java).apply {
                            putExtra(WebViewPlayerActivity.EXTRA_URL, server.url)
                            putExtra(WebViewPlayerActivity.EXTRA_TITLE, "${seriesDetail?.title} - الحلقة ${episodeDetail.episodeNumber}")
                        }
                        startActivity(intent)
                    }
                    // أكوام - نحتاج resolve أولاً
                    "akwam" -> {
                        android.util.Log.d("SeriesDetail", "Resolving Akwam URL")
                        Toast.makeText(this@SeriesDetailActivity, "جاري تحميل الفيديو...", Toast.LENGTH_SHORT).show()

                        val videoUrl = withContext(Dispatchers.IO) {
                            AkwamResolver.resolveWatch(server.url)
                        }

                        if (videoUrl != null) {
                            android.util.Log.d("SeriesDetail", "Akwam resolved to: $videoUrl")
                            val intent = Intent(this@SeriesDetailActivity, VideoPlayerActivity::class.java).apply {
                                putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, videoUrl)
                                putExtra(VideoPlayerActivity.EXTRA_TITLE, "${seriesDetail?.title} - الحلقة ${episodeDetail.episodeNumber}")
                            }
                            startActivity(intent)
                        } else {
                            Toast.makeText(this@SeriesDetailActivity, "تعذر الحصول على رابط الفيديو", Toast.LENGTH_SHORT).show()
                        }
                    }
                    // رابط مباشر - نشغل في ExoPlayer
                    "direct" -> {
                        android.util.Log.d("SeriesDetail", "Playing direct URL in ExoPlayer")
                        val intent = Intent(this@SeriesDetailActivity, VideoPlayerActivity::class.java).apply {
                            putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, server.url)
                            putExtra(VideoPlayerActivity.EXTRA_TITLE, "${seriesDetail?.title} - الحلقة ${episodeDetail.episodeNumber}")
                        }
                        startActivity(intent)
                    }
                    // نوع غير معروف - نتحقق من المصدر أو الرابط
                    else -> {
                        // لو المصدر عرب سيد أو الرابط يحتوي على asd.homes - WebView
                        if (server.source == "arabseed" ||
                            server.url.contains("asd.homes") ||
                            server.url.contains("embed")) {
                            android.util.Log.d("SeriesDetail", "Opening in WebView (arabseed/embed URL)")
                            val intent = Intent(this@SeriesDetailActivity, WebViewPlayerActivity::class.java).apply {
                                putExtra(WebViewPlayerActivity.EXTRA_URL, server.url)
                                putExtra(WebViewPlayerActivity.EXTRA_TITLE, "${seriesDetail?.title} - الحلقة ${episodeDetail.episodeNumber}")
                            }
                            startActivity(intent)
                        } else {
                            // محاولة تشغيل كرابط مباشر
                            android.util.Log.d("SeriesDetail", "Trying as direct URL (fallback)")
                            val intent = Intent(this@SeriesDetailActivity, VideoPlayerActivity::class.java).apply {
                                putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, server.url)
                                putExtra(VideoPlayerActivity.EXTRA_TITLE, "${seriesDetail?.title} - الحلقة ${episodeDetail.episodeNumber}")
                            }
                            startActivity(intent)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SeriesDetail", "playVideo error", e)
                Toast.makeText(this@SeriesDetailActivity, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun downloadWithTDM(server: DownloadServer, episodeDetail: EpisodeDetail) {
        lifecycleScope.launch {
            try {
                android.util.Log.d("SeriesDetail", "downloadWithTDM: source=${server.source}, url=${server.url}")

                // استبعاد روابط reviewrate.net
                if (server.url.contains("reviewrate.net")) {
                    Toast.makeText(this@SeriesDetailActivity, "هذا السيرفر غير متاح للتحميل، جرب سيرفر آخر", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val fileName = "${seriesDetail?.title}_E${episodeDetail.episodeNumber}.mp4"

                // لو الرابط هو TDM deep link - نفتحه مباشرة
                if (TDMHelper.isTDMDeepLink(server.url)) {
                    android.util.Log.d("SeriesDetail", "Opening TDM deep link directly")
                    TDMHelper.downloadWithTDM(this@SeriesDetailActivity, server.url, fileName)
                    return@launch
                }

                // لو المصدر أكوام - نحتاج resolve
                if (server.source == "akwam") {
                    Toast.makeText(this@SeriesDetailActivity, "جاري تجهيز التحميل...", Toast.LENGTH_SHORT).show()

                    val downloadUrl = withContext(Dispatchers.IO) {
                        AkwamResolver.resolveDownload(server.url)
                    }

                    if (downloadUrl != null) {
                        android.util.Log.d("SeriesDetail", "Akwam download resolved to: $downloadUrl")
                        TDMHelper.downloadWithTDM(this@SeriesDetailActivity, downloadUrl, fileName)
                    } else {
                        Toast.makeText(this@SeriesDetailActivity, "تعذر الحصول على رابط التحميل", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // رابط مباشر أو عرب سيد - نستخدمه مباشرة
                    android.util.Log.d("SeriesDetail", "Using direct download URL")
                    TDMHelper.downloadWithTDM(this@SeriesDetailActivity, server.url, fileName)
                }
            } catch (e: Exception) {
                android.util.Log.e("SeriesDetail", "downloadWithTDM error", e)
                Toast.makeText(this@SeriesDetailActivity, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        if (show) {
            binding.loadingContainer.visibility = View.VISIBLE
            binding.contentContainer.visibility = View.GONE
        } else {
            binding.loadingContainer.visibility = View.GONE
            binding.contentContainer.visibility = View.VISIBLE
        }
    }

    private fun showError(message: String?) {
        showLoading(false)
        Toast.makeText(
            this,
            message ?: getString(R.string.error_loading),
            Toast.LENGTH_SHORT
        ).show()
    }
}

// Server item data class
data class ServerItem(
    val name: String,
    val quality: String,
    val type: String,
    val originalName: String = ""  // اسم السيرفر الأصلي (بالإنجليزي)
)

// Server adapter for the dialog
class ServerAdapter(
    private val servers: List<ServerItem>,
    private val onServerClick: (Int) -> Unit
) : RecyclerView.Adapter<ServerAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val serverName: android.widget.TextView = itemView.findViewById(R.id.serverName)
        val serverQuality: android.widget.TextView = itemView.findViewById(R.id.serverQuality)

        init {
            itemView.setOnClickListener {
                onServerClick(adapterPosition)
            }
        }
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_server, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val server = servers[position]
        holder.serverName.text = server.name
        // عرض الجودة مع اسم السيرفر الأصلي
        val qualityText = if (server.originalName.isNotEmpty()) {
            "${server.quality} • ${server.originalName}"
        } else {
            server.quality
        }
        holder.serverQuality.text = qualityText
    }

    override fun getItemCount() = servers.size
}
