package com.example.vibic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.vibic.network.ApiClient
import com.example.vibic.network.TokenManager
import kotlinx.coroutines.launch

/**
 * Профиль ГРУППЫ — аналог ContactProfileActivity, но для групповых чатов.
 *
 * ВАЖНО про данные:
 *  - GET api/chats/{chatId}              → базовая инфа (имя, аватар, описание).
 *    Участников там НЕТ.
 *  - GET api/chats/{chatId}/participants → массив участников (роль, онлайн, user).
 *    Именно отсюда берём количество участников и список.
 *
 * Передаваемые extras:
 *   chat_id    — id чата (обязателен)
 *   chat_name  — имя группы (для мгновенного показа до загрузки)
 */
class GroupProfileActivity : AppCompatActivity() {

    private lateinit var backButton: ImageView
    private lateinit var avatarImage: ImageView
    private lateinit var avatarGroupIcon: ImageView
    private lateinit var avatarText: TextView
    private lateinit var profileName: TextView
    private lateinit var profileStatus: TextView

    private lateinit var aboutSection: LinearLayout
    private lateinit var aboutText: TextView

    private lateinit var inviteSection: LinearLayout
    private lateinit var inviteLinkText: TextView
    private lateinit var inviteCopyButton: ImageView

    private lateinit var membersHeader: TextView
    private lateinit var membersRecycler: RecyclerView
    private lateinit var showAllMembers: TextView
    private lateinit var membersAdapter: GroupMembersAdapter

    private lateinit var notifStatus: TextView
    private lateinit var rowNotifications: LinearLayout
    private lateinit var rowLeaveGroup: LinearLayout

    private lateinit var btnMessage: LinearLayout
    private lateinit var mediaAllButton: TextView

    // Admin UI
    private lateinit var editButton: ImageView
    private lateinit var btnAddMemberHeader: TextView
    private lateinit var rowAddMember: LinearLayout
    private lateinit var rowAdmins: LinearLayout
    private lateinit var adminsDivider: View
    private lateinit var adminsCount: TextView

    private var chatId = ""
    private var chatName = ""
    private var myUserId = ""
    /** Роль текущего пользователя в этой группе: owner | admin | member | "" */
    private var myRole = ""

    // Сколько участников показывать в свёрнутом виде
    private val PREVIEW_LIMIT = 4
    private var allMembers: List<GroupMember> = emptyList()
    private var expanded = false

