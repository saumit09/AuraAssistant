package com.aura.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AuraAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isWaitingForWhatsAppSend = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "AuraAccessibilityService connected and ready")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (isWaitingForWhatsAppSend) {
            val pkg = event.packageName?.toString() ?: ""
            if (pkg.contains("whatsapp", ignoreCase = true)) {
                // WhatsApp window became active, trigger auto-tap on Send
                mainHandler.postDelayed({
                    if (isWaitingForWhatsAppSend) {
                        tryClickWhatsAppSendButton()
                    }
                }, 500)
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "AuraAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    fun scheduleWhatsAppAutoSend() {
        isWaitingForWhatsAppSend = true
        // Set a timeout to cancel auto-send wait after 8 seconds
        mainHandler.postDelayed({
            isWaitingForWhatsAppSend = false
        }, 8000)
    }

    private fun tryClickWhatsAppSendButton(): Boolean {
        val root = rootInActiveWindow ?: return false
        try {
            // 1. Try finding by resource ID
            val sendById = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
            if (sendById.isNotEmpty()) {
                val node = sendById[0]
                val clicked = performClick(node)
                if (clicked) {
                    isWaitingForWhatsAppSend = false
                    Log.i(TAG, "Successfully auto-tapped WhatsApp send by ID")
                    return true
                }
            }

            // 2. Try finding by content description or text "Send"
            val sendByText = root.findAccessibilityNodeInfosByText("Send")
            for (node in sendByText) {
                if (performClick(node)) {
                    isWaitingForWhatsAppSend = false
                    Log.i(TAG, "Successfully auto-tapped WhatsApp send by text")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error trying to click WhatsApp send button", e)
        }
        return false
    }

    fun clickNodeByText(targetText: String): Boolean {
        val root = rootInActiveWindow ?: return false
        try {
            val nodes = root.findAccessibilityNodeInfosByText(targetText)
            for (node in nodes) {
                if (performClick(node)) {
                    Log.i(TAG, "Clicked node matching text: $targetText")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clicking node with text: $targetText", e)
        }
        return false
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return false
    }

    companion object {
        private const val TAG = "AuraA11yService"

        @Volatile
        var instance: AuraAccessibilityService? = null
            private set

        val isServiceRunning: Boolean
            get() = instance != null
    }
}
