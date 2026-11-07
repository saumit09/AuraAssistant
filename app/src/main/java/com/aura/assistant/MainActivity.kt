package com.aura.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aura.assistant.data.PreferenceManager
import com.aura.assistant.service.AuraAccessibilityService
import com.aura.assistant.service.OverlayService
import com.aura.assistant.ui.theme.AuraBlue
import com.aura.assistant.ui.theme.AuraCardBorder
import com.aura.assistant.ui.theme.AuraCardSurface
import com.aura.assistant.ui.theme.AuraCyan
import com.aura.assistant.ui.theme.AuraDarkBackground
import com.aura.assistant.ui.theme.AuraGreen
import com.aura.assistant.ui.theme.AuraMagenta
import com.aura.assistant.ui.theme.AuraTextMuted
import com.aura.assistant.ui.theme.AuraTextPrimary
import com.aura.assistant.ui.theme.AuraTextSecondary
import com.aura.assistant.ui.theme.AuraTheme
import com.aura.assistant.ui.theme.AuraViolet

class MainActivity : ComponentActivity() {

    private lateinit var prefs: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferenceManager.getInstance(this)

        setContent {
            AuraTheme {
                SetupDashboard(
                    prefs = prefs,
                    onLaunchOverlay = { launchOverlay() },
                    onRefreshPermissions = { /* Recomposed via state */ }
                )
            }
        }
    }

    private fun launchOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please enable 'Appear on top' permission first.", Toast.LENGTH_SHORT).show()
            openOverlaySettings()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Please grant Microphone permission first.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!prefs.hasApiKey) {
            Toast.makeText(this, "Please save your Gemini API Key before testing.", Toast.LENGTH_SHORT).show()
            return
        }

        OverlayService.start(this)
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}

