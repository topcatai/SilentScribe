# Local Call Transcription & Summarization App
## Master Blueprint v4.0 — Final

> **Distribution:** Sideload only (APK). No Play Store constraints apply.
> **Target device:** MIUI, Android API 33+.
> **Connectivity:** Zero network access. Fully air-gapped after install.
> **Backup:** Removed from scope. Revisit in a future phase.

---

## 0. All Decisions Made

Every open question from prior reviews is resolved here. Nothing is left ambiguous.

| Decision | Choice | Rationale |
|---|---|---|
| Storage permission | `MANAGE_EXTERNAL_STORAGE` | Sideload only — no Play Store review risk |
| Directory monitoring | `FileObserver(File(...))` non-deprecated constructor | Raw path access; ContentObserver not suitable for filesystem |
| Offline ASR engine | **Vosk** (`com.alphacephei:vosk-android:0.3.47`) | Truly offline, file-based transcription, ~50MB model, no Google Play Services required |
| ASR model | Vosk `vosk-model-small-en-in` (~36MB) bundled in `assets/` | Extracted to internal storage on first run; user can override with a larger model |
| Summarization | **Rule-based** (Phase 1) | Extracts action items, sentiment, key phrases from transcript — no LLM model required |
| MediaPipe GenAI | **Removed from Phase 1** | Gemma 2B is 1.4GB; impractical to require. Add in Phase 2 as optional enhancement |
| SFTP backup | **Removed from scope** | Revisit in a future phase |
| `EncryptedSharedPreferences` | **Removed** | Only needed for SFTP credentials — now irrelevant |
| Navigation library | **Jetpack Navigation 2.x (stable)** | Navigation 3 is alpha — not suitable for production |
| Database | **Plain Room (SQLite)** — no field-level encryption | Accepted risk, documented in §1 |
| Simulated ASR | **Rejected** | Fake transcripts displayed as real is unacceptable without disclosure |
| SFTP backup filename | **N/A** | SFTP removed |
| Manual backup trigger | **N/A** | SFTP removed |

---

## 1. Security Model

| Layer | Decision | Accepted Risk |
|---|---|---|
| Database at rest | Plain SQLite via Room | Readable on rooted device or via ADB backup. Android file-based encryption (default since API 29) provides physical-access protection. |
| Network | Zero outbound connections | Enforced by `network_security_config.xml` (blocks all cleartext) and absence of any network calls in code |
| Inter-app | No exported components, no implicit intents | App is invisible to other apps |
| Credentials | None stored | No SFTP, no accounts, no tokens |

> ⚠️ **Explicit acceptance:** The Room database at `/data/data/<package>/databases/call_transcriptions.db` contains plaintext transcripts. This is readable by root. This is a known, accepted tradeoff for this phase.

---

## 2. Dependencies

### `libs.versions.toml`

```toml
[versions]
kotlin                   = "2.0.21"
ksp                      = "2.0.21-1.0.28"
agp                      = "8.7.3"
coreKtx                  = "1.15.0"
lifecycleRuntime         = "2.8.7"
activityCompose          = "1.9.3"
composeBom               = "2024.12.01"
navigationCompose        = "2.8.5"
room                     = "2.7.1"
kotlinxSerializationJson = "1.7.3"
vosk                     = "0.3.47"
workManager              = "2.10.1"

[libraries]
androidx-core-ktx              = { module = "androidx.core:core-ktx",                          version.ref = "coreKtx" }
androidx-lifecycle-runtime     = { module = "androidx.lifecycle:lifecycle-runtime-ktx",         version.ref = "lifecycleRuntime" }
androidx-activity-compose      = { module = "androidx.activity:activity-compose",               version.ref = "activityCompose" }
androidx-compose-bom           = { module = "androidx.compose:compose-bom",                    version.ref = "composeBom" }
androidx-navigation-compose    = { module = "androidx.navigation:navigation-compose",           version.ref = "navigationCompose" }
room-runtime                   = { module = "androidx.room:room-runtime",                       version.ref = "room" }
room-ktx                       = { module = "androidx.room:room-ktx",                          version.ref = "room" }
room-compiler                  = { module = "androidx.room:room-compiler",                     version.ref = "room" }
kotlinx-serialization-json     = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerializationJson" }
vosk-android                   = { module = "com.alphacephei:vosk-android",                    version.ref = "vosk" }

[plugins]
android-application    = { id = "com.android.application",                    version.ref = "agp" }
kotlin-android         = { id = "org.jetbrains.kotlin.android",               version.ref = "kotlin" }
kotlin-compose         = { id = "org.jetbrains.kotlin.plugin.compose",        version.ref = "kotlin" }
kotlin-serialization   = { id = "org.jetbrains.kotlin.plugin.serialization",  version.ref = "kotlin" }
ksp                    = { id = "com.google.devtools.ksp",                     version.ref = "ksp" }
```

