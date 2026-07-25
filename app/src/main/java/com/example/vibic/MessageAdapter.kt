package com.example.vibic

import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.example.vibic.network.MessageDto
import com.example.vibic.network.ReplyPreview
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MessageAdapter(
    private val myUserId: String,
    private val onMessageLongClick: (MessageDto, View) -> Unit = { _, _ -> },
    private val onViewOncePlayed: (MessageDto) -> Unit = {} // НОВЫЙ КОЛЛБЭК
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // Исходный список сообщений (без разделителей). Внешние позиции из ChatDetailActivity
    // (updateMessage / removeMessageAt / findMessagePositionById) адресуют именно его.
    private val messages = mutableListOf<MessageDto>()

    // Плоский список для отрисовки: сообщения + виртуальные DateHeader'ы между ними.
    private val displayItems = mutableListOf<Any>()

    // Виртуальный элемент-разделитель даты
    data class DateHeader(val label: String)

    // Единственный активный MediaPlayer — только одно аудио играет одновременно
    private var activeMediaPlayer: MediaPlayer? = null
    private var activePlayButton: ImageView? = null
    private var activeSeekBar: SeekBar? = null
    private var activeDurationText: TextView? = null
    private val progressHandler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    companion object {
        private const val VIEW_TYPE_SENT          = 1
        private const val VIEW_TYPE_RECEIVED      = 2
        private const val VIEW_TYPE_AUDIO_SENT    = 3
        private const val VIEW_TYPE_AUDIO_RECV    = 4
        private const val VIEW_TYPE_VIDEO_SENT    = 5
        private const val VIEW_TYPE_VIDEO_RECV    = 6
        private const val VIEW_TYPE_DATE_HEADER   = 7
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    fun addMessage(message: MessageDto) {
        messages.add(message)
        rebuildDisplayItems()
    }

    fun setMessages(newMessages: List<MessageDto>) {
        messages.clear()
        messages.addAll(newMessages)
        rebuildDisplayItems()
    }

    /** Возвращает позицию в исходном списке сообщений (как и раньше — индекс без учёта разделителей). */
    fun findMessagePositionById(messageId: String?): Int {
        if (messageId == null) return -1
        return messages.indexOfFirst { it.id == messageId }
    }

    /** position — индекс в исходном списке messages (как было до разделителей). */
    fun updateMessage(position: Int, realMessage: MessageDto) {
        if (position >= 0 && position < messages.size) {
            messages[position] = realMessage
            rebuildDisplayItems()
        }
    }

    /** position — индекс в исходном списке messages. */
    fun removeMessageAt(position: Int) {
        if (position >= 0 && position < messages.size) {
            messages.removeAt(position)
            rebuildDisplayItems()
        }
    }

    // ─── Building display list with date separators ──────────────────────────

    private fun rebuildDisplayItems() {
        displayItems.clear()
        var lastDayKey: String? = null
        for (msg in messages) {
            val date = parseDate(msg.createdAt)
            val dayKey = date?.let { dayKey(it) }
            if (dayKey != null && dayKey != lastDayKey) {
                displayItems.add(DateHeader(formatDayLabel(date)))
                lastDayKey = dayKey
            }
            displayItems.add(msg)
        }
        notifyDataSetChanged()
    }

    // ─── RecyclerView ─────────────────────────────────────────────────────────

    private fun isSentByMe(message: MessageDto): Boolean {
        val senderId = message.senderId ?: return false
        return senderId == myUserId
    }

    /**
     * Заполняет блок цитаты в пузыре, если сообщение является ответом.
     * Данные берём из message.replyTo (приходит с сервера). Если по какой-то
     * причине replyTo нет, но есть replyToId (например, у только что отправленного
     * локального "оптимистичного" сообщения) — пытаемся найти оригинал в уже
     * загруженном списке messages.
     */
    private fun bindReplyQuote(
        message: MessageDto,
        container: LinearLayout,
        senderNameView: TextView,
        textView: TextView
    ) {
        android.util.Log.d("ReplyDebug", "content=${message.content} | replyToId=${message.replyToId} | replyTo=${message.replyTo?.content} | msgInList=${messages.size}")
        // Не ответ — прячем блок.
        if (message.replyToId == null && message.replyTo == null) {
            container.visibility = View.GONE
            return
        }

        // Источник данных: серверный replyTo, иначе ищем оригинал локально по id.
        val original: ReplyPreview? = message.replyTo ?: run {
            val id = message.replyToId
            if (id != null) {
                messages.firstOrNull { it.id == id }?.let { orig ->
                    ReplyPreview(
                        id = orig.id,
                        content = orig.content,
                        senderId = orig.senderId,
                        sender = orig.sender
                    )
                }
            } else null
        }

        // Если оригинал так и не нашли, но это всё же ответ — показываем заглушку,
        // а не прячем цитату совсем.
        if (original == null) {
            senderNameView.text = "Сообщение"
            textView.text = "Сообщение недоступно"
            container.visibility = View.VISIBLE
            return
        }

        val senderName = when {
            original.sender?.firstName?.isNotBlank() == true ->
                listOfNotNull(
                    original.sender.firstName?.takeIf { it.isNotBlank() },
                    original.sender.lastName?.takeIf { it.isNotBlank() }
                ).joinToString(" ")
            original.senderId == myUserId -> "Вы"
            else -> "Сообщение"
        }

        senderNameView.text = senderName
        textView.text = original.content?.takeIf { it.isNotBlank() } ?: "Сообщение"
        container.visibility = View.VISIBLE
    }

    override fun getItemViewType(position: Int): Int {
        val item = displayItems[position]
        if (item is DateHeader) return VIEW_TYPE_DATE_HEADER
        val msg = item as MessageDto
        val sent = isSentByMe(msg)
        return when (msg.type) {
            "audio", "audio_once" -> if (sent) VIEW_TYPE_AUDIO_SENT else VIEW_TYPE_AUDIO_RECV
            "video_note"          -> if (sent) VIEW_TYPE_VIDEO_SENT else VIEW_TYPE_VIDEO_RECV
            else                  -> if (sent) VIEW_TYPE_SENT       else VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        fun inflate(layout: Int) = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return when (viewType) {
            VIEW_TYPE_SENT        -> SentMessageViewHolder(inflate(R.layout.item_message_sent))
            VIEW_TYPE_RECEIVED    -> ReceivedMessageViewHolder(inflate(R.layout.item_message_received))
            VIEW_TYPE_AUDIO_SENT  -> AudioViewHolder(inflate(R.layout.item_message_audio_sent))
            VIEW_TYPE_AUDIO_RECV  -> AudioViewHolder(inflate(R.layout.item_message_audio_received))
            VIEW_TYPE_VIDEO_SENT  -> VideoNoteViewHolder(inflate(R.layout.item_message_video_note_sent))
            VIEW_TYPE_VIDEO_RECV  -> VideoNoteViewHolder(inflate(R.layout.item_message_video_note_received))
            VIEW_TYPE_DATE_HEADER -> DateHeaderViewHolder(inflate(R.layout.item_message_date_separator))
            else                  -> SentMessageViewHolder(inflate(R.layout.item_message_sent))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        android.util.Log.d("ReplyDebug", "bind holder=${holder.javaClass.simpleName} pos=$position")
        val item = displayItems[position]
        when (holder) {
            is DateHeaderViewHolder      -> holder.bind(item as DateHeader)
            is SentMessageViewHolder     -> holder.bind(item as MessageDto)
            is ReceivedMessageViewHolder -> holder.bind(item as MessageDto)
            is AudioViewHolder           -> holder.bind(item as MessageDto)
            is VideoNoteViewHolder       -> holder.bind(item as MessageDto)
        }
    }

    override fun getItemCount(): Int = displayItems.size

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is VideoNoteViewHolder) {
            holder.release()
        }
    }

    // ─── Date header ViewHolder ───────────────────────────────────────────────

    inner class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateText: TextView = itemView.findViewById(R.id.dateText)
        fun bind(header: DateHeader) {
            dateText.text = header.label
        }
    }

    // ─── Text ViewHolders ─────────────────────────────────────────────────────

    inner class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val messageTime: TextView = itemView.findViewById(R.id.messageTime)
        private val replyContainer: LinearLayout = itemView.findViewById(R.id.replyContainer)
        private val replySenderName: TextView = itemView.findViewById(R.id.replySenderName)
        private val replyText: TextView = itemView.findViewById(R.id.replyText)

        fun bind(message: MessageDto) {
            messageText.text = message.content ?: ""
            messageTime.text = formatTime(message.createdAt)
            bindReplyQuote(message, replyContainer, replySenderName, replyText)
            itemView.setOnLongClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                onMessageLongClick(message, itemView)
                true
            }
        }
    }

    inner class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val messageTime: TextView = itemView.findViewById(R.id.messageTime)
        private val replyContainer: LinearLayout = itemView.findViewById(R.id.replyContainer)
        private val replySenderName: TextView = itemView.findViewById(R.id.replySenderName)
        private val replyText: TextView = itemView.findViewById(R.id.replyText)

        fun bind(message: MessageDto) {
            messageText.text = message.content ?: ""
            messageTime.text = formatTime(message.createdAt)
            bindReplyQuote(message, replyContainer, replySenderName, replyText)
            itemView.setOnLongClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                onMessageLongClick(message, itemView)
                true
            }
        }
    }

    // ─── Audio ViewHolder ─────────────────────────────────────────────────────

    inner class AudioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val playBtn: ImageView  = itemView.findViewById(R.id.playPauseButton)
        private val seekBar: SeekBar    = itemView.findViewById(R.id.audioProgress)
        private val duration: TextView  = itemView.findViewById(R.id.audioDuration)
        private val time: TextView      = itemView.findViewById(R.id.messageTime)

        fun bind(message: MessageDto) {
            val url = message.content ?: return

            // Визуальное выделение "разового" ГС
            if (message.type == "audio_once") {
                duration.setTextColor(MaterialColors.getColor(duration, R.attr.vibicDanger))
                time.text = "1x • ${formatTime(message.createdAt)}"
            } else {
                duration.setTextColor(MaterialColors.getColor(duration, R.attr.vibicTextSecondary))
                time.text = formatTime(message.createdAt)
            }

            seekBar.progress = 0
            duration.text = "0:00"
            updatePlayIcon(false)

            playBtn.setOnClickListener {
                if (activeMediaPlayer != null && activePlayButton == playBtn) {
                    togglePause()
                } else {
                    stopCurrentAudio()
                    startAudio(url, message)
                }
            }

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) activeMediaPlayer?.seekTo(progress)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        private fun startAudio(url: String, message: MessageDto) {
            activePlayButton = playBtn
            activeSeekBar = seekBar
            activeDurationText = duration

            val mp = MediaPlayer()
            activeMediaPlayer = mp

            mp.setDataSource(url)
            mp.setOnPreparedListener { player ->
                seekBar.max = player.duration
                player.start()
                updatePlayIcon(true)
                startProgressUpdates()
            }
            mp.setOnCompletionListener {
                stopCurrentAudio()
                // Если это разовое ГС - удаляем после проигрывания
                if (message.type == "audio_once") {
                    onViewOncePlayed(message)
                }
            }
            mp.setOnErrorListener { _, _, _ ->
                stopCurrentAudio()
                true
            }
            mp.prepareAsync()
        }

        private fun togglePause() {
            val mp = activeMediaPlayer ?: return
            if (mp.isPlaying) {
                mp.pause()
                updatePlayIcon(false)
                stopProgressUpdates()
            } else {
                mp.start()
                updatePlayIcon(true)
                startProgressUpdates()
            }
        }

        private fun updatePlayIcon(playing: Boolean) {
            playBtn.setImageResource(
                if (playing) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )
        }
    }

    // ─── Video Note ViewHolder ────────────────────────────────────────────────

    inner class VideoNoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val videoView: VideoView   = itemView.findViewById(R.id.videoView)
        private val playOverlay: FrameLayout = itemView.findViewById(R.id.playOverlay)
        private val playIcon: ImageView    = itemView.findViewById(R.id.playIcon)
        private val time: TextView         = itemView.findViewById(R.id.messageTime)

        fun bind(message: MessageDto) {
            time.text = formatTime(message.createdAt)
            val url = message.content ?: return

            playOverlay.visibility = View.VISIBLE

            videoView.setVideoURI(Uri.parse(url))
            videoView.setOnPreparedListener { mp ->
                mp.isLooping = false
            }
            videoView.setOnCompletionListener {
                playOverlay.visibility = View.VISIBLE
                playIcon.setImageResource(android.R.drawable.ic_media_play)
            }

            playOverlay.setOnClickListener {
                if (videoView.isPlaying) {
                    videoView.pause()
                    playIcon.setImageResource(android.R.drawable.ic_media_play)
                } else {
                    videoView.start()
                    playOverlay.visibility = View.GONE
                }
            }

            videoView.setOnClickListener {
                if (videoView.isPlaying) {
                    videoView.pause()
                    playOverlay.visibility = View.VISIBLE
                    playIcon.setImageResource(android.R.drawable.ic_media_play)
                }
            }
        }

        fun release() {
            videoView.stopPlayback()
        }
    }

    // ─── Audio playback helpers ───────────────────────────────────────────────

    private fun stopCurrentAudio() {
        stopProgressUpdates()
        activeMediaPlayer?.release()
        activeMediaPlayer = null
        activePlayButton?.setImageResource(android.R.drawable.ic_media_play)
        activeSeekBar?.progress = 0
        activeDurationText?.text = "0:00"
        activePlayButton = null
        activeSeekBar = null
        activeDurationText = null
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressRunnable = object : Runnable {
            override fun run() {
                val mp = activeMediaPlayer ?: return
                if (mp.isPlaying) {
                    activeSeekBar?.progress = mp.currentPosition
                    val elapsed = mp.currentPosition / 1000
                    activeDurationText?.text = "%d:%02d".format(elapsed / 60, elapsed % 60)
                    progressHandler.postDelayed(this, 200)
                }
            }
        }
        progressHandler.post(progressRunnable!!)
    }

    private fun stopProgressUpdates() {
        progressRunnable?.let { progressHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    // ─── Date helpers ─────────────────────────────────────────────────────────

    private fun parseDate(dateString: String?): Date? {
        if (dateString.isNullOrEmpty()) return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in patterns) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.getDefault())
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                val date = fmt.parse(dateString)
                if (date != null) return date
            } catch (_: Exception) {}
        }
        return null
    }

    /** Ключ дня в локальной таймзоне для группировки. */
    private fun dayKey(date: Date): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        fmt.timeZone = TimeZone.getDefault()
        return fmt.format(date)
    }

    /** «Сегодня», «Вчера», «15 мая» или «15 мая 2024». */
    private fun formatDayLabel(date: Date): String {
        val msgCal = Calendar.getInstance().apply { time = date }
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        fun sameDay(a: Calendar, b: Calendar): Boolean =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                    a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

        return when {
            sameDay(msgCal, today)     -> "Сегодня"
            sameDay(msgCal, yesterday) -> "Вчера"
            msgCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) ->
                SimpleDateFormat("d MMMM", Locale("ru")).format(date)
            else ->
                SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(date)
        }
    }

    // ─── Time formatter ───────────────────────────────────────────────────────

    private fun formatTime(dateString: String?): String {
        val date = parseDate(dateString) ?: return ""
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    }
}