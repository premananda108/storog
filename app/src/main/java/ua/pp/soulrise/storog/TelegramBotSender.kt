package ua.pp.soulrise.storog

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class TelegramBotSender(
    private val botToken: String, // Pass the token when creating an instance
    private val defaultChatId: String // Pass the default chat_id
) {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        // You can add timeouts and other client configurations
        // engine {
        //     requestTimeout = 10_000 // 10 seconds
        // }
    }

    suspend fun sendMessage(messageText: String, chatId: String = defaultChatId): Boolean {
        return withContext(Dispatchers.IO) { // Execute the network request on a background thread
            try {
                val url = "https://api.telegram.org/bot$botToken/sendMessage"
                Log.d("TelegramBotSender", "Sending message to $chatId: $messageText")

                val response: HttpResponse = client.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(mapOf(
                        "chat_id" to chatId,
                        "text" to messageText,
                        // "parse_mode" to "MarkdownV2" // Optional, for formatting
                        // "disable_web_page_preview" to true // Optional
                    ))
                }

                if (response.status.isSuccess()) {
                    Log.i("TelegramBotSender", "Message sent successfully: ${response.bodyAsText()}")
                    true
                } else {
                    Log.e("TelegramBotSender", "Error sending message: ${response.status} - ${response.bodyAsText()}")
                    false
                }
            } catch (e: Exception) {
                Log.e("TelegramBotSender", "Exception sending message", e)
                false
            }
        }
    }

    suspend fun sendPhoto(photoBytes: ByteArray, chatId: String = defaultChatId, caption: String? = null): Boolean {
        return withContext(Dispatchers.IO) { // Execute the network request on a background thread
            try {
                val url = "https://api.telegram.org/bot$botToken/sendPhoto"
                Log.d("TelegramBotSender", "Sending photo to $chatId, caption: $caption")

                val response: HttpResponse = client.post(url) {
                    setBody(MultiPartFormDataContent(
                        formData {
                            append("chat_id", chatId)
                            if (caption != null) {
                                append("caption", caption)
                            }
                            append("photo", photoBytes, Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg") // or image/png, depending on the format
                                append(HttpHeaders.ContentDisposition, "filename=\"photo.jpg\"")
                            })
                        }
                    ))
                }

                if (response.status.isSuccess()) {
                    Log.i("TelegramBotSender", "Photo sent successfully: ${response.bodyAsText()}")
                    true
                } else {
                    Log.e("TelegramBotSender", "Error sending photo: ${response.status} - ${response.bodyAsText()}")
                    false
                }
            } catch (e: Exception) {
                Log.e("TelegramBotSender", "Exception sending photo", e)
                false
            }
        }
    }

    // Call this method when the service is no longer needed (e.g., in ViewModel's onCleared)
    fun close() {
        client.close()
        Log.d("TelegramBotSender", "HttpClient closed.")
    }
}