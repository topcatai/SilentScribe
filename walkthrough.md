# Walkthrough: Local Call Transcription & Summarization App

This walkthrough summarizes the implementation details, verification steps, and results achieved in executing the local call transcription and summarization app blueprint.

## Changes Made

1. **Gradle Build Configuration**:
   - Upgraded compile and target SDK to version 35/36.
   - Integrated dependency version catalog targeting Room `2.8.4` (using KSP compiler), Vosk Android `0.3.47` for offline ASR, and Jetpack Navigation `2.8.5` (stable).
   - Removed SFTP backup libraries and database encryption constraints to meet Phase 1 requirements.

2. **Android Manifest & Permissions**:
   - Configured `MANAGE_EXTERNAL_STORAGE` permission for raw file system access on MIUI.
   - Configured Foreground Service with `dataSync` type for background processing.
   - Configured Boot completed receiver for automatic watcher recovery.
   - Added network security configuration (`network_security_config.xml`) that disables all cleartext traffic to guarantee complete offline data isolation.
   - Excluded Room DB files from device and cloud backups via XML resource configs.
   - Hardened security by explicitly removing the `INTERNET` permission (`tools:node="remove"`) from the manifest to prevent network transmission.

3. **Data Layer**:
   - Created the [CallLog](file:///c:/aiproject/Goose/Mobile_Audio_Whatsapp/data/CallLog.kt) entity supporting status states: `PENDING`, `PROCESSING`, `COMPLETED`, `SKIPPED_TOO_SHORT`.
   - Coded the Room [CallLogDao](file:///c:/aiproject/Goose/Mobile_Audio_Whatsapp/data/CallLogDao.kt) to handle full log query and partial phone number searches.
   - Implemented [FilenameParser](file:///c:/aiproject/Goose/Mobile_Audio_Whatsapp/app/src/main/java/com/example/mobileaudiowhatsapp/util/FilenameParser.kt) supporting MIUI primary filename formats and fallback rules (name prefixes and raw number values).

4. **Background Service & Engine**:
   - Created [CallFolderWatcherService](file:///c:/aiproject/Goose/Mobile_Audio_Whatsapp/app/src/main/java/com/example/mobileaudiowhatsapp/service/CallFolderWatcherService.kt) to monitor the target directory with a non-deprecated `FileObserver` constructor.
   - Integrated screen ON/OFF receiver: background queues suspend immediately on screen unlock and resume processing on screen lock to save device resources.
   - Added an automatic 2-second file-size stability debounce step before transcription start.
   - Fully implemented the [SpeechToTextManager](file:///c:/aiproject/Goose/Mobile_Audio_Whatsapp/app/src/main/java/com/example/mobileaudiowhatsapp/ml/SpeechToTextManager.kt) decoder using a native `MediaCodec` and `MediaExtractor` pipeline supporting mono downmixing, 16kHz resampling, and producing Vosk-compatible WAV file output.
   - Implemented a Try-Finally cleanup block in [SpeechToTextManager](file:///c:/aiproject/Goose/Mobile_Audio_Whatsapp/app/src/main/java/com/example/mobileaudiowhatsapp/ml/SpeechToTextManager.kt) to delete temporary decoded WAV files immediately after processing.
   - Implemented an orphaned temp WAV file purge utility in [MainActivity](file:///c:/aiproject/Goose/Mobile_Audio_Whatsapp/app/src/main/java/com/example/mobileaudiowhatsapp/MainActivity.kt) on application launch to clean up from any previous ungraceful shutdowns.
   - Created [SummarisationManager](file:///c:/aiproject/Goose/Mobile_Audio_Whatsapp/app/src/main/java/com/example/mobileaudiowhatsapp/ml/SummarisationManager.kt) to extract sentiment, key highlights, and action items using a rule-based parsing engine.

5. **UI & Navigation Layer**:
   - Implemented type-safe navigation routes using Kotlin Serialization: `Dashboard`, `History`, `CallDetails(id)`, and `Settings`.
   - Developed `DashboardScreen` containing the setup wizard (All Files Access, battery optimization white-listing, MIUI autostart guide, folder selector) and live counters.
   - Created `HistoryScreen` displaying grouped lists of phone numbers and names under date headers, including a real-time partial phone number search box.
   - Developed `CallDetailsScreen` showing dual tabs (Exact Transcript | Summary) and clipboard sharing.
   - Built a custom `SettingsScreen` to alter watch folders and specify custom Vosk model folders.

---

## What Was Tested & Validation Results

### 1. Build Verification
We verified the complete compilation and code correctness via Gradle tasks:
- **Clean Compile & Assemble**: Executed `./gradlew assembleDebug` successfully.
- **Android Lint checks**: Executed `./gradlew lintDebug` successfully with 0 errors and non-critical warnings.

### 2. Automated Unit Tests
Created and ran unit tests under `app/src/test/java/com/example/mobileaudiowhatsapp/ui/main/`:
- **[FilenameParserTest](file:///c:/aiproject/Goose/Mobile_Audio_Whatsapp/app/src/test/java/com/example/mobileaudiowhatsapp/ui/main/MainScreenViewModelTest.kt)**: Verified primary format mapping (`0091-6361265991(...)_...mp3`), prefix extraction (`Papa_...mp3`), number-only extraction (`+91...mp3`), and normalization (e.g. dropping 91 prefixes to domestic 10 digits).
- **[SummarisationManagerTest](file:///c:/aiproject/Goose/Mobile_Audio_Whatsapp/app/src/test/java/com/example/mobileaudiowhatsapp/ui/main/SummarisationManagerTest.kt)**: Validated rule-based keypoint extraction, sentiment classifier (Positive, Negative, Neutral), action-item triggers, and call duration formatting.

Command execution output:
```bash
> Task :app:testDebugUnitTest
> Task :app:test
BUILD SUCCESSFUL in 28s
```
All unit tests passed.

---

## Device & File Context

The app is built and verified to support MIUI/Xiaomi HyperOS call storage formats.

### Target Platform Context
The app targets Xiaomi HyperOS (Android API 33+):

![Xiaomi HyperOS Target Device](images/media__1779428581522.png)

### Call Recorder Files Layout
The filename formats parsed and supported by the app correspond directly to MIUI call recorder directory structure:

![MIUI Call Recorder Files Directory](images/media__1779432161359.png)
