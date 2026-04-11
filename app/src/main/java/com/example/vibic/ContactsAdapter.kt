package com.example.vibic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ContactsAdapter(
    private val onItemClick: (UserResponse) -> Unit
) : RecyclerView.Adapter<ContactsAdapter.ContactViewHolder>() {

    private var users: List<UserResponse> = emptyList()

    fun submitList(newUsers: List<UserResponse>) {
        users = newUsers
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val user = users[position]
        holder.bind(user)

        holder.itemView.setOnClickListener {
            onItemClick(user)
        }
    }

    override fun getItemCount(): Int = users.size

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.contactName)
        private val phoneText: TextView = itemView.findViewById(R.id.contactPhone)
        private val avatarText: TextView = itemView.findViewById(R.id.avatarText)

        fun bind(user: UserResponse) {
            val fullName = buildFullName(user)
            val displayName = if (fullName.isNotBlank()) fullName else "Пользователь"

            nameText.text = displayName

            val phone = user.phone?.trim().orEmpty()
            if (phone.isNotEmpty()) {
                phoneText.visibility = View.VISIBLE
                phoneText.text = phone
            } else {
                phoneText.visibility = View.VISIBLE
                phoneText.text = "Нет номера"
            }

            avatarText.text = getAvatarLetter(displayName)
        }
    }

    private fun buildFullName(user: UserResponse): String {
        return listOfNotNull(
            user.first_name?.trim()?.takeIf { it.isNotEmpty() },
            user.last_name?.trim()?.takeIf { it.isNotEmpty() }
        ).joinToString(" ")
    }

    private fun getAvatarLetter(name: String): String {
        return name
            .trim()
            .firstOrNull()
            ?.uppercaseChar()
            ?.toString()
            ?: "?"
    }
}