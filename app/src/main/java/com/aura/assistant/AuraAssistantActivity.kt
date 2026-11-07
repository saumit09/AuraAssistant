package com.aura.assistant

import android.app.Activity
import android.os.Bundle
import android.provider.Settings
import com.aura.assistant.service.OverlayService

/**
 * Trampoline activity for android.intent.action.ASSIST triggers.
 * Automatically spawns the floating overlay and finishes cleanly.
 */
class AuraAssistantActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Settings.canDrawOverlays(this)) {
            OverlayService.start(this)
        } else {
            // If overlay permission not granted, launch MainActivity
            startActivity(android.content.Intent(this, MainActivity::class.java))
        }

        finish()
    }
}
