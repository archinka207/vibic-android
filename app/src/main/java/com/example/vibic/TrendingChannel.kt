package com.example.vibic

import android.graphics.Color

data class TrendingChannel(
    val rank: Int,
    val name: String,
    val subscribers: String,   // напр. "212K"
    val growth: String,        // напр. "+9.8K"
    val verified: Boolean,
    val colorStart: Int,
    val colorEnd: Int
) {
    val letter: String get() = name.trim().take(1).uppercase()
}

// Временные данные-заглушки. Потом заменим на ответ бэка.
object TrendingMock {
    fun channels(): List<TrendingChannel> = listOf(
        TrendingChannel(1, "Дизайн как искусство", "248K", "+12K",  false, Color.parseColor("#8B5CF6"), Color.parseColor("#6D28D9")),
        TrendingChannel(2, "Криптошум",            "212K", "+9.8K", true,  Color.parseColor("#A78BFA"), Color.parseColor("#7C3AED")),
        TrendingChannel(3, "Кино по понедельникам","187K", "+8.1K", false, Color.parseColor("#60A5FA"), Color.parseColor("#3B82F6")),
        TrendingChannel(4, "Качалка",              "143K", "+6.7K", false, Color.parseColor("#2DD4BF"), Color.parseColor("#0D9488")),
        TrendingChannel(5, "Мемы 18+",             "139K", "+5.4K", false, Color.parseColor("#EC4899"), Color.parseColor("#BE185D")),
        TrendingChannel(6, "Тех новости",          "121K", "+4.9K", true,  Color.parseColor("#818CF8"), Color.parseColor("#4F46E5")),
        TrendingChannel(7, "Книжная полка",        "99K",  "+3.2K", false, Color.parseColor("#F472B6"), Color.parseColor("#DB2777")),
        TrendingChannel(8, "Кофе и код",           "87K",  "+2.8K", false, Color.parseColor("#22D3EE"), Color.parseColor("#0891B2")),
    )
}