### `app/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    compileSdk = 35
    defaultConfig {
        minSdk = 31          // Android 12 minimum — covers all current MIUI devices
        targetSdk = 35
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.vosk.android)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
}
```

> **Note on Vosk model:** Download `vosk-model-small-en-in-0.4.zip` from alphacephei.com, unzip it, and place the extracted folder as `app/src/main/assets/vosk-model-small-en-in`. The model folder (~36MB) is bundled into the APK and extracted to internal storage on first launch. The user can override this by placing any Vosk-compatible model directory on device storage and selecting it from Settings.

---

## 3. Android Manifest

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- ── PERMISSIONS ── -->

    <!-- All-files access: sideload only — acceptable without Play Store review -->
    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />

    <!-- Foreground service — dataSync type requires no Play Store approval -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

    <!-- Notification permission (runtime request required on API 33+) -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- Restart watcher on device reboot -->
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <!-- MIUI battery whitelist -->
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

    <application
        android:networkSecurityConfig="@xml/network_security_config"
        android:fullBackupContent="@xml/backup_rules_legacy"
        android:dataExtractionRules="@xml/backup_rules_api31"
        android:allowBackup="false">

        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Foreground watcher — not exported, no implicit intents -->
        <service
            android:name=".service.CallFolderWatcherService"
            android:foregroundServiceType="dataSync"
            android:exported="false" />

        <!-- Boot receiver — restarts watcher after reboot -->
        <receiver
            android:name=".receiver.BootCompletedReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>

    </application>
</manifest>
```

---

## 4. Resource Files

### `res/xml/network_security_config.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

### `res/xml/backup_rules_legacy.xml` — API ≤ 30
```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="database" path="call_transcriptions.db" />
</full-backup-content>
```

### `res/xml/backup_rules_api31.xml` — API 31+
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="database" path="call_transcriptions.db" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="database" path="call_transcriptions.db" />
    </device-transfer>
