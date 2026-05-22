package com.example.mobileaudiowhatsapp.ml

import android.content.Context
import java.io.File

object VoskModelManager {

    fun ensureModel(context: Context): File {
        val modelDir = File(context.filesDir, "vosk-model")
        // If the directory exists and is not empty, we assume it's already extracted
        if (modelDir.exists() && modelDir.list()?.isNotEmpty() == true) {
            return modelDir
        }

        modelDir.mkdirs()
        copyAssetFolder(context, "vosk-model-small-en-in", modelDir)
        return modelDir
    }

    private fun copyAssetFolder(context: Context, srcFolder: String, destFolder: File) {
        val assets = context.assets.list(srcFolder)
        if (assets.isNullOrEmpty()) {
            // It's a file or empty folder
            val relativePath = srcFolder.substringAfter("vosk-model-small-en-in/")
            val targetFile = File(destFolder, relativePath)
            targetFile.parentFile?.mkdirs()
            runCatching {
                context.assets.open(srcFolder).use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }.onFailure {
                // If it was indeed a directory that was empty, let's just make it a directory
                targetFile.mkdirs()
            }
        } else {
            // It's a directory containing files
            for (asset in assets) {
                copyAssetFolder(context, "$srcFolder/$asset", destFolder)
            }
        }
    }

    fun customModelPath(context: Context): String? {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getString("custom_model_path", null)
    }
}
