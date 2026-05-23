package com.example.mobileaudiowhatsapp.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.mobileaudiowhatsapp.History
import com.example.mobileaudiowhatsapp.Settings as SettingsRoute
import com.example.mobileaudiowhatsapp.data.AppDatabase
import com.example.mobileaudiowhatsapp.service.CallFolderWatcherService
import androidx.core.content.FileProvider
import androidx.compose.material.icons.filled.Share
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

// Sleek dark palette design system
val SlateDarkBg = Color(0xFF0A0D18)
val GlassCardColor = Color(0xFF141829)
val GlassBorderColor = Color(0xFF242B49)
val AccentViolet = Color(0xFF7C4DFF)
val AccentTeal = Color(0xFF00E5FF)
val ActiveGreen = Color(0xFF00E676)
val PausedOrange = Color(0xFFFF9100)
val StoppedRed = Color(0xFFFF5252)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavHostController) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getInstance(context) }
    val dao = remember { database.callLogDao() }

    val completedCount by dao.observeCompletedCount().collectAsState(initial = 0)
    val pendingCount by dao.observePendingCount().collectAsState(initial = 0)
    val skippedCount by dao.observeSkippedCount().collectAsState(initial = 0)

    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    
    // Setup wizard states
    var allFilesAccessGranted by remember { mutableStateOf(false) }
    var batteryExemptionGranted by remember { mutableStateOf(false) }
    var notificationPermissionGranted by remember { mutableStateOf(false) }
    var miuiGuidanceDismissed by remember { mutableStateOf(false) }
    var watchDirSet by remember { mutableStateOf(false) }
    var configuredWatchDir by remember { mutableStateOf("") }

    // Read permissions and status
    fun checkPermissions() {
        allFilesAccessGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        batteryExemptionGranted = pm.isIgnoringBatteryOptimizations(context.packageName)

        notificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        miuiGuidanceDismissed = prefs.getBoolean("miui_guidance_dismissed", false)
        val dir = prefs.getString("watch_dir", null)
        watchDirSet = !dir.isNullOrBlank() && File(dir).exists() && File(dir).isDirectory
        configuredWatchDir = dir ?: ""
    }

    // Refresh permissions on Resume
    LaunchedEffect(Unit) {
        checkPermissions()
    }

    val setupCompleted = allFilesAccessGranted && batteryExemptionGranted && notificationPermissionGranted && miuiGuidanceDismissed && watchDirSet

    // Watcher running status (is service running?)
    // Simple state by testing whether service is running
    var isServiceRunning by remember { mutableStateOf(false) }
    
    @Suppress("DEPRECATION")
    fun updateServiceStatus() {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        var running = false
        for (service in activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (CallFolderWatcherService::class.java.name == service.service.className) {
                running = true
                break
            }
        }
        isServiceRunning = running
    }

    LaunchedEffect(setupCompleted) {
        updateServiceStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SilentScribe",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif
                    )
                },
                actions = {
                    if (setupCompleted) {
                        IconButton(onClick = { shareDatabase(context) }) {
                            Icon(Icons.Default.Share, contentDescription = "Export Database", tint = Color.White)
                        }
                    }
                    IconButton(onClick = {
                        checkPermissions()
                        updateServiceStatus()
                        Toast.makeText(context, "Status refreshed", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                    if (setupCompleted) {
                        IconButton(onClick = { navController.navigate(SettingsRoute) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SlateDarkBg,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(SlateDarkBg, Color(0xFF0F1326))
                    )
                )
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!setupCompleted) {
                    SetupWizardCard(
                        context = context,
                        allFilesAccessGranted = allFilesAccessGranted,
                        batteryExemptionGranted = batteryExemptionGranted,
                        notificationPermissionGranted = notificationPermissionGranted,
                        miuiGuidanceDismissed = miuiGuidanceDismissed,
                        watchDirSet = watchDirSet,
                        configuredWatchDir = configuredWatchDir,
                        onFolderConfigured = { path ->
                            prefs.edit().putString("watch_dir", path).apply()
                            checkPermissions()
                            updateServiceStatus()
                        },
                        onMiuiDismissed = {
                            prefs.edit().putBoolean("miui_guidance_dismissed", true).apply()
                            checkPermissions()
                        },
                        onPermissionRefresh = {
                            checkPermissions()
                        }
                    )
                } else {
                    DashboardContent(
                        context = context,
                        completedCount = completedCount,
                        pendingCount = pendingCount,
                        skippedCount = skippedCount,
                        isServiceRunning = isServiceRunning,
                        configuredWatchDir = configuredWatchDir,
                        onToggleService = {
                            if (isServiceRunning) {
                                context.stopService(Intent(context, CallFolderWatcherService::class.java))
                                Toast.makeText(context, "Service Stopped", Toast.LENGTH_SHORT).show()
                            } else {
                                ContextCompat.startForegroundService(
                                    context,
                                    Intent(context, CallFolderWatcherService::class.java)
                                )
                                Toast.makeText(context, "Service Started", Toast.LENGTH_SHORT).show()
                            }
                            updateServiceStatus()
                        },
                        onNavigateToHistory = {
                            navController.navigate(History)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SetupWizardCard(
    context: Context,
    allFilesAccessGranted: Boolean,
    batteryExemptionGranted: Boolean,
    notificationPermissionGranted: Boolean,
    miuiGuidanceDismissed: Boolean,
    watchDirSet: Boolean,
    configuredWatchDir: String,
    onFolderConfigured: (String) -> Unit,
    onMiuiDismissed: () -> Unit,
    onPermissionRefresh: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassBorderColor, RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = GlassCardColor),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Initial Setup Wizard",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Please complete the following configuration checklist to enable offline call recording watch and speech-to-text transcription.",
                fontSize = 13.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            // Step 1: Storage Permission
            SetupStepItem(
                stepIndex = 1,
                title = "All Files Access Permission",
                description = "Required to scan MIUI call recordings from device storage.",
                isCompleted = allFilesAccessGranted,
                actionText = "Grant Storage Permission",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } else {
                        val activity = context as? Activity
                        if (activity != null) {
                            androidx.core.app.ActivityCompat.requestPermissions(
                                activity,
                                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
                                101
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step 2: Battery Restrictions
            SetupStepItem(
                stepIndex = 2,
                title = "Battery Whitelist",
                description = "Allows the background service to run indefinitely without system interference.",
                isCompleted = batteryExemptionGranted,
                actionText = "Disable Battery Limits",
                onAction = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step 3: Notification Permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    onPermissionRefresh()
                }

                SetupStepItem(
                    stepIndex = 3,
                    title = "Post Notifications",
                    description = "Required for the background service foreground status notification.",
                    isCompleted = notificationPermissionGranted,
                    actionText = "Allow Notifications",
                    onAction = {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Step 4: MIUI Autostart & Battery Optimizer Guidelines
            SetupStepItem(
                stepIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) 4 else 3,
                title = "MIUI Background Guidelines",
                description = "Autostart: Enabled\nApp Battery Saver: No Restrictions",
                isCompleted = miuiGuidanceDismissed,
                actionText = "Open App Info",
                onAction = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                },
                secondaryActionText = "I have configured this",
                onSecondaryAction = onMiuiDismissed
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step 5: Watch Directory
            SetupDirectoryPickerStep(
                stepIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) 5 else 4,
                isCompleted = watchDirSet,
                currentPath = configuredWatchDir,
                onFolderSaved = onFolderConfigured
            )
        }
    }
}

@Composable
fun SetupStepItem(
    stepIndex: Int,
    title: String,
    description: String,
    isCompleted: Boolean,
    actionText: String,
    onAction: () -> Unit,
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E233C), RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (isCompleted) Color(0xFF00E676).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    if (isCompleted) Color(0xFF00E676).copy(alpha = 0.1f) else AccentViolet.copy(alpha = 0.1f),
                    CircleShape
                )
                .align(Alignment.CenterVertically),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Done",
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    text = "$stepIndex",
                    color = AccentViolet,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = Color.LightGray,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (!isCompleted) {
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onAction,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentViolet),
                        contentPadding = ButtonDefaults.ContentPadding,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(actionText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    if (secondaryActionText != null && onSecondaryAction != null) {
                        Button(
                            onClick = onSecondaryAction,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            contentPadding = ButtonDefaults.ContentPadding,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(secondaryActionText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SetupDirectoryPickerStep(
    stepIndex: Int,
    isCompleted: Boolean,
    currentPath: String,
    onFolderSaved: (String) -> Unit
) {
    val context = LocalContext.current
    var pathText by remember(currentPath) { mutableStateOf(currentPath) }
    var errorMsg by remember { mutableStateOf("") }

    LaunchedEffect(currentPath) {
        if (currentPath.isNotEmpty()) {
            val f = File(currentPath)
            if (!f.exists()) {
                errorMsg = "Folder does not exist"
            } else if (!f.isDirectory) {
                errorMsg = "Path is not a folder"
            } else {
                errorMsg = ""
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E233C), RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (isCompleted) Color(0xFF00E676).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    if (isCompleted) Color(0xFF00E676).copy(alpha = 0.1f) else AccentViolet.copy(alpha = 0.1f),
                    CircleShape
                )
                .align(Alignment.CenterVertically),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted && errorMsg.isEmpty()) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Done",
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    text = "$stepIndex",
                    color = AccentViolet,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Select Call Recording Folder",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = "Provide the absolute directory path where your MIUI recorder saves call audios.",
                fontSize = 12.sp,
                color = Color.LightGray,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )

            OutlinedTextField(
                value = pathText,
                onValueChange = {
                    pathText = it
                    errorMsg = ""
                },
                label = { Text("Absolute Folder Path", color = Color.Gray, fontSize = 12.sp) },
                singleLine = true,
                isError = errorMsg.isNotEmpty(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentTeal,
                    unfocusedBorderColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF13172E),
                    unfocusedContainerColor = Color(0xFF13172E)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMsg.isNotEmpty()) {
                Text(
                    text = errorMsg,
                    color = Color.Red,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Presets for quick selection
            Text(
                text = "Common MIUI Paths:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AccentTeal
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        pathText = "/storage/emulated/0/MIUI/sound_recorder/call_rec"
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .background(Color(0xFF2C3258), RoundedCornerShape(8.dp))
                        .height(30.dp)
                ) {
                    Text("MIUI Call Rec", fontSize = 10.sp)
                }

                TextButton(
                    onClick = {
                        pathText = "/storage/emulated/0/sound_recorder/call_rec"
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .background(Color(0xFF2C3258), RoundedCornerShape(8.dp))
                        .height(30.dp)
                ) {
                    Text("Alternative", fontSize = 10.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val file = File(pathText.trim())
                    if (!file.exists()) {
                        errorMsg = "Path does not exist on this device"
                    } else if (!file.isDirectory) {
                        errorMsg = "Path is a file, must be a directory"
                    } else {
                        errorMsg = ""
                        onFolderSaved(file.absolutePath)
                        Toast.makeText(context, "Folder Configured Successfully!", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Verify & Save", color = Color(0xFF0A0D18), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DashboardContent(
    context: Context,
    completedCount: Int,
    pendingCount: Int,
    skippedCount: Int,
    isServiceRunning: Boolean,
    configuredWatchDir: String,
    onToggleService: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    // Elegant Glowing status badge
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassBorderColor, RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = GlassCardColor),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Watcher Service Status
            Row(
                modifier = Modifier
                    .background(Color(0xFF1E233C), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .alpha(if (isServiceRunning) alphaAnim else 1.0f)
                        .background(
                            if (isServiceRunning) ActiveGreen else StoppedRed,
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isServiceRunning) "Folder Watcher: Running" else "Folder Watcher: Inactive",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Service status action button
            Button(
                onClick = onToggleService,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isServiceRunning) StoppedRed else AccentViolet
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(
                    imageVector = if (isServiceRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isServiceRunning) "Stop Transcribing Service" else "Start Transcribing Service",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Monitoring: $configuredWatchDir",
                fontSize = 11.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Statistics Grid Title
    Text(
        text = "Transcription Statistics",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        textAlign = TextAlign.Start
    )

    // Stats Grid
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            title = "Completed",
            count = completedCount,
            accentColor = ActiveGreen,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Pending",
            count = pendingCount,
            accentColor = PausedOrange,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Skipped",
            count = skippedCount,
            accentColor = StoppedRed,
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Navigation button to History
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToHistory() }
            .border(1.dp, GlassBorderColor.copy(alpha = 0.8f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161A2E)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(AccentTeal.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = AccentTeal,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "View Transcripts Database",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Browse transcribed calls, summaries, and exact speech text logs.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.LightGray
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Share database card
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { shareDatabase(context) }
            .border(1.dp, GlassBorderColor.copy(alpha = 0.8f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161A2E)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(AccentViolet.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = AccentViolet,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Export Database (.db)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Share SQLite database to WhatsApp, Telegram, or Email.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.LightGray
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    count: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .border(1.dp, GlassBorderColor, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = GlassCardColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$count",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
        }
    }
}

fun shareDatabase(context: Context) {
    try {
        val dbFile = context.getDatabasePath("call_transcriptions.db")
        if (!dbFile.exists()) {
            Toast.makeText(context, "Database does not exist yet", Toast.LENGTH_SHORT).show()
            return
        }

        // Force a checkpoint of the database to make sure the WAL file is fully flushed into the main .db file
        try {
            val db = AppDatabase.getInstance(context)
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)")
        } catch (e: Exception) {
            android.util.Log.e("SilentScribe", "Failed to force WAL checkpoint", e)
        }

        // Copy database to a temporary file in the cache directory to prevent file locking/sharing issues
        val cacheFile = File(context.cacheDir, "SilentScribe_Database.db")
        FileInputStream(dbFile).use { input ->
            FileOutputStream(cacheFile).use { output ->
                input.copyTo(output)
            }
        }

        // Share the copy using FileProvider
        val uri = FileProvider.getUriForFile(
            context,
            "com.example.mobileaudiowhatsapp.fileprovider",
            cacheFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/x-sqlite3"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Export Transcripts Database")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        android.util.Log.e("SilentScribe", "Failed to share database", e)
    }
}
