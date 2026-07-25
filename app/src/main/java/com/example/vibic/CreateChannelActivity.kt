package com.example.vibic

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.vibic.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.random.Random

/**
 * Закрытый вайб (канал). Четыре шага через ViewFlipper:
 *  0 — имя + описание + аватар
 *  1 — тип (публичный/приватный) + инвайт-ссылка
 *  2 — подписчики (поиск по контактам, мульти-выбор)
 *  3 — готово (пульсирующее кольцо вокруг аватара)
 *
 * После шага 3 идёт переход в ChatDetailActivity созданного канала.
 */
class CreateChannelActivity : AppCompatActivity() {

    // ---- ГОЛОВНОЙ КАРКАС ----
    private lateinit var flipper: ViewFlipper
    private lateinit var backButton: FrameLayout
    private lateinit var titleText: TextView
    private lateinit var stepCounter: TextView
    private lateinit var step1Dot: View
    private lateinit var step2Dot: View
    private lateinit var step3Dot: View
    private lateinit var bottomBar: LinearLayout
    private lateinit var btnSkip: TextView
    private lateinit var btnPrimary: TextView

    // ---- ШАГ 1: имя/описание ----
    private lateinit var channelAvatarContainer: FrameLayout
    private lateinit var channelAvatarLetter: TextView
    private lateinit var channelAvatarImage: ImageView
    private lateinit var channelNameInput: EditText
    private lateinit var channelDescriptionInput: EditText
    private lateinit var descriptionCounter: TextView

    // ---- ШАГ 2: тип ----
    private lateinit var cardPublic: LinearLayout
    private lateinit var cardPrivate: LinearLayout
    private lateinit var radioPublic: View
    private lateinit var radioPrivate: FrameLayout
    private lateinit var iconPublicTile: FrameLayout
    private lateinit var iconPrivateTile: FrameLayout
    private lateinit var inviteLinkText: TextView
    private lateinit var btnCopyLink: FrameLayout

    // ---- ШАГ 3: подписчики ----
    private lateinit var subscribersSearchInput: EditText
    private lateinit var subscribersList: RecyclerView
    private lateinit var subscribersProgress: ProgressBar
    private lateinit var subscribersChipsScroll: HorizontalScrollView
    private lateinit var subscribersChipsContainer: LinearLayout

    // ---- ШАГ 4: готово ----
    private lateinit var pulseRing: View
    private lateinit var doneAvatarLetter: TextView
    private lateinit var doneAvatarImage: ImageView
    private lateinit var doneTitle: TextView
    private lateinit var doneSubtitle: TextView

    // ---- STATE ----
    private var currentStep = 0          // 0..3
    private var isPrivate = true         // по умолчанию — приватный (это же "закрытый вайб")
    private var inviteLink: String = ""
    private var selectedAvatarUri: Uri? = null

    private val allContacts = mutableListOf<UserResponse>()
    private val filteredContacts = mutableListOf<UserResponse>()
    private val selectedIds = mutableSetOf<String>()
    private val selectedMap = mutableMapOf<String, UserResponse>()

    // ---- LAUNCHERS ----
    private val pickAvatarLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedAvatarUri = uri
            channelAvatarLetter.visibility = View.GONE
            channelAvatarImage.visibility = View.VISIBLE
            Glide.with(this).load(uri).circleCrop().into(channelAvatarImage)