</data-extraction-rules>
```

> Now that there are no SFTP credentials to protect, the backup exclusion targets the database itself — preventing it from appearing in Google Drive or device-transfer backups.

---

## 5. Data Layer

### `data/CallLog.kt`

```kotlin
@Entity(tableName = "call_logs", indices = [Index(value = ["filename"], unique = true)])
data class CallLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filename: String,
    val phoneNumber: String,      // Normalised: 10-digit, no prefix
    val displayName: String?,     // Parsed from filename prefix (e.g. "Papa"), else null
    val timestampMs: Long,        // Unix epoch ms parsed from filename
    val durationSeconds: Int,
    val exactTranscript: String?, // Plaintext. Null until status = COMPLETED
    val callerSummary: String?,   // Plaintext. Null until status = COMPLETED
    val status: String,           // PENDING | PROCESSING | COMPLETED | SKIPPED_TOO_SHORT
    val isNew: Int,               // 1 = arrived after first launch; 0 = pre-existing
    val syncStatus: Int = 0       // Reserved for Phase 2 CRM
)
```

### `data/CallLogDao.kt`

```kotlin
@Dao
interface CallLogDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: CallLog): Long  // Returns -1 if filename already exists (dedup)

    @Update
    suspend fun update(log: CallLog)

    // Full list, newest first — drives HistoryScreen
    @Query("SELECT * FROM call_logs ORDER BY timestamp_ms DESC")
    fun observeAll(): Flow<List<CallLog>>

    // Partial phone number search
    @Query("SELECT * FROM call_logs WHERE phone_number LIKE '%' || :q || '%' ORDER BY timestamp_ms DESC")
    fun search(q: String): Flow<List<CallLog>>

    // Next pending pre-existing file for idle (screen-off) processing
    @Query("SELECT * FROM call_logs WHERE status = 'PENDING' AND is_new = 0 ORDER BY timestamp_ms ASC LIMIT 1")
    suspend fun nextPendingOlder(): CallLog?

    // Single record for CallDetailsScreen
    @Query("SELECT * FROM call_logs WHERE id = :id")
    suspend fun getById(id: Int): CallLog?

    // Stats for Dashboard
    @Query("SELECT COUNT(*) FROM call_logs WHERE status = 'COMPLETED'")
    fun observeCompletedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM call_logs WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>
}
```

### `data/AppDatabase.kt`

```kotlin
@Database(entities = [CallLog::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun callLogDao(): CallLogDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "call_transcriptions.db")
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
```

---

## 6. Filename Parsing

### Supported Formats (Verified MIUI Output)

| Format | Example |
|---|---|
| Primary — saved contact | `0091-6361265991(00916361265991)_20260415122848.mp3` |
| Fallback — name prefix | `Papa_919876543210_20260522105122.mp3` |
| Fallback — number only | `+919876543210_20260522105122.mp3` |

### `util/FilenameParser.kt`

```kotlin
data class ParsedMetadata(
    val phoneNumber: String,   // Normalised 10-digit
    val displayName: String?,  // Contact name or formatted prefix, null if absent
    val timestampMs: Long
)

object FilenameParser {

    // Primary: anything(CleanNumber)_YYYYMMDDHHMMSS.ext
    private val PRIMARY = Regex(
        """^(.+)\(([^)]+)\)_(\d{12,14})\.(mp3|m4a|wav|aac|ogg)$""",
        RegexOption.IGNORE_CASE
    )

    // Fallback: optional_name_PhoneNumber_YYYYMMDDHHMMSS.ext
    private val FALLBACK = Regex(
        """^(?:(.+)_)?(\+?\d{10,13})_(\d{12,14})\.(mp3|m4a|wav|aac|ogg)$""",
        RegexOption.IGNORE_CASE
    )

    fun parse(filename: String): ParsedMetadata? {
        PRIMARY.matchEntire(filename)?.let { m ->
            val raw  = m.groups[2]?.value?.trim() ?: return null
            val ts   = m.groups[3]?.value         ?: return null
            return ParsedMetadata(normalise(raw), m.groups[1]?.value?.trim(), parseTs(ts))
        }
        FALLBACK.matchEntire(filename)?.let { m ->
            val raw  = m.groups[2]?.value ?: return null
            val ts   = m.groups[3]?.value ?: return null
            return ParsedMetadata(normalise(raw), m.groups[1]?.value?.trim(), parseTs(ts))
        }
        return null
    }

    // Strip 0091 / +91 / 91 prefix → 10-digit domestic number
    private fun normalise(raw: String): String {
        val d = raw.filter { it.isDigit() }
        return when {
            d.length == 12 && d.startsWith("91")   -> d.drop(2)
            d.length == 13 && d.startsWith("091")  -> d.drop(3)
            d.length == 14 && d.startsWith("0091") -> d.drop(4)
            else -> d
        }
    }

    // Handles 14-digit (YYYYMMDDHHMMSS) and 12-digit (YYYYMMDDHHMM → append "00")
    private fun parseTs(s: String): Long {
        val padded = if (s.length == 12) "${s}00" else s
        return runCatching {
            java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US).parse(padded)?.time ?: 0L
        }.getOrDefault(0L)
    }
}
```

---

## 7. Background Service

### `service/CallFolderWatcherService.kt` — structure

```kotlin
class CallFolderWatcherService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fileObserver: FileObserver? = null
    private var olderFileJob: Job? = null

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification("Watching for new calls…"))
        val dir = PreferenceManager.getDefaultSharedPreferences(this)
            .getString("watch_dir", null) ?: return START_NOT_STICKY

        initialScan(dir)
        startFileObserver(dir)
        registerScreenReceiver()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        fileObserver?.stopWatching()
        serviceScope.cancel()
        unregisterReceiver(screenReceiver)
    }

    override fun onBind(intent: Intent?) = null

    // ── Initial scan ─────────────────────────────────────────────────────────
    // On first-ever launch: files found → is_new = 0
    // On reboot/restart: UNIQUE constraint silently ignores already-seen files;
    // is_new values are preserved exactly as set at first insert.

    private fun initialScan(dir: String) = serviceScope.launch {
        val dao = AppDatabase.getInstance(this@CallFolderWatcherService).callLogDao()
        val isFirstLaunch = PreferenceManager.getDefaultSharedPreferences(this@CallFolderWatcherService)
            .getBoolean("first_launch_done", false).not()

        File(dir).listFiles()?.forEach { file ->
            if (!file.isAudioFile()) return@forEach
            val meta = FilenameParser.parse(file.name) ?: return@forEach
            dao.insert(CallLog(
                filename        = file.name,
                phoneNumber     = meta.phoneNumber,
                displayName     = meta.displayName,
                timestampMs     = meta.timestampMs,
                durationSeconds = 0,           // Will be resolved when processed
                status          = "PENDING",
                isNew           = if (isFirstLaunch) 0 else 0  // Pre-existing = always 0
            ))
        }
        if (isFirstLaunch) {
            PreferenceManager.getDefaultSharedPreferences(this@CallFolderWatcherService)
                .edit().putBoolean("first_launch_done", true).apply()
        }
    }

    // ── FileObserver ─────────────────────────────────────────────────────────
    // Non-deprecated File constructor (API 29+)
    // CLOSE_WRITE: file handle closed after write — safe to read
    // MOVED_TO: some recorders write to a temp file then move

    private fun startFileObserver(dir: String) {
        fileObserver = object : FileObserver(File(dir), CLOSE_WRITE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                serviceScope.launch {           // Always dispatch; never call suspend from onEvent directly
                    onNewFileDetected(File(dir, path))
                }
            }
        }.also { it.startWatching() }
    }

    // ── New file pipeline ────────────────────────────────────────────────────

    private suspend fun onNewFileDetected(file: File) {
        if (!file.isAudioFile()) return
        val meta = FilenameParser.parse(file.name) ?: return
        val dao  = AppDatabase.getInstance(this).callLogDao()

        val rowId = dao.insert(CallLog(
            filename = file.name, phoneNumber = meta.phoneNumber,
            displayName = meta.displayName, timestampMs = meta.timestampMs,
            durationSeconds = 0, status = "PENDING", isNew = 1
        ))
        if (rowId == -1L) return    // Already exists — skip

        if (!awaitFileStable(file)) return    // Still being written — will be picked up on next restart
        val duration = file.audioDurationSeconds()
        if (duration < 5) {
            dao.update(dao.getById(rowId.toInt())!!.copy(status = "SKIPPED_TOO_SHORT", durationSeconds = duration))
            return
        }
        dao.update(dao.getById(rowId.toInt())!!.copy(durationSeconds = duration))
        transcribeAndSummarise(file, rowId.toInt())    // New files: process regardless of screen state
    }

    // ── File stability debounce ───────────────────────────────────────────────

    private suspend fun awaitFileStable(
        file: File,
        intervalMs: Long = 2000,
        maxAttempts: Int = 10
    ): Boolean {
        var last = -1L
        repeat(maxAttempts) {
            val size = file.length()
            if (size > 0 && size == last) return true
            last = size
            delay(intervalMs)
        }
        return false
    }

    // ── Screen state ─────────────────────────────────────────────────────────

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> serviceScope.launch { processNextOlderFile() }
                Intent.ACTION_SCREEN_ON  -> {
                    olderFileJob?.cancel()
                    updateNotification("Transcription paused — resumes when screen locks")
                }
            }
        }
    }

    private fun registerScreenReceiver() {
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        })
    }

    private suspend fun processNextOlderFile() {
        val dao  = AppDatabase.getInstance(this).callLogDao()
        val next = dao.nextPendingOlder() ?: run {
            updateNotification("All calls processed")
            return
        }
        val file = File(/* watchDir */ "", next.filename)
        if (!file.exists() || !awaitFileStable(file)) return

        val duration = file.audioDurationSeconds()
        if (duration < 5) {
            dao.update(next.copy(status = "SKIPPED_TOO_SHORT", durationSeconds = duration))
            processNextOlderFile()   // Recurse to next
            return
        }
        dao.update(next.copy(durationSeconds = duration))
        olderFileJob = serviceScope.launch { transcribeAndSummarise(file, next.id) }
    }

    // ── Transcription & summarisation ────────────────────────────────────────
    // Injected via SpeechToTextManager and SummarisationManager (see §8 and §9)

    private suspend fun transcribeAndSummarise(file: File, logId: Int) { /* see §8 */ }
}

