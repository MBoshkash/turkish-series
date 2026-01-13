package com.turkish.series.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.turkish.series.R
import com.turkish.series.models.EpisodeSummary
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Adapter لعرض قائمة الحلقات
 */
class EpisodeAdapter(
    private val onItemClick: (EpisodeSummary) -> Unit
) : ListAdapter<EpisodeSummary, EpisodeAdapter.EpisodeViewHolder>(EpisodeDiffCallback()) {

    private var seriesQuality: String? = null

    fun setSeriesQuality(quality: String?) {
        seriesQuality = quality
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_episode, parent, false)
        return EpisodeViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        holder.bind(getItem(position), seriesQuality)
    }

    class EpisodeViewHolder(
        itemView: View,
        private val onItemClick: (EpisodeSummary) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val episodeNumber: TextView = itemView.findViewById(R.id.episodeNumber)
        private val episodeTitle: TextView = itemView.findViewById(R.id.episodeTitle)
        private val episodeDate: TextView = itemView.findViewById(R.id.episodeDate)
        private val qualityBadge: TextView = itemView.findViewById(R.id.qualityBadge)

        fun bind(episode: EpisodeSummary, quality: String?) {
            // Episode number
            episodeNumber.text = episode.number.toString()

            // Title
            episodeTitle.text = episode.title ?: "الحلقة ${episode.number}"

            // Date - format to show only date without time
            episode.dateAdded?.let { dateStr ->
                val formattedDate = formatDate(dateStr)
                if (formattedDate != null) {
                    episodeDate.text = formattedDate
                    episodeDate.visibility = View.VISIBLE
                } else {
                    episodeDate.visibility = View.GONE
                }
            } ?: run {
                episodeDate.visibility = View.GONE
            }

            // Quality badge
            if (!quality.isNullOrEmpty()) {
                qualityBadge.text = quality
                qualityBadge.visibility = View.VISIBLE
            } else {
                qualityBadge.visibility = View.GONE
            }

            // Click listener
            itemView.setOnClickListener {
                onItemClick(episode)
            }
        }

        /**
         * Format date to simple format d/M/yyyy
         * Input can be:
         * - Arabic: "الخميس 18 سبتمبر 2025 - 02:18 مساءاً"
         * - ISO: "2026-01-12T20:43:28.758683Z"
         * Output: "18/9/2025"
         */
        private fun formatDate(dateStr: String): String? {
            return try {
                // First try to extract from Arabic format
                // Pattern: "اليوم DD شهر YYYY"
                val arabicMonths = mapOf(
                    "يناير" to 1, "فبراير" to 2, "مارس" to 3, "أبريل" to 4,
                    "مايو" to 5, "يونيو" to 6, "يوليو" to 7, "أغسطس" to 8,
                    "سبتمبر" to 9, "أكتوبر" to 10, "نوفمبر" to 11, "ديسمبر" to 12
                )

                // Try to find day, month, year in Arabic format
                val dayRegex = Regex("""(\d{1,2})\s+(يناير|فبراير|مارس|أبريل|مايو|يونيو|يوليو|أغسطس|سبتمبر|أكتوبر|نوفمبر|ديسمبر)\s+(\d{4})""")
                val arabicMatch = dayRegex.find(dateStr)

                if (arabicMatch != null) {
                    val day = arabicMatch.groupValues[1]
                    val monthName = arabicMatch.groupValues[2]
                    val year = arabicMatch.groupValues[3]
                    val month = arabicMonths[monthName] ?: 1
                    return "$day/$month/$year"
                }

                // Try ISO format
                val inputFormats = listOf(
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US),
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
                    SimpleDateFormat("yyyy-MM-dd", Locale.US)
                )

                var date: java.util.Date? = null
                for (format in inputFormats) {
                    try {
                        date = format.parse(dateStr)
                        if (date != null) break
                    } catch (e: Exception) {
                        continue
                    }
                }

                if (date != null) {
                    SimpleDateFormat("d/M/yyyy", Locale.US).format(date)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    class EpisodeDiffCallback : DiffUtil.ItemCallback<EpisodeSummary>() {
        override fun areItemsTheSame(oldItem: EpisodeSummary, newItem: EpisodeSummary): Boolean {
            return oldItem.number == newItem.number
        }

        override fun areContentsTheSame(oldItem: EpisodeSummary, newItem: EpisodeSummary): Boolean {
            return oldItem == newItem
        }
    }
}
