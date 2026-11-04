package com.zara.assistant.engine

import android.os.Handler
import android.os.Looper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object VoiceBrainEngine {

    interface CommandCallback {
        fun onWhatsAppSend(contact: String, message: String)
        fun onAppOpen(appName: String)
        fun onConversational(reply: String)
    }

    private val httpClient = OkHttpClient()
    private const val VERCEL_API_URL = "https://zara-ai-assistant-gamma.vercel.app/api/chat"

    fun process(spokenText: String, callback: CommandCallback) {
        val lower = spokenText.lowercase().trim()
        val cleaned = lower.replace(Regex("^(hey\\s+)?(zara|சாரா|ஜாரா)[\\s,]*"), "").trim()

        // 1. WhatsApp Intent: "WhatsApp-la [Contact]-ku [Message] nu anupu"
        if (cleaned.contains("whatsapp") || cleaned.contains("வாட்ஸ்அப்")) {
            val contactMatch = Regex("(?:la|ல)\\s+([a-zA-Z0-9_]+)(?:-ku|\\s+ku|\\s+க்கு)").find(cleaned)
            val msgMatch = Regex("(?:ku|க்கு)\\s+(.+?)(?:\\s+nu|\\s+solli|\\s+text|\\s+anupu|$)").find(cleaned)

            val contact = contactMatch?.groupValues?.get(1) ?: "Amma"
            val message = msgMatch?.groupValues?.get(1)?.trim() ?: "Hi"

            callback.onWhatsAppSend(contact, message)
            return
        }

        // 2. Direct App Launchers
        if (cleaned.contains("insta") || cleaned.contains("instagram")) {
            callback.onAppOpen("instagram")
            return
        }
        if (cleaned.contains("youtube") || cleaned.contains("யூடியூப்")) {
            callback.onAppOpen("youtube")
            return
        }
        if (cleaned.contains("camera") || cleaned.contains("கேமரா")) {
            callback.onAppOpen("camera")
            return
        }
        if (cleaned.contains("spotify") || cleaned.contains("பாட்டு")) {
            callback.onAppOpen("spotify")
            return
        }

        // 3. Fallback: Query live Vercel AI Brain asynchronously
        queryVercelBrain(cleaned, callback)
    }

    private fun queryVercelBrain(text: String, callback: CommandCallback) {
        val jsonPayload = JSONObject().apply {
            put("message", text)
            put("lang", "ta-IN")
        }

        val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(VERCEL_API_URL)
            .post(requestBody)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Handler(Looper.getMainLooper()).post {
                    callback.onConversational("சரிங்க, செஞ்சுடலாம்!")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                Handler(Looper.getMainLooper()).post {
                    try {
                        val json = JSONObject(body)
                        val action = json.optString("action")
                        val app = json.optString("app")
                        val reply = json.optString("tamil_reply", json.optString("reply", "Done!"))

                        if (action == "open_app" && app.isNotEmpty()) {
                            callback.onAppOpen(app)
                        } else {
                            callback.onConversational(reply)
                        }
                    } catch (e: Exception) {
                        callback.onConversational("சரிங்க!")
                    }
                }
            }
        })
    }
}