// ── Extensions ───────────────────────────────────────────────────────────────

private fun File.isAudioFile() =
    isFile && extension.lowercase() in setOf("mp3", "m4a", "wav", "aac", "ogg")

private fun File.audioDurationSeconds(): Int {
    val mmr = MediaMetadataRetriever()
    return runCatching {
        mmr.setDataSource(absolutePath)
        val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        (ms / 1000).toInt()
    }.getOrDefault(0).also { mmr.release() }
}
```

### `receiver/BootCompletedReceiver.kt`

```kotlin
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startForegroundService(
                Intent(context, CallFolderWatcherService::class.java)
            )
        }
    }
}
```

---

## 8. Offline Speech-to-Text (Vosk)

Vosk processes audio files directly — no microphone, no Google Play Services, no network.

### Model Bootstrap (run once on first launch)

```kotlin
object VoskModelManager {
    fun ensureModel(context: Context): File {
        val modelDir = File(context.filesDir, "vosk-model")
        if (modelDir.exists() && modelDir.list()?.isNotEmpty() == true) return modelDir

        // Extract bundled model from assets on first launch
        context.assets.list("vosk-model-small-en-in")?.forEach { name ->
            val target = File(modelDir, name)
            target.parentFile?.mkdirs()
            context.assets.open("vosk-model-small-en-in/$name").use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
        }
        return modelDir
    }

