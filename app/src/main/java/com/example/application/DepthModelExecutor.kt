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

    // PASTIKAN NAMA FILE INI SESUAI HASIL DOWNLOAD DARI COLAB
    private val modelName = "depth_anything_v2_mobile_252.ptl"

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
            // [PENTING] Ubah ukuran tensor input menjadi 252
            val shape = longArrayOf(1, 3, 252, 252)
            val inputTensor = Tensor.fromBlob(input, shape)

            val outputTensor = m.forward(IValue.from(inputTensor)).toTensor()
            outputTensor.dataAsFloatArray
        } catch (e: Exception) {
            Log.e("Executor", "Inference failed", e)
            null
        }
    }

    fun close() {
        module = null
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