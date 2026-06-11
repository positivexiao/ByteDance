package com.bytedace.doubaoapp.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MessageEntity::class, SessionEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun sessionDao(): SessionDao

    companion object {
        /** 数据库迁移: v1 → v2, 添加 usedWebSearch, searchResultsJson, followUpSuggestionsJson 列 */
        val MIGRATION_1_2 = Migration(1, 2) {
            it.execSQL("ALTER TABLE messages ADD COLUMN usedWebSearch INTEGER NOT NULL DEFAULT 0")
            it.execSQL("ALTER TABLE messages ADD COLUMN searchResultsJson TEXT")
            it.execSQL("ALTER TABLE messages ADD COLUMN followUpSuggestionsJson TEXT")
        }
    }
}