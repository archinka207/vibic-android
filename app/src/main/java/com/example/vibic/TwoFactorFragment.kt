package com.example.vibic

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment

/**
 * Экран «Двухфакторная защита». Пока полностью UI-заглушка:
 * - тогглы PIN-кода и биометрии сохраняются в SharedPreferences ("two_factor_prefs"),
 *   но реально вход в приложение не блокируют;
 * - строки "Резервный email" и "Коды восстановления" — статическое отображение;
 * - верхний баннер "Защита активна/не настроена" пересчитывается из состояния тогглов.
 *
 * Когда на бэке появятся реальные эндпоинты (setup-pin, set-backup-email и т.д.) —
 * подключим в saveAndUpdate() и в обработчиках кликов на строки восстановления.
 */
class TwoFactorFragment : Fragment() {

    private lateinit var btnBack: ImageView

    private lateinit var statusTitle: TextView
    private lateinit var statusSubtitle: TextView

    private lateinit var pinRow: LinearLayout
    private lateinit var pinSwitch: IosSwitch
    private lateinit var pinSubtitle: TextView

    private lateinit var biometricRow: LinearLayout
    private lateinit var biometricSwitch: IosSwitch

    private lateinit var backupEmailRow: LinearLayout
    private lateinit var backupEmailValue: TextView

    private lateinit var backupCodesRow: LinearLayout
    private lateinit var backupCodesSubtitle: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_two_factor, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        btnBack             = view.findViewById(R.id.btnBack)
        statusTitle         = view.findViewById(R.id.statusTitle)
        statusSubtitle      = view.findViewById(R.id.statusSubtitle)
        pinRow              = view.findViewById(R.id.pinRow)
        pinSwitch           = view.findViewById(R.id.pinSwitch)
        pinSubtitle         = view.findViewById(R.id.pinSubtitle)
        biometricRow        = view.findViewById(R.id.biometricRow)
        biometricSwitch     = view.findViewById(R.id.biometricSwitch)
        backupEmailRow      = view.findViewById(R.id.backupEmailRow)
        backupEmailValue    = view.findViewById(R.id.backupEmailValue)
        backupCodesRow      = view.findViewById(R.id.backupCodesRow)
        backupCodesSubtitle = view.findViewById(R.id.backupCodesSubtitle)

        btnBack.setOnClickListener { activity?.finish() }

