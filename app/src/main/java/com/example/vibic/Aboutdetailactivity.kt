package com.example.vibic

import android.graphics.Color
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class AboutDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SECTION = "section"
        const val SECTION_WHATS_NEW = "whats_new"
        const val SECTION_LICENSES  = "licenses"
        const val SECTION_TERMS     = "terms"
        const val SECTION_PRIVACY   = "privacy"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_about_detail)

        findViewById<FrameLayout>(R.id.backButton).setOnClickListener { finish() }

        val titleText   = findViewById<TextView>(R.id.titleText)
        val contentText = findViewById<TextView>(R.id.contentText)

        val section = intent.getStringExtra(EXTRA_SECTION) ?: SECTION_WHATS_NEW

        val (title, content) = when (section) {
            SECTION_WHATS_NEW -> "Что нового" to whatsNewText()
            SECTION_LICENSES  -> "Лицензии"   to licensesText()
            SECTION_TERMS     -> "Условия использования"        to termsText()
            SECTION_PRIVACY   -> "Политика конфиденциальности"  to privacyText()
            else              -> "О приложении" to ""
        }

        titleText.text   = title
        contentText.text = content
    }

    private fun whatsNewText() = """
        Версия 1.0.0

        • Первая публичная сборка Vibic
        • Личные и групповые чаты
        • Закрытые вайбы (каналы)
        • Двухфакторная защита
        • QR-коды профиля
        • Поддержка медиа: фото, видео, голосовые сообщения
        • Темная тема и плавные анимации интерфейса

        Спасибо, что пользуетесь Vibic. Если нашли баг или есть идея — напишите в раздел «Помощь».
    """.trimIndent()

    private fun licensesText() = """
        Vibic использует следующие open-source библиотеки:

        Kotlin
        © JetBrains. Лицензия Apache 2.0.

        AndroidX
        © Google. Лицензия Apache 2.0.

        Material Components for Android
        © Google. Лицензия Apache 2.0.

        Glide
        © Bump Technologies. Лицензия BSD, MIT, Apache 2.0.

        Retrofit
        © Square, Inc. Лицензия Apache 2.0.

        OkHttp
        © Square, Inc. Лицензия Apache 2.0.

        Gson
        © Google. Лицензия Apache 2.0.

        Kotlin Coroutines
        © JetBrains. Лицензия Apache 2.0.

        ───────────────────────

        Полные тексты лицензий доступны на сайтах правообладателей.
    """.trimIndent()

    private fun termsText() = """
        Условия использования Vibic

        1. Общие положения
        Используя приложение Vibic, вы соглашаетесь с настоящими Условиями. Если вы не согласны с какими-либо пунктами — пожалуйста, не используйте приложение.

        2. Регистрация и аккаунт
        Для использования Vibic требуется номер телефона. Вы обязуетесь предоставлять достоверные данные и несете ответственность за сохранность доступа к своему аккаунту.

        3. Допустимое использование
        Запрещено использовать Vibic для рассылки спама, оскорблений, угроз, распространения вредоносного контента, нарушения прав третьих лиц или законодательства.

        4. Контент пользователей
        Вы сохраняете права на отправляемый контент, но предоставляете Vibic неисключительную лицензию на его передачу другим пользователям в рамках работы сервиса.

        5. Ответственность
        Vibic предоставляется «как есть». Мы стремимся к стабильной работе, но не гарантируем отсутствие сбоев. Мы не несем ответственности за действия пользователей в чатах.

        6. Изменения условий
        Мы можем вносить изменения в эти Условия. О существенных изменениях вы будете уведомлены в приложении.

        7. Контакты
        По вопросам, связанным с этими Условиями, обращайтесь через раздел «Помощь» в настройках профиля.

        Дата вступления в силу: 1 января 2026 года.
    """.trimIndent()

    private fun privacyText() = """
        Политика конфиденциальности Vibic

        1. Какие данные мы собираем
        • Номер телефона — для регистрации и идентификации аккаунта.
        • Имя, аватар, статус — отображаются вашим контактам.
        • Сообщения — передаются между участниками чата.
        • Технические данные: модель устройства, версия ОС, IP-адрес сессии — для безопасности.

        2. Как мы используем данные
        Данные используются исключительно для работы сервиса: доставки сообщений, синхронизации между устройствами, защиты от взломов и спама.

        3. Передача третьим лицам
        Мы не продаём ваши данные. Передача возможна только по требованию государственных органов в рамках закона.

        4. Хранение данных
        Сообщения хранятся на серверах Vibic пока вы не удалите их или аккаунт. Резервные копии удаляются в течение 30 дней после удаления исходных данных.

        5. Шифрование
        Передача данных между устройством и сервером защищена TLS. Голосовые и видеосообщения шифруются при передаче.

        6. Ваши права
        Вы можете в любой момент:
        • Изменить данные профиля
        • Удалить отдельные сообщения и чаты
        • Удалить аккаунт целиком — все ваши данные будут стерты

        7. Контакты
        По вопросам обработки данных пишите в раздел «Помощь».

        Дата вступления в силу: 1 января 2026 года.
    """.trimIndent()
}