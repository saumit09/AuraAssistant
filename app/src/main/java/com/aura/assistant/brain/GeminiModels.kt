package com.aura.assistant.brain

/**
 * Representation of a function tool called by Gemini.
 */
data class GeminiToolCall(
    val functionName: String,
    val arguments: Map<String, Any?>
)

/**
 * Outcome of a Gemini request.
 */
sealed interface GeminiResponse {
    data class SuccessText(
        val text: String
    ) : GeminiResponse

    data class SuccessFunctionCall(
        val toolCall: GeminiToolCall,
        val thoughts: String? = null
    ) : GeminiResponse

    data class Failure(
        val errorMessage: String,
        val statusCode: Int? = null
    ) : GeminiResponse
}
