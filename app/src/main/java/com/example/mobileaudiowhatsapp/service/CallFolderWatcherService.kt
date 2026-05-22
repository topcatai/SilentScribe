package com.example.mobileaudiowhatsapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.mobileaudiowhatsapp.MainActivity
import com.example.mobileaudiowhatsapp.data.AppDatabase
import com.example.mobileaudiowhatsapp.data.CallLog
import com.example.mobileaudiowhatsapp.ml.SpeechToTextManager
import com.example.mobileaudiowhatsapp.ml.SummarisationManager
import com.example.mobileaudiowhatsapp.util.FilenameParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class CallFolderWatcherService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fileObserver: FileObserver? = null
    private var olderFileJob: Job? = null
    private val transcriptionMutex = Mutex()

    private var isScreenOn = true

    companion object {
        private const val CHANNEL_ID = "call_transcription_watcher_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "WatcherService"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val dir = prefs.getString("watch_dir", null)

        if (dir.isNullOrBlank()) {
            // Service started but no watch directory configured yet.
            // Run in foreground with basic message to keep it alive.
            startForegroundServiceCompat("No watch folder configured")
            return START_STICKY
        }

        startForegroundServiceCompat("Watching: $dir")
        
        initialScan(dir)
        startFileObserver(dir)
        registerScreenReceiver()

        // Check current screen state on start
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        isScreenOn = pm.isInteractive
        if (!isScreenOn) {
            triggerOlderFilesProcessing()
        }

        return START_STICKY
    }

    private fun startForegroundServiceCompat(message: String) {
        val notification = buildNotification(message)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fileObserver?.stopWatching()
        runCatching { unregisterReceiver(screenReceiver) }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Initial Scan ─────────────────────────────────────────────────────────
    private fun initialScan(dir: String) = serviceScope.launch {
        val database = AppDatabase.getInstance(this@CallFolderWatcherService)
        val dao = database.callLogDao()
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isFirstLaunch = !prefs.getBoolean("first_launch_done", false)

        val folder = File(dir)
        if (!folder.exists() || !folder.isDirectory) return@launch

        folder.listFiles()?.forEach { file ->
            if (!file.isAudioFile()) return@forEach
            val meta = FilenameParser.parse(file.name) ?: return@forEach
            dao.insert(
                CallLog(
                    filename = file.name,
                    phoneNumber = meta.phoneNumber,
                    displayName = meta.displayName,
                    timestampMs = meta.timestampMs,
                    durationSeconds = 0, // Resolved when processed
                    status = "PENDING",
                    isNew = 0 // Pre-existing calls are marked isNew = 0
                )
            )
        }

        if (isFirstLaunch) {
            prefs.edit().putBoolean("first_launch_done", true).apply()
        }
    }

    // ── FileObserver ─────────────────────────────────────────────────────────
    private fun startFileObserver(dir: String) {
        fileObserver?.stopWatching()

        val mask = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO
        fileObserver = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(File(dir), mask) {
                override fun onEvent(event: Int, path: String?) {
                    if (path != null) {
                        serviceScope.launch { onNewFileDetected(File(dir, path)) }
                    }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(dir, mask) {
                override fun onEvent(event: Int, path: String?) {
                    if (path != null) {
                        serviceScope.launch { onNewFileDetected(File(dir, path)) }
                    }
                }
            }
        }
        fileObserver?.startWatching()
    }

    // ── New File Detected ────────────────────────────────────────────────────
    private suspend fun onNewFileDetected(file: File) {
        if (!file.isAudioFile()) return
        val meta = FilenameParser.parse(file.name) ?: return
        val dao = AppDatabase.getInstance(this).callLogDao()

        // Insert as PENDING, marked as isNew = 1 since it arrived during runtime
        val rowId = dao.insert(
            CallLog(
                filename = file.name,
                phoneNumber = meta.phoneNumber,
                displayName = meta.displayName,
                timestampMs = meta.timestampMs,
                durationSeconds = 0,
                status = "PENDING",
                isNew = 1
            )
        )
        if (rowId == -1L) return // Duplicate file

        if (!awaitFileStable(file)) {
            Log.e(TAG, "File was not stable: ${file.name}")
            return
        }

        val duration = file.audioDurationSeconds()
        val finalLog = dao.getById(rowId.toInt()) ?: return

        if (duration < 5) {
            dao.update(finalLog.copy(status = "SKIPPED_TOO_SHORT", durationSeconds = duration))
            return
        }

        dao.update(finalLog.copy(durationSeconds = duration))
        
        // Process new files immediately (regardless of screen state)
        transcribeAndSummarise(file, rowId.toInt())
    }

    // ── File Stability Debounce ──────────────────────────────────────────────
    private suspend fun awaitFileStable(
        file: File,
        intervalMs: Long = 2000,
        maxAttempts: Int = 10
    ): Boolean {
        var lastSize = -1L
        repeat(maxAttempts) {
            if (!file.exists()) return false
            val currentSize = file.length()
            if (currentSize > 0 && currentSize == lastSize) {
                return true
            }
            lastSize = currentSize
            delay(intervalMs)
        }
        return false
    }

    // ── Screen State Broadcast Receiver ──────────────────────────────────────
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    triggerOlderFilesProcessing()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    olderFileJob?.cancel()
                    updateNotification("Transcription paused — resumes when screen locks")
                }
            }
        }
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun triggerOlderFilesProcessing() {
        olderFileJob?.cancel()
        olderFileJob = serviceScope.launch {
            processNextOlderFile()
        }
    }

    private suspend fun processNextOlderFile() {
        // Double-check screen state before picking up a file
        if (isScreenOn) return

        val dao = AppDatabase.getInstance(this).callLogDao()
        val next = dao.nextPendingOlder() ?: run {
            updateNotification("All calls processed")
            return
        }

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val dir = prefs.getString("watch_dir", null) ?: return
        val file = File(dir, next.filename)

        if (!file.exists()) {
            dao.update(next.copy(status = "FAILED"))
            processNextOlderFile()
            return
        }

        if (!awaitFileStable(file)) {
            processNextOlderFile()
            return
        }

        val duration = file.audioDurationSeconds()
        if (duration < 5) {
            dao.update(next.copy(status = "SKIPPED_TOO_SHORT", durationSeconds = duration))
            processNextOlderFile() // Recurse to next
            return
        }

        dao.update(next.copy(durationSeconds = duration))
        
        try {
            transcribeAndSummarise(file, next.id)
            // Proceed recursively on success
            processNextOlderFile()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error in processNextOlderFile loop", e)
            processNextOlderFile()
        }
    }

    // ── Transcription & Summarisation ────────────────────────────────────────
    private suspend fun transcribeAndSummarise(file: File, logId: Int) {
        transcriptionMutex.withLock {
            val dao = AppDatabase.getInstance(this@CallFolderWatcherService).callLogDao()
            val log = dao.getById(logId) ?: return

            if (log.status == "SKIPPED_TOO_SHORT" || log.status == "COMPLETED") return

            dao.update(log.copy(status = "PROCESSING"))
            updateNotification("Transcribing call from ${log.phoneNumber}...")

            try {
                val stt = SpeechToTextManager(this@CallFolderWatcherService)
                val transcript = stt.transcribe(file)
                val summary = SummarisationManager.summarise(
                    transcript,
                    log.phoneNumber,
                    log.durationSeconds
                )

                dao.update(
                    log.copy(
                        exactTranscript = transcript,
                        callerSummary = summary,
                        status = "COMPLETED"
                    )
                )
                updateNotification("Transcription completed for ${log.phoneNumber}")
            } catch (e: CancellationException) {
                // Reset to PENDING so it can be retried next time the screen turns off
                dao.update(log.copy(status = "PENDING"))
                updateNotification("Transcription paused — resumes when screen locks")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "ASR failed for: ${file.name}", e)
                dao.update(log.copy(status = "FAILED"))
                updateNotification("ASR failed for ${log.phoneNumber}")
            }
        }
    }

    // ── Notification Channel Setup ───────────────────────────────────────────
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SilentScribe Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors call folder and transcribes recordings offline under SilentScribe"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SilentScribe Watcher")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }
}

// ── Extension Functions ──────────────────────────────────────────────────────
private fun File.isAudioFile(): Boolean {
    return isFile && extension.lowercase() in setOf("mp3", "m4a", "wav", "aac", "ogg")
}

private fun File.audioDurationSeconds(): Int {
    val mmr = MediaMetadataRetriever()
    return runCatching {
        mmr.setDataSource(absolutePath)
        val durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val ms = durationStr?.toLong() ?: 0L
        (ms / 1000).toInt()
    }.getOrDefault(0).also {
        runCatching { mmr.release() }
    }
}
