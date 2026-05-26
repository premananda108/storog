package ua.pp.prema.storog.engine

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// ── Model catalog ─────────────────────────────────────────────────────────────

data class ModelInfo(val name: String, val url: String, val sizeGb: Double = 0.0, val minRamGb: Double = 0.0)

val AVAILABLE_MODELS = listOf(
    ModelInfo("gemma-4-E2B(2.6GB)", "https://pub-b07512464f924792a1bb4c7b3571db1e.r2.dev/gemma-4-E2B-it.litertlm", 2.6, 3.6),
    ModelInfo("gemma-4-E4B(3.7GB)", "https://pub-b07512464f924792a1bb4c7b3571db1e.r2.dev/gemma-4-E4B-it.litertlm", 3.7, 4.5)
)

object PreflightLimits {
    const val MIN_RAM_GB = 3.6
    const val RECOMMENDED_RAM_GB = 5.2
    const val MIN_STORAGE_GB = 3.0
    const val RECOMMENDED_STORAGE_GB = 4.5
}

// ── Download progress ─────────────────────────────────────────────────────────

sealed class DownloadState {
    data class Progress(val percent: Int) : DownloadState()
    data class Done(val file: File) : DownloadState()
    data class Error(val exception: DownloadException) : DownloadState()
}

// ── Download errors ───────────────────────────────────────────────────────────

sealed class DownloadException(msg: String) : Exception(msg) {
    class NoSpace : DownloadException("no space")
    class HttpError(val code: Int) : DownloadException("HTTP $code")
    class Timeout : DownloadException("timeout")
    class IoError(val detail: String) : DownloadException(detail)
    class LowRam(val deviceRam: Double, val requiredRam: Double) : DownloadException("low ram")
}

// ── Engine result ─────────────────────────────────────────────────────────────

data class EngineHandle(val engine: Engine, val backend: String, val modelName: String)

// ── LiteRtManager ─────────────────────────────────────────────────────────────

/**
 * Manages LiteRT Engine lifecycle.
 * Created in ChatViewModel and lives as long as the ViewModel (survives screen rotation).
 */
class LiteRtManager(private val context: Context) {

    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var engineHandle: EngineHandle? = null

    val activeHandle: EngineHandle? get() = engineHandle

    // ── Engine init ───────────────────────────────────────────────────────────

    /**
     * Initializes Engine from model file.
     * Must be called from Dispatchers.IO.
     * @param skipMemoryCheck if true — skips lowMemory check (used when
     *   restarting engine after cancelling generation, when C++ thread might still hold memory).
     * @throws Exception with categorized message on error
     */
    suspend fun initEngine(modelFile: File, preferGpu: Boolean, skipMemoryCheck: Boolean = false): EngineHandle =
        withContext(Dispatchers.IO) {
            // Close old engine FIRST — only then check available memory.
            // engine.close() can block if the C++ inference thread is still running.
            // withTimeoutOrNull(3000) ensures we never hang for more than 3 seconds.
            // If it times out we abandon the old engine (it will be GC-finalized later).
            val closed = withTimeoutOrNull(3_000L) {
                try {
                    engineHandle?.engine?.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing previous engine (ignoring): ${e.message}")
                }
            }
            if (closed == null) {
                Log.w(TAG, "engine.close() timed out after 3s — abandoning old engine")
            }
            engineHandle = null

            // Check the clean shutdown flag and clear the cache if last run was interrupted/crashed
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val cleanShutdown = prefs.getBoolean("engine_clean_shutdown", true)
            val cacheDir = File(context.cacheDir, "litert_cache")
            if (!cleanShutdown) {
                Log.w(TAG, "Last shutdown was not clean (possible crash or interruption). Clearing LiteRT cache.")
                try {
                    cacheDir.deleteRecursively()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear LiteRT cache directory", e)
                }
                prefs.edit().putBoolean("engine_clean_shutdown", true).apply()
            }
            cacheDir.mkdirs()

            runPreflight(modelFile, skipMemoryCheck)

            // Mark shutdown as not clean before initialization starts
            prefs.edit().putBoolean("engine_clean_shutdown", false).apply()

            try {
                val handle = tryCreateEngine(modelFile.absolutePath, preferGpu)
                engineHandle = handle
                // Successful initialization, set flag back to true
                prefs.edit().putBoolean("engine_clean_shutdown", true).apply()
                handle
            } catch (e: Exception) {
                // If initialization failed, we still want to reset the flag to true since we handled the error cleanly
                prefs.edit().putBoolean("engine_clean_shutdown", true).apply()
                throw e
            }
        }

