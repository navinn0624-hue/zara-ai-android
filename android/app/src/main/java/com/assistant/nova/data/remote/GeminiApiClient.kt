package com.assistant.nova.data.remote

import android.util.Log
import com.assistant.nova.data.local.PreferencesManager
import com.assistant.nova.data.remote.dto.GeminiResponse
import com.assistant.nova.domain.model.AgentAction
import com.assistant.nova.domain.model.SettingType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiApiClient(
    private val preferencesManager: PreferencesManager,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateResponse(
        userPrompt: String,
        recentHistory: List<Pair<String, String>>, // sender ("user"/"model") to text
        memories: List<Pair<String, String>> = emptyList()
    ): GeminiResponse = withContext(Dispatchers.IO) {
        val apiKey = preferencesManager.geminiApiKey
        if (apiKey.isBlank()) {
            return@withContext GeminiResponse(
                replyText = "Please configure your Gemini API Key in Settings to enable AI responses.",
                isSuccess = false,
                errorMessage = "API key not configured"
            )
        }

        try {
            val url = "$BASE_URL/models/$MODEL_NAME:generateContent?key=$apiKey"
            val requestJson = buildRequestBody(userPrompt, recentHistory, memories)

            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Gemini API error ${response.code}: $body")
                    val parsedError = try {
                        JSONObject(body).optJSONObject("error")?.optString("message") ?: "Error ${response.code}"
                    } catch (e: Exception) {
                        "HTTP ${response.code}: ${response.message}"
                    }
                    return@withContext GeminiResponse(
                        replyText = "Sorry, I encountered an issue contacting the AI service: $parsedError",
                        isSuccess = false,
                        errorMessage = parsedError
                    )
                }

                parseGeminiResponse(body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network or parsing exception during Gemini call", e)
            GeminiResponse(
                replyText = "Network connection failed. Please check your internet connection.",
                isSuccess = false,
                errorMessage = e.localizedMessage
            )
        }
    }

    private fun buildRequestBody(
        userPrompt: String,
        recentHistory: List<Pair<String, String>>,
        memories: List<Pair<String, String>>
    ): JSONObject {
        val root = JSONObject()

        // 1. System Instruction
        val systemInstruction = JSONObject()
        val systemParts = JSONArray()
        val memoryContext = if (memories.isNotEmpty()) {
            val facts = memories.joinToString("; ") { "${it.first}: ${it.second}" }
            "\nPersistent user memories:\n$facts"
        } else ""

        val systemPrompt = """
            You are NOVA, an advanced personal AI voice assistant on an Android smartphone.
            Language Rules:
            - You fluently understand and speak English, Tamil (தமிழ்), and Tanglish (Tamil words written in English alphabet).
            - Always detect the language of the user query and reply in the EXACT same language and script.
            - If the user talks in Tamil script, reply in natural Tamil script.
            - If the user talks in Tanglish (e.g. 'alarm vai 6 am ku', 'eppadi irukka', 'whatsapp open pannu'), reply in conversational Tanglish!
            - Keep voice responses crisp, direct, and conversational (1 to 3 sentences maximum unless asked for more).

            Device Action Capabilities:
            You have device tools to help the user. If the user's intent is to perform an action on the phone, invoke the corresponding function call:
            - launch_app(appName, packageName)
            - set_alarm(hour, minute, label)
            - set_timer(durationSeconds, label)
            - open_setting(settingType) -> "wifi", "bluetooth", "display", "sound", "battery", "general"
            - prepare_call(contactName, phoneNumber)
            - prepare_message(recipient, messageText)
            - save_memory(key, value)
            - recall_memory(query)
            $memoryContext
        """.trimIndent()

        systemParts.put(JSONObject().put("text", systemPrompt))
        systemInstruction.put("parts", systemParts)
        root.put("system_instruction", systemInstruction)

        // 2. Tools declaration
        root.put("tools", buildToolsDeclaration())

        // 3. Contents (Conversation history + current turn)
        val contents = JSONArray()
        for ((role, text) in recentHistory.takeLast(6)) {
            val contentObj = JSONObject()
            contentObj.put("role", if (role.equals("user", ignoreCase = true)) "user" else "model")
            val parts = JSONArray()
            parts.put(JSONObject().put("text", text))
            contentObj.put("parts", parts)
            contents.put(contentObj)
        }

        // Current message
        val currentContent = JSONObject()
        currentContent.put("role", "user")
        val currentParts = JSONArray()
        currentParts.put(JSONObject().put("text", userPrompt))
        currentContent.put("parts", currentParts)
        contents.put(currentContent)

        root.put("contents", contents)

        // 4. Generation Config
        val genConfig = JSONObject()
        genConfig.put("temperature", 0.6)
        genConfig.put("maxOutputTokens", 512)
        root.put("generationConfig", genConfig)

        return root
    }

    private fun buildToolsDeclaration(): JSONArray {
        val toolsArray = JSONArray()
        val functionDeclarations = JSONArray()

        functionDeclarations.put(JSONObject().apply {
            put("name", "launch_app")
            put("description", "Launch an installed Android application by name")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("appName", JSONObject().put("type", "STRING").put("description", "Name of the app to launch, e.g. WhatsApp, YouTube, Camera, Calculator"))
                    put("packageName", JSONObject().put("type", "STRING").put("description", "Optional Android package name if known"))
                })
                put("required", JSONArray().put("appName"))
            })
        })

        functionDeclarations.put(JSONObject().apply {
            put("name", "set_alarm")
            put("description", "Set an alarm on the phone for a specific hour and minute")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("hour", JSONObject().put("type", "INTEGER").put("description", "Hour in 24-hour format (0-23)"))
                    put("minute", JSONObject().put("type", "INTEGER").put("description", "Minute (0-59)"))
                    put("label", JSONObject().put("type", "STRING").put("description", "Optional label or purpose for the alarm"))
                })
                put("required", JSONArray().put("hour").put("minute"))
            })
        })

        functionDeclarations.put(JSONObject().apply {
            put("name", "set_timer")
            put("description", "Set a countdown timer on the phone")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("durationSeconds", JSONObject().put("type", "INTEGER").put("description", "Timer duration in seconds"))
                    put("label", JSONObject().put("type", "STRING").put("description", "Label for the timer"))
                })
                put("required", JSONArray().put("durationSeconds"))
            })
        })

        functionDeclarations.put(JSONObject().apply {
            put("name", "open_setting")
            put("description", "Open a specific Android settings screen")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("settingType", JSONObject().put("type", "STRING").put("description", "Setting to open: 'wifi', 'bluetooth', 'display', 'sound', 'battery', or 'general'"))
                })
                put("required", JSONArray().put("settingType"))
            })
        })

        functionDeclarations.put(JSONObject().apply {
            put("name", "prepare_call")
            put("description", "Initiate a phone call to a contact or number (will ask user confirmation first)")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("contactName", JSONObject().put("type", "STRING").put("description", "Name of the contact or person"))
                    put("phoneNumber", JSONObject().put("type", "STRING").put("description", "Phone number to call if provided or resolved"))
                })
                put("required", JSONArray().put("contactName"))
            })
        })

        functionDeclarations.put(JSONObject().apply {
            put("name", "prepare_message")
            put("description", "Draft an SMS or message to a recipient (will ask user confirmation before sending)")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("recipient", JSONObject().put("type", "STRING").put("description", "Contact name or phone number"))
                    put("messageText", JSONObject().put("type", "STRING").put("description", "Body text of the message"))
                })
                put("required", JSONArray().put("recipient").put("messageText"))
            })
        })

        functionDeclarations.put(JSONObject().apply {
            put("name", "save_memory")
            put("description", "Save an important user fact or preference into long-term assistant memory")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("key", JSONObject().put("type", "STRING").put("description", "Memory topic or key, e.g. 'favorite_food', 'workplace', 'girlfriend_name'"))
                    put("value", JSONObject().put("type", "STRING").put("description", "Fact content to remember"))
                })
                put("required", JSONArray().put("key").put("value"))
            })
        })

        toolsArray.put(JSONObject().put("function_declarations", functionDeclarations))
        return toolsArray
    }

    private fun parseGeminiResponse(responseBody: String): GeminiResponse {
        val root = JSONObject(responseBody)
        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            return GeminiResponse("I didn't receive an answer. Please try again.")
        }

        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.optJSONObject("content")
        val parts = content?.optJSONArray("parts")

        if (parts == null || parts.length() == 0) {
            return GeminiResponse("No response content received.")
        }

        var textReply = ""
        var agentAction: AgentAction? = null

        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            if (part.has("text")) {
                textReply += part.getString("text")
            }
            if (part.has("functionCall")) {
                val functionCall = part.getJSONObject("functionCall")
                val fnName = functionCall.optString("name")
                val fnArgs = functionCall.optJSONObject("args") ?: JSONObject()
                agentAction = mapFunctionCallToAction(fnName, fnArgs)
            }
        }

        // If function was called and no text reply was generated, provide a standard descriptive reply
        if (textReply.isBlank() && agentAction != null) {
            textReply = when (agentAction) {
                is AgentAction.LaunchApp -> "Opening ${agentAction.appName}..."
                is AgentAction.SetAlarm -> "Setting alarm for ${String.format("%02d:%02d", agentAction.hour, agentAction.minute)}."
                is AgentAction.SetTimer -> "Setting timer for ${agentAction.durationSeconds} seconds."
                is AgentAction.OpenSetting -> "Opening ${agentAction.settingType.name.lowercase()} settings."
                is AgentAction.PrepareCall -> "Should I call ${agentAction.contactName}?"
                is AgentAction.PrepareMessage -> "Ready to send message to ${agentAction.recipient}. Would you like to proceed?"
                is AgentAction.SaveMemory -> "I have remembered that for you."
                is AgentAction.RecallMemory -> "Checking my memory..."
                is AgentAction.GeneralResponse -> agentAction.responseText
            }
        }

        return GeminiResponse(
            replyText = textReply.trim(),
            detectedAction = agentAction,
            isSuccess = true
        )
    }

    private fun mapFunctionCallToAction(name: String, args: JSONObject): AgentAction? {
        return try {
            when (name) {
                "launch_app" -> AgentAction.LaunchApp(
                    appName = args.optString("appName", ""),
                    packageName = if (args.has("packageName")) args.optString("packageName") else null
                )
                "set_alarm" -> AgentAction.SetAlarm(
                    hour = args.optInt("hour", 7),
                    minute = args.optInt("minute", 0),
                    label = args.optString("label", "NOVA Alarm")
                )
                "set_timer" -> AgentAction.SetTimer(
                    durationSeconds = args.optInt("durationSeconds", 60),
                    label = args.optString("label", "NOVA Timer")
                )
                "open_setting" -> {
                    val typeStr = args.optString("settingType", "general").uppercase()
                    val type = try {
                        SettingType.valueOf(typeStr)
                    } catch (e: Exception) {
                        SettingType.GENERAL
                    }
                    AgentAction.OpenSetting(type)
                }
                "prepare_call" -> AgentAction.PrepareCall(
                    contactName = args.optString("contactName", "Contact"),
                    phoneNumber = args.optString("phoneNumber", "")
                )
                "prepare_message" -> AgentAction.PrepareMessage(
                    recipient = args.optString("recipient", "Recipient"),
                    messageText = args.optString("messageText", "")
                )
                "save_memory" -> AgentAction.SaveMemory(
                    key = args.optString("key", "note"),
                    value = args.optString("value", "")
                )
                "recall_memory" -> AgentAction.RecallMemory(
                    query = args.optString("query", "")
                )
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error mapping function call $name", e)
            null
        }
    }

    companion object {
        private const val TAG = "GeminiApiClient"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val MODEL_NAME = "gemini-1.5-flash"
    }
}
