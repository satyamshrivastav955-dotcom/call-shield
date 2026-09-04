package com.antai.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "contacts", indices = [Index(value = ["phone"], unique = true)])
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phone: String,
    val displayName: String,
    val label: String,
    val relationshipTag: String? = null,
    val isTrusted: Boolean = false,
    val linked: Boolean = false,
    val synced: Boolean = false,
)

@Entity(tableName = "messages", indices = [Index(value = ["peerPhone", "createdAt"])])
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val peerPhone: String,
    val fromMe: Boolean,
    val body: String,
    val riskScore: Double = 0.0,
    val band: String = "passive",
    val intercepted: Boolean = false,
    val verdictText: String = "",
    val delivered: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "verdicts", indices = [Index(value = ["createdAt"])])
data class VerdictEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionKey: String,
    val kind: String,
    val riskScore: Double,
    val band: String,
    val verdictText: String,
    val why: String,
    val action: String,
    val scamType: String? = null,
    val signals: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "reports", indices = [Index(value = ["createdAt"])])
data class ReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionKey: String,
    val kind: String,
    val title: String,
    val body: String,
    val scamType: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "calls", indices = [Index(value = ["startedAt"])])
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val peerPhone: String,
    val peerName: String,
    val kind: String,
    val status: String,
    val riskPeak: Double = 0.0,
    val startedAt: Long,
    val endedAt: Long? = null,
)

@Entity(tableName = "flag_cache", indices = [Index(value = ["phone"], unique = true)])
data class FlagCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phone: String,
    val kind: String = "scam",
    val count: Int = 1,
    val confidence: Double = 0.0,
    val locallyFlagged: Boolean = true,
)