    // Optional: user can select a larger model from device storage in Settings
    fun customModelPath(context: Context): String? =
        PreferenceManager.getDefaultSharedPreferences(context).getString("custom_model_path", null)
}
```

### `ml/SpeechToTextManager.kt`

```kotlin
class SpeechToTextManager(private val context: Context) {

    suspend fun transcribe(audioFile: File): String = withContext(Dispatchers.Default) {
        val modelPath = VoskModelManager.customModelPath(context)
            ?: VoskModelManager.ensureModel(context).absolutePath

        val model      = Model(modelPath)
        val recogniser = Recogniser(model, 16000.0f)

        // Vosk requires 16kHz mono PCM — convert via MediaCodec decoder
        val pcmFile = decodeToPcm(audioFile)

        val result = StringBuilder()
        pcmFile.inputStream().use { stream ->
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } >= 0) {
                if (recogniser.acceptWaveForm(buffer, bytesRead)) {
                    val partial = JSONObject(recogniser.result).optString("text")
                    if (partial.isNotBlank()) result.append(partial).append(" ")
                }
            }
            val final = JSONObject(recogniser.finalResult).optString("text")
            if (final.isNotBlank()) result.append(final)
        }

        recogniser.close()
        model.close()
        pcmFile.delete()
        result.toString().trim()
    }

    // Decode any audio format → 16kHz mono PCM WAV using Android MediaCodec
    private suspend fun decodeToPcm(input: File): File = withContext(Dispatchers.IO) {
        val output = File(input.parent, "${input.nameWithoutExtension}_pcm.wav")
        // Use MediaExtractor + MediaCodec to decode to raw PCM, then write a WAV header
        // Full implementation: standard Android MediaCodec decode pipeline
        output
    }
}
```

> **Model note:** `vosk-model-small-en-in` is the Indian English model. It handles Indian accents and phone number diction well. The user can replace it with `vosk-model-en-us-0.22` (~1.8GB) for higher accuracy at the cost of storage. The Settings screen allows pointing to a custom model directory.

---

## 9. Summarisation (Rule-Based, Phase 1)

No LLM required. Extracts structured insights directly from the transcript text.

### `ml/SummarisationManager.kt`

```kotlin
object SummarisationManager {

