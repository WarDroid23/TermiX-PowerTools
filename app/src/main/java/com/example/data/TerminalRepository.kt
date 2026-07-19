package com.example.data

import kotlinx.coroutines.flow.Flow

class TerminalRepository(private val dao: TerminalDao) {

    // Command History
    suspend fun getRecentHistory(limit: Int): List<TerminalHistory> = dao.getRecentHistory(limit)
    suspend fun addHistory(command: String) = dao.insertHistory(TerminalHistory(command = command))
    suspend fun clearHistory() = dao.clearHistory()

    // Scripts
    val allScripts: Flow<List<CustomScript>> = dao.getScriptsFlow()
    suspend fun getScripts(): List<CustomScript> = dao.getScripts()
    suspend fun getScriptByName(name: String): CustomScript? = dao.getScriptByName(name)
    suspend fun addScript(name: String, content: String) = dao.insertScript(CustomScript(name = name, content = content))
    suspend fun deleteScript(id: Long) = dao.deleteScript(id)
    suspend fun deleteScriptByName(name: String) = dao.deleteScriptByName(name)

    // Tasks (Todo)
    val allTasks: Flow<List<TodoTask>> = dao.getTasksFlow()
    suspend fun getTasks(): List<TodoTask> = dao.getTasks()
    suspend fun addTask(task: String) = dao.insertTask(TodoTask(task = task))
    suspend fun updateTaskStatus(id: Long, completed: Boolean) = dao.updateTaskStatus(id, completed)
    suspend fun deleteTask(id: Long) = dao.deleteTask(id)
    suspend fun clearCompletedTasks() = dao.clearCompletedTasks()

    // Notes
    val allNotes: Flow<List<PocketNote>> = dao.getNotesFlow()
    suspend fun getNotes(): List<PocketNote> = dao.getNotes()
    suspend fun getNoteByTitle(title: String): PocketNote? = dao.getNoteByTitle(title)
    suspend fun addNote(title: String, content: String) = dao.insertNote(PocketNote(title = title, content = content))
    suspend fun deleteNote(id: Long) = dao.deleteNote(id)
    suspend fun deleteNoteByTitle(title: String) = dao.deleteNoteByTitle(title)

    // Snippets
    val allSnippets: Flow<List<CommandSnippet>> = dao.getSnippetsFlow()
    suspend fun getSnippets(): List<CommandSnippet> = dao.getSnippets()
    suspend fun addSnippet(title: String, description: String, command: String, category: String, id: Long = 0) = dao.insertSnippet(CommandSnippet(id = id, title = title, description = description, command = command, category = category))
    suspend fun deleteSnippet(id: Long) = dao.deleteSnippet(id)

