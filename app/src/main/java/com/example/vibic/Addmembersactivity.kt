package com.example.vibic

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vibic.network.AddParticipantRequest
import com.example.vibic.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Экран добавления участников в группу.
 * Открывается из GroupProfileActivity. Возвращает RESULT_OK, если кого-то добавили,
 * чтобы профиль обновил список участников.
 *
 * Extras:
 *   chat_id — id группы (обязателен)
 */
class AddMembersActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CHAT_ID = "chat_id"

        /** БД хранит телефон как 79991111111 (без +, 8→7). См. GroupInfoActivity. */
        fun phoneToDbFormat(phone: String): String {
            val digits = phone.replace(Regex("[^0-9]"), "")
            return when {
                digits.length == 11 && digits.startsWith("7") -> digits
                digits.length == 11 && digits.startsWith("8") -> "7${digits.drop(1)}"
                digits.length == 10                           -> "7$digits"
                else                                          -> digits
            }
        }
    }

    private lateinit var backButton: ImageView
    private lateinit var searchInput: EditText
    private lateinit var contactsList: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnAdd: TextView

    private var chatId = ""

    private val allContacts = mutableListOf<UserResponse>()
    private val filtered = mutableListOf<UserResponse>()
    private val selectedIds = mutableSetOf<String>()
    private val selectedMap = mutableMapOf<String, UserResponse>()

    private val requestPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadContacts()
        else Toast.makeText(this, "Нет доступа к контактам", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, R.color.dark_bg)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.dark_bg)
        setContentView(R.layout.activity_add_members)

        chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: ""
        if (chatId.isBlank()) {
            Toast.makeText(this, "Ошибка: чат не передан", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        backButton = findViewById(R.id.backButton)
        searchInput = findViewById(R.id.searchInput)
        contactsList = findViewById(R.id.contactsList)
        progressBar = findViewById(R.id.progressBar)
        btnAdd = findViewById(R.id.btnAdd)

        backButton.setOnClickListener { finish() }
        btnAdd.alpha = 0.5f
        btnAdd.setOnClickListener { addSelected() }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { filterContacts(s?.toString().orEmpty()) }
        })

        contactsList.layoutManager = LinearLayoutManager(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) loadContacts()
        else requestPermLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    private fun loadContacts() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val contacts = withContext(Dispatchers.IO) { readPhoneContacts() }
            allContacts.clear()
            allContacts.addAll(contacts)
            filterContacts("")
            progressBar.visibility = View.GONE
        }
    }

    private fun readPhoneContacts(): List<UserResponse> {
        val result = mutableListOf<UserResponse>()
        val seen = hashSetOf<String>()
        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val phone = normalizePhone(if (phoneIdx >= 0) it.getString(phoneIdx) else null)
                if (phone.isBlank() || !seen.add(phone)) continue
                val name = if (nameIdx >= 0) it.getString(nameIdx)?.trim() ?: phone else phone
                val parts = name.split("\\s+".toRegex(), limit = 2)
                result.add(
                    UserResponse(
                        id = "local_${UUID.randomUUID()}",
                        phone = phone,
                        first_name = parts.getOrNull(0),
                        last_name = parts.getOrNull(1),
                        avatar_url = null,
                        status = null,
                        online = false
                    )
                )
            }
        }
        return result
    }

    private fun filterContacts(query: String) {
        val q = query.trim().lowercase()
        filtered.clear()
        filtered.addAll(
            if (q.isEmpty()) allContacts
            else allContacts.filter {
                val name = "${it.first_name.orEmpty()} ${it.last_name.orEmpty()}".lowercase()
                name.contains(q) || it.phone?.contains(q) == true
            }
        )
        contactsList.adapter = AddContactsAdapter(filtered, selectedIds) { contact, selected ->
            if (selected) {
                selectedIds.add(contact.id)
                selectedMap[contact.id] = contact
            } else {
                selectedIds.remove(contact.id)
                selectedMap.remove(contact.id)
            }
            btnAdd.alpha = if (selectedIds.isEmpty()) 0.5f else 1f
            updateAddLabel()
        }
    }

    private fun updateAddLabel() {
        val n = selectedIds.size
        btnAdd.text = if (n > 0) "Добавить · $n" else "Добавить"
    }

    private fun addSelected() {
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "Выберите хотя бы одного участника", Toast.LENGTH_SHORT).show()
            return
        }
        btnAdd.isEnabled = false
        btnAdd.alpha = 0.5f

        val phones = selectedMap.values.mapNotNull { it.phone }.filter { it.isNotBlank() }

        lifecycleScope.launch {
            // 1) Резолвим телефоны в зарегистрированных пользователей
            val userIds = withContext(Dispatchers.IO) {
                phones.map { phone ->
                    async {
                        try {
                            val search = phoneToDbFormat(phone)
                            if (search.isBlank()) return@async null
                            val resp = ApiClient.apiService.searchUsers(search)
                            if (resp.isSuccessful) resp.body()?.firstOrNull()?.id else null
                        } catch (_: Exception) { null }
                    }
                }.awaitAll().filterNotNull().distinct()
            }

            if (userIds.isEmpty()) {
                Toast.makeText(this@AddMembersActivity, "Нет зарегистрированных пользователей среди выбранных", Toast.LENGTH_SHORT).show()
                btnAdd.isEnabled = true
                btnAdd.alpha = 1f
                return@launch
            }

            // 2) Добавляем каждого участника
            val added = withContext(Dispatchers.IO) {
                userIds.map { uid ->
                    async {
                        try {
                            val resp = ApiClient.apiService.addParticipant(
                                chatId, AddParticipantRequest(user_id = uid, role = "member")
                            )
                            resp.isSuccessful
                        } catch (_: Exception) { false }
                    }
                }.awaitAll().count { it }
            }

            if (added > 0) {
                Toast.makeText(this@AddMembersActivity, "Добавлено: $added", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } else {
                Toast.makeText(this@AddMembersActivity, "Не удалось добавить участников", Toast.LENGTH_SHORT).show()
                btnAdd.isEnabled = true
                btnAdd.alpha = 1f
            }
        }
    }

    private fun normalizePhone(phone: String?): String {
        if (phone.isNullOrBlank()) return ""
        val c = phone.replace("\\s|-|\\(|\\)".toRegex(), "")
        return if (c.startsWith("+")) "+${c.drop(1).replace("[^0-9]".toRegex(), "")}"
        else c.replace("[^0-9]".toRegex(), "")
    }

    // ── Adapter ──────────────────────────────────────────────────────────────
    inner class AddContactsAdapter(
        private val contacts: List<UserResponse>,
        private val selectedIds: Set<String>,
        private val onToggle: (UserResponse, Boolean) -> Unit
    ) : RecyclerView.Adapter<AddContactsAdapter.VH>() {

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val avatarText: TextView = itemView.findViewById(R.id.avatarText)
            val avatarCard: CardView = itemView.findViewById(R.id.avatarCard)
            val contactName: TextView = itemView.findViewById(R.id.contactName)
            val contactPhone: TextView = itemView.findViewById(R.id.contactPhone)
            val checkCircle: View = itemView.findViewById(R.id.checkCircle)
            val checkMark: ImageView = itemView.findViewById(R.id.checkMark)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_contact_add_member, parent, false))

        override fun getItemCount() = contacts.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val contact = contacts[position]
            val name = "${contact.first_name.orEmpty()} ${contact.last_name.orEmpty()}".trim()
                .ifBlank { contact.phone ?: "?" }
            holder.contactName.text = name
            holder.avatarText.text = name.take(1).uppercase()

            val phone = contact.phone
            if (!phone.isNullOrBlank()) {
                holder.contactPhone.visibility = View.VISIBLE
                holder.contactPhone.text = phone
            } else {
                holder.contactPhone.visibility = View.GONE
            }

            applyChecked(holder, selectedIds.contains(contact.id))

            holder.itemView.setOnClickListener {
                val nowSelected = !selectedIds.contains(contact.id)
                onToggle(contact, nowSelected)
                applyChecked(holder, nowSelected)
            }
        }

        private fun applyChecked(holder: VH, checked: Boolean) {
            holder.checkCircle.setBackgroundResource(
                if (checked) R.drawable.bg_radio_selected else R.drawable.bg_radio_unselected
            )
            holder.checkMark.visibility = if (checked) View.VISIBLE else View.GONE
        }
    }
}