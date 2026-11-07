package com.aura.assistant.executor

data class ExecutionResult(
    val success: Boolean,
    val spokenFeedback: String,
    val actionLabel: String,
    val debugDetails: String? = null
)
