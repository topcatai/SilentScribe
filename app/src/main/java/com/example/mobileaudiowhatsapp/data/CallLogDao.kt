package com.example.mobileaudiowhatsapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT COUNT(*) FROM call_logs WHERE status = 'SKIPPED_TOO_SHORT'")
    fun observeSkippedCount(): Flow<Int>
}
