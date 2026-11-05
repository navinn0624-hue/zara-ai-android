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

    interface AgentCallback {
        fun onExecuteCall(target: String, voiceReply: String)
        fun onExecuteWhatsApp(contact: String, message: String, voiceReply: String)
        fun onExecuteAppOpen(appName: String, voiceReply: String)
        fun onExecuteTorch(enable: Boolean, voiceReply: String)
        fun onExecuteVolume(raise: Boolean, mute: Boolean, voiceReply: String)
        fun onExecuteTimer(seconds: Int, label: String, voiceReply: String)
        fun onExecuteAlarm(hour: Int, minute: Int, label: String, voiceReply: String)
        fun onExecuteYouTube(query: String, voiceReply: String)
        fun onExecuteSettings(action: String, title: String, voiceReply: String)
        fun onConversational(reply: String, display: String)
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

    /**
     * Autonomous OpenJarvis Multi-Step Agent:
     * Evaluates natural speech (Tamil, Tanglish, English), decides on the right tool,
     * and calls the phone's native hardware/software executors.
     */
    fun process(context: Context, spokenText: String, callback: AgentCallback) {
        val prefs = context.getSharedPreferences("zara_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_GEMINI_KEY

        queryAgentBrain(context, spokenText, apiKey, callback)
    }

    private fun queryAgentBrain(context: Context, prompt: String, apiKey: String, callback: AgentCallback) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val systemPrompt = """
You are JARVIS, an autonomous personal AI Agent for Android inspired by Tony Stark's JARVIS and OpenJarvis.
You have full autonomous control over the user's Android smartphone.
The user speaks in Tamil, English, or Tanglish. Always address the user politely and respectfully as 'Sir' or 'Boss'.

Available tools to execute:
1. call_phone: { "target": string (contact name like 'Amma' or phone number) }
2. send_whatsapp: { "contact": string, "message": string }
3. open_app: { "app_name": string (e.g. 'Free Fire', 'Instagram', 'YouTube', 'Chrome', 'Gallery', etc.) }
4. control_hardware: { "device": "torch" | "volume" | "wifi" | "bluetooth", "action": "on" | "off" | "up" | "down" | "mute" }
5. set_timer: { "seconds": number, "label": string }
6. set_alarm: { "hour": number, "minute": number, "label": string }
7. play_media: { "query": string }
8. check_battery: {}
9. answer: { "reply_tanglish": string (accurate conversational explanation in sweet Tanglish/Tamil) }

Analyze the user's explicit or implicit intent (even with Tamil slang or casual speaking).
Always respond strictly with valid JSON:
{
  "thought": "Brief autonomous reasoning",
  "action": "call_phone" | "send_whatsapp" | "open_app" | "control_hardware" | "set_timer" | "set_alarm" | "play_media" | "check_battery" | "answer",
  "parameters": { ... },
  "voice_reply": "Tanglish or Tamil polite JARVIS phrase addressing user as Sir",
  "display_reply": "Short screen text"
}
""".trimIndent()

        val jsonPayload = JSONObject().apply {
            val contents = JSONArray().apply {
                val item = JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", "$systemPrompt\n\nUser: $prompt") })
                    })
                }
                put(item)
            }
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("responseMimeType", "application/json")
                put("maxOutputTokens", 350)
            })
        }

        val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(requestBody).build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                queryVercelBrain(context, prompt, callback)
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
                                        answer += part.getString("text")
                                    }
                                }
                            }
                            if (answer.isNotEmpty()) {
                                executeAgentAction(context, JSONObject(answer.trim()), callback)
                                return@post
                            }
                        }
                        queryVercelBrain(context, prompt, callback)
                    } catch (e: Exception) {
                        queryVercelBrain(context, prompt, callback)
                    }
                }
            }
        })
    }

    private fun queryVercelBrain(context: Context, text: String, callback: AgentCallback) {
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
                    callback.onConversational("Yes Sir, systems are standing by.", "Standing by...")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                Handler(Looper.getMainLooper()).post {
                    try {
                        val json = JSONObject(body)
                        executeAgentAction(context, json, callback)
                    } catch (e: Exception) {
                        callback.onConversational("Yes Sir, ready for your instructions.", "Ready, Sir.")
                    }
                }
            }
        })
    }

    /**
     * Executes the Agent's reasoned decision directly on the Android OS.
     */
    private fun executeAgentAction(context: Context, decision: JSONObject, callback: AgentCallback) {
        val action = decision.optString("action", "answer")
        val params = decision.optJSONObject("parameters") ?: JSONObject()
        val voiceReply = decision.optString("voice_reply", "Right away, Sir.")
        val displayReply = decision.optString("display_reply", "Executing...")

        when (action) {
            "call_phone" -> {
                val target = params.optString("target", "Amma")
                callback.onExecuteCall(target, voiceReply)
            }
            "send_whatsapp" -> {
                val contact = params.optString("contact", "Amma")
                val message = params.optString("message", "Hi")
                callback.onExecuteWhatsApp(contact, message, voiceReply)
            }
            "open_app" -> {
                val appName = params.optString("app_name", "settings")
                callback.onExecuteAppOpen(appName, voiceReply)
            }
            "control_hardware" -> {
                val device = params.optString("device", "torch")
                val act = params.optString("action", "on")
                when (device) {
                    "torch" -> callback.onExecuteTorch(act == "on", voiceReply)
                    "volume" -> callback.onExecuteVolume(act == "up", act == "mute", voiceReply)
                    "wifi" -> callback.onExecuteSettings(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi", voiceReply)
                    "bluetooth" -> callback.onExecuteSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth", voiceReply)
                    else -> callback.onConversational(voiceReply, displayReply)
                }
            }
            "set_timer" -> {
                val seconds = params.optInt("seconds", 60)
                val label = params.optString("label", "JARVIS Timer")
                callback.onExecuteTimer(seconds, label, voiceReply)
            }
            "set_alarm" -> {
                val hour = params.optInt("hour", 7)
                val minute = params.optInt("minute", 0)
                val label = params.optString("label", "JARVIS Alarm")
                callback.onExecuteAlarm(hour, minute, label, voiceReply)
            }
            "play_media" -> {
                val query = params.optString("query", "Tamil songs")
                callback.onExecuteYouTube(query, voiceReply)
            }
            "check_battery" -> {
                val batteryStatus: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val rep = "Battery level is at $level percent, Sir."
                callback.onConversational(rep, "Battery: $level%")
            }
            else -> {
                val rep = params.optString("reply_tanglish", voiceReply)
                callback.onConversational(rep, displayReply)
            }
        }
    }
}
