package com.example.application

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private val zones = FloatArray(3) // Menyimpan data jarak

    // 1. CAT UNTUK GARIS PEMISAH VERTIKAL (PUTIH)
    private val separatorPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    // 2. CAT UNTUK GARIS BATAS BAWAH (KUNING PUTUS-PUTUS)
    private val limitPaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 6f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(30f, 15f), 0f)
    }

    // 3. CAT UNTUK TEKS ANGKA JARAK (KUNING TEBAL)
    private val textPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 80f           // Ukuran teks besar
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER // Rata tengah
        isFakeBoldText = true    // Teks tebal
        setShadowLayer(5f, 0f, 0f, Color.BLACK) // Bayangan hitam agar terbaca di latar terang
    }

    private var limitLineYPercent: Float = 0f

    fun updateZones(newZones: FloatArray) {
        System.arraycopy(newZones, 0, zones, 0, 3)
        invalidate() // Refresh layar agar angka berubah
    }

    fun updateLimitLine(percent: Float) {
        limitLineYPercent = percent
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val zoneWidth = width / 3

        // --- A. GAMBAR GARIS VERTIKAL (PEMISAH) ---
        canvas.drawLine(zoneWidth, 0f, zoneWidth, height, separatorPaint)
        canvas.drawLine(zoneWidth * 2, 0f, zoneWidth * 2, height, separatorPaint)

        // --- B. GAMBAR GARIS LIMIT (BATAS BAWAH) ---
        if (limitLineYPercent > 0f) {
            val yPos = height * limitLineYPercent
            canvas.drawLine(0f, yPos, width, yPos, limitPaint)
        }

        // --- C. GAMBAR TEKS JARAK ---
        for (i in 0 until 3) {
            val distance = zones[i]

            // Format teks: "1.2m"
            val text = "%.1fm".format(distance)

            // Posisi X: Di tengah-tengah kolom
            val centerX = (i * zoneWidth) + (zoneWidth / 2)

            // Posisi Y: Sedikit di atas garis kuning (limit line) agar mudah dilihat
            // Kalau limitLine belum ada, taruh di tengah layar
            val centerY = if (limitLineYPercent > 0) {
                (height * limitLineYPercent) - 100f // 100 pixel di atas garis
            } else {
                height / 2
            }

            // Ubah warna teks jadi Merah jika sangat dekat (< 1.2m)
            if (distance < 1.2f) {
                textPaint.color = Color.RED
            } else {
                textPaint.color = Color.YELLOW
            }

            canvas.drawText(text, centerX, centerY, textPaint)
        }
    }
}