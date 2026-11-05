package com.example.vibic

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.vibic.channel.ADMIN_RIGHTS_INFO
import com.example.vibic.channel.AdminRights
import com.example.vibic.channel.ChannelAdmin
import com.example.vibic.channel.ChannelAdminPolicy
import com.example.vibic.channel.ChannelAdmins
import com.example.vibic.channel.ChannelStaff
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Права одного админа канала. Два режима:
 * - пользователь уже админ — правка прав и должности, можно снять с должности;
 * - ещё не админ — назначение («Новый админ»).
 *
 * Возвращает RESULT_OK, если что-то сохранено или админ снят.
 */
class AdminRightsActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_CHAT_ID = "chat_id"
        private const val EXTRA_USER_ID = "user_id"
        private const val EXTRA_USER_NAME = "user_name"
        private const val EXTRA_AVATAR_URL = "avatar_url"

        /** [name] и [avatarUrl] нужны для нового админа — у существующего они уже есть. */
        fun intent(context: Context, chatId: String, userId: String, name: String, avatarUrl: String?) =
            Intent(context, AdminRightsActivity::class.java).apply {
                putExtra(EXTRA_CHAT_ID, chatId)
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_USER_NAME, name)
                putExtra(EXTRA_AVATAR_URL, avatarUrl)
            }

        private const val LOCKED_ALPHA = 0.35f
    }

    private val repo get() = ChannelAdmins.repository
    private val me get() = ChannelAdmins.myUserId()

    private lateinit var doneButton: TextView
    private lateinit var titleInput: EditText
    private lateinit var rightsCounter: TextView
    private lateinit var saveButton: View
    private lateinit var demoteButton: View
    private lateinit var switches: List<IosSwitch>

    private var chatId = ""
    private var userId = ""
    private var staff: ChannelStaff? = null
    /** null — назначаем нового админа. */
    private var existing: ChannelAdmin? = null
    private var canEdit = false

    private var initialRights = AdminRights.NONE
    private var initialTitle = ""
    private var saving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_admin_rights)

        chatId = intent.getStringExtra(EXTRA_CHAT_ID).orEmpty()
        userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
        if (chatId.isBlank() || userId.isBlank()) {
            Toast.makeText(this, "Ошибка: не передан канал или пользователь", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        doneButton = findViewById(R.id.doneButton)
        titleInput = findViewById(R.id.titleInput)
        rightsCounter = findViewById(R.id.rightsCounter)
        saveButton = findViewById(R.id.saveButton)
        demoteButton = findViewById(R.id.demoteButton)
        switches = setupRightRows()

        showPerson(intent.getStringExtra(EXTRA_USER_NAME) ?: "Пользователь", intent.getStringExtra(EXTRA_AVATAR_URL))

        findViewById<View>(R.id.backButton).setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
        doneButton.setOnClickListener { save() }
        saveButton.setOnClickListener { save() }
        demoteButton.setOnClickListener { confirmDemote() }
        titleInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = updateState()
        })

        load()
    }

    private fun setupRightRows(): List<IosSwitch> {
        val ids = listOf(
            R.id.right0, R.id.right1, R.id.right2, R.id.right3, R.id.right4,
            R.id.right5, R.id.right6, R.id.right7, R.id.right8,
        )
        val offColor = ContextCompat.getColor(this, R.color.channel_switch_off)
        return ids.mapIndexed { i, id ->
            val row = findViewById<View>(id)
            val info = ADMIN_RIGHTS_INFO[i]
            row.findViewById<ImageView>(R.id.rightIcon).setImageResource(info.icon)
            row.findViewById<TextView>(R.id.rightTitle).text = info.title
            row.findViewById<TextView>(R.id.rightDescription).apply {
                text = info.description
                visibility = if (info.description != null) View.VISIBLE else View.GONE
            }
            row.findViewById<IosSwitch>(R.id.rightSwitch).apply {
                trackColorOff = offColor
                setOnCheckedChangeListener { updateState() }
            }
        }
    }

    // ── Загрузка ─────────────────────────────────────────────────────────────

    private fun load() {
        lifecycleScope.launch {
            val s = ChannelAdmins.loadStaff(chatId)
            if (s == null) {
                Toast.makeText(this@AdminRightsActivity, "Не удалось загрузить канал", Toast.LENGTH_SHORT).show()
                finish(); return@launch
            }
            staff = s
            existing = s.adminOrNull(userId)
            bind(s)
            findViewById<View>(R.id.content).visibility = View.VISIBLE
        }
    }

    private fun bind(s: ChannelStaff) {
        val admin = existing
        val mine = s.rightsOf(me)

        if (admin == null) {
            findViewById<TextView>(R.id.screenTitle).text = "Новый админ"
            findViewById<TextView>(R.id.personSubtitle).text = "Новый администратор"
            // Права по умолчанию, но не больше, чем есть у назначающего
            initialRights = AdminRights.DEFAULT_NEW_ADMIN intersect mine
            initialTitle = ""
            canEdit = ChannelAdminPolicy.checkPromote(s, me, userId, AdminRights.NONE, "") == null
        } else {
            showPerson(admin.name, admin.avatarUrl)
            findViewById<TextView>(R.id.personSubtitle).text = promotedText(s, admin)
            initialRights = admin.rights
            initialTitle = admin.customTitle
            // NONE: проверяем само право редактировать, а не конкретный набор прав
            canEdit = ChannelAdminPolicy.checkUpdateRights(s, me, userId, AdminRights.NONE, "") == null
        }

        titleInput.setText(initialTitle)
        titleInput.isEnabled = canEdit

        // Свитч права, которого нет у меня, выдать нельзя: он полупрозрачный и не нажимается
        val myFlags = mine.toFlags()
        val flags = initialRights.toFlags()
        var anyLocked = false
        switches.forEachIndexed { i, sw ->
            sw.setChecked(flags[i], animate = false)
            val unlocked = canEdit && myFlags[i]
            sw.isEnabled = unlocked
            sw.alpha = if (unlocked) 1f else LOCKED_ALPHA
            if (canEdit && !myFlags[i]) anyLocked = true
        }
        findViewById<View>(R.id.lockedHint).visibility = if (anyLocked) View.VISIBLE else View.GONE

        saveButton.visibility = if (canEdit) View.VISIBLE else View.GONE
        doneButton.visibility = if (canEdit) View.VISIBLE else View.GONE
        demoteButton.visibility =
            if (admin != null && ChannelAdminPolicy.canDemote(s, me, userId)) View.VISIBLE else View.GONE

        updateState()
    }

    private fun showPerson(name: String, avatarUrl: String?) {
        findViewById<TextView>(R.id.personName).text = name
        val avatarText = findViewById<TextView>(R.id.avatarText)
        val avatarImage = findViewById<ImageView>(R.id.avatarImage)
        avatarText.text = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        if (!avatarUrl.isNullOrBlank()) {
            avatarImage.visibility = View.VISIBLE
            avatarText.visibility = View.GONE
            Glide.with(this).load(avatarUrl).circleCrop().into(avatarImage)
        }
    }

    /** «Назначен вами · 5 ноября». У админов с сервера даты пока нет — тогда без неё. */
    private fun promotedText(s: ChannelStaff, admin: ChannelAdmin): String {
        val who = when (admin.promotedBy) {
            me -> "Назначен вами"
            s.ownerId -> "Назначен владельцем"
            else -> "Назначен администратором"
        }
        if (admin.promotedAt <= 0L) return who
        val date = Date(admin.promotedAt)
        val sameYear = Calendar.getInstance().run {
            val now = get(Calendar.YEAR); time = date; get(Calendar.YEAR) == now
        }
        val pattern = if (sameYear) "d MMMM" else "d MMMM yyyy"
        return "$who · ${SimpleDateFormat(pattern, Locale("ru")).format(date)}"
    }

    // ── Состояние ────────────────────────────────────────────────────────────

    private fun currentRights() = AdminRights.fromFlags(switches.map { it.isChecked })

    private fun currentTitle() = titleInput.text.toString().trim()

    private fun hasChanges(): Boolean =
        canEdit && (currentRights() != initialRights || currentTitle() != initialTitle)

    private fun updateState() {
        if (!::switches.isInitialized) return
        rightsCounter.text = "${switches.count { it.isChecked }} из ${switches.size} прав"
    }

    // ── Действия ─────────────────────────────────────────────────────────────

    private fun leave() {
        if (!hasChanges()) { finish(); return }
        AlertDialog.Builder(this, R.style.VibicAlertDialog)
            .setTitle("Сохранить изменения?")
            .setMessage(if (existing == null) "Администратор ещё не назначен." else "Права и должность изменены, но не сохранены.")
            .setPositiveButton("Сохранить") { _, _ -> save() }
            .setNegativeButton("Не сохранять") { _, _ -> finish() }
            .setNeutralButton("Отмена", null)
            .show()
    }

    private fun save() {
        if (!canEdit || saving) return
        saving = true
        val rights = currentRights()
        val title = currentTitle()
        lifecycleScope.launch {
            val result = if (existing == null) {
                repo.promoteToAdmin(chatId, userId, rights, title).map { "Администратор назначен" }
            } else {
                repo.updateRights(chatId, userId, rights, title).map { "Права сохранены" }
            }
            saving = false
            result
                .onSuccess { done(it) }
                .onFailure { toast(it.message ?: "Не получилось сохранить") }
        }
    }

    private fun confirmDemote() {
        val name = findViewById<TextView>(R.id.personName).text
        AlertDialog.Builder(this, R.style.VibicAlertDialog)
            .setTitle("Снять с должности?")
            .setMessage("$name останется подписчиком, но потеряет все права администратора.")
            .setPositiveButton("Снять") { _, _ ->
                lifecycleScope.launch {
                    repo.demote(chatId, userId)
                        .onSuccess { done("$name больше не администратор") }
                        .onFailure { toast(it.message ?: "Не получилось") }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun done(message: String) {
        toast(message)
        setResult(RESULT_OK)
        finish()
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
