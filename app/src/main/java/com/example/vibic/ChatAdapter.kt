package com.example.vibic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.vibic.databinding.ItemChatBinding

class ChatAdapter(private val onItemClick: (Chat) -> Unit) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private var chats = listOf<Chat>()

    fun submitList(newChats: List<Chat>) {
        chats = newChats
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = chats[position]
        holder.bind(chat)
        holder.itemView.setOnClickListener { onItemClick(chat) }
    }

    override fun getItemCount() = chats.size

    inner class ChatViewHolder(private val binding: ItemChatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: Chat) {
            binding.chatName.text = chat.name
            binding.lastMessage.text = chat.lastMessage
            binding.lastMessageTime.text = chat.lastMessageTime
            binding.avatarText.text = chat.name.take(1).uppercase()
            binding.avatarText.visibility = View.VISIBLE
            binding.avatarImage.visibility = View.GONE
        }
    }
}