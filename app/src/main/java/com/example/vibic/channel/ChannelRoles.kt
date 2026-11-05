package com.example.vibic.channel

/** Роль пользователя в канале. */
enum class ChannelRole { OWNER, ADMIN, SUBSCRIBER }

/** Права администратора канала. У владельца всегда [ALL]. */
data class AdminRights(
    val changeProfile: Boolean = false,
    val postMessages: Boolean = false,
    val editOthersPosts: Boolean = false,
    val deleteOthersPosts: Boolean = false,
    val inviteUsers: Boolean = false,
    val banUsers: Boolean = false,
    val manageLive: Boolean = false,
    val viewStats: Boolean = false,
    val addAdmins: Boolean = false,
) {
    /** Права списком в порядке полей — для экранов с галочками. */
    fun toFlags(): List<Boolean> = listOf(
        changeProfile, postMessages, editOthersPosts, deleteOthersPosts,
        inviteUsers, banUsers, manageLive, viewStats, addAdmins,
    )

    /** true, если каждое включённое здесь право есть и в [other]. */
    fun isSubsetOf(other: AdminRights): Boolean =
        toFlags().zip(other.toFlags()).all { (mine, theirs) -> !mine || theirs }

    /** Только права, которые есть и здесь, и в [other]. */
    infix fun intersect(other: AdminRights): AdminRights =
        fromFlags(toFlags().zip(other.toFlags()).map { (a, b) -> a && b })

    companion object {
        /** Обратно к [toFlags]: список из 9 значений в том же порядке. */
        fun fromFlags(f: List<Boolean>): AdminRights {
            require(f.size == 9) { "Нужно 9 прав, пришло ${f.size}" }
            return AdminRights(f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8])
        }

        val NONE = AdminRights()

        val ALL = AdminRights(
            changeProfile = true, postMessages = true, editOthersPosts = true,
            deleteOthersPosts = true, inviteUsers = true, banUsers = true,
            manageLive = true, viewStats = true, addAdmins = true,
        )

        /** Что получает новый админ, если права не выбраны вручную. */
        val DEFAULT_NEW_ADMIN = AdminRights(
            postMessages = true, inviteUsers = true, viewStats = true,
        )
    }
}

data class ChannelAdmin(
    val userId: String,
    val name: String,
    val avatarUrl: String?,
    /** Подпись вместо «админ», например «редактор». Пустая — без подписи. */
    val customTitle: String = "",
    val rights: AdminRights = AdminRights.DEFAULT_NEW_ADMIN,
    /** Кто назначил. Снять админа может он или владелец. */
    val promotedBy: String,
    /** Время назначения, epoch millis. */
    val promotedAt: Long,
)

/**
 * Руководство канала: один владелец и список админов.
 * Владелец не входит в [admins] — его права всегда [AdminRights.ALL].
 */
data class ChannelStaff(
    val channelId: String,
    val ownerId: String,
    val admins: List<ChannelAdmin>,
) {
    fun myRole(currentUserId: String): ChannelRole = when {
        currentUserId == ownerId -> ChannelRole.OWNER
        admins.any { it.userId == currentUserId } -> ChannelRole.ADMIN
        else -> ChannelRole.SUBSCRIBER
    }

    fun adminOrNull(userId: String): ChannelAdmin? = admins.firstOrNull { it.userId == userId }

    /** Права пользователя: у владельца все, у подписчика никаких. */
    fun rightsOf(userId: String): AdminRights = when (userId) {
        ownerId -> AdminRights.ALL
        else -> adminOrNull(userId)?.rights ?: AdminRights.NONE
    }
}

