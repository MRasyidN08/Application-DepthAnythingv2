package com.example.application

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PixelFormat
import androidx.camera.core.ImageProxy

class KotlinPreprocessor {
    // Ukuran Target (Versi Ringan)
    private val TARGET_WIDTH = 252
    private val TARGET_HEIGHT = 252

    // Standar Normalisasi ImageNet (Wajib untuk Depth Anything)
    private val MEAN_R = 0.485f
    private val MEAN_G = 0.456f
    private val MEAN_B = 0.406f

    private val STD_R = 0.229f
    private val STD_G = 0.224f
    private val STD_B = 0.225f

    // Buffer Output (Ukuran 252)
    private val floatArray = FloatArray(1 * 3 * TARGET_WIDTH * TARGET_HEIGHT)
    private var pixels = IntArray(TARGET_WIDTH * TARGET_HEIGHT)

    private var inputBitmap: Bitmap? = null
    private var scaledBitmap: Bitmap? = null

    fun preprocess(imageProxy: ImageProxy): FloatArray? {
        // 1. Validasi Format (Harus RGBA_8888)
        if (imageProxy.format != PixelFormat.RGBA_8888 && imageProxy.planes.size != 1) {
            return null
        }

        val width = imageProxy.width
        val height = imageProxy.height
        val buffer = imageProxy.planes[0].buffer

        // 2. Salin data kamera ke Bitmap Asli
        if (inputBitmap == null || inputBitmap!!.width != width || inputBitmap!!.height != height) {
            inputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        buffer.rewind()
        inputBitmap!!.copyPixelsFromBuffer(buffer)

        // 3. Resize ke 252x252 (Downscale)
        if (scaledBitmap == null) {
            scaledBitmap = Bitmap.createBitmap(TARGET_WIDTH, TARGET_HEIGHT, Bitmap.Config.ARGB_8888)
        }

        // Hitung skala
        val scaleX = TARGET_WIDTH.toFloat() / width
        val scaleY = TARGET_HEIGHT.toFloat() / height
        val matrix = Matrix()
        matrix.postScale(scaleX, scaleY)

        // Rotasi (Opsional: sesuaikan dengan orientasi device Anda)
        val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()
        if (rotation != 0f) {
            matrix.postRotate(rotation, TARGET_WIDTH/2f, TARGET_HEIGHT/2f)
        }

        // Gambar ulang ke Bitmap Kecil (252x252)
        val canvas = android.graphics.Canvas(scaledBitmap!!)
        canvas.drawColor(android.graphics.Color.BLACK) // Reset background

        // Teknik createScaledBitmap lebih cepat untuk downscaling
        val tempScaled = Bitmap.createScaledBitmap(inputBitmap!!, TARGET_WIDTH, TARGET_HEIGHT, true)

        // 4. Ambil Pixels
        tempScaled.getPixels(pixels, 0, TARGET_WIDTH, 0, 0, TARGET_WIDTH, TARGET_HEIGHT)

        if (tempScaled != inputBitmap) {
            tempScaled.recycle()
        }

        // 5. KONVERSI KE FLOAT + NORMALISASI
        val area = TARGET_WIDTH * TARGET_HEIGHT

        for (i in pixels.indices) {
            val pixel = pixels[i]

            // Ekstrak RGB
            val rInt = (pixel shr 16) and 0xFF
            val gInt = (pixel shr 8) and 0xFF
            val bInt = (pixel) and 0xFF

            // Rumus: ( (Value/255.0) - Mean ) / Std
            floatArray[i] = ((rInt / 255.0f) - MEAN_R) / STD_R
            floatArray[i + area] = ((gInt / 255.0f) - MEAN_G) / STD_G
            floatArray[i + area * 2] = ((bInt / 255.0f) - MEAN_B) / STD_B
        }

        return floatArray
    }
}