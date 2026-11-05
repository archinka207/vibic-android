package com.example.vibic.channel

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Заглушка в памяти, пока на сервере нет эндпоинтов. Проверяет те же правила,
 * что [ChannelAdminPolicy]. Данные живут до перезапуска приложения.
 */
class FakeChannelAdminRepository(
    private val currentUserId: () -> String,
    /** Пароль, который принимает [transferOwnership]. */
    private val ownerPassword: String = "1234",
    private val clock: () -> Long = System::currentTimeMillis,
) : ChannelAdminRepository {

    /** Имя и аватар пользователя — на сервере их подставит бэк. */
    data class UserBrief(val name: String, val avatarUrl: String? = null)

    private val mutex = Mutex()
    private val channels = mutableMapOf<String, ChannelStaff>()
    private val users = mutableMapOf<String, UserBrief>()

    /** Завести канал, если заглушка его ещё не знает. Уже известный канал не перезаписывается. */
    suspend fun seedChannel(channelId: String, ownerId: String, admins: List<ChannelAdmin> = emptyList()) {
        mutex.withLock { channels.getOrPut(channelId) { ChannelStaff(channelId, ownerId, admins) } }
    }

    suspend fun seedUser(userId: String, name: String, avatarUrl: String? = null) {
        mutex.withLock { users[userId] = UserBrief(name, avatarUrl) }
    }

    override suspend fun getAdmins(channelId: String): Result<ChannelStaff> = mutex.withLock {
        channels[channelId]?.let { Result.success(it) }
            ?: Result.failure(ChannelAdminError.ChannelNotFound)
    }

    override suspend fun promoteToAdmin(
        channelId: String,
        userId: String,
        rights: AdminRights,
        customTitle: String,
    ): Result<ChannelAdmin> = edit(channelId) { staff, me ->
        ChannelAdminPolicy.checkPromote(staff, me, userId, rights, customTitle)?.let { throw it }
        val user = users[userId] ?: UserBrief(name = userId)
        val admin = ChannelAdmin(
            userId = userId,
            name = user.name,
            avatarUrl = user.avatarUrl,
            customTitle = customTitle,
            rights = rights,
            promotedBy = me,
            promotedAt = clock(),
        )
        staff.copy(admins = staff.admins + admin) to admin
    }

    override suspend fun updateRights(
        channelId: String,
        userId: String,
        rights: AdminRights,
        customTitle: String?,
    ): Result<ChannelAdmin> = edit(channelId) { staff, me ->
        val title = customTitle ?: staff.adminOrNull(userId)?.customTitle.orEmpty()
        ChannelAdminPolicy.checkUpdateRights(staff, me, userId, rights, title)?.let { throw it }
        val updated = staff.adminOrNull(userId)!!.copy(rights = rights, customTitle = title)
        staff.copy(admins = staff.admins.map { if (it.userId == userId) updated else it }) to updated
    }

    override suspend fun demote(channelId: String, userId: String): Result<Unit> = edit(channelId) { staff, me ->
        ChannelAdminPolicy.checkDemote(staff, me, userId)?.let { throw it }
        staff.copy(admins = staff.admins.filterNot { it.userId == userId }) to Unit
    }

    override suspend fun transferOwnership(
        channelId: String,
        newOwnerId: String,
        password: String,
    ): Result<ChannelStaff> = edit(channelId) { staff, me ->
        ChannelAdminPolicy.checkTransfer(staff, me, newOwnerId)?.let { throw it }
        if (password != ownerPassword) throw ChannelAdminError.WrongPassword
        val oldOwner = users[me] ?: UserBrief(name = me)
        val exOwnerAsAdmin = ChannelAdmin(
            userId = me,
            name = oldOwner.name,
            avatarUrl = oldOwner.avatarUrl,
            rights = AdminRights.ALL,
            promotedBy = newOwnerId,
            promotedAt = clock(),
        )
        val next = staff.copy(
            ownerId = newOwnerId,
            admins = staff.admins.filterNot { it.userId == newOwnerId } + exOwnerAsAdmin,
        )
        next to next
    }

    /** Применяет изменение атомарно; при [ChannelAdminError] канал не меняется. */
    private suspend fun <T> edit(
        channelId: String,
        block: (staff: ChannelStaff, me: String) -> Pair<ChannelStaff, T>,
    ): Result<T> = mutex.withLock {
        val staff = channels[channelId] ?: return Result.failure(ChannelAdminError.ChannelNotFound)
        try {
            val (next, value) = block(staff, currentUserId())
            channels[channelId] = next
            Result.success(value)
        } catch (e: ChannelAdminError) {
            Result.failure(e)
        }
    }
}
