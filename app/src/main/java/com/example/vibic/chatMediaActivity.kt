package com.example.vibic

import com.google.android.material.color.MaterialColors
import androidx.core.content.ContextCompat
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vibic.network.ApiClient
import com.example.vibic.network.MessageDto
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ChatMediaActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CHAT_ID   = "chat_id"
        const val EXTRA_CHAT_NAME = "chat_name"
    }

    private lateinit var backButton: ImageView
    private lateinit var mediaCount: TextView
    private lateinit var mediaGrid: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tabPhoto: TextView
    private lateinit var tabVoice: TextView
    private lateinit var tabFiles: TextView
    private lateinit var tabLinks: TextView

    private var chatId   = ""
    private var chatName = ""
    private var allMessages = listOf<MessageDto>()
    private var currentTab = "photo"

    // ── MediaPlayer для голосовых ──────────────────────────────────────────────
    private var activePlayer: MediaPlayer? = null
    private var activePlayBtn: ImageView? = null
    private var activeSeek: SeekBar? = null
    private val handler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_chat_media)

        chatId   = intent.getStringExtra(EXTRA_CHAT_ID)   ?: ""
        chatName = intent.getStringExtra(EXTRA_CHAT_NAME) ?: ""

        backButton  = findViewById(R.id.backButton)
        mediaCount  = findViewById(R.id.mediaCount)
        mediaGrid   = findViewById(R.id.mediaGrid)
        emptyState  = findViewById(R.id.emptyState)
        emptyText   = findViewById(R.id.emptyText)
        progressBar = findViewById(R.id.progressBar)
        tabPhoto    = findViewById(R.id.tabPhoto)
        tabVoice    = findViewById(R.id.tabVoice)
        tabFiles    = findViewById(R.id.tabFiles)
        tabLinks    = findViewById(R.id.tabLinks)

        backButton.setOnClickListener { finish() }

        tabPhoto.setOnClickListener { switchTab("photo") }
        tabVoice.setOnClickListener { switchTab("voice") }
        tabFiles.setOnClickListener { switchTab("files") }
        tabLinks.setOnClickListener { switchTab("links") }

        loadMessages()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCurrentAudio()
    }

    // ── Загрузка сообщений ─────────────────────────────────────────────────────

    private fun loadMessages() {
        progressBar.visibility = View.VISIBLE
        emptyState.visibility  = View.GONE

        lifecycleScope.launch {
            try {
                val resp = ApiClient.apiService.getMessages(chatId)
                if (resp.isSuccessful) {
                    allMessages = resp.body() ?: emptyList()
                    showTab(currentTab)
                } else {
                    showEmpty("Не удалось загрузить медиа")
                }
            } catch (e: Exception) {
                showEmpty("Ошибка загрузки")
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    // ── Табы ───────────────────────────────────────────────────────────────────

    private fun switchTab(tab: String) {
        currentTab = tab
        stopCurrentAudio()

        // Обновляем вид кнопок
        val selected   = R.drawable.bg_tab_selected
        val unselected = R.drawable.bg_tab_unselected

        tabPhoto.setBackgroundResource(if (tab == "photo") selected else unselected)
        tabVoice.setBackgroundResource(if (tab == "voice") selected else unselected)
        tabFiles.setBackgroundResource(if (tab == "files") selected else unselected)
        tabLinks.setBackgroundResource(if (tab == "links") selected else unselected)

        tabPhoto.setTextColor(MaterialColors.getColor(tabPhoto, if (tab == "photo") R.attr.vibicTextPrimary else R.attr.vibicTextSecondary))
        tabVoice.setTextColor(MaterialColors.getColor(tabVoice, if (tab == "voice") R.attr.vibicTextPrimary else R.attr.vibicTextSecondary))
        tabFiles.setTextColor(MaterialColors.getColor(tabFiles, if (tab == "files") R.attr.vibicTextPrimary else R.attr.vibicTextSecondary))
        tabLinks.setTextColor(MaterialColors.getColor(tabLinks, if (tab == "links") R.attr.vibicTextPrimary else R.attr.vibicTextSecondary))

        showTab(tab)
    }

    private fun showTab(tab: String) {
        val items = when (tab) {
            "photo" -> allMessages.filter { isPhotoOrVideo(it) }
            "voice" -> allMessages.filter { it.type == "audio" }
            "files" -> allMessages.filter { isFile(it) }
            "links" -> allMessages.filter { isLink(it) }
            else    -> emptyList()
        }

        mediaCount.text = "${items.size}"

        if (items.isEmpty()) {
            val label = when (tab) {
                "photo" -> "Нет фото и видео"
                "voice" -> "Нет голосовых сообщений"
                "files" -> "Нет файлов"
                "links" -> "Нет ссылок"
                else    -> "Пусто"
            }
            showEmpty(label)
            return
        }

        emptyState.visibility = View.GONE
        mediaGrid.visibility  = View.VISIBLE

        when (tab) {
            "photo" -> {
                mediaGrid.layoutManager = GridLayoutManager(this, 3)
                mediaGrid.adapter = MediaGridAdapter(items)
            }
            "voice" -> {
                mediaGrid.layoutManager = LinearLayoutManager(this)
                mediaGrid.adapter = AudioListAdapter(items)
            }
            "files" -> {
                mediaGrid.layoutManager = LinearLayoutManager(this)
                mediaGrid.adapter = AudioListAdapter(items)
            }
            "links" -> {
                mediaGrid.layoutManager = LinearLayoutManager(this)
                mediaGrid.adapter = LinksAdapter(items)
            }
        }
    }

    private fun isPhotoOrVideo(msg: MessageDto): Boolean {
        val url = msg.content ?: return false
        return msg.type == "video_note" ||
                url.contains(".jpg", ignoreCase = true) ||
                url.contains(".jpeg", ignoreCase = true) ||
                url.contains(".png", ignoreCase = true) ||
                url.contains(".mp4", ignoreCase = true) ||
                url.contains(".mov", ignoreCase = true)
    }

    private fun isLink(msg: MessageDto): Boolean {
        val text = msg.content ?: return false
        return text.startsWith("http://") || text.startsWith("https://")
    }

    private fun isFile(msg: MessageDto): Boolean {
        val url = msg.content ?: return false
        return url.contains(".pdf", ignoreCase = true) ||
                url.contains(".doc", ignoreCase = true) ||
                url.contains(".zip", ignoreCase = true) ||
                url.contains(".txt", ignoreCase = true)
    }

    private fun showEmpty(text: String) {
        mediaGrid.visibility  = View.GONE
        emptyState.visibility = View.VISIBLE
        emptyText.text        = text
    }

    // ── Grid Adapter (фото/видео) ──────────────────────────────────────────────

    inner class MediaGridAdapter(
        private val items: List<MessageDto>
    ) : RecyclerView.Adapter<MediaGridAdapter.VH>() {

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val thumb: ImageView     = itemView.findViewById(R.id.mediaThumb)
            val videoIcon: ImageView = itemView.findViewById(R.id.videoIcon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_media_grid, parent, false)

            // Квадратные ячейки
            val size = (parent.width - 12) / 3  // целое деление с учётом отступов
            v.layoutParams = RecyclerView.LayoutParams(size, size).apply {
                setMargins(2, 2, 2, 2)
            }
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val msg = items[position]
            val url = msg.content ?: return

            if (msg.type == "video_note") {
                holder.videoIcon.visibility = View.VISIBLE
                holder.thumb.setImageResource(android.R.drawable.ic_media_play)
            } else {
                holder.videoIcon.visibility = View.GONE
                // Загрузка картинки через Glide если есть, иначе placeholder
                try {
                    com.bumptech.glide.Glide.with(holder.thumb.context)
                        .load(url)
                        .centerCrop()
                        .placeholder(R.drawable.bg_chat_input_capsule)
                        .into(holder.thumb)
                } catch (e: Exception) {
                    holder.thumb.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            }

            holder.itemView.setOnClickListener {
                Toast.makeText(this@ChatMediaActivity, "Просмотр в разработке", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Audio List Adapter (голосовые) ─────────────────────────────────────────

    inner class AudioListAdapter(
        private val items: List<MessageDto>
    ) : RecyclerView.Adapter<AudioListAdapter.VH>() {

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val playBtn:  ImageView = itemView.findViewById(R.id.playButton)
            val seek:     SeekBar   = itemView.findViewById(R.id.audioSeek)
            val date:     TextView  = itemView.findViewById(R.id.audioDate)
            val duration: TextView  = itemView.findViewById(R.id.audioDuration)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_media_audio, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val msg = items[position]
            val url = msg.content ?: return

            holder.date.text     = formatDate(msg.createdAt)
            holder.duration.text = "0:00"
            holder.seek.progress = 0
            holder.playBtn.setImageResource(android.R.drawable.ic_media_play)

            holder.playBtn.setOnClickListener {
                if (activePlayer != null && activePlayBtn == holder.playBtn) {
                    togglePause(holder.playBtn)
                } else {
                    stopCurrentAudio()
                    startAudio(url, holder.playBtn, holder.seek, holder.duration)
                }
            }

            holder.seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) activePlayer?.seekTo(progress)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
    }

    // ── Audio helpers ──────────────────────────────────────────────────────────

    private fun startAudio(url: String, btn: ImageView, seek: SeekBar, duration: TextView) {
        activePlayBtn = btn
        activeSeek    = seek

        val mp = MediaPlayer()
        activePlayer  = mp

        mp.setDataSource(url)
        mp.setOnPreparedListener { player ->
            seek.max = player.duration
            player.start()
            btn.setImageResource(android.R.drawable.ic_media_pause)
            startProgressUpdates(player, seek, duration)
        }
        mp.setOnCompletionListener { stopCurrentAudio() }
        mp.setOnErrorListener { _, _, _ -> stopCurrentAudio(); true }
        mp.prepareAsync()
    }

    private fun togglePause(btn: ImageView) {
        val mp = activePlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            btn.setImageResource(android.R.drawable.ic_media_play)
            progressRunnable?.let { handler.removeCallbacks(it) }
        } else {
            mp.start()
            btn.setImageResource(android.R.drawable.ic_media_pause)
            startProgressUpdates(mp, activeSeek!!, null)
        }
    }

    private fun startProgressUpdates(mp: MediaPlayer, seek: SeekBar, duration: TextView?) {
        progressRunnable?.let { handler.removeCallbacks(it) }
        progressRunnable = object : Runnable {
            override fun run() {
                if (mp.isPlaying) {
                    seek.progress = mp.currentPosition
                    val sec = mp.currentPosition / 1000
                    duration?.text = "%d:%02d".format(sec / 60, sec % 60)
                    handler.postDelayed(this, 200)
                }
            }
        }
        handler.post(progressRunnable!!)
    }

    private fun stopCurrentAudio() {
        progressRunnable?.let { handler.removeCallbacks(it) }
        activePlayer?.release()
        activePlayer = null
        activePlayBtn?.setImageResource(android.R.drawable.ic_media_play)
        activeSeek?.progress = 0
        activePlayBtn = null
        activeSeek    = null
    }

    // ── Links Adapter ──────────────────────────────────────────────────────────

    inner class LinksAdapter(
        private val items: List<MessageDto>
    ) : RecyclerView.Adapter<LinksAdapter.VH>() {

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val url:  TextView = itemView.findViewById(R.id.linkUrl)
            val date: TextView = itemView.findViewById(R.id.linkDate)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_media_link, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val msg = items[position]
            holder.url.text  = msg.content ?: ""
            holder.date.text = formatDate(msg.createdAt)
            holder.itemView.setOnClickListener {
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(msg.content))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@ChatMediaActivity, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ── Форматирование даты ────────────────────────────────────────────────────

    private fun formatDate(dateStr: String?): String {
        if (dateStr.isNullOrEmpty()) return ""
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault())
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            val date = fmt.parse(dateStr) ?: return ""
            SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru")).format(date)
        } catch (e: Exception) { "" }
    }
}