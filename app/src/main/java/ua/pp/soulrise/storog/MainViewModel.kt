package ua.pp.soulrise.storog

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.type.Content
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Properties

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private lateinit var telegramSender: TelegramBotSender
    private lateinit var geminiService: GeminiService
    private val chatHistory = mutableListOf<Content>() // Инициализируем историю чата

    init {
        val properties = Properties()
        try {
            getApplication<Application>().assets.open("my_config.properties").use { inputStream ->
                properties.load(inputStream)
            }
            val botToken = properties.getProperty("MY_BOT_TOKEN")
            val targetChatId = properties.getProperty("TARGET_CHAT_ID")
            val geminiApiKey = properties.getProperty("GEMINI_API_KEY")

            if (botToken != null && targetChatId != null && geminiApiKey != null) {
                telegramSender = TelegramBotSender(botToken, targetChatId)
                geminiService = GeminiService(geminiApiKey) // Инициализация GeminiService
            } else {
                android.util.Log.e("MainViewModel", "GEMINI_API_KEY, MY_BOT_TOKEN or TARGET_CHAT_ID not found in properties file.")
                // В реальном приложении здесь должна быть более надежная обработка ошибок.
            }
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Error loading properties file", e)
        }
    }

    // Пример функции, которая вызывается по нажатию кнопки или другому событию
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

    // Функция для отправки фото
    fun onSendPhotoButtonClicked(photoBytes: ByteArray, callback: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            if (!::telegramSender.isInitialized || !::geminiService.isInitialized) {
                android.util.Log.e("MainViewModel", "TelegramSender or GeminiService not initialized.")
                callback(false, "Ошибка: Сервисы не инициализированы")
                return@launch
            }

            val bitmap = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size)
            if (bitmap == null) {
                android.util.Log.e("MainViewModel", "Failed to decode photoBytes to Bitmap.")
                callback(false, "Ошибка: Не удалось обработать изображение")
                return@launch
            }

            var geminiResponseText: String? = "Анализ изображения не удался."
            var analysisSuccess = false

            try {
                // Используем streaming версию для примера, но можно и non-streaming
                // Для простоты здесь мы соберем весь ответ, прежде чем отправлять
                val responseFlow = geminiService.generateChatResponseStreaming(
                    userPrompt = "есть ли на фото человек?",
                    imageBitmap = bitmap,
                    chatHistory = chatHistory // Передаем и обновляем историю чата
                )

                val stringBuilder = StringBuilder()
                responseFlow.collect { (chunk, isFinal) ->
                    stringBuilder.append(chunk)
                    if (isFinal) {
                        // Можно обработать флаг isFinal, если нужно
                    }
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
                geminiResponseText = "Ошибка анализа изображения: ${e.localizedMessage}"
                analysisSuccess = false
            }

            // Отправляем фото с ответом от Gemini в качестве подписи
            val captionToSend = if (analysisSuccess) geminiResponseText else "Не удалось получить описание от Gemini."
            val telegramSuccess = telegramSender.sendPhoto(photoBytes = photoBytes, caption = captionToSend)

            callback(telegramSuccess && analysisSuccess, geminiResponseText)
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (::telegramSender.isInitialized) {
            telegramSender.close()
        }
        // GeminiService не требует явного close(), если не использует ресурсы, которые нужно освобождать
    }
}