package com.example.vibic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vibic.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

class ContactsFragment : Fragment() {

    private lateinit var searchInput: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var placeholder: LinearLayout
    private lateinit var placeholderText: TextView
    private lateinit var adapter: ContactsAdapter

    private val allContacts = mutableListOf<UserResponse>()
    private val filteredContacts = mutableListOf<UserResponse>()

    private val requestContactsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                loadPhoneContacts()
            } else {
                showPlaceholder("Нет доступа к контактам")
                Toast.makeText(
                    requireContext(),
                    "Разрешение на чтение контактов отклонено",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_contacts, container, false)

        searchInput = view.findViewById(R.id.searchInput)
        recyclerView = view.findViewById(R.id.recyclerViewContacts)
        placeholder = view.findViewById(R.id.contactsPlaceholder)
        placeholderText = view.findViewById(R.id.placeholderText)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearch()
        checkContactsPermissionAndLoad()
    }

    private fun setupRecyclerView() {
        adapter = ContactsAdapter { contact ->
            onContactClicked(contact)
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun setupSearch() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString().orEmpty()
                updateClearIcon(text.isNotEmpty())
                filterContacts(text)
            }
        })

        // Клик по правому крестику — очистить поле.
        searchInput.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val drawableEnd = searchInput.compoundDrawablesRelative[2] ?: return@setOnTouchListener false
                val touchX = event.x
                val fieldWidth = searchInput.width
                val drawableWidth = drawableEnd.bounds.width()
                val paddingEnd = searchInput.paddingEnd
                if (touchX >= fieldWidth - paddingEnd - drawableWidth - 16) {
                    if (searchInput.text.isNotEmpty()) {
                        searchInput.setText("")
                        v.performClick()
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }

        updateClearIcon(false)
    }

    private fun updateClearIcon(visible: Boolean) {
        val startIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_search)
        val endIcon = if (visible) ContextCompat.getDrawable(requireContext(), R.drawable.ic_clear)
        else null
        searchInput.setCompoundDrawablesRelativeWithIntrinsicBounds(startIcon, null, endIcon, null)
    }

    private fun checkContactsPermissionAndLoad() {
        val hasPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            loadPhoneContacts()
        } else {
            requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun loadPhoneContacts() {
        // Если контакты уже прочитаны и закешированы в репозитории — берём их
        // синхронно, без плейсхолдера "Загрузка...". Это убирает микро-вспышку
        // при повторном открытии вкладки.
        val cached = ContactsRepository.cachedOrNull()
        if (cached != null) {
            allContacts.clear()
            allContacts.addAll(cached)
            filterContacts(searchInput.text?.toString().orEmpty())
            return
        }

        // Первая загрузка — показываем плейсхолдер только пока кеша нет.
        showPlaceholder("Загрузка контактов...")

        lifecycleScope.launch {
            val contacts = ContactsRepository.getContacts(requireContext().applicationContext)

            allContacts.clear()
            allContacts.addAll(contacts)

            filterContacts(searchInput.text?.toString().orEmpty())
        }
    }

    private fun readContactsFromPhone(): List<UserResponse> {
        val result = mutableListOf<UserResponse>()
        val seenPhones = hashSetOf<String>()

        val cursor: Cursor? = requireContext().contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val rawName = if (nameIndex >= 0) it.getString(nameIndex) else null
                val rawPhone = if (phoneIndex >= 0) it.getString(phoneIndex) else null

                val normalizedPhone = normalizePhone(rawPhone)

                if (normalizedPhone.isBlank()) continue
                if (!seenPhones.add(normalizedPhone)) continue

                val displayName = rawName?.trim().orEmpty()
                val firstName: String?
                val lastName: String?

                if (displayName.isBlank()) {
                    firstName = normalizedPhone
                    lastName = null
                } else {
                    val parts = displayName.split("\\s+".toRegex(), limit = 2)
                    firstName = parts.getOrNull(0)
                    lastName = parts.getOrNull(1)
                }

                result.add(
                    UserResponse(
                        id = "local_${UUID.randomUUID()}",
                        phone = normalizedPhone,
                        first_name = firstName,
                        last_name = lastName,
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
        val trimmedQuery = query.trim()

        filteredContacts.clear()

        if (trimmedQuery.isEmpty()) {
            filteredContacts.addAll(allContacts)
        } else {
            val nameNeedle  = trimmedQuery.lowercase(LOCALE_RU)
            val digitNeedle = trimmedQuery.filter { it.isDigit() }

            filteredContacts.addAll(
                allContacts.filter { contact ->
                    val nameMatch = buildFullName(contact).lowercase(LOCALE_RU).contains(nameNeedle)

                    // Поиск по номеру — только если в запросе есть цифры. Сравниваем
                    // голые цифры обеих сторон, чтобы "+7 (999) 111-22-33" находился
                    // и по "9991112233", и по "111-22-33".
                    val phoneMatch = digitNeedle.isNotEmpty() &&
                            (contact.phone ?: "")
                                .filter { it.isDigit() }
                                .contains(digitNeedle)

                    nameMatch || phoneMatch
                }
            )
        }

        adapter.setHighlightQuery(trimmedQuery)

        if (filteredContacts.isEmpty()) {
            // Если у пользователя вообще ещё нет контактов — другой текст,
            // чем при пустом результате поиска.
            val message = if (allContacts.isEmpty()) "Введите имя или номер для поиска"
            else "Контакты не найдены"
            showPlaceholder(message)
            adapter.submitList(emptyList())
        } else {
            hidePlaceholder()
            adapter.submitList(filteredContacts.toList())
        }
    }

    private fun onContactClicked(contact: UserResponse) {
        val phone = contact.phone

        if (phone.isNullOrBlank()) {
            Toast.makeText(requireContext(), "У контакта нет номера", Toast.LENGTH_SHORT).show()
            return
        }

        val searchPhone = phoneToDbFormat(phone)

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.searchUsers(searchPhone)

                if (!response.isSuccessful) {
                    Toast.makeText(
                        requireContext(),
                        "Ошибка поиска: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val usersFromApi = response.body().orEmpty()

                if (usersFromApi.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "Этот контакт ещё не зарегистрирован в Vibic",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val matchedUser = usersFromApi.firstOrNull() ?: run {
                    Toast.makeText(requireContext(), "Пользователь не найден", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                createChatAndOpen(matchedUser)

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Приводит номер телефона к формату, в котором он хранится в БД: только цифры, начиная с 7.
     *
     * Примеры:
     *   +7 (999) 111-11-11  →  79991111111
     *   +79991111111        →  79991111111
     *   89991111111         →  79991111111  (8 → 7 для российских номеров)
     *   79991111111         →  79991111111
     */
    private fun phoneToDbFormat(phone: String): String {
        // Убираем всё кроме цифр
        val digitsOnly = phone.replace("[^0-9]".toRegex(), "")

        return when {
            // +7xxxxxxxxxx или 7xxxxxxxxxx → 7xxxxxxxxxx
            digitsOnly.length == 11 && digitsOnly.startsWith("7") -> digitsOnly
            // 8xxxxxxxxxx (российский формат) → 7xxxxxxxxxx
            digitsOnly.length == 11 && digitsOnly.startsWith("8") -> "7${digitsOnly.drop(1)}"
            // 10-значный без кода страны → добавляем 7
            digitsOnly.length == 10 -> "7$digitsOnly"
            // иначе отдаём как есть (только цифры)
            else -> digitsOnly
        }
    }

    private fun createChatAndOpen(user: UserResponse) {
        lifecycleScope.launch {
            try {
                val request = CreatePrivateChatRequest(
                    target_user_id = user.id
                )

                val response = ApiClient.apiService.createPrivateChat(request)

                if (response.isSuccessful) {
                    val chat = response.body()
                    if (chat != null) {
                        val intent = Intent(requireContext(), ChatDetailActivity::class.java).apply {
                            putExtra("chat_id", chat.id)
                            putExtra("chat_name", buildFullName(user).ifBlank { user.phone ?: "Контакт" })
                        }
                        startActivity(intent)
                    } else {
                        Toast.makeText(requireContext(), "Не удалось открыть чат", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Ошибка создания чата: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun buildFullName(user: UserResponse): String {
        return listOfNotNull(
            user.first_name?.trim()?.takeIf { it.isNotEmpty() },
            user.last_name?.trim()?.takeIf { it.isNotEmpty() }
        ).joinToString(" ")
    }

    private fun normalizePhone(phone: String?): String {
        if (phone.isNullOrBlank()) return ""

        val cleaned = phone
            .replace("\\s".toRegex(), "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")

        return if (cleaned.startsWith("+")) {
            "+" + cleaned.drop(1).replace("[^0-9]".toRegex(), "")
        } else {
            cleaned.replace("[^0-9]".toRegex(), "")
        }
    }

    private fun showPlaceholder(message: String) {
        placeholder.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        placeholderText.text = message
    }

    private fun hidePlaceholder() {
        placeholder.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }

    companion object {
        private val LOCALE_RU = Locale("ru")
    }
}