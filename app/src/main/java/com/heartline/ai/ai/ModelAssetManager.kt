package com.heartline.ai.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

sealed interface AssetLoadingState {
    data object Checking : AssetLoadingState
    data object Missing : AssetLoadingState
    data object Ready : AssetLoadingState
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : AssetLoadingState {
        val progress: Float = if (totalBytes <= 0L) 0f else bytesDownloaded.toFloat() / totalBytes.toFloat()
    }
    data class Failed(val message: String) : AssetLoadingState
}

class ModelAssetManager(context: Context) {
    private val appContext = context.applicationContext
    private val modelDir = File(appContext.filesDir, "models")
    private val modelFile = File(modelDir, MODEL_FILE_NAME)
    private val partialFile = File(modelDir, "$MODEL_FILE_NAME.download")
    private val verifiedMarkerFile = File(modelDir, "$MODEL_FILE_NAME.sha256")
    private val _state = MutableStateFlow<AssetLoadingState>(AssetLoadingState.Checking)

    val state: StateFlow<AssetLoadingState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = if (isModelReady()) AssetLoadingState.Ready else AssetLoadingState.Missing
    }

    fun getReadyModelFile(): File {
        if (!isModelReady()) {
            throw IllegalStateException("Gemma model is not loaded. Complete Asset Loading before chatting.")
        }
        return modelFile
    }

    suspend fun loadAsset() = withContext(Dispatchers.IO) {
        if (isModelReady()) {
            _state.value = AssetLoadingState.Ready
            return@withContext
        }
        _state.value = AssetLoadingState.Downloading(0L, EXPECTED_MODEL_BYTES)

        runCatching {
            modelDir.mkdirs()
            removeStaleModels()
            downloadModel()
            verifyModel()
            partialFile.delete()
            _state.value = AssetLoadingState.Ready
        }.onFailure { error ->
            modelFile.delete()
            partialFile.delete()
            _state.value = AssetLoadingState.Failed(error.message ?: "Asset loading failed.")
        }
    }

    private fun downloadModel() {
        var existingBytes = partialFile.length().takeIf { it in 1 until EXPECTED_MODEL_BYTES } ?: 0L
        if (partialFile.length() >= EXPECTED_MODEL_BYTES) partialFile.delete()
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            if (existingBytes > 0L) {
                setRequestProperty("Range", "bytes=$existingBytes-")
            }
        }

        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("Model download failed with HTTP ${connection.responseCode}.")
        }
        if (existingBytes > 0L && connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
            partialFile.delete()
            existingBytes = 0L
        }

        connection.inputStream.use { input ->
            FileOutputStream(partialFile, existingBytes > 0L).buffered().use { output ->
                val buffer = ByteArray(1024 * 1024)
                var downloaded = existingBytes
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    _state.value = AssetLoadingState.Downloading(downloaded, EXPECTED_MODEL_BYTES)
                }
            }
        }

        if (partialFile.length() != EXPECTED_MODEL_BYTES) {
            throw IllegalStateException("Downloaded model was ${partialFile.length()} bytes; expected $EXPECTED_MODEL_BYTES bytes.")
        }
        if (modelFile.exists()) modelFile.delete()
        check(partialFile.renameTo(modelFile)) { "Could not save loaded model asset." }
    }

    private fun verifyModel() {
        val actual = modelFile.sha256()
        if (!actual.equals(EXPECTED_SHA256, ignoreCase = true)) {
            modelFile.delete()
            verifiedMarkerFile.delete()
            throw IllegalStateException("Downloaded model checksum did not match.")
        }
        verifiedMarkerFile.writeText(EXPECTED_SHA256)
    }

    private fun isModelReady(): Boolean =
        modelFile.exists() &&
            modelFile.length() == EXPECTED_MODEL_BYTES &&
            if (verifiedMarkerFile.readTextOrNull()?.equals(EXPECTED_SHA256, ignoreCase = true) == true) {
                true
            } else {
                runCatching {
                    modelFile.sha256().equals(EXPECTED_SHA256, ignoreCase = true).also { verified ->
                        if (verified) verifiedMarkerFile.writeText(EXPECTED_SHA256)
                    }
                }.getOrDefault(false)
            }

    private fun removeStaleModels() {
        modelDir.listFiles { file ->
            file.name != MODEL_FILE_NAME && (file.extension == "litertlm" || file.extension == "llmpart")
        }?.forEach { it.delete() }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(Locale.US, it) }
    }

    private fun File.readTextOrNull(): String? = runCatching { readText().trim() }.getOrNull()

    companion object {
        const val MODEL_FILE_NAME = "gemma-4-E2B-it-web.litertlm"
        const val EXPECTED_MODEL_BYTES = 2_008_432_640L
        const val EXPECTED_SHA256 = "3A08E8D94E23B814AE5414469C370C503813949ACB8CEAA17E4EBF8A35AF35B5"
        const val MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-web.litertlm"
    }
}
