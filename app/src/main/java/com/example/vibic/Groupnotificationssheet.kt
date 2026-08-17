package com.example.vibic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.vibic.network.ApiClient
import com.example.vibic.network.UpdateNotificationsRequest
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/**
 * Модальное окно настроек уведомлений группы.
 *
 * Что синхронизируется с сервером:
 *   - главный тумблер "Уведомления" → PUT api/chats/{chatId}/notifications {enabled}
 * Что хранится локально (сервер этих полей не держит): превью текста, вибрация,
 * выбор звука, "отключить на 1 час".
 *
 * Использование:
 *   GroupNotificationsSheet.new(chatId, chatName).show(supportFragmentManager, "notif")
 * Результат (изменился ли главный флаг) отдаётся через onChanged.
 */
class GroupNotificationsSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_CHAT_ID = "chat_id"
        private const val ARG_CHAT_NAME = "chat_name"

        fun new(chatId: String, chatName: String): GroupNotificationsSheet =
            GroupNotificationsSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_CHAT_ID, chatId)
                    putString(ARG_CHAT_NAME, chatName)
                }
            }
    }

    /** Колбэк: вызывается при смене главного флага уведомлений (true = включены). */
    var onChanged: ((Boolean) -> Unit)? = null

    override fun getTheme() = R.style.BottomSheetTheme

    private lateinit var chatId: String
    private lateinit var chatName: String

    private lateinit var prefs: android.content.SharedPreferences
    private fun key(name: String) = "notif_${chatId}_$name"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_group_notifications, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chatId = arguments?.getString(ARG_CHAT_ID) ?: ""
        chatName = arguments?.getString(ARG_CHAT_NAME) ?: "Группа"
        prefs = requireContext().getSharedPreferences("group_notif_prefs", android.content.Context.MODE_PRIVATE)

        view.findViewById<TextView>(R.id.sheetSubtitle).text = chatName

        val switchNotifications = view.findViewById<IosSwitch>(R.id.switchNotifications)
        val switchPreview = view.findViewById<IosSwitch>(R.id.switchPreview)
        val switchVibration = view.findViewById<IosSwitch>(R.id.switchVibration)

        // Восстанавливаем локальные значения (по умолчанию: вкл/вкл/выкл)
        switchNotifications.isChecked = prefs.getBoolean(key("enabled"), true)
        switchPreview.isChecked = prefs.getBoolean(key("preview"), true)
        switchVibration.isChecked = prefs.getBoolean(key("vibration"), false)

        // Главный тумблер — синхронизируем с сервером
        switchNotifications.setOnCheckedChangeListener { checked ->
            prefs.edit().putBoolean(key("enabled"), checked).apply()
            updateServerNotifications(checked)
            onChanged?.invoke(checked)
        }

        switchPreview.setOnCheckedChangeListener { checked ->
            prefs.edit().putBoolean(key("preview"), checked).apply()
        }
        switchVibration.setOnCheckedChangeListener { checked ->
            prefs.edit().putBoolean(key("vibration"), checked).apply()
        }

        // ── Звук (радио) ──
        val soundRows = listOf(
            "default" to view.findViewById<LinearLayout>(R.id.soundDefault),
            "bell" to view.findViewById<LinearLayout>(R.id.soundBell),
            "silent" to view.findViewById<LinearLayout>(R.id.soundSilent)
        )
        val dots = mapOf(
            "default" to view.findViewById<View>(R.id.soundDefaultDot),
            "bell" to view.findViewById<View>(R.id.soundBellDot),
            "silent" to view.findViewById<View>(R.id.soundSilentDot)
        )
        val rings = mapOf(
            "default" to view.findViewById<View>(R.id.soundDefaultRing),
            "bell" to view.findViewById<View>(R.id.soundBellRing),
            "silent" to view.findViewById<View>(R.id.soundSilentRing)
        )

        fun applySound(selected: String) {
            for ((k, _) in soundRows) {
                val on = k == selected
                dots[k]?.visibility = if (on) View.VISIBLE else View.GONE
                rings[k]?.setBackgroundResource(
                    if (on) R.drawable.bg_radio_selected else R.drawable.bg_radio_unselected
                )
            }
        }

        var currentSound = prefs.getString(key("sound"), "default") ?: "default"
        applySound(currentSound)

        for ((k, row) in soundRows) {
            row.setOnClickListener {
                currentSound = k
                prefs.edit().putString(key("sound"), k).apply()
                applySound(k)
            }
        }

        // ── Отключить на 1 час ──
        view.findViewById<LinearLayout>(R.id.btnMuteHour).setOnClickListener {
            val until = System.currentTimeMillis() + 60 * 60 * 1000L
            prefs.edit()
                .putLong(key("muted_until"), until)
                .putBoolean(key("enabled"), false)
                .apply()
            switchNotifications.isChecked = false
            updateServerNotifications(false)
            onChanged?.invoke(false)
            Toast.makeText(requireContext(), "Уведомления отключены на 1 час", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    private fun updateServerNotifications(enabled: Boolean) {
        if (chatId.isBlank()) return
        lifecycleScope.launch {
            try {
                ApiClient.apiService.updateNotifications(chatId, UpdateNotificationsRequest(enabled = enabled))
            } catch (_: Exception) {
                // молча — локальная настройка уже сохранена
            }
        }
    }
}