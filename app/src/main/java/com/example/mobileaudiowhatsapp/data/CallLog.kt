package com.example.mobileaudiowhatsapp.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "call_logs",
    indices = [Index(value = ["filename"], unique = true)]
)
data class CallLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filename: String,
    @ColumnInfo(name = "phone_number") val phoneNumber: String,      // Normalised: 10-digit, no prefix
    @ColumnInfo(name = "display_name") val displayName: String?,     // Parsed from filename prefix (e.g. "Papa"), else null
    @ColumnInfo(name = "timestamp_ms") val timestampMs: Long,        // Unix epoch ms parsed from filename
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int,
    @ColumnInfo(name = "exact_transcript") val exactTranscript: String? = null, // Plaintext. Null until status = COMPLETED
    @ColumnInfo(name = "caller_summary") val callerSummary: String? = null,   // Plaintext. Null until status = COMPLETED
    val status: String,           // PENDING | PROCESSING | COMPLETED | SKIPPED_TOO_SHORT
    @ColumnInfo(name = "is_new") val isNew: Int,               // 1 = arrived after first launch; 0 = pre-existing
    @ColumnInfo(name = "sync_status") val syncStatus: Int = 0       // Reserved for Phase 2 CRM
)
