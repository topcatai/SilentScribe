package com.example.mobileaudiowhatsapp.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.mobileaudiowhatsapp.data.AppDatabase
import com.example.mobileaudiowhatsapp.service.CallFolderWatcherService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember { AppDatabase.getInstance(context) }
    val dao = remember { database.callLogDao() }

    val logs by dao.observeAll().collectAsState(initial = emptyList())
    val totalRecordsCount = logs.size

    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    
    var watchDirPath by remember { mutableStateOf(prefs.getString("watch_dir", "") ?: "") }
    var customModelPath by remember { mutableStateOf(prefs.getString("custom_model_path", "") ?: "") }

    var watchDirError by remember { mutableStateOf("") }
    var customModelError by remember { mutableStateOf("") }

    var showWipeConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "App Settings",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
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
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. Watched Directory Path
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GlassBorderColor, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = GlassCardColor),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Watched Directory",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Change the path to monitor call audio files.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                        )

                        OutlinedTextField(
                            value = watchDirPath,
                            onValueChange = {
                                watchDirPath = it
                                watchDirError = ""
                            },
                            label = { Text("Watch Directory Absolute Path", color = Color.Gray) },
                            singleLine = true,
                            isError = watchDirError.isNotEmpty(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentTeal,
                                unfocusedBorderColor = GlassBorderColor,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF13172E),
                                unfocusedContainerColor = Color(0xFF13172E)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (watchDirError.isNotEmpty()) {
                            Text(
                                text = watchDirError,
                                color = Color.Red,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                val file = File(watchDirPath.trim())
                                if (!file.exists()) {
                                    watchDirError = "Directory path does not exist"
                                } else if (!file.isDirectory) {
                                    watchDirError = "Path is a file, must be a folder"
                                } else {
                                    watchDirError = ""
                                    prefs.edit().putString("watch_dir", file.absolutePath).apply()
                                    Toast.makeText(context, "Watch Directory updated", Toast.LENGTH_SHORT).show()
                                    // Restart watcher service to apply new directory
                                    val serviceIntent = Intent(context, CallFolderWatcherService::class.java)
                                    context.stopService(serviceIntent)
                                    context.startForegroundService(serviceIntent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Update Watch Folder", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // 2. Custom ASR Model Path Settings
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GlassBorderColor, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = GlassCardColor),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Custom ASR Model Folder (Optional)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Override the default small model by specifying an absolute path to a larger unzipped Vosk model directory on your storage.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                        )

                        OutlinedTextField(
                            value = customModelPath,
                            onValueChange = {
                                customModelPath = it
                                customModelError = ""
                            },
                            label = { Text("Model Directory Absolute Path", color = Color.Gray) },
                            singleLine = true,
                            isError = customModelError.isNotEmpty(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentTeal,
                                unfocusedBorderColor = GlassBorderColor,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF13172E),
                                unfocusedContainerColor = Color(0xFF13172E)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (customModelError.isNotEmpty()) {
                            Text(
                                text = customModelError,
                                color = Color.Red,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (prefs.getString("custom_model_path", null) != null) {
                                TextButton(
                                    onClick = {
                                        customModelPath = ""
                                        customModelError = ""
                                        prefs.edit().remove("custom_model_path").apply()
                                        Toast.makeText(context, "Reverted to default bundled model", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = StoppedRed)
                                ) {
                                    Text("Reset Default", fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Button(
                                onClick = {
                                    if (customModelPath.trim().isEmpty()) {
                                        prefs.edit().remove("custom_model_path").apply()
                                        Toast.makeText(context, "Reverted to default model", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }

                                    val file = File(customModelPath.trim())
                                    if (!file.exists()) {
                                        customModelError = "Model path does not exist"
                                    } else if (!file.isDirectory) {
                                        customModelError = "Path is a file, must be a model folder"
                                    } else {
                                        customModelError = ""
                                        prefs.edit().putString("custom_model_path", file.absolutePath).apply()
                                        Toast.makeText(context, "Custom model configured", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentViolet),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Model Folder", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // 3. MIUI Guidance
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GlassBorderColor, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF281C10)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = PausedOrange)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "MIUI Autostart Settings Reminder",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        
                        Text(
                            text = "To guarantee the background service runs properly on MIUI devices:\n\n" +
                                    "1. Settings → Apps → Manage Apps → [App Name] → Autostart → Enable\n" +
                                    "2. Settings → Battery & Performance → App Battery Saver → [App Name] → No Restrictions",
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 10.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Open App Info Settings", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }

                // 4. Database Administration
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GlassBorderColor, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = GlassCardColor),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Database Management",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Manage call log transcript records stored on your device storage.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total Database Records: $totalRecordsCount",
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )

                            Button(
                                onClick = { showWipeConfirmDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = StoppedRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Clear Data", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Bottom spacer
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Wipe database confirmation dialog
            if (showWipeConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showWipeConfirmDialog = false },
                    title = { Text("Delete All Database Records?") },
                    text = { Text("This will permanently wipe all transcribed call text logs and summaries. Audio files on your storage will remain untouched.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showWipeConfirmDialog = false
                                coroutineScope.launch {
                                    withContext(Dispatchers.IO) {
                                        database.clearAllTables()
                                    }
                                    Toast.makeText(context, "Database cleared successfully", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = StoppedRed)
                        ) {
                            Text("Permanently Delete", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showWipeConfirmDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}