        loadState()
        setupListeners()
        updateStatusBanner()
    }

    // ── Загрузка/сохранение ───────────────────────────────────────────────────

    private fun prefs() = requireContext()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadState() {
        val p = prefs()
        val pinEnabled       = p.getBoolean(KEY_PIN_ENABLED, false)
        val biometricEnabled = p.getBoolean(KEY_BIOMETRIC, false)
        val pinSetAt         = p.getLong(KEY_PIN_SET_AT, 0L)
        val backupEmail      = p.getString(KEY_BACKUP_EMAIL, null)
        val codesCount       = p.getInt(KEY_BACKUP_CODES_COUNT, 0)

        // Свитчи без срабатывания listener'а — выставляем сами.
        pinSwitch.setOnCheckedChangeListener(null)
        biometricSwitch.setOnCheckedChangeListener(null)
        pinSwitch.setChecked(pinEnabled, animate = false)
        biometricSwitch.setChecked(biometricEnabled && pinEnabled, animate = false)
        biometricSwitch.isEnabled = pinEnabled

        // Подзаголовок PIN
        pinSubtitle.text = when {
            !pinEnabled -> "Не задан"
            pinSetAt > 0 -> "6 цифр, обновлён ${daysAgoText(pinSetAt)}"
            else -> "6 цифр"
        }

        // Email — маскируем для UI, если задан.
        backupEmailValue.text = backupEmail?.let { maskEmail(it) } ?: "Не указан"

        // Коды восстановления — показываем количество, если есть.
        backupCodesSubtitle.text = if (codesCount > 0) {
            "$codesCount одноразовых кодов · сохранены"
        } else {
            "Не сгенерированы"
        }
    }

    private fun setupListeners() {
        pinSwitch.setOnCheckedChangeListener { checked ->
            val p = prefs()
            if (checked) {
                // ЗАГЛУШКА: реально тут открыть экран ввода PIN.
                // Пока просто помечаем как "задан" и фиксируем время.
                p.edit()
                    .putBoolean(KEY_PIN_ENABLED, true)
                    .putLong(KEY_PIN_SET_AT, System.currentTimeMillis())
                    .apply()
                Toast.makeText(requireContext(),
                    "PIN-код пока заглушка — ввод появится позже",
                    Toast.LENGTH_SHORT).show()
            } else {
                // Снимаем PIN — биометрия автоматом тоже выключается, она зависит от PIN.
                p.edit()
                    .putBoolean(KEY_PIN_ENABLED, false)
                    .putBoolean(KEY_BIOMETRIC, false)
                    .remove(KEY_PIN_SET_AT)
                    .apply()
            }
            loadState()
            updateStatusBanner()
        }

        biometricSwitch.setOnCheckedChangeListener { checked ->
            // Биометрия имеет смысл только при включённом PIN. Защита в loadState
            // (isEnabled = pinEnabled), но на всякий случай ещё раз.
            if (!pinSwitch.isChecked && checked) {
                biometricSwitch.isChecked = false
                Toast.makeText(requireContext(),
                    "Сначала включите PIN-код",
                    Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }
            prefs().edit().putBoolean(KEY_BIOMETRIC, checked).apply()
            updateStatusBanner()
        }

        backupEmailRow.setOnClickListener {
            // ЗАГЛУШКА: в будущем — диалог ввода email + отправка кода подтверждения.
            Toast.makeText(requireContext(),
                "Установка email появится в следующих обновлениях",
                Toast.LENGTH_SHORT).show()
        }

        backupCodesRow.setOnClickListener {
            // ЗАГЛУШКА: показ списка кодов с возможностью сгенерировать новые.
            Toast.makeText(requireContext(),
                "Список кодов появится в следующих обновлениях",
                Toast.LENGTH_SHORT).show()
        }
    }

    // ── Верхний баннер ────────────────────────────────────────────────────────

    private fun updateStatusBanner() {
        val p = prefs()
        val pin       = p.getBoolean(KEY_PIN_ENABLED, false)
        val biometric = p.getBoolean(KEY_BIOMETRIC, false)
        val email     = p.getString(KEY_BACKUP_EMAIL, null)

        val parts = mutableListOf<String>()
        if (pin) parts.add("PIN-код")
        if (biometric) parts.add("Face ID")
        if (!email.isNullOrBlank()) parts.add("резервный email")

        if (parts.isEmpty()) {
            statusTitle.text = "Защита не настроена"
            statusSubtitle.text = "Включите PIN-код, чтобы защитить аккаунт."
        } else {
            statusTitle.text = "Защита активна"
            statusSubtitle.text = parts.joinToString(", ").replaceFirstChar { it.uppercase() } +
                    " подключены."
        }
    }

    // ── Утилиты ───────────────────────────────────────────────────────────────

    /** "14 дней назад", "сегодня", "вчера" — для подзаголовка PIN. */
    private fun daysAgoText(timestampMs: Long): String {
        val diffMs = System.currentTimeMillis() - timestampMs
        val days = (diffMs / (1000L * 60 * 60 * 24)).toInt()
        return when {
            days <= 0 -> "сегодня"
            days == 1 -> "вчера"
            days < 5 -> "$days дня назад"
            else -> "$days дней назад"
        }
    }

    /** "olegsmith@gmail.com" → "o***@gmail.com" */
    private fun maskEmail(email: String): String {
        val at = email.indexOf('@')
        if (at <= 0) return email
        val local = email.substring(0, at)
        val domain = email.substring(at)
        val first = local.firstOrNull()?.toString() ?: ""
        return "$first***$domain"
    }

    companion object {
        private const val PREFS_NAME = "two_factor_prefs"
        private const val KEY_PIN_ENABLED = "pin_enabled"
        private const val KEY_PIN_SET_AT = "pin_set_at"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        private const val KEY_BACKUP_EMAIL = "backup_email"
        private const val KEY_BACKUP_CODES_COUNT = "backup_codes_count"
    }
}