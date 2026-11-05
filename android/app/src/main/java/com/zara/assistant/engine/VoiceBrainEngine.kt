package com.zara.assistant.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object VoiceBrainEngine {

    interface CommandCallback {
        fun onWhatsAppSend(contact: String, message: String)
        fun onSetTimer(seconds: Int, label: String)
        fun onSetAlarm(hour: Int, minute: Int, label: String)
        fun onToggleTorch(enable: Boolean)
        fun onPhoneCall(contactOrNumber: String)
        fun onYouTubePlay(query: String)
        fun onVolumeChange(raise: Boolean, mute: Boolean)
        fun onOpenSettingsPage(action: String, title: String)
        fun onAppOpen(appName: String)
        fun onConversational(reply: String)
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val VERCEL_API_URL = "https://zara-ai-assistant-gamma.vercel.app/api/chat"
    private val DEFAULT_GEMINI_KEY: String by lazy {
        try {
            String(android.util.Base64.decode("QVEuQWI4Uk42SUFXckRlY19OMHNNWnNWRUMteEVNOXRJY19ibDd3OEZBMEhSTWJtYTM0WFE=", android.util.Base64.DEFAULT), Charsets.UTF_8).trim()
        } catch (e: Exception) {
            ""
        }
    }

    fun process(context: Context, spokenText: String, callback: CommandCallback) {
        val lower = spokenText.lowercase().trim()
        val cleaned = lower.replace(Regex("^(hey\\s+)?(zara|சாரா|ஜாரா)[\\s,]*"), "").trim()

        // 1. Direct Hands-Free Phone Call
        // Examples: "call amma", "amma ku call pannu", "call 9876543210", "அம்மாவுக்கு கால் பண்ணு", "dial 12345"
        val isCallCommand = (cleaned.startsWith("call ") || cleaned.startsWith("dial ") ||
                cleaned.contains("call pannu") || cleaned.contains("call panna") ||
                cleaned.contains("கால் பண்ணு") || cleaned.contains("டயல் பண்ணு") ||
                cleaned.contains("ku call") || cleaned.contains("-ku call") ||
                cleaned.contains("போன் பண்ணு") || cleaned.contains("phone pannu")) &&
                !cleaned.contains("what is") && !cleaned.contains("who is")

        if (isCallCommand) {
            var target = cleaned.replace(Regex("(?:call|phone|ku|பண்ணு|டயல்|பண்ணுங்க|போடு|போடுங்க|-ku|panna|solren|pannidu|please|solna)"), " ").trim()
            if (target.isEmpty()) target = "Amma"
            callback.onPhoneCall(target)
            return
        }

        // 2. WhatsApp: App Open vs Automation Message
        if (cleaned.contains("whatsapp") || cleaned.contains("வாட்ஸ்அப்") || cleaned.contains("வாட்சப்") || cleaned.contains("watsap")) {
            val isJustOpen = (cleaned == "whatsapp" || cleaned == "open whatsapp" || cleaned == "whatsapp open" ||
                    cleaned == "whatsapp open pannu" || cleaned == "whatsapp app" || cleaned == "வாட்ஸ்அப் திற") ||
                    (!cleaned.contains("anupu") && !cleaned.contains("send") && !cleaned.contains("msg") && !cleaned.contains("message") && !cleaned.contains("solli"))

            if (isJustOpen) {
                callback.onAppOpen("whatsapp")
                return
            }

            var contact = "Amma"
            var message = "Hi"

            val patternA = Regex("(?:la|ல|to)\\s+([a-zA-Z0-9_\\u0B80-\\u0BFF]+)(?:-ku|\\s+ku|\\s+க்கு)\\s+(.+?)(?:\\s+nu|\\s+solli|\\s+text|\\s+msg|\\s+anupu|$)")
            val matchA = patternA.find(cleaned)

            if (matchA != null) {
                contact = matchA.groupValues[1]
                message = matchA.groupValues[2].trim()
            } else {
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

        // 3. Hands-Free Open ANY App
        // Examples: "open instagram", "free fire open pannu", "open chrome", "gallery open பண்ணு"
        if (cleaned.startsWith("open ") || cleaned.contains(" open") || cleaned.contains("திற")) {
            val appTarget = cleaned.replace(Regex("(?:open|திற|app|ஆப்|pannu|பண்ணு|போ|po)"), " ").trim()
            if (appTarget.isNotEmpty()) {
                callback.onAppOpen(appTarget)
                return
            }
        }

        // 4. Timer Automation
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

        // 5. Alarm Automation
        if (cleaned.contains("alarm") || cleaned.contains("அலாரம்")) {
            var hour = 7
            var minute = 0
            val timeMatch = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|மணி)?", RegexOption.IGNORE_CASE).find(cleaned)
            if (timeMatch != null) {
                hour = timeMatch.groupValues[1].toIntOrNull() ?: 7
                minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
                val amPm = timeMatch.groupValues[3].lowercase()
                if (amPm == "pm" && hour < 12) hour += 12
                if (amPm == "am" && hour == 12) hour = 0
            }
            callback.onSetAlarm(hour, minute, "Zara Alarm")
            return
        }

        // 6. Torch / Flashlight
        if (cleaned.contains("torch") || cleaned.contains("flashlight") || cleaned.contains("பிளாஷ்") || cleaned.contains("வெளிச்சம்")) {
            val turnOn = !cleaned.contains("off") && !cleaned.contains("ஆஃப்") && !cleaned.contains("moodu") && !cleaned.contains("stop")
            callback.onToggleTorch(turnOn)
            return
        }

        // 7. Volume Control
        if (cleaned.contains("volume") || cleaned.contains("சத்தம்")) {
            if (cleaned.contains("mute") || cleaned.contains("silent") || cleaned.contains("சைலன்ட்")) {
                callback.onVolumeChange(raise = false, mute = true)
            } else if (cleaned.contains("up") || cleaned.contains("raise") || cleaned.contains("ஏத்து") || cleaned.contains("கூட்டு") || cleaned.contains("increase")) {
                callback.onVolumeChange(raise = true, mute = false)
            } else {
                callback.onVolumeChange(raise = false, mute = false)
            }
            return
        }

        // 8. Wi-Fi / Bluetooth Settings
        if (cleaned.contains("wifi") || cleaned.contains("வைஃபை")) {
            callback.onOpenSettingsPage(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi Settings")
            return
        }
        if (cleaned.contains("bluetooth") || cleaned.contains("ப்ளூடூத்")) {
            callback.onOpenSettingsPage(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth Settings")
            return
        }

        // 9. Battery Level Check
        if (cleaned.contains("battery") || cleaned.contains("சார்ஜ்") || cleaned.contains("பேட்டரி")) {
            val batteryStatus: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            callback.onConversational("உங்க போன் பேட்டரி $level சதவீதம் இருக்கு! (Battery is at $level%)")
            return
        }

        // 10. YouTube Search & Play
        if (cleaned.contains("youtube") || cleaned.contains("யூடியூப்") || cleaned.contains("paattu") || cleaned.contains("song") || cleaned.contains("பாட்டு")) {
            val query = cleaned.replace(Regex("(?:youtube|யூடியூப்|la|open|pannu|podu|play|search|paattu|song|பாட்டு)"), " ")
                .trim().replace(Regex("\\s+"), " ")
            callback.onYouTubePlay(if (query.isNotEmpty()) query else "Tamil Hit Songs")
            return
        }

        // 11. Instagram
        if (cleaned.contains("insta") || cleaned.contains("instagram") || cleaned.contains("இன்ஸ்டா")) {
            callback.onAppOpen("instagram")
            return
        }

        // 12. Camera / Selfie
        if (cleaned.contains("camera") || cleaned.contains("photo") || cleaned.contains("selfie") || cleaned.contains("கேமரா")) {
            callback.onAppOpen("camera")
            return
        }

        // 13. Spotify
        if (cleaned.contains("spotify") || cleaned.contains("ஸ்பாடிபை")) {
            callback.onAppOpen("spotify")
            return
        }

        // 14. Settings
        if (cleaned.contains("setting") || cleaned.contains("செட்டிங்ஸ்")) {
            callback.onAppOpen("settings")
            return
        }

        // 15. Calculator
        if (cleaned.contains("calc") || cleaned.contains("calculator") || cleaned.contains("கல்குலேட்டர்")) {
            callback.onAppOpen("calculator")
            return
        }

        // 16. Intelligent Gemini AI Brain Question Answering
        askGeminiOrVercel(context, cleaned, callback)
    }

    private fun askGeminiOrVercel(context: Context, prompt: String, callback: CommandCallback) {
        val prefs = context.getSharedPreferences("zara_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_GEMINI_KEY

        queryDirectGemini(apiKey, prompt, callback)
    }

    private fun queryDirectGemini(apiKey: String, prompt: String, callback: CommandCallback) {
        // High-performance Gemini 3.5 Flash endpoint (active & verified with user API key)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val systemPrompt = "You are Zara, an advanced personal voice AI assistant for an Android smartphone. " +
                "The user speaks in Tamil, English, or Tanglish. " +
                "The user says: '$prompt'. " +
                "Answer directly, concisely, and intelligently in 1 to 2 conversational sentences in natural Tanglish or Tamil. " +
                "Do not use markdown bolding, asterisks, or bullet points. Speak directly as Zara."

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
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.4)
                put("maxOutputTokens", 250)
            })
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
                        val candidates = json.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val content = candidates.getJSONObject(0).optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            var answer = ""
                            if (parts != null) {
                                for (i in 0 until parts.length()) {
                                    val part = parts.getJSONObject(i)
                                    if (part.has("text")) {
                                        val t = part.getString("text").trim()
                                        if (t.isNotEmpty()) {
                                            answer = t
                                        }
                                    }
                                }
                            }
                            if (answer.isNotEmpty()) {
                                callback.onConversational(answer)
                                return@post
                            }
                        }
                        queryVercelBrain(prompt, callback)
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
                    callback.onConversational("மன்னிக்கவும், இணையத்தில் இணைக்க முடியவில்லை. மீண்டும் சொல்லுங்க!")
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