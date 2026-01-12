package com.turkish.series

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.turkish.series.adapters.EpisodeAdapter
import com.turkish.series.api.ApiClient
import com.turkish.series.databinding.ActivitySeriesDetailBinding
import com.turkish.series.models.EpisodeSummary
import com.turkish.series.models.SeriesDetail
import kotlinx.coroutines.launch

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
            openEpisodePlayer(episode)
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
        Glide.with(this)
            .load(series.backdrop ?: series.poster)
            .placeholder(R.drawable.placeholder_poster)
            .into(binding.backdropImage)

        // Title
        binding.titleText.text = series.title

        // Original Title
        series.originalTitle?.let {
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

        // Description
        binding.descriptionText.text = series.description ?: ""

        // Episodes
        series.episodes?.let { episodes ->
            // Sort episodes (newest first)
            val sortedEpisodes = episodes.sortedByDescending { it.number }
            episodeAdapter.submitList(sortedEpisodes)
        }
    }

    private fun openEpisodePlayer(episode: EpisodeSummary) {
        val intent = Intent(this, EpisodePlayerActivity::class.java).apply {
            putExtra(EpisodePlayerActivity.EXTRA_SERIES_ID, seriesId)
            putExtra(EpisodePlayerActivity.EXTRA_SERIES_TITLE, seriesDetail?.title)
            putExtra(EpisodePlayerActivity.EXTRA_EPISODE_NUMBER, episode.number)
        }
        startActivity(intent)
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
}
