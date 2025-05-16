package ua.pp.soulrise.storog

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.GenerationConfig
import com.google.ai.client.generativeai.type.InvalidStateException
import com.google.ai.client.generativeai.type.asTextOrNull
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext

class GeminiService(
    private val apiKey: String,
    private val defaultModelName: String = "gemini-1.5-flash-latest" // Можно задать значение по умолчанию
) {

    // Конфигурация генерации, аналог generate_content_config
    private val generationConfig = GenerationConfig.Builder().apply {
        // responseMimeType = "text/plain" // SDK обычно сам определяет или использует text/plain
        // temperature = 0.9f
        // topK = 1
        // topP = 1f
        // maxOutputTokens = 2048
    }.build()

    init {
        if (apiKey.isEmpty()) {
            Log.w(
                "GeminiService",
                "API Key provided to GeminiService is empty. Calls will likely fail."
            )
            // Можно выбросить IllegalArgumentException, если пустой ключ недопустим
            // throw IllegalArgumentException("API Key cannot be empty.")
        }
        Log.i("GeminiService", "GeminiService initialized with model: $defaultModelName")
    }

    /**
     * Генерирует ответ от LLM в потоковом режиме.
     *
     * @param userPrompt Текстовый запрос от пользователя.
     * @param imageBitmap Опциональное изображение для мультимодального запроса.
     * @param chatHistory Текущая история чата (список Content объектов).
     * @param modelNameOverride Имя используемой модели, если нужно переопределить defaultModelName.
     * @return Flow<Pair<String, Boolean>> Поток текстовых фрагментов ответа.
     *         Также обновляет chatHistory, добавляя в него запрос пользователя и полный ответ модели.
     */
    suspend fun generateChatResponseStreaming(
        userPrompt: String,
        imageBitmap: Bitmap? = null,
        chatHistory: MutableList<Content>, // Передаем изменяемый список для обновления
        modelNameOverride: String? = null
    ): Flow<Pair<String, Boolean>> { // Pair<chunkText, isFinalChunk>
        if (apiKey.isEmpty()) {
            // Эта проверка уже есть в init, но дублирование здесь для конкретного вызова не помешает
            Log.e("GeminiService", "API Key is missing.")
            throw IllegalArgumentException("API Key is missing. Cannot make API calls.")
        }

        val currentModelName = modelNameOverride ?: defaultModelName

        val generativeModel = GenerativeModel(
            modelName = currentModelName,
            apiKey = apiKey,
            generationConfig = generationConfig
            // safetySettings = ... // Можно настроить параметры безопасности
        )

        // Создаем контент для текущего запроса пользователя
        val userInputContent = content(role = "user") {
            imageBitmap?.let { image(it) } // Добавляем изображение, если оно есть
            text(userPrompt)
        }

        // Формируем историю для текущего вызова API
        val currentCallHistory = chatHistory.toList() + userInputContent

        Log.d(
            "GeminiService",
            "Sending to $currentModelName. History size: ${currentCallHistory.size}, Prompt: $userPrompt, Image: ${imageBitmap != null}"
        )

        val fullResponseBuilder = StringBuilder()
        var isFinished = false

        return generativeModel.generateContentStream(*currentCallHistory.toTypedArray())
            .map { responseChunk ->
                val chunkText = responseChunk.text ?: ""
                fullResponseBuilder.append(chunkText)
                Log.d("GeminiService", "Chunk received: $chunkText")
                Pair(chunkText, false)
            }
            .catch { e ->
                Log.e("GeminiService", "Error generating content stream for $currentModelName", e)
                isFinished = true
                if (e is InvalidStateException && e.message?.contains("API key not valid") == true) {
                    emit(Pair("Ошибка: Неверный API ключ. Проверьте настройки.", true))
                } else if (e.message?.contains("RESOURCE_EXHAUSTED") == true) {
                    emit(Pair("Ошибка: Квота API исчерпана. Попробуйте позже.", true))
                } else {
                    emit(Pair("Ошибка: ${e.localizedMessage ?: "Неизвестная ошибка"}", true))
                }
            }
            .onCompletion { cause ->
                if (!isFinished) {
                    if (cause == null) {
                        val finalModelResponse = fullResponseBuilder.toString()
                        Log.d(
                            "GeminiService",
                            "Stream completed. Full response: $finalModelResponse"
                        )
                        chatHistory.add(userInputContent)
                        chatHistory.add(content(role = "model") { text(finalModelResponse) })
                    } else {
                        Log.e("GeminiService", "Stream failed with exception", cause)
                    }
                }
            }
            .flowOn(Dispatchers.IO)
    }

}