package ua.pp.prema.storog

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ua.pp.prema.storog.engine.LiteRtManager
import java.util.Properties

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private lateinit var telegramSender: TelegramBotSender
    private lateinit var liteRtService: LiteRtService
    private lateinit var liteRtManager: LiteRtManager
    private var messagesSent = 0 // Sent messages counter

    init {
        val properties = Properties()
        try {
            getApplication<Application>().assets.open("my_config.properties").use { inputStream ->
                properties.load(inputStream)
            }
            val botToken = properties.getProperty("MY_BOT_TOKEN")
            val sharedPreferences = getApplication<Application>().getSharedPreferences("StorogSettings", Context.MODE_PRIVATE)
            val targetChatId = sharedPreferences.getString("TARGET_CHAT_ID", null)

            if (botToken != null && targetChatId != null) {
                telegramSender = TelegramBotSender(botToken, targetChatId)
                // Initialize LiteRtManager and LiteRtService
                liteRtManager = LiteRtManager(getApplication())
                liteRtService = LiteRtService(getApplication(), liteRtManager)
            } else {
                android.util.Log.e("MainViewModel", "MY_BOT_TOKEN or TARGET_CHAT_ID not found.")
                // In a real application, there should be more robust error handling here.
            }
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Error loading properties file", e)
        }
    }

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
            if (!::telegramSender.isInitialized || !::liteRtService.isInitialized) {
                android.util.Log.e("MainViewModel", "TelegramSender or LiteRtService not initialized.")
                callback(false, "Error: Services not initialized")
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
    }
}