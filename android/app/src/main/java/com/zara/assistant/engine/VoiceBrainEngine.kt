package com.zara.assistant.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

object VoiceBrainEngine {

    interface CommandCallback {
        fun onWhatsAppSend(contact: String, message: String)
        fun onSetTimer(seconds: Int, label: String)
        fun onSetAlarm(hour: Int, minute: Int, label: String)
        fun onToggleTorch(enable: Boolean)
        fun onPhoneCall(contactOrNumber: String)
        fun onYouTubePlay(query: String)
        fun onAppOpen(appName: String)
        fun onConversational(reply: String)
    }

    private val httpClient = OkHttpClient()
    private const val VERCEL_API_URL = "https://zara-ai-assistant-gamma.vercel.app/api/chat"

    fun process(context: Context, spokenText: String, callback: CommandCallback) {
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

        // 2. Timer Automation
        // Examples: "set timer for 5 minutes", "5 nimisham timer vai", "10 seconds timer", "டைமர் வை"
        if (cleaned.contains("timer") || cleaned.contains("டைமர்")) {
            var seconds = 60
            val numMatch = Regex("(\\d+)\\s*(minute|minutes|min|mins|nimisham|second|seconds|sec|secs)?").find(cleaned)
            if (numMatch != null) {
                val value = numMatch.groupValues[1].toIntOrNull() ?: 1
                val unit = numMatch.groupValues[2].lowercase()
                seconds = if (unit.contains("sec")) value else value * 60
            }
            callback.onSetTimer(seconds, "Zara Timer")
            return
        }

        // 3. Alarm Automation
        // Examples: "set alarm for 6 am", "alarm 7:30", "6 manikku alarm vai", "அலாரம் வை"
        if (cleaned.contains("alarm") || cleaned.contains("அலாரம்")) {
            var hour = 7
            var minute = 0
            val timeMatch = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(cleaned)
            if (timeMatch != null) {
                var h = timeMatch.groupValues[1].toIntOrNull() ?: 7
                val m = timeMatch.groupValues[2].toIntOrNull() ?: 0
                val ampm = timeMatch.groupValues[3].lowercase()
                if (ampm == "pm" && h < 12) h += 12
                if (ampm == "am" && h == 12) h = 0
                hour = h
                minute = m
            }
            callback.onSetAlarm(hour, minute, "Zara Alarm")
            return
        }

        // 4. Torch / Flashlight
        // Examples: "torch on pannu", "flashlight podu", "torch off", "டார்ச்"
        if (cleaned.contains("torch") || cleaned.contains("flashlight") || cleaned.contains("டார்ச்")) {
            val enable = !cleaned.contains("off") && !cleaned.contains("stop") && !cleaned.contains("moodu")
            callback.onToggleTorch(enable)
            return
        }

        // 5. Phone Call / Dialer
        // Examples: "call 9876543210", "call Amma", "அம்மாவுக்கு கால் பண்ணு"
        if (cleaned.contains("call") || cleaned.contains("கால்") || cleaned.contains("டயல்")) {
            val target = cleaned.replace(Regex("(?:call|phone|ku|பண்ணு|டயல்)"), "").trim()
            callback.onPhoneCall(if (target.isNotEmpty()) target else "9876543210")
            return
        }

        // 6. YouTube Search & Play
        // Examples: "youtube-la ilayaraja songs podu", "play Leo trailer on youtube", "பாட்டு போடு"
        if (cleaned.contains("youtube") || cleaned.contains("யூடியூப்") || cleaned.contains("paattu") || cleaned.contains("song") || cleaned.contains("பாட்டு")) {
            val query = cleaned.replace(Regex("(?:youtube|யூடியூப்|la|open|pannu|podu|play|search|paattu|song|பாட்டு)"), " ")
                .trim().replace(Regex("\\s+"), " ")
            callback.onYouTubePlay(if (query.isNotEmpty()) query else "Tamil Hit Songs")
            return
        }

        // 7. Instagram
        if (cleaned.contains("insta") || cleaned.contains("instagram") || cleaned.contains("இன்ஸ்டா")) {
            callback.onAppOpen("instagram")
            return
        }

        // 8. Camera / Selfie
        if (cleaned.contains("camera") || cleaned.contains("photo") || cleaned.contains("selfie") || cleaned.contains("கேமரா")) {
            callback.onAppOpen("camera")
            return
        }

        // 9. Spotify
        if (cleaned.contains("spotify") || cleaned.contains("ஸ்பாடிபை")) {
            callback.onAppOpen("spotify")
            return
        }

        // 10. Settings
        if (cleaned.contains("setting") || cleaned.contains("செட்டிங்ஸ்")) {
            callback.onAppOpen("settings")
            return
        }

        // 11. Calculator
        if (cleaned.contains("calc") || cleaned.contains("calculator") || cleaned.contains("கல்குலேட்டர்")) {
            callback.onAppOpen("calculator")
            return
        }

        // 12. General AI Brain: Ask Gemini or Vercel
        askGeminiOrVercel(context, cleaned, callback)
    }

    private fun askGeminiOrVercel(context: Context, prompt: String, callback: CommandCallback) {
        val prefs = context.getSharedPreferences("zara_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""

        if (apiKey.isNotEmpty()) {
            queryDirectGemini(apiKey, prompt, callback)
        } else {
            queryVercelBrain(prompt, callback)
        }
    }

    /**
     * Direct Gemini 2.5 Flash / 1.5 Flash caller for lightning speed responses
     */
    private fun queryDirectGemini(apiKey: String, prompt: String, callback: CommandCallback) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

        val systemPrompt = "You are Zara, a high-tech smart personal voice assistant on Android. " +
                "The user asked: '$prompt'. Answer in 1 or 2 concise, sweet conversational sentences in Tamil / Tanglish / English according to the language of the prompt. No markdown."

        val jsonPayload = JSONObject().apply {
            val contents = JSONArray().apply {
                val item = JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemPrompt) })
                    })
                }
                put(item)
            }
            put("contents", contents)
        }

        val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(requestBody).build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                queryVercelBrain(prompt, callback)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                Handler(Looper.getMainLooper()).post {
                    try {
                        val json = JSONObject(body)
                        val text = json.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")
                            .trim()
                        callback.onConversational(text)
                    } catch (e: Exception) {
                        queryVercelBrain(prompt, callback)
                    }
                }
            }
        })
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
                    callback.onConversational("சரிங்க! செஞ்சுடலாம்.")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                Handler(Looper.getMainLooper()).post {
                    try {
                        val json = JSONObject(body)
                        val reply = json.optString("tamil_reply", json.optString("reply", "Done!"))
                        callback.onConversational(reply)
                    } catch (e: Exception) {
                        callback.onConversational("சரிங்க!")
                    }
                }
            }
        })
    }
}
