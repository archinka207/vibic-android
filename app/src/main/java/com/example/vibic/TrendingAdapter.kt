package com.example.vibic

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TrendingAdapter(
    private val items: List<TrendingChannel>
) : RecyclerView.Adapter<TrendingAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val rankNumber: TextView   = v.findViewById(R.id.rankNumber)
        val avatarFrame: FrameLayout = v.findViewById(R.id.avatarFrame)
        val avatarLetter: TextView = v.findViewById(R.id.avatarLetter)
        val channelName: TextView  = v.findViewById(R.id.channelName)
        val verifiedIcon: ImageView = v.findViewById(R.id.verifiedIcon)
        val subscribers: TextView  = v.findViewById(R.id.subscribers)
        val growth: TextView       = v.findViewById(R.id.growth)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trending_channel, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.rankNumber.text = c.rank.toString()
        holder.avatarLetter.text = c.letter
        holder.channelName.text = c.name
        holder.subscribers.text = "${c.subscribers} подписчиков"
        holder.growth.text = "↗ ${c.growth}"
        holder.verifiedIcon.visibility = if (c.verified) View.VISIBLE else View.GONE

        // круглый градиентный аватар
        val gd = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(c.colorStart, c.colorEnd)
        )
        gd.shape = GradientDrawable.OVAL
        holder.avatarFrame.background = gd
    }
}