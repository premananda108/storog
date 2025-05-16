package ua.pp.soulrise.storog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.Properties

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private lateinit var telegramSender: TelegramBotSender

    init {
        val properties = Properties()
        try {
            getApplication<Application>().assets.open("telegram_config.properties").use { inputStream ->
                properties.load(inputStream)
            }
            val botToken = properties.getProperty("MY_BOT_TOKEN")
            val targetChatId = properties.getProperty("TARGET_CHAT_ID")

            if (botToken != null && targetChatId != null) {
                telegramSender = TelegramBotSender(botToken, targetChatId)
            } else {
                // Обработка ошибки: токен или ID чата не найдены в файле
                // Можно выбросить исключение или использовать значения по умолчанию
                android.util.Log.e("MainViewModel", "MY_BOT_TOKEN or TARGET_CHAT_ID not found in properties file.")
                // В качестве примера, можно инициализировать с пустыми значениями или выбросить ошибку
                // throw IllegalStateException("MY_BOT_TOKEN or TARGET_CHAT_ID not found in properties file.")
                // Для простоты примера, если значения не найдены, бот не будет инициализирован корректно.
                // В реальном приложении здесь должна быть более надежная обработка ошибок.
            }
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Error loading properties file", e)
            // Обработка ошибки чтения файла
        }
    }

    // Пример функции, которая вызывается по нажатию кнопки или другому событию
    fun onSendAlertButtonClicked(alertMessage: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = telegramSender.sendMessage(alertMessage)
            callback(success)
        }
    }

    // Функция для отправки фото
    fun onSendPhotoButtonClicked(photoBytes: ByteArray, caption: String? = null, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = telegramSender.sendPhoto(photoBytes = photoBytes, caption = caption)
            callback(success)
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (::telegramSender.isInitialized) {
            telegramSender.close()
        }
    }
}