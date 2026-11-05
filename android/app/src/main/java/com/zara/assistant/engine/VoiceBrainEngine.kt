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

    fun process(context: Context, spokenText: String, callback: CommandCallback) {
        val lower = spokenText.lowercase().trim()
        val cleaned = lower.replace(Regex("^(hey\\s+)?(zara|சாரா|ஜாரா)[\\s,]*"), "").trim()

        // 1. WhatsApp Intent (Tamil, Tanglish & English)
        if (cleaned.contains("whatsapp") || cleaned.contains("வாட்ஸ்அப்") || cleaned.contains("வாட்சப்") || cleaned.contains("watsap")) {
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

        // 2. Timer Automation
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

        // 4. Volume Control
        if (cleaned.contains("volume") || cleaned.contains("சத்தம்") || cleaned.contains("sound")) {
            val isMute = cleaned.contains("mute") || cleaned.contains("silent") || cleaned.contains("shut")
            val isRaise = cleaned.contains("increase") || cleaned.contains("kootu") || cleaned.contains("up") || cleaned.contains("adheegam")
            callback.onVolumeChange(raise = isRaise, mute = isMute)
            return
        }

        // 5. Torch / Flashlight
        if (cleaned.contains("torch") || cleaned.contains("flashlight") || cleaned.contains("டார்ச்")) {
            val enable = !cleaned.contains("off") && !cleaned.contains("stop") && !cleaned.contains("moodu")
            callback.onToggleTorch(enable)
            return
        }

        // 6. Wi-Fi & Bluetooth Controls
        if (cleaned.contains("wifi") || cleaned.contains("வைஃபை")) {
            callback.onOpenSettingsPage(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi Settings")
            return
        }
        if (cleaned.contains("bluetooth") || cleaned.contains("ப்ளூடூத்")) {
            callback.onOpenSettingsPage(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth Settings")
            return
        }

        // 7. Battery Check
        if (cleaned.contains("battery") || cleaned.contains("பேட்டரி")) {
            val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            callback.onConversational("உங்க போன் பேட்டரி $level சதவீதம் இருக்கு! (Battery is at $level%)")
            return
        }

        // 8. Phone Calls
        if (cleaned.contains("call") || cleaned.contains("கால்") || cleaned.contains("டயல்")) {
            val target = cleaned.replace(Regex("(?:call|phone|ku|பண்ணு|டயல்)"), "").trim()
            callback.onPhoneCall(if (target.isNotEmpty()) target else "9876543210")
            return
        }

        // 9. YouTube Search & Play
        if (cleaned.contains("youtube") || cleaned.contains("யூடியூப்") || cleaned.contains("paattu") || cleaned.contains("song") || cleaned.contains("பாட்டு")) {
            val query = cleaned.replace(Regex("(?:youtube|யூடியூப்|la|open|pannu|podu|play|search|paattu|song|பாட்டு)"), " ")
                .trim().replace(Regex("\\s+"), " ")
            callback.onYouTubePlay(if (query.isNotEmpty()) query else "Tamil Hit Songs")
            return
        }

        // 10. Instagram
        if (cleaned.contains("insta") || cleaned.contains("instagram") || cleaned.contains("இன்ஸ்டா")) {
            callback.onAppOpen("instagram")
            return
        }

        // 11. Camera / Selfie
        if (cleaned.contains("camera") || cleaned.contains("photo") || cleaned.contains("selfie") || cleaned.contains("கேமரா")) {
            callback.onAppOpen("camera")
            return
        }

        // 12. Spotify
        if (cleaned.contains("spotify") || cleaned.contains("ஸ்பாடிபை")) {
            callback.onAppOpen("spotify")
            return
        }

        // 13. Settings
        if (cleaned.contains("setting") || cleaned.contains("செட்டிங்ஸ்")) {
            callback.onAppOpen("settings")
            return
        }

        // 14. Calculator
        if (cleaned.contains("calc") || cleaned.contains("calculator") || cleaned.contains("கல்குலேட்டர்")) {
            callback.onAppOpen("calculator")
            return
        }

        // 15. Intelligent Gemini / AI Question Answering
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

    private fun queryDirectGemini(apiKey: String, prompt: String, callback: CommandCallback) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

        val systemPrompt = "You are Zara, a personal voice assistant. The user asked: '$prompt'. " +
                "Answer concisely in 1 or 2 conversational sentences in sweet Tamil or Tanglish. No markdown."

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