    fun summarise(transcript: String, phoneNumber: String, durationSeconds: Int): String {
        val lines    = transcript.trim().split(". ", "\n").filter { it.isNotBlank() }
        val wordCount = transcript.split(" ").size

        val keyPoints  = extractKeyPoints(transcript)
        val sentiment  = detectSentiment(transcript)
        val actionItems = extractActionItems(transcript)
        val duration   = formatDuration(durationSeconds)

        return buildString {
            appendLine("📞 Call with $phoneNumber — $duration")
            appendLine("📊 Sentiment: $sentiment  |  ~$wordCount words transcribed")
            appendLine()
            if (keyPoints.isNotEmpty()) {
                appendLine("Key Points:")
                keyPoints.forEach { appendLine("  • $it") }
            }
            if (actionItems.isNotEmpty()) {
                appendLine()
                appendLine("Action Items:")
                actionItems.forEach { appendLine("  → $it") }
            }
        }.trim()
    }

    private fun extractKeyPoints(text: String): List<String> {
        // Extract sentences containing keywords: amounts, dates, locations, names, agreements
        val keywords = listOf("will", "would", "agreed", "confirmed", "meet", "send", "call back",
            "payment", "amount", "rupees", "tomorrow", "next week", "address", "problem", "issue")
        return text.split(". ")
            .filter { sentence -> keywords.any { kw -> sentence.lowercase().contains(kw) } }
            .take(5)
    }

    private fun extractActionItems(text: String): List<String> {
        val actionPhrases = listOf("will send", "will call", "will come", "need to", "have to",
            "please", "don't forget", "remind", "by tomorrow", "by next week")
        return text.split(". ")
            .filter { s -> actionPhrases.any { p -> s.lowercase().contains(p) } }
            .take(3)
    }

    private fun detectSentiment(text: String): String {
        val positive = listOf("thank", "great", "good", "happy", "okay", "fine", "agree", "sure")
        val negative = listOf("problem", "issue", "wrong", "angry", "upset", "not", "never", "refuse")
        val t = text.lowercase()
        val pos = positive.count { t.contains(it) }
        val neg = negative.count { t.contains(it) }
        return when {
            pos > neg + 2 -> "Positive"
            neg > pos + 2 -> "Negative / Tense"
            else          -> "Neutral"
        }
    }

