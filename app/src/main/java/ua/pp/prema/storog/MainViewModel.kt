package ua.pp.prema.storog

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.pp.prema.storog.engine.DownloadException
import ua.pp.prema.storog.engine.DownloadState
import ua.pp.prema.storog.engine.EngineErrorKind
import ua.pp.prema.storog.engine.LiteRtManager
import ua.pp.prema.storog.engine.ModelInfo
import ua.pp.prema.storog.engine.PreflightChecker
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Properties

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // ── State ──────────────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow(StorogUiState())
    val uiState: StateFlow<StorogUiState> = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    // ── Dependencies ────────────────────────────────────────────────────────
    private lateinit var telegramSender: TelegramBotSender
    private lateinit var liteRtService: LiteRtService
    private lateinit var liteRtManager: LiteRtManager
    private lateinit var preflightChecker: PreflightChecker
    
    private var messagesSent = 0 // Sent messages counter

    init {
        val properties = Properties()
        liteRtManager = LiteRtManager(getApplication())
        preflightChecker = PreflightChecker(getApplication())
        liteRtService = LiteRtService(getApplication(), liteRtManager)

        val sharedPreferences = getApplication<Application>().getSharedPreferences("StorogSettings", Context.MODE_PRIVATE)
        val targetChatId = sharedPreferences.getString("TARGET_CHAT_ID", null)
        val botToken = sharedPreferences.getString("MY_BOT_TOKEN", null)

        if (!botToken.isNullOrBlank() && !targetChatId.isNullOrBlank()) {
            telegramSender = TelegramBotSender(botToken, targetChatId)
        } else {
            Log.i(TAG, "Telegram not configured (missing token or chat id).")
            _events.trySend(UiEvent.ShowError("Telegram not configured. Go to Settings."))
        }
        
        // Start model initialization sequence. LiteRT model can still be loaded without Telegram config.
        runPreflightAndLoadModel()
    }

    // ── Preflight & Model Loading ──────────────────────────────────────────

    private fun runPreflightAndLoadModel() {
        // If models are already downloaded, skip storage check
        val hasModels = getApplication<Application>().filesDir.listFiles { _, n -> n.endsWith(".litertlm") }?.isNotEmpty() == true
        if (hasModels) {
            onPreflightAccepted()
            return
        }

        val result = preflightChecker.check()
        if (!result.canRun || result.warnings.isNotEmpty()) {
            _events.trySend(UiEvent.ShowPreflight(result))
        } else {
            proceedWithModelLoad()
        }
    }

    fun onPreflightAccepted() {
        proceedWithModelLoad()
    }

    private fun proceedWithModelLoad() {
        val downloaded = liteRtManager.downloadedModels()
        if (downloaded.isEmpty()) {
            _events.trySend(UiEvent.ShowModelSelection)
            _uiState.update { it.copy(modelStatus = ModelStatus.NotLoaded) }
        } else {
            val prefs = getApplication<Application>().getSharedPreferences("app_prefs", Application.MODE_PRIVATE)
            val lastModelName = prefs.getString("last_model_name", null)
            val modelToLoad = if (lastModelName != null) {
                downloaded.firstOrNull { it.name == lastModelName } ?: downloaded[0]
            } else {
                downloaded[0]
            }
            loadModel(modelToLoad)
        }
    }

    /**
     * Loads model from file and initializes the LiteRT engine.
     * @param skipMemoryCheck if true, skips lowMemory check (used when reloading after cancellation)
     */
    fun loadModel(modelFile: File, skipMemoryCheck: Boolean = false) {
        val preferGpu = getApplication<Application>()
            .getSharedPreferences("app_prefs", Application.MODE_PRIVATE)
            .getBoolean("prefer_gpu", false)

        _uiState.update { it.copy(modelStatus = ModelStatus.Loading(modelFile.nameWithoutExtension), isMonitoringEnabled = false) }

        viewModelScope.launch {
            try {
                val handle = liteRtManager.initEngine(modelFile, preferGpu, skipMemoryCheck)

                // Check if there was a GPU→CPU fallback
                val fallback = liteRtManager.gpuFallbackReason
                if (fallback != null) {
                    _events.trySend(UiEvent.ShowGpuFallback(fallback))
                    liteRtManager.clearGpuFallback()
                }

                // Persist the chosen model
                getApplication<Application>()
                    .getSharedPreferences("app_prefs", Application.MODE_PRIVATE)
                    .edit()
                    .putString("last_model_name", modelFile.name)
                    .apply()

                _uiState.update { it.copy(
                    modelStatus = ModelStatus.Ready(handle.modelName, handle.backend),
                    isMonitoringEnabled = true
                )}
                Log.i(TAG, "Model loaded successfully: ${handle.modelName} [${handle.backend}]")
            } catch (e: Exception) {
                val kind = liteRtManager.categorizeEngineError(e, modelFile)
                val message = engineErrorMessage(kind)
                _events.trySend(UiEvent.ShowError(message))
                _uiState.update { it.copy(
                    modelStatus = ModelStatus.Error(message),
                    isMonitoringEnabled = false
                )}
                Log.e(TAG, "Model loading failed: $message", e)
            }
        }
    }

    /**
     * Downloads model from URL.
     */
    fun downloadModel(model: ModelInfo) {
        val targetFile = File(getApplication<Application>().filesDir, "${model.name}.litertlm")
        _uiState.update { it.copy(modelStatus = ModelStatus.Downloading(model.name, 0), isMonitoringEnabled = false) }

        viewModelScope.launch {
            liteRtManager.downloadModel(model, targetFile)
                .catch { e ->
                    val msg = when (e) {
                        is SocketTimeoutException -> "Download timeout. Please check your connection."
                        is IOException -> "Download failed: ${e.message ?: "unknown error"}"
                        else -> "Download error: ${e.message ?: "unknown error"}"
                    }
                    targetFile.delete()
                    _events.trySend(UiEvent.ShowError(msg))
                    _uiState.update { it.copy(modelStatus = ModelStatus.Error(msg)) }
                }
                .collect { state ->
                    when (state) {
                        is DownloadState.Progress ->
                            _uiState.update { it.copy(modelStatus = ModelStatus.Downloading(model.name, state.percent)) }
                        is DownloadState.Done ->
                            loadModel(state.file)
                        is DownloadState.Error -> {
                            targetFile.delete()
                            val msg = downloadErrorMessage(state.exception)
                            _events.trySend(UiEvent.ShowError(msg))
                            _uiState.update { it.copy(modelStatus = ModelStatus.Error(msg)) }
                        }
                    }
                }
        }
    }

    // ── Error Messages ─────────────────────────────────────────────────────

    private fun engineErrorMessage(kind: EngineErrorKind): String = when (kind) {
        is EngineErrorKind.OutOfMemory -> "Not enough memory to load model"
        is EngineErrorKind.FileNotFound -> "Model file not found"
        is EngineErrorKind.Corrupt -> "Model file is corrupted"
        is EngineErrorKind.UnsupportedAbi -> "Unsupported CPU architecture: ${Build.SUPPORTED_ABIS.joinToString()}"
        is EngineErrorKind.Generic -> "Engine error: ${kind.message}"
    }

    private fun downloadErrorMessage(e: DownloadException): String = when (e) {
        is DownloadException.NoSpace -> "Not enough storage space"
        is DownloadException.HttpError -> "Download failed: HTTP ${e.code}"
        is DownloadException.Timeout -> "Download timeout"
        is DownloadException.IoError -> "IO error: ${e.detail}"
        is DownloadException.LowRam -> "Insufficient RAM: ${e.deviceRam}GB (need ${e.requiredRam}GB)"
    }

    fun availableModels(): List<ModelInfo> = liteRtManager.availableModels()

    // ── Monitoring API ─────────────────────────────────────────────────────

    // Example of a function that is called by a button press or other event
    fun onSendAlertButtonClicked(alertMessage: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (!::telegramSender.isInitialized) {
                android.util.Log.e("MainViewModel", "TelegramSender not initialized.")
                callback(false)
                return@launch
            }
            val success = telegramSender.sendMessage(alertMessage)
            callback(success)
        }
    }

    // Function to send photo with AI prompt
    fun processAndSendImageWithPrompt(photoBytes: ByteArray, prompt: String, callback: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            if (!::telegramSender.isInitialized) {
                android.util.Log.e("MainViewModel", "TelegramSender not initialized.")
                callback(false, "Error: Telegram not configured")
                return@launch
            }

            val bitmap = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size)
            if (bitmap == null) {
                android.util.Log.e("MainViewModel", "Failed to decode photoBytes to Bitmap.")
                callback(false, "Error: Failed to process image")
                return@launch
            }

            var liteRtResponseText: String? = "Image analysis failed."
            var analysisSuccess = false

            try {
                // Modify prompt to guide the model output format
                val modifiedPrompt = prompt + " Always start the answer with 'Yes' or 'No' or 'Not sure'"
                
                // Call LiteRtService to generate response
                val responseFlow = liteRtService.generateChatResponseStreaming(
                    userPrompt = modifiedPrompt,
                    imageBitmap = bitmap
                )

                val stringBuilder = StringBuilder()
                responseFlow.collect { token ->
                    stringBuilder.append(token)
                }
                liteRtResponseText = stringBuilder.toString()
                
                if (liteRtResponseText.isNotBlank() && !liteRtResponseText.startsWith("Error:")) {
                    analysisSuccess = true
                    android.util.Log.i("MainViewModel", "LiteRT analysis successful: $liteRtResponseText")
                } else {
                    android.util.Log.w("MainViewModel", "LiteRT analysis returned empty or error: $liteRtResponseText")
                }

            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error during LiteRT analysis", e)
                liteRtResponseText = "Image analysis error: ${e.localizedMessage}"
                analysisSuccess = false
            }

            // Check the response from LiteRT before sending to Telegram
            if (analysisSuccess && (liteRtResponseText.startsWith(
                    "No",
                    ignoreCase = true
                ) == true)
            ) {
                android.util.Log.i("MainViewModel", "Sending to Telegram skipped because AI response starts with 'No'. Response: $liteRtResponseText")
                // Report analysis success, but that sending was skipped.
                callback(true, "SKIPPED_NO:$liteRtResponseText") // Analysis success, but sending skipped
            } else {
                // Send photo with LiteRT response as caption
                val captionToSend = if (analysisSuccess) liteRtResponseText else "Failed to get description from LiteRT."
                val telegramSuccess = telegramSender.sendPhoto(photoBytes = photoBytes, caption = captionToSend)
                if (telegramSuccess) {
                    messagesSent++
                    if (messagesSent >= 3) {
                        // Send message about reaching the limit
                        telegramSender.sendMessage("Reached the limit of 3 messages. Monitoring stopped.")
                        // Call callback with a special flag to stop monitoring
                        callback(true, "STOP_MONITORING:$liteRtResponseText")
                    } else {
                        callback(true, liteRtResponseText)
                    }
                } else {
                    callback(false, liteRtResponseText)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (::telegramSender.isInitialized) {
            telegramSender.close()
        }
        if (::liteRtManager.isInitialized) {
            liteRtManager.close()
        }
        Log.d(TAG, "ViewModel cleared — engine released")
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}