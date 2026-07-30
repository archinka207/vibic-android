package com.example.vibic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class BlacklistActivity : AppCompatActivity() {

    private lateinit var backButton: ImageView
    private lateinit var blacklistRecycler: RecyclerView
    private lateinit var emptyView: TextView

    private lateinit var adapter: BlockedAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_blacklist)

        backButton = findViewById(R.id.backButton)
        blacklistRecycler = findViewById(R.id.blacklistRecycler)
        emptyView = findViewById(R.id.emptyView)

        backButton.setOnClickListener { finish() }

        adapter = BlockedAdapter { user ->
            BlockedStore.remove(this, user.id)
            reload()
        }
        blacklistRecycler.layoutManager = LinearLayoutManager(this)
        blacklistRecycler.adapter = adapter

        reload()
    }

    private fun reload() {
        val users = BlockedStore.getAll(this)
        adapter.submitList(users)

        val isEmpty = users.isEmpty()
        emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        blacklistRecycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private class BlockedAdapter(
        private val onUnblock: (BlockedUser) -> Unit
    ) : RecyclerView.Adapter<BlockedAdapter.ViewHolder>() {

        private var items: List<BlockedUser> = emptyList()

        fun submitList(newItems: List<BlockedUser>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_blocked_user, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val user = items[position]
            holder.letter.text = user.letter
            holder.name.text = user.name
            holder.btnUnblock.setOnClickListener { onUnblock(user) }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val letter: TextView = itemView.findViewById(R.id.letter)
            val name: TextView = itemView.findViewById(R.id.name)
            val btnUnblock: TextView = itemView.findViewById(R.id.btnUnblock)
        }
    }
}