    /**
     * @param skipMemoryCheck if true — does not check OS lowMemory flag.
     *   Useful when restarting immediately after cancellation, when old C++ thread
     *   might still be releasing memory and OS temporarily reports lowMemory=true.
     */
    private fun runPreflight(modelFile: File, skipMemoryCheck: Boolean = false) {
        val abis = Build.SUPPORTED_ABIS.toList()
        Log.d(TAG, "=== Engine Init === Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        Log.d(TAG, "ABIs: $abis  SDK: ${Build.VERSION.SDK_INT}")

        if (!abis.contains("arm64-v8a")) {
            throw Exception("unsupported abi: arm64-v8a required, found ${abis.joinToString()}")
        }
        if (!modelFile.exists()) {
            throw Exception("not found: ${modelFile.absolutePath}")
        }

        val actMgr  = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo().also { actMgr.getMemoryInfo(it) }
        val ramGb   = memInfo.totalMem.toDouble() / GiB
        val availGb = memInfo.availMem.toDouble() / GiB
        Log.d(TAG, "RAM Total: %.1f GB, Avail: %.1f GB, LowMemory: ${memInfo.lowMemory} (skipCheck=$skipMemoryCheck)")
        
        if (ramGb < 2.5) {
            throw Exception("out of memory: device has only %.1f GB RAM".format(ramGb))
        }
        
        if (!skipMemoryCheck && memInfo.lowMemory) {
            throw Exception("out of memory: device is currently in a low memory state (avail: %.1f GB)".format(availGb))
        }

        val modelSizeGb = modelFile.length().toDouble() / GiB
        if (ramGb < modelSizeGb + 0.5) {
            throw Exception("out of memory: total RAM (%.1f GB) is too low for model size (%.1f GB)".format(ramGb, modelSizeGb))
        }
    }

    private fun tryCreateEngine(modelPath: String, preferGpu: Boolean): EngineHandle {
        val cache = File(context.cacheDir, "litert_cache").apply { mkdirs() }.absolutePath

        if (preferGpu) {
            try {
                Log.d(TAG, "Trying GPU…")
                val eng = Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.GPU(),
                        visionBackend = Backend.GPU(),
                        audioBackend = null,
                        cacheDir = cache
                    )
                ).apply { initialize() }
                Log.d(TAG, "GPU OK")
                return EngineHandle(eng, "GPU", File(modelPath).nameWithoutExtension)
            } catch (e: Exception) {
                val rawMsg = e.message ?: "unknown"
                Log.w(TAG, "GPU failed: $rawMsg — falling back to CPU")
                // GPU failure is reported to ViewModel via gpuFallbackReason
                gpuFallbackReason = when {
                    rawMsg.contains("OpenCL", ignoreCase = true) -> "OpenCL is not supported"
                    rawMsg.contains("Vulkan", ignoreCase = true)  -> "Vulkan is not supported"
                    else -> rawMsg
                }
                // Persist preference to avoid trying GPU again on next launch
                try {
                    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("prefer_gpu", false)
                        .apply()
                } catch (_: Exception) {
                }
            }
        }

