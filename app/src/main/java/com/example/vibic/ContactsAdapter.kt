package com.example.vibic

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class ContactsAdapter(
    private val onItemClick: (UserResponse) -> Unit
) : RecyclerView.Adapter<ContactsAdapter.ContactViewHolder>() {

    private var users: List<UserResponse> = emptyList()
    private var highlightQuery: String = ""

    private var highlightDigits: String = ""

    fun submitList(newUsers: List<UserResponse>) {
        users = newUsers
        notifyDataSetChanged()
    }

    fun setHighlightQuery(query: String) {
        val normalized = query.trim()
        val digits = normalized.filter { it.isDigit() }
        if (normalized == highlightQuery && digits == highlightDigits) return
        highlightQuery = normalized
        highlightDigits = digits
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val user = users[position]
        holder.bind(user)

        holder.itemView.setOnClickListener {
            onItemClick(user)
        }
    }

    override fun getItemCount(): Int = users.size

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.contactName)
        private val phoneText: TextView = itemView.findViewById(R.id.contactPhone)
        private val avatarText: TextView = itemView.findViewById(R.id.avatarText)

        fun bind(user: UserResponse) {
            val fullName = buildFullName(user)
            val displayName = if (fullName.isNotBlank()) fullName else "Пользователь"

            nameText.text = highlightText(displayName)

            val phone = user.phone?.trim().orEmpty()
            if (phone.isNotEmpty()) {
                phoneText.visibility = View.VISIBLE
                phoneText.text = highlightPhone(phone)
            } else {
                phoneText.visibility = View.VISIBLE
                phoneText.text = "Нет номера"
            }

            avatarText.text = getAvatarLetter(displayName)
        }
    }

    private fun buildFullName(user: UserResponse): String {
        return listOfNotNull(
            user.first_name?.trim()?.takeIf { it.isNotEmpty() },
            user.last_name?.trim()?.takeIf { it.isNotEmpty() }
        ).joinToString(" ")
    }

    private fun getAvatarLetter(name: String): String {
        return name
            .trim()
            .firstOrNull()
            ?.uppercaseChar()
            ?.toString()
            ?: "?"
    }

    /** Подсветка в обычном тексте (имя). Поиск по всему запросу. */
    private fun highlightText(text: String): CharSequence {
        if (highlightQuery.isEmpty()) return text
        val haystack = text.lowercase(LOCALE_RU)
        val needle = highlightQuery.lowercase(LOCALE_RU)
        if (needle.isEmpty()) return text

        val spannable = SpannableString(text)
        var start = haystack.indexOf(needle)
        while (start >= 0) {
            val end = start + needle.length
            applyHighlightSpans(spannable, start, end)
            start = haystack.indexOf(needle, end)
        }
        return spannable
    }

    /**
     * Подсветка в номере телефона: ищем совпадение по цифрам запроса.
     * Если у юзера номер "+7 (999) 111-22-33", а запрос "9991112",
     * подсветится "999) 111-2" (исходные символы, соответствующие найденным цифрам).
     */
    private fun highlightPhone(phone: String): CharSequence {
        if (highlightDigits.isEmpty()) return phone

        // Собираем массив "индексы цифр в исходной строке"
        val digitPositions = ArrayList<Int>(phone.length)
        val digitsOnly = StringBuilder(phone.length)
        phone.forEachIndexed { i, ch ->
            if (ch.isDigit()) {
                digitPositions.add(i)
                digitsOnly.append(ch)
            }
        }
        if (digitsOnly.isEmpty()) return phone

        val haystack = digitsOnly.toString()
        val needle = highlightDigits
        val spannable = SpannableString(phone)

        var pos = haystack.indexOf(needle)
        while (pos >= 0) {
            val startInOriginal = digitPositions[pos]
            // Конец — позиция последней совпавшей цифры + 1 в исходной строке
            val lastDigitIdx = pos + needle.length - 1
            val endInOriginal = digitPositions[lastDigitIdx] + 1
            applyHighlightSpans(spannable, startInOriginal, endInOriginal)
            pos = haystack.indexOf(needle, pos + needle.length)
        }
        return spannable
    }

    private fun applyHighlightSpans(spannable: SpannableString, start: Int, end: Int) {
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
    }

    companion object {
        private val LOCALE_RU = Locale("ru")
        private val HIGHLIGHT_FG = Color.parseColor("#FFFFFF")
        private val HIGHLIGHT_BG = Color.parseColor("#556A11CB")
    }
}