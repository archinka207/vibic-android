package com.example.vibic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.vibic.network.ApiClient
import com.example.vibic.network.TokenManager
import com.example.vibic.network.WebSocketManager
import kotlinx.coroutines.launch
import java.io.File

class ProfileFragment : Fragment() {

    private lateinit var avatarCard: CardView
    private lateinit var avatarText: TextView
    private lateinit var avatarImage: ImageView
    private lateinit var nicknameText: TextView
    private lateinit var usernameRow: LinearLayout
    private lateinit var usernameText: TextView
    private lateinit var phoneText: TextView
    private lateinit var statusText: TextView                // ← статус "Доступен в Vibic"

    // Верхние кнопки
    private lateinit var qrButton: FrameLayout
    private lateinit var editButton: FrameLayout

    // Строки меню
    // private lateinit var myPageRow: LinearLayout // временно скрыта, см. fragment_profile.xml
    private lateinit var notificationsRow: LinearLayout
    private lateinit var privacyRow: LinearLayout
    private lateinit var devicesRow: LinearLayout
    private lateinit var twoFactorRow: LinearLayout
    private lateinit var themeRow: LinearLayout
    private lateinit var aboutRow: LinearLayout
    private lateinit var helpRow: LinearLayout
    private lateinit var rateRow: LinearLayout

    private lateinit var rootView: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        rootView = inflater.inflate(R.layout.fragment_profile, container, false)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        avatarCard = view.findViewById(R.id.avatarCard)
        avatarText = view.findViewById(R.id.avatarText)
        avatarImage = view.findViewById(R.id.avatarImage)
        nicknameText = view.findViewById(R.id.nicknameInput)
        usernameRow = view.findViewById(R.id.usernameRow)
        usernameText = view.findViewById(R.id.usernameText)
        phoneText = view.findViewById(R.id.phoneNumberText)
        statusText = view.findViewById(R.id.statusText)              // ← новый id

        qrButton = view.findViewById(R.id.qrButton)
        editButton = view.findViewById(R.id.editButton)

        // myPageRow = view.findViewById(R.id.myPageRow) // временно скрыта, см. fragment_profile.xml
        notificationsRow = view.findViewById(R.id.notificationsRow)
        privacyRow = view.findViewById(R.id.privacyRow)
        devicesRow = view.findViewById(R.id.devicesRow)
        twoFactorRow = view.findViewById(R.id.twoFactorRow)
        themeRow = view.findViewById(R.id.themeRow)
        aboutRow = view.findViewById(R.id.aboutRow)
        helpRow = view.findViewById(R.id.helpRow)
        rateRow = view.findViewById(R.id.rateRow)

        loadLocalUserData()
        loadUserProfileInBackground()
        updateTwoFactorStatus()

        // Аватарка в профиле больше не кликабельна — менять её можно только
        // на экране редактирования профиля (EditProfileActivity).
        nicknameText.setOnClickListener { openEditProfileScreen() }

        // Верхние кнопки
        qrButton.setOnClickListener {
            startActivity(Intent(requireContext(), MyQrActivity::class.java))
        }
        editButton.setOnClickListener { openEditProfileScreen() }

