package com.example.vibic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QrScannerActivity : AppCompatActivity() {

    companion object {
        const val RESULT_QR_VALUE = "qr_value"
    }

    private lateinit var previewView: PreviewView
    private lateinit var btnBack: ImageView
    private lateinit var hint: TextView

    private lateinit var cameraExecutor: ExecutorService
    private var isResultSent = false

    private val requestCameraLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else {
                Toast.makeText(this, "Нужен доступ к камере", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        setContentView(R.layout.activity_qr_scanner)

        previewView = findViewById(R.id.qrPreviewView)
        btnBack     = findViewById(R.id.btnQrBack)
        hint        = findViewById(R.id.qrHint)

        btnBack.setOnClickListener { finish() }

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestCameraLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, QrAnalyzer { qrValue ->
                        if (!isResultSent) {
                            isResultSent = true
                            runOnUiThread {
                                val result = Intent().apply {
                                    putExtra(RESULT_QR_VALUE, qrValue)
                                }
                                setResult(RESULT_OK, result)
                                finish()
                            }
                        }
                    })
                }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analyzer
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Ошибка камеры", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

// ── Анализатор QR через ML Kit ────────────────────────────────────────────────
class QrAnalyzer(private val onQrDetected: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                    ?.rawValue
                    ?.let { onQrDetected(it) }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}