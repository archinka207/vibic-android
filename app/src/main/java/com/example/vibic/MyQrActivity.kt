package com.example.vibic

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

class MyQrActivity : AppCompatActivity() {

    private lateinit var backButton: FrameLayout
    private lateinit var shareButton: TextView
    private lateinit var qrSquareWrap: FrameLayout
    private lateinit var qrImage: ImageView
    private lateinit var qrUserName: TextView
    private lateinit var qrUserHandle: TextView
    private lateinit var scanOthersButton: LinearLayout

    private var qrPayload: String = ""
    private var qrBitmap: Bitmap? = null

    private val qrLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val value = result.data?.getStringExtra(QrScannerActivity.RESULT_QR_VALUE)
            if (!value.isNullOrBlank()) {
                val data = Intent().putExtra(QrScannerActivity.RESULT_QR_VALUE, value)
                setResult(Activity.RESULT_OK, data)
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.e("MyQrActivity", ">>> onCreate STARTED <<<")
        setContentView(R.layout.activity_my_qr)

        backButton       = findViewById(R.id.backButton)
        shareButton      = findViewById(R.id.shareButton)
        qrSquareWrap     = findViewById(R.id.qrSquareWrap)
        qrImage          = findViewById(R.id.qrImage)
        qrUserName       = findViewById(R.id.qrUserName)
        qrUserHandle     = findViewById(R.id.qrUserHandle)
        scanOthersButton = findViewById(R.id.scanOthersButton)

        loadUserData()
        setupSquareWrap()
        renderQr()

        backButton.setOnClickListener { finish() }
        shareButton.setOnClickListener { shareQrLink() }
        scanOthersButton.setOnClickListener {
            qrLauncher.launch(Intent(this, QrScannerActivity::class.java))
        }
    }

    /** Делает белую плашку квадратной: высота = ширине после первой разметки */
    private fun setupSquareWrap() {
        qrSquareWrap.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    qrSquareWrap.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    val w = qrSquareWrap.width
                    Log.e("MyQrActivity", "qrSquareWrap width=$w")
                    if (w > 0) {
                        val lp = qrSquareWrap.layoutParams
                        lp.height = w
                        qrSquareWrap.layoutParams = lp
                    }
                }
            }
        )
    }

    private fun loadUserData() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val firstName = prefs.getString("user_first_name", null)
        val lastName  = prefs.getString("user_last_name", null)
        val nickname  = prefs.getString("user_nickname", null)
        val username  = prefs.getString("user_username", null)

        val fullName = when {
            !firstName.isNullOrBlank() && !lastName.isNullOrBlank() -> "$firstName $lastName"
            !firstName.isNullOrBlank() -> firstName!!
            !lastName.isNullOrBlank()  -> lastName!!
            !nickname.isNullOrBlank()  -> nickname!!
            else -> "Пользователь"
        }

        qrUserName.text = fullName
        qrUserHandle.text = if (!username.isNullOrBlank()) "@$username · Vibic" else "Vibic"

        // Ссылка-приглашение БЕЗ телефона.
        //   - если есть username → vibic://user/<username>
        //   - если нет          → vibic://invite/<случайный токен>
        // Токен сохраняем в prefs, чтобы он не менялся.
        qrPayload = when {
            !username.isNullOrBlank() -> "vibic://user/$username"
            else -> {
                var token = prefs.getString("invite_token", null)
                if (token.isNullOrBlank()) {
                    token = generateInviteToken()
                    prefs.edit().putString("invite_token", token).apply()
                }
                "vibic://invite/$token"
            }
        }
        Log.e("MyQrActivity", "qrPayload=$qrPayload")
    }

    /** Случайный криптостойкий токен длиной 12 символов из [A-Za-z0-9]. */
    private fun generateInviteToken(): String {
        val alphabet = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        val random = java.security.SecureRandom()
        val sb = StringBuilder(12)
        repeat(12) { sb.append(alphabet[random.nextInt(alphabet.size)]) }
        return sb.toString()
    }

    private fun renderQr() {
        Log.e("MyQrActivity", ">>> renderQr called <<<")
        val bmp = generateQrBitmap(qrPayload, 1024)
        if (bmp != null) {
            Log.e("MyQrActivity", "QR bitmap created: ${bmp.width}x${bmp.height} -> setImageBitmap")
            qrBitmap = bmp
            qrImage.setImageBitmap(bmp)
        } else {
            Log.e("MyQrActivity", "QR bitmap is NULL")
            Toast.makeText(this, "Не удалось сгенерировать QR-код", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateQrBitmap(text: String, size: Int): Bitmap? {
        return try {
            Log.e("MyQrActivity", "ZXing: encoding '$text', size=$size")
            val hints = hashMapOf<EncodeHintType, Any>(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                EncodeHintType.MARGIN to 1
            )
            val writer = QRCodeWriter()
            val matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size, hints)

            val w = matrix.width
            val h = matrix.height
            Log.e("MyQrActivity", "ZXing matrix: ${w}x${h}")
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                val offset = y * w
                for (x in 0 until w) {
                    pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
                }
            }

            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, w, 0, 0, w, h)
            }
        } catch (t: Throwable) {
            Log.e("MyQrActivity", "QR generation FAILED: ${t::class.java.name}: ${t.message}", t)
            null
        }
    }

    private fun shareQrLink() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Vibic ссылка", qrPayload)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Log.e("MyQrActivity", "copy failed: ${t.message}", t)
            Toast.makeText(this, "Не удалось скопировать", Toast.LENGTH_SHORT).show()
        }
    }
}