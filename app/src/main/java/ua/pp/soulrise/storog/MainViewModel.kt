package ua.pp.soulrise.storog

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.type.Content
import kotlinx.coroutines.launch
import java.util.Properties

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private lateinit var telegramSender: TelegramBotSender
    private lateinit var geminiService: GeminiService
    private val chatHistory = mutableListOf<Content>() // Initialize chat history
    private var messagesSent = 0 // Sent messages counter

    init {
        val properties = Properties()
        try {
            getApplication<Application>().assets.open("my_config.properties").use { inputStream ->
                properties.load(inputStream)
            }
            val botToken = properties.getProperty("MY_BOT_TOKEN")
            val geminiApiKey = properties.getProperty("GEMINI_API_KEY")
            val sharedPreferences = getApplication<Application>().getSharedPreferences("StorogSettings", Context.MODE_PRIVATE)
            val targetChatId = sharedPreferences.getString("TARGET_CHAT_ID", null)

            if (botToken != null && targetChatId != null && geminiApiKey != null) {
                telegramSender = TelegramBotSender(botToken, targetChatId)
                geminiService = GeminiService(geminiApiKey) // Initialization of GeminiService
            } else {
                android.util.Log.e("MainViewModel", "GEMINI_API_KEY, MY_BOT_TOKEN or TARGET_CHAT_ID not found.")
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

    // Функция для отправки фото с AI промптом
    fun processAndSendImageWithPrompt(photoBytes: ByteArray, prompt: String, callback: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            if (!::telegramSender.isInitialized || !::geminiService.isInitialized) {
                android.util.Log.e("MainViewModel", "TelegramSender or GeminiService not initialized.")
                callback(false, "Error: Services not initialized")
                return@launch
            }

            val bitmap = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size)
            if (bitmap == null) {
                android.util.Log.e("MainViewModel", "Failed to decode photoBytes to Bitmap.")
                callback(false, "Error: Failed to process image")
                return@launch
            }

            var geminiResponseText: String? = "Image analysis failed."
            var analysisSuccess = false

            try {
                // Using streaming version for example, but non-streaming is also possible
                // For simplicity, we will collect the entire response here before sending
                val modifiedPrompt = prompt + " Always start the answer with 'Yes' or 'No' or 'Not sure'"
                val responseFlow = geminiService.generateChatResponseStreaming(
                    userPrompt = modifiedPrompt, // Using the modified prompt
                    imageBitmap = bitmap,
                    chatHistory = chatHistory // Pass and update chat history
                )

                val stringBuilder = StringBuilder()
                responseFlow.collect { (chunk, _) -> // isFinal is not used
                    stringBuilder.append(chunk)
                }
                geminiResponseText = stringBuilder.toString()
                if (geminiResponseText.isNotBlank() && !geminiResponseText.startsWith("Ошибка:")) {
                    analysisSuccess = true
                    android.util.Log.i("MainViewModel", "Gemini analysis successful: $geminiResponseText")
                } else {
                    android.util.Log.w("MainViewModel", "Gemini analysis returned empty or error: $geminiResponseText")
                }

            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error during Gemini API call", e)
                geminiResponseText = "Image analysis error: ${e.localizedMessage}"
                analysisSuccess = false
            }

            // Check the response from Gemini before sending
            if (analysisSuccess && (geminiResponseText.startsWith(
                    "No",
                    ignoreCase = true
                ) == true)
            ) {
                android.util.Log.i("MainViewModel", "Sending to Telegram skipped because AI response starts with 'No'. Response: $geminiResponseText")
                // Report analysis success, but that sending was skipped.
                // Pass a special message or flag if necessary for the UI.
                // In this case, callback(true, ...) will mean that the analysis was successful, but sending might have been skipped.
                // MainActivity will need to check responseMsg to see if sending occurred.
                // Or another parameter can be added to the callback.
                // For now, for simplicity, we will assume that if geminiResponseText starts with "No",
                // then telegramSuccess will be false, but analysisSuccess can be true.
                // To allow the UI to distinguish this, we will change the callback logic.
                callback(true, "SKIPPED_NO:$geminiResponseText") // Analysis success, but sending skipped
            } else {
                // Send photo with Gemini response as caption
                val captionToSend = if (analysisSuccess) geminiResponseText else "Failed to get description from Gemini."
                val telegramSuccess = telegramSender.sendPhoto(photoBytes = photoBytes, caption = captionToSend)
                if (telegramSuccess) {
                    messagesSent++
                    if (messagesSent >= 3) {
                        // Send message about reaching the limit
                        telegramSender.sendMessage("Reached the limit of 3 messages. Monitoring stopped.")
                        // Call callback with a special flag to stop monitoring
                        callback(true, "STOP_MONITORING:$geminiResponseText")
                    } else {
                        callback(true, geminiResponseText)
                    }
                } else {
                    callback(false, geminiResponseText)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (::telegramSender.isInitialized) {
            telegramSender.close()
        }
        // GeminiService does not require explicit close() if it does not use resources that need to be released
    }
}