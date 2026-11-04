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

        // 1. WhatsApp Intent: Supports multiple Tanglish & Tamil phrasing
        // Examples: "whatsapp-la suresh-ku enga irukka nu anupu", "whatsapp suresh hi", "வாட்ஸ்அப் சுரேஷ்"
        if (cleaned.contains("whatsapp") || cleaned.contains("வாட்ஸ்அப்") || cleaned.contains("வாட்சப்")) {
            var contact = "Amma"
            var message = "Hi"

            // Pattern A: "whatsapp la [contact] ku [message] nu [anupu/sollu]"
            val patternA = Regex("(?:la|ல|to)\\s+([a-zA-Z0-9_\\u0B80-\\u0BFF]+)(?:-ku|\\s+ku|\\s+க்கு)\\s+(.+?)(?:\\s+nu|\\s+solli|\\s+text|\\s+msg|\\s+anupu|$)")
            val matchA = patternA.find(cleaned)

            if (matchA != null) {
                contact = matchA.groupValues[1]
                message = matchA.groupValues[2].trim()
            } else {
                // Pattern B: "whatsapp [contact] [message]"
                val words = cleaned.replace(Regex("(?:whatsapp|வாட்ஸ்அப்|la|ku|anupu|msg|text)"), " ")
                    .trim().split(Regex("\\s+"))
                if (words.isNotEmpty()) {
                    contact = words[0]
                    if (words.size > 1) {
                        message = words.subList(1, words.size).joinToString(" ")
                    }
                }
            }

            callback.onWhatsAppSend(contact, message)
            return
        }

        // 2. Instagram
        if (cleaned.contains("insta") || cleaned.contains("instagram") || cleaned.contains("இன்ஸ்டா")) {
            callback.onAppOpen("instagram")
            return
        }

        // 3. YouTube / Songs
        if (cleaned.contains("youtube") || cleaned.contains("யூடியூப்") || cleaned.contains("paattu") || cleaned.contains("song") || cleaned.contains("பாட்டு")) {
            callback.onAppOpen("youtube")
            return
        }

        // 4. Camera
        if (cleaned.contains("camera") || cleaned.contains("photo") || cleaned.contains("கேமரா")) {
            callback.onAppOpen("camera")
            return
        }

        // 5. Spotify
        if (cleaned.contains("spotify") || cleaned.contains("ஸ்பாடிபை")) {
            callback.onAppOpen("spotify")
            return
        }

        // 6. Settings
        if (cleaned.contains("setting") || cleaned.contains("செட்டிங்ஸ்")) {
            callback.onAppOpen("settings")
            return
        }

        // 7. General Assistant & Conversational Fallback via live Vercel AI
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
