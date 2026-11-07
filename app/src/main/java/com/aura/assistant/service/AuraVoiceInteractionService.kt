package com.aura.assistant.service

import android.content.ComponentName
import android.service.voice.VoiceInteractionService
import android.util.Log

/**
 * Android VoiceInteractionService for system-wide default digital assistant binding.
 * When set as the Default Digital Assistant in Samsung One UI settings,
 * this service handles system assist gestures and side-key long presses.
 */
class AuraVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        val isActive = isActiveService(this, ComponentName(this, AuraVoiceInteractionService::class.java))
        Log.i(TAG, "AuraVoiceInteractionService onReady. Is active assistant: $isActive")
    }

    override fun onShutdown() {
        super.onShutdown()
        Log.i(TAG, "AuraVoiceInteractionService onShutdown")
    }

    companion object {
        private const val TAG = "AuraVoiceService"
    }
}
