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
                type = server.type
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
                type = "download"
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
                Toast.makeText(this@SeriesDetailActivity, "جاري تحميل الفيديو...", Toast.LENGTH_SHORT).show()

                // Resolve the actual video URL
                val videoUrl = withContext(Dispatchers.IO) {
                    if (server.type == "akwam") {
                        AkwamResolver.resolveWatch(server.url)
                    } else {
                        server.url
                    }
                }

                if (videoUrl != null) {
                    // Open ExoPlayer in fullscreen
                    val intent = Intent(this@SeriesDetailActivity, VideoPlayerActivity::class.java).apply {
                        putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, videoUrl)
                        putExtra(VideoPlayerActivity.EXTRA_TITLE, "${seriesDetail?.title} - الحلقة ${episodeDetail.episodeNumber}")
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this@SeriesDetailActivity, "تعذر الحصول على رابط الفيديو", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SeriesDetailActivity, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun downloadWithTDM(server: DownloadServer, episodeDetail: EpisodeDetail) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@SeriesDetailActivity, "جاري تجهيز التحميل...", Toast.LENGTH_SHORT).show()

                // Resolve the actual download URL
                val downloadUrl = withContext(Dispatchers.IO) {
                    AkwamResolver.resolveDownload(server.url)
                }

                if (downloadUrl != null) {
                    val fileName = "${seriesDetail?.title}_E${episodeDetail.episodeNumber}.mp4"

                    // Check if TDM is installed
                    if (TDMHelper.isTDMInstalled(this@SeriesDetailActivity)) {
                        TDMHelper.downloadWithTDM(this@SeriesDetailActivity, downloadUrl, fileName)
                    } else {
                        // Prompt to install TDM
                        TDMHelper.showInstallDialog(this@SeriesDetailActivity)
                    }
                } else {
                    Toast.makeText(this@SeriesDetailActivity, "تعذر الحصول على رابط التحميل", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
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
    val type: String
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
        holder.serverQuality.text = server.quality
    }

    override fun getItemCount() = servers.size
}
