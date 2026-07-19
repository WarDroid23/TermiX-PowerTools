package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "terminal_history")
data class TerminalHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val command: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_scripts")
data class CustomScript(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "todo_tasks")
data class TodoTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val task: String,
    val isCompleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "pocket_notes")
data class PocketNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "command_snippets")
data class CommandSnippet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val command: String,
    val category: String,
    val timestamp: Long = System.currentTimeMillis()
)
