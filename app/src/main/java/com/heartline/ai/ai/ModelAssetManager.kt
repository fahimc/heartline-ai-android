package com.heartline.ai.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
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
            throw IllegalStateException("App assets are not ready. Complete Asset Loading before chatting.")
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
            copyBundledAsset()
            verifyModel()
            partialFile.delete()
            _state.value = AssetLoadingState.Ready
        }.onFailure { error ->
            modelFile.delete()
            partialFile.delete()
            _state.value = AssetLoadingState.Failed(error.message ?: "Asset loading failed.")
        }
    }

    private fun copyBundledAsset() {
        partialFile.delete()
        appContext.assets.open("$MODEL_ASSET_DIR/$MODEL_FILE_NAME").buffered().use { input ->
            partialFile.outputStream().buffered().use { output ->
                val buffer = ByteArray(1024 * 1024)
                var downloaded = 0L
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
            throw IllegalStateException("Loaded assets were ${partialFile.length()} bytes; expected $EXPECTED_MODEL_BYTES bytes.")
        }
        if (modelFile.exists()) modelFile.delete()
        check(partialFile.renameTo(modelFile)) { "Could not save loaded model asset." }
    }

    private fun verifyModel() {
        val actual = modelFile.sha256()
        if (!actual.equals(EXPECTED_SHA256, ignoreCase = true)) {
            modelFile.delete()
            verifiedMarkerFile.delete()
            throw IllegalStateException("Loaded assets could not be verified.")
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
        const val MODEL_FILE_NAME = "qwen35_mm_q8_ekv2048.litertlm"
        const val MODEL_ASSET_DIR = "models"
        const val EXPECTED_MODEL_BYTES = 1_159_757_824L
        const val EXPECTED_SHA256 = "92999FE4A9242C983E99892D6E57F368E8CD7A4534BC9A383A9551155B7F70A5"
    }
}
