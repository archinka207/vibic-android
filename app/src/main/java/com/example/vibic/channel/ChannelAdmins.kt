package com.example.vibic.channel

import com.example.vibic.network.ApiClient
import com.example.vibic.network.ParticipantDto
import com.example.vibic.network.TokenManager

/**
 * Общая точка доступа к админам каналов для экранов.
 *
 * Пока бэкенд не умеет права админов, работает [FakeChannelAdminRepository]. Чтобы заглушка
 * знала реальных владельца и админов, при первом обращении к каналу она заполняется
 * из GET api/chats/{chatId}/participants (роли owner / admin, права — по умолчанию).
 * Изменения в заглушке живут до перезапуска приложения и на сервер не уходят.
 */
object ChannelAdmins {

    /** Участник канала: имя и аватар для списков. */
    data class Member(val userId: String, val name: String, val avatarUrl: String?, val role: String)

    val repository: FakeChannelAdminRepository by lazy {
        FakeChannelAdminRepository(currentUserId = { myUserId() })
    }

    fun myUserId(): String = TokenManager.getUserId().orEmpty()

    /** Владелец и админы канала или null, если сервер недоступен. */
    suspend fun loadStaff(channelId: String): ChannelStaff? {
        repository.getAdmins(channelId).getOrNull()?.let { return it }

        val members = loadMembers(channelId) ?: return null
        val owner = members.firstOrNull { it.role == "owner" } ?: return null
        val admins = members.filter { it.role == "admin" }.map {
            ChannelAdmin(
                userId = it.userId,
                name = it.name,
                avatarUrl = it.avatarUrl,
                promotedBy = owner.userId,
                promotedAt = 0L,
            )
        }
        repository.seedChannel(channelId, owner.userId, admins)
        return repository.getAdmins(channelId).getOrNull()
    }

    /** Все участники канала с сервера или null при ошибке сети. */
    suspend fun loadMembers(channelId: String): List<Member>? {
        val list = try {
            val resp = ApiClient.apiService.getParticipants(channelId, limit = 200)
            if (resp.isSuccessful) resp.body() else null
        } catch (_: Exception) {
            null
        } ?: return null

        return list.map { it.toMember() }.onEach { repository.seedUser(it.userId, it.name, it.avatarUrl) }
    }

    private fun ParticipantDto.toMember(): Member {
        val name = listOfNotNull(
            user?.firstName?.trim()?.takeIf { it.isNotEmpty() },
            user?.lastName?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(" ").ifBlank { "Пользователь" }
        return Member(userId, name, user?.avatarUrl, role.lowercase())
    }
}
