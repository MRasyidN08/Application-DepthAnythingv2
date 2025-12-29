package com.example.application

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class FeedbackManager(context: Context) : TextToSpeech.OnInitListener {

    // --- HARDWARE ---
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var lastSpeakTime = 0L
    private var lastMessage = ""

    // --- KONFIGURASI INTERVAL ---
    private val INTERVAL_BAHAYA = 1500L
    private val INTERVAL_WASPADA = 2500L // Sedikit dipercepat agar info detail sempat terucap

    // --- THRESHOLD KALIBRASI ---
    private val THRESHOLD_DEKAT = 0.7f
    private val THRESHOLD_SEDANG = 0.4f

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
// ... (onInit tetap sama) ...
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("id", "ID"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            isTtsReady = true
        }
    }

    // UBAH: Menerima FloatArray(3)
    fun update(zones: FloatArray) {
        if (zones.size != 3) return // Pastikan ukurannya 3

        // 1. Cek Max Global
        var maxGlobal = 0f
        for (z in zones) { if (z > maxGlobal) maxGlobal = z }

        // Anggap nilai kedalaman model yang kecil (jarak jauh) adalah aman,
        // nilai kedalaman model yang besar (jarak dekat) adalah bahaya.
        if (maxGlobal < THRESHOLD_SEDANG) return // Aman

        val isDanger = maxGlobal >= THRESHOLD_DEKAT
        val T = if (isDanger) THRESHOLD_DEKAT else THRESHOLD_SEDANG

        // 2. DETEKSI 3 ZONA (Kiri, Tengah, Kanan)
        val pL = zones[0] >= T // Path Left
        val pC = zones[1] >= T // Path Center
        val pR = zones[2] >= T // Path Right

        // 3. RESOLVE PESAN SUARA (LOGIKA KOMBINASI 3 ZONA)
        val direction = resolveMessage(pL, pC, pR)
        val distance = if (isDanger) "Dekat" else "Sedang"

        // Jika pesannya "Awas Tembok", tidak perlu sebut jarak lagi biar cepat
        val finalMessage = if (direction.contains("Tembok")) direction else "$direction $distance"

        // 4. EKSEKUSI
        triggerHaptic(isDanger, pC) // Pola getar beda jika bahaya di tengah
        triggerAudio(finalMessage, isDanger)
    }

    // UBAH: Hanya menerima 3 Boolean
    private fun resolveMessage(pL: Boolean, pC: Boolean, pR: Boolean): String {
        return when {
            // --- PRIORITAS 1: BLOKIR TOTAL ---
            pL && pC && pR -> "Awas Tembok"

            // --- PRIORITAS 2: KOMBINASI GANDA ---
            pL && pC -> "Kiri Depan"
            pR && pC -> "Kanan Depan"
            pL && pR -> "Kiri dan Kanan" // Celah di tengah

            // --- PRIORITAS 3: SINGLE DIRECTION ---
            pC -> "Depan"
            pL -> "Kiri"
            pR -> "Kanan"

            else -> "Hati-hati"
        }
    }

    // UBAH: isHeadHazard diganti isCenterHazard
    private fun triggerHaptic(isDanger: Boolean, isCenterHazard: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amp = if (isDanger) 255 else 100
            val dur = if (isDanger) 100L else 50L

            // Jika bahaya di TENGAH, pola getar berbeda (Double Pulse)
            if (isCenterHazard) {
                val timings = longArrayOf(0, 50, 50, 50) // Getar-Diam-Getar
                val amplitudes = intArrayOf(0, amp, 0, amp)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                // Getar Biasa (Single Pulse)
                vibrator.vibrate(VibrationEffect.createOneShot(dur, amp))
            }
        }
    }

    // ... (Bagian triggerAudio dan release tetap sama) ...
    private fun triggerAudio(msg: String, isDanger: Boolean) {
        if (!isTtsReady) return
        val now = System.currentTimeMillis()
        val interval = if (isDanger) INTERVAL_BAHAYA else INTERVAL_WASPADA

        // Force speak jika pesan berubah drastis (misal dari "Kiri" jadi "AWAS TEMBOK")
        if (msg != lastMessage || (now - lastSpeakTime) > interval) {

            val mode = if (isDanger) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD

            // Tambahkan intonasi darurat teks
            val spokenText = if (isDanger && (msg.contains("Tembok") || msg.contains("Kepala"))) {
                "BERHENTI! $msg"
            } else {
                msg
            }

            tts?.speak(spokenText, mode, null, null)
            lastMessage = msg
            lastSpeakTime = now
        }
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
    }
}