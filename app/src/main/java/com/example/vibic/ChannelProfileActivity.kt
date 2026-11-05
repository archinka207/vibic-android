package com.example.vibic

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.vibic.channel.AdminRights
import com.example.vibic.channel.ChannelAdmins
import com.example.vibic.channel.ChannelRole
import com.example.vibic.channel.ChannelStaff
import com.example.vibic.channel.ChannelStats
import com.example.vibic.channel.FakeChannelStatsRepository
import com.example.vibic.network.ApiClient
import com.example.vibic.network.ChatDto
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Профиль канала для владельца и админа. Подписчику показываем [ContactProfileActivity].
 * Что видно админу, решают его права: кнопки без прав скрыты.
 *
 * Extras: chat_id (обязателен), chat_name.
 */
class ChannelProfileActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_CHAT_NAME = "chat_name"
    }

    private val statsRepository = FakeChannelStatsRepository()

    private lateinit var editButton: ImageView
    private lateinit var avatarImage: ImageView
    private lateinit var avatarText: TextView
    private lateinit var profileName: TextView
    private lateinit var subscribersText: TextView
    private lateinit var roleBadgeIcon: ImageView
    private lateinit var roleBadgeText: TextView

    private lateinit var btnPost: View
    private lateinit var btnStats: View
    private lateinit var btnInvite: View
    private lateinit var btnShare: View

    private lateinit var statsRow: View
    private lateinit var statSubscribers: View
    private lateinit var statReach: View
    private lateinit var statEr: View

    private lateinit var rowAdmins: View
    private lateinit var rowSubscribers: View
    private lateinit var rowType: View
    private lateinit var rowInviteLinks: View
    private lateinit var rowComments: View
    private lateinit var rowBlacklist: View
    private lateinit var rowLog: View

    private lateinit var rowDanger: View
    private lateinit var dangerIcon: ImageView
    private lateinit var dangerText: TextView

    private var chatId = ""
    private var chatName = ""
    private var chat: ChatDto? = null
    private var staff: ChannelStaff? = null
    private var role = ChannelRole.SUBSCRIBER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_channel_profile)

        chatId = intent.getStringExtra(EXTRA_CHAT_ID).orEmpty()
        chatName = intent.getStringExtra(EXTRA_CHAT_NAME) ?: "Канал"
        if (chatId.isBlank()) {
            Toast.makeText(this, "Ошибка: канал не передан", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        initViews()
        showName(chatName)
        setupClickListeners()
    }

    // Права могли поменяться на экране админов — перечитываем при каждом возврате.
    override fun onResume() {
        super.onResume()
        load()
    }

    private fun initViews() {
        editButton = findViewById(R.id.editButton)
        avatarImage = findViewById(R.id.avatarImage)
        avatarText = findViewById(R.id.avatarText)
        profileName = findViewById(R.id.profileName)
        subscribersText = findViewById(R.id.subscribersText)
        roleBadgeIcon = findViewById(R.id.roleBadgeIcon)
        roleBadgeText = findViewById(R.id.roleBadgeText)

        btnPost = setupAction(R.id.btnPost, R.drawable.ic_send, "Пост", primary = true)
        btnStats = setupAction(R.id.btnStats, R.drawable.ic_trending, "Статистика")
        btnInvite = setupAction(R.id.btnInvite, R.drawable.ic_chat_add, "Пригласить")
        btnShare = setupAction(R.id.btnShare, android.R.drawable.ic_menu_share, "Поделиться")

        statsRow = findViewById(R.id.statsRow)
        statSubscribers = setupStat(R.id.statSubscribers, "подписчики")
        statReach = setupStat(R.id.statReach, "охват поста")
        statEr = setupStat(R.id.statEr, "ER")

        rowAdmins = setupRow(R.id.rowAdmins, R.drawable.ic_shield, "Администраторы")
        rowSubscribers = setupRow(R.id.rowSubscribers, R.drawable.ic_group, "Подписчики")
        rowType = setupRow(R.id.rowType, R.drawable.ic_globe, "Тип канала")
        rowInviteLinks = setupRow(R.id.rowInviteLinks, R.drawable.ic_key, "Пригласительные ссылки")
        rowComments = setupRow(R.id.rowComments, R.drawable.ic_chat, "Комментарии и реакции")
        rowBlacklist = setupRow(R.id.rowBlacklist, R.drawable.ic_block, "Чёрный список")
        rowLog = setupRow(R.id.rowLog, R.drawable.ic_history, "Журнал действий")

        rowDanger = findViewById(R.id.rowDanger)
        dangerIcon = findViewById(R.id.dangerIcon)
        dangerText = findViewById(R.id.dangerText)
    }

    private fun setupAction(id: Int, @DrawableRes icon: Int, label: String, primary: Boolean = false): View {
        val root = findViewById<View>(id)
        val iconView = root.findViewById<ImageView>(R.id.actionIcon)
        iconView.setImageResource(icon)
        root.findViewById<TextView>(R.id.actionLabel).text = label
        if (primary) {
            root.findViewById<View>(R.id.actionCapsule).setBackgroundResource(R.drawable.bg_channel_capsule_primary)
            iconView.setColorFilter(ContextCompat.getColor(this, R.color.white))
        }
        return root
    }

    private fun setupStat(id: Int, label: String): View =
        findViewById<View>(id).also { it.findViewById<TextView>(R.id.statLabel).text = label }

    private fun setupRow(id: Int, @DrawableRes icon: Int, title: String): View =
        findViewById<View>(id).also {
            it.findViewById<ImageView>(R.id.rowIcon).setImageResource(icon)
            it.findViewById<TextView>(R.id.rowTitle).text = title
        }

    // ── Загрузка ─────────────────────────────────────────────────────────────

    private fun load() {
        lifecycleScope.launch {
            val loadedChat = try {
                ApiClient.apiService.getChat(chatId).body()
            } catch (_: Exception) {
                null
            }
            loadedChat?.let { chat = it; showChat(it) }

            val loadedStaff = ChannelAdmins.loadStaff(chatId)
            if (loadedStaff == null) {
                // Не знаем роль — показываем обычный профиль, если ещё ничего не показали
                if (staff == null) openSubscriberProfile()
                else Toast.makeText(this@ChannelProfileActivity, "Нет соединения", Toast.LENGTH_SHORT).show()
                return@launch
            }
            staff = loadedStaff
            role = loadedStaff.myRole(ChannelAdmins.myUserId())
            if (role == ChannelRole.SUBSCRIBER) {
                // Например, админа сняли, пока экран был открыт
                openSubscriberProfile(); return@launch
            }
            render(loadedStaff)
        }
    }

    private fun openSubscriberProfile() {
        startActivity(Intent(this, ContactProfileActivity::class.java).apply {
            putExtra("chat_id", chatId)
            putExtra("chat_name", profileName.text.toString())
        })
        finish()
    }

    private fun showName(name: String) {
        profileName.text = name
        avatarText.text = name.trim().take(1).uppercase()
    }

    private fun showChat(c: ChatDto) {
        c.name?.takeIf { it.isNotBlank() }?.let { chatName = it; showName(it) }

        if (!c.avatarUrl.isNullOrBlank()) {
            avatarImage.visibility = View.VISIBLE
            avatarText.visibility = View.GONE
            Glide.with(this).load(c.avatarUrl).circleCrop().into(avatarImage)
        }

        val count = c.participantsCount
        subscribersText.text = if (count != null) "$count ${subscribersWord(count)}" else ""
        statSubscribers.findViewById<TextView>(R.id.statValue).text = count?.let(::formatCount) ?: "—"
        rowSubscribers.findViewById<TextView>(R.id.rowValue).text = count?.let(::formatCount).orEmpty()

        rowType.findViewById<TextView>(R.id.rowValue).text =
            c.username?.takeIf { it.isNotBlank() }?.let { "Публичный · @${it.removePrefix("@")}" } ?: "Частный"
    }

    // ── Отрисовка по роли и правам ───────────────────────────────────────────

    private fun render(s: ChannelStaff) {
        val me = ChannelAdmins.myUserId()
        val rights: AdminRights = s.rightsOf(me)
        val isOwner = role == ChannelRole.OWNER

        if (isOwner) {
            roleBadgeIcon.setImageResource(R.drawable.ic_crown)
            roleBadgeText.text = "Вы владелец канала"
        } else {
            roleBadgeIcon.setImageResource(R.drawable.ic_shield)
            val title = s.adminOrNull(me)?.customTitle.orEmpty()
            roleBadgeText.text = if (title.isBlank()) "Вы администратор" else "Вы администратор · $title"
        }

        editButton.visibleIf(rights.changeProfile)

        btnPost.visibleIf(rights.postMessages)
        btnStats.visibleIf(rights.viewStats)
        btnInvite.visibleIf(rights.inviteUsers)
        // «Поделиться» доступно всем

        statsRow.visibleIf(rights.viewStats)
        if (rights.viewStats) loadStats()

        // Админы и журнал видны всем админам, остальное — по правам
        rowAdmins.findViewById<TextView>(R.id.rowValue).text = (s.admins.size + 1).toString()
        rowType.visibleIf(rights.changeProfile)
        rowInviteLinks.visibleIf(rights.inviteUsers)
        rowComments.visibleIf(rights.changeProfile)
        rowBlacklist.visibleIf(rights.banUsers)

        if (isOwner) {
            dangerIcon.setImageResource(R.drawable.ic_trash)
            dangerText.text = "Удалить канал"
        } else {
            dangerIcon.setImageResource(R.drawable.ic_logout)
            dangerText.text = "Покинуть канал"
        }
    }

    private fun loadStats() {
        lifecycleScope.launch {
            val stats = statsRepository.getStats(chatId).getOrNull() ?: ChannelStats()
            setStat(statSubscribers, value = null, growth = stats.subscribersGrowth?.let { signed(it.toDouble(), "") })
            setStat(statReach, stats.postReach?.let(::formatCount), stats.postReachGrowthPercent?.let { signed(it, "%") })
            setStat(statEr, stats.erPercent?.let { "${oneDecimal(it)}%" }, stats.erGrowthPoints?.let { signed(it, "%") })
        }
    }

    /** [value] = null — значение не трогаем (у подписчиков оно уже из чата). */
    private fun setStat(card: View, value: String?, growth: Pair<String, Boolean>?) {
        value?.let { card.findViewById<TextView>(R.id.statValue).text = it }
        val growthView = card.findViewById<TextView>(R.id.statGrowth)
        if (growth == null) {
            growthView.visibility = View.INVISIBLE
            return
        }
        val (text, positive) = growth
        growthView.text = text
        growthView.setTextColor(
            ContextCompat.getColor(this, if (positive) R.color.channel_growth else R.color.channel_danger)
        )
        growthView.visibility = View.VISIBLE
    }

    // ── Нажатия ──────────────────────────────────────────────────────────────

    private fun setupClickListeners() {
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        editButton.setOnClickListener {
            startActivity(Intent(this, EditGroupActivity::class.java).apply {
                putExtra(EditGroupActivity.EXTRA_CHAT_ID, chatId)
                putExtra(EditGroupActivity.EXTRA_CHAT_NAME, chatName)
            })
        }

        // Пост пишется в самом канале — возвращаемся в него
        btnPost.setOnClickListener {
            startActivity(Intent(this, ChatDetailActivity::class.java).apply {
                putExtra("chat_id", chatId)
                putExtra("chat_name", chatName)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            finish()
        }

        btnStats.setOnClickListener { soon("Подробная статистика") }

        btnInvite.setOnClickListener {
            startActivity(Intent(this, AddMembersActivity::class.java).apply {
                putExtra(AddMembersActivity.EXTRA_CHAT_ID, chatId)
            })
        }

        btnShare.setOnClickListener {
            val username = chat?.username?.takeIf { it.isNotBlank() }?.removePrefix("@")
            val text = if (username != null) "Канал «$chatName»: @$username" else "Канал «$chatName» в Vibic"
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }, "Поделиться"
            ))
        }

        rowAdmins.setOnClickListener {
            startActivity(Intent(this, ChannelAdminsActivity::class.java).apply {
                putExtra(ChannelAdminsActivity.EXTRA_CHAT_ID, chatId)
            })
        }
        rowSubscribers.setOnClickListener { soon("Список подписчиков") }
        rowType.setOnClickListener { soon("Смена типа канала") }
        rowInviteLinks.setOnClickListener { soon("Пригласительные ссылки") }
        rowComments.setOnClickListener { soon("Комментарии и реакции") }
        rowBlacklist.setOnClickListener { soon("Чёрный список") }
        rowLog.setOnClickListener { soon("Журнал действий") }

        rowDanger.setOnClickListener {
            if (role == ChannelRole.OWNER) confirmDelete() else confirmLeave()
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this, R.style.VibicAlertDialog)
            .setTitle("Удалить канал")
            .setMessage("Канал «$chatName» и все посты будут удалены для всех подписчиков. Это нельзя отменить.")
            .setPositiveButton("Удалить") { _, _ ->
                runAndExit("Канал удалён") { ApiClient.apiService.deleteChat(chatId).isSuccessful }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun confirmLeave() {
        AlertDialog.Builder(this, R.style.VibicAlertDialog)
            .setTitle("Покинуть канал")
            .setMessage("Вы перестанете быть администратором «$chatName».")
            .setPositiveButton("Покинуть") { _, _ ->
                runAndExit("Вы покинули канал") { ApiClient.apiService.leaveChat(chatId).isSuccessful }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    /** Выполняет запрос и при успехе возвращает на список чатов. */
    private fun runAndExit(successText: String, request: suspend () -> Boolean) {
        lifecycleScope.launch {
            val ok = try { request() } catch (_: Exception) { false }
            if (!ok) {
                Toast.makeText(this@ChannelProfileActivity, "Не получилось, попробуйте ещё раз", Toast.LENGTH_SHORT).show()
                return@launch
            }
            Toast.makeText(this@ChannelProfileActivity, successText, Toast.LENGTH_SHORT).show()
            startActivity(Intent(this@ChannelProfileActivity, MainActivityApp::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
        }
    }

    private fun soon(what: String) =
        Toast.makeText(this, "$what — в разработке", Toast.LENGTH_SHORT).show()

    // ── Форматирование ───────────────────────────────────────────────────────

    private fun View.visibleIf(show: Boolean) {
        visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun subscribersWord(n: Int): String {
        val mod100 = n % 100
        val mod10 = n % 10
        return when {
            mod100 in 11..14 -> "подписчиков"
            mod10 == 1 -> "подписчик"
            mod10 in 2..4 -> "подписчика"
            else -> "подписчиков"
        }
    }

    private fun formatCount(n: Int): String = when {
        n >= 1_000_000 -> "${oneDecimal(n / 1_000_000.0)}M"
        n >= 10_000 -> "${n / 1000}K"
        n >= 1_000 -> "${oneDecimal(n / 1000.0)}K"
        else -> n.toString()
    }

    /** 4.0 → «4», 4.25 → «4,3». */
    private fun oneDecimal(v: Double): String =
        String.format(Locale("ru"), "%.1f", v).removeSuffix(",0")

    /** Текст прироста со знаком и признак «не в минус». */
    private fun signed(v: Double, suffix: String): Pair<String, Boolean> {
        val sign = if (v >= 0) "+" else "−"
        return "$sign${oneDecimal(kotlin.math.abs(v))}$suffix" to (v >= 0)
    }
}
