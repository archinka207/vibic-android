package com.example.vibic

data class CreateChatRequest(
    val type: String,
    val name: String?,
    val user_ids: List<String>
)
// Личный чат: бэку нужен ТОЛЬКО id собеседника
data class CreatePrivateChatRequest(
    val target_user_id: String
)

// Группа: имя + список участников (+ необязательное описание)
data class CreateGroupChatRequest(
    val name: String,
    val user_ids: List<String>,
    val description: String? = null
)

// Канал: только имя (+ необязательное описание), без участников
data class CreateChannelRequest(
    val name: String,
    val description: String? = null
)