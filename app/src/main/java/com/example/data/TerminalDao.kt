package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TerminalDao {

    // Terminal command history
    @Query("SELECT * FROM terminal_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int): List<TerminalHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: TerminalHistory)

    @Query("DELETE FROM terminal_history")
    suspend fun clearHistory()

    // Custom automation scripts
    @Query("SELECT * FROM custom_scripts ORDER BY timestamp DESC")
    fun getScriptsFlow(): Flow<List<CustomScript>>

    @Query("SELECT * FROM custom_scripts ORDER BY timestamp DESC")
    suspend fun getScripts(): List<CustomScript>

    @Query("SELECT * FROM custom_scripts WHERE name = :name LIMIT 1")
    suspend fun getScriptByName(name: String): CustomScript?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: CustomScript)

    @Query("DELETE FROM custom_scripts WHERE id = :id")
    suspend fun deleteScript(id: Long)

    @Query("DELETE FROM custom_scripts WHERE name = :name")
    suspend fun deleteScriptByName(name: String)

    // Todo tasks
    @Query("SELECT * FROM todo_tasks ORDER BY timestamp DESC")
    fun getTasksFlow(): Flow<List<TodoTask>>

    @Query("SELECT * FROM todo_tasks ORDER BY timestamp DESC")
    suspend fun getTasks(): List<TodoTask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TodoTask)

    @Query("UPDATE todo_tasks SET isCompleted = :completed WHERE id = :id")
    suspend fun updateTaskStatus(id: Long, completed: Boolean)

    @Query("DELETE FROM todo_tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("DELETE FROM todo_tasks WHERE isCompleted = 1")
    suspend fun clearCompletedTasks()

    // Pocket Notes / Files
    @Query("SELECT * FROM pocket_notes ORDER BY timestamp DESC")
    fun getNotesFlow(): Flow<List<PocketNote>>

    @Query("SELECT * FROM pocket_notes ORDER BY timestamp DESC")
    suspend fun getNotes(): List<PocketNote>

    @Query("SELECT * FROM pocket_notes WHERE title = :title LIMIT 1")
    suspend fun getNoteByTitle(title: String): PocketNote?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: PocketNote)

    @Query("DELETE FROM pocket_notes WHERE id = :id")
    suspend fun deleteNote(id: Long)

    @Query("DELETE FROM pocket_notes WHERE title = :title")
    suspend fun deleteNoteByTitle(title: String)

    // Command Snippets
    @Query("SELECT * FROM command_snippets ORDER BY timestamp DESC")
    fun getSnippetsFlow(): Flow<List<CommandSnippet>>

    @Query("SELECT * FROM command_snippets ORDER BY timestamp DESC")
    suspend fun getSnippets(): List<CommandSnippet>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnippet(snippet: CommandSnippet)

    @Query("DELETE FROM command_snippets WHERE id = :id")
    suspend fun deleteSnippet(id: Long)
}