        // Строки меню — заглушки
        // myPageRow.setOnClickListener { // временно скрыта, см. fragment_profile.xml
        //     startActivity(Intent(requireContext(), MyPageActivity::class.java))
        // }
        notificationsRow.setOnClickListener {
            startActivity(Intent(requireContext(), NotificationsActivity::class.java))
        }
        privacyRow.setOnClickListener {
            startActivity(Intent(requireContext(), PrivacyActivity::class.java))
        }
        devicesRow.setOnClickListener {
            startActivity(Intent(requireContext(), DevicesActivity::class.java))
        }
        twoFactorRow.setOnClickListener {
            val intent = Intent(requireContext(), TwoFactorActivity::class.java)
            intent.putExtra(TwoFactorActivity.EXTRA_SETTINGS_MODE, true)
            startActivity(intent)
        }
        aboutRow.setOnClickListener {
            startActivity(Intent(requireContext(), AboutActivity::class.java))
        }
        helpRow.setOnClickListener {
            startActivity(Intent(requireContext(), HelpActivity::class.java))
        }
        themeRow.setOnClickListener {
            startActivity(Intent(requireContext(), ThemePickerActivity::class.java))
        }
        rateRow.setOnClickListener {
            val pkg = requireContext().packageName
            try {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                marketIntent.setPackage("com.android.vending")
                startActivity(marketIntent)
            } catch (e: android.content.ActivityNotFoundException) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
            }
        }

        usernameRow.setOnClickListener { copyUsernameToClipboard() }
    }

    override fun onResume() {
        super.onResume()
        if (::nicknameText.isInitialized) {
            loadLocalUserData()
            updateTwoFactorStatus()
            updateThemeModeLabel()
        }
    }

    /** Подтягивает выбранный "theme_mode" из prefs и показывает его подписью под "Тема оформления". */
    private fun updateThemeModeLabel() {
        val themeModeText = view?.findViewById<TextView>(R.id.themeModeText) ?: return
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getString("theme_mode", "system")
        themeModeText.text = when (mode) {
            "light" -> "Светлая"
            "dark" -> "Тёмная"
            else -> "Как в системе"
        }
    }

    /** Подтягивает статус 2FA из его prefs и отображает "Вкл" / "Выкл". */
    private fun updateTwoFactorStatus() {
        val statusView = view?.findViewById<TextView>(R.id.twoFactorStatus) ?: return
        val tfa = requireContext().getSharedPreferences("two_factor_prefs", Context.MODE_PRIVATE)
        val pin = tfa.getBoolean("pin_enabled", false)
        statusView.text = if (pin) "Вкл" else "Выкл"
    }

    private fun openEditProfileScreen() {
        startActivity(Intent(requireContext(), EditProfileActivity::class.java))
    }

    private fun showSoonToast() {
        Toast.makeText(requireContext(), "Скоро будет доступно", Toast.LENGTH_SHORT).show()
    }

    private fun loadLocalUserData() {
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        val name = prefs.getString("user_nickname", null)
        val firstName = prefs.getString("user_first_name", null)
        val lastName = prefs.getString("user_last_name", null)
        val phone = TokenManager.getPhone() ?: prefs.getString("user_phone", null)
        val username = prefs.getString("user_username", null)
        val status = prefs.getString("user_status", null)

        val fullName = when {
            !firstName.isNullOrBlank() && !lastName.isNullOrBlank() -> "$firstName $lastName"
            !firstName.isNullOrBlank() -> firstName
            !lastName.isNullOrBlank() -> lastName
            !name.isNullOrBlank() -> name
            else -> null
        }

        if (fullName != null) {
            nicknameText.text = fullName
            avatarText.text = fullName.take(1).uppercase()
        } else {
            nicknameText.text = "Пользователь"
            avatarText.text = "П"
        }

        applyUsername(username)
        phoneText.text = formatPhoneNumber(phone ?: TokenManager.getPhone() ?: "+7")

        applyStatus(status)

        loadSavedAvatar()
    }

    /** Показывает или скрывает строку статуса. */
    private fun applyStatus(status: String?) {
        if (!status.isNullOrBlank()) {
            statusText.text = status
            statusText.visibility = View.VISIBLE
        } else {
            statusText.visibility = View.GONE
        }
    }

    /** Показывает или скрывает строку @username с иконкой копирования. */
    private fun applyUsername(username: String?) {
        if (!username.isNullOrBlank()) {
            usernameText.text = "@$username"
            usernameRow.visibility = View.VISIBLE
        } else {
            usernameRow.visibility = View.GONE
        }
    }

    private fun copyUsernameToClipboard() {
        val username = requireContext()
            .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("user_username", null)
        if (username.isNullOrBlank()) return

        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("username", "@$username"))
        Toast.makeText(requireContext(), "Скопировано", Toast.LENGTH_SHORT).show()
    }

    private fun loadUserProfileInBackground() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getCurrentUser()

                if (response.isSuccessful) {
                    val user = response.body()
                    if (user != null) {
                        val fullName = buildFullName(user.first_name, user.last_name)

                        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        prefs.edit().apply {
                            putString("user_nickname", fullName)
                            putString("user_first_name", user.first_name)
                            putString("user_last_name", user.last_name)
                            user.phone?.let { putString("user_phone", it) }
                            // Статус с сервера сохраняем локально, чтобы при следующем открытии
                            // экрана не ждать загрузки.
                            if (user.status != null) {
                                putString("user_status", user.status)
                            } else {
                                remove("user_status")
                            }
                            apply()
                        }

                        requireActivity().runOnUiThread {
                            nicknameText.text = fullName
                            phoneText.text = formatPhoneNumber(user.phone ?: TokenManager.getPhone())
                            applyStatus(user.status)

                            if (avatarImage.visibility != View.VISIBLE) {
                                avatarText.text = fullName.take(1).uppercase()
                            }
                        }
                    }
                } else {
                    if (response.code() == 401) {
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "Сессия истекла, войдите заново", Toast.LENGTH_SHORT).show()
                            this@ProfileFragment.logout()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Ошибка фоновой загрузки", e)
            }
        }
    }

    private fun buildFullName(firstName: String?, lastName: String?): String {
        return when {
            !firstName.isNullOrBlank() && !lastName.isNullOrBlank() -> "$firstName $lastName"
            !firstName.isNullOrBlank() -> firstName
            !lastName.isNullOrBlank() -> lastName
            else -> "Пользователь"
        }
    }

    private fun loadSavedAvatar() {
        try {
            val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val path = prefs.getString("avatar_path", null)

            if (path != null) {
                val file = File(path)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(path)
                    avatarImage.setImageBitmap(bitmap)
                    avatarText.visibility = View.GONE
                    avatarImage.visibility = View.VISIBLE
                    return
                } else {
                    prefs.edit().remove("avatar_path").apply()
                }
            }

            showInitialsAvatar()
        } catch (e: Exception) {
            showInitialsAvatar()
        }
    }

    private fun showInitialsAvatar() {
        avatarText.visibility = View.VISIBLE
        avatarImage.visibility = View.GONE
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val name = prefs.getString("user_nickname", "П")
        avatarText.text = name?.take(1)?.uppercase() ?: "П"
    }

    private fun formatPhoneNumber(phone: String?): String {
        if (phone.isNullOrBlank()) return "+7 (999) 123-45-67"

        val digits = phone.filter { it.isDigit() }

        return when (digits.length) {
            11 -> {
                val code = digits.drop(1).take(3)
                val first = digits.drop(4).take(3)
                val second = digits.drop(7).take(2)
                val third = digits.drop(9).take(2)
                "+7 ($code) $first-$second-$third"
            }
            10 -> {
                val code = digits.take(3)
                val first = digits.drop(3).take(3)
                val second = digits.drop(6).take(2)
                val third = digits.drop(8).take(2)
                "+7 ($code) $first-$second-$third"
            }
            else -> {
                if (phone.startsWith("+")) phone else "+$phone"
            }
        }
    }

    private fun logout() {
        PushTokenSyncManager.deleteCurrentTokenFromBackend()

        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        TokenManager.clearTokens()
        WebSocketManager.shared.disconnect()

        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }
}