    private fun formatDuration(s: Int): String {
        val m = s / 60; val sec = s % 60
        return if (m > 0) "${m}m ${sec}s" else "${sec}s"
    }
}
```

---

## 10. MIUI Setup Requirements

These steps are mandatory for the app to work reliably on MIUI. The setup wizard must walk through all of them in order.

### Step 1 — All Files Access (`MANAGE_EXTERNAL_STORAGE`)

```kotlin
fun requestAllFilesAccess(activity: Activity) {
    if (!Environment.isExternalStorageManager()) {
        activity.startActivity(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
        )
    }
}
```

### Step 2 — Battery Optimisation Whitelist

```kotlin
fun requestBatteryExemption(activity: Activity) {
    val pm = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (!pm.isIgnoringBatteryOptimizations(activity.packageName)) {
        activity.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
        )
    }
}
```

### Step 3 — MIUI Autostart (Manual — Cannot Be Automated)

Display a persistent guidance card on the Dashboard until the user dismisses it:

```
To keep transcription running reliably on MIUI:

1. Settings → Apps → Manage Apps → [App Name] → Autostart → Enable
2. Settings → Battery & Performance → App Battery Saver → [App Name] → No Restrictions

These steps cannot be done automatically. The app may be killed without them.
```

Persist a `SharedPreferences` flag `"miui_guidance_dismissed"` when the user taps "Done" to hide the card.

### Step 4 — Notification Permission (API 33+)

```kotlin
if (Build.VERSION.SDK_INT >= 33) {
    ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
        RC_NOTIFICATIONS
    )
}
```

---

## 11. UI Screens (Jetpack Compose + Navigation 2.x Stable)

### Navigation Setup

```kotlin
// NavigationKeys.kt — type-safe routes
@Serializable object Dashboard
@Serializable object History
@Serializable data class CallDetails(val id: Int)
@Serializable object Settings

// NavGraph.kt
@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(navController, startDestination = Dashboard) {
        composable<Dashboard>    { DashboardScreen(navController) }
        composable<History>      { HistoryScreen(navController, viewModel = historyViewModel(navController)) }
        composable<CallDetails>  { CallDetailsScreen(it.toRoute<CallDetails>().id) }
        composable<Settings>     { SettingsScreen() }
    }
}

// HistoryViewModel scoped to nav back stack entry so search state survives
// navigation to CallDetailsScreen and back
@Composable
fun historyViewModel(navController: NavHostController): HistoryViewModel {
    val entry = remember(navController) { navController.getBackStackEntry<History>() }
    return viewModel(entry)
}
```

### Screen Responsibilities

**`DashboardScreen`**
- Setup wizard (shown until all steps complete): All Files Access → Battery exemption → MIUI autostart card → folder picker
- Live counters: Completed / Pending / Skipped
- Watcher status badge: Active / Paused (screen on) / Stopped
- Transcription progress card (current file being processed)

**`HistoryScreen`**
- Single-column list of phone numbers + display names, grouped under date headers (e.g. "May 22, 2026")
- Search bar at top — partial match on `phoneNumber`; query stored in `HistoryViewModel`, survives back navigation
- Tap → `CallDetailsScreen`

**`HistoryViewModel`**

```kotlin
class HistoryViewModel(private val dao: CallLogDao) : ViewModel() {
    var query by mutableStateOf("")
        private set

    fun onQueryChange(q: String) { query = q }

