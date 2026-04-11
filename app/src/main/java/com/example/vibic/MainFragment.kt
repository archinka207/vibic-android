package com.example.vibic

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
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
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vibic.network.ApiClient
import com.example.vibic.network.TokenManager
import com.example.vibic.network.ChatDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
    private lateinit var callTab: LinearLayout
    private lateinit var profileTab: LinearLayout

    private lateinit var chatIcon: ImageView
    private lateinit var contactsIcon: ImageView
    private lateinit var callIcon: ImageView

    private lateinit var chatText: TextView
    private lateinit var contactsText: TextView
    private lateinit var callText: TextView
    private lateinit var profileText: TextView

    private lateinit var profileAvatarCard: CardView
    private lateinit var profileAvatarImage: ImageView
    private lateinit var profileAvatarText: TextView

    private lateinit var chatBadge: TextView
    private lateinit var tabIndicator: View

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var recyclerViewChats: RecyclerView

    private val cachedChats = mutableListOf<Chat>()
    private var currentSelectedTab: LinearLayout? = null
    private var isLoading = false

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

        // --- ОБРАБОТКА СИСТЕМНЫХ ОТСТУПОВ ---
        // Это предотвратит наложение нижней панели на системные кнопки (жесты)
        // и сдвинет заголовок из-под статус-бара
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        initViews(view)
        setupRecyclerView()
        setupSearchTouchHandling()
        setupTabs()

        loadCachedChats()
        loadChatsInBackground()
        updateProfileTabAvatar()

        setupAvatarUpdateListener()

        rootView.post {
            animateTabSelection(chatsTab, chatIcon, chatText)
            showChatsScreen()
        }
    }

    override fun onResume() {
        super.onResume()
        updateProfileTabAvatar()
        loadCachedChats()
    }

    private fun initViews(view: View) {
        contentFrame = view.findViewById(R.id.contentFrame)
        searchInput = view.findViewById(R.id.searchInput)
        vibicTitle = view.findViewById(R.id.Vibic)

        chatsTab = view.findViewById(R.id.chatsTab)
        contactsTab = view.findViewById(R.id.contactsTab)
        callTab = view.findViewById(R.id.callTab)
        profileTab = view.findViewById(R.id.profileTab)

        chatIcon = view.findViewById(R.id.chatIcon)
        contactsIcon = view.findViewById(R.id.contactsIcon)
        callIcon = view.findViewById(R.id.callIcon)

        chatText = view.findViewById(R.id.chatText)
        contactsText = view.findViewById(R.id.contactsText)
        callText = view.findViewById(R.id.callText)
        profileText = view.findViewById(R.id.profileText)

        profileAvatarCard = view.findViewById(R.id.profileAvatarCard)
        profileAvatarImage = view.findViewById(R.id.profileAvatarImage)
        profileAvatarText = view.findViewById(R.id.profileAvatarText)

        chatBadge = view.findViewById(R.id.chatBadge)
        tabIndicator = view.findViewById(R.id.tabIndicator)

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
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter { chat ->
            val intent = Intent(requireContext(), ChatDetailActivity::class.java).apply {
                putExtra("chat_id", chat.id)
                putExtra("chat_name", chat.name)
            }
            startActivity(intent)
        }

        recyclerViewChats.adapter = chatAdapter
    }

    private fun setupTabs() {
        chatsTab.setOnClickListener {
            animateTabSelection(chatsTab, chatIcon, chatText)
            showChatsScreen()
        }

        contactsTab.setOnClickListener {
            animateTabSelection(contactsTab, contactsIcon, contactsText)
            showContactsScreen()
        }

        callTab.setOnClickListener {
            animateTabSelection(callTab, callIcon, callText)
            showCallsScreen()
        }

        profileTab.setOnClickListener {
            animateTabSelection(profileTab, profileAvatarCard, profileText)
            showProfileScreen()
        }
    }

    private fun setupAvatarUpdateListener() {
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == "avatar_path" || key == "user_nickname" || key == "user_first_name" || key == "user_last_name") {
                updateProfileTabAvatar()
            }
        }
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
        childFragmentManager.fragments.forEach { fragment ->
            childFragmentManager.beginTransaction().remove(fragment).commitAllowingStateLoss()
        }

        contentFrame.removeAllViews()
        contentFrame.addView(recyclerViewChats)

        recyclerViewChats.visibility = View.VISIBLE
        searchInput.visibility = View.VISIBLE
        vibicTitle.visibility = View.VISIBLE
        vibicTitle.text = "Чаты"
    }

    private fun showContactsScreen() {
        contentFrame.removeAllViews()
        recyclerViewChats.visibility = View.GONE
        searchInput.visibility = View.GONE
        vibicTitle.visibility = View.VISIBLE
        vibicTitle.text = "Контакты"

        childFragmentManager.beginTransaction()
            .replace(R.id.contentFrame, ContactsFragment())
            .commitAllowingStateLoss()
    }

    private fun showCallsScreen() {
        contentFrame.removeAllViews()
        recyclerViewChats.visibility = View.GONE
        searchInput.visibility = View.GONE
        vibicTitle.visibility = View.VISIBLE
        vibicTitle.text = "Настройки"

        childFragmentManager.beginTransaction()
            .replace(R.id.contentFrame, CallsFragment())
            .commitAllowingStateLoss()
    }

    private fun showProfileScreen() {
        contentFrame.removeAllViews()
        recyclerViewChats.visibility = View.GONE
        searchInput.visibility = View.GONE
        vibicTitle.visibility = View.GONE

        childFragmentManager.beginTransaction()
            .replace(R.id.contentFrame, ProfileFragment())
            .commitAllowingStateLoss()
    }

    private fun animateTabSelection(selectedTab: LinearLayout, iconToBounce: View?, text: TextView) {
        if (currentSelectedTab == selectedTab) return
        currentSelectedTab = selectedTab

        tabIndicator.animate()
            .x(selectedTab.x)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator(1.0f))
            .start()

        iconToBounce?.animate()
            ?.scaleX(1.2f)?.scaleY(1.2f)
            ?.setDuration(150)
            ?.withEndAction {
                iconToBounce.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }?.start()

        val activeColor = Color.parseColor("#6A11CB")
        val inactiveColor = Color.parseColor("#808080")

        val tabs = listOf(
            Triple(chatsTab, chatIcon as View, chatText),
            Triple(contactsTab, contactsIcon as View, contactsText),
            Triple(callTab, callIcon as View, callText),
            Triple(profileTab, profileAvatarCard as View, profileText)
        )

        for ((tab, tIcon, tText) in tabs) {
            val isSelected = tab == selectedTab
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
                    profileAvatarText.visibility = View.GONE
                    return
                }
            } else {
                prefs.edit().remove("avatar_path").apply()
            }
        }

        val firstName = prefs.getString("user_first_name", null)
        val lastName = prefs.getString("user_last_name", null)
        val nickname = prefs.getString("user_nickname", "А").orEmpty()

        val initials = when {
            !firstName.isNullOrBlank() && !lastName.isNullOrBlank() ->
                "${firstName.take(1)}${lastName.take(1)}".uppercase()
            !firstName.isNullOrBlank() -> firstName.take(1).uppercase()
            !lastName.isNullOrBlank() -> lastName.take(1).uppercase()
            nickname.isNotBlank() -> nickname.take(1).uppercase()
            else -> "А"
        }

        profileAvatarText.text = initials
        profileAvatarText.visibility = View.VISIBLE
        profileAvatarImage.visibility = View.GONE
    }

    private fun loadCachedChats() {
        val prefs = requireContext().getSharedPreferences("chat_cache", Context.MODE_PRIVATE)
        val cachedJson = prefs.getString("chats", null)

        if (cachedJson != null) {
            try {
                val gson = Gson()
                val type = object : TypeToken<List<Chat>>() {}.type
                val chats: List<Chat> = gson.fromJson(cachedJson, type)
                cachedChats.clear()
                cachedChats.addAll(chats)

                if (cachedChats.isNotEmpty()) {
                    chatAdapter.submitList(cachedChats.toList())
                    val totalUnread = cachedChats.sumOf { it.unreadCount }
                    updateUnreadBadge(totalUnread)
                }
            } catch (e: Exception) {
                Log.e("MainFragment", "Ошибка загрузки кэша", e)
            }
        }
    }

    private fun saveChatsToCache(chats: List<Chat>) {
        try {
            val gson = Gson()
            val json = gson.toJson(chats)
            val prefs = requireContext().getSharedPreferences("chat_cache", Context.MODE_PRIVATE)
            prefs.edit().putString("chats", json).apply()
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
                if (token.isNullOrBlank()) {
                    isLoading = false
                    return@launch
                }

                val response = ApiClient.apiService.getChats()

                if (response.isSuccessful()) {
                    val chatsResponse = response.body() ?: emptyList()

                    val chats = chatsResponse.map { chatResponse ->
                        Chat(
                            id = chatResponse.id,
                            name = chatResponse.name ?: getChatName(chatResponse),
                            lastMessage = chatResponse.lastMessage?.content ?: "Нет сообщений",
                            lastMessageTime = formatTime(chatResponse.lastMessage?.createdAt ?: chatResponse.updatedAt),
                            avatarUrl = chatResponse.avatarUrl,
                            unreadCount = chatResponse.unreadCount,
                            isOnline = false,
                            type = chatResponse.type,
                            otherUserId = null
                        )
                    }

                    cachedChats.clear()
                    cachedChats.addAll(chats)
                    chatAdapter.submitList(cachedChats.toList())
                    saveChatsToCache(cachedChats)

                    val totalUnread = cachedChats.sumOf { it.unreadCount }
                    updateUnreadBadge(totalUnread)
                }
            } catch (e: Exception) {
                Log.e("MainFragment", "❌ Ошибка загрузки чатов", e)
            } finally {
                isLoading = false
            }
        }
    }

    private fun getChatName(chatResponse: ChatDto): String {
        return when (chatResponse.type) {
            "private" -> "Пользователь"
            "group" -> chatResponse.name ?: "Группа"
            else -> "Чат"
        }
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