package com.aura.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.aura.assistant.brain.GeminiBrain
import com.aura.assistant.brain.GeminiResponse
import com.aura.assistant.data.PreferenceManager
import com.aura.assistant.executor.PhoneTaskExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class VoicePipelineManager(
    private val context: Context,
    private val onDismissRequest: () -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = PreferenceManager.getInstance(context)
    private val taskExecutor = PhoneTaskExecutor(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var lastSpokenUtteranceId: String? = null

    init {
        initTts()
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.getDefault()
                isTtsInitialized = true
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == lastSpokenUtteranceId) {
                            scheduleDismissal(prefs.autoDismissDelayMs)
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        if (utteranceId == lastSpokenUtteranceId) {
                            scheduleDismissal(prefs.autoDismissDelayMs)
                        }
                    }
                })
            } else {
                Log.e(TAG, "Failed to initialize TextToSpeech engine")
            }
        }
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = SpeechState.Error("Speech recognition is not available on this device.")
            return
        }

        stopListening()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createRecognitionListener())
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        try {
            speechRecognizer?.startListening(intent)
            _state.value = SpeechState.Listening(partialTranscript = "", rmsDb = 0f)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting SpeechRecognizer", e)
            _state.value = SpeechState.Error("Failed to start voice listener: ${e.localizedMessage}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying speech recognizer", e)
        }
        speechRecognizer = null
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            private var currentTranscript = ""

            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = SpeechState.Listening(partialTranscript = "", rmsDb = 0f)
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                val currentState = _state.value
                if (currentState is SpeechState.Listening) {
                    _state.value = currentState.copy(rmsDb = rmsdB)
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                // User paused speaking; will receive onResults shortly
            }

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Try speaking again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timed out."
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                    SpeechRecognizer.ERROR_NETWORK -> "Network error occurred."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy."
                    else -> "Speech recognition error code: $error"
                }
                Log.w(TAG, "SpeechRecognizer error: $message ($error)")

                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    // Gracefully close on silence
                    scheduleDismissal(2000L)
                } else {
                    _state.value = SpeechState.Error(message)
                    scheduleDismissal(3000L)
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val finalPrompt = matches?.firstOrNull() ?: currentTranscript
                if (finalPrompt.isNotBlank()) {
                    processPrompt(finalPrompt)
                } else {
                    scheduleDismissal(1000L)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull() ?: ""
                if (partial.isNotBlank()) {
                    currentTranscript = partial
                    val currentState = _state.value
                    val rms = (currentState as? SpeechState.Listening)?.rmsDb ?: 0f
                    _state.value = SpeechState.Listening(partialTranscript = partial, rmsDb = rms)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    fun processPrompt(prompt: String) {
        _state.value = SpeechState.Thinking(userPrompt = prompt)

        scope.launch {
            val brain = GeminiBrain(
                apiKey = prefs.geminiApiKey,
                modelName = prefs.geminiModel
            )

            when (val result = brain.processUserPrompt(prompt)) {
                is GeminiResponse.SuccessFunctionCall -> {
                    val toolName = result.toolCall.functionName
                    _state.value = SpeechState.ExecutingTool(
                        userPrompt = prompt,
                        toolName = toolName,
                        statusMessage = "Executing $toolName..."
                    )

                    val outcome = taskExecutor.execute(result.toolCall)

                    _state.value = SpeechState.Speaking(
                        userPrompt = prompt,
                        assistantReply = outcome.spokenFeedback,
                        toolExecuted = outcome.actionLabel
                    )

                    speak(outcome.spokenFeedback)
                }

                is GeminiResponse.SuccessText -> {
                    _state.value = SpeechState.Speaking(
                        userPrompt = prompt,
                        assistantReply = result.text
                    )
                    speak(result.text)
                }

                is GeminiResponse.Failure -> {
                    _state.value = SpeechState.Error(result.errorMessage)
                    speak(result.errorMessage)
                }
            }
        }
    }

    private fun speak(text: String) {
        if (!isTtsInitialized || textToSpeech == null) {
            scheduleDismissal(prefs.autoDismissDelayMs)
            return
        }

        val utteranceId = "AuraUtterance_${System.currentTimeMillis()}"
        lastSpokenUtteranceId = utteranceId
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    private fun scheduleDismissal(delayMs: Long) {
        mainHandler.postDelayed({
            onDismissRequest()
        }, delayMs)
    }

    fun destroy() {
        stopListening()
        mainHandler.removeCallbacksAndMessages(null)
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
        scope.cancel()
    }

    companion object {
        private const val TAG = "VoicePipelineManager"
    }
}
