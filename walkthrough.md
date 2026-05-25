# Walkthrough: Whisper ASR & local LLM Integration (v1.1.0)

This walkthrough documents the major upgrade from Vosk to **Whisper (via Sherpa-ONNX)** and the addition of a **local Large Language Model (via MediaPipe Tasks GenAI)**, as well as the database schema migration to version 2 and the publication of release `v1.1.0`.

## 🛠️ Changes Made

1. **Speech-to-Text Upgrade (Whisper)**:
   - Swapped **Vosk ASR** out for **OpenAI's Whisper** (via `sherpa-onnx-1.13.2.aar` local dependency).
   - Set up `OfflineRecognizer` with CPU provider and auto-language detection supporting multilingual and Hinglish audio decoding.
   - Retained the optimized native audio decoding pipeline (MediaCodec/MediaExtractor downmixing stereo to mono and resampling to 16kHz PCM WAV) but streamlined the try-finally cleanup blocks to immediately delete temporary WAV files.

2. **Local LLM Summarization**:
   - Integrated **MediaPipe Tasks GenAI SDK** (`tasks-genai:0.10.35`) to run on-device Small Language Models (e.g., Llama 3.2 1B or Gemma 2B).
   - Upgraded summarization from rule-based keyword searches to intelligent on-device generative LLM inference, extracting structured sentiment, call highlights, and action items.
   - Added a safe fallback to a rule-based parser in case the model is not loaded, is missing, or the device runs out of memory.

3. **Database Schema Migration (v1 ➔ v2)**:
   - Incremented database version to `2`.
   - Wrote a safe Room migration `MIGRATION_1_2` to add the `failure_reason` text column to the `call_logs` table without losing existing call records.
   - Verified that the migration preserves all 63 completed database transcripts.

4. **Background Service Optimizations**:
   - Updated `CallFolderWatcherService` to run a boot scan. Existing files found during boot are registered with the status `SKIPPED_HISTORICAL` (`isNew = 0`) to prevent overwhelming the device's CPU. Only new incoming calls arriving during active runtime are transcribed.
   - Added stacktrace capture to `failure_reason` in SQLite to simplify debugging background processing failures.
   - Kept the screen-lock resource optimizer: transcription and LLM inference suspend when the screen is active and resume when the screen is locked to save system resources.

5. **UI & Configuration Improvements**:
   - Display a `"Historical — not transcribed"` label on the `HistoryScreen` list items for any file skipped during startup.
   - Upgraded `SettingsScreen` to add parameters for the local LLM model file path and translation toggles.
   - Included translation model checks to display warning notices if Translate mode is selected but the loaded folder points to a `tiny-en` (English-only) model.

---

## 🔬 Verification & Validation Results

### 1. Build Verification
We compiled the release version of the application successfully:
```powershell
.\gradlew assembleRelease
```
- **Result**: `BUILD SUCCESSFUL` in 1m 42s.
- **APK Output**: `app-release-unsigned.apk` (~61.96 MB, well within the 80 MB constraint).

### 2. Database Migration Check
Verified schema updates via Python SQLite tool:
- **`user_version`**: `2`
- **`call_logs` Count**: All 63 completed transcripts survived migration.
- **`failure_reason` Column**: Successfully appended as `TEXT` type.

### 3. Automated Unit Tests
Executed all tests successfully:
```powershell
.\gradlew test
```
All unit tests for filename parsing and summarization parsing passed.

---

## 📦 Release Publication (v1.1.0)
Due to the major architectural changes and database migrations, the version was bumped to `v1.1.0` (versionCode `2`). 
- **Release Page**: Created on GitHub under tag `v1.1.0`.
- **Artifact Upload**: Uploaded `app-release-unsigned.apk` (61.96 MB) to the `v1.1.0` release asset stream.
- **Cleanup**: The incorrect release and tag `v1.0.1` containing the Whisper build were fully deleted from GitHub.
