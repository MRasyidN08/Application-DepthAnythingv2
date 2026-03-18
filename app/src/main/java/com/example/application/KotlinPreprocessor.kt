package com.example.application

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.PixelFormat
import androidx.camera.core.ImageProxy

class KotlinPreprocessor {
    private val TARGET_WIDTH = 518
    private val TARGET_HEIGHT = 518

    // Standar Normalisasi ImageNet (Wajib untuk Depth Anything)
    private val MEAN_R = 0.485f
    private val MEAN_G = 0.456f
    private val MEAN_B = 0.406f

    private val STD_R = 0.229f
    private val STD_G = 0.224f
    private val STD_B = 0.225f

    private val floatArray = FloatArray(1 * 3 * TARGET_WIDTH * TARGET_HEIGHT)
    private val pixels = IntArray(TARGET_WIDTH * TARGET_HEIGHT)

    private var inputBitmap: Bitmap? = null
    private var outputBitmap: Bitmap? = null

    fun preprocess(imageProxy: ImageProxy): FloatArray? {
        if (imageProxy.format != PixelFormat.RGBA_8888 || imageProxy.planes.size != 1) {
            return null
        }

        val width = imageProxy.width
        val height = imageProxy.height
        val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()
        val buffer = imageProxy.planes[0].buffer

        // Salin buffer ke bitmap mentah
        if (inputBitmap == null || inputBitmap!!.width != width || inputBitmap!!.height != height) {
            inputBitmap?.recycle()
            inputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        buffer.rewind()
        inputBitmap!!.copyPixelsFromBuffer(buffer)

        // Siapkan bitmap output 518x518
        if (outputBitmap == null) {
            outputBitmap = Bitmap.createBitmap(TARGET_WIDTH, TARGET_HEIGHT, Bitmap.Config.ARGB_8888)
        }

        // =======================================================
        // PERBAIKAN UTAMA: Bangun matrix yang benar-benar dipakai
        // untuk menggambar ke outputBitmap via Canvas.
        //
        // Urutan operasi:
        // 1. Rotasi dulu di sekitar pusat gambar asli
        // 2. Scale ke ukuran target
        //
        // Dengan Canvas + Matrix, rotasi BENAR-BENAR diterapkan,
        // berbeda dengan createScaledBitmap yang mengabaikan Matrix.
        // =======================================================
        val matrix = Matrix()

        // Tentukan dimensi setelah rotasi agar scaling proporsional
        val (srcW, srcH) = if (rotation == 90f || rotation == 270f) {
            height.toFloat() to width.toFloat()
        } else {
            width.toFloat() to height.toFloat()
        }

        val scaleX = TARGET_WIDTH / srcW
        val scaleY = TARGET_HEIGHT / srcH

        // Rotasi di sekitar pusat bitmap asli, lalu scale
        matrix.postTranslate(-width / 2f, -height / 2f)   // geser ke pusat
        matrix.postRotate(rotation)                         // rotasi
        matrix.postTranslate(srcW / 2f, srcH / 2f)        // kembalikan
        matrix.postScale(scaleX, scaleY)                   // scale ke target

        val canvas = Canvas(outputBitmap!!)
        canvas.drawBitmap(inputBitmap!!, matrix, null)

        // Ambil piksel dari outputBitmap yang sudah dirotasi & discale
        outputBitmap!!.getPixels(pixels, 0, TARGET_WIDTH, 0, 0, TARGET_WIDTH, TARGET_HEIGHT)

        // Normalisasi ke format CHW (Channel, Height, Width) untuk PyTorch
        val area = TARGET_WIDTH * TARGET_HEIGHT
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val rInt = (pixel shr 16) and 0xFF
            val gInt = (pixel shr 8) and 0xFF
            val bInt = pixel and 0xFF

            floatArray[i]            = ((rInt / 255.0f) - MEAN_R) / STD_R
            floatArray[i + area]     = ((gInt / 255.0f) - MEAN_G) / STD_G
            floatArray[i + area * 2] = ((bInt / 255.0f) - MEAN_B) / STD_B
        }

        return floatArray
    }

    fun release() {
        inputBitmap?.recycle()
        inputBitmap = null
        outputBitmap?.recycle()
        outputBitmap = null
    }
}