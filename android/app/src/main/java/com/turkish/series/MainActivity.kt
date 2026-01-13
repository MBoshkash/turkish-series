package com.turkish.series

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
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
    private var filteredSeries: List<SeriesSummary> = emptyList()
    private var displayedSeries: List<SeriesSummary> = emptyList()

    // Pagination
    private val pageSize = 30
    private var currentPage = 0
    private var hasMoreData = true
    private var isLoadingMore = false

    // Current sort type - default is LATEST (by last_updated = latest episode added)
    private var currentSortType = SortType.LATEST
    private var sortAscending = false

    // Filters
    private var selectedYear: String? = null
    private var selectedRating: Double? = null
    private var selectedGenre: String? = null

    // Available filter options
    private var availableYears: List<String> = emptyList()
    private var availableGenres: List<String> = emptyList()

    // Double back to exit
    private var backPressedTime: Long = 0

    enum class SortType {
        LATEST,      // الأحدث (حسب آخر تحديث)
        OLDEST,      // الأقدم
        YEAR_DESC,   // السنة (الأحدث)
        YEAR_ASC,    // السنة (الأقدم)
        EPISODES,    // عدد الحلقات
        RATING,      // التقييم
        NAME         // الاسم
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDrawer()
        setupRecyclerView()
        setupSearch()
        setupSwipeRefresh()
        setupFilters()

        loadSeries()

        // Check for updates
        checkForUpdates()
    }

    private fun checkForUpdates() {
        lifecycleScope.launch {
            com.turkish.series.utils.UpdateChecker.checkForUpdate(this@MainActivity)
        }
    }

    private fun setupDrawer() {
        // Menu button opens drawer
        binding.menuButton.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        // Navigation items
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_series -> {
                    // Already on series page
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_telegram -> {
                    // Open Telegram
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/turkish_series_app"))
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "تعذر فتح تيليجرام", Toast.LENGTH_SHORT).show()
                    }
                    binding.drawerLayout.closeDrawers()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = SeriesAdapter { series ->
            openSeriesDetail(series)
        }

        binding.seriesRecyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            adapter = this@MainActivity.adapter

            // Scroll listener for pagination - load more when near bottom
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    // Only load more when scrolling down
                    if (dy <= 0) return

                    val layoutManager = recyclerView.layoutManager as GridLayoutManager
                    val totalItemCount = layoutManager.itemCount
                    val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()

                    // Load more when 6 items from bottom
                    if (hasMoreData && !isLoadingMore && lastVisibleItemPosition >= totalItemCount - 6) {
                        loadMoreSeries()
                    }
                }
            })
        }

    }

    private fun setupSearch() {
        // Search button
        binding.searchButton.setOnClickListener {
            showSearchBar(true)
        }

        // Close search
        binding.closeSearchButton.setOnClickListener {
            showSearchBar(false)
            binding.searchEditText.setText("")
        }

        // Search on text change
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                resetPagination()
                applyFilters()
            }
        })

        // Search on enter
        binding.searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                resetPagination()
                applyFilters()
                true
            } else false
        }
    }

    private fun showSearchBar(show: Boolean) {
        if (show) {
            binding.searchCard.visibility = View.VISIBLE
            binding.searchEditText.requestFocus()
        } else {
            binding.searchCard.visibility = View.GONE
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener {
            loadSeries()
        }
    }

    private fun setupFilters() {
        // Sort/Filter button
        binding.sortFilterButton.setOnClickListener {
            showSortDialog()
        }

        // Year filter
        binding.yearFilterButton.setOnClickListener {
            showYearFilterDialog()
        }

        // Rating filter
        binding.ratingFilterButton.setOnClickListener {
            showRatingFilterDialog()
        }

        // Genre filter
        binding.genreFilterButton.setOnClickListener {
            showGenreFilterDialog()
        }
    }

    private fun showSortDialog() {
        val dialog = BottomSheetDialog(this)
        val options = arrayOf(
            "الأحدث (آخر تحديث)",
            "الأقدم",
            "السنة (الأحدث)",
            "السنة (الأقدم)",
            "عدد الحلقات",
            "التقييم",
            "الاسم"
        )

        val dialogView = layoutInflater.inflate(R.layout.dialog_filter_list, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.filterRecyclerView)
        val title = dialogView.findViewById<android.widget.TextView>(R.id.filterTitle)
        title.text = "الترتيب"

        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerView.adapter = FilterAdapter(options.toList()) { index ->
            currentSortType = when (index) {
                0 -> SortType.LATEST
                1 -> SortType.OLDEST
                2 -> SortType.YEAR_DESC
                3 -> SortType.YEAR_ASC
                4 -> SortType.EPISODES
                5 -> SortType.RATING
                6 -> SortType.NAME
                else -> SortType.LATEST
            }
            binding.sortFilterButton.text = options[index].split(" ")[0]
            resetPagination()
            applyFilters()
            dialog.dismiss()
        }

        dialog.setContentView(dialogView)
        dialog.show()
    }

    private fun showYearFilterDialog() {
        val dialog = BottomSheetDialog(this)
        val options = listOf("الكل") + availableYears.sortedDescending()

        val dialogView = layoutInflater.inflate(R.layout.dialog_filter_list, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.filterRecyclerView)
        val title = dialogView.findViewById<android.widget.TextView>(R.id.filterTitle)
        title.text = "السنة"

        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerView.adapter = FilterAdapter(options) { index ->
            selectedYear = if (index == 0) null else options[index]
            binding.yearFilterButton.text = selectedYear ?: "السنة"
            resetPagination()
            applyFilters()
            dialog.dismiss()
        }

        dialog.setContentView(dialogView)
        dialog.show()
    }

    private fun showRatingFilterDialog() {
        val dialog = BottomSheetDialog(this)
        val options = listOf("الكل", "9+", "8+", "7+", "6+", "5+")

        val dialogView = layoutInflater.inflate(R.layout.dialog_filter_list, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.filterRecyclerView)
        val title = dialogView.findViewById<android.widget.TextView>(R.id.filterTitle)
        title.text = "التقييم"

        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerView.adapter = FilterAdapter(options) { index ->
            selectedRating = when (index) {
                0 -> null
                1 -> 9.0
                2 -> 8.0
                3 -> 7.0
                4 -> 6.0
                5 -> 5.0
                else -> null
            }
            binding.ratingFilterButton.text = if (selectedRating != null) options[index] else "التقييم"
            resetPagination()
            applyFilters()
            dialog.dismiss()
        }

        dialog.setContentView(dialogView)
        dialog.show()
    }

    private fun showGenreFilterDialog() {
        val dialog = BottomSheetDialog(this)
        val options = listOf("الكل") + availableGenres.sorted()

        val dialogView = layoutInflater.inflate(R.layout.dialog_filter_list, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.filterRecyclerView)
        val title = dialogView.findViewById<android.widget.TextView>(R.id.filterTitle)
        title.text = "النوع"

        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerView.adapter = FilterAdapter(options) { index ->
            selectedGenre = if (index == 0) null else options[index]
            binding.genreFilterButton.text = selectedGenre ?: "النوع"
            resetPagination()
            applyFilters()
            dialog.dismiss()
        }

        dialog.setContentView(dialogView)
        dialog.show()
    }

    private fun loadSeries() {
        showLoading(true)

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getSeriesList()
                // Filter out series with 0 episodes
                allSeries = response.series.filter { (it.episodesCount ?: 0) > 0 }

                // Extract available years and genres
                availableYears = allSeries.mapNotNull { it.year }.distinct()
                availableGenres = allSeries.flatMap { it.genres ?: emptyList() }.distinct()

                resetPagination()
                applyFilters()
                showContent()
            } catch (e: Exception) {
                showError(e.message)
            }
        }
    }

    private fun resetPagination() {
        currentPage = 0
        hasMoreData = true
        displayedSeries = emptyList()
    }

    private fun applyFilters() {
        val query = binding.searchEditText.text?.toString() ?: ""

        // 1. Filter by search query
        var result = if (query.isEmpty()) {
            allSeries
        } else {
            allSeries.filter { series ->
                series.title.contains(query, ignoreCase = true) ||
                        series.originalTitle?.contains(query, ignoreCase = true) == true
            }
        }

        // 2. Apply year filter
        selectedYear?.let { year ->
            result = result.filter { it.year == year }
        }

        // 3. Apply rating filter
        selectedRating?.let { minRating ->
            result = result.filter { (it.rating ?: 0.0) >= minRating }
        }

        // 4. Apply genre filter
        selectedGenre?.let { genre ->
            result = result.filter { it.genres?.contains(genre) == true }
        }

        // 5. Sort (LATEST = newest first by last episode date, OLDEST = oldest first)
        result = when (currentSortType) {
            SortType.LATEST -> result.sortedByDescending { parseArabicDate(it.lastEpisodeDate) }
            SortType.OLDEST -> result.sortedBy { parseArabicDate(it.lastEpisodeDate) }
            SortType.YEAR_DESC -> result.sortedByDescending { it.year ?: "0000" }
            SortType.YEAR_ASC -> result.sortedBy { it.year ?: "9999" }
            SortType.EPISODES -> result.sortedByDescending { it.episodesCount ?: 0 }
            SortType.RATING -> result.sortedByDescending { it.rating ?: 0.0 }
            SortType.NAME -> result.sortedBy { it.title }
        }

        filteredSeries = result

        // Apply pagination
        loadPage()

        // Update results count
        updateResultsCount()
    }

    private fun loadPage() {
        val endIndex = minOf((currentPage + 1) * pageSize, filteredSeries.size)

        displayedSeries = filteredSeries.take(endIndex)
        adapter.submitList(displayedSeries.toList())

        hasMoreData = endIndex < filteredSeries.size
    }

    private fun loadMoreSeries() {
        if (!hasMoreData || isLoadingMore) return

        isLoadingMore = true
        binding.bottomLoading.visibility = View.VISIBLE
        currentPage++
        loadPage()

        // Delay hiding the loading and resetting isLoadingMore
        // This prevents multiple rapid calls
        binding.bottomLoading.postDelayed({
            binding.bottomLoading.visibility = View.GONE
            isLoadingMore = false
        }, 300)
    }

    private fun updateResultsCount() {
        val total = allSeries.size
        val filtered = filteredSeries.size

        val hasFilters = !binding.searchEditText.text.isNullOrEmpty() ||
                         selectedYear != null ||
                         selectedRating != null ||
                         selectedGenre != null

        binding.resultsCount.text = if (hasFilters) {
            "$filtered نتيجة"
        } else {
            "$total مسلسل"
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

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else if (binding.searchCard.visibility == View.VISIBLE) {
            showSearchBar(false)
            binding.searchEditText.setText("")
        } else {
            // Double back to exit
            if (backPressedTime + 2000 > System.currentTimeMillis()) {
                super.onBackPressed()
            } else {
                Toast.makeText(this, "اضغط مرة أخرى للخروج", Toast.LENGTH_SHORT).show()
                backPressedTime = System.currentTimeMillis()
            }
        }
    }

    /**
     * Parse Arabic date string to timestamp for sorting
     * Input: "الثلاثاء 13 يناير 2026 - 01:56 صباحا"
     * Output: timestamp in milliseconds (or 0 if parsing fails)
     */
    private fun parseArabicDate(dateStr: String?): Long {
        if (dateStr.isNullOrEmpty()) return 0L

        return try {
            val arabicMonths = mapOf(
                "يناير" to 1, "فبراير" to 2, "مارس" to 3, "أبريل" to 4,
                "مايو" to 5, "يونيو" to 6, "يوليو" to 7, "أغسطس" to 8,
                "سبتمبر" to 9, "أكتوبر" to 10, "نوفمبر" to 11, "ديسمبر" to 12
            )

            // Extract day, month, year from pattern like "الثلاثاء 13 يناير 2026"
            val dateRegex = Regex("""(\d{1,2})\s+(يناير|فبراير|مارس|أبريل|مايو|يونيو|يوليو|أغسطس|سبتمبر|أكتوبر|نوفمبر|ديسمبر)\s+(\d{4})""")
            val match = dateRegex.find(dateStr)

            if (match != null) {
                val day = match.groupValues[1].toInt()
                val monthName = match.groupValues[2]
                val year = match.groupValues[3].toInt()
                val month = arabicMonths[monthName] ?: 1

                // Extract time if available
                var hour = 0
                var minute = 0
                val timeRegex = Regex("""(\d{1,2}):(\d{2})\s*(صباحا|مساءا|صباحاً|مساءاً)?""")
                val timeMatch = timeRegex.find(dateStr)
                if (timeMatch != null) {
                    hour = timeMatch.groupValues[1].toInt()
                    minute = timeMatch.groupValues[2].toInt()
                    val period = timeMatch.groupValues[3]
                    if (period.contains("مساء") && hour < 12) {
                        hour += 12
                    } else if (period.contains("صباح") && hour == 12) {
                        hour = 0
                    }
                }

                // Create calendar and get timestamp
                val calendar = java.util.Calendar.getInstance()
                calendar.set(year, month - 1, day, hour, minute, 0)
                calendar.get(java.util.Calendar.MILLISECOND)
                calendar.timeInMillis
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }
}

/**
 * Simple adapter for filter dialog
 */
class FilterAdapter(
    private val items: List<String>,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<FilterAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: android.widget.TextView = itemView.findViewById(android.R.id.text1)

        init {
            itemView.setOnClickListener {
                onItemClick(adapterPosition)
            }
        }
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = items[position]
        holder.textView.setTextColor(holder.itemView.context.getColor(R.color.text_primary))
        holder.textView.setPadding(32, 32, 32, 32)
    }

    override fun getItemCount() = items.size
}
