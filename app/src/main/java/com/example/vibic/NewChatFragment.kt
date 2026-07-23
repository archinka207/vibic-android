package com.example.vibic

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vibic.network.ApiClient
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class NewChatFragment : BottomSheetDialogFragment() {

    private lateinit var btnClose: FrameLayout
    private lateinit var btnNewGroup: LinearLayout
    private lateinit var btnNewChannel: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var allList: RecyclerView

    private val allContacts = mutableListOf<UserResponse>()
    private val filteredContacts = mutableListOf<UserResponse>()

    private val requestContactsPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) loadContacts() else Toast.makeText(requireContext(), "Нет доступа к контактам", Toast.LENGTH_SHORT).show()
        }

    override fun getTheme() = R.style.BottomSheetTheme

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_new_chat, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnClose     = view.findViewById(R.id.btnClose)
        btnNewGroup  = view.findViewById(R.id.btnNewGroup)
        btnNewChannel = view.findViewById(R.id.btnNewChannel)
        searchInput  = view.findViewById(R.id.searchInput)
        allList      = view.findViewById(R.id.allContactsList)

        btnClose.setOnClickListener { dismiss() }

        btnNewGroup.setOnClickListener {
            dismiss()
            startActivity(android.content.Intent(requireContext(), CreateGroupActivity::class.java))
        }

        btnNewChannel.setOnClickListener {
            dismiss()
            startActivity(android.content.Intent(requireContext(), CreateChannelActivity::class.java))
        }

        // Поиск
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { filterContacts(s?.toString().orEmpty()) }
        })

        allList.layoutManager = LinearLayoutManager(requireContext())

        checkPermissionsAndLoad()
    }

    private fun checkPermissionsAndLoad() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) {
            loadContacts()
        } else {
            requestContactsPermLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun loadContacts() {
        lifecycleScope.launch {
            val contacts = withContext(Dispatchers.IO) { readPhoneContacts() }
            allContacts.clear()
            allContacts.addAll(contacts)
            filterContacts("")
        }
    }

    private fun readPhoneContacts(): List<UserResponse> {
        val result = mutableListOf<UserResponse>()
        val seen   = hashSetOf<String>()
        val cursor: Cursor? = requireContext().contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        cursor?.use {
            val nameIdx  = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val rawPhone = if (phoneIdx >= 0) it.getString(phoneIdx) else null
                val phone    = normalizePhone(rawPhone)
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
        filteredContacts.clear()
        filteredContacts.addAll(
            if (q.isEmpty()) allContacts
            else allContacts.filter {
                val name = "${it.first_name.orEmpty()} ${it.last_name.orEmpty()}".lowercase()
                name.contains(q) || it.phone?.contains(q) == true
            }
        )

        allList.adapter = ContactsAdapter { contact -> onContactSelected(contact) }
            .also { it.submitList(filteredContacts.toList()) }
    }

    private fun onContactSelected(contact: UserResponse) {
        val phone = contact.phone ?: return
        val searchPhone = phoneToDbFormat(phone)

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.searchUsers(searchPhone)
                if (!response.isSuccessful || response.body().isNullOrEmpty()) {
                    Toast.makeText(requireContext(), "Контакт не зарегистрирован в Vibic", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val user = response.body()!!.first()
                val chatResp = ApiClient.apiService.createPrivateChat(
                    CreatePrivateChatRequest(target_user_id = user.id)
                )
                if (chatResp.isSuccessful) {
                    val chat = chatResp.body() ?: return@launch
                    dismiss()
                    val intent = android.content.Intent(requireContext(), ChatDetailActivity::class.java).apply {
                        putExtra("chat_id", chat.id)
                        putExtra("chat_name", "${contact.first_name.orEmpty()} ${contact.last_name.orEmpty()}".trim()
                            .ifBlank { contact.phone ?: "Контакт" })
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(requireContext(), "Ошибка: ${chatResp.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка сети", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun normalizePhone(phone: String?): String {
        if (phone.isNullOrBlank()) return ""
        val cleaned = phone.replace("\\s|-|\\(|\\)".toRegex(), "")
        return if (cleaned.startsWith("+")) "+${cleaned.drop(1).replace("[^0-9]".toRegex(), "")}"
        else cleaned.replace("[^0-9]".toRegex(), "")
    }

    private fun phoneToDbFormat(phone: String): String {
        val digits = phone.replace("[^0-9]".toRegex(), "")
        return when {
            digits.length == 11 && digits.startsWith("7") -> digits
            digits.length == 11 && digits.startsWith("8") -> "7${digits.drop(1)}"
            digits.length == 10 -> "7$digits"
            else -> digits
        }
    }
}