            // И на финальном экране тоже
            doneAvatarLetter.visibility = View.GONE
            doneAvatarImage.visibility = View.VISIBLE
            Glide.with(this).load(uri).circleCrop().into(doneAvatarImage)
        }
    }

    private val requestContactsPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadContacts()
        else Toast.makeText(this, "Нет доступа к контактам", Toast.LENGTH_SHORT).show()
    }

    // -------------------------------------------------------------
    // LIFECYCLE
    // -------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, R.color.dark_bg)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.dark_bg)
        setContentView(R.layout.activity_create_channel)

        bindViews()
        bindListeners()
        inviteLink = generateInviteLink()
        inviteLinkText.text = inviteLink
        renderStep()
    }

    private fun bindViews() {
        flipper      = findViewById(R.id.flipper)
        backButton   = findViewById(R.id.backButton)
        titleText    = findViewById(R.id.titleText)
        stepCounter  = findViewById(R.id.stepCounter)
        step1Dot     = findViewById(R.id.step1)
        step2Dot     = findViewById(R.id.step2)
        step3Dot     = findViewById(R.id.step3)
        bottomBar    = findViewById(R.id.bottomBar)
        btnSkip      = findViewById(R.id.btnSkip)
        btnPrimary   = findViewById(R.id.btnPrimary)

        // Шаг 1
        channelAvatarContainer  = findViewById(R.id.channelAvatarContainer)
        channelAvatarLetter     = findViewById(R.id.channelAvatarLetter)
        channelAvatarImage      = findViewById(R.id.channelAvatarImage)
        channelNameInput        = findViewById(R.id.channelNameInput)
        channelDescriptionInput = findViewById(R.id.channelDescriptionInput)
        descriptionCounter      = findViewById(R.id.descriptionCounter)

        // Шаг 2
        cardPublic       = findViewById(R.id.cardPublic)
        cardPrivate      = findViewById(R.id.cardPrivate)
        radioPublic      = findViewById(R.id.radioPublic)
        radioPrivate     = findViewById(R.id.radioPrivate)
        inviteLinkText   = findViewById(R.id.inviteLinkText)
        btnCopyLink      = findViewById(R.id.btnCopyLink)

        // Шаг 3
        subscribersSearchInput    = findViewById(R.id.subscribersSearchInput)
        subscribersList           = findViewById(R.id.subscribersList)
        subscribersProgress       = findViewById(R.id.subscribersProgress)
        subscribersChipsScroll    = findViewById(R.id.subscribersChipsScroll)
        subscribersChipsContainer = findViewById(R.id.subscribersChipsContainer)

        // Шаг 4
        pulseRing        = findViewById(R.id.pulseRing)
        doneAvatarLetter = findViewById(R.id.doneAvatarLetter)
        doneAvatarImage  = findViewById(R.id.doneAvatarImage)
        doneTitle        = findViewById(R.id.doneTitle)
        doneSubtitle     = findViewById(R.id.doneSubtitle)

        subscribersList.layoutManager = LinearLayoutManager(this)
    }

    private fun bindListeners() {
        backButton.setOnClickListener { onBackPressed() }

        // ----- ШАГ 1 -----
        channelAvatarContainer.setOnClickListener { pickAvatarLauncher.launch("image/*") }

        channelNameInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val letter = s?.toString()?.trim()?.take(1)?.uppercase().orEmpty().ifBlank { "M" }
                channelAvatarLetter.text = letter
                doneAvatarLetter.text = letter
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        channelDescriptionInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                descriptionCounter.text = "${s?.length ?: 0}/200"
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // ----- ШАГ 2 -----
        cardPublic.setOnClickListener  { setPrivate(false) }
        cardPrivate.setOnClickListener { setPrivate(true) }

        btnCopyLink.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("invite", inviteLink))
            Toast.makeText(this, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
        }

        // ----- ШАГ 3 -----
        subscribersSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { filterContacts(s?.toString().orEmpty()) }
        })

        // ----- НИЖНЯЯ ПАНЕЛЬ -----
        btnSkip.setOnClickListener { goNext(skip = true) }
        btnPrimary.setOnClickListener { goNext(skip = false) }
    }

    override fun onBackPressed() {
        if (currentStep == 0 || currentStep == 3) {
            super.onBackPressed()
        } else {
            currentStep--
            renderStep()
        }
    }

    // -------------------------------------------------------------
    // ШАГИ
    // -------------------------------------------------------------

    private fun renderStep() {
        flipper.displayedChild = currentStep

        // Степпер виден только на шагах 0..2
        val showStepper = currentStep < 3
        stepCounter.visibility = if (showStepper) View.VISIBLE else View.GONE
        step1Dot.visibility    = if (showStepper) View.VISIBLE else View.GONE
        step2Dot.visibility    = if (showStepper) View.VISIBLE else View.GONE
        step3Dot.visibility    = if (showStepper) View.VISIBLE else View.GONE
        backButton.visibility  = if (currentStep == 3) View.INVISIBLE else View.VISIBLE

        when (currentStep) {
            0 -> {
                stepCounter.text = "1/3"
                step1Dot.setBackgroundResource(R.drawable.bg_step_active)
                step2Dot.setBackgroundResource(R.drawable.bg_step_inactive)
                step3Dot.setBackgroundResource(R.drawable.bg_step_inactive)
                titleText.text = "Создай вайб"
                btnSkip.visibility = View.GONE
                btnPrimary.text = "Далее"
            }
            1 -> {
                stepCounter.text = "2/3"
                step1Dot.setBackgroundResource(R.drawable.bg_step_active)
                step2Dot.setBackgroundResource(R.drawable.bg_step_active)
                step3Dot.setBackgroundResource(R.drawable.bg_step_inactive)
                titleText.text = "Тип вайба"
                btnSkip.visibility = View.GONE
                btnPrimary.text = "Далее"
                refreshTypeUI()
            }
            2 -> {
                stepCounter.text = "3/3"
                step1Dot.setBackgroundResource(R.drawable.bg_step_active)
                step2Dot.setBackgroundResource(R.drawable.bg_step_active)
                step3Dot.setBackgroundResource(R.drawable.bg_step_active)
                titleText.text = "Подписчики"
                btnSkip.visibility = View.VISIBLE
                btnPrimary.text = "Создать вайб"
                ensureContactsLoaded()
            }
            3 -> {
                // Финал
                titleText.text = ""
                bottomBar.visibility = View.GONE
                startPulseAnimation()
            }
        }
    }

    private fun goNext(skip: Boolean) {
        when (currentStep) {
            0 -> {
                val name = channelNameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Введите название вайба", Toast.LENGTH_SHORT).show()
                    return
                }
                currentStep = 1
                renderStep()
            }
            1 -> {
                currentStep = 2
                renderStep()
            }
            2 -> {
                createChannel(inviteWithoutSubscribers = skip)
            }
        }
    }

    // -------------------------------------------------------------
    // ШАГ 2: тип
    // -------------------------------------------------------------

    private fun setPrivate(value: Boolean) {
        isPrivate = value
        refreshTypeUI()
    }

    private fun refreshTypeUI() {
        if (isPrivate) {
            cardPublic.setBackgroundResource(R.drawable.bg_type_card_unselected)
            cardPrivate.setBackgroundResource(R.drawable.bg_type_card_selected)
            radioPublic.setBackgroundResource(R.drawable.bg_radio_unselected)
            radioPrivate.setBackgroundResource(R.drawable.bg_radio_selected)
            (radioPrivate.getChildAt(0) as? ImageView)?.visibility = View.VISIBLE
        } else {
            cardPublic.setBackgroundResource(R.drawable.bg_type_card_selected)
            cardPrivate.setBackgroundResource(R.drawable.bg_type_card_unselected)
            radioPublic.setBackgroundResource(R.drawable.bg_radio_selected)
            radioPrivate.setBackgroundResource(R.drawable.bg_radio_unselected)
            (radioPrivate.getChildAt(0) as? ImageView)?.visibility = View.GONE
        }
    }

    private fun generateInviteLink(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val tail = (1..8).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        return "vibic.app/+$tail"
    }

    // -------------------------------------------------------------
    // ШАГ 3: контакты
    // -------------------------------------------------------------

    private var contactsLoaded = false

    private fun ensureContactsLoaded() {
        if (contactsLoaded) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) {
            loadContacts()
        } else {
            requestContactsPerm.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun loadContacts() {
        subscribersProgress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { readPhoneContacts() }
            allContacts.clear()
            allContacts.addAll(list)
            contactsLoaded = true
            filterContacts("")
            subscribersProgress.visibility = View.GONE
        }
    }

    private fun readPhoneContacts(): List<UserResponse> {
        val result = mutableListOf<UserResponse>()
        val seen = hashSetOf<String>()
        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val phone = normalizePhone(if (phoneIdx >= 0) it.getString(phoneIdx) else null)
                if (phone.isBlank() || !seen.add(phone)) continue
                val name = if (nameIdx >= 0) it.getString(nameIdx)?.trim() ?: phone else phone
                val parts = name.split("\\s+".toRegex(), limit = 2)
                result.add(
                    UserResponse(
                        id         = "local_${UUID.randomUUID()}",
                        phone      = phone,
                        first_name = parts.getOrNull(0),
                        last_name  = parts.getOrNull(1),
                        avatar_url = null,
                        status     = null,
                        online     = false
                    )
                )
            }
        }
        return result
    }

    private fun filterContacts(query: String) {
        val q = query.trim().lowercase()
        filteredContacts.clear()
        filteredContacts.addAll(
            if (q.isEmpty()) allContacts
            else allContacts.filter {
                val name = "${it.first_name.orEmpty()} ${it.last_name.orEmpty()}".lowercase()
                name.contains(q) || it.phone?.contains(q) == true
            }
        )
        subscribersList.adapter = SelectableContactsAdapter(filteredContacts, selectedIds) { c, sel ->
            if (sel) {
                selectedIds.add(c.id); selectedMap[c.id] = c
            } else {
                selectedIds.remove(c.id); selectedMap.remove(c.id)
            }
            updateChips()
        }
    }

    private fun updateChips() {
        subscribersChipsContainer.removeAllViews()
        if (selectedIds.isEmpty()) {
            subscribersChipsScroll.visibility = View.GONE
            return
        }
        subscribersChipsScroll.visibility = View.VISIBLE
        for (id in selectedIds) {
            val contact = selectedMap[id] ?: continue
            val name = "${contact.first_name.orEmpty()} ${contact.last_name.orEmpty()}".trim()
                .ifBlank { contact.phone ?: "?" }
            val chip = LayoutInflater.from(this)
                .inflate(R.layout.item_selected_contact_chip, subscribersChipsContainer, false)
            chip.findViewById<TextView>(R.id.chipAvatarText).text = name.take(1).uppercase()
            chip.findViewById<TextView>(R.id.chipName).text = name.split(" ").first()
            chip.findViewById<FrameLayout>(R.id.chipRemove).setOnClickListener {
                selectedIds.remove(id)
                selectedMap.remove(id)
                updateChips()
                filterContacts(subscribersSearchInput.text.toString())
            }
            subscribersChipsContainer.addView(chip)
        }
        subscribersChipsScroll.post {
            subscribersChipsScroll.fullScroll(HorizontalScrollView.FOCUS_RIGHT)
        }
    }

    private fun normalizePhone(phone: String?): String {
        if (phone.isNullOrBlank()) return ""
        val c = phone.replace("\\s|-|\\(|\\)".toRegex(), "")
        return if (c.startsWith("+")) "+${c.drop(1).replace("[^0-9]".toRegex(), "")}"
        else c.replace("[^0-9]".toRegex(), "")
    }

    // -------------------------------------------------------------
    // СОЗДАНИЕ КАНАЛА
    // -------------------------------------------------------------

    private fun createChannel(inviteWithoutSubscribers: Boolean) {
        val name = channelNameInput.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Введите название вайба", Toast.LENGTH_SHORT).show()
            currentStep = 0
            renderStep()
            return
        }

        btnPrimary.isEnabled = false
        btnSkip.isEnabled = false
        btnPrimary.alpha = 0.5f

        lifecycleScope.launch {
            // 1) Резолвим выбранных подписчиков -> их user_id
            val userIds = if (inviteWithoutSubscribers || selectedIds.isEmpty()) {
                emptyList()
            } else {
                withContext(Dispatchers.IO) {
                    selectedMap.values.map { c ->
                        async {
                            try {
                                val phone = GroupInfoActivity.phoneToDbFormat(c.phone ?: return@async null)
                                if (phone.isBlank()) return@async null
                                val resp = ApiClient.apiService.searchUsers(phone)
                                if (resp.isSuccessful) resp.body()?.firstOrNull()?.id else null
                            } catch (e: Exception) { null }
                        }
                    }.awaitAll().filterNotNull().distinct()
                }
            }

            // 2) Создаём канал
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.apiService.createChannel(
                        CreateChannelRequest(
                            name = name,
                            description = channelDescriptionInput.text.toString().trim()
                                .ifBlank { null }
                        )
                    )
                }

                if (response.isSuccessful) {
                    val chat = response.body()
                    if (chat != null) {
                        // 3) Показываем экран "Готово" с пульсацией
                        currentStep = 3
                        doneTitle.text = "Вайб создан!"
                        doneSubtitle.text =
                            "«${name}» готов принимать ваши посты. Поделитесь ссылкой, чтобы пригласить подписчиков."
                        renderStep()

                        // Через 1.6с открываем чат канала
                        pulseRing.postDelayed({
                            val intent = Intent(this@CreateChannelActivity, ChatDetailActivity::class.java)
                            intent.putExtra("chat_id", chat.id)
                            intent.putExtra("chat_name", chat.name ?: name)
                            startActivity(intent)
                            setResult(RESULT_OK)
                            finish()
                        }, 1600)
                    } else {
                        showError("Ошибка: пустой ответ сервера")
                    }
                } else {
                    showError("Ошибка создания вайба (${response.code()})")
                }
            } catch (e: Exception) {
                showError("Ошибка: ${e.message}")
            }
        }
    }

    private fun showError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        btnPrimary.isEnabled = true
        btnSkip.isEnabled = true
        btnPrimary.alpha = 1f
    }

    // -------------------------------------------------------------
    // ШАГ 4: пульсация кольца
    // -------------------------------------------------------------

    private fun startPulseAnimation() {
        val anim = AnimationUtils.loadAnimation(this, R.anim.pulse_ring)
        pulseRing.startAnimation(anim)
    }

    // -------------------------------------------------------------
    // АДАПТЕР КОНТАКТОВ (с галочкой)
    // -------------------------------------------------------------

    inner class SelectableContactsAdapter(
        private val contacts: List<UserResponse>,
        private val selectedIds: Set<String>,
        private val onToggle: (UserResponse, Boolean) -> Unit
    ) : RecyclerView.Adapter<SelectableContactsAdapter.VH>() {

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val avatarText:   TextView    = itemView.findViewById(R.id.avatarText)
            val avatarCard:   CardView    = itemView.findViewById(R.id.avatarCard)
            val contactName:  TextView    = itemView.findViewById(R.id.contactName)
            val contactPhone: TextView    = itemView.findViewById(R.id.contactPhone)
            val checkOverlay: FrameLayout = itemView.findViewById(R.id.checkOverlay)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_contact_selectable, parent, false))

        override fun getItemCount() = contacts.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val contact = contacts[position]
            val name = "${contact.first_name.orEmpty()} ${contact.last_name.orEmpty()}".trim()
                .ifBlank { contact.phone ?: "?" }
            holder.contactName.text = name
            holder.contactPhone.text = contact.phone ?: ""
            holder.avatarText.text = name.take(1).uppercase()
            val isSelected = selectedIds.contains(contact.id)
            holder.checkOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener {
                val nowSelected = !selectedIds.contains(contact.id)
                onToggle(contact, nowSelected)
                holder.checkOverlay.visibility = if (nowSelected) View.VISIBLE else View.GONE
            }
        }
    }
}