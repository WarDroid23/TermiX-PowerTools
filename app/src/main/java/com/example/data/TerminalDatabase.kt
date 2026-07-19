package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TerminalHistory::class,
        CustomScript::class,
        TodoTask::class,
        PocketNote::class,
        CommandSnippet::class
    ],
    version = 2,
    exportSchema = false
)
abstract class TerminalDatabase : RoomDatabase() {
    abstract fun terminalDao(): TerminalDao

    companion object {
        @Volatile
        private var INSTANCE: TerminalDatabase? = null

        fun getDatabase(context: Context): TerminalDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TerminalDatabase::class.java,
                    "terminal_productivity_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
