package com.example.vibic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.vibic.network.MessageDto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MessageAdapter(private val myUserId: String) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<MessageDto>()

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    fun addMessage(message: MessageDto) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun setMessages(newMessages: List<MessageDto>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun findMessagePositionById(messageId: String?): Int {
        if (messageId == null) return -1
        return messages.indexOfFirst { it.id == messageId }
    }

    fun updateMessage(position: Int, realMessage: MessageDto) {
        if (position >= 0 && position < messages.size) {
            messages[position] = realMessage
            notifyItemChanged(position)
        }
    }

    fun removeMessageAt(position: Int) {
        if (position >= 0 && position < messages.size) {
            messages.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    private fun isSentByMe(message: MessageDto): Boolean {
        val senderId = message.senderId ?: return false
        return senderId == myUserId
    }

    override fun getItemViewType(position: Int): Int {
        return if (isSentByMe(messages[position])) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_sent, parent, false)
                SentMessageViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_received, parent, false)
                ReceivedMessageViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is SentMessageViewHolder -> holder.bind(message)
            is ReceivedMessageViewHolder -> holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    inner class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val messageTime: TextView = itemView.findViewById(R.id.messageTime)

        fun bind(message: MessageDto) {
            messageText.text = message.content ?: ""
            messageTime.text = formatTime(message.createdAt)
        }
    }

    inner class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val messageTime: TextView = itemView.findViewById(R.id.messageTime)

        fun bind(message: MessageDto) {
            messageText.text = message.content ?: ""
            messageTime.text = formatTime(message.createdAt)
        }
    }

    private fun formatTime(dateString: String?): String {
        if (dateString.isNullOrEmpty()) return ""

        val inputPatterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )

        for (pattern in inputPatterns) {
            try {
                val inputFormat = SimpleDateFormat(pattern, Locale.getDefault())
                inputFormat.timeZone = TimeZone.getTimeZone("UTC")

                val date = inputFormat.parse(dateString)
                if (date != null) {
                    val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    return outputFormat.format(date)
                }
            } catch (_: Exception) {
            }
        }

        return ""
    }
}