package com.zenith.assistant

import android.app.ActivityManager
import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class LocalModelSpec(
    val id: String,
    val name: String,
    val filename: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val ramRequiredMb: Int,
    val description: String,
    val languages: String
)

interface DownloadCallback {
    fun onProgress(bytesDownloaded: Long, totalBytes: Long, speedKbps: Long)
    fun onSuccess(file: File)
    fun onError(error: String)
}

object AvailableModels {
    val MODELS = listOf(
        LocalModelSpec(
            id = "qwen2.5-0.5b",
            name = "Qwen 2.5 0.5B Instruct (Q4_K_M)",
            filename = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            sizeBytes = 398_000_000L, // ~380 MB
            ramRequiredMb = 800,
            description = "Ultra-fast lightweight local model. Highly recommended for 4GB-6GB RAM phones.",
            languages = "English, Hindi, Marathi, Multilingual"
        ),
        LocalModelSpec(
            id = "llama3.2-1b",
            name = "Llama 3.2 1B Instruct (Q4_K_M)",
            filename = "llama-3.2-1b-instruct-q4_k_m.gguf",
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            sizeBytes = 800_000_000L, // ~800 MB
            ramRequiredMb = 1400,
            description = "Balanced assistant model with high reasoning ability for daily commands.",
            languages = "English, Multilingual"
        ),
        LocalModelSpec(
            id = "qwen2.5-1.5b",
            name = "Qwen 2.5 1.5B Instruct (Q4_K_M)",
            filename = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            sizeBytes = 1_120_000_000L, // ~1.1 GB
            ramRequiredMb = 2000,
            description = "High accuracy model with strong Hindi and Marathi understanding.",
            languages = "English, Hindi, Marathi, Multilingual"
        )
    )
}

class ModelDownloadManager(private val ctx: Context) {
    private val exec = Executors.newSingleThreadExecutor()
    @Volatile private var isCancelled = false

    fun getModelsDir(): File {
        val dir = File(ctx.getExternalFilesDir(null), "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getModelFile(spec: LocalModelSpec): File {
        return File(getModelsDir(), spec.filename)
    }

    fun isModelDownloaded(spec: LocalModelSpec): Boolean {
        val f = getModelFile(spec)
        return f.exists() && f.length() > 0
    }

    fun getDownloadedModels(): List<File> {
        val dir = getModelsDir()
        return dir.listFiles { _, name -> name.endsWith(".gguf") }?.toList() ?: emptyList()
    }

    fun deleteModel(spec: LocalModelSpec): Boolean {
        val f = getModelFile(spec)
        return if (f.exists()) f.delete() else false
    }

    fun cancelDownload() {
        isCancelled = true
    }

    fun downloadModel(spec: LocalModelSpec, callback: DownloadCallback) {
        isCancelled = false
        exec.execute {
            var conn: HttpURLConnection? = null
            var input: InputStream? = null
            var output: FileOutputStream? = null
            val outputFile = getModelFile(spec)
            val tempFile = File(getModelsDir(), "${spec.filename}.tmp")

            try {
                val url = URL(spec.downloadUrl)
                conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true
                conn.connect()

                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    callback.onError("Server returned HTTP ${conn.responseCode}: ${conn.responseMessage}")
                    return@execute
                }

                val totalBytes = if (conn.contentLengthLong > 0) conn.contentLengthLong else spec.sizeBytes
                input = conn.inputStream
                output = FileOutputStream(tempFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var downloadedBytes = 0L
                var startTime = System.currentTimeMillis()
                var lastProgressTime = startTime

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (isCancelled) {
                        output.close()
                        input.close()
                        tempFile.delete()
                        callback.onError("Download cancelled by user.")
                        return@execute
                    }

                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastProgressTime > 500) {
                        val elapsedSec = (now - startTime) / 1000.0
                        val speedKbps = if (elapsedSec > 0) ((downloadedBytes / 1024) / elapsedSec).toLong() else 0L
                        callback.onProgress(downloadedBytes, totalBytes, speedKbps)
                        lastProgressTime = now
                    }
                }

                output.flush()
                output.close()
                input.close()

                if (tempFile.renameTo(outputFile)) {
                    callback.onSuccess(outputFile)
                } else {
                    tempFile.copyTo(outputFile, overwrite = true)
                    tempFile.delete()
                    callback.onSuccess(outputFile)
                }

            } catch (e: Exception) {
                tempFile.delete()
                callback.onError("Download failed: ${e.localizedMessage}")
            } finally {
                try {
                    output?.close()
                    input?.close()
                    conn?.disconnect()
                } catch (_: Exception) {}
            }
        }
    }

    fun getDeviceMemoryStats(): String {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalGb = String.format("%.1f", memInfo.totalMem / (1024.0 * 1024.0 * 1024.0))
        val availGb = String.format("%.1f", memInfo.availMem / (1024.0 * 1024.0 * 1024.0))
        return "Available RAM: $availGb GB / Total: $totalGb GB"
    }
}
