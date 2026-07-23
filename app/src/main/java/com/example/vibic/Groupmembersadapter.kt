package com.example.vibic

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

/**
 * Простая модель участника группы для UI.
 * Маппится из UserResponse / любого DTO, который вернёт бэкенд.
 */
data class GroupMember(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    /** "owner" | "admin" | "member" */
    val role: String = "member",
    /** Текст статуса: "в сети", "был(а) 5 мин назад" и т.п. */
    val statusText: String = "",
    val isOnline: Boolean = false
)

class GroupMembersAdapter(
    private val onMemberClick: (GroupMember) -> Unit = {}
) : RecyclerView.Adapter<GroupMembersAdapter.MemberViewHolder>() {

    private var members: List<GroupMember> = emptyList()

    fun submitList(newMembers: List<GroupMember>) {
        members = newMembers
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group_member, parent, false)
        return MemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val member = members[position]
        holder.bind(member)
        holder.itemView.setOnClickListener { onMemberClick(member) }
    }

    override fun getItemCount(): Int = members.size

    inner class MemberViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatarImage: ImageView = itemView.findViewById(R.id.memberAvatarImage)
        private val avatarText: TextView = itemView.findViewById(R.id.memberAvatarText)
        private val nameText: TextView = itemView.findViewById(R.id.memberName)
        private val roleBadge: TextView = itemView.findViewById(R.id.memberRoleBadge)
        private val statusText: TextView = itemView.findViewById(R.id.memberStatus)

        fun bind(member: GroupMember) {
            val displayName = member.name.ifBlank { "Пользователь" }
            nameText.text = displayName

            // Avatar
            if (!member.avatarUrl.isNullOrBlank()) {
                avatarImage.visibility = View.VISIBLE
                avatarText.visibility = View.GONE
                Glide.with(itemView).load(member.avatarUrl).circleCrop().into(avatarImage)
            } else {
                avatarImage.visibility = View.GONE
                avatarText.visibility = View.VISIBLE
                avatarText.text = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            }

            // Role badge
            when (member.role.lowercase()) {
                "owner" -> {
                    roleBadge.visibility = View.VISIBLE
                    roleBadge.text = "владелец"
                    roleBadge.setBackgroundResource(R.drawable.bg_role_badge_owner)
                    roleBadge.setTextColor(Color.parseColor("#E8A33D"))
                }
                "admin" -> {
                    roleBadge.visibility = View.VISIBLE
                    roleBadge.text = "админ"
                    roleBadge.setBackgroundResource(R.drawable.bg_role_badge_admin)
                    roleBadge.setTextColor(Color.parseColor("#B366FF"))
                }
                else -> roleBadge.visibility = View.GONE
            }

            // Status
            val status = when {
                member.statusText.isNotBlank() -> member.statusText
                member.isOnline -> "в сети"
                else -> "был(а) недавно"
            }
            statusText.text = status
            statusText.setTextColor(
                if (member.isOnline || status == "в сети") Color.parseColor("#B366FF")
                else Color.parseColor("#888888")
            )
        }
    }
}