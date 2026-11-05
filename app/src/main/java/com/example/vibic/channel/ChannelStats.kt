package com.example.vibic.channel

import kotlin.math.abs

/**
 * Статистика канала для мини-карточек профиля. Число подписчиков берётся из самого чата,
 * здесь только то, чего в чате нет. null — данных нет, карточка покажет «—».
 */
data class ChannelStats(
    /** Прирост подписчиков за 7 дней. */
    val subscribersGrowth: Int? = null,
    /** Средний охват поста за 7 дней. */
    val postReach: Int? = null,
    /** Изменение охвата к прошлой неделе, %. */
    val postReachGrowthPercent: Double? = null,
    /** Вовлечённость: реакции, комментарии и репосты / охват, %. */
    val erPercent: Double? = null,
    /** Изменение ER к прошлой неделе, процентные пункты. */
    val erGrowthPoints: Double? = null,
)

interface ChannelStatsRepository {
    suspend fun getStats(channelId: String): Result<ChannelStats>
}

/**
 * ДЕМО-ЦИФРЫ. Сервер статистику пока не отдаёт (эндпоинт — в docs/channel-admins-api.md).
 * Значения стабильны для одного канала, но к реальности отношения не имеют.
 * Перед релизом заменить на настоящий репозиторий.
 */
class FakeChannelStatsRepository : ChannelStatsRepository {
    override suspend fun getStats(channelId: String): Result<ChannelStats> {
        val seed = abs(channelId.hashCode())
        return Result.success(
            ChannelStats(
                subscribersGrowth = 3 + seed % 40,
                postReach = 120 + seed % 900,
                postReachGrowthPercent = (seed % 25).toDouble(),
                erPercent = 2.0 + (seed % 60) / 10.0,
                erGrowthPoints = (seed % 9) / 10.0,
            )
        )
    }
}
