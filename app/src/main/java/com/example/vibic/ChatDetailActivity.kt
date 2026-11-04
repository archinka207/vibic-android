package com.example.vibic

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.camera2.interop.Camera2Interop
import android.hardware.camera2.CaptureRequest
import android.util.Range as AndroidRange
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vibic.network.ApiClient
import com.example.vibic.network.SendMessageRequest
import com.google.android.material.color.MaterialColors
import com.example.vibic.network.TokenManager
import com.example.vibic.network.WebSocketManager
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Date
import java.util.UUID
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.vibic.network.MessageDto
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
class ChatDetailActivity : AppCompatActivity() {

    // ── Основные views ─────────────────────────────────────────────────────────
    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageView
    private lateinit var micButton: ImageView
    private lateinit var videoNoteButton: ImageView
    private lateinit var attachButton: ImageView
    private lateinit var backButton: ImageView
    private lateinit var chatUserCapsule: LinearLayout
    private lateinit var emojiButton: ImageView
    private lateinit var emojiPicker: EmojiPickerView
    private lateinit var emojiPanelContainer: LinearLayout
    private lateinit var keyboardButton: ImageView
    private lateinit var chatUserName: TextView
    private lateinit var chatUserStatus: TextView

    // ── Reply views ────────────────────────────────────────────────────────────
    private lateinit var replyBar: LinearLayout
    private lateinit var replyBarSenderName: TextView
    private lateinit var replyBarText: TextView
    private lateinit var replyBarClose: ImageView
    private lateinit var replyBarThumb: ImageView
    private lateinit var replyDivider: View

    // ── Голосовое recording views ──────────────────────────────────────────────
    private lateinit var inputLayout: LinearLayout
    private lateinit var recordingLayout: LinearLayout
    private lateinit var lockedRecordingLayout: LinearLayout
    private lateinit var recordingTimer: TextView
    private lateinit var lockedTimer: TextView
    private lateinit var recordingDot: View
    private lateinit var lockedDot: View
    private lateinit var slideToCancelLayout: LinearLayout
    private lateinit var lockLayout: LinearLayout
    private lateinit var deleteRecordingButton: ImageView
    private lateinit var sendLockedButton: FrameLayout

    // Новые элементы для Lock-режима (Пауза и 1x)
    private lateinit var pauseRecordingButton: ImageView
    private lateinit var viewOnceButton: TextView
    private var isRecordingPaused = false
    private var isVoiceViewOnce = false

    // ── Video note ─────────────────────────────────────────────────────────────
    private lateinit var videoNoteOverlay: FrameLayout
    private lateinit var cameraPreviewView: PreviewView
    private lateinit var cameraPreviewViewB: PreviewView
    private lateinit var videoNoteTimer: TextView
    private lateinit var videoNoteHint: TextView
    private lateinit var videoNoteRecordButton: FrameLayout
    private lateinit var videoNoteRecordInner: View
    private lateinit var videoNoteCancelButton: ImageView
    private lateinit var videoNoteFlipButton: ImageView

    // ── CameraX ────────────────────────────────────────────────────────────────
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeVideoRecording: Recording? = null
    private var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var isVideoRecording = false
    private var isFlipping = false
    private var videoFile: File? = null
    private var timerJob: Job? = null
    private var timerStartMs = 0L
    // Действие, которое выполняется после прихода Finalize от текущей записи (для безопасного флипа)
    private var pendingFlipAction: (() -> Unit)? = null
    // Какой из двух PreviewView сейчас активен (true = A, false = B). При флипе свапается.
    private var isPreviewAActive = true
    // Последний успешно снятый кадр preview — обновляется постоянно в фоне.
    // Используется для "заморозки" при флипе (WhatsApp-style).
    @Volatile private var lastPreviewFrame: android.graphics.Bitmap? = null
    private var frameGrabberJob: Job? = null

    // ── Контекстное меню ───────────────────────────────────────────────────────
    private lateinit var contextMenuOverlay: FrameLayout
    private lateinit var clonedMessageContainer: FrameLayout
    private lateinit var reactionsBar: LinearLayout
    private lateinit var actionsMenu: LinearLayout
    private lateinit var actionReply: LinearLayout
    private lateinit var actionCopy: LinearLayout
    private lateinit var actionForward: LinearLayout
    private lateinit var actionEdit: LinearLayout
    private lateinit var actionPin: LinearLayout
    private lateinit var actionDelete: LinearLayout
    private var hiddenAnchorView: View? = null

    // ── Search ──
    private lateinit var searchBar: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var searchClear: ImageView
    private lateinit var searchNavBar: LinearLayout
    private lateinit var searchCounter: TextView
    private lateinit var searchUp: ImageView
    private lateinit var searchDown: ImageView
    private var isSearchMode = false
    private var searchMatches: List<String> = emptyList()   // id сообщений, от новых к старым
    private var searchIndex = -1

    // ── State ──────────────────────────────────────────────────────────────────
    private lateinit var adapter: MessageAdapter
    private var chatId = ""
    private var myUserId = ""
    private var userName = ""
    private val allMessages = mutableListOf<MessageDto>()
    private val sentMessageIds = mutableSetOf<String>()
    private val processedMessageIds = mutableSetOf<String>()
    private var webSocketJob: Job? = null
    private var connectionCheckJob: Job? = null
    private val gson = Gson()
    private var isSubscribed = false
    private var isFirstLoad = true
    private var replyToMessage: MessageDto? = null

    // ── Voice state ────────────────────────────────────────────────────────────
    private var mediaRecorder: MediaRecorder? = null
    private var voiceFile: File? = null
    private var isVoiceRecording = false
    private var isLocked = false
    private var voiceRecordingSeconds = 0
    private var voiceRecordingJob: Job? = null
    private var dotAnimator: ObjectAnimator? = null
    private var touchStartX = 0f
    private var touchStartY = 0f
    // ── Анимации записи (Telegram-style) ──
    private enum class RecMode { VOICE, VIDEO }
    private var recMode = RecMode.VOICE
    /** Кнопка, которую сейчас держат: микрофон или кружок. */
    private var activeRecButton: ImageView? = null
    private var holdStarted = false
    private val holdRunnable = Runnable { onHoldStart() }
    private var isVideoHold = false
    private var videoHoldJob: Job? = null
    private var ampJob: Job? = null
    private var videoFinalizeAction: ((Boolean, File) -> Unit)? = null
    private var circleHomeX = 0f
    private var circleHomeY = 0f
    private var lockPillBaseY = 0f
    private var slideArrowAnim: ObjectAnimator? = null
    private var lockArrowAnim: ObjectAnimator? = null
    private lateinit var recordOverlay: FrameLayout
    private lateinit var recordCircle: RecordCircleView
    private lateinit var lockPill: LinearLayout
    private lateinit var lockArrow: ImageView
    private lateinit var cancelTrash: ImageView
    private lateinit var cancelMic: ImageView
    private lateinit var slideArrow: TextView
    private lateinit var videoNoteCircle: FrameLayout
    private lateinit var videoNoteProgress: VideoProgressRingView
    private val dpf get() = resources.displayMetrics.density
    private val CANCEL_THRESHOLD get() = 110f * dpf
    private val LOCK_THRESHOLD get() = 90f * dpf

    companion object {
        private const val TAG = "ChatDetailActivity"
        /** Extra: сразу открыть поиск по чату (кнопка «Поиск» в профиле). */
        const val EXTRA_OPEN_SEARCH = "open_search"
        /**
         * Id чата, на который сейчас реально смотрит пользователь.
         * Выставляется в onResume и сбрасывается в onPause.
         * FCM-сервис проверяет это значение, чтобы не показывать пуш по
         * сообщениям из открытого прямо сейчас чата.
         */
        var currentChatId: String? = null
    }

