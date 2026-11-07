package com.aura.assistant.service

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.aura.assistant.R
import com.aura.assistant.ui.SiriPopupOverlay
import com.aura.assistant.ui.theme.AuraTheme
import com.aura.assistant.voice.SpeechState
import com.aura.assistant.voice.VoicePipelineManager

/**
 * Layer 1 VoiceInteractionSession hosting the Layer 2 Jetpack Compose floating overlay.
 * Uses a non-modal transparent window anchored to the bottom of the screen
 * so the user's background app remains completely visible.
 */
class AuraVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    private var composeView: ComposeView? = null
    private var pipelineManager: VoicePipelineManager? = null

    override fun onCreate() {
        super.onCreate()
        setTheme(R.style.Theme_AuraAssistant_Transparent)

        val window = window ?: return
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        )
        window.setGravity(Gravity.BOTTOM)
        window.setBackgroundDrawableResource(android.R.color.transparent)

        pipelineManager = VoicePipelineManager(
            context = context,
            onDismissRequest = { hide() }
        )

        composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                AuraTheme {
                    val pipeline = pipelineManager ?: return@AuraTheme
                    val state by pipeline.state.collectAsState()

                    SiriPopupOverlay(
                        state = state,
                        onDismiss = { hide() },
                        onMicClick = {
                            if (state is SpeechState.Listening) {
                                pipeline.stopListening()
                            } else {
                                pipeline.startListening()
                            }
                        }
                    )
                }
            }
        }

        setContentView(composeView as View)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        pipelineManager?.startListening()
    }

    override fun onHide() {
        super.onHide()
        pipelineManager?.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        pipelineManager?.destroy()
        pipelineManager = null
        composeView = null
    }
}
