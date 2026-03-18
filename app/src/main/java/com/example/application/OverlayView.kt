package com.example.application

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private val zones = FloatArray(3)

    private val separatorPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val limitPaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 6f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(30f, 15f), 0f)
    }

    private val textPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 80f
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
    }

    private var limitLineYPercent: Float = 0f

    fun updateZones(newZones: FloatArray) {
        System.arraycopy(newZones, 0, zones, 0, 3)
        invalidate()
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

        canvas.drawLine(zoneWidth, 0f, zoneWidth, height, separatorPaint)
        canvas.drawLine(zoneWidth * 2, 0f, zoneWidth * 2, height, separatorPaint)

        if (limitLineYPercent > 0f) {
            val yPos = height * limitLineYPercent
            canvas.drawLine(0f, yPos, width, yPos, limitPaint)
        }

        for (i in 0 until 3) {
            val distance = zones[i]
            val text = "%.1fm".format(distance)

            val centerX = (i * zoneWidth) + (zoneWidth / 2)
            val centerY = if (limitLineYPercent > 0) {
                (height * limitLineYPercent) - 100f
            } else {
                height / 2
            }

            // Teks menjadi merah jika jarak sangat dekat (di bawah 1 meter)
            if (distance <= 1.0f) {
                textPaint.color = Color.RED
            } else {
                textPaint.color = Color.YELLOW
            }

            canvas.drawText(text, centerX, centerY, textPaint)
        }
    }
}