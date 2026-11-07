package com.aura.assistant.voice

sealed interface SpeechState {
    object Idle : SpeechState
    
    data class Listening(
        val partialTranscript: String = "",
        val rmsDb: Float = 0f
    ) : SpeechState

    data class Thinking(
        val userPrompt: String
    ) : SpeechState

    data class ExecutingTool(
        val userPrompt: String,
        val toolName: String,
        val statusMessage: String
    ) : SpeechState

    data class Speaking(
        val userPrompt: String,
        val assistantReply: String,
        val toolExecuted: String? = null
    ) : SpeechState

    data class Error(
        val message: String
    ) : SpeechState
}
