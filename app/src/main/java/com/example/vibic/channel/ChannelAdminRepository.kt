package com.example.vibic.channel

/**
 * Управление админами канала. Текущий пользователь (кто выполняет действие)
 * определяется внутри реализации: у сервера — по токену, у fake — через конструктор.
 *
 * Ошибки приходят как [Result.failure] с [ChannelAdminError].
 * Эндпоинты для бэкенда: docs/channel-admins-api.md.
 */
interface ChannelAdminRepository {

    /** Владелец и список админов канала. */
    suspend fun getAdmins(channelId: String): Result<ChannelStaff>

    suspend fun promoteToAdmin(
        channelId: String,
        userId: String,
        rights: AdminRights = AdminRights.DEFAULT_NEW_ADMIN,
        customTitle: String = "",
    ): Result<ChannelAdmin>

    /** [customTitle] = null — оставить подпись как есть. */
    suspend fun updateRights(
        channelId: String,
        userId: String,
        rights: AdminRights,
        customTitle: String? = null,
    ): Result<ChannelAdmin>

    suspend fun demote(channelId: String, userId: String): Result<Unit>

    /**
     * Передать владение админу [newOwnerId]. Нужен пароль владельца.
     * Бывший владелец остаётся админом со всеми правами.
     */
    suspend fun transferOwnership(channelId: String, newOwnerId: String, password: String): Result<ChannelStaff>
}
