package com.example.application

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.exp

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

    private val MODEL_WIDTH = 518
    private val MODEL_HEIGHT = 518

    private val ZONE_COUNT = 3
    private val zoneWidth = MODEL_WIDTH / ZONE_COUNT

    // Area scan diubah ke 60%-80%:
    // - Sebelumnya 55%-90% → terlalu banyak menangkap lantai dekat kaki
    // - 60%-80% → fokus ke area "depan tubuh", lantai jauh lebih sedikit
    private val SCAN_START_Y = (MODEL_HEIGHT * 0.60f).toInt()  // was 0.55
    private val SCAN_END_Y   = (MODEL_HEIGHT * 0.80f).toInt()  // was 0.90

    private val MIN_DISTANCE = 0.3f
    private val MAX_DISTANCE = 5.0f

    private val SMOOTHING_ALPHA = 0.3f

    // ── KALIBRASI EKSPONENSIAL ─────────────────────────────────────────
    // Dikalibrasi dari 4 titik pengukuran nyata:
    //   rawDepth 4.864 → 0.3m  | rawDepth 3.803 → 1.0m
    //   rawDepth 2.742 → 2.0m  | rawDepth 2.288 → 3.0m
    //
    // Rumus: distance = exp(EXP_A * rawDepth + EXP_B)
    // Jika semua hasil masih terlalu besar → turunkan EXP_B
    // Jika semua hasil masih terlalu kecil → naikkan EXP_B
    private val EXP_A = -0.4348f
    private val EXP_B =  2.0207f

    // Percentile 0.30:
    // - Sort descending: index 0 = piksel terdekat di zona
    // - 0.30 berarti ambil dari 30% piksel terdekat
    // - Cukup sensitif untuk rintangan yang mengisi ≥30% zona
    // - Tidak mudah terpicu lantai (sudah dikurangi oleh scan area baru)
    //   maupun objek kecil/noise
    private val DEPTH_PERCENTILE = 0.30f

    // =========================
    // ====== VARIABLE =========
    // =========================

    private val zones = FloatArray(ZONE_COUNT)
    private val smoothedDistance = FloatArray(ZONE_COUNT) { 2.0f }
    private val depthSamples = ArrayList<Float>(5000)

    private var lastFpsTime = System.currentTimeMillis()
    private var frameCount = 0

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        frameCount++
        if (now - lastFpsTime >= 1000) {
            val fps = frameCount * 1000.0 / (now - lastFpsTime)
            onFpsUpdate(fps)
            frameCount = 0
            lastFpsTime = now
        }

        val inputTensor = kotlinPreprocessor.preprocess(imageProxy)
        imageProxy.close()
        if (inputTensor == null) return

        val output = modelExecutor.submitInference(inputTensor)
        if (output == null || output.isEmpty()) return

        // ── Flat scene detection ───────────────────────────────────────
        var frameMin = Float.MAX_VALUE
        var frameMax = -Float.MAX_VALUE
        for (value in output) {
            if (value < frameMin) frameMin = value
            if (value > frameMax) frameMax = value
        }
        val frameRange = frameMax - frameMin

        if (frameRange < 0.3f) {
            Log.d("DepthDebug", "Flat scene (range=${"%.3f".format(frameRange)}), skip")
            postResult()
            return
        }

        // ── Proses tiap zona ───────────────────────────────────────────
        for (zone in 0 until ZONE_COUNT) {
            depthSamples.clear()

            val startX = zone * zoneWidth
            val endX   = startX + zoneWidth

            for (y in SCAN_START_Y until SCAN_END_Y step 2) {
                for (x in startX until endX step 2) {
                    val idx = y * MODEL_WIDTH + x
                    if (idx < output.size) depthSamples.add(output[idx])
                }
            }

            if (depthSamples.isEmpty()) continue

            // Sort descending: nilai besar = dekat
            // Ambil index ke-30% = representatif rintangan yang mengisi
            // minimal 30% zona, tahan terhadap noise & objek kecil
            depthSamples.sortDescending()
            val pIdx = (DEPTH_PERCENTILE * depthSamples.size)
                .toInt().coerceIn(0, depthSamples.size - 1)
            val rawDepth = depthSamples[pIdx]

            // ── Konversi ke meter (eksponensial) ───────────────────────
            val rawDistance = exp(EXP_A * rawDepth + EXP_B)
                .toFloat()
                .coerceIn(MIN_DISTANCE, MAX_DISTANCE)

            // ── EMA Smoothing ──────────────────────────────────────────
            smoothedDistance[zone] = SMOOTHING_ALPHA * rawDistance +
                    (1f - SMOOTHING_ALPHA) * smoothedDistance[zone]
            zones[zone] = smoothedDistance[zone]
        }

        Log.d("DepthDebug",
            "L: ${"%.2f".format(zones[0])}m | " +
                    "M: ${"%.2f".format(zones[1])}m | " +
                    "R: ${"%.2f".format(zones[2])}m | " +
                    "range=${"%.3f".format(frameRange)}"
        )

        postResult()
    }

    private fun postResult() {
        overlayView.post {
            overlayView.updateZones(zones)
            val limitPercent = SCAN_END_Y.toFloat() / MODEL_HEIGHT.toFloat()
            overlayView.updateLimitLine(limitPercent)
        }
        feedbackManager.update(zones)
    }
}