    // Prepopulate some default helper scripts
    suspend fun prepopulateDefaultsIfEmpty() {
        val scripts = dao.getScripts()
        if (scripts.isEmpty()) {
            dao.insertScript(
                CustomScript(
                    name = "welcome",
                    content = """
                        echo "================================"
                        echo "   WELCOME TO POCKET TERMINAL"
                        echo "================================"
                        echo "Your productive command interface is ready."
                        quotes
                        sysinfo
                        echo "Tip: Run 'help' to see what I can do!"
                    """.trimIndent()
                )
            )
            dao.insertScript(
                CustomScript(
                    name = "pomowork",
                    content = """
                        echo "[SCRIPT] Starting Pomodoro Focus Session..."
                        todo list
                        echo "Starting 25 minute focus block..."
                        pomo start 25
                    """.trimIndent()
                )
            )
            dao.insertScript(
                CustomScript(
                    name = "sysaudit",
                    content = """
                        echo "[SCRIPT] Auditing System & Workspace..."
                        sysinfo
                        echo "Checking remaining tasks:"
                        todo list
                        echo "Checking saved files:"
                        notes list
                    """.trimIndent()
                )
            )
        }

        // Also add a sample note if empty
        val notes = dao.getNotes()
        if (notes.isEmpty()) {
            dao.insertNote(
                PocketNote(
                    title = "readme",
                    content = """
                        Welcome to your pocket terminal!
                        This terminal is equipped with CLI commands to boost your mobile productivity.
                        
                        Try these commands:
                        - todo add "Buy coffee"
                        - todo list
                        - pomo start 25
                        - sysinfo
                        - notes list
                        - quotes
                        - matrix
                        
                        You can edit and run scripts using the 'script' utility or the GUI sidebar tab!
                    """.trimIndent()
                )
            )
            // Pre-install default files for python, js, c, ruby, php, perl, go!
            dao.insertNote(
                PocketNote(
                    title = "hello.py",
                    content = """
                        # Python test script
                        print("Hello from Python 3!")
                        print("Math result: 5 + 5")
                    """.trimIndent()
                )
            )
            dao.insertNote(
                PocketNote(
                    title = "app.js",
                    content = """
                        // Node.js test script
                        console.log("Hello from Javascript / Node.js!")
                        console.log("Ready to execute.")
                    """.trimIndent()
                )
            )
            dao.insertNote(
                PocketNote(
                    title = "main.c",
                    content = """
                        #include <stdio.h>
                        int main() {
                            printf("Hello from Compiled C!\n");
                            printf("Execution finished.\n");
                            return 0;
                        }
                    """.trimIndent()
                )
            )
            dao.insertNote(
                PocketNote(
                    title = "main.go",
                    content = """
                        package main
                        import "fmt"
                        func main() {
                            fmt.Println("Hello from Go!")
                        }
                    """.trimIndent()
                )
            )
            dao.insertNote(
                PocketNote(
                    title = "hello.rb",
                    content = """
                        # Ruby test script
                        puts "Hello from Ruby!"
                    """.trimIndent()
                )
            )
            dao.insertNote(
                PocketNote(
                    title = "index.php",
                    content = """
                        <?php
                        echo "Hello from PHP!";
                        ?>
                    """.trimIndent()
                )
            )
            dao.insertNote(
                PocketNote(
                    title = "app.pl",
                    content = """
                        # Perl test script
                        print "Hello from Perl!";
                    """.trimIndent()
                )
            )
        }

        // Also add a sample todo if empty
        val tasks = dao.getTasks()
        if (tasks.isEmpty()) {
            dao.insertTask(TodoTask(task = "Explore Termux command lists"))
            dao.insertTask(TodoTask(task = "Run the 'welcome' script"))
            dao.insertTask(TodoTask(task = "Try starting a 25-min Pomodoro timer"))
        }

        // Prepopulate default snippets if empty
        val snippets = dao.getSnippets()
        if (snippets.isEmpty()) {
            dao.insertSnippet(
                CommandSnippet(
                    title = "System Summary",
                    description = "Displays device parameters, CPU and uptime",
                    command = "neofetch",
                    category = "Utilities"
                )
            )
            dao.insertSnippet(
                CommandSnippet(
                    title = "Check Workspace Status",
                    description = "Run a system audit script to view status, tasks, and files",
                    command = "script run sysaudit",
                    category = "VCS"
                )
            )
            dao.insertSnippet(
                CommandSnippet(
                    title = "Python Demo Code",
                    description = "Run the hello.py Python script",
                    command = "python hello.py",
                    category = "Languages"
                )
            )
            dao.insertSnippet(
                CommandSnippet(
                    title = "C Compilation Demo",
                    description = "Compile main.c with clang and run it",
                    command = "clang main.c -o greet && ./greet",
                    category = "Languages"
                )
            )
            dao.insertSnippet(
                CommandSnippet(
                    title = "Secure Network Call",
                    description = "Simulate curl with response saving to pocket_response.json",
                    command = "curl https://termux.api/pocket -o pocket_response.json",
                    category = "Network"
                )
            )
            dao.insertSnippet(
                CommandSnippet(
                    title = "Strong Pass Generator",
                    description = "Generate a secure random 16-character password",
                    command = "passgen 16",
                    category = "Utilities"
                )
            )
        }
    }
}
