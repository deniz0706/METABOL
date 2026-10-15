package com.deniz0706.metabol

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** In-app scanner: CameraX camera feed and on-device ML Kit barcode recognition. */
class BarcodeScannerActivity : ComponentActivity() {
    private val busy = AtomicBoolean(false)
    private val completed = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(
            Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_ITF, Barcode.FORMAT_CODE_128
        ).build()
    )
    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else {
            status.text = "Kamera izni gerekli. Barkodu elle girebilirsin."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(7, 11, 18)) }
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        root.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        root.addView(ScannerFrame(this), FrameLayout.LayoutParams(-1, -1))
        val top = TextView(this).apply {
            text = "‹    METABOL  /  BARKOD TARA"
            setTextColor(Color.WHITE)
            textSize = 17f
            setPadding(dp(22), dp(24), dp(12), dp(16))
            setBackgroundColor(Color.argb(170, 7, 11, 18))
            setOnClickListener { finish() }
        }
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        status = TextView(this).apply {
            text = "Ürünün çizgili barkodunu yatay çerçeveye getir.\nIşık yeterli olsun, tüm çizgiler görünsün."
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(20), dp(20), dp(30))
            setBackgroundColor(Color.argb(190, 7, 11, 18))
        }
        root.addView(status, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) startCamera()
        else permission.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (isFinishing || isDestroyed) return@addListener
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    if (completed.get() || !busy.compareAndSet(false, true)) {
                        proxy.close()
                    } else {
                        val media = proxy.image
                        if (media == null) {
                            busy.set(false)
                            proxy.close()
                        } else {
                            val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    val code = barcodes.firstNotNullOfOrNull { barcode ->
                                        barcode.rawValue?.takeIf { it.matches(Regex("[0-9]{8,14}")) }
                                    }
                                    if (code != null && completed.compareAndSet(false, true)) {
                                        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                                        if (android.os.Build.VERSION.SDK_INT >= 26)
                                            vibrator.vibrate(VibrationEffect.createOneShot(75, VibrationEffect.DEFAULT_AMPLITUDE))
                                        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_BARCODE, code))
                                        finish()
                                    }
                                }
                                .addOnFailureListener {
                                    runOnUiThread { status.text = "Algılama hatası. Kamerayı tekrar hizala." }
                                }
                                .addOnCompleteListener {
                                    busy.set(false)
                                    proxy.close()
                                }
                        }
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                status.text = "Kamera başlatılamadı: ${e.localizedMessage ?: "Bilinmeyen hata"}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        scanner.close()
        executor.shutdown()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object { const val EXTRA_BARCODE = "metabol_barcode" }
}

private class ScannerFrame(context: android.content.Context) : View(context) {
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(100, 0, 0, 0) }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(113, 232, 196)
        style = Paint.Style.STROKE
        strokeWidth = 5f * resources.displayMetrics.density
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = width * 0.07f
        val right = width * 0.93f
        val top = height * 0.39f
        val bottom = height * 0.61f
        canvas.drawRect(0f, 0f, width.toFloat(), top, shade)
        canvas.drawRect(0f, bottom, width.toFloat(), height.toFloat(), shade)
        canvas.drawRect(0f, top, left, bottom, shade)
        canvas.drawRect(right, top, width.toFloat(), bottom, shade)
        canvas.drawRoundRect(RectF(left, top, right, bottom), 20f, 20f, line)
    }
}
