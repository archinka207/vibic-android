package com.example.vibic

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Общий доступ к контактам телефонной книги.
 *
 * Используется и в [ContactsFragment] (полный список с поиском), и в
 * [MainFragment] (для секции «Контакты» под результатами поиска чатов).
 * В памяти держим один прочитанный список — повторные вызовы возвращают его,
 * пока приложение живо. Можно явно сбросить кэш через [invalidate].
 */
object ContactsRepository {

    @Volatile
    private var cached: List<UserResponse>? = null
    private val mutex = Mutex()

    /**
     * Возвращает контакты из телефонной книги. Если уже загружены — отдаёт
     * из кэша без обращения к ContentResolver. Безопасен для параллельных
     * вызовов: только один реальный запрос к БД, остальные ждут результата.
     *
     * Вызов из main thread безопасен — чтение делается на Dispatchers.IO.
     * Требует уже выданного разрешения READ_CONTACTS, иначе вернёт пустой список.
     */
    suspend fun getContacts(context: Context): List<UserResponse> {
        cached?.let { return it }
        return mutex.withLock {
            cached?.let { return it }
            val loaded = withContext(Dispatchers.IO) { readContactsFromPhone(context) }
            cached = loaded
            loaded
        }
    }

    /** Сбрасывает кэш — следующий [getContacts] перечитает из БД. */
    fun invalidate() {
        cached = null
    }
    /** Возвращает уже загруженные контакты без обращения к БД, либо null. */
    fun cachedOrNull(): List<UserResponse>? = cached

    private fun readContactsFromPhone(context: Context): List<UserResponse> {
        val result = mutableListOf<UserResponse>()
        val seenPhones = hashSetOf<String>()

        val cursor: Cursor? = try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
        } catch (_: SecurityException) {
            // Нет разрешения READ_CONTACTS — отдаём пустой список, не падаем.
            return emptyList()
        }

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val rawName = if (nameIndex >= 0) it.getString(nameIndex) else null
                val rawPhone = if (phoneIndex >= 0) it.getString(phoneIndex) else null

                val normalizedPhone = normalizePhone(rawPhone)

                if (normalizedPhone.isBlank()) continue
                if (!seenPhones.add(normalizedPhone)) continue

                val displayName = rawName?.trim().orEmpty()
                val firstName: String?
                val lastName: String?

                if (displayName.isBlank()) {
                    firstName = normalizedPhone
                    lastName = null
                } else {
                    val parts = displayName.split("\\s+".toRegex(), limit = 2)
                    firstName = parts.getOrNull(0)
                    lastName = parts.getOrNull(1)
                }

                result.add(
                    UserResponse(
                        id = "local_${UUID.randomUUID()}",
                        phone = normalizedPhone,
                        first_name = firstName,
                        last_name = lastName,
                        avatar_url = null,
                        status = null,
                        online = false
                    )
                )
            }
        }

        return result
    }

    private fun normalizePhone(phone: String?): String {
        if (phone.isNullOrBlank()) return ""

        val cleaned = phone
            .replace("\\s".toRegex(), "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")

        return if (cleaned.startsWith("+")) {
            "+" + cleaned.drop(1).replace("[^0-9]".toRegex(), "")
        } else {
            cleaned.replace("[^0-9]".toRegex(), "")
        }
    }
}