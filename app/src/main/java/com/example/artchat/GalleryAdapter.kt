package com.example.artchat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.artchat.R
import com.example.artchat.model.DrawingItem
import java.text.SimpleDateFormat
import java.util.*

class GalleryAdapter(
    private val drawings: List<DrawingItem>,
    private val onItemClick: (DrawingItem) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_drawing, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(drawings[position])
        holder.itemView.setOnClickListener {
            onItemClick(drawings[position])
        }
    }

    override fun getItemCount(): Int = drawings.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)

        fun bind(drawing: DrawingItem) {
            ivThumbnail.setImageBitmap(drawing.thumbnail)
            tvTitle.text = drawing.title
            tvDate.text = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                .format(Date(drawing.date))
        }
    }
}