    val logs: StateFlow<List<CallLog>> = snapshotFlow { query }
        .debounce(150)
        .flatMapLatest { q -> if (q.isBlank()) dao.observeAll() else dao.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
```

**`CallDetailsScreen`**
- Header: phone number / display name, date, duration, status badge
- Two tabs: **Transcript** | **Summary**
- Share button: copies active tab content to clipboard
- Back button returns to History with search query intact

**`SettingsScreen`**
- Watched folder path (re-pick via directory picker)
- Custom Vosk model path (optional — points to a user-downloaded model folder on device storage)
- MIUI autostart guidance card (always visible as a reminder)
- App version / database record count

---

## 12. File Tree

```
app/src/main/
├── assets/
│   └── vosk-model-small-en-in/     ← unzip vosk-model-small-en-in-0.4.zip here
│       ├── am/
│       ├── conf/
│       ├── graph/
│       └── ivector/
├── res/xml/
│   ├── network_security_config.xml
│   ├── backup_rules_legacy.xml
│   └── backup_rules_api31.xml
└── java/com/example/calltranscriber/
    ├── MainActivity.kt
    ├── data/
    │   ├── CallLog.kt
    │   ├── CallLogDao.kt
    │   └── AppDatabase.kt
    ├── ml/
    │   ├── SpeechToTextManager.kt
    │   ├── SummarisationManager.kt
    │   └── VoskModelManager.kt
    ├── service/
    │   └── CallFolderWatcherService.kt
    ├── receiver/
    │   └── BootCompletedReceiver.kt
    ├── util/
    │   └── FilenameParser.kt
    └── ui/
        ├── NavGraph.kt
        ├── NavigationKeys.kt
        ├── DashboardScreen.kt
        ├── HistoryScreen.kt
        ├── CallDetailsScreen.kt
        ├── SettingsScreen.kt
        └── HistoryViewModel.kt
```

---

## 13. Verification Checklist

### Build
```bash
./gradlew assembleDebug   # Zero errors
./gradlew lint            # Zero warnings on permissions / deprecated APIs
```

### Permissions
- [ ] App launches → setup wizard shows All Files Access prompt → redirects to system settings
- [ ] Battery exemption prompt appears and app is whitelisted
- [ ] `POST_NOTIFICATIONS` runtime dialog shown on API 33+

### Filename Parsing
- [ ] `0091-6361265991(00916361265991)_20260415122848.mp3` → `phoneNumber=6361265991`, `ts=2026-04-15 12:28`
- [ ] `Papa_919876543210_20260522105122.mp3` → `phoneNumber=9876543210`, `displayName=Papa`
- [ ] `+919876543210_20260522105122.mp3` → `phoneNumber=9876543210`, `displayName=null`

### Watcher
- [ ] Add audio file → debounce holds → stability confirmed → duration checked → transcription starts
- [ ] Add file < 5 seconds → `status=SKIPPED_TOO_SHORT`
- [ ] Lock screen → older `PENDING` file begins processing
- [ ] Unlock screen → older file processing pauses; notification updates
- [ ] New file added while screen ON → processes immediately without waiting for screen lock
- [ ] Reboot → service restarts → `is_new` values unchanged → no duplicate rows

### UI
- [ ] History screen groups calls under correct date headers
- [ ] Type `636` → only `6361265991` entry visible
- [ ] Tap entry → tap Back → `636` still in search bar
- [ ] Transcript and Summary tabs both display content on CallDetailsScreen
- [ ] Share button copies active tab text to clipboard

### Vosk ASR
- [ ] On first launch, model extracted from assets to `filesDir/vosk-model`
- [ ] Audio file transcribed to readable English text
- [ ] Custom model path in Settings overrides bundled model

---

## 14. Known Limitations & Phase 2 Notes

| Item | Note |
|---|---|
| `MANAGE_EXTERNAL_STORAGE` | Sideload only. Requires Play Store declaration review if ever published |
| Plain SQLite | Readable on rooted device. Accepted. |
| Vosk accuracy | Small English-Indian model is good for common phrases; upgrade to `vosk-model-en-us-0.22` for higher accuracy |
| MediaCodec PCM decoder | Full implementation of `decodeToPcm()` in `SpeechToTextManager` is required — standard Android MediaCodec pipeline |
| SFTP backup | Removed. Revisit as Phase 2 feature |
| LLM summarisation | MediaPipe Gemma integration deferred to Phase 2; rule-based summariser ships in Phase 1 |
| Room migrations | Schema is version 1; any column additions require a `Migration` — do not increment version without one |
