package com.turkish.series

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.turkish.series.adapters.SeriesAdapter
import com.turkish.series.api.ApiClient
import com.turkish.series.databinding.ActivityMainBinding
import com.turkish.series.models.SeriesSummary
import kotlinx.coroutines.launch

/**
 * الصفحة الرئيسية - قائمة المسلسلات
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: SeriesAdapter

    private var allSeries: List<SeriesSummary> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupSearch()
        setupSwipeRefresh()

        loadSeries()
    }

    private fun setupRecyclerView() {
        adapter = SeriesAdapter { series ->
            openSeriesDetail(series)
        }

        binding.seriesRecyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            adapter = this@MainActivity.adapter
        }
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterSeries(s?.toString() ?: "")
            }
        })
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener {
            loadSeries()
        }
    }

    private fun loadSeries() {
        showLoading(true)

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getSeriesList()
                allSeries = response.series
                adapter.submitList(allSeries)
                showContent()
            } catch (e: Exception) {
                showError(e.message)
            }
        }
    }

    private fun filterSeries(query: String) {
        if (query.isEmpty()) {
            adapter.submitList(allSeries)
        } else {
            val filtered = allSeries.filter { series ->
                series.title.contains(query, ignoreCase = true) ||
                        series.originalTitle?.contains(query, ignoreCase = true) == true
            }
            adapter.submitList(filtered)
        }
    }

    private fun openSeriesDetail(series: SeriesSummary) {
        val intent = Intent(this, SeriesDetailActivity::class.java).apply {
            putExtra(SeriesDetailActivity.EXTRA_SERIES_ID, series.id)
            putExtra(SeriesDetailActivity.EXTRA_SERIES_TITLE, series.title)
        }
        startActivity(intent)
    }

    private fun showLoading(show: Boolean) {
        binding.loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        binding.swipeRefresh.isRefreshing = false
        binding.emptyState.visibility = View.GONE
    }

    private fun showContent() {
        binding.loadingProgress.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false

        if (allSeries.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
        } else {
            binding.emptyState.visibility = View.GONE
        }
    }

    private fun showError(message: String?) {
        binding.loadingProgress.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false
        binding.emptyState.visibility = View.VISIBLE

        Toast.makeText(
            this,
            message ?: getString(R.string.error_loading),
            Toast.LENGTH_SHORT
        ).show()

        binding.retryButton.setOnClickListener {
            loadSeries()
        }
    }
}
