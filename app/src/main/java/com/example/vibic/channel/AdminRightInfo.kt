package com.example.vibic.channel

import androidx.annotation.DrawableRes
import com.example.vibic.R

/** Как право показывается в интерфейсе. */
data class AdminRightInfo(
    val title: String,
    /** Серая строка под названием, может отсутствовать. */
    val description: String?,
    /** Коротко — для строки под именем админа в списке. */
    val short: String,
    @DrawableRes val icon: Int,
)

/** Подписи прав в порядке [AdminRights.toFlags]. */
val ADMIN_RIGHTS_INFO = listOf(
    AdminRightInfo("Изменять профиль канала", "Название, аватар, описание", "профиль", R.drawable.ic_edit_top),
    AdminRightInfo("Публиковать посты", null, "посты", R.drawable.ic_send),
    AdminRightInfo("Редактировать чужие посты", null, "правка постов", R.drawable.ic_chat),
    AdminRightInfo("Удалять чужие посты", null, "удаление постов", R.drawable.ic_trash),
    AdminRightInfo("Приглашать подписчиков", "Создавать пригласительные ссылки", "приглашения", R.drawable.ic_chat_add),
    AdminRightInfo("Блокировать пользователей", null, "блокировки", R.drawable.ic_block),
    AdminRightInfo("Управлять трансляциями", null, "трансляции", R.drawable.ic_camera),
    AdminRightInfo("Видеть статистику", null, "статистика", R.drawable.ic_trending),
    AdminRightInfo("Добавлять администраторов", "Сможет назначать других админов", "назначение админов", R.drawable.ic_shield),
)
