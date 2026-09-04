package com.antai.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(contacts: List<ContactEntity>)

    @Query("SELECT * FROM contacts ORDER BY label")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE phone = :phone LIMIT 1")
    suspend fun byPhone(phone: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE (isTrusted = 1 OR linked = 1) ORDER BY label")
    fun observeTrusted(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE synced = 0")
    suspend fun unsynced(): List<ContactEntity>

    @Query("UPDATE contacts SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)
}

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Query("""
        UPDATE messages SET delivered = 1, riskScore = :risk, band = :band,
        verdictText = :verdictText, intercepted = :intercepted WHERE id = :id
    """)
    suspend fun markResult(id: Long, risk: Double, band: String,
                           verdictText: String, intercepted: Boolean)

    @Query("SELECT * FROM messages WHERE peerPhone = :peerPhone ORDER BY createdAt ASC")
    fun observeThread(peerPhone: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE delivered = 0 ORDER BY createdAt ASC")
    suspend fun pending(): List<MessageEntity>

    @Query("UPDATE messages SET delivered = 1 WHERE id = :id")
    suspend fun markDelivered(id: Long)

    @Query("""
        SELECT m.* FROM messages m
        WHERE m.id = (SELECT MAX(id) FROM messages WHERE peerPhone = m.peerPhone)
        ORDER BY m.createdAt DESC
    """)
    fun observeConversations(): Flow<List<MessageEntity>>

    @Query("SELECT DISTINCT peerPhone FROM messages")
    suspend fun peerPhones(): List<String>

    @Query("DELETE FROM messages WHERE peerPhone = :peerPhone")
    suspend fun clearThread(peerPhone: String)
}

@Dao
interface VerdictDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(verdict: VerdictEntity)

    @Query("SELECT * FROM verdicts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<VerdictEntity>>

    @Query("SELECT * FROM verdicts WHERE sessionKey = :sessionKey ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestFor(sessionKey: String): VerdictEntity?

    @Query("SELECT * FROM verdicts WHERE band != 'passive' ORDER BY createdAt DESC")
    suspend fun flagsOnly(): List<VerdictEntity>
}

@Dao
interface ReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: ReportEntity)

    @Query("SELECT * FROM reports ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ReportEntity>>
}

@Dao
interface CallLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(call: CallLogEntity): Long

    @Query("SELECT * FROM calls ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<CallLogEntity>>

    @Query("UPDATE calls SET status = :status, riskPeak = :risk, endedAt = :endedAt WHERE id = :id")
    suspend fun update(id: Long, status: String, risk: Double, endedAt: Long)
}

@Dao
interface FlagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(flag: FlagCacheEntity)

    @Query("SELECT * FROM flag_cache WHERE phone = :phone LIMIT 1")
    suspend fun byPhone(phone: String): FlagCacheEntity?

    @Query("SELECT * FROM flag_cache ORDER BY count DESC")
    fun observeAll(): Flow<List<FlagCacheEntity>>
}