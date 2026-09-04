package com.antai.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ContactEntity::class, MessageEntity::class, VerdictEntity::class,
        ReportEntity::class, CallLogEntity::class, FlagCacheEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contacts(): ContactDao
    abstract fun messages(): MessageDao
    abstract fun verdicts(): VerdictDao
    abstract fun reports(): ReportDao
    abstract fun calls(): CallLogDao
    abstract fun flags(): FlagDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "antai.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}