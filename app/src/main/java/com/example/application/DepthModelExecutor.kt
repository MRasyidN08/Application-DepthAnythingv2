package com.example.application

import android.content.Context
import android.util.Log
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream

class DepthModelExecutor(private val context: Context) {

    private var module: Module? = null

    private val modelName = "depth_anything_v2_vits_mobile_int8.ptl"

    init {
        loadModule()
    }

    private fun loadModule() {
        try {
            val modelPath = copyAssetIfNeeded(context, modelName)
            module = LiteModuleLoader.load(modelPath)
            Log.d("Executor", "Model loaded successfully")
        } catch (e: Exception) {
            Log.e("Executor", "Error loading model", e)
        }
    }

    fun submitInference(input: FloatArray): FloatArray? {
        val m = module ?: return null

        return try {
            val shape = longArrayOf(1, 3, 518, 518)
            val inputTensor = Tensor.fromBlob(input, shape)
            val outputTensor = m.forward(IValue.from(inputTensor)).toTensor()
            outputTensor.dataAsFloatArray
        } catch (e: Exception) {
            Log.e("Executor", "Inference failed", e)
            null
        }
    }

    // =======================================================
    // PERBAIKAN: Panggil destroy() agar native memory PyTorch
    // benar-benar dibebaskan, bukan hanya di-nullify.
    // Tanpa ini, memory native tidak di-GC oleh JVM dan bisa
    // menyebabkan memory leak / OOM saat aplikasi ditutup.
    // =======================================================
    fun close() {
        try {
            module?.destroy()
        } catch (e: Exception) {
            Log.e("Executor", "Error destroying module", e)
        } finally {
            module = null
        }
    }

    private fun copyAssetIfNeeded(context: Context, name: String): String {
        val out = File(context.filesDir, name)
        if (!out.exists()) {
            context.assets.open(name).use { input ->
                FileOutputStream(out).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return out.absolutePath
    }
}