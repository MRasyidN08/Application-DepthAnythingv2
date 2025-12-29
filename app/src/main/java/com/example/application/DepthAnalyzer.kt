package com.example.application

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.max
import kotlin.math.min

class DepthAnalyzer(
    private val kotlinPreprocessor: KotlinPreprocessor,
    private val modelExecutor: DepthModelExecutor,
    private val feedbackManager: FeedbackManager,
    private val overlayView: OverlayView,
    private val onFpsUpdate: (Double) -> Unit
) : ImageAnalysis.Analyzer {

    // =========================
    // ====== KONFIGURASI ======
    // =========================

    private val MODEL_WIDTH = 252
    private val MODEL_HEIGHT = 252

    // Zona kiri – tengah – kanan
    private val ZONE_COUNT = 3
    private val zoneWidth = MODEL_WIDTH / ZONE_COUNT

    // ROI bawah (area bahaya)
    private val SCAN_START_Y = (MODEL_HEIGHT * 0.55f).toInt()
    private val SCAN_END_Y   = (MODEL_HEIGHT * 0.90f).toInt()

    // Percentile terdekat (anti objek kecil hilang)
    private val DEPTH_PERCENTILE = 0.08f   // 8%

    // Kalibrasi LINEAR (STABIL)
    // meter = A * depth + B
    // ⬇️ NANTI BOLEH DI-TUNING
    private val CALIB_A = -4.2f
    private val CALIB_B = 5.5f

    // Batas jarak
    private val MIN_DISTANCE = 0.4f
    private val MAX_DISTANCE = 5.0f

    // Smoothing agar tidak loncat
    private val SMOOTHING_ALPHA = 0.15f

    // =========================
    // ====== VARIABLE ========
    // =========================

    private val zones = FloatArray(ZONE_COUNT)
    private val smoothedDistance = FloatArray(ZONE_COUNT) { 3.0f }
    private val depthSamples = ArrayList<Float>(1500)

    // FPS
    private var lastFpsTime = System.currentTimeMillis()
    private var frameCount = 0

    // =========================
    // ====== ANALYZE ==========
    // =========================

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {

        // ---------- FPS ----------
        val now = System.currentTimeMillis()
        frameCount++
        if (now - lastFpsTime >= 1000) {
            val fps = frameCount * 1000.0 / (now - lastFpsTime)
            onFpsUpdate(fps)
            frameCount = 0
            lastFpsTime = now
        }

        // ---------- PREPROCESS ----------
        val inputTensor = kotlinPreprocessor.preprocess(imageProxy)
        imageProxy.close()
        if (inputTensor == null) return

        // ---------- INFERENCE ----------
        val output = modelExecutor.submitInference(inputTensor)
        if (output == null || output.isEmpty()) return

        // ---------- PER ZONA ----------
        for (zone in 0 until ZONE_COUNT) {

            depthSamples.clear()

            val startX = zone * zoneWidth
            val endX = startX + zoneWidth

            // Scan ROI bawah
            for (y in SCAN_START_Y until SCAN_END_Y step 3) {
                for (x in startX until endX step 3) {
                    val idx = y * MODEL_WIDTH + x
                    if (idx < output.size) {
                        val d = output[idx]
                        if (d > 0f && d < 10f) { // buang noise ekstrem
                            depthSamples.add(d)
                        }
                    }
                }
            }

            // ---------- PILIH DEPTH TERDEKAT ----------
            val rawDepth = if (depthSamples.isNotEmpty()) {
                depthSamples.sort() // KECIL = DEKAT
                val pIdx = (depthSamples.size * DEPTH_PERCENTILE)
                    .toInt()
                    .coerceIn(0, depthSamples.size - 1)
                depthSamples[pIdx]
            } else {
                Float.NaN
            }

            // ---------- KONVERSI KE METER ----------
            var rawDistance = if (!rawDepth.isNaN()) {
                CALIB_A * rawDepth + CALIB_B
            } else {
                MAX_DISTANCE
            }

            // ---------- CLAMP ----------
            rawDistance = rawDistance.coerceIn(MIN_DISTANCE, MAX_DISTANCE)

            // ---------- SMOOTHING ----------
            smoothedDistance[zone] =
                SMOOTHING_ALPHA * rawDistance +
                        (1f - SMOOTHING_ALPHA) * smoothedDistance[zone]

            zones[zone] = smoothedDistance[zone]
        }

        // ---------- DEBUG ----------
        Log.d(
            "DepthDebug",
            "L: ${zones[0]} | M: ${zones[1]} | R: ${zones[2]}"
        )

        // ---------- UI UPDATE ----------
        overlayView.post {
            overlayView.updateZones(zones)

            // garis batas sesuai ROI
            val limitPercent = SCAN_END_Y.toFloat() / MODEL_HEIGHT.toFloat()
            overlayView.updateLimitLine(limitPercent)
        }

        feedbackManager.update(zones)
    }
}
