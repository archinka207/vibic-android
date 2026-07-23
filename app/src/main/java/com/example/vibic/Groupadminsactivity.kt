package com.example.vibic

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.vibic.network.ApiClient
import com.example.vibic.network.TokenManager
import com.example.vibic.network.UpdateRoleRequest
import kotlinx.coroutines.launch

/**
 * Экран "Администраторы".
 * Показывает участников с ролью owner / admin / subscriber.
 *
 * Управление ролями на бэкенде доступно ТОЛЬКО владельцу (owner):
 *   POST   api/chats/{chatId}/participants/{userId}/role  {role: member|admin|subscriber}
 *   DELETE api/chats/{chatId}/participants/{userId}        (убрать из группы)
 * Роли owner назначить нельзя; роль самого owner менять нельзя.
 *
 * Extras:
 *   chat_id — id группы (обязателен)
 */
class GroupAdminsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CHAT_ID = "chat_id"
    }

    private lateinit var backButton: ImageView
    private lateinit var adminsRecycler: RecyclerView
    private lateinit var rowAddAdmin: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: AdminsAdapter

    private var chatId = ""
    private var myUserId = ""
    private var myRole = ""

    private val admins = mutableListOf<GroupMember>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        setContentView(R.layout.activity_group_admins)

        chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: ""
        myUserId = TokenManager.getUserId() ?: ""
        if (chatId.isBlank()) {
            Toast.makeText(this, "Ошибка: чат не передан", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        backButton = findViewById(R.id.backButton)
        adminsRecycler = findViewById(R.id.adminsRecycler)
        rowAddAdmin = findViewById(R.id.rowAddAdmin)
        progressBar = findViewById(R.id.progressBar)

        backButton.setOnClickListener { finish() }

        adapter = AdminsAdapter(admins) { member -> onAdminClick(member) }
        adminsRecycler.layoutManager = LinearLayoutManager(this)
        adminsRecycler.adapter = adapter

        rowAddAdmin.setOnClickListener {
            // Назначение нового админа — выбор из участников. Пока заглушка
            // (можно открыть AddMembersActivity-подобный экран выбора).
            Toast.makeText(this, "Выбор нового администратора — в разработке", Toast.LENGTH_SHORT).show()
        }

        loadAdmins()
    }

    private fun loadAdmins() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val resp = ApiClient.apiService.getParticipants(chatId, limit = 200, offset = 0)
                if (resp.isSuccessful) {
                    val list = resp.body() ?: emptyList()

                    myRole = list.firstOrNull { it.userId == myUserId }?.role?.lowercase() ?: ""

                    val mapped = list.map { p ->
                        val u = p.user
                        val fullName = listOfNotNull(
                            u?.firstName?.trim()?.takeIf { it.isNotEmpty() },
                            u?.lastName?.trim()?.takeIf { it.isNotEmpty() }
                        ).joinToString(" ").ifBlank { "Пользователь" }
                        GroupMember(
                            id = p.userId,
                            name = fullName,
                            avatarUrl = u?.avatarUrl,
                            role = p.role.lowercase(),
                            isOnline = u?.online ?: false
                        )
                    }

                    // Только owner / admin / subscriber, owner первым
                    val filtered = mapped
                        .filter { it.role == "owner" || it.role == "admin" || it.role == "subscriber" }
                        .sortedBy {
                            when (it.role) {
                                "owner" -> 0
                                "admin" -> 1
                                else -> 2
                            }
                        }

                    admins.clear()
                    admins.addAll(filtered)
                    adapter.notifyDataSetChanged()

                    // "Добавить администратора" имеет смысл только для owner
                    rowAddAdmin.visibility = if (myRole == "owner") View.VISIBLE else View.GONE
                } else {
                    Toast.makeText(this@GroupAdminsActivity, "Не удалось загрузить (${resp.code()})", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@GroupAdminsActivity, "Ошибка сети", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun onAdminClick(member: GroupMember) {
        // Менять роли может только owner; роль самого owner не трогаем
        if (myRole != "owner") return
        if (member.role == "owner") return
        if (member.id == myUserId) return

        val options = arrayOf("Все права (админ)", "Только сообщения", "Убрать из админов")
        AlertDialog.Builder(this)
            .setTitle(member.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> updateRole(member, "admin")
                    1 -> updateRole(member, "subscriber")
                    2 -> updateRole(member, "member") // снять «админство» — вернуть в обычные участники
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateRole(member: GroupMember, newRole: String) {
        lifecycleScope.launch {
            try {
                val resp = ApiClient.apiService.updateParticipantRole(
                    chatId, member.id, UpdateRoleRequest(role = newRole)
                )
                if (resp.isSuccessful) {
                    val msg = when (newRole) {
                        "admin" -> "Назначен администратором"
                        "subscriber" -> "Роль: только сообщения"
                        else -> "Убран из администраторов"
                    }
                    Toast.makeText(this@GroupAdminsActivity, msg, Toast.LENGTH_SHORT).show()
                    loadAdmins()
                } else {
                    val text = if (resp.code() == 403) "Недостаточно прав"
                    else "Не удалось изменить роль (${resp.code()})"
                    Toast.makeText(this@GroupAdminsActivity, text, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@GroupAdminsActivity, "Ошибка сети", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun roleLabel(role: String): String = when (role) {
        "owner" -> "Создатель группы"
        "admin" -> "Все права"
        "subscriber" -> "Только сообщения"
        else -> "Участник"
    }

    // ── Adapter ──────────────────────────────────────────────────────────────
    inner class AdminsAdapter(
        private val items: List<GroupMember>,
        private val onClick: (GroupMember) -> Unit
    ) : RecyclerView.Adapter<AdminsAdapter.VH>() {

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val avatarImage: ImageView = itemView.findViewById(R.id.adminAvatarImage)
            val avatarText: TextView = itemView.findViewById(R.id.adminAvatarText)
            val name: TextView = itemView.findViewById(R.id.adminName)
            val ownerBadge: TextView = itemView.findViewById(R.id.adminOwnerBadge)
            val roleLabel: TextView = itemView.findViewById(R.id.adminRoleLabel)
            val chevron: ImageView = itemView.findViewById(R.id.adminChevron)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_group_admin, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val m = items[position]
            holder.name.text = m.name
            holder.roleLabel.text = roleLabel(m.role)
            holder.ownerBadge.visibility = if (m.role == "owner") View.VISIBLE else View.GONE

            if (!m.avatarUrl.isNullOrBlank()) {
                holder.avatarImage.visibility = View.VISIBLE
                holder.avatarText.visibility = View.GONE
                Glide.with(holder.itemView).load(m.avatarUrl).circleCrop().into(holder.avatarImage)
            } else {
                holder.avatarImage.visibility = View.GONE
                holder.avatarText.visibility = View.VISIBLE
                holder.avatarText.text = m.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            }

            // Шеврон-стрелка кликабельна только если owner может менять роль
            val clickable = myRole == "owner" && m.role != "owner" && m.id != myUserId
            holder.chevron.visibility = if (clickable) View.VISIBLE else View.INVISIBLE
            holder.itemView.isClickable = clickable
            holder.itemView.setOnClickListener { if (clickable) onClick(m) }
        }
    }
}