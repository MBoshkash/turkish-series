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

/**
 * Adapter لعرض قائمة الحلقات
 */
class EpisodeAdapter(
    private val onItemClick: (EpisodeSummary) -> Unit
) : ListAdapter<EpisodeSummary, EpisodeAdapter.EpisodeViewHolder>(EpisodeDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_episode, parent, false)
        return EpisodeViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class EpisodeViewHolder(
        itemView: View,
        private val onItemClick: (EpisodeSummary) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val episodeNumber: TextView = itemView.findViewById(R.id.episodeNumber)
        private val episodeTitle: TextView = itemView.findViewById(R.id.episodeTitle)
        private val episodeDate: TextView = itemView.findViewById(R.id.episodeDate)
        private val serversCount: TextView = itemView.findViewById(R.id.serversCount)

        fun bind(episode: EpisodeSummary) {
            // Episode number
            episodeNumber.text = episode.number.toString()

            // Title
            episodeTitle.text = episode.title ?: "الحلقة ${episode.number}"

            // Date
            episode.dateAdded?.let {
                episodeDate.text = it
                episodeDate.visibility = View.VISIBLE
            } ?: run {
                episodeDate.visibility = View.GONE
            }

            // Servers count
            episode.serversCount?.let {
                serversCount.text = "$it سيرفر"
                serversCount.visibility = View.VISIBLE
            } ?: run {
                serversCount.visibility = View.GONE
            }

            // Click listener
            itemView.setOnClickListener {
                onItemClick(episode)
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
