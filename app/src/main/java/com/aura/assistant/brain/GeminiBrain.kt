package com.aura.assistant.brain

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiBrain(
    private val apiKey: String,
    private val modelName: String = "gemini-2.0-flash"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun processUserPrompt(prompt: String): GeminiResponse = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext GeminiResponse.Failure(
                "Gemini API Key is missing. Please configure it in Aura Assistant settings."
            )
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

        val requestJson = buildRequestPayload(prompt)
        val body = requestJson.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = parseErrorMessage(responseBody, response.code)
                    Log.e(TAG, "Gemini API error ($response): $responseBody")
                    return@withContext GeminiResponse.Failure(errorMsg, response.code)
                }

                parseGeminiResponse(responseBody)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception calling Gemini API", e)
            GeminiResponse.Failure("Network error communicating with Gemini: ${e.localizedMessage}")
        }
    }

    private fun buildRequestPayload(prompt: String): JSONObject {
        val root = JSONObject()

        // System Instruction
        val systemInstruction = JSONObject().apply {
            val parts = JSONArray().put(JSONObject().apply {
                put("text", "You are Aura Assistant, an ultra-fast, intelligent system assistant for Samsung Galaxy and Android One UI devices. " +
                        "You control device hardware, make calls, send messages, launch apps, set alarms/timers, and interact with the screen. " +
                        "When the user request matches an action, invoke the corresponding tool function call. Always provide a concise confirmation response.")
            })
            put("parts", parts)
        }
        root.put("systemInstruction", systemInstruction)

        // Contents (User prompt)
        val contents = JSONArray().apply {
            val userContent = JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            }
            put(userContent)
        }
        root.put("contents", contents)

        // Tools / Function Declarations
        val toolsArray = JSONArray().apply {
            val toolObj = JSONObject().apply {
                put("functionDeclarations", buildFunctionDeclarations())
            }
            put(toolObj)
        }
        root.put("tools", toolsArray)

        return root
    }

    private fun buildFunctionDeclarations(): JSONArray {
        val declarations = JSONArray()

        // 1. open_app
        declarations.put(JSONObject().apply {
            put("name", "open_app")
            put("description", "Find and launch an installed application on the user's phone by name.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    put("app_name", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Name of the application to launch (e.g. 'Spotify', 'WhatsApp', 'YouTube', 'Camera', 'Gallery').")
                    })
                }
                put("properties", props)
                put("required", JSONArray().put("app_name"))
            })
        })

        // 2. make_phone_call
        declarations.put(JSONObject().apply {
            put("name", "make_phone_call")
            put("description", "Place a direct phone call to a contact name or a phone number.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    put("contact_name_or_number", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Contact name (e.g. 'Mom', 'John') or phone number digits.")
                    })
                }
                put("properties", props)
                put("required", JSONArray().put("contact_name_or_number"))
            })
        })

        // 3. send_message
        declarations.put(JSONObject().apply {
            put("name", "send_message")
            put("description", "Send a message via SMS or WhatsApp to a contact or phone number.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    put("app", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Messaging platform: 'sms' or 'whatsapp'.")
                    })
                    put("recipient", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Contact name or phone number of recipient.")
                    })
                    put("message", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Text content of the message.")
                    })
                }
                put("properties", props)
                put("required", JSONArray().put("app").put("recipient").put("message"))
            })
        })

        // 4. set_alarm_or_timer
        declarations.put(JSONObject().apply {
            put("name", "set_alarm_or_timer")
            put("description", "Set a clock alarm or countdown timer on the device.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    put("type", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Either 'alarm' or 'timer'.")
                    })
                    put("hour", JSONObject().apply {
                        put("type", "INTEGER")
                        put("description", "Hour of alarm in 24h format (0-23).")
                    })
                    put("minute", JSONObject().apply {
                        put("type", "INTEGER")
                        put("description", "Minute of alarm (0-59).")
                    })
                    put("duration_seconds", JSONObject().apply {
                        put("type", "INTEGER")
                        put("description", "Duration in seconds for a timer.")
                    })
                    put("label", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Optional label or description for alarm/timer.")
                    })
                }
                put("properties", props)
                put("required", JSONArray().put("type"))
            })
        })

        // 5. toggle_hardware
        declarations.put(JSONObject().apply {
            put("name", "toggle_hardware")
            put("description", "Controls hardware settings: flashlight, bluetooth_panel, wifi_panel, dnd, volume_level.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    put("setting", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Target setting: 'flashlight', 'bluetooth_panel', 'wifi_panel', 'dnd', 'volume_level'.")
                    })
                    put("state", JSONObject().apply {
                        put("type", "BOOLEAN")
                        put("description", "True to turn on / raise, False to turn off / mute.")
                    })
                }
                put("properties", props)
                put("required", JSONArray().put("setting").put("state"))
            })
        })

        // 6. screen_click_action
        declarations.put(JSONObject().apply {
            put("name", "screen_click_action")
            put("description", "Performs an accessibility tap on any visible screen button or text element.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    put("target_text", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Label, text, or content description of the on-screen element to click.")
                    })
                }
                put("properties", props)
                put("required", JSONArray().put("target_text"))
            })
        })

        return declarations
    }

    private fun parseGeminiResponse(jsonString: String): GeminiResponse {
        val root = JSONObject(jsonString)
        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            return GeminiResponse.Failure("No response candidate returned by Gemini.")
        }

        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.optJSONObject("content")
        val parts = content?.optJSONArray("parts")

        if (parts == null || parts.length() == 0) {
            return GeminiResponse.Failure("Empty content parts in Gemini response.")
        }

        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            if (part.has("functionCall")) {
                val functionCall = part.getJSONObject("functionCall")
                val functionName = functionCall.getString("name")
                val argsObj = functionCall.optJSONObject("args") ?: JSONObject()

                val argsMap = mutableMapOf<String, Any?>()
                val keys = argsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    argsMap[key] = argsObj.get(key)
                }

                return GeminiResponse.SuccessFunctionCall(
                    toolCall = GeminiToolCall(functionName, argsMap)
                )
            }
        }

        // Otherwise return plain text
        val textBuilder = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            if (part.has("text")) {
                textBuilder.append(part.getString("text"))
            }
        }

        return GeminiResponse.SuccessText(textBuilder.toString().trim())
    }

    private fun parseErrorMessage(body: String, code: Int): String {
        return try {
            val errorJson = JSONObject(body).optJSONObject("error")
            val message = errorJson?.optString("message") ?: "HTTP error $code"
            "Gemini Error ($code): $message"
        } catch (e: Exception) {
            "HTTP error $code from Gemini: $body"
        }
    }

    companion object {
        private const val TAG = "GeminiBrain"
    }
}
