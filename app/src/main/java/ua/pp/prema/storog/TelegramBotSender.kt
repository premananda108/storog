package ua.pp.prema.storog

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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.long
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.JsonElement

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

// Suspend helper to fetch chat id from getUpdates using provided token.
suspend fun fetchChatId(token: String): String? {
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }
    return try {
        val url = "https://api.telegram.org/bot$token/getUpdates"
        val resp = client.get(url)
        if (!resp.status.isSuccess()) return null
        val text = resp.bodyAsText()
        val elem = Json.parseToJsonElement(text)
        if (elem is JsonElement) {
            val result = elem.jsonObject["result"]
            if (result != null && result is JsonElement && result.jsonArray.isNotEmpty()) {
                val last = result.jsonArray.last()
                // message may be under "message" or "channel_post" etc.
                val msg = last.jsonObject["message"] ?: last.jsonObject["channel_post"]
                val chat = msg?.jsonObject?.get("chat")
                val idElem = chat?.jsonObject?.get("id")
                val id = idElem?.jsonPrimitive?.content
                id
            } else null
        } else null
    } catch (e: Exception) {
        Log.e("TelegramBotSender", "fetchChatId error", e)
        null
    } finally {
        client.close()
    }
}