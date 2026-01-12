package com.turkish.series.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.turkish.series.R
import com.turkish.series.models.SeriesSummary

/**
 * Adapter لعرض قائمة المسلسلات
 */
class SeriesAdapter(
    private val onItemClick: (SeriesSummary) -> Unit
) : ListAdapter<SeriesSummary, SeriesAdapter.SeriesViewHolder>(SeriesDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeriesViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_series, parent, false)
        return SeriesViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: SeriesViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SeriesViewHolder(
        itemView: View,
        private val onItemClick: (SeriesSummary) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val posterImage: ImageView = itemView.findViewById(R.id.posterImage)
        private val titleText: TextView = itemView.findViewById(R.id.titleText)
        private val yearText: TextView = itemView.findViewById(R.id.yearText)
        private val ratingText: TextView = itemView.findViewById(R.id.ratingText)
        private val episodesText: TextView = itemView.findViewById(R.id.episodesText)

        fun bind(series: SeriesSummary) {
            // Title
            titleText.text = series.title

            // Year
            yearText.text = series.year ?: ""
            yearText.visibility = if (series.year.isNullOrEmpty()) View.GONE else View.VISIBLE

            // Rating
            series.rating?.let {
                ratingText.text = String.format("%.1f", it)
                ratingText.visibility = View.VISIBLE
            } ?: run {
                ratingText.visibility = View.GONE
            }

            // Episodes count
            series.episodesCount?.let {
                episodesText.text = itemView.context.getString(R.string.episode_count, it)
                episodesText.visibility = View.VISIBLE
            } ?: run {
                episodesText.visibility = View.GONE
            }

            // Poster
            Glide.with(itemView.context)
                .load(series.poster)
                .placeholder(R.drawable.placeholder_poster)
                .error(R.drawable.placeholder_poster)
                .transition(DrawableTransitionOptions.withCrossFade())
                .centerCrop()
                .into(posterImage)

            // Click listener
            itemView.setOnClickListener {
                onItemClick(series)
            }
        }
    }

    class SeriesDiffCallback : DiffUtil.ItemCallback<SeriesSummary>() {
        override fun areItemsTheSame(oldItem: SeriesSummary, newItem: SeriesSummary): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SeriesSummary, newItem: SeriesSummary): Boolean {
            return oldItem == newItem
        }
    }
}