        Log.d(TAG, "Using CPU backend")
        val eng = Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                visionBackend = Backend.CPU(),
                audioBackend = null,
                cacheDir = cache
            )
        ).apply { initialize() }
        return EngineHandle(eng, "CPU", File(modelPath).nameWithoutExtension)
    }

    /** If GPU failed and switched to CPU — here is the reason for display to user. */
    var gpuFallbackReason: String? = null
        private set

    fun clearGpuFallback() { gpuFallbackReason = null }

    // ── Inference ─────────────────────────────────────────────────────────────
    private val inferenceMutex = Mutex()

    /**
     * Sends message to model. Returns Flow<String> of tokens.
     * Flow completes after the last token or with an exception on error.
     */
    fun generateResponse(
        prompt: String,
        imageFile: File?,
        audioBytes: ByteArray?
    ): Flow<String> = flow {
        val handle = engineHandle ?: throw IllegalStateException("Engine not initialized")
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        inferenceMutex.withLock {
            // Set flag to false before running inference
            prefs.edit().putBoolean("engine_clean_shutdown", false).apply()
            try {
                handle.engine.createConversation().use { conv ->
                    val contentList = mutableListOf<Content>()
                    imageFile?.let { contentList.add(Content.ImageFile(it.absolutePath)) }
                    audioBytes?.let { contentList.add(Content.AudioBytes(it)) }
                    when {
                        prompt.isNotEmpty() -> contentList.add(Content.Text(prompt))
                        audioBytes != null  -> contentList.add(Content.Text("Transcribe or describe this audio"))
                        else                -> contentList.add(Content.Text("Describe this image"))
                    }

                    conv.sendMessageAsync(Contents.of(contentList))
                        .collect { msg ->
                            // NOTE: ensureActive() intentionally removed.
                            // Calling it here converts coroutine cancellation into an immediate
                            // exit from collect, which triggers an early close() on the native
                            // Conversation while the C++ inference thread is still running —
                            // causing a crash. Cancellation is handled by stopRequested flag
                            // in ChatViewModel instead (soft-stop pattern).
                            emit(msg.text)
                        }
                }
                // Completed successfully
                prefs.edit().putBoolean("engine_clean_shutdown", true).apply()
            } catch (e: Throwable) {
                // If it was cancelled or failed but cleanly handled, reset flag to true
                prefs.edit().putBoolean("engine_clean_shutdown", true).apply()
                throw e
            }
        }
    }.flowOn(Dispatchers.IO)

    // ── Download ──────────────────────────────────────────────────────────────

    /**
     * Downloads model from URL. Returns Flow<DownloadState>.
     * Checks free space before starting download.
     */
    fun downloadModel(model: ModelInfo, targetFile: File): Flow<DownloadState> = flow {
        val partFile = File(targetFile.parentFile, "${targetFile.name}.part")
        
        val actMgr  = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo().also { actMgr.getMemoryInfo(it) }
        val ramGb   = memInfo.totalMem.toDouble() / GiB
        if (model.minRamGb > 0 && ramGb < model.minRamGb) {
            throw DownloadException.LowRam(ramGb, model.minRamGb)
        }

        if (model.sizeGb > 0) {
            val stat = StatFs(targetFile.parentFile!!.absolutePath)
            val free = stat.availableBlocksLong * stat.blockSizeLong
            val requiredBytes = (model.sizeGb * 1024 * 1024 * 1024).toLong() + (100 * 1024 * 1024L)
            if (free < requiredBytes) {
                throw DownloadException.NoSpace()
            }
        }

        try {
            val url        = URL(model.url)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout    = 60_000
                setRequestProperty("Accept-Encoding", "identity") // exact Content-Length
                connect()
            }

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw DownloadException.HttpError(code)
            }

            val fileLength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                connection.contentLengthLong
            else
                connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L

            if (fileLength > 0) {
                val stat = StatFs(targetFile.parentFile!!.absolutePath)
                val free = stat.availableBlocksLong * stat.blockSizeLong
                if (free < fileLength + 100 * 1024 * 1024L) {
                    throw DownloadException.NoSpace()
                }
            }

            connection.inputStream.use { input ->
                FileOutputStream(partFile).use { output ->
                    val buf         = ByteArray(65_536)
                    var total       = 0L
                    var lastPercent = -1
                    var count: Int

                    while (input.read(buf).also { count = it } != -1) {
                        currentCoroutineContext().ensureActive()
                        output.write(buf, 0, count)
                        total += count
                        if (fileLength > 0) {
                            val pct = (total * 100 / fileLength).toInt()
                            if (pct != lastPercent) {
                                lastPercent = pct
                                emit(DownloadState.Progress(pct))
                            }
                        }
                    }
                    output.flush()
                    
                    if (fileLength > 0 && total != fileLength) {
                        throw IOException("Incomplete download: expected $fileLength bytes, got $total")
                    }
                }
            }
            
            if (!partFile.renameTo(targetFile)) {
                throw IOException("Atomic rename failed")
            }
            
            emit(DownloadState.Done(targetFile))
        } finally {
            if (!currentCoroutineContext().isActive || !targetFile.exists()) {
                partFile.delete()
            }
        }
    }.retryWhen { cause, attempt ->
        if (cause is CancellationException || cause is DownloadException.NoSpace) {
            return@retryWhen false
        }
        if (cause is DownloadException.HttpError && cause.code in 400..499) {
            return@retryWhen false
        }
        if (attempt < 2) {
            delay(2000L * (attempt + 1))
            return@retryWhen true
        }
        false
    }.catch { e ->
        if (e is CancellationException) throw e
        val exception = e as? DownloadException ?: DownloadException.IoError(e.message ?: "unknown")
        emit(DownloadState.Error(exception))
    }.flowOn(Dispatchers.IO)

    // ── Error categorization ──────────────────────────────────────────────────

    fun categorizeEngineError(e: Exception, modelFile: File? = null): EngineErrorKind {
        val raw = e.message?.lowercase() ?: ""
        return when {
            raw.contains("out of memory") || raw.contains("oom") || raw.contains("failed to allocate") ->
                EngineErrorKind.OutOfMemory
            raw.contains("not found") || raw.contains("no such file") -> {
                modelFile?.delete()
                EngineErrorKind.FileNotFound
            }
            raw.contains("corrupt") || raw.contains("invalid") || raw.contains("magic") ||
            raw.contains("parse")   || raw.contains("flatbuffer") -> {
                modelFile?.delete()
                EngineErrorKind.Corrupt
            }
            raw.contains("arm64") || raw.contains("abi") || raw.contains("unsupported abi") ->
                EngineErrorKind.UnsupportedAbi
            else -> EngineErrorKind.Generic(e.message ?: "unknown")
        }
    }

    fun categorizeInferenceError(e: Throwable): InferenceErrorKind {
        val raw = e.message?.lowercase() ?: ""
        return when {
            raw.contains("context") && (raw.contains("limit") || raw.contains("overflow") || raw.contains("length")) ->
                InferenceErrorKind.ContextOverflow
            raw.contains("out of memory") || raw.contains("oom") || raw.contains("failed to allocate") ->
                InferenceErrorKind.OutOfMemory
            else -> InferenceErrorKind.Generic(e.message ?: "unknown")
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    fun downloadedModels(): List<File> =
        context.filesDir.listFiles { _, n -> n.endsWith(".litertlm") }?.toList() ?: emptyList()

    fun availableModels(): List<ModelInfo> = AVAILABLE_MODELS

    fun close() {
        val handle = engineHandle
        engineHandle = null
        if (handle != null) {
            managerScope.launch {
                try {
                    withTimeoutOrNull(3_000L) {
                        handle.engine.close()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing engine in background: ${e.message}")
                }
            }
        }
        Log.d(TAG, "Engine closed")
    }

    companion object {
        private const val TAG = "LiteRtManager"
        private const val GiB = 1024.0 * 1024 * 1024
    }
}

// ── Error kinds ───────────────────────────────────────────────────────────────

sealed class EngineErrorKind {
    object OutOfMemory    : EngineErrorKind()
    object FileNotFound   : EngineErrorKind()
    object Corrupt        : EngineErrorKind()
    object UnsupportedAbi : EngineErrorKind()
    data class Generic(val message: String) : EngineErrorKind()
}

sealed class InferenceErrorKind {
    object ContextOverflow : InferenceErrorKind()
    object OutOfMemory     : InferenceErrorKind()
    data class Generic(val message: String) : InferenceErrorKind()
}

// ── Extension: extract text from Message ─────────────────────────────────────

val Message.text: String
    get() = contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