    // ── Launchers ──────────────────────────────────────────────────────────────
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) Toast.makeText(this, "Фото выбрано!", Toast.LENGTH_SHORT).show()
    }
    private val requestAudioPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }
    private val requestCameraAudioPermLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms[Manifest.permission.CAMERA] != true || perms[Manifest.permission.RECORD_AUDIO] != true)
            Toast.makeText(this, "Нужны права камеры и микрофона", Toast.LENGTH_SHORT).show()
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_chat_detail)

        // Делаем иконки в статус-баре белыми
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        val chatMainLayout = findViewById<LinearLayout>(R.id.chatMainLayout)
        val topBar = findViewById<LinearLayout>(R.id.topBar)

        ViewCompat.setOnApplyWindowInsetsListener(chatMainLayout) { v, insets ->
            val ime  = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Убираем верхний отступ у экрана, оставляем только нижний для клавиатуры
            v.setPadding(0, 0, 0, maxOf(ime.bottom, bars.bottom))

            // Добавляем отступ в саму шапку (запускаем под статус бар)
            val paddingHorizontal = (8 * resources.displayMetrics.density).toInt()
            val paddingTopExtra = (2 * resources.displayMetrics.density).toInt()
            val paddingBottom = (4 * resources.displayMetrics.density).toInt()

            topBar.setPadding(
                paddingHorizontal,
                bars.top + paddingTopExtra,
                paddingHorizontal,
                paddingBottom
            )

            insets
        }

        chatId = intent.getStringExtra("chat_id") ?: run {
            Toast.makeText(this, "Ошибка: ID чата не передан", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        userName      = intent.getStringExtra("chat_name") ?: "Чат"
        myUserId      = TokenManager.getUserId() ?: ""
        restoreUserIdFromTokenIfNeeded()

        initViews()
        setupUI()
        setupRecyclerView()
        setupClickListeners()
        restoreUserIdFromNetworkIfNeeded()
        loadCachedMessages()
        loadMessagesInBackground()
        subscribeToWebSocket()

        if (WebSocketManager.shared.isConnectedValue) {
            WebSocketManager.shared.subscribeToChat(chatId)
            isSubscribed = true
        }
        lifecycleScope.launch {
            WebSocketManager.shared.connectionState.collect { connected ->
                if (connected && !isSubscribed && chatId.isNotEmpty()) {
                    WebSocketManager.shared.subscribeToChat(chatId)
                    isSubscribed = true
                }
            }
        }
        startWebSocketStatusMonitor()

        if (intent.getBooleanExtra(EXTRA_OPEN_SEARCH, false)) openSearch()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_SEARCH, false)) openSearch()
    }

    override fun onResume() {
        super.onResume()
        // Пользователь снова видит этот чат — FCM-сервис не должен показывать
        // пуши по сообщениям из chatId, они и так появятся через WebSocket.
        currentChatId = chatId
        MyFirebaseMessagingService.clearChatNotifications(this, chatId)   // <— добавить эту строку
    }

    override fun onPause() {
        super.onPause()
        // Экран ушёл из foreground (свернули приложение, открыли другой экран
        // и т.п.) — снимаем флаг, пуши снова разрешены.
        if (currentChatId == chatId) currentChatId = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isSubscribed && chatId.isNotEmpty()) {
            WebSocketManager.shared.unsubscribeFromChat(chatId); isSubscribed = false
        }
        stopVoiceRecording(send = false)
        isFlipping = true
        activeVideoRecording?.stop()
        timerJob?.cancel()
        webSocketJob?.cancel()
        connectionCheckJob?.cancel()
    }

    // ── initViews ──────────────────────────────────────────────────────────────

    private fun initViews() {
        recyclerView          = findViewById(R.id.messagesRecyclerView)
        messageInput          = findViewById(R.id.messageInput)
        sendButton            = findViewById(R.id.sendButton)
        micButton             = findViewById(R.id.micButton)
        videoNoteButton       = findViewById(R.id.videoNoteButton)
        attachButton          = findViewById(R.id.attachButton)
        backButton            = findViewById(R.id.backButton)
        chatUserCapsule       = findViewById(R.id.chatUserCapsule)
        emojiButton           = findViewById(R.id.emojiButton)
        emojiPicker           = findViewById(R.id.emojiPicker)
        emojiPanelContainer   = findViewById(R.id.emojiPanelContainer)
        keyboardButton        = findViewById(R.id.keyboardButton)
        chatUserName          = findViewById(R.id.chatUserName)
        chatUserStatus        = findViewById(R.id.chatUserStatus)
        replyBar              = findViewById(R.id.replyBar)
        replyBarSenderName    = findViewById(R.id.replyBarSenderName)
        replyBarText          = findViewById(R.id.replyBarText)
        replyBarClose         = findViewById(R.id.replyBarClose)
        replyBarThumb         = findViewById(R.id.replyBarThumb)
        replyDivider          = findViewById(R.id.replyDivider)
        inputLayout           = findViewById(R.id.inputLayout)
        recordingLayout       = findViewById(R.id.recordingLayout)
        lockedRecordingLayout = findViewById(R.id.lockedRecordingLayout)
        recordingTimer        = findViewById(R.id.recordingTimer)
        lockedTimer           = findViewById(R.id.lockedTimer)
        recordingDot          = findViewById(R.id.recordingDot)
        lockedDot             = findViewById(R.id.lockedDot)
        slideToCancelLayout   = findViewById(R.id.slideToCancelLayout)
        lockLayout            = findViewById(R.id.lockLayout)
        deleteRecordingButton = findViewById(R.id.deleteRecordingButton)
        sendLockedButton      = findViewById(R.id.sendLockedButton)

        pauseRecordingButton  = findViewById(R.id.pauseRecordingButton)
        viewOnceButton        = findViewById(R.id.viewOnceButton)

        videoNoteOverlay      = findViewById(R.id.videoNoteOverlay)
        cameraPreviewView     = findViewById(R.id.cameraPreviewView)
        cameraPreviewViewB    = findViewById(R.id.cameraPreviewViewB)
        // COMPATIBLE = TextureView под капотом. Без этого PreviewView.bitmap
        // часто возвращает null (SurfaceView mode), и блюр-stub не накладывается.
        cameraPreviewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        cameraPreviewViewB.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        videoNoteTimer        = findViewById(R.id.videoNoteTimer)
        videoNoteHint         = findViewById(R.id.videoNoteHint)
        videoNoteRecordButton = findViewById(R.id.videoNoteRecordButton)
        videoNoteRecordInner  = findViewById(R.id.videoNoteRecordInner)
        videoNoteCancelButton = findViewById(R.id.videoNoteCancelButton)
        videoNoteFlipButton   = findViewById(R.id.videoNoteFlipButton)
        contextMenuOverlay    = findViewById(R.id.contextMenuOverlay)
        clonedMessageContainer= findViewById(R.id.clonedMessageContainer)
        reactionsBar          = findViewById(R.id.reactionsBar)
        actionsMenu           = findViewById(R.id.actionsMenu)
        actionReply           = findViewById(R.id.actionReply)
        actionCopy            = findViewById(R.id.actionCopy)
        actionForward         = findViewById(R.id.actionForward)
        actionEdit            = findViewById(R.id.actionEdit)
        actionPin             = findViewById(R.id.actionPin)
        actionDelete          = findViewById(R.id.actionDelete)

        recordOverlay         = findViewById(R.id.recordOverlay)
        recordCircle          = findViewById(R.id.recordCircle)
        lockPill              = findViewById(R.id.lockPill)
        lockArrow             = findViewById(R.id.lockArrow)
        cancelTrash           = findViewById(R.id.cancelTrash)
        cancelMic             = findViewById(R.id.cancelMic)
        slideArrow            = findViewById(R.id.slideArrow)
        videoNoteCircle       = findViewById(R.id.videoNoteCircle)
        videoNoteProgress     = findViewById(R.id.videoNoteProgress)
        recordCircle.setColor(MaterialColors.getColor(recordCircle, R.attr.vibicBrand))

        searchBar             = findViewById(R.id.searchBar)
        searchInput           = findViewById(R.id.searchInput)
        searchClear           = findViewById(R.id.searchClear)
        searchNavBar          = findViewById(R.id.searchNavBar)
        searchCounter         = findViewById(R.id.searchCounter)
        searchUp              = findViewById(R.id.searchUp)
        searchDown            = findViewById(R.id.searchDown)

        contextMenuOverlay.setOnClickListener { hideContextMenu() }
    }

    private fun setupUI() {
        chatUserName.text = userName
        updateConnectionStatus()
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(
            myUserId = myUserId,
            onMessageLongClick = { message, anchorView ->
                showMessageActionsPopup(message, anchorView)
            },
            onViewOncePlayed = { message ->
                deleteMessage(message)
                Toast.makeText(this@ChatDetailActivity, "Одноразовое сообщение удалено", Toast.LENGTH_SHORT).show()
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recyclerView.adapter = adapter

        // Поле ввода плавает над списком: снизу у списка отступ = высота нижней зоны + 12dp,
        // чтобы последнее сообщение не пряталось под капсулой (и при росте до 5 строк).
        val bottomArea = findViewById<View>(R.id.bottomArea)
        bottomArea.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            val h = bottom - top
            if (h == oldBottom - oldTop && recyclerView.paddingBottom != 0) return@addOnLayoutChangeListener
            val atEnd = !recyclerView.canScrollVertically(1)
            val pad = h + (12 * resources.displayMetrics.density).toInt()
            if (recyclerView.paddingBottom != pad) {
                recyclerView.setPadding(recyclerView.paddingLeft, recyclerView.paddingTop, recyclerView.paddingRight, pad)
                if (atEnd && adapter.itemCount > 0) recyclerView.post { recyclerView.scrollToPosition(adapter.itemCount - 1) }
            }
        }
    }

    // ── Click listeners ────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setupClickListeners() {
        backButton.setOnClickListener { if (isSearchMode) closeSearch() else finish() }
        setupSearch()
        attachButton.setOnClickListener { openGallery() }
        replyBarClose.setOnClickListener { clearReply() }

        messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val hasText = !s.isNullOrEmpty()
                sendButton.visibility = if (hasText) View.VISIBLE else View.GONE
                micButton.visibility = if (hasText) View.GONE else View.VISIBLE
                videoNoteButton.visibility = if (hasText) View.GONE else View.VISIBLE
            }
        })

        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty()) sendMessage(text)
        }
        messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendButton.performClick(); true
            } else false
        }

        // Две кнопки: удержание микрофона — голосовое, удержание кружка — видеосообщение
        micButton.setOnTouchListener { _, event -> handleRecordTouch(micButton, RecMode.VOICE, event); true }
        videoNoteButton.setOnTouchListener { _, event -> handleRecordTouch(videoNoteButton, RecMode.VIDEO, event); true }
        deleteRecordingButton.setOnClickListener { cancelRecording() }
        sendLockedButton.setOnClickListener { finishRecording(send = true) }

        // Кнопка Паузы/Продолжения ГС
        pauseRecordingButton.setOnClickListener {
            if (isRecordingPaused) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    mediaRecorder?.resume()
                }
                isRecordingPaused = false
                pauseRecordingButton.setImageResource(android.R.drawable.ic_media_pause)
                dotAnimator?.resume()
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    mediaRecorder?.pause()
                }
                isRecordingPaused = true
                pauseRecordingButton.setImageResource(android.R.drawable.ic_media_play)
                dotAnimator?.pause()
            }
        }

        // Кнопка Одноразового ГС "1x"
        viewOnceButton.setOnClickListener {
            isVoiceViewOnce = !isVoiceViewOnce
            viewOnceButton.alpha = if (isVoiceViewOnce) 1.0f else 0.4f
        }

        videoNoteCancelButton.setOnClickListener {
            isFlipping = true; activeVideoRecording?.stop(); activeVideoRecording = null
            isVideoRecording = false; videoFile?.delete(); videoFile = null
            isFlipping = false; closeVideoNoteOverlay()
        }

        videoNoteFlipButton.setOnClickListener {
            if (isFlipping) return@setOnClickListener
            val wasRecording = isVideoRecording
            isFlipping = true

            // Снимаем bitmap СЕЙЧАС, пока preview ещё стабильно стримит, с тройной страховкой.
            val frozenBitmap = captureCameraPreviewBitmap()
            Log.d(TAG, "flip click: bitmap=${frozenBitmap?.width}x${frozenBitmap?.height}")

            if (wasRecording) {
                // Останавливаем запись и ждём Finalize, и только потом флипаем.
                isVideoRecording = false
                val rec = activeVideoRecording
                activeVideoRecording = null
                val staleFile = videoFile
                videoFile = null

                pendingFlipAction = {
                    staleFile?.delete()
                    flipCamera(frozenBitmap) {
                        isFlipping = false
                        if (videoCapture != null) startCameraRecording()
                    }
                }
                rec?.stop() // Finalize прилетит асинхронно → сработает pendingFlipAction
            } else {
                flipCamera(frozenBitmap) { isFlipping = false }
            }
        }

        videoNoteRecordButton.setOnClickListener {
            if (isVideoRecording) {
                isFlipping = false; isVideoRecording = false
                activeVideoRecording?.stop(); activeVideoRecording = null
                val file = videoFile; videoFile = null
                closeVideoNoteOverlay()
                if (file != null && file.exists() && file.length() > 500) uploadAndSendVideoNote(
                    file
                )
                else file?.delete()
            } else {
                startRecordingWithTimer()
            }
        }

        emojiButton.setOnClickListener {
            if (emojiPanelContainer.visibility == View.VISIBLE) {
                hideEmojiPanelAndShowKeyboard()
            } else {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(messageInput.windowToken, 0)
                it.postDelayed({
                    emojiPanelContainer.visibility = View.VISIBLE
                    if (adapter.itemCount > 0) recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
                }, 150)
            }
        }
        keyboardButton.setOnClickListener { hideEmojiPanelAndShowKeyboard() }
        messageInput.setOnClickListener {
            if (emojiPanelContainer.visibility == View.VISIBLE) emojiPanelContainer.visibility =
                View.GONE
        }
        emojiPicker.setOnEmojiPickedListener { messageInput.append(it.emoji) }

        chatUserCapsule.setOnClickListener {
            // Пока тип неизвестен — открываем по факту ответа сервера.
            lifecycleScope.launch {
                val type = try {
                    ApiClient.apiService.getChat(chatId).body()?.type ?: "private"
                } catch (_: Exception) {
                    "private"
                }
                val target = if (type == "group")
                    GroupProfileActivity::class.java
                else
                    ContactProfileActivity::class.java

                startActivity(Intent(this@ChatDetailActivity, target).apply {
                    putExtra("chat_id", chatId)
                    putExtra("chat_name", userName)
                })
            }
        }
    }

    // ── Reply ──────────────────────────────────────────────────────────────────

    private fun setReply(message: MessageDto) {
        replyToMessage = message
        val senderName = when {
            message.sender?.firstName != null ->
                "Ответить ${message.sender.firstName} ${message.sender.lastName ?: ""}".trim()
            message.senderId == myUserId -> "Ответить себе"
            else -> "Ответить"
        }
        replyBarSenderName.text = senderName
        replyBarText.text = when (message.type) {
            "audio", "audio_once" -> "🎤 Голосовое сообщение"
            "video_note" -> "📹 Видеосообщение"
            else         -> message.content ?: ""
        }
        replyBarThumb.visibility = View.GONE
        replyBar.visibility     = View.VISIBLE
        replyDivider.visibility = View.VISIBLE
        messageInput.requestFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(messageInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun clearReply() {
        replyToMessage = null
        replyBar.visibility     = View.GONE
        replyDivider.visibility = View.GONE
    }

    // ── Video Note ─────────────────────────────────────────────────────────────

    private fun openVideoNoteOverlay() {
        emojiPanelContainer.visibility = View.GONE
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(messageInput.windowToken, 0)
        messageInput.clearFocus()
        videoNoteOverlay.postDelayed({
            videoNoteOverlay.alpha = 0f; videoNoteOverlay.visibility = View.VISIBLE
            videoNoteOverlay.animate().alpha(1f).setDuration(200).start()
            videoNoteTimer.visibility = View.INVISIBLE; videoNoteHint.visibility = View.VISIBLE
            videoNoteRecordInner.setBackgroundResource(R.drawable.bg_record_button_inner)
            startCameraPreview()
            startFrameGrabber()
        }, 200)
    }

    /** Постоянно (каждые 200мс) пытается снять кадр PreviewView и кэширует его в lastPreviewFrame. */
    private fun startFrameGrabber() {
        frameGrabberJob?.cancel()
        frameGrabberJob = lifecycleScope.launch {
            while (true) {
                delay(200)
                val view = if (isPreviewAActive) cameraPreviewView else cameraPreviewViewB
                val bmp = try { view.bitmap } catch (_: Exception) { null }
                if (bmp != null) {
                    lastPreviewFrame = bmp
                }
            }
        }
    }

    private fun stopFrameGrabber() {
        frameGrabberJob?.cancel()
        frameGrabberJob = null
        lastPreviewFrame = null
    }

    private fun closeVideoNoteOverlay() {
        timerJob?.cancel(); timerJob = null; timerStartMs = 0L
        pendingFlipAction = null
        isFlipping = false
        stopFrameGrabber()
        try { ProcessCameraProvider.getInstance(this).get().unbindAll() } catch (_: Exception) {}
        videoCapture = null
        videoNoteOverlay.animate().alpha(0f).setDuration(150).withEndAction {
            videoNoteOverlay.visibility = View.GONE
            videoNoteTimer.text = "0:00.0"
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        }.start()
    }

    /**
     * Привязывает камеру к указанному PreviewView. Делает unbindAll перед привязкой
     * (CameraX не разрешает два Preview одновременно на одном lifecycle).
     * onFirstFrame вызывается ОДИН РАЗ, когда новая камера прислала первый кадр.
     *
     * ВАЖНО: observer streamState подписывается ДО unbindAll/bindToLifecycle —
     * иначе при быстром старте можно пропустить переход в STREAMING.
     */
    private fun startCameraPreviewOn(targetView: PreviewView, onFirstFrame: (() -> Unit)? = null) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                targetView.scaleType = PreviewView.ScaleType.FILL_CENTER
                val previewBuilder = Preview.Builder()
                Camera2Interop.Extender(previewBuilder).setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, AndroidRange(15, 30))
                val preview = previewBuilder.build().also { it.setSurfaceProvider(targetView.surfaceProvider) }
                val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.SD)).build()
                val newCapture = VideoCapture.withOutput(recorder)

                // Подписываемся на streamState ДО bind — чтобы поймать первый переход в STREAMING.
                // При флипе важно: текущее состояние может быть STREAMING (от старой камеры),
                // его игнорируем, ждём IDLE → STREAMING нового цикла.
                if (onFirstFrame != null) {
                    val initialState = targetView.previewStreamState.value
                    var sawIdle = (initialState != PreviewView.StreamState.STREAMING)
                    val observer = object : androidx.lifecycle.Observer<PreviewView.StreamState> {
                        override fun onChanged(value: PreviewView.StreamState) {
                            if (value == PreviewView.StreamState.IDLE) sawIdle = true
                            if (value == PreviewView.StreamState.STREAMING && sawIdle) {
                                targetView.previewStreamState.removeObserver(this)
                                onFirstFrame.invoke()
                            }
                        }
                    }
                    targetView.previewStreamState.observe(this, observer)
                }

                provider.unbindAll()
                val camera = provider.bindToLifecycle(this, cameraSelector, preview, newCapture)
                videoCapture = newCapture
                camera.cameraInfo.exposureState.let { es ->
                    if (es.isExposureCompensationSupported)
                        camera.cameraControl.setExposureCompensationIndex(
                            (es.exposureCompensationRange.lower + es.exposureCompensationRange.upper) / 2)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка камеры", e)
                Toast.makeText(this, "Ошибка камеры", Toast.LENGTH_SHORT).show()
                isFlipping = false
                pendingFlipAction = null
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** Первый запуск — отвязывает всё и привязывает камеру к активному preview. */
    private fun startCameraPreview() {
        try { ProcessCameraProvider.getInstance(this).get().unbindAll() } catch (_: Exception) {}
        videoCapture = null
        isPreviewAActive = true
        cameraPreviewView.visibility = View.VISIBLE
        cameraPreviewView.alpha = 1f
        cameraPreviewViewB.visibility = View.INVISIBLE
        cameraPreviewViewB.alpha = 0f
        startCameraPreviewOn(cameraPreviewView)
    }

    /**
     * Telegram-style переключение камеры через blurred stub.
     * 1) Снимаем bitmap текущего preview и блюрим его.
     * 2) Кладём blurred ImageView поверх PreviewView (внутри CardView).
     * 3) unbind + rebind на тот же PreviewView с новой камерой.
     * 4) Ждём первый кадр новой камеры → fade-out blurred stub.
     *
     * Никакого 3D-поворота, никакого раздвоения. Если bitmap не успел сняться —
     * просто без stub'а (короткая чернота, но это бывает только на самом первом флипе).
     */
    /**
     * Снимает bitmap текущего PreviewView с тройной страховкой:
     * 1) Через PreviewView.bitmap (работает в COMPATIBLE-режиме).
     * 2) Через поиск TextureView в дереве и getBitmap() напрямую.
     * 3) Через рисование на Canvas вручную (работает всегда, но может быть прозрачным
     *    если поверх Surface ничего не нарисовано).
     */
    private fun captureCameraPreviewBitmap(): android.graphics.Bitmap? {
        // Способ 1: стандартный API
        cameraPreviewView.bitmap?.let {
            Log.d(TAG, "capture: способ 1 (PreviewView.bitmap) сработал")
            return it
        }

        // Способ 2: ищем TextureView внутри PreviewView
        findTextureView(cameraPreviewView)?.let { tv ->
            if (tv.isAvailable) {
                tv.bitmap?.let {
                    Log.d(TAG, "capture: способ 2 (TextureView.bitmap) сработал")
                    return it
                }
            }
        }

        // Способ 3: рисуем сам PreviewView на Canvas
        return try {
            val w = cameraPreviewView.width
            val h = cameraPreviewView.height
            if (w > 0 && h > 0) {
                val out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(out)
                cameraPreviewView.draw(canvas)
                Log.d(TAG, "capture: способ 3 (View.draw) сработал")
                out
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "capture: все способы провалились", e)
            null
        }
    }

    /** Рекурсивно ищет TextureView внутри view-дерева. */
    private fun findTextureView(view: View): android.view.TextureView? {
        if (view is android.view.TextureView) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                findTextureView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    /**
     * Double-buffer флип камеры через два PreviewView (A и B).
     *
     * Пока активный (A) продолжает крутить старую камеру, новую биндим на
     * неактивный (B) — он invisible, пользователь его не видит.
     * Когда B прислал первый кадр (STREAMING) — мгновенно свапаем alpha/visibility
     * в одном кадре композитора. Чёрного просвета между unbind и первым кадром
     * новой камеры пользователь физически не видит, потому что unbind/bind
     * происходят на скрытом view.
     *
     * Параметр frozenBitmap оставлен для обратной совместимости — больше не нужен.
     */
    private fun flipCamera(frozenBitmap: android.graphics.Bitmap?, onDone: (() -> Unit)? = null) {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
            CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA

        val activeView   = if (isPreviewAActive) cameraPreviewView  else cameraPreviewViewB
        val inactiveView = if (isPreviewAActive) cameraPreviewViewB else cameraPreviewView

        // Готовим неактивный: layout'нутый, но прозрачный
        inactiveView.alpha = 0f
        inactiveView.visibility = View.VISIBLE

        // Биндим новую камеру на неактивный preview. Активный продолжает
        // рисовать старую картинку до момента, пока новая не пришлёт первый кадр.
        startCameraPreviewOn(inactiveView) {
            // Первый кадр новой камеры готов — атомарный свап в одном frame composer'е
            inactiveView.alpha = 1f
            activeView.alpha = 0f
            activeView.visibility = View.INVISIBLE
            isPreviewAActive = !isPreviewAActive
            onDone?.invoke()
        }
    }

    /**
     * Быстрый блюр bitmap. На API 31+ возможно использовать RenderEffect (но он работает только
     * на View, не на Bitmap), поэтому делаем универсальный stack-blur по даунскейленному bitmap.
     * Даунскейл x4 → блюр на уменьшенной → upscale обратно. Очень быстро (<10мс на ~260dp).
     */
    private fun blurBitmap(src: android.graphics.Bitmap, radius: Float = 20f): android.graphics.Bitmap {
        val scale = 0.25f
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val scaled = android.graphics.Bitmap.createScaledBitmap(src, w, h, true)
        return try {
            stackBlur(scaled, radius.toInt().coerceAtLeast(1))
        } catch (_: Throwable) {
            scaled
        }
    }

    /** Stack-blur — быстрый, без RenderScript, чистый Kotlin. */
    private fun stackBlur(bitmap: android.graphics.Bitmap, radius: Int): android.graphics.Bitmap {
        if (radius < 1) return bitmap
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1
        val r = IntArray(wh); val g = IntArray(wh); val b = IntArray(wh)
        var rsum: Int; var gsum: Int; var bsum: Int
        var x: Int; var y: Int; var i: Int; var p: Int; var yp: Int; var yi: Int; var yw = 0
        val vmin = IntArray(maxOf(w, h))
        val dv = IntArray(256 * div)
        i = 0
        while (i < 256 * div) { dv[i] = i / div; i++ }

        yi = 0; y = 0
        while (y < h) {
            rsum = 0; gsum = 0; bsum = 0
            i = -radius
            while (i <= radius) {
                p = pixels[yi + minOf(wm, maxOf(i, 0))]
                rsum += (p shr 16) and 0xff
                gsum += (p shr 8) and 0xff
                bsum += p and 0xff
                i++
            }
            x = 0
            while (x < w) {
                r[yi] = dv[rsum]; g[yi] = dv[gsum]; b[yi] = dv[bsum]
                if (y == 0) vmin[x] = minOf(x + radius + 1, wm)
                val p1 = pixels[yw + vmin[x]]
                val p2 = pixels[yw + maxOf(x - radius, 0)]
                rsum += ((p1 shr 16) and 0xff) - ((p2 shr 16) and 0xff)
                gsum += ((p1 shr 8) and 0xff) - ((p2 shr 8) and 0xff)
                bsum += (p1 and 0xff) - (p2 and 0xff)
                yi++; x++
            }
            yw += w; y++
        }

        x = 0
        while (x < w) {
            rsum = 0; gsum = 0; bsum = 0
            yp = -radius * w
            i = -radius
            while (i <= radius) {
                yi = maxOf(0, yp) + x
                rsum += r[yi]; gsum += g[yi]; bsum += b[yi]
                yp += w; i++
            }
            yi = x
            y = 0
            while (y < h) {
                pixels[yi] = (0xff shl 24) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                if (x == 0) vmin[y] = minOf(y + radius + 1, hm) * w
                p = x + vmin[y]
                rsum += r[p] - r[maxOf(y - radius, 0) * w + x]
                gsum += g[p] - g[maxOf(y - radius, 0) * w + x]
                bsum += b[p] - b[maxOf(y - radius, 0) * w + x]
                yi += w; y++
            }
            x++
        }

        val out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun startRecordingWithTimer() {
        timerStartMs = System.currentTimeMillis(); timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            while (true) {
                val elapsed = System.currentTimeMillis() - timerStartMs
                val secs = (elapsed / 1000).toInt(); val tenths = ((elapsed % 1000) / 100).toInt()
                videoNoteTimer.text = "%d:%02d.%d".format(secs / 60, secs % 60, tenths)
                if (elapsed >= 60_000L) {
                    isVideoRecording = false; activeVideoRecording?.stop(); activeVideoRecording = null
                    val file = videoFile; videoFile = null; closeVideoNoteOverlay()
                    if (file != null && file.exists() && file.length() > 500) uploadAndSendVideoNote(file) else file?.delete()
                    break
                }
                delay(100)
            }
        }
        videoNoteHint.visibility = View.GONE; videoNoteTimer.visibility = View.VISIBLE
        videoNoteRecordInner.setBackgroundResource(R.drawable.bg_recording_dot)
        videoNoteRecordButton.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        startCameraRecording()
    }

    @SuppressLint("MissingPermission")
    private fun startCameraRecording() {
        val vc = videoCapture ?: run {
            Toast.makeText(this, "Камера не готова", Toast.LENGTH_SHORT).show()
            isFlipping = false
            return
        }
        val file = File(filesDir, "videonote_${System.currentTimeMillis()}.mp4").also { videoFile = it }
        val fileSnapshot = file
        activeVideoRecording = vc.output.prepareRecording(this, FileOutputOptions.Builder(file).build())
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(this)) { event ->
                if (event !is VideoRecordEvent.Finalize) return@start
                // 1) Если запись остановлена ради флипа — выполняем отложенное действие
                val pending = pendingFlipAction
                if (pending != null) {
                    pendingFlipAction = null
                    pending.invoke()
                    return@start
                }
                val finalizeAction = videoFinalizeAction
                if (finalizeAction != null) {
                    videoFinalizeAction = null
                    finalizeAction(event.hasError(), fileSnapshot)
                    return@start
                }
                // 2) Обычная обработка ошибки
                if (event.hasError()) {
                    Log.e(TAG, "Ошибка записи: ${event.error}")
                    if (videoFile === fileSnapshot) {
                        videoFile?.delete(); videoFile = null
                    } else {
                        fileSnapshot.delete()
                    }
                    runOnUiThread {
                        Toast.makeText(this, "Ошибка записи", Toast.LENGTH_SHORT).show()
                        isVideoRecording = false
                        closeVideoNoteOverlay()
                    }
                }
            }
        isVideoRecording = true
    }

    // ── Voice recording ────────────────────────────────────────────────────────

    /**
     * Кнопки записи как в Telegram: удержание — запись,
     * влево — отмена, вверх — замок. Короткий тап — подсказка.
     */
    private fun handleRecordTouch(button: ImageView, mode: RecMode, event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isRecordingActive()) return
                activeRecButton = button
                recMode = mode
                touchStartX = event.rawX; touchStartY = event.rawY
                holdStarted = false
                button.animate().scaleX(0.85f).scaleY(0.85f).setDuration(120).start()
                button.postDelayed(holdRunnable, 180)
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeRecButton !== button) return
                if (!holdStarted || isLocked || !isRecordingActive()) return
                onHoldMove(event.rawX - touchStartX, event.rawY - touchStartY)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeRecButton !== button) return
                button.removeCallbacks(holdRunnable)
                button.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                if (!holdStarted) {
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        val hint = if (mode == RecMode.VOICE) "Удерживайте, чтобы записать голосовое"
                                   else "Удерживайте, чтобы записать кружок"
                        Toast.makeText(this, hint, Toast.LENGTH_SHORT).show()
                    }
                    return
                }
                if (isLocked || !isRecordingActive()) return
                finishRecording(send = true)
            }
        }
    }

    private fun isRecordingActive() = isVoiceRecording || isVideoHold

    private fun onHoldStart() {
        holdStarted = true
        if (recMode == RecMode.VOICE) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestAudioPermLauncher.launch(Manifest.permission.RECORD_AUDIO); return
            }
            computeCircleHome()
            micButton.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            startVoiceRecording()
            if (!isVoiceRecording) return
            showRecordUi(R.drawable.ic_composer_mic)
            startAmplitudeUpdates()
        } else {
            val camOk = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            val audioOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (!camOk || !audioOk) {
                requestCameraAudioPermLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                return
            }
            computeCircleHome()
            micButton.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            startVideoHold()
            showRecordUi(R.drawable.ic_composer_video_note)
            recordCircle.setAmplitude(0.15f)
        }
    }

    /** Центр кнопки записи в координатах слоя анимаций. */
    private fun computeCircleHome() {
        val btn = activeRecButton ?: micButton
        val b = IntArray(2); btn.getLocationInWindow(b)
        val o = IntArray(2); recordOverlay.getLocationInWindow(o)
        circleHomeX = b[0] - o[0] + btn.width / 2f
        circleHomeY = b[1] - o[1] + btn.height / 2f
    }

    private fun placeCircle(offX: Float, offY: Float) {
        val half = 90f * dpf
        recordCircle.translationX = circleHomeX + offX - half
        recordCircle.translationY = circleHomeY + offY - half
    }

    /** Круг под пальцем + замок сверху + бегущая стрелка «влево». */
    private fun showRecordUi(iconRes: Int) {
        recordCircle.animate().cancel()
        recordCircle.resetAmplitude()
        recordCircle.setIcon(iconRes, animate = false)
        recordCircle.isClickable = false
        recordCircle.setOnClickListener(null)
        placeCircle(0f, 0f)
        recordCircle.scaleX = 0.2f; recordCircle.scaleY = 0.2f; recordCircle.alpha = 1f
        recordCircle.visibility = View.VISIBLE
        recordCircle.animate().scaleX(1f).scaleY(1f).setDuration(260)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.6f)).start()

        lockPillBaseY = circleHomeY - recordCircle.baseRadius - 28f * dpf - 72f * dpf
        lockPill.translationX = circleHomeX - 20f * dpf
        lockPill.translationY = lockPillBaseY + 40f * dpf
        lockPill.alpha = 0f; lockPill.scaleX = 1f; lockPill.scaleY = 1f
        lockPill.visibility = View.VISIBLE
        lockArrow.alpha = 1f
        lockPill.animate().translationY(lockPillBaseY).alpha(1f).setStartDelay(120).setDuration(220)
            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()

        lockArrowAnim?.cancel()
        lockArrowAnim = ObjectAnimator.ofFloat(lockArrow, "translationY", 0f, -5f * dpf).apply {
            duration = 600; repeatCount = ObjectAnimator.INFINITE; repeatMode = ObjectAnimator.REVERSE; start()
        }
        slideArrowAnim?.cancel()
        slideArrowAnim = ObjectAnimator.ofFloat(slideArrow, "translationX", 0f, -6f * dpf).apply {
            duration = 700; repeatCount = ObjectAnimator.INFINITE; repeatMode = ObjectAnimator.REVERSE; start()
        }
    }

    private fun hideRecordUi(shrinkToHome: Boolean) {
        ampJob?.cancel(); ampJob = null
        lockArrowAnim?.cancel(); lockArrowAnim = null
        slideArrowAnim?.cancel(); slideArrowAnim = null
        slideArrow.translationX = 0f
        recordCircle.isClickable = false
        recordCircle.setOnClickListener(null)
        recordCircle.animate().cancel()
        if (shrinkToHome) placeCircle(0f, 0f)
        recordCircle.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(180)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction { recordCircle.visibility = View.GONE }.start()
        lockPill.animate().cancel()
        lockPill.animate().alpha(0f).translationY(lockPillBaseY + 30f * dpf).setStartDelay(0).setDuration(150)
            .withEndAction { lockPill.visibility = View.GONE }.start()
    }

    /** Палец ведёт круг: влево — к отмене, вверх — к замку (по доминирующей оси). */
    private fun onHoldMove(dx: Float, dy: Float) {
        val cdx = dx.coerceAtMost(0f)
        val cdy = dy.coerceAtMost(0f)
        val vertical = -cdy > -cdx
        val offX = if (vertical) 0f else cdx
        val offY = if (vertical) cdy else 0f
        placeCircle(offX, offY)

        slideToCancelLayout.translationX = offX * 0.5f
        slideToCancelLayout.alpha = (1f + offX / CANCEL_THRESHOLD).coerceIn(0f, 1f)

        val lockProgress = (-offY / LOCK_THRESHOLD).coerceIn(0f, 1f)
        lockPill.translationY = lockPillBaseY + offY * 0.4f
        lockArrow.alpha = 1f - lockProgress

        when {
            offX < -CANCEL_THRESHOLD -> cancelRecording()
            offY < -LOCK_THRESHOLD   -> lockRecording()
        }
    }

    /** Громкость микрофона → размер волн вокруг круга. */
    private fun startAmplitudeUpdates() {
        ampJob?.cancel()
        ampJob = lifecycleScope.launch {
            while (isVoiceRecording) {
                val a = if (isRecordingPaused) 0 else try { mediaRecorder?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }
                recordCircle.setAmplitude(kotlin.math.sqrt((a / 16000f).coerceIn(0f, 1f)))
                delay(70)
            }
        }
    }

    /** Нижняя панель записи: точка, таймер, «Влево — отмена». Выезжает справа. */
    private fun showRecordingPanel(video: Boolean) {
        inputLayout.visibility = View.GONE
        lockedRecordingLayout.visibility = View.GONE
        recordingLayout.visibility = View.VISIBLE
        recordingLayout.alpha = 0f; recordingLayout.translationX = 40f * dpf
        recordingLayout.animate().alpha(1f).translationX(0f).setDuration(180).start()
        slideToCancelLayout.alpha = 1f; slideToCancelLayout.translationX = 0f
        lockLayout.visibility = View.GONE
        recordingTimer.alpha = 1f; recordingDot.alpha = 1f
        recordingTimer.text = "0:00"; lockedTimer.text = "0:00"
        viewOnceButton.visibility = if (video) View.GONE else View.VISIBLE
        pauseRecordingButton.visibility = if (video) View.GONE else View.VISIBLE
        dotAnimator?.cancel()
        dotAnimator = ObjectAnimator.ofFloat(recordingDot, "alpha", 1f, 0f).apply {
            duration = 500; repeatCount = ObjectAnimator.INFINITE; repeatMode = ObjectAnimator.REVERSE; start()
        }
    }

    /** Замок: запись продолжается без пальца, круг становится кнопкой «Отправить». */
    private fun lockRecording() {
        if (isLocked || !isRecordingActive()) return
        isLocked = true
        micButton.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)

        recordingLayout.visibility = View.GONE
        lockedRecordingLayout.visibility = View.VISIBLE
        lockedRecordingLayout.alpha = 0f
        lockedRecordingLayout.animate().alpha(1f).setDuration(150).start()
        sendLockedButton.visibility = View.INVISIBLE
        dotAnimator?.cancel()
        dotAnimator = ObjectAnimator.ofFloat(lockedDot, "alpha", 1f, 0f).apply {
            duration = 500; repeatCount = ObjectAnimator.INFINITE; repeatMode = ObjectAnimator.REVERSE; start()
        }

        lockArrowAnim?.cancel(); lockArrowAnim = null
        lockPill.animate().cancel()
        lockPill.animate().scaleX(1.15f).scaleY(1.15f).setDuration(110).withEndAction {
            lockPill.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).translationY(lockPillBaseY + 30f * dpf)
                .setDuration(180).withEndAction { lockPill.visibility = View.GONE }.start()
        }.start()

        recordCircle.animate().translationX(circleHomeX - 90f * dpf).translationY(circleHomeY - 90f * dpf)
            .setDuration(200).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        recordCircle.setIcon(R.drawable.ic_send, animate = true)
        recordCircle.isClickable = true
        recordCircle.setOnClickListener { finishRecording(send = true) }
    }

    /** Отпустили палец / нажали «Отправить» в замке. */
    private fun finishRecording(send: Boolean) {
        if (!isRecordingActive()) return
        hideRecordUi(shrinkToHome = true)
        if (isVideoHold) {
            finishVideoHold(send)
            showNormalInput()
        } else {
            stopVoiceRecording(send)
        }
    }

    /** Отмена: круг исчезает, микрофон «падает» в корзину. */
    private fun cancelRecording() {
        if (!isRecordingActive()) return
        micButton.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        val dot = if (isLocked) lockedDot else recordingDot
        val d = IntArray(2); dot.getLocationInWindow(d)
        val o = IntArray(2); recordOverlay.getLocationInWindow(o)
        val x = d[0] - o[0] + dot.width / 2f
        val y = d[1] - o[1] + dot.height / 2f

        hideRecordUi(shrinkToHome = false)
        if (isVideoHold) finishVideoHold(send = false)
        else stopVoiceRecording(send = false, restoreInput = false)

        // Гасим содержимое панели, оставляем место под корзину
        dotAnimator?.cancel()
        listOf<View>(recordingTimer, slideToCancelLayout, recordingDot, lockedTimer, lockedDot).forEach {
            it.animate().alpha(0f).setDuration(150).start()
        }
        playTrashAnimation(x, y) { showNormalInput() }
    }

    private fun playTrashAnimation(x: Float, y: Float, onEnd: () -> Unit) {
        val half = 12f * dpf
        cancelTrash.apply {
            animate().cancel()
            translationX = x - half; translationY = y - half
            scaleX = 0f; scaleY = 0f; alpha = 1f; rotation = 0f; visibility = View.VISIBLE
        }
        cancelMic.apply {
            animate().cancel()
            translationX = x - half; translationY = y - half
            rotation = 0f; alpha = 1f; scaleX = 1f; scaleY = 1f; visibility = View.VISIBLE
        }
        cancelMic.animate().translationY(y - half - 70f * dpf).rotation(180f).setDuration(280)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                cancelTrash.animate().scaleX(1f).scaleY(1f).setDuration(160)
                    .setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
                cancelMic.animate().translationY(y - half).scaleX(0.6f).scaleY(0.6f).setDuration(260)
                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                    .withEndAction {
                        cancelMic.visibility = View.GONE
                        cancelTrash.animate().rotation(-12f).setDuration(70).withEndAction {
                            cancelTrash.animate().rotation(12f).setDuration(70).withEndAction {
                                cancelTrash.animate().rotation(0f).scaleX(0f).scaleY(0f).setDuration(180)
                                    .setStartDelay(80).withEndAction {
                                        cancelTrash.visibility = View.GONE
                                        cancelTrash.animate().setStartDelay(0)
                                        onEnd()
                                    }.start()
                            }.start()
                        }.start()
                    }.start()
            }.start()
    }

    // ── Кружок (удержание) ─────────────────────────────────────────────────────

    /** Кружок «вырастает» из кнопки в центр экрана, вокруг — кольцо прогресса 60 сек. */
    private fun startVideoHold() {
        isVideoHold = true
        isLocked = false
        emojiPanelContainer.visibility = View.GONE
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(messageInput.windowToken, 0)
        messageInput.clearFocus()

        videoNoteTimer.visibility = View.GONE
        videoNoteHint.visibility = View.GONE
        videoNoteCancelButton.visibility = View.GONE
        videoNoteRecordButton.visibility = View.GONE
        videoNoteFlipButton.visibility = View.VISIBLE
        videoNoteProgress.progress = 0f
        videoNoteProgress.setProgressColor(MaterialColors.getColor(videoNoteProgress, R.attr.vibicBrand))

        // Затемнение — только над нижней панелью, чтобы «Удалить» в замке нажимался
        val bottomArea = recordingLayout.parent as View
        val ba = IntArray(2); bottomArea.getLocationInWindow(ba)
        val root = findViewById<View>(R.id.rootLayout)
        val rl = IntArray(2); root.getLocationInWindow(rl)
        (videoNoteOverlay.layoutParams as FrameLayout.LayoutParams).bottomMargin =
            (root.height - (ba[1] - rl[1])).coerceAtLeast(0)
        videoNoteOverlay.requestLayout()

        videoNoteOverlay.alpha = 0f
        videoNoteOverlay.visibility = View.VISIBLE
        videoNoteOverlay.animate().alpha(1f).setDuration(220).start()
        videoNoteCircle.alpha = 0f
        videoNoteCircle.post {
            val (dx, dy) = videoCircleOffsetToButton()
            videoNoteCircle.translationX = dx; videoNoteCircle.translationY = dy
            videoNoteCircle.scaleX = 0.15f; videoNoteCircle.scaleY = 0.15f
            videoNoteCircle.alpha = 1f
            videoNoteCircle.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(320).setInterpolator(android.view.animation.OvershootInterpolator(0.9f)).start()
        }
        videoNoteFlipButton.alpha = 0f
        videoNoteFlipButton.animate().alpha(1f).setStartDelay(200).setDuration(200).start()

        startCameraPreview()
        startFrameGrabber()
        showRecordingPanel(video = true)

        videoHoldJob?.cancel()
        videoHoldJob = lifecycleScope.launch {
            var waited = 0
            while (videoCapture == null && waited < 3000 && isVideoHold) { delay(50); waited += 50 }
            if (!isVideoHold) return@launch
            if (videoCapture == null) {
                Toast.makeText(this@ChatDetailActivity, "Камера не готова", Toast.LENGTH_SHORT).show()
                cancelRecording(); return@launch
            }
            startCameraRecording()
            val start = System.currentTimeMillis()
            while (isVideoHold) {
                val elapsed = System.currentTimeMillis() - start
                val secs = (elapsed / 1000).toInt()
                val t = "%d:%02d".format(secs / 60, secs % 60)
                recordingTimer.text = t; lockedTimer.text = t
                videoNoteProgress.progress = elapsed / 60_000f
                if (elapsed >= 60_000L) { finishRecording(send = true); break }
                delay(50)
            }
        }
    }

    /** Смещение центра кружка к центру кнопки записи. */
    private fun videoCircleOffsetToButton(): Pair<Float, Float> {
        val c = IntArray(2); videoNoteCircle.getLocationInWindow(c)
        val o = IntArray(2); recordOverlay.getLocationInWindow(o)
        val cx = c[0] - o[0] - videoNoteCircle.translationX + videoNoteCircle.width / 2f
        val cy = c[1] - o[1] - videoNoteCircle.translationY + videoNoteCircle.height / 2f
        return Pair(circleHomeX - cx, circleHomeY - cy)
    }

    /** Остановка кружка: отправка — улетает в кнопку, отмена — схлопывается на месте. */
    private fun finishVideoHold(send: Boolean) {
        if (!isVideoHold) return
        isVideoHold = false
        isLocked = false
        videoHoldJob?.cancel(); videoHoldJob = null
        val wasRecording = isVideoRecording
        isVideoRecording = false
        val rec = activeVideoRecording
        activeVideoRecording = null
        videoFile = null
        if (wasRecording && rec != null) {
            videoFinalizeAction = { error, file ->
                if (send && !error && file.exists() && file.length() > 500) uploadAndSendVideoNote(file)
                else file.delete()
            }
            rec.stop()
        }

        videoNoteFlipButton.animate().alpha(0f).setStartDelay(0).setDuration(120).start()
        val (dx, dy) = if (send) videoCircleOffsetToButton() else Pair(0f, 0f)
        videoNoteCircle.animate().cancel()
        videoNoteCircle.animate().translationX(dx).translationY(dy).scaleX(0.1f).scaleY(0.1f).alpha(0f)
            .setDuration(240).setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                closeVideoNoteOverlay()
                (videoNoteOverlay.layoutParams as FrameLayout.LayoutParams).bottomMargin = 0
                videoNoteOverlay.requestLayout()
                videoNoteCircle.translationX = 0f; videoNoteCircle.translationY = 0f
                videoNoteCircle.scaleX = 1f; videoNoteCircle.scaleY = 1f; videoNoteCircle.alpha = 1f
                videoNoteProgress.progress = 0f
            }.start()
    }

    private fun startVoiceRecording() {
        voiceFile = File(cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        try {
            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this)
            else @Suppress("DEPRECATION") MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100); setAudioEncodingBitRate(128000)
                setOutputFile(voiceFile?.absolutePath); prepare(); start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка записи", e); voiceFile?.delete(); voiceFile = null
            Toast.makeText(this, "Не удалось начать запись", Toast.LENGTH_SHORT).show(); return
        }
        isVoiceRecording = true; isLocked = false; voiceRecordingSeconds = 0
        isRecordingPaused = false
        isVoiceViewOnce = false
        viewOnceButton.alpha = 0.4f
        pauseRecordingButton.setImageResource(android.R.drawable.ic_media_pause)

        showRecordingPanel(video = false)
        voiceRecordingJob = lifecycleScope.launch {
            while (isVoiceRecording) {
                delay(1000)
                if (!isRecordingPaused) {
                    voiceRecordingSeconds++
                    val t = "%d:%02d".format(voiceRecordingSeconds / 60, voiceRecordingSeconds % 60)
                    recordingTimer.text = t; lockedTimer.text = t
                    if (voiceRecordingSeconds >= 300) stopVoiceRecording(send = true)
                }
            }
        }
    }

    private fun stopVoiceRecording(send: Boolean, restoreInput: Boolean = true) {
        if (!isVoiceRecording) return
        isVoiceRecording = false; isLocked = false; voiceRecordingJob?.cancel(); ampJob?.cancel()
        if (restoreInput) dotAnimator?.cancel()
        try { mediaRecorder?.apply { stop(); release() }; mediaRecorder = null }
        catch (e: Exception) {
            Log.e(TAG, "Ошибка остановки", e); mediaRecorder?.release(); mediaRecorder = null
            voiceFile?.delete(); voiceFile = null; if (restoreInput) showNormalInput(); return
        }
        val file = voiceFile; voiceFile = null
        if (send && file != null && file.exists() && file.length() > 500) { showNormalInput(); uploadAndSendAudio(file) }
        else { file?.delete(); if (send) Toast.makeText(this, "Слишком короткое", Toast.LENGTH_SHORT).show(); if (restoreInput) showNormalInput() }
    }

    private fun showNormalInput() {
        dotAnimator?.cancel()
        recordingLayout.visibility = View.GONE; lockedRecordingLayout.visibility = View.GONE
        inputLayout.visibility = View.VISIBLE; recordingTimer.text = "0:00"; lockedTimer.text = "0:00"
        recordingDot.alpha = 1f; lockedDot.alpha = 1f; recordingTimer.alpha = 1f; lockedTimer.alpha = 1f
        slideToCancelLayout.alpha = 1f; slideToCancelLayout.translationX = 0f
        sendLockedButton.visibility = View.VISIBLE
        inputLayout.alpha = 0f
        inputLayout.animate().alpha(1f).setDuration(160).start()
    }

    // ── Upload ─────────────────────────────────────────────────────────────────

    private fun uploadAndSendAudio(file: File) {
        lifecycleScope.launch {
            try {
                val typePart = "audio".toRequestBody("text/plain".toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody("audio/m4a".toMediaTypeOrNull()))
                val up = ApiClient.apiService.uploadMedia(typePart, filePart)
                if (!up.isSuccessful) { Toast.makeText(this@ChatDetailActivity, "Ошибка загрузки аудио", Toast.LENGTH_SHORT).show(); return@launch }
                val url = up.body()?.url ?: return@launch

                val audioType = if (isVoiceViewOnce) "audio_once" else "audio"
                val resp = ApiClient.apiService.sendMessage(chatId, SendMessageRequest(content = url, type = audioType, replyToId = replyToMessage?.id))
                if (resp.isSuccessful) { resp.body()?.let { addNewMessage(it) }; clearReply() }
            } catch (e: Exception) { Log.e(TAG, "Ошибка отправки аудио", e) }
            finally { file.delete() }
        }
    }

    private fun uploadAndSendVideoNote(file: File) {
        lifecycleScope.launch {
            try {
                val typePart = "video".toRequestBody("text/plain".toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody("video/mp4".toMediaTypeOrNull()))
                val up = ApiClient.apiService.uploadMedia(typePart, filePart)
                if (!up.isSuccessful) { Toast.makeText(this@ChatDetailActivity, "Ошибка загрузки видео", Toast.LENGTH_SHORT).show(); return@launch }
                val url = up.body()?.url ?: return@launch
                val resp = ApiClient.apiService.sendMessage(chatId, SendMessageRequest(content = url, type = "video_note", replyToId = replyToMessage?.id))
                if (resp.isSuccessful) { resp.body()?.let { addNewMessage(it) }; clearReply() }
            } catch (e: Exception) { Log.e(TAG, "Ошибка отправки видео", e) }
            finally { file.delete() }
        }
    }

    // ── Text message ───────────────────────────────────────────────────────────

    private fun sendMessage(text: String) {
        messageInput.text.clear(); sendButton.isEnabled = false
        val replyMsg = replyToMessage            // сохраняем целиком, не только id
        val currentReplyId = replyMsg?.id
        clearReply()
        val tempId  = UUID.randomUUID().toString()

        // Цитата для локального сообщения, чтобы отобразилась сразу,
        // не дожидаясь ответа сервера.
        val replyPreview = replyMsg?.let {
            com.example.vibic.network.ReplyPreview(
                id = it.id,
                content = it.content,
                senderId = it.senderId,
                sender = it.sender
            )
        }

        val tempMsg = MessageDto(
            id = tempId, chatId = chatId, senderId = myUserId,
            content = text, type = "text", createdAt = getCurrentTime(),
            replyToId = currentReplyId, replyTo = replyPreview
        )
        allMessages.add(tempMsg); allMessages.sortBy { it.createdAt }
        sentMessageIds.add(tempId); processedMessageIds.add(tempId)
        runOnUiThread { adapter.setMessages(allMessages); recyclerView.scrollToPosition(adapter.itemCount - 1) }
        saveMessagesToCache(allMessages)
        lifecycleScope.launch {
            try {
                val resp = ApiClient.apiService.sendMessage(chatId, SendMessageRequest(content = text, replyToId = currentReplyId))
                if (resp.isSuccessful) {
                    resp.body()?.let { real ->
                        val idx = allMessages.indexOfFirst { it.id == tempId }
                        if (idx != -1) {
                            allMessages.removeAt(idx); processedMessageIds.remove(tempId); sentMessageIds.remove(tempId)
                            if (!processedMessageIds.contains(real.id)) {
                                real.id?.let { processedMessageIds.add(it) }
                                // Если сервер не вернул цитату — подставляем локальную,
                                // чтобы она не пропала после подмены temp → real.
                                val mergedReal = if (real.replyTo == null && real.replyToId == null && currentReplyId != null)
                                    real.copy(replyToId = currentReplyId, replyTo = replyPreview)
                                else real
                                allMessages.add(mergedReal); allMessages.sortBy { it.createdAt }
                            }
                            runOnUiThread { adapter.setMessages(allMessages); recyclerView.scrollToPosition(adapter.itemCount - 1) }
                            saveMessagesToCache(allMessages)
                        }
                    }
                } else {
                    allMessages.removeAll { it.id == tempId }; processedMessageIds.remove(tempId); sentMessageIds.remove(tempId)
                    runOnUiThread { adapter.setMessages(allMessages) }
                }
            } catch (e: Exception) {
                allMessages.removeAll { it.id == tempId }; processedMessageIds.remove(tempId); sentMessageIds.remove(tempId)
                runOnUiThread { adapter.setMessages(allMessages) }
            } finally { runOnUiThread { sendButton.isEnabled = true } }
        }
    }

    // ── WebSocket ──────────────────────────────────────────────────────────────

    private fun subscribeToWebSocket() {
        webSocketJob?.cancel()
        webSocketJob = lifecycleScope.launch {
            WebSocketManager.shared.messagesFlow.collect { message ->
                when (message.type) {
                    "new_message", "message" -> handleNewMessage(message)
                    "delete"                 -> handleDeletedMessage(message)
                    "connected", "connection_established", "hello" -> runOnUiThread {
                        updateConnectionStatus()
                        if (!isSubscribed && chatId.isNotEmpty()) { WebSocketManager.shared.subscribeToChat(chatId); isSubscribed = true }
                    }
                }
            }
        }
    }

    private fun handleNewMessage(message: com.example.vibic.network.WebSocketMessage) {
        try {
            val json    = com.google.gson.JsonParser.parseString(message.rawJson).asJsonObject
            val msgJson = json.get("payload") ?: json.get("message") ?: json.get("data") ?: json
            val dto     = gson.fromJson(msgJson, MessageDto::class.java)

            // Фильтруем пустые или служебные пакеты, не содержащие сообщения
            if (dto == null || dto.senderId.isNullOrBlank() || dto.content.isNullOrBlank()) {
                Log.d(TAG, "Пропущено пустое или системное событие: ${message.rawJson}")
                return
            }

            val incomingChatId = dto.chatId ?: chatId
            if (incomingChatId == chatId) {
                val id    = dto.id ?: UUID.randomUUID().toString()
                var final = dto.copy(id = id, chatId = incomingChatId)

                // Если сервер прислал сообщение-ответ без цитаты, но мы знаем
                // оригинал локально — восстанавливаем reply-данные, чтобы цитата
                // не пропадала (актуально и для ответов на чужие сообщения).
                if (final.replyTo == null) {
                    val replyId = final.replyToId
                    val original = if (replyId != null) allMessages.firstOrNull { it.id == replyId } else null
                    if (original != null) {
                        final = final.copy(
                            replyToId = replyId,
                            replyTo = com.example.vibic.network.ReplyPreview(
                                id = original.id,
                                content = original.content,
                                senderId = original.senderId,
                                sender = original.sender
                            )
                        )
                    }
                }

                if (processedMessageIds.contains(final.id)) return
                addNewMessage(final); saveMessagesToCache(allMessages)
            }
        } catch (e: Exception) { Log.e(TAG, "Ошибка парсинга", e) }
    }

    private fun handleDeletedMessage(message: com.example.vibic.network.WebSocketMessage) {
        try {
            val json = com.google.gson.JsonParser.parseString(message.rawJson).asJsonObject
            val id   = json.getAsJsonObject("payload").get("message_id")?.asString ?: return
            runOnUiThread { removeMessageLocally(id) }
        } catch (e: Exception) { Log.e(TAG, "Ошибка удаления", e) }
    }

    private fun addNewMessage(message: MessageDto) {
        message.id?.let { if (processedMessageIds.contains(it)) return else processedMessageIds.add(it) }
        if (allMessages.none { it.id == message.id }) {
            allMessages.add(message)
            runOnUiThread {
                adapter.addMessage(message)
                if (isSearchMode) runSearch(keepCurrent = true)
                recyclerView.post { if (adapter.itemCount > 0) recyclerView.smoothScrollToPosition(adapter.itemCount - 1) }
            }
        }
    }

    // ── Cache ──────────────────────────────────────────────────────────────────

    // Примерный вид вашего метода загрузки кэша:
    private fun loadCachedMessages() {
        try {
            val prefs = getSharedPreferences("message_cache", MODE_PRIVATE)
            val cachedJson = prefs.getString("chat_$chatId", null)
            if (cachedJson != null) {
                // Используем полный путь к TypeToken во избежание проблем с импортами
                val type = object : com.google.gson.reflect.TypeToken<List<MessageDto>>() {}.type
                val cachedList: List<MessageDto> = gson.fromJson(cachedJson, type) ?: emptyList()

                // Фильтруем пустые элементы перед выводом на экран
                val cleanList = cachedList.filter { !it.senderId.isNullOrBlank() && !it.content.isNullOrBlank() }

                allMessages.clear()
                allMessages.addAll(cleanList)
                allMessages.sortBy { it.createdAt }
                adapter.setMessages(allMessages)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка чтения кэша", e)
        }
    }

    private fun saveMessagesToCache(messages: List<MessageDto>) {
        try { getSharedPreferences("message_cache", MODE_PRIVATE).edit().putString("chat_$chatId", gson.toJson(messages)).apply() }
        catch (e: Exception) { Log.e(TAG, "Ошибка сохранения кэша", e) }
    }

    private fun loadMessagesInBackground() {
        lifecycleScope.launch {
            try {
                // Проверяем токен ДО запроса — если его нет, история не придёт
                val token = TokenManager.getAccessToken()
                if (token.isNullOrBlank()) {
                    Log.e(TAG, "loadMessages: НЕТ ТОКЕНА — пользователь не авторизован")
                    runOnUiThread {
                        Toast.makeText(this@ChatDetailActivity, "Нужно войти заново", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val resp = ApiClient.apiService.getMessages(chatId)
                Log.d(TAG, "loadMessages: code=${resp.code()}, body size=${resp.body()?.size}")

                if (resp.isSuccessful) {
                    val msgs = resp.body() ?: emptyList()
                    Log.d(TAG, "loadMessages: сервер вернул ${msgs.size} сообщений")

                    val cleanList = msgs.filter { !it.senderId.isNullOrBlank() && !it.content.isNullOrBlank() }
                    Log.d(TAG, "loadMessages: после фильтра осталось ${cleanList.size}")

                    // Если сервер реально пуст — НЕ затираем локальный кэш
                    if (cleanList.isEmpty() && allMessages.isNotEmpty()) {
                        Log.w(TAG, "loadMessages: сервер пуст, оставляю кэш (${allMessages.size})")
                        return@launch
                    }

                    // Сервер может не возвращать reply_to / reply_to_id. Чтобы цитаты
                    // не пропадали после перезахода, переносим reply-данные из старого
                    // списка (он наполнен из локального кэша) по id сообщения.
                    val prevReplyById = allMessages
                        .filter { it.id != null && (it.replyTo != null || it.replyToId != null) }
                        .associateBy { it.id!! }

                    val mergedList = cleanList.map { server ->
                        if (server.replyTo == null && server.replyToId == null) {
                            val prev = server.id?.let { prevReplyById[it] }
                            if (prev != null)
                                server.copy(replyToId = prev.replyToId, replyTo = prev.replyTo)
                            else server
                        } else server
                    }

                    allMessages.clear(); sentMessageIds.clear(); processedMessageIds.clear()
                    allMessages.addAll(mergedList); allMessages.sortBy { it.createdAt }
                    allMessages.forEach { it.id?.let { id -> processedMessageIds.add(id) } }
                    saveMessagesToCache(allMessages)
                    runOnUiThread {
                        adapter.setMessages(allMessages)
                        if (isSearchMode) runSearch(keepCurrent = true)
                        if (adapter.itemCount > 0 && isFirstLoad) {
                            recyclerView.scrollToPosition(adapter.itemCount - 1)
                            isFirstLoad = false
                        }
                    }
                } else {
                    // Логируем тело ошибки, чтобы понять причину
                    val errBody = resp.errorBody()?.string()
                    Log.e(TAG, "loadMessages: ОШИБКА ${resp.code()} — $errBody")
                    if (resp.code() == 401) {
                        Toast.makeText(this@ChatDetailActivity, "Сессия истекла", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadMessages: ИСКЛЮЧЕНИЕ", e)
                runOnUiThread {
                    Toast.makeText(this@ChatDetailActivity, "Ошибка сети при загрузке истории", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    // ══════════════════════════════════════════════════════════════════════════
    // ── Context menu (Telegram-style) ────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════════════

    private fun showMessageActionsPopup(message: MessageDto, anchorView: View) {
        val rootForInsets = findViewById<View>(R.id.rootLayout) ?: window.decorView
        val imeOpen = ViewCompat.getRootWindowInsets(rootForInsets)
            ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom?.let { it > 0 } == true
        if (imeOpen) {
            currentFocus?.let { focused ->
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(focused.windowToken, 0)
            }
            anchorView.postDelayed({ showMessageActionsPopupInternal(message, anchorView) }, 250)
            return
        }
        showMessageActionsPopupInternal(message, anchorView)
    }

    private fun showMessageActionsPopupInternal(message: MessageDto, anchorView: View) {
        val isSentByMe = message.senderId == myUserId

        val msgW        = anchorView.width
        val msgH        = anchorView.height
        val msgTimeText = anchorView.findViewById<TextView>(R.id.messageTime)
            ?.text?.toString() ?: ""

        val rootView   = findViewById<View>(R.id.rootLayout)
        val rootLoc    = IntArray(2).also { rootView.getLocationOnScreen(it) }
        val msgLoc     = IntArray(2).also { anchorView.getLocationOnScreen(it) }
        val msgLeftRaw = msgLoc[0] - rootLoc[0]
        val msgTopRaw  = msgLoc[1] - rootLoc[1]

        actionEdit.visibility = if (isSentByMe) View.VISIBLE else View.GONE

        actionReply.setOnClickListener   { hideContextMenu(); setReply(message) }
        actionCopy.setOnClickListener    {
            hideContextMenu()
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("", message.content ?: ""))
            Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show()
        }
        actionForward.setOnClickListener { hideContextMenu(); Toast.makeText(this, "Переслать в разработке", Toast.LENGTH_SHORT).show() }
        actionEdit.setOnClickListener    { hideContextMenu(); startEditMessage(message) }
        actionPin.setOnClickListener     { hideContextMenu(); Toast.makeText(this, "Закреплено", Toast.LENGTH_SHORT).show() }
        actionDelete.setOnClickListener  { hideContextMenu(); deleteMessage(message) }

        contextMenuOverlay.alpha      = 0f
        contextMenuOverlay.visibility = View.VISIBLE

        contextMenuOverlay.post {
            contextMenuOverlay.post {

                val density = resources.displayMetrics.density
                val dp8  = (8f  * density).toInt()
                val dp12 = (12f * density).toInt()
                val dp16 = (16f * density).toInt()

                val overlayW = contextMenuOverlay.width
                val overlayH = contextMenuOverlay.height

                val insets  = ViewCompat.getRootWindowInsets(contextMenuOverlay)
                val statusH = insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top    ?: 0
                val navH    = insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
                val imeH    = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
                val bottomInset = maxOf(navH, imeH)
                val safeTop = statusH + dp8
                val safeBot = (overlayH - bottomInset - dp8).coerceAtLeast(safeTop + 1)

                val unspecW = View.MeasureSpec.makeMeasureSpec(overlayW, View.MeasureSpec.AT_MOST)
                val unspecH = View.MeasureSpec.makeMeasureSpec(overlayH, View.MeasureSpec.AT_MOST)
                reactionsBar.measure(unspecW, unspecH)
                actionsMenu.measure(
                    View.MeasureSpec.makeMeasureSpec((220 * density).toInt(), View.MeasureSpec.EXACTLY),
                    unspecH
                )
                val rxW   = reactionsBar.measuredWidth
                val rxH   = reactionsBar.measuredHeight
                val menuW = (220 * density).toInt()
                val menuH = actionsMenu.measuredHeight

                val msgLeft  = msgLeftRaw.coerceIn(dp8, (overlayW - msgW - dp8).coerceAtLeast(dp8))
                val menuLeft = if (isSentByMe) (overlayW - menuW - dp12).coerceAtLeast(dp8) else dp12
                val rxLeft   = if (isSentByMe) (overlayW - rxW - dp12).coerceAtLeast(dp8) else dp12

                val msgTop = msgTopRaw.coerceIn(safeTop, (safeBot - msgH).coerceAtLeast(safeTop))

                val rxMenuGap = dp8
                val stackH    = rxH + rxMenuGap + menuH
                val stackGap  = dp8

                val stackTopBelow = msgTop + msgH + stackGap
                val stackTopAbove = msgTop - stackGap - stackH

                val stackTop = when {
                    stackTopBelow + stackH <= safeBot -> stackTopBelow
                    stackTopAbove >= safeTop          -> stackTopAbove
                    else -> (safeBot - stackH).coerceAtLeast(safeTop)
                }

                val rxTop   = stackTop
                val menuTop = stackTop + rxH + rxMenuGap

                clonedMessageContainer.removeAllViews()
                val layoutId = if (isSentByMe) R.layout.item_message_sent
                else            R.layout.item_message_received
                val clone = layoutInflater.inflate(layoutId, clonedMessageContainer, false)
                clone.findViewById<TextView>(R.id.messageText).text = message.content ?: ""
                clone.findViewById<TextView>(R.id.messageTime).text = msgTimeText
                clone.layoutParams = FrameLayout.LayoutParams(
                    msgW, FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity    = Gravity.TOP or Gravity.START
                    topMargin  = msgTop
                    leftMargin = msgLeft
                }
                clonedMessageContainer.addView(clone)

                hiddenAnchorView = anchorView
                anchorView.visibility = View.INVISIBLE

                reactionsBar.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity    = Gravity.TOP or Gravity.START
                    topMargin  = rxTop
                    leftMargin = rxLeft
                }
                actionsMenu.layoutParams = FrameLayout.LayoutParams(
                    (220 * density).toInt(),
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity    = Gravity.TOP or Gravity.START
                    topMargin  = menuTop
                    leftMargin = menuLeft
                }

                reactionsBar.alpha  = 0f; reactionsBar.scaleX = 0.8f;  reactionsBar.scaleY = 0.8f
                actionsMenu.alpha   = 0f; actionsMenu.scaleX  = 0.88f; actionsMenu.scaleY  = 0.88f

                contextMenuOverlay.animate().alpha(1f).setDuration(130).start()
                reactionsBar.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f).setDuration(220)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.4f))
                    .start()
                actionsMenu.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f).setDuration(240).setStartDelay(50)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                    .start()
            }
        }
    }

    private fun hideContextMenu() {
        contextMenuOverlay.animate().alpha(0f).setDuration(150).withEndAction {
            contextMenuOverlay.visibility = View.GONE
            clonedMessageContainer.removeAllViews()
            hiddenAnchorView?.visibility = View.VISIBLE
            hiddenAnchorView = null
        }.start()
    }

    private fun startEditMessage(message: MessageDto) {
        messageInput.setText(message.content ?: "")
        messageInput.setSelection(messageInput.text.length)
        messageInput.requestFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(messageInput, InputMethodManager.SHOW_IMPLICIT)
        Toast.makeText(this, "Режим редактирования (в разработке)", Toast.LENGTH_SHORT).show()
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun setupSearch() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchClear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                if (isSearchMode) runSearch(keepCurrent = false)
            }
        })
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { hideKeyboard(); true } else false
        }
        searchClear.setOnClickListener { searchInput.setText("") }
        // Вверх — к более старым совпадениям, вниз — к более новым
        searchUp.setOnClickListener { moveSearch(+1) }
        searchDown.setOnClickListener { moveSearch(-1) }
    }

    private fun openSearch() {
        if (isSearchMode) { showSearchKeyboard(); return }
        if (contextMenuOverlay.visibility == View.VISIBLE) hideContextMenu()
        if (replyBar.visibility == View.VISIBLE) clearReply()
        isSearchMode = true
        chatUserCapsule.visibility = View.GONE
        searchBar.visibility = View.VISIBLE
        inputLayout.visibility = View.GONE
        emojiPanelContainer.visibility = View.GONE
        searchNavBar.visibility = View.VISIBLE
        // Крестик у правого края: пока открыт поиск, жест «Назад» с правого края
        // в зоне шапки отключён, иначе касание крестика срабатывает как «Назад»
        val topBar = findViewById<View>(R.id.topBar)
        topBar.post {
            ViewCompat.setSystemGestureExclusionRects(
                topBar, listOf(android.graphics.Rect(topBar.width / 2, 0, topBar.width, topBar.height))
            )
        }
        searchInput.setText("")
        runSearch(keepCurrent = false)
        showSearchKeyboard()
    }

    // Клавиатуру можно показать только когда окно в фокусе. При возврате из профиля
    // фокус приходит позже — тогда откроем её в onWindowFocusChanged.
    private var pendingSearchKeyboard = false

    private fun showSearchKeyboard() {
        searchInput.requestFocus()
        if (!hasWindowFocus()) { pendingSearchKeyboard = true; return }
        pendingSearchKeyboard = false
        searchInput.post {
            WindowCompat.getInsetsController(window, searchInput).show(WindowInsetsCompat.Type.ime())
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && pendingSearchKeyboard && isSearchMode) showSearchKeyboard()
    }

    private fun closeSearch() {
        isSearchMode = false
        pendingSearchKeyboard = false
        hideKeyboard()
        searchBar.visibility = View.GONE
        chatUserCapsule.visibility = View.VISIBLE
        searchNavBar.visibility = View.GONE
        inputLayout.visibility = View.VISIBLE
        ViewCompat.setSystemGestureExclusionRects(findViewById(R.id.topBar), emptyList())
        searchMatches = emptyList(); searchIndex = -1
        adapter.setSearch("", null)
    }

    private fun runSearch(keepCurrent: Boolean) {
        val query = searchInput.text.toString().trim()
        val prevId = searchMatches.getOrNull(searchIndex)
        searchMatches = if (query.isEmpty()) emptyList() else allMessages
            .filter { m ->
                m.id != null && m.type !in listOf("audio", "audio_once", "video_note") &&
                    m.content?.contains(query, ignoreCase = true) == true
            }
            .map { it.id!! }
            .reversed()
        searchIndex = when {
            searchMatches.isEmpty() -> -1
            keepCurrent && prevId != null && prevId in searchMatches -> searchMatches.indexOf(prevId)
            else -> 0
        }
        applySearchState(scroll = !keepCurrent)
    }

    private fun moveSearch(delta: Int) {
        if (searchMatches.isEmpty()) return
        val next = searchIndex + delta
        if (next !in searchMatches.indices) return
        searchIndex = next
        applySearchState(scroll = true)
    }

    private fun applySearchState(scroll: Boolean) {
        val query = searchInput.text.toString().trim()
        val currentId = searchMatches.getOrNull(searchIndex)
        adapter.setSearch(query, currentId)

        searchCounter.text = when {
            query.isEmpty()         -> ""
            searchMatches.isEmpty() -> "Ничего не найдено"
            else                    -> "${searchIndex + 1} из ${searchMatches.size}"
        }
        val canUp = searchIndex in 0 until searchMatches.size - 1
        val canDown = searchIndex > 0
        searchUp.isEnabled = canUp;     searchUp.alpha = if (canUp) 1f else 0.3f
        searchDown.isEnabled = canDown; searchDown.alpha = if (canDown) 1f else 0.3f

        if (scroll && currentId != null) {
            val pos = adapter.findDisplayPositionById(currentId)
            if (pos >= 0) {
                val lm = recyclerView.layoutManager as LinearLayoutManager
                lm.scrollToPositionWithOffset(pos, recyclerView.height / 3)
            }
        }
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    // ── Back press ────────────────────────────────────────────────────────────

    override fun onBackPressed() {
        when {
            isVideoHold || isVoiceRecording -> cancelRecording()
            videoNoteOverlay.visibility == View.VISIBLE -> {
                isFlipping = true; activeVideoRecording?.stop(); activeVideoRecording = null
                isVideoRecording = false; videoFile?.delete(); videoFile = null
                isFlipping = false; closeVideoNoteOverlay()
            }
            contextMenuOverlay.visibility == View.VISIBLE -> hideContextMenu()
            isSearchMode -> closeSearch()
            replyBar.visibility           == View.VISIBLE -> clearReply()
            else -> super.onBackPressed()
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    private fun deleteMessage(message: MessageDto) {
        val id = message.id ?: return
        lifecycleScope.launch {
            try {
                val resp = ApiClient.apiService.deleteMessage(id)
                if (resp.isSuccessful) removeMessageLocally(id)
                else Toast.makeText(this@ChatDetailActivity, "Не удалось удалить: ${resp.code()}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this@ChatDetailActivity, "Ошибка сети", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun removeMessageLocally(messageId: String) {
        val pos = adapter.findMessagePositionById(messageId)
        allMessages.removeAll { it.id == messageId }
        processedMessageIds.remove(messageId); sentMessageIds.remove(messageId)
        saveMessagesToCache(allMessages)
        if (pos >= 0) adapter.removeMessageAt(pos) else adapter.setMessages(allMessages)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun updateConnectionStatus() {
        val connected = WebSocketManager.shared.isConnectedValue
        runOnUiThread {
            chatUserStatus.text = if (connected) "В сети" else "Офлайн"
            chatUserStatus.setTextColor(
                MaterialColors.getColor(chatUserStatus, if (connected) R.attr.vibicSuccess else R.attr.vibicDanger)
            )
        }
    }

    private fun startWebSocketStatusMonitor() {
        connectionCheckJob?.cancel()
        connectionCheckJob = lifecycleScope.launch {
            WebSocketManager.shared.connectionState.collect { connected ->
                chatUserStatus.text = if (connected) "В сети" else "Офлайн"
                chatUserStatus.setTextColor(
                    MaterialColors.getColor(chatUserStatus, if (connected) R.attr.vibicSuccess else R.attr.vibicDanger)
                )
            }
        }
    }

    private fun hideEmojiPanelAndShowKeyboard() {
        emojiPanelContainer.visibility = View.GONE; messageInput.requestFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(messageInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun openGallery() = pickImageLauncher.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    )

    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC"); return sdf.format(Date())
    }

    private fun restoreUserIdFromTokenIfNeeded() {
        val isPhone = myUserId.isNotEmpty() && (myUserId.startsWith("+") || myUserId.all { it.isDigit() })
        if (myUserId.isEmpty() || isPhone) {
            try {
                val token = TokenManager.getAccessToken() ?: return
                val parts = token.split(".")
                if (parts.size >= 2) {
                    val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE))
                    val json    = org.json.JSONObject(payload)
                    val sub     = json.optString("sub"); val uid = json.optString("user_id")
                    val isSubPhone = sub.startsWith("+") || sub.all { it.isDigit() }
                    val finalId = when { isSubPhone && uid.isNotEmpty() -> uid; uid.isNotEmpty() -> uid; else -> sub }
                    if (finalId.isNotEmpty() && !(finalId.startsWith("+") || finalId.all { it.isDigit() })) {
                        myUserId = finalId; TokenManager.saveUserId(finalId)
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "Ошибка userId", e) }
        }
    }

    private fun restoreUserIdFromNetworkIfNeeded() {
        if (myUserId.isEmpty() || myUserId.startsWith("+") || myUserId.all { it.isDigit() }) {
            lifecycleScope.launch {
                try {
                    val resp = ApiClient.apiService.getCurrentUser()
                    if (resp.isSuccessful) resp.body()?.id?.let { id ->
                        myUserId = id; TokenManager.saveUserId(id)
                        runOnUiThread {
                            adapter = MessageAdapter(
                                myUserId = myUserId,
                                onMessageLongClick = { msg, anchor -> showMessageActionsPopup(msg, anchor) },
                                onViewOncePlayed = { message ->
                                    deleteMessage(message)
                                    Toast.makeText(this@ChatDetailActivity, "Одноразовое сообщение удалено", Toast.LENGTH_SHORT).show()
                                }
                            )
                            recyclerView.adapter = adapter; adapter.setMessages(allMessages)
                            if (adapter.itemCount > 0) recyclerView.scrollToPosition(adapter.itemCount - 1)
                        }
                    }
                } catch (e: Exception) { Log.e(TAG, "Ошибка userId сеть", e) }
            }
        }
    }
}