    // Запуск экрана добавления участников; при RESULT_OK — перезагружаем список
    private val addMembersLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) loadParticipants()
    }

    // Запуск экрана редактирования; при RESULT_OK — перезагружаем инфу о группе
    private val editGroupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) loadGroupDetails()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_group_profile)

        chatId = intent.getStringExtra("chat_id") ?: ""
        chatName = intent.getStringExtra("chat_name") ?: "Группа"
        myUserId = TokenManager.getUserId() ?: ""

        initViews()
        setupRecycler()
        populateBasicInfo()
        setupClickListeners()

        if (chatId.isNotEmpty()) {
            loadGroupDetails()
            loadParticipants()
        }
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        avatarImage = findViewById(R.id.avatarImage)
        avatarGroupIcon = findViewById(R.id.avatarGroupIcon)
        avatarText = findViewById(R.id.avatarText)
        profileName = findViewById(R.id.profileName)
        profileStatus = findViewById(R.id.profileStatus)

        aboutSection = findViewById(R.id.aboutSection)
        aboutText = findViewById(R.id.aboutText)

        inviteSection = findViewById(R.id.inviteSection)
        inviteLinkText = findViewById(R.id.inviteLinkText)
        inviteCopyButton = findViewById(R.id.inviteCopyButton)

        membersHeader = findViewById(R.id.membersHeader)
        membersRecycler = findViewById(R.id.membersRecycler)
        showAllMembers = findViewById(R.id.showAllMembers)

        notifStatus = findViewById(R.id.notifStatus)
        rowNotifications = findViewById(R.id.rowNotifications)
        rowLeaveGroup = findViewById(R.id.rowLeaveGroup)

        btnMessage = findViewById(R.id.btnMessage)
        mediaAllButton = findViewById(R.id.mediaAllButton)

        editButton = findViewById(R.id.editButton)
        btnAddMemberHeader = findViewById(R.id.btnAddMemberHeader)
        rowAddMember = findViewById(R.id.rowAddMember)
        rowAdmins = findViewById(R.id.rowAdmins)
        adminsDivider = findViewById(R.id.adminsDivider)
        adminsCount = findViewById(R.id.adminsCount)
    }

    private fun setupRecycler() {
        membersAdapter = GroupMembersAdapter { member ->
            Toast.makeText(this, member.name, Toast.LENGTH_SHORT).show()
        }
        membersRecycler.layoutManager = LinearLayoutManager(this)
        membersRecycler.adapter = membersAdapter
    }

    private fun populateBasicInfo() {
        profileName.text = chatName
        profileStatus.text = "загрузка…"
        avatarGroupIcon.visibility = View.VISIBLE
        avatarText.visibility = View.GONE
        avatarImage.visibility = View.GONE

        // Статус уведомлений из локальных настроек (по умолчанию — включены)
        val notifEnabled = getSharedPreferences("group_notif_prefs", MODE_PRIVATE)
            .getBoolean("notif_${chatId}_enabled", true)
        notifStatus.text = if (notifEnabled) "Вкл" else "Выкл"
    }

    /** Базовая инфа чата: имя, аватар, описание. Участников здесь НЕТ. */
    private fun loadGroupDetails() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getChat(chatId)
                if (response.isSuccessful) {
                    val chat = response.body() ?: return@launch
                    val displayName = chat.name ?: chatName
                    profileName.text = displayName

                    if (!chat.avatarUrl.isNullOrBlank()) {
                        avatarImage.visibility = View.VISIBLE
                        avatarGroupIcon.visibility = View.GONE
                        avatarText.visibility = View.GONE
                        Glide.with(this@GroupProfileActivity)
                            .load(chat.avatarUrl)
                            .circleCrop()
                            .into(avatarImage)
                    }

                    val description = chat.description
                    if (!description.isNullOrBlank()) {
                        aboutSection.visibility = View.VISIBLE
                        aboutText.text = description
                    }

                    // Счётчик участников приходит сразу в ответе чата (participants_count).
                    // Список (имена/аватары) подгружается отдельно в loadParticipants().
                    chat.participantsCount?.let { count ->
                        if (allMembers.isEmpty()) {
                            profileStatus.text = buildStatusLabel(count, 0)
                            membersHeader.text = "Участники · $count"
                        }
                    }
                }
            } catch (_: Exception) {
                // тихо игнорируем — показываем то, что есть из intent
            }
        }
    }

    /**
     * Участники — отдельный запрос GET api/chats/{chatId}/participants.
     * Размер ответа = количество участников, online берём из user.online.
     */
    private fun loadParticipants() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getParticipants(chatId, limit = 100, offset = 0)
                if (!response.isSuccessful) {
                    profileStatus.text = "участники недоступны"
                    return@launch
                }
                val list = response.body() ?: emptyList()

                val members = list.map { p ->
                    val u = p.user
                    val fullName = listOfNotNull(
                        u?.firstName?.trim()?.takeIf { it.isNotEmpty() },
                        u?.lastName?.trim()?.takeIf { it.isNotEmpty() }
                    ).joinToString(" ").ifBlank { "Пользователь" }

                    GroupMember(
                        id = p.userId,
                        name = fullName,
                        avatarUrl = u?.avatarUrl,
                        role = p.role,
                        isOnline = u?.online ?: false
                    )
                }
                // owner → admin → остальные
                val sorted = members.sortedBy { m ->
                    when (m.role.lowercase()) {
                        "owner" -> 0
                        "admin" -> 1
                        else -> 2
                    }
                }
                setMembers(sorted)
            } catch (_: Exception) {
                profileStatus.text = "участники недоступны"
            }
        }
    }

    private fun setMembers(members: List<GroupMember>) {
        allMembers = members
        val online = members.count { it.isOnline }
        profileStatus.text = buildStatusLabel(members.size, online)
        membersHeader.text = "Участники · ${members.size}"

        // Роль текущего пользователя в группе
        myRole = members.firstOrNull { it.id == myUserId }?.role?.lowercase() ?: ""
        applyAdminVisibility()

        // Кол-во администраторов (owner считается админом)
        val adminCount = members.count { it.role.lowercase() == "admin" || it.role.lowercase() == "owner" }
        adminsCount.text = adminCount.toString()

        if (members.isEmpty()) {
            membersRecycler.visibility = View.GONE
            showAllMembers.visibility = View.GONE
            return
        }
        membersRecycler.visibility = View.VISIBLE
        applyMembersView()

        showAllMembers.visibility =
            if (members.size > PREVIEW_LIMIT) View.VISIBLE else View.GONE
    }

    /**
     * Карандаш и "Администраторы" — только для owner/admin.
     * "Добавить участника" показываем всем (но сервер разрешит добавление
     * только owner/admin — обычному участнику вернётся 403 с понятным тостом).
     */
    private fun applyAdminVisibility() {
        val isAdmin = myRole == "owner" || myRole == "admin"
        editButton.visibility = if (isAdmin) View.VISIBLE else View.GONE
        rowAdmins.visibility = if (isAdmin) View.VISIBLE else View.GONE
        adminsDivider.visibility = if (isAdmin) View.VISIBLE else View.GONE
    }

    private fun applyMembersView() {
        val list = if (expanded || allMembers.size <= PREVIEW_LIMIT) allMembers
        else allMembers.take(PREVIEW_LIMIT)
        membersAdapter.submitList(list)
        showAllMembers.text =
            if (expanded) "Свернуть" else "Показать всех участников"
    }

    private fun buildStatusLabel(total: Int, online: Int): String {
        val totalLabel = when {
            total % 100 in 11..19 -> "$total участников"
            total % 10 == 1 -> "$total участник"
            total % 10 in 2..4 -> "$total участника"
            else -> "$total участников"
        }
        return if (online > 0) "$totalLabel, $online в сети" else totalLabel
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { finish() }
        btnMessage.setOnClickListener { finish() }

        mediaAllButton.setOnClickListener {
            startActivity(
                Intent(this, ChatMediaActivity::class.java).apply {
                    putExtra(ChatMediaActivity.EXTRA_CHAT_ID, chatId)
                    putExtra(ChatMediaActivity.EXTRA_CHAT_NAME, chatName)
                }
            )
        }

        findViewById<LinearLayout>(R.id.btnMute).setOnClickListener {
            openNotificationsSheet()
        }

        findViewById<LinearLayout>(R.id.btnSearch).setOnClickListener {
            Toast.makeText(this, "Поиск в разработке", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.btnShare).setOnClickListener {
            val link = inviteLinkText.text?.toString().orEmpty()
            val shareText = if (link.isNotBlank()) link else "Группа «${profileName.text}»"
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }, "Поделиться"
            ))
        }

        inviteCopyButton.setOnClickListener { copyInviteLink() }
        inviteSection.setOnClickListener { copyInviteLink() }

        showAllMembers.setOnClickListener {
            expanded = !expanded
            applyMembersView()
        }

        editButton.setOnClickListener {
            val intent = Intent(this, EditGroupActivity::class.java)
            intent.putExtra(EditGroupActivity.EXTRA_CHAT_ID, chatId)
            intent.putExtra(EditGroupActivity.EXTRA_CHAT_NAME, chatName)
            editGroupLauncher.launch(intent)
        }

        val openAddMembers = View.OnClickListener { openAddMembers() }
        btnAddMemberHeader.setOnClickListener(openAddMembers)
        rowAddMember.setOnClickListener(openAddMembers)

        rowAdmins.setOnClickListener {
            val intent = Intent(this, GroupAdminsActivity::class.java)
            intent.putExtra(GroupAdminsActivity.EXTRA_CHAT_ID, chatId)
            startActivity(intent)
        }

        rowNotifications.setOnClickListener { openNotificationsSheet() }

        rowLeaveGroup.setOnClickListener {
            AlertDialog.Builder(this, R.style.VibicAlertDialog)
                .setTitle("Покинуть группу")
                .setMessage("Вы уверены, что хотите покинуть «${profileName.text}»?")
                .setPositiveButton("Покинуть") { _, _ -> leaveGroup() }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    private fun openAddMembers() {
        val intent = Intent(this, AddMembersActivity::class.java)
        intent.putExtra(AddMembersActivity.EXTRA_CHAT_ID, chatId)
        addMembersLauncher.launch(intent)
    }

    private fun openNotificationsSheet() {
        val sheet = GroupNotificationsSheet.new(chatId, chatName)
        sheet.onChanged = { enabled ->
            notifStatus.text = if (enabled) "Вкл" else "Выкл"
        }
        sheet.show(supportFragmentManager, "group_notifications")
    }

    private fun leaveGroup() {
        lifecycleScope.launch {
            try {
                val resp = ApiClient.apiService.leaveChat(chatId)
                if (resp.isSuccessful) {
                    Toast.makeText(this@GroupProfileActivity, "Вы покинули группу", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@GroupProfileActivity, MainActivityApp::class.java)
                    intent.flags =
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@GroupProfileActivity, "Не удалось покинуть группу (${resp.code()})", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@GroupProfileActivity, "Ошибка сети", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyInviteLink() {
        val link = inviteLinkText.text?.toString().orEmpty()
        if (link.isBlank()) return
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("invite", link))
        Toast.makeText(this, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
    }
}