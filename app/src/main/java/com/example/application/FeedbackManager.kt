package com.example.application

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale

class FeedbackManager(context: Context) : TextToSpeech.OnInitListener {

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

    private val INTERVAL_BAHAYA = 1500L
    private val INTERVAL_WASPADA = 2500L

    // Threshold Jarak dalam METER
    private val THRESHOLD_DEKAT = 1.0f
    private val THRESHOLD_SEDANG = 2.0f

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("id", "ID"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            isTtsReady = true
        }
    }

    fun update(zones: FloatArray) {
        if (zones.size != 3) return

        // 1. Cek Jarak Terdekat (Min Global) karena satuannya Meter
        var minGlobal = Float.MAX_VALUE
        for (z in zones) { if (z < minGlobal) minGlobal = z }

        if (minGlobal > THRESHOLD_SEDANG) return // Aman jika lebih besar dari 2 meter

        val isDanger = minGlobal <= THRESHOLD_DEKAT
        val T = if (isDanger) THRESHOLD_DEKAT else THRESHOLD_SEDANG

        // 2. Objek terdeteksi jika nilainya LEBIH KECIL (<=) Threshold
        val pL = zones[0] <= T
        val pC = zones[1] <= T
        val pR = zones[2] <= T

        val direction = resolveMessage(pL, pC, pR)
        val distance = if (isDanger) "Dekat" else "Sedang"

        val finalMessage = if (direction.contains("Tembok")) direction else "$direction $distance"

        triggerHaptic(isDanger, pC)
        triggerAudio(finalMessage, isDanger)
    }

    private fun resolveMessage(pL: Boolean, pC: Boolean, pR: Boolean): String {
        return when {
            pL && pC && pR -> "Awas Tembok"
            pL && pC -> "Kiri Depan"
            pR && pC -> "Kanan Depan"
            pL && pR -> "Kiri dan Kanan"
            pC -> "Depan"
            pL -> "Kiri"
            pR -> "Kanan"
            else -> "Hati-hati"
        }
    }

    private fun triggerHaptic(isDanger: Boolean, isCenterHazard: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amp = if (isDanger) 255 else 100
            val dur = if (isDanger) 100L else 50L

            if (isCenterHazard) {
                val timings = longArrayOf(0, 50, 50, 50)
                val amplitudes = intArrayOf(0, amp, 0, amp)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(dur, amp))
            }
        }
    }

    private fun triggerAudio(msg: String, isDanger: Boolean) {
        if (!isTtsReady) return
        val now = System.currentTimeMillis()
        val interval = if (isDanger) INTERVAL_BAHAYA else INTERVAL_WASPADA

        if (msg != lastMessage || (now - lastSpeakTime) > interval) {
            val mode = if (isDanger) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
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