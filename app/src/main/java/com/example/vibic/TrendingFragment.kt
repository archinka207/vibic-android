package com.example.vibic

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class TrendingFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_trending, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val all = TrendingMock.channels()
        val top3 = all.take(3)
        val rest = all.drop(3)

        // top3: [0]=1место(центр), [1]=2место(слева), [2]=3место(справа)
        top3.getOrNull(0)?.let { fillPodium(view, it, R.id.p1Avatar, R.id.p1Letter, R.id.p1Name, R.id.p1Subs, R.id.p1Growth) }
        top3.getOrNull(1)?.let { fillPodium(view, it, R.id.p2Avatar, R.id.p2Letter, R.id.p2Name, R.id.p2Subs, R.id.p2Growth) }
        top3.getOrNull(2)?.let { fillPodium(view, it, R.id.p3Avatar, R.id.p3Letter, R.id.p3Name, R.id.p3Subs, R.id.p3Growth) }

        // список 4..N
        val list = view.findViewById<RecyclerView>(R.id.trendingList)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = TrendingAdapter(rest)

        // аватар "твоей позиции"
        circleGradient(
            view.findViewById(R.id.myAvatar),
            android.graphics.Color.parseColor("#22D3EE"),
            android.graphics.Color.parseColor("#0891B2")
        )
    }

    private fun fillPodium(
        root: View, c: TrendingChannel,
        avatarId: Int, letterId: Int, nameId: Int, subsId: Int, growthId: Int
    ) {
        circleGradient(root.findViewById(avatarId), c.colorStart, c.colorEnd)
        root.findViewById<TextView>(letterId).text = c.letter
        root.findViewById<TextView>(nameId).text = c.name
        root.findViewById<TextView>(subsId).text = c.subscribers
        root.findViewById<TextView>(growthId).text = "↗ ${c.growth}"
    }

    private fun circleGradient(frame: FrameLayout, start: Int, end: Int) {
        val gd = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(start, end))
        gd.shape = GradientDrawable.OVAL
        frame.background = gd
    }
}