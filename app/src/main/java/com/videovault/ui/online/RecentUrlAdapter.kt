package com.videovault.ui.online

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.videovault.databinding.ItemRecentUrlBinding
import java.text.SimpleDateFormat
import java.util.*

class RecentUrlAdapter(
    private val onClick: (RecentUrl) -> Unit,
    private val onDelete: (RecentUrl) -> Unit
) : ListAdapter<RecentUrl, RecentUrlAdapter.VH>(object : DiffUtil.ItemCallback<RecentUrl>() {
    override fun areItemsTheSame(a: RecentUrl, b: RecentUrl) = a.url == b.url
    override fun areContentsTheSame(a: RecentUrl, b: RecentUrl) = a == b
}) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(ItemRecentUrlBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(h: VH, position: Int) { h.bind(getItem(position)) }

    inner class VH(private val b: ItemRecentUrlBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: RecentUrl) {
            b.tvTitle.text = item.title; b.tvUrl.text = item.url
            b.tvTime.text = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(item.timestamp))
            b.root.setOnClickListener { onClick(item) }; b.btnDelete.setOnClickListener { onDelete(item) }
        }
    }
}