@Composable
fun SetupDashboard(
    prefs: PreferenceManager,
    onLaunchOverlay: () -> Unit,
    onRefreshPermissions: () -> Unit
) {
    val context = LocalContext.current

    var apiKeyInput by remember { mutableStateOf(prefs.geminiApiKey) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var apiKeySavedFeedback by remember { mutableStateOf(false) }

    // Dynamic Permission Checks
    var hasAudioPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var hasPhonePermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED)
    }
    var hasContactsPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var isAccessibilityActive by remember {
        mutableStateOf(AuraAccessibilityService.isServiceRunning)
    }

    // Permission Request Launcher
    val runtimePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasAudioPermission = results[Manifest.permission.RECORD_AUDIO] ?: hasAudioPermission
        hasPhonePermission = results[Manifest.permission.CALL_PHONE] ?: hasPhonePermission
        hasContactsPermission = results[Manifest.permission.READ_CONTACTS] ?: hasContactsPermission
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AuraDarkBackground
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Aura Branding
            item {
                HeaderCard()
            }

            // Quick Launch Floating Assistant Bar
            item {
                Button(
                    onClick = onLaunchOverlay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuraCyan
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = AuraDarkBackground,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Test Assistant Overlay",
                        color = AuraDarkBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            // Card 1: Gemini API Configuration
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AuraCardBorder, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = AuraCardSurface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = AuraCyan,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Gemini API Key",
                                color = AuraTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Stored encrypted via Android Keystore (EncryptedSharedPreferences).",
                            color = AuraTextSecondary,
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Paste AI Studio Gemini Key…", color = AuraTextMuted) },
                            singleLine = true,
                            visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = {
                                prefs.geminiApiKey = apiKeyInput
                                apiKeySavedFeedback = true
                            }),
                            trailingIcon = {
                                IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                    Icon(
                                        imageVector = if (isApiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle visibility",
                                        tint = AuraTextSecondary
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AuraCyan,
                                unfocusedBorderColor = AuraCardBorder,
                                focusedTextColor = AuraTextPrimary,
                                unfocusedTextColor = AuraTextPrimary,
                                cursorColor = AuraCyan
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedVisibility(visible = apiKeySavedFeedback || prefs.hasApiKey) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = AuraGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Saved securely",
                                        color = AuraGreen,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    prefs.geminiApiKey = apiKeyInput
                                    apiKeySavedFeedback = true
                                    Toast.makeText(context, "Gemini API key saved!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AuraViolet),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Save Key", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Card 2: Samsung One UI Permissions Checklist
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AuraCardBorder, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = AuraCardSurface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Samsung One UI Setup Checklist",
                            color = AuraTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Enable system permissions for full device integration.",
                            color = AuraTextSecondary,
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Permission Item 1: Audio & Runtime
                        PermissionRowItem(
                            title = "Microphone & Telephony",
                            description = "Required for live voice STT and direct phone calls.",
                            isGranted = hasAudioPermission && hasPhonePermission && hasContactsPermission,
                            icon = Icons.Default.Mic,
                            buttonLabel = "Grant",
                            onAction = {
                                val perms = mutableListOf(
                                    Manifest.permission.RECORD_AUDIO,
                                    Manifest.permission.CALL_PHONE,
                                    Manifest.permission.READ_CONTACTS
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                runtimePermissionsLauncher.launch(perms.toTypedArray())
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Permission Item 2: Appear On Top
                        PermissionRowItem(
                            title = "Appear on Top (Overlay)",
                            description = "Allows the Siri frosted-glass capsule to float above any app.",
                            isGranted = hasOverlayPermission,
                            icon = Icons.Default.Layers,
                            buttonLabel = "Enable",
                            onAction = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Permission Item 3: Default Assistant
                        PermissionRowItem(
                            title = "Default Digital Assistant",
                            description = "Binds Aura to Samsung Side Key and gesture swipe-ups.",
                            isGranted = false, // Direct check via settings
                            icon = Icons.Default.SettingsSuggest,
                            buttonLabel = "Configure",
                            onAction = {
                                val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Permission Item 4: Accessibility Service
                        PermissionRowItem(
                            title = "Aura Accessibility Service",
                            description = "Enables automatic WhatsApp message sending and UI clicks.",
                            isGranted = isAccessibilityActive,
                            icon = Icons.Default.AccessibilityNew,
                            buttonLabel = "Turn On",
                            onAction = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Permission Item 5: Battery Optimization
                        PermissionRowItem(
                            title = "Unrestricted Battery",
                            description = "Prevents Samsung One UI from killing the background service.",
                            isGranted = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                                ?.isIgnoringBatteryOptimizations(context.packageName) ?: false,
                            icon = Icons.Default.BatteryAlert,
                            buttonLabel = "Optimize",
                            onAction = {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                }
                            }
                        )
                    }
                }
            }

            // Card 3: Samsung Galaxy Hardware Tips
            item {
                SamsungHardwareTipsCard()
            }
        }
    }
}

@Composable
private fun HeaderCard() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(AuraCyan, AuraViolet, AuraDarkBackground)
                    )
                )
                .border(2.dp, AuraCyan.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = AuraDarkBackground,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Aura Assistant",
            color = AuraTextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Native Gemini-Powered Voice Assistant for One UI",
            color = AuraTextSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun PermissionRowItem(
    title: String,
    description: String,
    isGranted: Boolean,
    icon: ImageVector,
    buttonLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isGranted) AuraGreen.copy(alpha = 0.15f) else Color(0x228A2BE2)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else icon,
                contentDescription = null,
                tint = if (isGranted) AuraGreen else AuraCyan,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = AuraTextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            Text(
                text = description,
                color = AuraTextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isGranted) {
            Text(
                text = "Active",
                color = AuraGreen,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        } else {
            OutlinedButton(
                onClick = onAction,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraCyan),
                border = androidx.compose.foundation.BorderStroke(1.dp, AuraCyan.copy(alpha = 0.6f))
            ) {
                Text(buttonLabel, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SamsungHardwareTipsCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AuraCardBorder, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = AuraCardSurface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Samsung Galaxy One UI Tips",
                color = AuraTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• Remap Side Key: Settings -> Advanced features -> Side button -> Press and hold -> Default digital assistant (Aura Assistant).\n" +
                        "• Swipe Up Gesture: In full-screen gesture mode, swipe diagonally inward from bottom-left or bottom-right corner.\n" +
                        "• WhatsApp Auto-Send: When requesting \"Send WhatsApp to Mom saying I am on my way\", Aura opens the chat and taps Send automatically via Accessibility.",
                color = AuraTextSecondary,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }
    }
}