/** Почему действие с админами запрещено. */
sealed class ChannelAdminError(message: String) : Exception(message) {
    object ChannelNotFound : ChannelAdminError("Канал не найден")
    object NotAllowed : ChannelAdminError("Недостаточно прав")
    object OwnerRightsLocked : ChannelAdminError("Права владельца нельзя изменить")
    object AlreadyAdmin : ChannelAdminError("Пользователь уже администратор")
    object NotAdmin : ChannelAdminError("Пользователь не администратор")
    object RightsExceedOwn : ChannelAdminError("Нельзя выдать права, которых нет у вас")
    object TitleTooLong : ChannelAdminError("Подпись длиннее ${ChannelAdminPolicy.MAX_TITLE_LENGTH} символов")
    object WrongPassword : ChannelAdminError("Неверный пароль")
}

/**
 * Правила работы с админами. Чистые функции без сети — их использует fake-репозиторий,
 * а UI может по ним прятать недоступные кнопки. Сервер обязан проверять то же самое.
 */
object ChannelAdminPolicy {

    const val MAX_TITLE_LENGTH = 16

    /** Может ли [actorId] назначить нового админа с правами [rights]. */
    fun checkPromote(staff: ChannelStaff, actorId: String, targetId: String, rights: AdminRights, title: String): ChannelAdminError? {
        if (targetId == staff.ownerId) return ChannelAdminError.OwnerRightsLocked
        if (staff.adminOrNull(targetId) != null) return ChannelAdminError.AlreadyAdmin
        return checkGrant(staff, actorId, rights, title)
    }

    /** Может ли [actorId] поменять права админа [targetId] на [rights]. */
    fun checkUpdateRights(staff: ChannelStaff, actorId: String, targetId: String, rights: AdminRights, title: String): ChannelAdminError? {
        if (targetId == staff.ownerId) return ChannelAdminError.OwnerRightsLocked
        val target = staff.adminOrNull(targetId) ?: return ChannelAdminError.NotAdmin
        if (actorId != staff.ownerId && target.promotedBy != actorId) return ChannelAdminError.NotAllowed
        // Права, которые у админа уже есть, можно оставить, даже если у меня их нет
        return checkGrant(staff, actorId, rights, title, alreadyHas = target.rights)
    }

    /** Снять админа может владелец или тот, кто его назначил. */
    fun checkDemote(staff: ChannelStaff, actorId: String, targetId: String): ChannelAdminError? {
        if (targetId == staff.ownerId) return ChannelAdminError.OwnerRightsLocked
        val target = staff.adminOrNull(targetId) ?: return ChannelAdminError.NotAdmin
        if (actorId != staff.ownerId && target.promotedBy != actorId) return ChannelAdminError.NotAllowed
        return null
    }

    /** Передать канал может только владелец и только действующему админу. */
    fun checkTransfer(staff: ChannelStaff, actorId: String, newOwnerId: String): ChannelAdminError? {
        if (actorId != staff.ownerId) return ChannelAdminError.NotAllowed
        if (staff.adminOrNull(newOwnerId) == null) return ChannelAdminError.NotAdmin
        return null
    }

    fun canManageAdmins(staff: ChannelStaff, actorId: String): Boolean =
        staff.rightsOf(actorId).addAdmins

    fun canDemote(staff: ChannelStaff, actorId: String, targetId: String): Boolean =
        checkDemote(staff, actorId, targetId) == null

    /** Владелец выдаёт что угодно, админ с addAdmins — только то, что есть у него. */
    private fun checkGrant(
        staff: ChannelStaff,
        actorId: String,
        rights: AdminRights,
        title: String,
        alreadyHas: AdminRights = AdminRights.NONE,
    ): ChannelAdminError? {
        val own = staff.rightsOf(actorId)
        if (!own.addAdmins) return ChannelAdminError.NotAllowed
        val allowed = AdminRights.fromFlags(own.toFlags().zip(alreadyHas.toFlags()).map { (a, b) -> a || b })
        if (!rights.isSubsetOf(allowed)) return ChannelAdminError.RightsExceedOwn
        if (title.length > MAX_TITLE_LENGTH) return ChannelAdminError.TitleTooLong
        return null
    }
}
