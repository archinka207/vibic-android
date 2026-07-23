package com.example.vibic

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.vibic.databinding.ItemChatBinding
import java.util.Locale

/**
 * Адаптер для списка чатов. Поддерживает:
 *  - обычные элементы-чаты,
 *  - заголовок секции (например, "Контакты"),
 *  - элементы-контакты, по которым кликом создаётся новый чат.
 *
 * Закреплённые чаты помечаются булавкой справа (под временем),
 * если у них нет непрочитанных сообщений.
 *
 * Долгое нажатие на чате имеет ДВА режима:
 *  - по аватарке (avatarCard)   → onChatPeek(chat, view) — превью переписки + меню;
 *  - по остальной строке (root) → onChatMenu(chat, view) — только меню действий.
 * Короткий тап по строке         → onChatClick(chat) (открыть переписку).
 */
class ChatAdapter(
    private val onChatClick: (Chat) -> Unit,
    private val onContactClick: (UserResponse) -> Unit = {},
    private val onChatPeek: (Chat, View) -> Unit = { _, _ -> },
    private val onChatMenu: (Chat, View) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Row {
        data class ChatRow(val chat: Chat) : Row()
        data class ContactRow(val user: UserResponse) : Row()
        data class HeaderRow(val title: String) : Row()
    }

    private var rows: List<Row> = emptyList()
    private var highlightQuery: String = ""

    /** Старая сигнатура — обратная совместимость с местами, где используется ещё. */
    fun submitList(newChats: List<Chat>) {
        rows = newChats.map { Row.ChatRow(it) }
        notifyDataSetChanged()
    }

    /** Новая сигнатура: подаём готовый плоский список со всеми типами. */
    fun submitRows(newRows: List<Row>) {
        rows = newRows
        notifyDataSetChanged()
    }

    fun setHighlightQuery(query: String) {
        val normalized = query.trim()
        if (normalized == highlightQuery) return
        highlightQuery = normalized
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.HeaderRow  -> TYPE_HEADER
        is Row.ChatRow    -> TYPE_CHAT
        is Row.ContactRow -> TYPE_CONTACT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(createHeaderView(parent))
            TYPE_CHAT, TYPE_CONTACT -> {
                val binding = ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                if (viewType == TYPE_CHAT) ChatViewHolder(binding) else ContactViewHolder(binding)
            }
            else -> error("Unknown viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.HeaderRow  -> (holder as HeaderViewHolder).bind(row.title)
            is Row.ChatRow    -> (holder as ChatViewHolder).bind(row.chat)
            is Row.ContactRow -> (holder as ContactViewHolder).bind(row.user)
        }
    }

    override fun getItemCount() = rows.size

    // ── Создание view для заголовка секции ────────────────────────────────────

    private fun createHeaderView(parent: ViewGroup): TextView {
        val context = parent.context
        val density = context.resources.displayMetrics.density
        return TextView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(
                (16 * density).toInt(),
                (16 * density).toInt(),
                (16 * density).toInt(),
                (4 * density).toInt()
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.START
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }

    // ── ViewHolders ───────────────────────────────────────────────────────────

    inner class HeaderViewHolder(private val view: TextView) : RecyclerView.ViewHolder(view) {
        fun bind(title: String) {
            view.text = title.uppercase(LOCALE_RU)
        }
    }

    inner class ChatViewHolder(private val binding: ItemChatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: Chat) {
            binding.chatName.text        = highlight(chat.name)
            binding.lastMessage.text     = highlight(chat.lastMessage)
            binding.lastMessageTime.text = chat.lastMessageTime
            binding.lastMessageTime.visibility = View.VISIBLE
            binding.avatarText.text      = chat.name.take(1).uppercase(LOCALE_RU)
            binding.avatarText.visibility = View.VISIBLE
            binding.avatarImage.visibility = View.GONE

            // Иконка "без звука"
            binding.muteIcon.visibility = if (chat.isMuted) View.VISIBLE else View.GONE

            // Непрочитанные
            if (chat.unreadCount > 0) {
                binding.unreadBadge.visibility = View.VISIBLE
                binding.unreadBadge.text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString()
            } else {
                binding.unreadBadge.visibility = View.GONE
            }

            // Булавка справа у закреплённого чата (если нет непрочитанных)
            binding.pinIndicator.visibility =
                if (chat.isPinned && chat.unreadCount == 0) View.VISIBLE else View.GONE

            // Короткий тап — открыть переписку
            binding.root.setOnClickListener { onChatClick(chat) }

            // Долгое нажатие по строке (кроме аватара) — только меню действий.
            binding.root.setOnLongClickListener {
                binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                onChatMenu(chat, binding.root)
                true
            }

            // Долгое нажатие по аватарке — превью переписки + меню.
            // Аватар лежит поверх root, поэтому перехватывает long-press первым.
            binding.avatarCard.setOnLongClickListener {
                binding.avatarCard.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                onChatPeek(chat, binding.root)
                true
            }
            // Короткий тап по аватарке = открыть чат (как по строке)
            binding.avatarCard.setOnClickListener { onChatClick(chat) }
        }
    }

    inner class ContactViewHolder(private val binding: ItemChatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(user: UserResponse) {
            val fullName = listOfNotNull(
                user.first_name?.trim()?.takeIf { it.isNotEmpty() },
                user.last_name?.trim()?.takeIf { it.isNotEmpty() }
            ).joinToString(" ")
            val displayName = if (fullName.isNotBlank()) fullName else (user.phone ?: "Контакт")

            binding.chatName.text        = highlight(displayName)
            binding.lastMessage.text     = user.phone ?: ""
            binding.lastMessageTime.text = ""
            binding.lastMessageTime.visibility = View.GONE
            binding.avatarText.text      = displayName.take(1).uppercase(LOCALE_RU)
            binding.avatarText.visibility = View.VISIBLE
            binding.avatarImage.visibility = View.GONE
            binding.muteIcon.visibility = View.GONE
            binding.unreadBadge.visibility = View.GONE

            // Контакты не бывают закреплёнными — прячем булавку
            // (вью могла переиспользоваться после закреплённого чата).
            binding.pinIndicator.visibility = View.GONE

            // У контактов long-press не нужен — снимаем listener'ы (переиспользование вью).
            binding.root.setOnLongClickListener(null)
            binding.root.isLongClickable = false
            binding.avatarCard.setOnLongClickListener(null)
            binding.avatarCard.isLongClickable = false

            binding.root.setOnClickListener { onContactClick(user) }
            binding.avatarCard.setOnClickListener { onContactClick(user) }
        }
    }

    // ── Подсветка совпадений ──────────────────────────────────────────────────

    private fun highlight(text: String): CharSequence {
        if (highlightQuery.isEmpty()) return text
        val haystack = text.lowercase(LOCALE_RU)
        val needle   = highlightQuery.lowercase(LOCALE_RU)
        if (needle.isEmpty()) return text

        val spannable = SpannableString(text)
        var start = haystack.indexOf(needle)
        while (start >= 0) {
            val end = start + needle.length
            spannable.setSpan(
                ForegroundColorSpan(HIGHLIGHT_FG),
                start, end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                BackgroundColorSpan(HIGHLIGHT_BG),
                start, end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            start = haystack.indexOf(needle, end)
        }
        return spannable
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_CHAT = 1
        private const val TYPE_CONTACT = 2

        private val LOCALE_RU = Locale("ru")
        private val HIGHLIGHT_FG = Color.parseColor("#FFFFFF")
        private val HIGHLIGHT_BG = Color.parseColor("#556A11CB")
    }
}