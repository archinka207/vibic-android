package com.example.vibic

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
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
import com.example.vibic.channel.ADMIN_RIGHTS_INFO
import com.example.vibic.channel.AdminRights
import com.example.vibic.channel.ChannelAdmin
import com.example.vibic.channel.ChannelAdminPolicy
import com.example.vibic.channel.ChannelAdmins
import com.example.vibic.channel.ChannelRole
import com.example.vibic.channel.ChannelStaff
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.launch

/**
 * Администраторы канала: список, назначение, права, снятие и передача владения.
 * Работает через [ChannelAdmins.repository] (пока заглушка в памяти).
 *
 * Extras: chat_id (обязателен).
 */
class ChannelAdminsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CHAT_ID = "chat_id"
    }

    /** Строка списка: владелец или админ. */
    private data class Row(val userId: String, val name: String, val avatarUrl: String?, val admin: ChannelAdmin?)

    private val repo get() = ChannelAdmins.repository
    private val me get() = ChannelAdmins.myUserId()

    private lateinit var rowAddAdmin: View
    private lateinit var progressBar: View
    private val rows = mutableListOf<Row>()
    private val adapter = AdminsAdapter()

    private var chatId = ""
    private var staff: ChannelStaff? = null
    private var ownerName = "Владелец"
    private var ownerAvatar: String? = null

    /** Экран прав вернул RESULT_OK — что-то поменялось, обновляем список. */
    private val rightsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { if (it.resultCode == RESULT_OK) refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_channel_admins)

        chatId = intent.getStringExtra(EXTRA_CHAT_ID).orEmpty()
        if (chatId.isBlank()) {
            Toast.makeText(this, "Ошибка: канал не передан", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        rowAddAdmin = findViewById(R.id.rowAddAdmin)
        progressBar = findViewById(R.id.progressBar)
        findViewById<RecyclerView>(R.id.adminsRecycler).apply {
            layoutManager = LinearLayoutManager(this@ChannelAdminsActivity)
            adapter = this@ChannelAdminsActivity.adapter
        }
        rowAddAdmin.setOnClickListener { pickNewAdmin() }

        load()
    }

    private fun load() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val s = ChannelAdmins.loadStaff(chatId)
            // Имя и аватар владельца — из участников канала
            ChannelAdmins.loadMembers(chatId)?.firstOrNull { it.userId == s?.ownerId }?.let {
                ownerName = it.name
                ownerAvatar = it.avatarUrl
            }
            progressBar.visibility = View.GONE
            if (s == null) {
                toast("Не удалось загрузить администраторов")
                return@launch
            }
            show(s)
        }
    }

    private fun show(s: ChannelStaff) {
        staff = s
        rows.clear()
        rows += Row(s.ownerId, ownerName, ownerAvatar, admin = null)
        rows += s.admins.sortedBy { it.promotedAt }.map { Row(it.userId, it.name, it.avatarUrl, it) }
        adapter.notifyDataSetChanged()
        rowAddAdmin.visibility = if (ChannelAdminPolicy.canManageAdmins(s, me)) View.VISIBLE else View.GONE
    }

    /** После изменения берём свежее состояние из репозитория. */
    private fun refresh() {
        lifecycleScope.launch { repo.getAdmins(chatId).getOrNull()?.let(::show) }
    }

    // ── Назначение ───────────────────────────────────────────────────────────

    private fun pickNewAdmin() {
        val s = staff ?: return
        lifecycleScope.launch {
            val members = ChannelAdmins.loadMembers(chatId)
            if (members == null) { toast("Нет соединения"); return@launch }
            val candidates = members.filter { m ->
                m.userId != s.ownerId && s.adminOrNull(m.userId) == null && m.userId != me
            }
            if (candidates.isEmpty()) { toast("Нет подписчиков, которых можно назначить"); return@launch }

            AlertDialog.Builder(this@ChannelAdminsActivity, R.style.VibicAlertDialog)
                .setTitle("Кого назначить?")
                .setItems(candidates.map { it.name }.toTypedArray()) { _, i ->
                    val target = candidates[i]
                    openRights(target.userId, target.name, target.avatarUrl)
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    // ── Действия с админом ───────────────────────────────────────────────────

    private fun openRights(userId: String, name: String, avatarUrl: String?) {
        rightsLauncher.launch(AdminRightsActivity.intent(this, chatId, userId, name, avatarUrl))
    }

    /** Открыть права можно, если я могу их менять или снять этого админа. */
    private fun canOpenRights(s: ChannelStaff, admin: ChannelAdmin): Boolean =
        ChannelAdminPolicy.checkUpdateRights(s, me, admin.userId, AdminRights.NONE, "") == null ||
            ChannelAdminPolicy.canDemote(s, me, admin.userId)

    private fun canTransferTo(s: ChannelStaff, admin: ChannelAdmin): Boolean =
        ChannelAdminPolicy.checkTransfer(s, me, admin.userId) == null

    private fun confirmTransfer(admin: ChannelAdmin) {
        AlertDialog.Builder(this, R.style.VibicAlertDialog)
            .setTitle("Передать владение")
            .setMessage("${admin.name} станет владельцем канала. Вы останетесь администратором со всеми правами. Вернуть канал сможет только новый владелец.")
            .setPositiveButton("Продолжить") { _, _ -> askPasswordAndTransfer(admin) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun askPasswordAndTransfer(admin: ChannelAdmin) {
        val input = dialogEditText("Пароль").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this, R.style.VibicAlertDialog)
            .setTitle("Введите пароль")
            .setMessage("Пароль двухэтапной проверки")
            .setView(padded(input))
            .setPositiveButton("Передать") { _, _ ->
                val password = input.text.toString()
                act { repo.transferOwnership(chatId, admin.userId, password).map { "Канал передан: ${admin.name}" } }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    /** Выполняет действие репозитория, показывает итог и обновляет список. */
    private fun act(block: suspend () -> Result<String>) {
        lifecycleScope.launch {
            block()
                .onSuccess { toast(it) }
                .onFailure { toast(it.message ?: "Не получилось") }
            refresh()
        }
    }

    // ── Вспомогательное ──────────────────────────────────────────────────────

    private fun dialogEditText(hintText: String) = EditText(this).apply {
        hint = hintText
        isSingleLine = true
        setTextColor(MaterialColors.getColor(this, R.attr.vibicTextPrimary))
        setHintTextColor(MaterialColors.getColor(this, R.attr.vibicTextSecondary))
    }

    private fun padded(view: View): View {
        val pad = (20 * resources.displayMetrics.density).toInt()
        return FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(view)
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun subtitle(row: Row): String {
        val admin = row.admin ?: return "Все права"
        val flags = admin.rights.toFlags()
        val rights = ADMIN_RIGHTS_INFO.filterIndexed { i, _ -> flags[i] }.map { it.short }
            .joinToString(", ").ifEmpty { "без прав" }
        return if (admin.customTitle.isBlank()) rights else "${admin.customTitle} · $rights"
    }

    // ── Список ───────────────────────────────────────────────────────────────

    private inner class AdminsAdapter : RecyclerView.Adapter<AdminsAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val avatarImage: ImageView = v.findViewById(R.id.adminAvatarImage)
            val avatarText: TextView = v.findViewById(R.id.adminAvatarText)
            val name: TextView = v.findViewById(R.id.adminName)
            val ownerBadge: TextView = v.findViewById(R.id.adminOwnerBadge)
            val label: TextView = v.findViewById(R.id.adminRoleLabel)
            val chevron: ImageView = v.findViewById(R.id.adminChevron)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_group_admin, parent, false))

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val row = rows[position]
            h.name.text = if (row.userId == me) "${row.name} (вы)" else row.name
            h.ownerBadge.visibility = if (row.admin == null) View.VISIBLE else View.GONE
            h.label.text = subtitle(row)

            if (!row.avatarUrl.isNullOrBlank()) {
                h.avatarImage.visibility = View.VISIBLE
                h.avatarText.visibility = View.GONE
                Glide.with(h.itemView).load(row.avatarUrl).circleCrop().into(h.avatarImage)
            } else {
                h.avatarImage.visibility = View.GONE
                h.avatarText.visibility = View.VISIBLE
                h.avatarText.text = row.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            }

            val s = staff
            val admin = row.admin
            val clickable = s != null && admin != null && canOpenRights(s, admin)
            h.chevron.visibility = if (clickable) View.VISIBLE else View.INVISIBLE
            h.itemView.isClickable = clickable
            h.itemView.setOnClickListener {
                if (clickable && admin != null) openRights(admin.userId, admin.name, admin.avatarUrl)
            }
            // Передать канал — долгим нажатием, только владельцу
            val transferable = s != null && admin != null && canTransferTo(s, admin)
            h.itemView.isLongClickable = transferable
            h.itemView.setOnLongClickListener {
                if (transferable && admin != null) confirmTransfer(admin)
                transferable
            }
        }
    }
}
