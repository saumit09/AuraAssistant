package com.aura.assistant.executor

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import com.aura.assistant.brain.GeminiToolCall
import com.aura.assistant.service.AuraAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhoneTaskExecutor(private val context: Context) {

    suspend fun execute(toolCall: GeminiToolCall): ExecutionResult = withContext(Dispatchers.Default) {
        val name = toolCall.functionName
        val args = toolCall.arguments
        Log.i(TAG, "Executing tool: $name with args: $args")

        try {
            when (name) {
                "open_app" -> {
                    val appName = args["app_name"]?.toString() ?: ""
                    executeOpenApp(appName)
                }

                "make_phone_call" -> {
                    val target = args["contact_name_or_number"]?.toString() ?: ""
                    executeMakePhoneCall(target)
                }

                "send_message" -> {
                    val app = args["app"]?.toString() ?: "sms"
                    val recipient = args["recipient"]?.toString() ?: ""
                    val message = args["message"]?.toString() ?: ""
                    executeSendMessage(app, recipient, message)
                }

                "set_alarm_or_timer" -> {
                    val type = args["type"]?.toString() ?: "alarm"
                    val hour = (args["hour"] as? Number)?.toInt()
                    val minute = (args["minute"] as? Number)?.toInt()
                    val durationSeconds = (args["duration_seconds"] as? Number)?.toInt()
                    val label = args["label"]?.toString() ?: "Aura Assistant"
                    executeSetAlarmOrTimer(type, hour, minute, durationSeconds, label)
                }

                "toggle_hardware" -> {
                    val setting = args["setting"]?.toString() ?: ""
                    val state = args["state"] as? Boolean ?: true
                    executeToggleHardware(setting, state)
                }

                "screen_click_action" -> {
                    val targetText = args["target_text"]?.toString() ?: ""
                    executeScreenClick(targetText)
                }

                else -> ExecutionResult(
                    success = false,
                    spokenFeedback = "I don't know how to handle the action $name.",
                    actionLabel = "Unknown Action"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing action $name", e)
            ExecutionResult(
                success = false,
                spokenFeedback = "Failed to complete the action: ${e.localizedMessage}",
                actionLabel = "Action Failed",
                debugDetails = e.stackTraceToString()
            )
        }
    }

    private fun executeOpenApp(appName: String): ExecutionResult {
        if (appName.isBlank()) {
            return ExecutionResult(false, "No app name was specified.", "Open App")
        }

        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        var bestMatchPkg: String? = null
        var bestMatchLabel: String? = null
        var bestScore = 0.0

        for (app in installedApps) {
            // Must have a valid launch intent
            val launchIntent = pm.getLaunchIntentForPackage(app.packageName) ?: continue
            val label = pm.getApplicationLabel(app).toString()

            val score = calculateMatchScore(appName.lowercase(), label.lowercase())
            if (score > bestScore) {
                bestScore = score
                bestMatchPkg = app.packageName
                bestMatchLabel = label
            }
        }

        if (bestMatchPkg != null && bestScore >= 0.5) {
            val intent = pm.getLaunchIntentForPackage(bestMatchPkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            if (intent != null) {
                context.startActivity(intent)
                return ExecutionResult(
                    success = true,
                    spokenFeedback = "Opening $bestMatchLabel.",
                    actionLabel = "Launched $bestMatchLabel"
                )
            }
        }

        return ExecutionResult(
            success = false,
            spokenFeedback = "Could not find any installed app matching $appName.",
            actionLabel = "App Not Found"
        )
    }

    private fun executeMakePhoneCall(target: String): ExecutionResult {
        if (target.isBlank()) {
            return ExecutionResult(false, "No phone number or contact provided.", "Call")
        }

        val isNumber = target.any { it.isDigit() } && target.filter { it.isDigit() }.length >= 5
        val phoneNumber: String?
        val displayName: String

        if (isNumber) {
            phoneNumber = target.trim()
            displayName = target.trim()
        } else {
            val contact = lookupContactPhone(target)
            if (contact != null) {
                phoneNumber = contact.second
                displayName = contact.first
            } else {
                return ExecutionResult(
                    success = false,
                    spokenFeedback = "Could not find contact $target in your phonebook.",
                    actionLabel = "Contact Not Found"
                )
            }
        }

        val cleanPhone = phoneNumber.filter { it.isDigit() || it == '+' }
        val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanPhone")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(callIntent)
            ExecutionResult(
                success = true,
                spokenFeedback = "Calling $displayName now.",
                actionLabel = "Calling $displayName"
            )
        } catch (e: SecurityException) {
            // Fallback to dialer if CALL_PHONE permission is not granted
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            ExecutionResult(
                success = true,
                spokenFeedback = "Opening dialer for $displayName.",
                actionLabel = "Dialing $displayName"
            )
        }
    }

    private fun executeSendMessage(app: String, recipient: String, message: String): ExecutionResult {
        val isWhatsApp = app.contains("whatsapp", ignoreCase = true) || app.contains("wa", ignoreCase = true)
        
        // Find phone number for contact if needed
        val targetNumber = if (recipient.any { it.isDigit() } && recipient.filter { it.isDigit() }.length >= 5) {
            recipient.filter { it.isDigit() || it == '+' }
        } else {
            lookupContactPhone(recipient)?.second?.filter { it.isDigit() || it == '+' }
        }

        if (isWhatsApp) {
            if (targetNumber.isNullOrBlank()) {
                return ExecutionResult(
                    success = false,
                    spokenFeedback = "Could not find a phone number for $recipient to send WhatsApp message.",
                    actionLabel = "WhatsApp Contact Missing"
                )
            }

            // Clean number without '+' for WhatsApp deep link
            val waNumber = targetNumber.replace("+", "").trim()
            val waUri = Uri.parse("https://wa.me/$waNumber?text=${Uri.encode(message)}")
            val waIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            return try {
                // Schedule accessibility auto-tap on WhatsApp's Send button
                AuraAccessibilityService.instance?.scheduleWhatsAppAutoSend()

                context.startActivity(waIntent)
                ExecutionResult(
                    success = true,
                    spokenFeedback = "Sending WhatsApp message to $recipient.",
                    actionLabel = "WhatsApp Sent"
                )
            } catch (e: Exception) {
                // If WhatsApp is not installed, fallback to SMS
                executeSendSms(targetNumber, recipient, message)
            }
        } else {
            return executeSendSms(targetNumber ?: recipient, recipient, message)
        }
    }

    private fun executeSendSms(number: String, recipientLabel: String, message: String): ExecutionResult {
        val smsUri = Uri.parse("smsto:${number.filter { it.isDigit() || it == '+' }}")
        val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(smsIntent)
            ExecutionResult(
                success = true,
                spokenFeedback = "Composing SMS to $recipientLabel.",
                actionLabel = "SMS Composed"
            )
        } catch (e: Exception) {
            ExecutionResult(
                success = false,
                spokenFeedback = "Unable to open messaging app: ${e.localizedMessage}",
                actionLabel = "SMS Failed"
            )
        }
    }

    private fun executeSetAlarmOrTimer(
        type: String,
        hour: Int?,
        minute: Int?,
        durationSeconds: Int?,
        label: String
    ): ExecutionResult {
        return if (type.contains("timer", ignoreCase = true)) {
            val seconds = durationSeconds ?: 60
            val timerIntent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(timerIntent)
            val minutesFormatted = if (seconds >= 60) "${seconds / 60} minutes" else "$seconds seconds"
            ExecutionResult(
                success = true,
                spokenFeedback = "Timer set for $minutesFormatted.",
                actionLabel = "Timer: $minutesFormatted"
            )
        } else {
            val h = hour ?: 7
            val m = minute ?: 0
            val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, h)
                putExtra(AlarmClock.EXTRA_MINUTES, m)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(alarmIntent)
            val timeFormatted = String.format("%02d:%02d", h, m)
            ExecutionResult(
                success = true,
                spokenFeedback = "Alarm set for $timeFormatted.",
                actionLabel = "Alarm: $timeFormatted"
            )
        }
    }

    private fun executeToggleHardware(setting: String, state: Boolean): ExecutionResult {
        val s = setting.lowercase()
        return when {
            s.contains("flashlight") || s.contains("torch") -> {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return ExecutionResult(
                    false, "Flashlight hardware unavailable.", "Torch Failed"
                )
                cameraManager.setTorchMode(cameraId, state)
                val status = if (state) "on" else "off"
                ExecutionResult(
                    success = true,
                    spokenFeedback = "Flashlight turned $status.",
                    actionLabel = "Flashlight $status"
                )
            }

            s.contains("volume") -> {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val direction = if (state) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
                ExecutionResult(
                    success = true,
                    spokenFeedback = if (state) "Volume raised." else "Volume lowered.",
                    actionLabel = "Volume Adjusted"
                )
            }

            s.contains("wifi") -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(panelIntent)
                } else {
                    val settingsIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                }
                ExecutionResult(
                    success = true,
                    spokenFeedback = "Opening Wi-Fi controls.",
                    actionLabel = "Wi-Fi Panel"
                )
            }

            s.contains("bluetooth") || s.contains("internet") -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val panelIntent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(panelIntent)
                } else {
                    val settingsIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                }
                ExecutionResult(
                    success = true,
                    spokenFeedback = "Opening connectivity controls.",
                    actionLabel = "Connectivity Panel"
                )
            }

            s.contains("dnd") || s.contains("do not disturb") -> {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.isNotificationPolicyAccessGranted) {
                    val filter = if (state) {
                        NotificationManager.INTERRUPTION_FILTER_ALARMS
                    } else {
                        NotificationManager.INTERRUPTION_FILTER_ALL
                    }
                    nm.setInterruptionFilter(filter)
                    ExecutionResult(
                        success = true,
                        spokenFeedback = if (state) "Do Not Disturb turned on." else "Do Not Disturb turned off.",
                        actionLabel = "DND Toggled"
                    )
                } else {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ExecutionResult(
                        success = true,
                        spokenFeedback = "Please allow Do Not Disturb permission in settings.",
                        actionLabel = "DND Permission Required"
                    )
                }
            }

            else -> ExecutionResult(
                success = false,
                spokenFeedback = "Unknown hardware setting: $setting",
                actionLabel = "Unknown Hardware"
            )
        }
    }

    private fun executeScreenClick(targetText: String): ExecutionResult {
        val a11y = AuraAccessibilityService.instance
        if (a11y == null) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return ExecutionResult(
                success = false,
                spokenFeedback = "Please enable Aura Assistant in Accessibility Settings to allow screen actions.",
                actionLabel = "Enable Accessibility"
            )
        }

        val clicked = a11y.clickNodeByText(targetText)
        return if (clicked) {
            ExecutionResult(
                success = true,
                spokenFeedback = "Tapped on $targetText.",
                actionLabel = "Clicked: $targetText"
            )
        } else {
            ExecutionResult(
                success = false,
                spokenFeedback = "Could not find an on-screen element with text $targetText.",
                actionLabel = "Click Target Not Found"
            )
        }
    }

    private fun lookupContactPhone(nameQuery: String): Pair<String, String>? {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$nameQuery%")

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val name = cursor.getString(nameIdx)
                    val number = cursor.getString(numIdx)
                    return Pair(name, number)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up contact", e)
        }
        return null
    }

    private fun calculateMatchScore(query: String, candidate: String): Double {
        if (query == candidate) return 1.0
        if (candidate.startsWith(query)) return 0.9
        if (candidate.contains(query)) return 0.75
        val queryWords = query.split(" ")
        val matchedWords = queryWords.count { candidate.contains(it) }
        return matchedWords.toDouble() / queryWords.size.coerceAtLeast(1) * 0.6
    }

    companion object {
        private const val TAG = "PhoneTaskExecutor"
    }
}
