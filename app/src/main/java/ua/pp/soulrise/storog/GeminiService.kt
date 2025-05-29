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
    private val defaultModelName: String = "gemini-1.5-flash-latest" // Default value can be set
) {

    // Generation configuration, analogous to generate_content_config
    private val generationConfig = GenerationConfig.Builder().apply {
        // responseMimeType = "text/plain" // SDK usually determines this itself or uses text/plain
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
            // IllegalArgumentException can be thrown if an empty key is not allowed
            // throw IllegalArgumentException("API Key cannot be empty.")
        }
        Log.i("GeminiService", "GeminiService initialized with model: $defaultModelName")
    }

    /**
     * Generates a response from LLM in streaming mode.
     *
     * @param userPrompt Text query from the user.
     * @param imageBitmap Optional image for a multimodal query.
     * @param chatHistory Current chat history (list of Content objects).
     * @param modelNameOverride Name of the model to use, if defaultModelName needs to be overridden.
     * @return Flow<Pair<String, Boolean>> Stream of text fragments of the response.
     *         Also updates chatHistory by adding the user's request and the model's full response to it.
     */
    suspend fun generateChatResponseStreaming(
        userPrompt: String,
        imageBitmap: Bitmap? = null,
        chatHistory: MutableList<Content>, // Pass a mutable list for update
        modelNameOverride: String? = null
    ): Flow<Pair<String, Boolean>> { // Pair<chunkText, isFinalChunk>
        if (apiKey.isEmpty()) {
            // This check is already in init, but duplicating it here for a specific call won't hurt
            Log.e("GeminiService", "API Key is missing.")
            throw IllegalArgumentException("API Key is missing. Cannot make API calls.")
        }

        val currentModelName = modelNameOverride ?: defaultModelName

        val generativeModel = GenerativeModel(
            modelName = currentModelName,
            apiKey = apiKey,
            generationConfig = generationConfig
            // safetySettings = ... // Safety settings can be configured
        )

        // Create content for the current user request
        val userInputContent = content(role = "user") {
            imageBitmap?.let { image(it) } // Add image if it exists
            text(userPrompt)
        }

        // Form history for the current API call
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
                    emit(Pair("Error: Invalid API key. Check settings.", true))
                } else if (e.message?.contains("RESOURCE_EXHAUSTED") == true) {
                    emit(Pair("Error: API quota exhausted. Try again later.", true))
                } else {
                    emit(Pair("Error: ${e.localizedMessage ?: "Unknown error"}", true))
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