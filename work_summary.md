# Work Summary: Offline MIUI/HyperOS Call Transcription & Summarization App

This document summarizes the development progress, architecture, and current state of the application.

## 1. Project Objective & Constraints
* **Fully Offline**: Zero network permissions (`networkSecurityConfig` blocks all cleartext and no network permission is defined in the manifest).
* **MIUI/HyperOS Call Record Support**: Scans and parses call recordings locally.
* **ASR Engine**: Offline speech recognition using **Vosk** with a bundled Indian English model (`vosk-model-small-en-in`).
* **Rule-Based Summarization**: Extracts key insights, sentiment, and action items locally without requiring external APIs or heavy LLM runtime dependencies.

## 2. Key Accomplishments & Implementation Details

### Gradle & Build Configuration
* Configured dependency version catalog using stable libraries (Room `2.7.1` / Room compiler via KSP, Vosk Android `0.3.47`, Jetpack Navigation `2.8.5`).
* Targets SDK 35/36 with `minSdk = 31` (covering all modern MIUI/HyperOS devices).
* Built debug APK successfully under: `app/build/outputs/apk/debug/app-debug.apk`.

### Security & Privacy
* Complete network isolation enforced through XML security configuration.
* SQLite database (`call_transcriptions.db` under internal sandbox storage) excluded from cloud and device-transfer backups.

### Data & Parse Layer
* **CallLog Database Model**: Tracks log metadata, phone number (domestic 10-digit normalization), duration, exact transcript, rule-based summary, and queue status (`PENDING`, `PROCESSING`, `COMPLETED`, `SKIPPED_TOO_SHORT`).
* **FilenameParser**: Regular-expression matches primary MIUI filename patterns (e.g. `0091-6361265991(00916361265991)_20260415122848.mp3`), fallback names, and date-timestamp parsing.

### Background Watcher & Processing Engine
* **Folder Watcher**: Uses `FileObserver` to detect new call audio files.
* **Debounce & Stabilization**: Employs a 2-second file-size check before processing to ensure writing is completed.
* **Resource Optimization**: Implemented screen ON/OFF BroadcastReceiver. Background queue processing of pre-existing older files resumes automatically when the screen is locked and suspends when the screen is active.
* **Audio Decoder**: Decodes various audio codecs down to 16kHz mono PCM using Android's native `MediaCodec` extractor/decoder.
* **Rule-Based Summary**: Categorizes sentiment (Positive/Negative/Neutral) and parses text for actionable items.

### User Interface (Jetpack Compose)
* **Dashboard**: Modern dark/slate design featuring permission configuration guides and live status counters.
* **History**: Grouped caller lists organized by date, featuring real-time partial phone number search queries.
* **Call Details**: Displays tabbed panes for checking the exact transcript vs. the parsed call summary.
* **Settings**: Enables changing the target directory and choosing custom external Vosk models.

---

## 3. Verification & Testing
* **Unit Tests**:
  * `FilenameParserTest`: Validates normalization logic (+91 or domestic prefixes) and matches all sample filename formats.
  * `SummarisationManagerTest`: Validates key points, sentiment analysis, and duration formatting.
  * Both suites run and pass successfully (`BUILD SUCCESSFUL`).
* **APK Ready**: Sideloadable APK built and compiled at: `app/build/outputs/apk/debug/app-debug.apk`

---

## 4. How to Test / Ready to Install?
**Yes, the app is 100% ready to install and test!**

Because this app utilizes deep background file monitoring and runs completely offline, follow these steps to test:

1. **Transfer the APK**: Copy the `app-debug.apk` onto the target MIUI/HyperOS device.
2. **Sideload & Install**: Open the file manager on the device and install the APK.
3. **Run the Permission Wizard on First Launch**:
   * **All Files Access (`MANAGE_EXTERNAL_STORAGE`)**: Grant this when prompted, so the app can scan the MIUI call recorder folder.
   * **Battery Optimization Exemption**: Accept the whitelist prompt so MIUI does not kill the background watcher.
   * **MIUI Autostart (Manual)**: Toggle "Autostart" to *ON* for this app in Settings (Settings -> Apps -> Manage Apps -> [App Name] -> Autostart) and select "No Restrictions" under App Battery Saver.
   * **Post Notifications**: Grant notification permission so you can see the background queue processing status.
4. **Select Target Folder**: Point the watch folder in Settings to the MIUI call recordings directory (usually `MIUI/sound_recorder/call_rec/`).
5. **ASR Model Extraction**: The app will extract the bundled ~36MB Vosk English-Indian model from assets into internal storage on first launch.
6. **Trigger Processing**:
   * **New Calls**: Place a sample call recording in the watch folder or make a call. The FileObserver detects it and transcribes it immediately.
   * **Older/Pre-existing Calls**: Turn off the device screen to trigger idle background processing. Unlock to pause it.
