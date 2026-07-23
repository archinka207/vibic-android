package com.example.vibic

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vibic.network.ApiClient
import com.example.vibic.network.MessageDto
import com.example.vibic.network.TokenManager
import com.example.vibic.network.ChatDto
import com.example.vibic.network.WebSocketManager
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MainFragment : Fragment() {

    private lateinit var rootView: View

    private lateinit var contentFrame: FrameLayout
    private lateinit var searchInput: EditText
    private lateinit var vibicTitle: TextView

    private lateinit var chatsTab: LinearLayout
    private lateinit var contactsTab: LinearLayout
    private lateinit var profileTab: LinearLayout

    private lateinit var chatIcon: ImageView
    private lateinit var contactsIcon: ImageView


    private lateinit var chatText: TextView
    private lateinit var contactsText: TextView
    private lateinit var profileText: TextView

    private lateinit var profileAvatarCard: CardView
    private lateinit var profileAvatarImage: ImageView
    private lateinit var profileAvatarText: TextView

    private lateinit var chatBadge: TextView
    private lateinit var tabIndicator: View

    private lateinit var btnNewChat: FrameLayout

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var recyclerViewChats: RecyclerView

    private lateinit var chatsContainer: FrameLayout
    private lateinit var emptyView: TextView

    private val cachedChats = mutableListOf<Chat>()
    private var phoneContacts: List<UserResponse> = emptyList()
    private var contactsLoaded = false

    private var currentSelectedTab: LinearLayout? = null
    private var isLoading = false

    private var currentTab = "chats"

    private var searchQuery: String = ""

    // Подписка на входящие сообщения по WebSocket (живой счётчик непрочитанных).
    private var webSocketJob: Job? = null

    // Чаты, которые пользователь открыл/прочитал локально. Для них счётчик = 0,
    // даже если сервер при следующем getChats() пришлёт устаревшее ненулевое
    // значение (на бэке пока нет отметки "прочитано" — см. BACKEND_TODO).
    // Множество персистится в SharedPreferences, чтобы переживать перезапуск.
    private val locallyReadChatIds = mutableSetOf<String>()

    // ── Оверлей действий ───────────────────────────────────────────────────────
    private var actionsPopup: PopupWindow? = null
    private var dimView: View? = null

    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "avatar_path" || key == "user_nickname" || key == "user_first_name" || key == "user_last_name") {
            activity?.runOnUiThread { updateProfileTabAvatar() }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        rootView = inflater.inflate(R.layout.main_fragment, container, false)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        initViews(view)
        setupRecyclerView()
        setupSearchTouchHandling()
        setupSearchInput()
        setupTabs()

        loadLocallyReadIds()
        loadCachedChats()
        loadChatsInBackground()
        updateProfileTabAvatar()
        warmUpContacts()

        setupAvatarUpdateListener()
        observeIncomingMessages()

        rootView.post {
            animateTabSelection(chatsTab, chatIcon, chatText)
            showChatsScreen()
        }
    }

    override fun onResume() {
        super.onResume()
        updateProfileTabAvatar()
        loadCachedChats()
        isLoading = false
        loadChatsInBackground()
    }

    override fun onPause() {
        super.onPause()
        dismissActionsOverlay()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webSocketJob?.cancel()
        webSocketJob = null
    }

    private fun initViews(view: View) {
        contentFrame = view.findViewById(R.id.contentFrame)
        searchInput  = view.findViewById(R.id.searchInput)
        vibicTitle   = view.findViewById(R.id.vibic)

        chatsTab    = view.findViewById(R.id.chatsTab)
        contactsTab = view.findViewById(R.id.contactsTab)
        profileTab  = view.findViewById(R.id.profileTab)

        chatIcon     = view.findViewById(R.id.chatIcon)
        contactsIcon = view.findViewById(R.id.contactsIcon)

        chatText     = view.findViewById(R.id.chatText)
        contactsText = view.findViewById(R.id.contactsText)
        profileText  = view.findViewById(R.id.profileText)

        profileAvatarCard  = view.findViewById(R.id.profileAvatarCard)
        profileAvatarImage = view.findViewById(R.id.profileAvatarImage)
        profileAvatarText  = view.findViewById(R.id.profileAvatarText)

        chatBadge    = view.findViewById(R.id.chatBadge)
        btnNewChat   = view.findViewById(R.id.btnNewChat)

        btnNewChat.setOnClickListener {
            if (currentTab == "contacts") {
                AddContactFragment().show(childFragmentManager, "add_contact")
            } else {
                NewChatFragment().show(childFragmentManager, "new_chat")
            }
        }

        chatsContainer = FrameLayout(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        recyclerViewChats = RecyclerView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            layoutManager = LinearLayoutManager(requireContext())
            val paddingBottomPx = (100 * resources.displayMetrics.density).toInt()
            setPadding(0, 0, 0, paddingBottomPx)
            clipToPadding = false
        }

        emptyView = TextView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            gravity = Gravity.CENTER
            text = "Ничего не найдено"
            setTextColor(Color.parseColor("#888888"))
            textSize = 15f
            visibility = View.GONE
            val sidePadding = (32 * resources.displayMetrics.density).toInt()
            setPadding(sidePadding, 0, sidePadding, 0)
        }

        chatsContainer.addView(recyclerViewChats)
        chatsContainer.addView(emptyView)
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(
            onChatClick = { chat ->
                // Мгновенно убираем непрочитанные локально, не дожидаясь сервера,
                // чтобы бейдж у чата и общий счётчик на вкладке сразу уменьшились.
                markChatReadLocally(chat.id)
                // Снимаем системное уведомление (пуш) по этому чату.
                MyFirebaseMessagingService.clearChatNotifications(requireContext(), chat.id)

                val intent = Intent(requireContext(), ChatDetailActivity::class.java).apply {
                    putExtra("chat_id", chat.id)
                    putExtra("chat_name", chat.name)
                }
                startActivity(intent)
            },
            onContactClick = { user ->
                openChatWithContact(user)
            },
            onChatPeek = { chat, anchor ->
                showChatActionsOverlay(chat, anchor, withPreview = true)
            },
            onChatMenu = { chat, anchor ->
                showChatActionsOverlay(chat, anchor, withPreview = false)
            }
        )
        recyclerViewChats.adapter = chatAdapter
    }

    // ── Search ─────────────────────────────────────────────────────────────────

    private fun setupSearchInput() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString().orEmpty()
                searchQuery = text.trim()
                updateClearIcon(text.isNotEmpty())
                if (searchQuery.isNotEmpty() && !contactsLoaded) {
                    loadPhoneContactsIfPossible()
                }
                applyFilter()
            }
        })

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

    /**
     * Прогревает кеш контактов в фоне, пока пользователь ещё на вкладке чатов,
     * чтобы при первом открытии вкладки "Контакты" список уже был в памяти и
     * не мелькал плейсхолдер "Загрузка контактов...".
     *
     * Если разрешение READ_CONTACTS ещё не выдано — прогрев пропускается
     * (getContacts без разрешения вернул бы пустой список). В этом случае
     * вспышка при самом первом заходе неизбежна, но при последующих запусках
     * (когда разрешение уже есть) вкладка открывается мгновенно.
     */
    private fun warmUpContacts() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        lifecycleScope.launch {
            try {
                ContactsRepository.getContacts(requireContext().applicationContext)
            } catch (e: Exception) {
                Log.e("MainFragment", "Ошибка прогрева контактов", e)
            }
        }
    }

    private fun loadPhoneContactsIfPossible() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            contactsLoaded = true
            return
        }
        contactsLoaded = true
        lifecycleScope.launch {
            try {
                phoneContacts = ContactsRepository.getContacts(requireContext().applicationContext)
                if (searchQuery.isNotEmpty()) applyFilter()
            } catch (e: Exception) {
                Log.e("MainFragment", "Ошибка загрузки контактов для поиска", e)
            }
        }
    }

    private fun applyFilter() {
        // Закреплённые — наверх, остальные сохраняют исходный порядок.
        val ordered = cachedChats.sortedByDescending { it.isPinned }
        val chats = filterChats(ordered, searchQuery)
        val contacts = if (searchQuery.isNotEmpty()) filterContacts(phoneContacts, searchQuery, chats)
        else emptyList()

        chatAdapter.setHighlightQuery(searchQuery)

        val rows = mutableListOf<ChatAdapter.Row>()
        chats.forEach { rows.add(ChatAdapter.Row.ChatRow(it)) }
        if (contacts.isNotEmpty()) {
            rows.add(ChatAdapter.Row.HeaderRow("Контакты"))
            contacts.forEach { rows.add(ChatAdapter.Row.ContactRow(it)) }
        }
        chatAdapter.submitRows(rows)

        val hasQuery = searchQuery.isNotEmpty()
        val showEmpty = hasQuery && rows.isEmpty()
        emptyView.visibility = if (showEmpty) View.VISIBLE else View.GONE
        recyclerViewChats.visibility =
            if (showEmpty) View.GONE
            else if (currentTab == "chats") View.VISIBLE
            else recyclerViewChats.visibility
    }

    private fun filterChats(source: List<Chat>, query: String): List<Chat> {
        if (query.isEmpty()) return source.toList()
        val q = query.lowercase(LOCALE_RU)
        return source.filter { chat ->
            chat.name.lowercase(LOCALE_RU).contains(q) ||
                    chat.lastMessage.lowercase(LOCALE_RU).contains(q)
        }
    }

    private fun filterContacts(
        source: List<UserResponse>,
        query: String,
        chatsAlreadyShown: List<Chat>
    ): List<UserResponse> {
        if (source.isEmpty()) return emptyList()
        val nameNeedle = query.lowercase(LOCALE_RU)
        val digitNeedle = query.filter { it.isDigit() }
        val shownNames = chatsAlreadyShown.map { it.name.lowercase(LOCALE_RU) }.toHashSet()

        return source.asSequence()
            .filter { user ->
                val fullName = listOfNotNull(
                    user.first_name?.trim()?.takeIf { it.isNotEmpty() },
                    user.last_name?.trim()?.takeIf { it.isNotEmpty() }
                ).joinToString(" ")
                val nameMatch = fullName.lowercase(LOCALE_RU).contains(nameNeedle) && fullName.isNotBlank()
                val phoneMatch = digitNeedle.isNotEmpty() &&
                        (user.phone ?: "").filter { it.isDigit() }.contains(digitNeedle)
                (nameMatch || phoneMatch) && fullName.lowercase(LOCALE_RU) !in shownNames
            }
            .take(30)
            .toList()
    }

    private fun openChatWithContact(contact: UserResponse) {
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
                    Toast.makeText(requireContext(), "Ошибка поиска: ${response.code()}", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val matchedUser = response.body().orEmpty().firstOrNull()
                if (matchedUser == null) {
                    Toast.makeText(
                        requireContext(),
                        "Этот контакт ещё не зарегистрирован в Vibic",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val createReq = CreatePrivateChatRequest(
                    target_user_id = matchedUser.id
                )
                val createResp = ApiClient.apiService.createPrivateChat(createReq)
                if (!createResp.isSuccessful) {
                    Toast.makeText(
                        requireContext(),
                        "Не удалось открыть чат: ${createResp.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                val chat = createResp.body() ?: run {
                    Toast.makeText(requireContext(), "Не удалось открыть чат", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val fullName = listOfNotNull(
                    contact.first_name?.trim()?.takeIf { it.isNotEmpty() },
                    contact.last_name?.trim()?.takeIf { it.isNotEmpty() }
                ).joinToString(" ")
                val displayName = fullName.ifBlank { contact.phone ?: "Контакт" }

                val intent = Intent(requireContext(), ChatDetailActivity::class.java).apply {
                    putExtra("chat_id", chat.id)
                    putExtra("chat_name", displayName)
                }
                startActivity(intent)

                searchInput.setText("")
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun phoneToDbFormat(phone: String): String {
        val digitsOnly = phone.replace("[^0-9]".toRegex(), "")
        return when {
            digitsOnly.length == 11 && digitsOnly.startsWith("7") -> digitsOnly
            digitsOnly.length == 11 && digitsOnly.startsWith("8") -> "7${digitsOnly.drop(1)}"
            digitsOnly.length == 10 -> "7$digitsOnly"
            else -> digitsOnly
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ── Оверлей действий с чатом (peek + меню) ────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Показывает затемнение + (опционально) превью переписки + меню действий
     * рядом с нажатой строкой. Если строка близко к низу — меню разворачивается вверх.
     *
     * @param withPreview true для long-press по аватарке (с превью), false для строки.
     */
    private fun showChatActionsOverlay(chat: Chat, anchorRow: View, withPreview: Boolean) {
        dismissActionsOverlay()

        val activityRoot = requireActivity().window.decorView as ViewGroup
        val density = resources.displayMetrics.density
        val dp = { v: Int -> (v * density).toInt() }

        // 1) Затемнение поверх всего экрана
        val dim = View(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#F5060010"))
            alpha = 0f
            isClickable = true
            setOnClickListener { dismissActionsOverlay() }
        }
        dimView = dim
        activityRoot.addView(dim)
        dim.animate().alpha(1f).setDuration(150).start()

        // 2) Собираем контент поповера: [превью?] + меню
        val inflater = LayoutInflater.from(requireContext())
        val popupContent = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            // тап по пустому месту внутри попапа тоже закрывает его
            setOnClickListener { dismissActionsOverlay() }
        }

        if (withPreview) {
            val preview = inflater.inflate(R.layout.popup_chat_preview, popupContent, false)
            bindPreview(preview, chat)
            preview.isClickable = true // карточка съедает тап, не закрывая попап
            (preview.layoutParams as? LinearLayout.LayoutParams)?.apply {
                width = LinearLayout.LayoutParams.MATCH_PARENT
                bottomMargin = dp(10)
            }
            popupContent.addView(preview)
        }

        val menu = inflater.inflate(R.layout.popup_message_actions, popupContent, false)
        bindActionsMenu(menu, chat)
        menu.isClickable = true // фон меню между пунктами не закрывает попап
        (menu.layoutParams as? LinearLayout.LayoutParams)?.width =
            LinearLayout.LayoutParams.MATCH_PARENT
        popupContent.addView(menu)

        // 3) PopupWindow — во всю ширину экрана минус боковые отступы
        val marginX = dp(16)
        val popupWidth = activityRoot.width - marginX * 2

        val popup = PopupWindow(
            popupContent,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = dp(12).toFloat()
            isOutsideTouchable = true
            setOnDismissListener { removeDim() }
        }
        actionsPopup = popup

        // 4) Позиционирование: всегда фиксированно у верха экрана, как на макете
        popupContent.measure(
            View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(activityRoot.height, View.MeasureSpec.AT_MOST)
        )
        val contentH = popupContent.measuredHeight

        val topInset = dp(72)   // отступ от верха экрана (под статус-бар)
        val bottomSafe = activityRoot.height - dp(24)

// если контент не влезает — поджимаем к безопасной зоне
        val y = if (topInset + contentH <= bottomSafe) topInset
        else (bottomSafe - contentH).coerceAtLeast(dp(24))
        val x = marginX

        popup.showAtLocation(activityRoot, Gravity.TOP or Gravity.START, x, y)

        // Лёгкая анимация появления
        popupContent.alpha = 0f
        popupContent.scaleX = 0.92f
        popupContent.scaleY = 0.92f
        popupContent.pivotY = 0f
        popupContent.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(180)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()
    }

    private fun removeDim() {
        dimView?.let { d ->
            d.animate().alpha(0f).setDuration(120).withEndAction {
                (d.parent as? ViewGroup)?.removeView(d)
            }.start()
        }
        dimView = null
    }

    private fun dismissActionsOverlay() {
        actionsPopup?.let { if (it.isShowing) it.dismiss() }
        actionsPopup = null
        removeDim()
    }

    /** Заполняет шапку и пузыри превью из локального кэша сообщений чата. */
    private fun bindPreview(view: View, chat: Chat) {
        val avatarText = view.findViewById<TextView>(R.id.previewAvatar)
        val name = view.findViewById<TextView>(R.id.previewName)
        val status = view.findViewById<TextView>(R.id.previewStatus)
        val container = view.findViewById<LinearLayout>(R.id.previewMessages)

        avatarText.text = chat.name.take(1).uppercase(LOCALE_RU)
        name.text = chat.name
        val online = WebSocketManager.shared.isConnectedValue && chat.isOnline
        status.text = if (online) "в сети" else "был(а) недавно"
        status.setTextColor(Color.parseColor(if (online) "#B366FF" else "#888888"))

        container.removeAllViews()

        val myId = TokenManager.getUserId().orEmpty()
        val messages = loadPreviewMessages(chat.id).takeLast(6)

        if (messages.isEmpty()) {
            // Нет кэша — показываем хотя бы последнее сообщение из списка.
            addBubble(container, chat.lastMessage, chat.lastMessageTime, outgoing = false)
        } else {
            messages.forEach { m ->
                val outgoing = m.senderId == myId
                val text = when (m.type) {
                    "audio", "audio_once" -> "🎤 Голосовое сообщение"
                    "video_note"          -> "📹 Видеосообщение"
                    "image"               -> "🖼 Фото"
                    else                  -> m.content ?: ""
                }
                if (text.isNotBlank()) addBubble(container, text, formatTime(m.createdAt), outgoing)
            }
        }
    }

    /** Создаёт один пузырь в превью. */
    /** Создаёт один пузырь в превью: текст + время внутри, как в макете. */
    /** Создаёт один пузырь в превью: текст + время внутри, как в макете. */
    private fun addBubble(parent: LinearLayout, text: String, time: String, outgoing: Boolean) {
        val density = resources.displayMetrics.density
        val dp = { v: Int -> (v * density).toInt() }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(dp(12), dp(7), dp(10), dp(7))
            setBackgroundResource(
                if (outgoing) R.drawable.bg_preview_bubble_outgoing
                else R.drawable.bg_preview_bubble_incoming
            )
        }

        val msg = TextView(requireContext()).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = dp(220)
        }
        row.addView(msg, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        if (time.isNotBlank()) {
            val stamp = TextView(requireContext()).apply {
                this.text = time
                textSize = 11f
                setTextColor(Color.parseColor(if (outgoing) "#D8C6F7" else "#8A8898"))
            }
            row.addView(stamp, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(8)
                gravity = Gravity.BOTTOM
            })
        }

        parent.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(6)
            gravity = if (outgoing) Gravity.END else Gravity.START
            marginStart = if (outgoing) dp(40) else 0
            marginEnd = if (outgoing) 0 else dp(40)
        })
    }

    /** Читает последние сообщения чата из того же кэша, что пишет ChatDetailActivity. */
    private fun loadPreviewMessages(chatId: String): List<MessageDto> {
        return try {
            val prefs = requireContext().getSharedPreferences("message_cache", Context.MODE_PRIVATE)
            val json = prefs.getString("chat_$chatId", null) ?: return emptyList()
            val type = object : TypeToken<List<MessageDto>>() {}.type
            val list: List<MessageDto> = Gson().fromJson(json, type) ?: emptyList()
            list.filter { !it.senderId.isNullOrBlank() }
                .sortedBy { it.createdAt }
        } catch (e: Exception) {
            Log.e("MainFragment", "Ошибка чтения превью", e)
            emptyList()
        }
    }

    /** Навешивает обработчики на пункты меню действий. */
    private fun bindActionsMenu(menu: View, chat: Chat) {
        val muteText = menu.findViewById<TextView>(R.id.actionMuteText)
        val muteIcon = menu.findViewById<ImageView>(R.id.actionMuteIcon)
        val pinText  = menu.findViewById<TextView>(R.id.actionPinText)

        // Mute: текст + иконка по текущему состоянию
        if (chat.isMuted) {
            muteText.text = "Включить звук"
            muteIcon.setImageResource(R.drawable.ic_action_unmute)
        } else {
            muteText.text = "Без звука"
            muteIcon.setImageResource(R.drawable.ic_action_mute)
        }
        pinText.text = if (chat.isPinned) "Открепить" else "Закрепить"

        menu.findViewById<View>(R.id.actionFolder).setOnClickListener {
            dismissActionsOverlay()
            Toast.makeText(requireContext(), "Папки в разработке", Toast.LENGTH_SHORT).show()
        }
        menu.findViewById<View>(R.id.actionUnread).setOnClickListener {
            dismissActionsOverlay()
            markUnread(chat)
        }
        menu.findViewById<View>(R.id.actionPin).setOnClickListener {
            dismissActionsOverlay()
            togglePin(chat)
        }
        menu.findViewById<View>(R.id.actionMute).setOnClickListener {
            dismissActionsOverlay()
            toggleMute(chat)
        }
        menu.findViewById<View>(R.id.deleteAction).setOnClickListener {
            dismissActionsOverlay()
            showDeleteDialog(chat)
        }
    }

    // ── Действия над чатом ─────────────────────────────────────────────────────

    private fun toggleMute(chat: Chat) {
        updateChatInCache(chat.id) { it.copy(isMuted = !it.isMuted) }
        val nowMuted = cachedChats.firstOrNull { it.id == chat.id }?.isMuted ?: false
        // Бейдж на вкладке зависит от mute → пересчитываем.
        updateUnreadBadge(visibleUnreadCount())
        Toast.makeText(
            requireContext(),
            if (nowMuted) "Уведомления отключены" else "Уведомления включены",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun togglePin(chat: Chat) {
        updateChatInCache(chat.id) { it.copy(isPinned = !it.isPinned) }
        val nowPinned = cachedChats.firstOrNull { it.id == chat.id }?.isPinned ?: false
        Toast.makeText(
            requireContext(),
            if (nowPinned) "Чат закреплён" else "Чат откреплён",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun markUnread(chat: Chat) {
        updateChatInCache(chat.id) {
            it.copy(unreadCount = if (it.unreadCount > 0) it.unreadCount else 1)
        }
        // Снимаем "локально прочитан", иначе при следующей синхронизации обнулится.
        if (locallyReadChatIds.remove(chat.id)) saveLocallyReadIds()
        updateUnreadBadge(visibleUnreadCount())
        Toast.makeText(requireContext(), "Отмечено непрочитанным", Toast.LENGTH_SHORT).show()
    }

    /** Применяет изменение к одному чату, сохраняет кэш и перерисовывает список. */
    private fun updateChatInCache(chatId: String, transform: (Chat) -> Chat) {
        val idx = cachedChats.indexOfFirst { it.id == chatId }
        if (idx < 0) return
        cachedChats[idx] = transform(cachedChats[idx])
        saveChatsToCache(cachedChats)
        applyFilter()
    }

    // ── Кастомный диалог удаления ──────────────────────────────────────────────

    private fun showDeleteDialog(chat: Chat) {
        val dialog = Dialog(requireContext())
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_delete_chat, null, false)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        view.findViewById<TextView>(R.id.deleteMessage).text =
            "Чат с «${chat.name}» и вся история сообщений будут удалены."

        view.findViewById<View>(R.id.btnCancelDelete).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.btnConfirmDelete).setOnClickListener {
            dialog.dismiss()
            deleteChat(chat)
        }
        dialog.show()
    }

    private fun deleteChat(chat: Chat) {
        // 1) Локально — мгновенно
        cachedChats.removeAll { it.id == chat.id }
        saveChatsToCache(cachedChats)
        applyFilter()
        updateUnreadBadge(visibleUnreadCount())
        Toast.makeText(requireContext(), "Чат удалён", Toast.LENGTH_SHORT).show()

        // 2) Сервер — в фоне (тихо; если упадёт, локально уже удалено)
        lifecycleScope.launch {
            try {
                ApiClient.apiService.deleteChat(chat.id)
            } catch (e: Exception) {
                Log.e("MainFragment", "Ошибка удаления чата на сервере", e)
            }
        }
    }

    // ── Tabs / экраны ───────────────────────────────────────────────────────────

    private fun setupTabs() {
        chatsTab.setOnClickListener {
            animateTabSelection(chatsTab, chatIcon, chatText)
            showChatsScreen()
        }
        contactsTab.setOnClickListener {
            animateTabSelection(contactsTab, contactsIcon, contactsText)
            showContactsScreen()
        }
//        callTab.setOnClickListener {
//            animateTabSelection(callTab, callIcon, callText)
//            showTrendingScreen()
//        }
        profileTab.setOnClickListener {
            animateTabSelection(profileTab, profileAvatarCard, profileText)
            showProfileScreen()
        }
    }

    private fun setupAvatarUpdateListener() {
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    private fun setupSearchTouchHandling() {
        rootView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val focusedView = requireActivity().currentFocus
                if (focusedView is EditText) {
                    val outRect = Rect()
                    focusedView.getGlobalVisibleRect(outRect)
                    if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                        focusedView.clearFocus()
                        hideKeyboard(focusedView)
                    }
                }
            }
            false
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun showChatsScreen() {
        currentTab = "chats"
        childFragmentManager.fragments.forEach {
            childFragmentManager.beginTransaction().remove(it).commitAllowingStateLoss()
        }
        contentFrame.removeAllViews()
        contentFrame.addView(chatsContainer)
        recyclerViewChats.visibility = View.VISIBLE
        searchInput.visibility = View.VISIBLE
        vibicTitle.visibility  = View.VISIBLE
        vibicTitle.text = "Чаты"
        btnNewChat.visibility = View.VISIBLE
        applyFilter()
    }

    private fun showContactsScreen() {
        currentTab = "contacts"
        contentFrame.removeAllViews()
        recyclerViewChats.visibility = View.GONE
        searchInput.visibility = View.GONE
        vibicTitle.visibility  = View.VISIBLE
        vibicTitle.text = "Контакты"
        btnNewChat.visibility = View.VISIBLE
        childFragmentManager.beginTransaction()
            .replace(R.id.contentFrame, ContactsFragment())
            .commitAllowingStateLoss()
    }

    private fun showTrendingScreen() {
        currentTab = "trending"
        contentFrame.removeAllViews()
        recyclerViewChats.visibility = View.GONE
        searchInput.visibility = View.GONE
        vibicTitle.visibility  = View.GONE
        vibicTitle.text = "В тренде"
        btnNewChat.visibility = View.GONE
        childFragmentManager.beginTransaction()
            .replace(R.id.contentFrame, TrendingFragment())
            .commitAllowingStateLoss()
    }

    private fun showProfileScreen() {
        currentTab = "profile"
        contentFrame.removeAllViews()
        recyclerViewChats.visibility = View.GONE
        searchInput.visibility = View.GONE
        vibicTitle.visibility  = View.GONE
        btnNewChat.visibility = View.GONE
        childFragmentManager.beginTransaction()
            .replace(R.id.contentFrame, ProfileFragment())
            .commitAllowingStateLoss()
    }

    private fun animateTabSelection(selectedTab: LinearLayout, iconToBounce: View?, text: TextView) {
        if (currentSelectedTab == selectedTab) return
        currentSelectedTab = selectedTab

        iconToBounce?.animate()
            ?.scaleX(1.2f)?.scaleY(1.2f)
            ?.setDuration(150)
            ?.withEndAction {
                iconToBounce.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }?.start()

        val activeColor   = Color.parseColor("#6A11CB")
        val inactiveColor = Color.parseColor("#808080")

        val tabs = listOf(
            Triple(chatsTab,    chatIcon     as View, chatText),
            Triple(contactsTab, contactsIcon as View, contactsText),
            Triple(profileTab,  profileAvatarCard as View, profileText)
        )

        for ((tab, tIcon, tText) in tabs) {
            val isSelected  = tab == selectedTab
            val targetColor = if (isSelected) activeColor else inactiveColor

            if (tab == profileTab) {
                tText.setTextColor(targetColor)
                profileAvatarCard.alpha = if (isSelected) 1f else 0.85f
                if (isSelected) {
                    profileAvatarCard.cardElevation = 4f
                    profileAvatarCard.setCardBackgroundColor(Color.parseColor("#6A11CB"))
                } else {
                    profileAvatarCard.cardElevation = 0f
                    profileAvatarCard.setCardBackgroundColor(Color.parseColor("#8E44FF"))
                }
            } else {
                animateColorChange(tIcon as ImageView, tText, targetColor)
            }

            tText.typeface = if (isSelected) android.graphics.Typeface.DEFAULT_BOLD
            else android.graphics.Typeface.DEFAULT
        }
    }

    private fun animateColorChange(icon: ImageView, text: TextView, targetColor: Int) {
        val colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), text.currentTextColor, targetColor)
        colorAnimator.duration = 200
        colorAnimator.addUpdateListener { animator ->
            val color = animator.animatedValue as Int
            icon.setColorFilter(color)
            text.setTextColor(color)
        }
        colorAnimator.start()
    }

    // ── Живой счётчик непрочитанных (WebSocket) ────────────────────────────────

    /**
     * Подписываемся на тот же поток сообщений, что и ChatDetailActivity.
     * Когда приходит новое сообщение в чат, который сейчас НЕ открыт и НЕ наше
     * собственное — увеличиваем unreadCount этого чата, поднимаем его наверх и
     * обновляем превью/время. Благодаря этому цифры всегда соответствуют
     * реально непрочитанным сообщениям, а не устаревшему значению с сервера.
     */
    private fun observeIncomingMessages() {
        webSocketJob?.cancel()
        webSocketJob = lifecycleScope.launch {
            WebSocketManager.shared.messagesFlow.collect { wsMessage ->
                when (wsMessage.type) {
                    "new_message", "message" -> handleIncomingForList(wsMessage.rawJson)
                }
            }
        }
    }

    private fun handleIncomingForList(rawJson: String) {
        val dto = try {
            val json = JsonParser.parseString(rawJson).asJsonObject
            val msgJson = json.get("payload") ?: json.get("message") ?: json.get("data") ?: json
            Gson().fromJson(msgJson, MessageDto::class.java)
        } catch (e: Exception) {
            Log.e("MainFragment", "Не удалось разобрать входящее сообщение для списка", e)
            null
        } ?: return

        // Пропускаем служебные/пустые пакеты.
        if (dto.senderId.isNullOrBlank() || dto.content.isNullOrBlank()) return

        val incomingChatId = dto.chatId ?: return
        val myId = TokenManager.getUserId().orEmpty()

        // Свои собственные сообщения счётчик не увеличивают.
        val isOwn = dto.senderId == myId
        // Если этот чат прямо сейчас открыт — он считается прочитанным.
        val isOpen = incomingChatId == ChatDetailActivity.currentChatId

        // Текст превью с учётом типа сообщения.
        val previewText = when (dto.type) {
            "audio", "audio_once" -> "🎤 Голосовое сообщение"
            "video_note"          -> "📹 Видеосообщение"
            "image"               -> "🖼 Фото"
            else                  -> dto.content
        }

        activity?.runOnUiThread {
            val idx = cachedChats.indexOfFirst { it.id == incomingChatId }
            if (idx < 0) {
                // Сообщение в чат, которого ещё нет в кэше (например, только что
                // созданный) — подтянем актуальный список с сервера.
                loadChatsInBackground()
                return@runOnUiThread
            }

            val current = cachedChats[idx]
            val shouldCount = !isOwn && !isOpen
            if (shouldCount) {
                // Этот чат снова непрочитан — снимаем флаг "локально прочитан".
                if (locallyReadChatIds.remove(incomingChatId)) saveLocallyReadIds()
            }
            val newUnread = if (isOpen || isOwn) current.unreadCount
            else current.unreadCount + 1

            cachedChats[idx] = current.copy(
                lastMessage     = previewText,
                lastMessageTime = formatTime(dto.createdAt),
                unreadCount     = newUnread
            )

            // Поднимаем чат наверх (порядок с закреплёнными доведёт applyFilter).
            val updated = cachedChats.removeAt(idx)
            cachedChats.add(0, updated)

            saveChatsToCache(cachedChats)
            applyFilter()
            updateUnreadBadge(visibleUnreadCount())
        }
    }

    /** Помечает чат прочитанным локально: счётчик = 0, запоминаем id, обновляем UI. */
    private fun markChatReadLocally(chatId: String) {
        val changed = cachedChats.any { it.id == chatId && it.unreadCount > 0 }
        if (changed) {
            updateChatInCache(chatId) { it.copy(unreadCount = 0) }
        }
        if (locallyReadChatIds.add(chatId)) saveLocallyReadIds()
        updateUnreadBadge(visibleUnreadCount())
    }

    private fun loadLocallyReadIds() {
        val prefs = requireContext().getSharedPreferences("chat_cache", Context.MODE_PRIVATE)
        prefs.getStringSet("locally_read_ids", emptySet())?.let {
            locallyReadChatIds.clear()
            locallyReadChatIds.addAll(it)
        }
    }

    private fun saveLocallyReadIds() {
        requireContext().getSharedPreferences("chat_cache", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("locally_read_ids", locallyReadChatIds.toSet())
            .apply()
    }

    /** Сумма непрочитанных БЕЗ учёта заглушённых (mute) чатов — это и есть число на вкладке. */
    private fun visibleUnreadCount(): Int =
        cachedChats.filter { !it.isMuted }.sumOf { it.unreadCount }

    private fun updateUnreadBadge(count: Int) {
        if (count > 0) {
            chatBadge.visibility = View.VISIBLE
            chatBadge.text = if (count > 99) "99+" else count.toString()
        } else {
            chatBadge.visibility = View.GONE
        }
    }

    private fun updateProfileTabAvatar() {
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val avatarPath = prefs.getString("avatar_path", null)

        if (!avatarPath.isNullOrBlank()) {
            val file = File(avatarPath)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(avatarPath)
                if (bitmap != null) {
                    profileAvatarImage.setImageBitmap(bitmap)
                    profileAvatarImage.visibility = View.VISIBLE
                    profileAvatarText.visibility  = View.GONE
                    return
                }
            } else {
                prefs.edit().remove("avatar_path").apply()
            }
        }

        val firstName = prefs.getString("user_first_name", null)
        val lastName  = prefs.getString("user_last_name", null)
        val nickname  = prefs.getString("user_nickname", "А").orEmpty()

        val initials = when {
            !firstName.isNullOrBlank() && !lastName.isNullOrBlank() ->
                "${firstName.take(1)}${lastName.take(1)}".uppercase()
            !firstName.isNullOrBlank() -> firstName.take(1).uppercase()
            !lastName.isNullOrBlank()  -> lastName.take(1).uppercase()
            nickname.isNotBlank()      -> nickname.take(1).uppercase()
            else -> "А"
        }

        profileAvatarText.text = initials
        profileAvatarText.visibility  = View.VISIBLE
        profileAvatarImage.visibility = View.GONE
    }

    // ── Кэш чатов ─────────────────────────────────────────────────────────────

    private fun loadCachedChats() {
        val prefs = requireContext().getSharedPreferences("chat_cache", Context.MODE_PRIVATE)
        val cachedJson = prefs.getString("chats", null)
        if (cachedJson != null) {
            try {
                val type = object : TypeToken<List<Chat>>() {}.type
                val chats: List<Chat> = Gson().fromJson(cachedJson, type)
                cachedChats.clear()
                cachedChats.addAll(chats)
                if (cachedChats.isNotEmpty()) {
                    applyFilter()
                    updateUnreadBadge(visibleUnreadCount())
                }
            } catch (e: Exception) {
                Log.e("MainFragment", "Ошибка загрузки кэша", e)
            }
        }
    }

    private fun saveChatsToCache(chats: List<Chat>) {
        try {
            val json = Gson().toJson(chats)
            requireContext().getSharedPreferences("chat_cache", Context.MODE_PRIVATE)
                .edit().putString("chats", json).apply()
        } catch (e: Exception) {
            Log.e("MainFragment", "Ошибка сохранения кэша", e)
        }
    }

    private fun loadChatsInBackground() {
        if (isLoading) return
        isLoading = true
        lifecycleScope.launch {
            try {
                val token = TokenManager.getAccessToken()
                if (token.isNullOrBlank()) { isLoading = false; return@launch }

                val response = ApiClient.apiService.getChats()
                if (response.isSuccessful) {
                    // Сохраняем локальные флаги (mute/pin/unread), которых нет на сервере.
                    val prevById = cachedChats.associateBy { it.id }
                    val chats = (response.body() ?: emptyList())
                        // Свежие сверху: по времени последнего сообщения (или updatedAt чата).
                        // ISO-даты корректно сортируются как строки, парсить не нужно.
                        .sortedByDescending { it.lastMessage?.createdAt ?: it.updatedAt ?: "" }
                        .map { chatResponse ->
                            val prev = prevById[chatResponse.id]
                            // unread: на бэке пока нет отметки "прочитано", поэтому
                            // локальное состояние — источник правды.
                            //  • чат, прочитанный локально → 0;
                            //  • иначе берём большее из локального и серверного
                            //    значений (локальный живой счётчик мог уже обогнать
                            //    сервер за счёт сообщений по WebSocket).
                            val mergedUnread = when {
                                locallyReadChatIds.contains(chatResponse.id) -> 0
                                else -> maxOf(prev?.unreadCount ?: 0, chatResponse.unreadCount)
                            }
                            Chat(
                                id              = chatResponse.id,
                                name            = chatResponse.name ?: getChatName(chatResponse),
                                lastMessage     = chatResponse.lastMessage?.content ?: "Нет сообщений",
                                lastMessageTime = formatTime(chatResponse.lastMessage?.createdAt ?: chatResponse.updatedAt),
                                avatarUrl       = chatResponse.avatarUrl,
                                unreadCount     = mergedUnread,
                                isOnline        = false,
                                type            = chatResponse.type,
                                otherUserId     = null,
                                isMuted         = prev?.isMuted ?: false,
                                isPinned        = prev?.isPinned ?: false
                            )
                        }
                    // Чистим набор локально-прочитанных от чатов, которых уже нет.
                    val serverIds = chats.map { it.id }.toHashSet()
                    if (locallyReadChatIds.retainAll(serverIds)) saveLocallyReadIds()

                    cachedChats.clear()
                    cachedChats.addAll(chats)
                    applyFilter()
                    saveChatsToCache(cachedChats)
                    updateUnreadBadge(visibleUnreadCount())
                }
            } catch (e: Exception) {
                Log.e("MainFragment", "Ошибка загрузки чатов", e)
            } finally {
                isLoading = false
            }
        }
    }

    private fun getChatName(chatResponse: ChatDto) = when (chatResponse.type) {
        "private" -> "Пользователь"
        "group"   -> chatResponse.name ?: "Группа"
        else      -> "Чат"
    }

    private fun formatTime(dateString: String?): String {
        if (dateString.isNullOrBlank()) return ""
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
                if (date != null) return SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            } catch (_: Exception) {}
        }
        return ""
    }

    companion object {
        private val LOCALE_RU = Locale("ru")
    }
}