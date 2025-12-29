package com.example.application

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.application.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService

    // Komponen-komponen utama
    private var kotlinPreprocessor: KotlinPreprocessor? = null
    private var depthModelExecutor: DepthModelExecutor? = null
    private var feedbackManager: FeedbackManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Setup Binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. Setup Executor untuk Kamera (Background Thread)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // 3. Inisialisasi Modul Helper
        try {
            kotlinPreprocessor = KotlinPreprocessor()
            depthModelExecutor = DepthModelExecutor(this)
            feedbackManager = FeedbackManager(this)
        } catch (e: Exception) {
            Log.e("MainActivity", "Gagal inisialisasi komponen", e)
            Toast.makeText(this, "Gagal memuat model AI", Toast.LENGTH_LONG).show()
        }

        // 4. Cek Izin Kamera
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Mengikat ke Lifecycle Owner (Activity ini)
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview (Tampilan Kamera)
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            // Setup Image Analysis (Tempat proses AI berjalan)
            // STRATEGY_KEEP_ONLY_LATEST penting agar tidak terjadi lag menumpuk
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            // Mencegah crash jika komponen belum siap
            if (kotlinPreprocessor != null && depthModelExecutor != null && feedbackManager != null) {

                // --- DISINI BAGIAN PENTINGNYA ---
                // Kita memasukkan binding.overlayView agar Analyzer bisa menggambar angka jarak
                val analyzer = DepthAnalyzer(
                    kotlinPreprocessor!!,
                    depthModelExecutor!!,
                    feedbackManager!!,
                    binding.overlayView // <-- Pass OverlayView dari layout
                ) { fps ->
                    // Update teks FPS di Thread UI Utama
                    runOnUiThread {
                        binding.fpsText.text = String.format("FPS: %.1f", fps)
                    }
                }

                imageAnalyzer.setAnalyzer(cameraExecutor, analyzer)
            }

            // Pilih Kamera Belakang
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use case sebelumnya sebelum binding baru
                cameraProvider.unbindAll()

                // Bind use cases ke kamera
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )

            } catch (exc: Exception) {
                Log.e("MainActivity", "Gagal memunculkan kamera", exc)
                Toast.makeText(this, "Gagal memunculkan kamera.", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // --- Bagian Permission (Izin) ---
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            this, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Izin kamera ditolak. Aplikasi tidak dapat berjalan.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    // --- Bagian Cleanup (Pembersihan Memori) ---
    override fun onDestroy() {
        super.onDestroy()
        // Hentikan thread kamera
        cameraExecutor.shutdown()

        // Tutup model AI untuk mencegah memory leak
        depthModelExecutor?.close()

        // Matikan TTS (Text To Speech)
        feedbackManager?.release()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}