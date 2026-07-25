package com.example.vibic

import android.Manifest
import android.content.Intent
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
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class CreateGroupActivity : AppCompatActivity() {

    private lateinit var backButton: FrameLayout
    private lateinit var searchInput: EditText
    private lateinit var contactsList: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnNext: TextView
    private lateinit var selectedChipsScroll: HorizontalScrollView
    private lateinit var selectedChipsContainer: LinearLayout

    private val allContacts = mutableListOf<UserResponse>()
    private val filtered    = mutableListOf<UserResponse>()
    private val selectedIds = mutableSetOf<String>()
    private val selectedMap = mutableMapOf<String, UserResponse>()

    // Когда GroupInfoActivity вернёт RESULT_OK (группа создана) — закрываем и этот экран
    private val groupInfoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            finish()
        }
    }

    private val requestPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadContacts()
        else Toast.makeText(this, "Нет доступа к контактам", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor     = ContextCompat.getColor(this, R.color.dark_bg)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.dark_bg)
        setContentView(R.layout.activity_create_group)

        backButton             = findViewById(R.id.backButton)
        searchInput            = findViewById(R.id.searchInput)
        contactsList           = findViewById(R.id.contactsList)
        progressBar            = findViewById(R.id.progressBar)
        btnNext                = findViewById(R.id.btnNext)
        selectedChipsScroll    = findViewById(R.id.selectedChipsScroll)
        selectedChipsContainer = findViewById(R.id.selectedChipsContainer)

        backButton.setOnClickListener { finish() }

        btnNext.setOnClickListener {
            if (selectedIds.isEmpty()) {
                Toast.makeText(this, "Выберите хотя бы одного участника", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val phones = ArrayList<String>(
                selectedMap.values.mapNotNull { it.phone }.filter { it.isNotBlank() }
            )
            val intent = Intent(this, GroupInfoActivity::class.java)
            intent.putStringArrayListExtra(GroupInfoActivity.EXTRA_SELECTED_PHONES, phones)
            // Запускаем через launcher — чтобы поймать RESULT_OK и закрыться
            groupInfoLauncher.launch(intent)
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { filterContacts(s?.toString().orEmpty()) }
        })

        contactsList.layoutManager = LinearLayoutManager(this)
        btnNext.alpha = 0.5f

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
        val seen   = hashSetOf<String>()
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
            val nameIdx  = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val phone = normalizePhone(if (phoneIdx >= 0) it.getString(phoneIdx) else null)
                if (phone.isBlank() || !seen.add(phone)) continue
                val name  = if (nameIdx >= 0) it.getString(nameIdx)?.trim() ?: phone else phone
                val parts = name.split("\\s+".toRegex(), limit = 2)
                result.add(UserResponse(
                    id         = "local_${UUID.randomUUID()}",
                    phone      = phone,
                    first_name = parts.getOrNull(0),
                    last_name  = parts.getOrNull(1),
                    avatar_url = null,
                    status     = null,
                    online     = false
                ))
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
        contactsList.adapter = SelectableContactsAdapter(filtered, selectedIds) { contact, selected ->
            if (selected) {
                selectedIds.add(contact.id)
                selectedMap[contact.id] = contact
            } else {
                selectedIds.remove(contact.id)
                selectedMap.remove(contact.id)
            }
            updateChips()
            btnNext.alpha = if (selectedIds.isEmpty()) 0.5f else 1f
        }
    }

    private fun updateChips() {
        selectedChipsContainer.removeAllViews()
        if (selectedIds.isEmpty()) {
            selectedChipsScroll.visibility = View.GONE
            return
        }
        selectedChipsScroll.visibility = View.VISIBLE
        for (id in selectedIds) {
            val contact = selectedMap[id] ?: continue
            val name = "${contact.first_name.orEmpty()} ${contact.last_name.orEmpty()}".trim()
                .ifBlank { contact.phone ?: "?" }
            val chip = LayoutInflater.from(this)
                .inflate(R.layout.item_selected_contact_chip, selectedChipsContainer, false)
            chip.findViewById<TextView>(R.id.chipAvatarText).text = name.take(1).uppercase()
            chip.findViewById<TextView>(R.id.chipName).text       = name.split(" ").first()
            chip.findViewById<FrameLayout>(R.id.chipRemove).setOnClickListener {
                selectedIds.remove(id)
                selectedMap.remove(id)
                updateChips()
                btnNext.alpha = if (selectedIds.isEmpty()) 0.5f else 1f
                filterContacts(searchInput.text.toString())
            }
            selectedChipsContainer.addView(chip)
        }
        selectedChipsScroll.post {
            selectedChipsScroll.fullScroll(HorizontalScrollView.FOCUS_RIGHT)
        }
    }

    private fun normalizePhone(phone: String?): String {
        if (phone.isNullOrBlank()) return ""
        val c = phone.replace("\\s|-|\\(|\\)".toRegex(), "")
        return if (c.startsWith("+")) "+${c.drop(1).replace("[^0-9]".toRegex(), "")}"
        else c.replace("[^0-9]".toRegex(), "")
    }

    inner class SelectableContactsAdapter(
        private val contacts: List<UserResponse>,
        private val selectedIds: Set<String>,
        private val onToggle: (UserResponse, Boolean) -> Unit
    ) : RecyclerView.Adapter<SelectableContactsAdapter.VH>() {

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val avatarText:   TextView    = itemView.findViewById(R.id.avatarText)
            val avatarCard:   CardView    = itemView.findViewById(R.id.avatarCard)
            val contactName:  TextView    = itemView.findViewById(R.id.contactName)
            val contactPhone: TextView    = itemView.findViewById(R.id.contactPhone)
            val checkOverlay: FrameLayout = itemView.findViewById(R.id.checkOverlay)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_contact_selectable, parent, false))

        override fun getItemCount() = contacts.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val contact = contacts[position]
            val name = "${contact.first_name.orEmpty()} ${contact.last_name.orEmpty()}".trim()
                .ifBlank { contact.phone ?: "?" }
            holder.contactName.text  = name
            holder.contactPhone.text = contact.phone ?: ""
            holder.avatarText.text   = name.take(1).uppercase()
            val isSelected = selectedIds.contains(contact.id)
            holder.checkOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener {
                val nowSelected = !selectedIds.contains(contact.id)
                onToggle(contact, nowSelected)
                holder.checkOverlay.visibility = if (nowSelected) View.VISIBLE else View.GONE
            }
        }
    }
}