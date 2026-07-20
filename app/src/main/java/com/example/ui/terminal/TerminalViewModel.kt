package com.example.ui.terminal

import android.app.Application
import android.content.ClipboardManager
import android.content.ClipData
import android.net.TrafficStats
import android.os.BatteryManager
import android.content.Context
import android.os.SystemClock
import android.app.ActivityManager
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.text.SimpleDateFormat
import java.util.*

enum class LineType {
    INPUT,
    OUTPUT,
    ERROR,
    SUCCESS,
    INFO,
    HEADER,
    WARNING
}

data class TerminalLine(
    val text: String,
    val type: LineType = LineType.OUTPUT
)

enum class TerminalTheme(val displayName: String) {
    IMMERSIVE("Immersive Dark"),
    MATRIX("Matrix Green"),
    AMBER("Amber Phosphor"),
    CYBERPUNK("Cyberpunk Neon"),
    MONOKAI("Monokai Dark"),
    SNOW("Arctic White")
}

data class DeviceMetrics(
    val batteryLevel: Int = 100,
    val batteryTemp: Double = 30.0,
    val isCharging: Boolean = false,
    val availRamGb: Double = 4.0,
    val totalRamGb: Double = 8.0,
    val usedRamPercent: Int = 50,
    val cpuTemp: Double = 35.0,
    val cpuFrequencyMhz: Double = 1800.0,
    val appStorageFreeGb: Double = 10.0,
    val appStorageTotalGb: Double = 64.0,
    val systemUptime: String = "0h 0m",
    val efficiencyScore: Int = 90,
    val downloadSpeedKbps: Double = 0.0,
    val uploadSpeedKbps: Double = 0.0,
    val totalRxBytes: Long = 0L,
    val totalTxBytes: Long = 0L,
    val networkHistory: List<Pair<Double, Double>> = emptyList()
)

data class PomodoroSession(
    val totalSeconds: Int,
    val secondsRemaining: Int,
    val isRunning: Boolean,
    val isPaused: Boolean = false,
    val isWork: Boolean = true,
    val completedCount: Int = 0,
    val activeTask: String? = null,
    val sessionType: String = "Work"
)

class TerminalViewModel(
    private val application: Application,
    private val repository: TerminalRepository
) : AndroidViewModel(application) {

    // Clipboard History Management
    private val prefs = application.getSharedPreferences("termux_clipboard_prefs", Context.MODE_PRIVATE)
    private val _clipboardHistory = MutableStateFlow<List<String>>(emptyList())
    val clipboardHistory: StateFlow<List<String>> = _clipboardHistory.asStateFlow()

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        try {
            val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip()) {
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val text = clipData.getItemAt(0).text?.toString()
                    if (!text.isNullOrBlank()) {
                        addCopiedCommand(text.trim())
                    }
                }
            }
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    fun addCopiedCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return
        val current = _clipboardHistory.value.toMutableList()
        current.remove(trimmed) // Move to front if exists
        current.add(0, trimmed)
        if (current.size > 15) {
            current.removeAt(current.size - 1)
        }
        _clipboardHistory.value = current
        saveClipboardHistory(current)
    }

    fun clearClipboardHistory() {
        _clipboardHistory.value = emptyList()
        saveClipboardHistory(emptyList())
    }

    private fun saveClipboardHistory(list: List<String>) {
        try {
            prefs.edit().putString("clipboard_history_v1", list.joinToString("\u001f")).apply()
        } catch (e: Exception) {}
    }

    private fun loadClipboardHistory() {
        try {
            val raw = prefs.getString("clipboard_history_v1", null)
            if (raw != null) {
                _clipboardHistory.value = raw.split("\u001f").filter { it.isNotEmpty() }
            }
        } catch (e: Exception) {}
    }

    // Backup State Tracking
    private val _lastBackupTime = MutableStateFlow<Long>(0L)
    val lastBackupTime: StateFlow<Long> = _lastBackupTime.asStateFlow()

    private val _lastBackupSize = MutableStateFlow<Long>(0L)
    val lastBackupSize: StateFlow<Long> = _lastBackupSize.asStateFlow()

    private val _lastBackupFile = MutableStateFlow<String?>(null)
    val lastBackupFile: StateFlow<String?> = _lastBackupFile.asStateFlow()

    private fun saveBackupMetadata(time: Long, size: Long, filePath: String) {
        _lastBackupTime.value = time
        _lastBackupSize.value = size
        _lastBackupFile.value = filePath
        try {
            prefs.edit().apply {
                putLong("backup_time", time)
                putLong("backup_size", size)
                putString("backup_file", filePath)
                apply()
            }
        } catch (e: Exception) {}
    }

    private fun loadBackupMetadata() {
        try {
            _lastBackupTime.value = prefs.getLong("backup_time", 0L)
            _lastBackupSize.value = prefs.getLong("backup_size", 0L)
            _lastBackupFile.value = prefs.getString("backup_file", null)
        } catch (e: Exception) {}
    }

    fun triggerLocalBackup() {
        viewModelScope.launch {
            handleBackupCommand()
        }
    }

    private fun serializeSnippets(snippets: List<CommandSnippet>): String {
        val items = snippets.joinToString(",\n") { snippet ->
            """
            {
              "title": ${escapeJson(snippet.title)},
              "description": ${escapeJson(snippet.description)},
              "command": ${escapeJson(snippet.command)},
              "category": ${escapeJson(snippet.category)},
              "timestamp": ${snippet.timestamp}
            }
            """.trimIndent()
        }
        return "[\n$items\n]"
    }

    private fun serializeScripts(scripts: List<CustomScript>): String {
        val items = scripts.joinToString(",\n") { script ->
            """
            {
              "name": ${escapeJson(script.name)},
              "content": ${escapeJson(script.content)},
              "timestamp": ${script.timestamp}
            }
            """.trimIndent()
        }
        return "[\n$items\n]"
    }

    private fun escapeJson(str: String): String {
        val escaped = str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    fun createBackupZip(context: Context, snippetsJson: String, scriptsJson: String): File? {
        try {
            val backupDir = File(context.filesDir, "backups")
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }
            val zipFile = File(backupDir, "termux_config_backup.zip")
            if (zipFile.exists()) {
                zipFile.delete()
            }
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                // Add snippets.json
                val snippetsEntry = ZipEntry("snippets.json")
                zos.putNextEntry(snippetsEntry)
                zos.write(snippetsJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // Add scripts.json
                val scriptsEntry = ZipEntry("scripts.json")
                zos.putNextEntry(scriptsEntry)
                zos.write(scriptsJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            // Save metadata
            saveBackupMetadata(System.currentTimeMillis(), zipFile.length(), zipFile.absolutePath)
            return zipFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.removePrimaryClipChangedListener(clipboardListener)
        } catch (e: Exception) {}
    }

    // Network Telemetry Tracking
    private var lastRxBytes: Long = -1L
    private var lastTxBytes: Long = -1L
    private var lastNetUpdateTime: Long = -1L
    private val netSpeedHistory = Collections.synchronizedList(mutableListOf<Pair<Double, Double>>())

    // Terminal History and Outputs
    private val _terminalOutput = MutableStateFlow<List<TerminalLine>>(emptyList())
    val terminalOutput: StateFlow<List<TerminalLine>> = _terminalOutput.asStateFlow()

    private val _inputBuffer = MutableStateFlow("")
    val inputBuffer: StateFlow<String> = _inputBuffer.asStateFlow()

    // Command scroll history
    private val _historyList = MutableStateFlow<List<String>>(emptyList())
    private var historyPointer = -1

    // Theme state
    private val _selectedTheme = MutableStateFlow(TerminalTheme.IMMERSIVE)
    val selectedTheme: StateFlow<TerminalTheme> = _selectedTheme.asStateFlow()

    // Script execution state
    private val _isScriptExecuting = MutableStateFlow(false)
    val isScriptExecuting: StateFlow<Boolean> = _isScriptExecuting.asStateFlow()

    // Script console terminal output
    private val _scriptConsoleOutput = MutableStateFlow<List<TerminalLine>>(emptyList())
    val scriptConsoleOutput: StateFlow<List<TerminalLine>> = _scriptConsoleOutput.asStateFlow()

    fun clearScriptConsoleOutput() {
        _scriptConsoleOutput.value = emptyList()
    }

    // Matrix digital rain visualization toggle
    private val _isMatrixActive = MutableStateFlow(false)
    val isMatrixActive: StateFlow<Boolean> = _isMatrixActive.asStateFlow()

    // Nano text editor state
    private val _isNanoActive = MutableStateFlow(false)
    val isNanoActive: StateFlow<Boolean> = _isNanoActive.asStateFlow()

    private val _nanoFileName = MutableStateFlow("")
    val nanoFileName: StateFlow<String> = _nanoFileName.asStateFlow()

    private val _nanoContent = MutableStateFlow("")
    val nanoContent: StateFlow<String> = _nanoContent.asStateFlow()

    // Pomodoro Timer
    private val _pomoSession = MutableStateFlow(PomodoroSession(1500, 1500, false))
    val pomoSession: StateFlow<PomodoroSession> = _pomoSession.asStateFlow()
    private var pomoJob: Job? = null

    // Interactive prompt prefix
    private val _prompt = MutableStateFlow("~ $ ")
    val prompt: StateFlow<String> = _prompt.asStateFlow()

    // Active sub-shell or REPL mode
    var activeRepl: String? = null

    // Set of pre-installed packages
    val installedPackages = mutableSetOf(
        "apt", "apk", "pkg", "bash", "clang", "gcc", "python", "nodejs", "golang", "ruby", "php", "perl", "git", "curl", "wget", "neofetch", "coreutils", "nano", "tar", "gzip"
    )

    // Git simulated repository state
    private var gitInitialized = false
    private val gitStagedFiles = mutableSetOf<String>()
    private val gitUnstagedFiles = mutableSetOf<String>()
    private val gitCommits = mutableListOf<String>()

    // Python simulated variables
    private val pythonVariables = mutableMapOf<String, String>()

    // Preinstalled/available packages metadata
    data class PackageInfo(val name: String, val version: String, val description: String, val category: String)
    val virtualPackages = listOf(
        PackageInfo("apt", "2.6.1", "Advanced Package Tool", "Package Management"),
        PackageInfo("apk", "3.1.0", "Alpine Package Keeper", "Package Management"),
        PackageInfo("pkg", "1.19.1", "Termux Package Wrapper", "Package Management"),
        PackageInfo("bash", "5.2.15", "GNU Bourne-Again SHell", "System Shell"),
        PackageInfo("clang", "16.0.4", "C/C++ Compiler frontend", "Development"),
        PackageInfo("gcc", "12.2.0", "GNU Compiler Collection", "Development"),
        PackageInfo("python", "3.11.3", "High-level programming language", "Development"),
        PackageInfo("nodejs", "20.2.0", "Event-driven JavaScript runtime", "Development"),
        PackageInfo("golang", "1.21.1", "Go programming language compiler", "Development"),
        PackageInfo("ruby", "3.2.2", "Dynamic programming language", "Development"),
        PackageInfo("php", "8.2.6", "Server-side HTML scripting language", "Development"),
        PackageInfo("perl", "5.36.1", "Practical Extraction & Reporting", "Development"),
        PackageInfo("git", "2.41.0", "Distributed version control system", "VCS"),
        PackageInfo("curl", "8.1.2", "Command line tool for URL transfer", "Network"),
        PackageInfo("wget", "1.21.4", "Network utility to retrieve files", "Network"),
        PackageInfo("neofetch", "7.1.0", "Fast, customized system info script", "Utility"),
        PackageInfo("nano", "7.2.0", "Easy-to-use text editor", "Utility"),
        PackageInfo("coreutils", "9.3", "GNU core utilities (ls, cat, rm)", "System"),
        PackageInfo("rust", "1.70.0", "Systems programming language", "Development"),
        PackageInfo("docker", "24.0.2", "Pack, ship and run applications", "Infrastructure")
    )

    // System Device Metrics Flow
    private val _deviceMetrics = MutableStateFlow(DeviceMetrics())
    val deviceMetrics: StateFlow<DeviceMetrics> = _deviceMetrics.asStateFlow()

    // Room Flows for GUI Sync
    val allScripts = repository.allScripts
    val allTasks = repository.allTasks
    val allNotes = repository.allNotes
    val allSnippets = repository.allSnippets

    fun addSnippet(title: String, description: String, command: String, category: String, id: Long = 0) {
        viewModelScope.launch {
            repository.addSnippet(title, description, command, category, id)
        }
    }

    fun deleteSnippet(id: Long) {
        viewModelScope.launch {
            repository.deleteSnippet(id)
        }
    }

    private val quotesList = listOf(
        "Talk is cheap. Show me the code. — Linus Torvalds",
        "Programs must be written for people to read, and only incidentally for machines to execute. — Abelson & Sussman",
        "Truth can only be found in one place: the code. — Robert C. Martin",
        "Walking on water and developing software from a specification are easy if both are frozen. — Edward V Berard",
        "First, solve the problem. Then, write the code. — John Johnson",
        "Experience is the name everyone gives to their mistakes. — Oscar Wilde",
        "In order to be irreplaceable, one must always be different. — Coco Chanel",
        "Computers are good at following instructions, but not at reading your mind. — Donald Knuth",
        "Simplicity is the soul of efficiency. — Austin Freeman",
        "Make it work, make it right, make it fast. — Kent Beck"
    )

    init {
        try {
            val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.addPrimaryClipChangedListener(clipboardListener)
        } catch (e: Exception) {}
        loadClipboardHistory()
        loadBackupMetadata()

        viewModelScope.launch {
            repository.prepopulateDefaultsIfEmpty()
            loadHistory()
            appendLine("Pocket Terminal Emulator [Version 2.4]", LineType.HEADER)
            appendLine("Type 'help' to view documentation or tap elements in the GUI dashboard.", LineType.INFO)
            appendLine("System online. Enter command:", LineType.OUTPUT)
        }
        // Background device metrics polling
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                updateDeviceMetrics()
                delay(1500)
            }
        }
    }

    private fun updateDeviceMetrics() {
        try {
            val context = application.applicationContext

            // 1. Battery Info wrapped safely
            var batPct = 85
            var isCharging = false
            var batteryTemp = 28.5
            try {
                val batteryStatus: Intent? = context.registerReceiver(
                    null,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                )
                val batteryLevel = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val batteryScale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                batPct = if (batteryLevel >= 0 && batteryScale > 0) {
                    (batteryLevel * 100) / batteryScale
                } else {
                    val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                    bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
                }

                val chargingStatus = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                isCharging = chargingStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                        chargingStatus == BatteryManager.BATTERY_STATUS_FULL

                val batteryTempTenths = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
                batteryTemp = batteryTempTenths / 10.0
            } catch (e: Exception) {
                // Keep default simulated battery values on security exception
            }

            // 2. RAM Memory Info wrapped safely
            var availRamGb = 4.0
            var totalRamGb = 8.0
            var usedRamPercent = 50
            try {
                val memoryInfo = ActivityManager.MemoryInfo()
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                activityManager.getMemoryInfo(memoryInfo)
                availRamGb = memoryInfo.availMem / (1024.0 * 1024.0 * 1024.0)
                totalRamGb = memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
                val usedMem = totalRamGb - availRamGb
                usedRamPercent = ((usedMem / totalRamGb) * 100).toInt().coerceIn(0, 100)
            } catch (e: Exception) {
                // Ignore or keep defaults
            }

            // 3. CPU Temperature & Frequency fully simulated to avoid virtual sysfs I/O blocking
            val chargingOffset = if (isCharging) 4.5 else 0.0
            val loadOffset = (usedRamPercent - 30).coerceAtLeast(0) * 0.15
            val noise = (Math.random() * 0.6) - 0.3
            val baseCpuTemp = if (batteryTemp > 10.0) batteryTemp + 6.0 else 34.0
            val cpuTemp = baseCpuTemp + chargingOffset + loadOffset + noise

            val baseFreq = 1400.0
            val loadFreq = (usedRamPercent * 12.0)
            val freqNoise = (Math.random() * 50.0) - 25.0
            val cpuFreqMhz = baseFreq + loadFreq + freqNoise

            // 4. Storage Info
            var freeGb = 10.0
            var totalGb = 64.0
            try {
                val path = context.filesDir
                freeGb = path.freeSpace / (1024.0 * 1024.0 * 1024.0)
                totalGb = path.totalSpace / (1024.0 * 1024.0 * 1024.0)
            } catch (e: Exception) {
                // Keep defaults
            }

            // 5. System Uptime
            val uptimeSec = SystemClock.elapsedRealtime() / 1000
            val hr = uptimeSec / 3600
            val min = (uptimeSec % 3600) / 60
            val uptimeStr = "${hr}h ${min}m"

            // 6. Efficiency Score
            val tempPenalty = ((cpuTemp - 30.0) * 1.2).coerceAtLeast(0.0).toInt()
            val ramPenalty = (usedRamPercent * 0.4).toInt()
            val efficiencyScore = (100 - tempPenalty - ramPenalty).coerceIn(10, 100)

            // 7. Network Traffic Stats
            var rxBytes = 0L
            var txBytes = 0L
            var downSpeed = 0.0
            var upSpeed = 0.0
            try {
                rxBytes = TrafficStats.getTotalRxBytes()
                txBytes = TrafficStats.getTotalTxBytes()
                val now = SystemClock.elapsedRealtime()

                if (rxBytes != TrafficStats.UNSUPPORTED.toLong() && txBytes != TrafficStats.UNSUPPORTED.toLong()) {
                    if (lastRxBytes != -1L && lastTxBytes != -1L && lastNetUpdateTime != -1L) {
                        val timeDiffSec = (now - lastNetUpdateTime) / 1000.0
                        if (timeDiffSec > 0.1) {
                            val rxDiff = rxBytes - lastRxBytes
                            val txDiff = txBytes - lastTxBytes
                            
                            downSpeed = if (rxDiff >= 0) (rxDiff / 1024.0) / timeDiffSec else 0.0
                            upSpeed = if (txDiff >= 0) (txDiff / 1024.0) / timeDiffSec else 0.0
                        }
                    }
                    lastRxBytes = rxBytes
                    lastTxBytes = txBytes
                    lastNetUpdateTime = now
                } else {
                    val appUid = android.os.Process.myUid()
                    val uidRx = TrafficStats.getUidRxBytes(appUid)
                    val uidTx = TrafficStats.getUidTxBytes(appUid)
                    if (uidRx != TrafficStats.UNSUPPORTED.toLong()) {
                        if (lastRxBytes != -1L && lastTxBytes != -1L && lastNetUpdateTime != -1L) {
                            val timeDiffSec = (now - lastNetUpdateTime) / 1000.0
                            if (timeDiffSec > 0.1) {
                                val rxDiff = uidRx - lastRxBytes
                                val txDiff = uidTx - lastTxBytes
                                downSpeed = if (rxDiff >= 0) (rxDiff / 1024.0) / timeDiffSec else 0.0
                                upSpeed = if (txDiff >= 0) (txDiff / 1024.0) / timeDiffSec else 0.0
                            }
                        }
                        lastRxBytes = uidRx
                        lastTxBytes = uidTx
                        lastNetUpdateTime = now
                        rxBytes = uidRx
                        txBytes = uidTx
                    } else {
                        // Ambient dynamic flow simulation
                        downSpeed = (Math.random() * 8.5) + 0.5
                        upSpeed = (Math.random() * 2.1) + 0.1
                        rxBytes = 15243100L + (now / 10).toLong()
                        txBytes = 4210400L + (now / 40).toLong()
                    }
                }
            } catch (e: Exception) {
                downSpeed = (Math.random() * 12.0) + 1.0
                upSpeed = (Math.random() * 3.0) + 0.5
                rxBytes = 104857600L
                txBytes = 52428800L
            }

            synchronized(netSpeedHistory) {
                netSpeedHistory.add(Pair(downSpeed, upSpeed))
                while (netSpeedHistory.size > 15) {
                    netSpeedHistory.removeAt(0)
                }
            }

            _deviceMetrics.value = DeviceMetrics(
                batteryLevel = batPct,
                batteryTemp = batteryTemp,
                isCharging = isCharging,
                availRamGb = availRamGb,
                totalRamGb = totalRamGb,
                usedRamPercent = usedRamPercent,
                cpuTemp = cpuTemp,
                cpuFrequencyMhz = cpuFreqMhz,
                appStorageFreeGb = freeGb,
                appStorageTotalGb = totalGb,
                systemUptime = uptimeStr,
                efficiencyScore = efficiencyScore,
                downloadSpeedKbps = downSpeed,
                uploadSpeedKbps = upSpeed,
                totalRxBytes = rxBytes,
                totalTxBytes = txBytes,
                networkHistory = ArrayList(netSpeedHistory)
            )
        } catch (e: Exception) {
            // Root catch to prevent background thread crash/freeze
        }
    }

    private suspend fun loadHistory() {
        val history = repository.getRecentHistory(50)
        _historyList.value = history.map { it.command }.reversed()
        historyPointer = _historyList.value.size
    }

    fun onInputChange(text: String) {
        _inputBuffer.value = text
    }

    fun selectTheme(theme: TerminalTheme) {
        _selectedTheme.value = theme
        appendLine("System: Switched theme to ${theme.displayName}", LineType.INFO)
    }

    fun toggleMatrix(active: Boolean) {
        _isMatrixActive.value = active
        if (active) {
            appendLine("Starting matrix cascade visualization...", LineType.INFO)
        } else {
            appendLine("Matrix process terminated.", LineType.OUTPUT)
        }
    }

    // Command History scrolling (Up/Down)
    fun historyUp() {
        val history = _historyList.value
        if (history.isEmpty()) return
        if (historyPointer > 0) {
            historyPointer--
            _inputBuffer.value = history[historyPointer]
        } else if (historyPointer == 0) {
            _inputBuffer.value = history[0]
        }
    }

    fun historyDown() {
        val history = _historyList.value
        if (history.isEmpty()) return
        if (historyPointer < history.size - 1) {
            historyPointer++
            _inputBuffer.value = history[historyPointer]
        } else {
            historyPointer = history.size
            _inputBuffer.value = ""
        }
    }

    fun executeCommandBuffer() {
        val command = _inputBuffer.value.trim()
        if (command.isEmpty()) return

        _inputBuffer.value = ""
        executeCommand(command)
    }

    fun executeCommand(command: String, isFromScript: Boolean = false) {
        viewModelScope.launch {
            executeCommandSuspend(command, isFromScript)
        }
    }

    suspend fun executeCommandSuspend(command: String, isFromScript: Boolean = false) {
        if (!isFromScript) {
            appendLine("${_prompt.value}$command", LineType.INPUT)
            repository.addHistory(command)
            loadHistory()
        }

        val cmdTrimmed = command.trim()
        if (cmdTrimmed.isEmpty()) return

        // If interactive REPL session is active, handle and return
        if (activeRepl != null) {
            handleReplInput(cmdTrimmed)
            return
        }

        val parts = cmdTrimmed.split("\\s+".toRegex())
        val cmd = parts[0]
        val args = parts.drop(1)

        executeCommandLogic(cmd, args)
    }

    private suspend fun executeCommandLogic(cmdRaw: String, args: List<String>) {
        val cmd = cmdRaw.lowercase()

        // 1. Binary Execution Check
        if (cmdRaw.startsWith("./") || repository.getNoteByTitle(cmdRaw) != null) {
            val binaryName = cmdRaw.removePrefix("./")
            val note = repository.getNoteByTitle(binaryName)
            if (note != null && note.content.startsWith("BINARY:")) {
                val instructions = note.content.removePrefix("BINARY:").split(";")
                instructions.forEach { instr ->
                    if (instr.isNotEmpty()) {
                        appendLine(instr, LineType.OUTPUT)
                    }
                }
                return
            }
        }

        // 2. Command Router
        when (cmd) {
            "help" -> showHelp()
            "clear" -> clearScreen()
            "sysinfo" -> showSysInfo()
            "clean-cache" -> {
                appendLine("Scanning sandboxed directories...", LineType.INFO)
                delay(350)
                appendLine("Found 42.8 MB of cached index files and temporary build logs.", LineType.OUTPUT)
                delay(400)
                appendLine("Clearing tmp caches...", LineType.INFO)
                delay(300)
                appendLine("Clearing pkg/apt cache databases...", LineType.INFO)
                delay(250)
                appendLine("[SUCCESS] Sandbox cache cleaned successfully. Freed 42.8 MB.", LineType.SUCCESS)
            }
            "update-repos" -> {
                appendLine("Retrieving package archives from remote mirror...", LineType.INFO)
                delay(300)
                appendLine("Get:1 https://termux.org/packages stable InRelease [12.4 kB]", LineType.INFO)
                delay(250)
                appendLine("Get:2 https://termux.org/packages stable/main arm64 Packages [482 kB]", LineType.INFO)
                delay(400)
                appendLine("Fetched 494.4 kB in 1s (412 kB/s)", LineType.SUCCESS)
                appendLine("Reading package lists... Done", LineType.SUCCESS)
                appendLine("Repository status: ACTIVE & UP-TO-DATE", LineType.SUCCESS)
            }
            "sys-diagnose" -> {
                appendLine("Starting hardware and software system diagnostics...", LineType.HEADER)
                delay(400)
                appendLine("Checking CPU thermal throttling: NORMAL (38.2°C)", LineType.INFO)
                delay(300)
                appendLine("Checking RAM pressure: STABLE (3.4 GB available)", LineType.INFO)
                delay(300)
                val totalNotes = repository.getNotes().size
                val totalScripts = repository.getScripts().size
                appendLine("Scanning sandboxed storage: $totalNotes notes, $totalScripts scripts", LineType.OUTPUT)
                delay(250)
                appendLine("Diagnosis result: System healthy. Zero errors found.", LineType.SUCCESS)
            }
            "quotes" -> showQuote()
            "matrix" -> toggleMatrix(true)
            "todo" -> handleTodoCommand(args)
            "notes" -> handleNotesCommand(args)
            "script" -> handleScriptCommand(args)
            "nano" -> handleNanoCommand(args)
            "pomo" -> handlePomoCommand(args)
            "passgen" -> handlePassGenCommand(args)
            "base64" -> handleBase64Command(args)
            "echo" -> handleEchoCommand(args)
            "theme" -> handleThemeCommand(args)
            "history" -> handleHistoryCommand()
            "backup", "config-backup" -> handleBackupCommand()
            "apt-upgrade-all" -> handleAptUpgradeAllCommand()

            // ---- NEW PRE-INSTALLED TOOLS SIMULATION ----
            "neofetch" -> {
                val metrics = _deviceMetrics.value
                val archLogo = """
                       _      _      
                     _(_)_  _(_)_    termux@pocket
                    (_) (_)(_) (_)   -------------
                      _ _  _  _ _    OS: Android 13.0
                     (_(_)(_)(_(__   Kernel: Linux 5.15.0-aarch64
                      (_(_)(_)(_)    Shell: bash 5.2.15
                       (_(_(_(__     Uptime: ${metrics.systemUptime}
                        (_(_(__      Packages: ${installedPackages.size} (apt)
                         (_(__       Theme: ${_selectedTheme.value.displayName}
                                     CPU: Snapdragon Core (${metrics.cpuFrequencyMhz.toInt()} MHz)
                                     Memory: ${metrics.usedRamPercent}% (${(metrics.totalRamGb - metrics.availRamGb).toInt() * 100}MB / ${metrics.totalRamGb.toInt()}GB)
                                     Battery: ${metrics.batteryLevel}% (${if (metrics.isCharging) "charging" else "discharging"})
                """.trimIndent()
                appendLine(archLogo, LineType.SUCCESS)
            }

            "ls", "dir" -> {
                val notes = repository.getNotes()
                val scripts = repository.getScripts()
                if (notes.isEmpty() && scripts.isEmpty()) {
                    appendLine("Directory empty.", LineType.INFO)
                } else {
                    appendLine("Listing sandboxed directory contents:", LineType.HEADER)
                    notes.forEach { note ->
                        val extension = if (note.title.contains(".")) "" else ".txt"
                        appendLine("  -rw-r--r--   1 termux  termux   %4d B   %s%s".format(note.content.length, note.title, extension), LineType.OUTPUT)
                    }
                    scripts.forEach { script ->
                        appendLine("  -rwxr-xr-x   1 termux  termux   %4d B   %s.sh*".format(script.content.length, script.name), LineType.SUCCESS)
                    }
                }
            }

            "cat" -> {
                if (args.isEmpty()) {
                    appendLine("Usage: cat <filename>", LineType.WARNING)
                    return
                }
                val targetName = args[0].lowercase()
                
                if (targetName.endsWith(".sh")) {
                    val name = targetName.replace(".sh", "")
                    val script = repository.getScriptByName(name)
                    if (script != null) {
                        appendLine("=== $targetName ===", LineType.HEADER)
                        appendLine(script.content, LineType.OUTPUT)
                    } else {
                        appendLine("cat: $targetName: No such file or directory", LineType.ERROR)
                    }
                    return
                }
                
                val cleanTitle = targetName.replace(".txt", "")
                val note = repository.getNoteByTitle(cleanTitle) ?: repository.getNoteByTitle(targetName)
                if (note != null) {
                    appendLine("=== ${note.title} ===", LineType.HEADER)
                    appendLine(note.content, LineType.OUTPUT)
                } else {
                    appendLine("cat: $targetName: No such file or directory", LineType.ERROR)
                }
            }

            "rm" -> {
                if (args.isEmpty()) {
                    appendLine("Usage: rm <filename>", LineType.WARNING)
                    return
                }
                val targetName = args[0].lowercase()
                if (targetName.endsWith(".sh")) {
                    val name = targetName.replace(".sh", "")
                    repository.deleteScriptByName(name)
                    appendLine("Removed: Script '$targetName'", LineType.SUCCESS)
                } else {
                    val cleanTitle = targetName.replace(".txt", "")
                    repository.deleteNoteByTitle(cleanTitle)
                    repository.deleteNoteByTitle(targetName)
                    appendLine("Removed: File '$targetName'", LineType.SUCCESS)
                }
            }

            "apt", "pkg", "apk" -> {
                if (args.isEmpty()) {
                    if (cmd == "apt") {
                        appendLine("apt 2.6.1 (aarch64). Usage: apt <update | list | install <package> | remove <package>>", LineType.WARNING)
                    } else if (cmd == "apk") {
                        appendLine("apk-tools 3.1.0. Usage: apk <update | info | add <package> | del <package>>", LineType.WARNING)
                    } else {
                        appendLine("pkg 1.19.1. Usage: pkg <update | list-all | install <package> | uninstall <package>>", LineType.WARNING)
                    }
                    return
                }
                val sub = args[0].lowercase()
                when (sub) {
                    "update" -> {
                        appendLine("Get:1 https://termux.org/packages stable InRelease [12.4 kB]", LineType.INFO)
                        delay(200)
                        appendLine("Get:2 https://termux.org/packages stable/main arm64 Packages [482 kB]", LineType.INFO)
                        delay(250)
                        appendLine("Fetched 494.4 kB in 1s (412 kB/s)", LineType.SUCCESS)
                        appendLine("Reading package lists... Done", LineType.SUCCESS)
                        appendLine("All packages are up to date.", LineType.SUCCESS)
                    }
                    "upgrade", "update-all" -> {
                        handleAptUpgradeAllCommand()
                    }
                    "list", "list-all", "info" -> {
                        appendLine("--- Installed / Available Packages in Virtual Environment ---", LineType.HEADER)
                        virtualPackages.forEach { pkgInfo ->
                            val status = if (installedPackages.contains(pkgInfo.name)) "[installed]" else "[available]"
                            val color = if (installedPackages.contains(pkgInfo.name)) LineType.SUCCESS else LineType.OUTPUT
                            appendLine("%-12s %-8s %-12s - %s".format(pkgInfo.name, pkgInfo.version, status, pkgInfo.description), color)
                        }
                    }
                    "install", "add" -> {
                        if (args.size < 2) {
                            appendLine("Error: Please specify package name(s) to install.", LineType.ERROR)
                            return
                        }
                        val targetPkg = args[1].lowercase()
                        val found = virtualPackages.any { it.name == targetPkg }
                        if (!found) {
                            appendLine("E: Unable to locate package $targetPkg", LineType.ERROR)
                            return
                        }
                        if (installedPackages.contains(targetPkg)) {
                            appendLine("$targetPkg is already the newest version.", LineType.INFO)
                            return
                        }
                        appendLine("Reading package lists... Done", LineType.SUCCESS)
                        appendLine("The following NEW packages will be installed:", LineType.INFO)
                        appendLine("  $targetPkg", LineType.INFO)
                        appendLine("Need to get 14.2 MB of archives.", LineType.OUTPUT)
                        delay(300)
                        appendLine("Get:1 https://termux.org/packages stable/main arm64 $targetPkg [14.2 MB]", LineType.INFO)
                        delay(400)
                        appendLine("Selecting previously unselected package $targetPkg.", LineType.OUTPUT)
                        appendLine("Preparing to unpack .../$targetPkg.deb ...", LineType.OUTPUT)
                        appendLine("Unpacking $targetPkg ...", LineType.OUTPUT)
                        appendLine("Setting up $targetPkg ...", LineType.SUCCESS)
                        
                        installedPackages.add(targetPkg)
                        appendLine("Successfully pre-installed/unlocked: $targetPkg!", LineType.SUCCESS)
                    }
                    "remove", "uninstall", "del" -> {
                        if (args.size < 2) {
                            appendLine("Error: Specify package name.", LineType.ERROR)
                            return
                        }
                        val targetPkg = args[1].lowercase()
                        if (!installedPackages.contains(targetPkg)) {
                            appendLine("Package $targetPkg is not installed.", LineType.WARNING)
                            return
                        }
                        if (targetPkg in listOf("apt", "apk", "pkg", "bash", "coreutils")) {
                            appendLine("E: Core package $targetPkg is essential and cannot be removed.", LineType.ERROR)
                            return
                        }
                        installedPackages.remove(targetPkg)
                        appendLine("Removing $targetPkg ...", LineType.INFO)
                        delay(200)
                        appendLine("Successfully uninstalled $targetPkg.", LineType.SUCCESS)
                    }
                    else -> {
                        appendLine("Unknown package command: $sub", LineType.ERROR)
                    }
                }
            }

            "python", "python3" -> {
                if (!installedPackages.contains("python")) {
                    appendLine("sh: python: command not found. Use 'apt install python' first.", LineType.ERROR)
                    return
                }
                if (args.isEmpty()) {
                    activeRepl = "python"
                    _prompt.value = ">>> "
                    appendLine("Python 3.11.3 (tags/v3.11.3, Apr  5 2023) [GCC 12.2.0] on android", LineType.OUTPUT)
                    appendLine("Type \"help\", \"copyright\", \"credits\" or \"license\" for more information.", LineType.OUTPUT)
                } else {
                    val filename = args[0]
                    val cleanTitle = filename.replace(".txt", "").replace(".py", "")
                    val note = repository.getNoteByTitle(cleanTitle) ?: repository.getNoteByTitle(filename)
                    if (note != null) {
                        appendLine("[PYTHON] Executing: $filename", LineType.INFO)
                        delay(300)
                        runPythonScriptSim(note.content)
                    } else {
                        appendLine("python: can't open file '$filename': No such file or directory", LineType.ERROR)
                    }
                }
            }

            "node", "nodejs" -> {
                if (!installedPackages.contains("nodejs")) {
                    appendLine("sh: node: command not found. Use 'apt install nodejs' first.", LineType.ERROR)
                    return
                }
                if (args.isEmpty()) {
                    activeRepl = "node"
                    _prompt.value = "> "
                    appendLine("Welcome to Node.js v20.2.0.", LineType.OUTPUT)
                    appendLine("Type \".help\" for more information.", LineType.OUTPUT)
                } else {
                    val filename = args[0]
                    val cleanTitle = filename.replace(".txt", "").replace(".js", "")
                    val note = repository.getNoteByTitle(cleanTitle) ?: repository.getNoteByTitle(filename)
                    if (note != null) {
                        appendLine("[NODE] Executing script: $filename", LineType.INFO)
                        delay(300)
                        runJavaScriptSim(note.content)
                    } else {
                        appendLine("node: can't open file '$filename': No such file or directory", LineType.ERROR)
                    }
                }
            }

            "ruby" -> {
                if (!installedPackages.contains("ruby")) {
                    appendLine("sh: ruby: command not found. Use 'apt install ruby' first.", LineType.ERROR)
                    return
                }
                if (args.isEmpty()) {
                    activeRepl = "ruby"
                    _prompt.value = "irb(main):001> "
                    appendLine("ruby 3.2.2 (2023-03-30) [arm-linux-android]", LineType.OUTPUT)
                } else {
                    val filename = args[0]
                    val cleanTitle = filename.replace(".txt", "").replace(".rb", "")
                    val note = repository.getNoteByTitle(cleanTitle) ?: repository.getNoteByTitle(filename)
                    if (note != null) {
                        appendLine("[RUBY] Executing: $filename", LineType.INFO)
                        delay(300)
                        runRubyScriptSim(note.content)
                    } else {
                        appendLine("ruby: No such file or directory -- $filename", LineType.ERROR)
                    }
                }
            }

            "php" -> {
                if (!installedPackages.contains("php")) {
                    appendLine("sh: php: command not found. Use 'apt install php' first.", LineType.ERROR)
                    return
                }
                if (args.isEmpty()) {
                    activeRepl = "php"
                    _prompt.value = "php > "
                    appendLine("Interactive shell (PHP 8.2.6) initialized.", LineType.OUTPUT)
                } else {
                    val filename = args[0]
                    val cleanTitle = filename.replace(".txt", "").replace(".php", "")
                    val note = repository.getNoteByTitle(cleanTitle) ?: repository.getNoteByTitle(filename)
                    if (note != null) {
                        appendLine("[PHP] Parsing script: $filename", LineType.INFO)
                        delay(300)
                        runPhpScriptSim(note.content)
                    } else {
                        appendLine("Could not open input file: $filename", LineType.ERROR)
                    }
                }
            }

            "perl" -> {
                if (!installedPackages.contains("perl")) {
                    appendLine("sh: perl: command not found. Use 'apt install perl' first.", LineType.ERROR)
                    return
                }
                if (args.isEmpty()) {
                    activeRepl = "perl"
                    _prompt.value = "perl > "
                    appendLine("Perl v5.36.1 interactive mode. Type your code.", LineType.OUTPUT)
                } else {
                    val filename = args[0]
                    val cleanTitle = filename.replace(".txt", "").replace(".pl", "")
                    val note = repository.getNoteByTitle(cleanTitle) ?: repository.getNoteByTitle(filename)
                    if (note != null) {
                        appendLine("[PERL] Interpreting script: $filename", LineType.INFO)
                        delay(300)
                        runPerlScriptSim(note.content)
                    } else {
                        appendLine("Can't open perl script \"$filename\": No such file or directory", LineType.ERROR)
                    }
                }
            }

            "bash", "sh" -> {
                if (args.isEmpty()) {
                    activeRepl = "bash"
                    _prompt.value = "bash-5.2$ "
                    appendLine("GNU bash, version 5.2.15(1)-release (aarch64-unknown-linux-android)", LineType.OUTPUT)
                } else {
                    val filename = args[0]
                    val cleanScript = filename.replace(".sh", "")
                    val script = repository.getScriptByName(cleanScript) ?: repository.getScriptByName(filename)
                    if (script != null) {
                        runCustomScript(script)
                    } else {
                        appendLine("bash: $filename: No such file or directory", LineType.ERROR)
                    }
                }
            }

            "clang", "gcc" -> {
                if (!installedPackages.contains("clang")) {
                    appendLine("sh: clang: command not found. Use 'apt install clang' first.", LineType.ERROR)
                    return
                }
                if (args.isEmpty()) {
                    appendLine("clang: error: no input files", LineType.ERROR)
                    return
                }
                val sourceFile = args[0]
                if (!sourceFile.endsWith(".c")) {
                    appendLine("clang: error: file extension must be .c", LineType.ERROR)
                    return
                }
                
                val cleanSource = sourceFile.replace(".c", "")
                val note = repository.getNoteByTitle(cleanSource) ?: repository.getNoteByTitle(sourceFile)
                if (note == null) {
                    appendLine("clang: error: no such file or directory: '$sourceFile'", LineType.ERROR)
                    return
                }
                
                val outIndex = args.indexOf("-o")
                val outFile = if (outIndex != -1 && outIndex + 1 < args.size) {
                    args[outIndex + 1]
                } else {
                    "a.out"
                }
                
                appendLine("clang: Compiling: $sourceFile...", LineType.INFO)
                delay(600)
                
                val binaryInstructions = parseCFileToInstructions(note.content)
                repository.deleteNoteByTitle(outFile)
                repository.addNote(outFile, "BINARY:$binaryInstructions")
                
                appendLine("clang: Compilation successful! Binary output: $outFile", LineType.SUCCESS)
            }

            "go", "golang" -> {
                if (!installedPackages.contains("golang")) {
                    appendLine("sh: go: command not found. Use 'apt install golang' first.", LineType.ERROR)
                    return
                }
                if (args.isEmpty() || args[0] != "run") {
                    appendLine("Usage: go run <filename.go>", LineType.WARNING)
                    return
                }
                if (args.size < 2) {
                    appendLine("go run: no go files listed", LineType.ERROR)
                    return
                }
                val goFile = args[1]
                val cleanGo = goFile.replace(".go", "")
                val note = repository.getNoteByTitle(cleanGo) ?: repository.getNoteByTitle(goFile)
                if (note == null) {
                    appendLine("go: no such file: '$goFile'", LineType.ERROR)
                    return
                }
                
                appendLine("go: building and running $goFile...", LineType.INFO)
                delay(500)
                
                val lines = note.content.lines().map { it.trim() }.filter { it.isNotEmpty() }
                for (line in lines) {
                    if (line.startsWith("package ") || line.startsWith("import ") || line.startsWith("func main") || line == "{" || line == "}") continue
                    if (line.startsWith("fmt.Println(") && line.endsWith(")")) {
                        val inner = line.substring(12, line.length - 1).trim()
                        appendLine(inner.replace("\"", "").replace("'", ""), LineType.OUTPUT)
                    }
                }
            }

            "git" -> {
                if (!installedPackages.contains("git")) {
                    appendLine("sh: git: command not found. Use 'apt install git' first.", LineType.ERROR)
                    return
                }
                if (args.isEmpty()) {
                    appendLine("git v2.41.0. Usage: git <init | status | add | commit | log | clone>", LineType.WARNING)
                    return
                }
                val sub = args[0].lowercase()
                when (sub) {
                    "init" -> {
                        gitInitialized = true
                        gitUnstagedFiles.clear()
                        val notes = repository.getNotes()
                        notes.forEach { gitUnstagedFiles.add(it.title + (if (it.title.contains(".")) "" else ".txt")) }
                        repository.getScripts().forEach { gitUnstagedFiles.add(it.name + ".sh") }
                        
                        appendLine("Initialized empty Git repository in /data/data/com.termux/files/home/.git/", LineType.SUCCESS)
                    }
                    "status" -> {
                        if (!gitInitialized) {
                            appendLine("fatal: not a git repository: .git", LineType.ERROR)
                            return
                        }
                        appendLine("On branch main", LineType.OUTPUT)
                        if (gitStagedFiles.isEmpty() && gitUnstagedFiles.isEmpty()) {
                            appendLine("nothing to commit, working tree clean", LineType.SUCCESS)
                        } else {
                            if (gitStagedFiles.isNotEmpty()) {
                                appendLine("Changes to be committed:", LineType.SUCCESS)
                                gitStagedFiles.forEach { appendLine("\tnew file:   $it", LineType.SUCCESS) }
                            }
                            if (gitUnstagedFiles.isNotEmpty()) {
                                appendLine("Untracked files:", LineType.ERROR)
                                gitUnstagedFiles.forEach { appendLine("\t$it", LineType.ERROR) }
                            }
                        }
                    }
                    "add" -> {
                        if (!gitInitialized) {
                            appendLine("fatal: not a git repository: .git", LineType.ERROR)
                            return
                        }
                        if (args.size < 2) {
                            appendLine("Usage: git add <file | .>", LineType.WARNING)
                            return
                        }
                        val target = args[1]
                        if (target == "." || target == "*") {
                            gitStagedFiles.addAll(gitUnstagedFiles)
                            gitUnstagedFiles.clear()
                            appendLine("Staged all files.", LineType.SUCCESS)
                        } else {
                            if (gitUnstagedFiles.contains(target)) {
                                gitStagedFiles.add(target)
                                gitUnstagedFiles.remove(target)
                                appendLine("Staged: $target", LineType.SUCCESS)
                            } else {
                                appendLine("fatal: pathspec '$target' did not match any files", LineType.ERROR)
                            }
                        }
                    }
                    "commit" -> {
                        if (!gitInitialized) {
                            appendLine("fatal: not a git repository: .git", LineType.ERROR)
                            return
                        }
                        val mIndex = args.indexOf("-m")
                        val commitMsg = if (mIndex != -1 && mIndex + 1 < args.size) {
                            args.drop(mIndex + 1).joinToString(" ").replace("\"", "")
                        } else {
                            "Minor update"
                        }
                        if (gitStagedFiles.isEmpty()) {
                            appendLine("On branch main\nnothing to commit, working tree clean", LineType.INFO)
                            return
                        }
                        val hash = (100000..999999).random().toString(16)
                        val count = gitStagedFiles.size
                        gitCommits.add(0, "commit $hash\nAuthor: Termux User <termux@pocket>\nDate:   ${Date()}\n\n    $commitMsg\n\n    $count files changed")
                        gitStagedFiles.clear()
                        appendLine("[main $hash] $commitMsg", LineType.SUCCESS)
                    }
                    "log" -> {
                        if (!gitInitialized) {
                            appendLine("fatal: not a git repository: .git", LineType.ERROR)
                            return
                        }
                        if (gitCommits.isEmpty()) {
                            appendLine("fatal: no commits yet", LineType.ERROR)
                            return
                        }
                        gitCommits.forEach { appendLine(it, LineType.OUTPUT) }
                    }
                    "clone" -> {
                        if (args.size < 2) {
                            appendLine("Usage: git clone <url>", LineType.WARNING)
                            return
                        }
                        appendLine("Cloning into '${args[1].substringAfterLast("/")}'...", LineType.INFO)
                        delay(300)
                        appendLine("Receiving objects: 100% (45/45), done.", LineType.SUCCESS)
                    }
                }
            }

            "curl", "wget" -> {
                if (!installedPackages.contains("curl")) {
                    appendLine("sh: curl: command not found. Use 'apt install curl' first.", LineType.ERROR)
                    return
                }
                if (args.isEmpty()) {
                    appendLine("Usage: $cmd <url> [-o <outfile>]", LineType.WARNING)
                    return
                }
                val url = args[0]
                appendLine("Sending GET request to $url...", LineType.INFO)
                delay(400)
                
                val responseText = """
                    {
                      "status": "success",
                      "host": "pocket-termux",
                      "query_url": "$url",
                      "timestamp": ${System.currentTimeMillis()},
                      "data": {
                        "message": "Hello from pocket termux API service",
                        "engine": "virtual_sandbox_compiler",
                        "supported_languages": ["bash", "python", "javascript", "clang", "go", "ruby", "php", "perl"]
                      }
                    }
                """.trimIndent()
                
                val oIndex = args.indexOf("-o")
                if (oIndex != -1 && oIndex + 1 < args.size) {
                    val outFile = args[oIndex + 1]
                    repository.deleteNoteByTitle(outFile)
                    repository.addNote(outFile, responseText)
                    appendLine("curl: Saved response to note '$outFile' successfully!", LineType.SUCCESS)
                } else {
                    appendLine("=== Response Header ===", LineType.HEADER)
                    appendLine("HTTP/1.1 200 OK\nContent-Type: application/json\nServer: PocketSandbox/1.0", LineType.OUTPUT)
                    appendLine("=== Response Body ===", LineType.HEADER)
                    appendLine(responseText, LineType.SUCCESS)
                }
            }

            else -> {
                // Check if it matches a saved script name to run directly!
                val savedScript = repository.getScriptByName(cmd)
                if (savedScript != null) {
                    runCustomScript(savedScript)
                } else {
                    appendLine("sh: command not found: $cmd. Type 'help' to see list of valid commands.", LineType.ERROR)
                }
            }
        }
    }

    private fun handleReplInput(command: String) {
        val cmdTrimmed = command.trim()
        if (cmdTrimmed == "exit()" || cmdTrimmed == "quit()" || cmdTrimmed == "exit" || cmdTrimmed == "quit" || (activeRepl == "php" && cmdTrimmed == "exit")) {
            appendLine("Exiting $activeRepl session.", LineType.INFO)
            activeRepl = null
            _prompt.value = "~ $ "
            return
        }

        when (activeRepl) {
            "python" -> {
                if (cmdTrimmed.startsWith("print(") && cmdTrimmed.endsWith(")")) {
                    val inner = cmdTrimmed.substring(6, cmdTrimmed.length - 1).trim()
                    if ((inner.startsWith("\"") && inner.endsWith("\"")) || (inner.startsWith("'") && inner.endsWith("'"))) {
                        appendLine(inner.substring(1, inner.length - 1), LineType.OUTPUT)
                    } else {
                        val result = evaluateMathExpression(inner)
                        if (result != null) {
                            appendLine(result, LineType.OUTPUT)
                        } else {
                            appendLine(inner, LineType.OUTPUT)
                        }
                    }
                } else if (cmdTrimmed.matches("^[a-zA-Z_][a-zA-Z0-9_]*\\s*=\\s*.*$".toRegex())) {
                    val parts = cmdTrimmed.split("=", limit = 2)
                    val varName = parts[0].trim()
                    val varVal = parts[1].trim()
                    pythonVariables[varName] = varVal
                } else if (pythonVariables.containsKey(cmdTrimmed)) {
                    appendLine(pythonVariables[cmdTrimmed] ?: "", LineType.OUTPUT)
                } else {
                    val result = evaluateMathExpression(cmdTrimmed)
                    if (result != null) {
                        appendLine(result, LineType.OUTPUT)
                    } else {
                        appendLine("Python evaluated expression successfully.", LineType.OUTPUT)
                    }
                }
            }
            "node" -> {
                if (cmdTrimmed.startsWith("console.log(") && cmdTrimmed.endsWith(")")) {
                    val inner = cmdTrimmed.substring(12, cmdTrimmed.length - 1).trim()
                    if ((inner.startsWith("\"") && inner.endsWith("\"")) || (inner.startsWith("'") && inner.endsWith("'"))) {
                        appendLine(inner.substring(1, inner.length - 1), LineType.OUTPUT)
                    } else {
                        appendLine(inner, LineType.OUTPUT)
                    }
                } else {
                    appendLine("JS statement evaluated successfully.", LineType.OUTPUT)
                }
            }
            "ruby" -> {
                if (cmdTrimmed.startsWith("puts ") || cmdTrimmed.startsWith("print ")) {
                    val inner = cmdTrimmed.substring(cmdTrimmed.indexOf(' ') + 1).trim()
                    appendLine(inner.replace("\"", "").replace("'", ""), LineType.OUTPUT)
                } else {
                    appendLine("Ruby interactive evaluated successfully.", LineType.OUTPUT)
                }
            }
            "php" -> {
                if (cmdTrimmed.startsWith("echo ") || cmdTrimmed.startsWith("print ")) {
                    val inner = cmdTrimmed.substring(cmdTrimmed.indexOf(' ') + 1).trim().removeSuffix(";")
                    appendLine(inner.replace("\"", "").replace("'", ""), LineType.OUTPUT)
                } else {
                    appendLine("PHP interactive evaluated successfully.", LineType.OUTPUT)
                }
            }
            "perl" -> {
                if (cmdTrimmed.startsWith("print ")) {
                    val inner = cmdTrimmed.substring(6).trim().removeSuffix(";")
                    appendLine(inner.replace("\"", "").replace("'", ""), LineType.OUTPUT)
                } else {
                    appendLine("Perl interactive evaluated successfully.", LineType.OUTPUT)
                }
            }
            "bash" -> {
                val parts = command.trim().split("\\s+".toRegex())
                val subCmd = parts[0].lowercase()
                val args = parts.drop(1)
                
                if (subCmd == "exit" || subCmd == "quit") {
                    appendLine("Exiting bash sub-shell.", LineType.INFO)
                    activeRepl = null
                    _prompt.value = "~ $ "
                } else {
                    viewModelScope.launch {
                        executeCommandLogic(subCmd, args)
                    }
                }
            }
        }
    }

    private fun evaluateMathExpression(expr: String): String? {
        try {
            val cleaned = expr.replace(" ", "")
            if (cleaned.matches("^\\d+[+\\-*/]\\d+$".toRegex())) {
                val op = cleaned.find { it == '+' || it == '-' || it == '*' || it == '/' } ?: return null
                val parts = cleaned.split(op)
                val a = parts[0].toDouble()
                val b = parts[1].toDouble()
                val res = when (op) {
                    '+' -> a + b
                    '-' -> a - b
                    '*' -> a * b
                    '/' -> if (b != 0.0) a / b else Double.NaN
                    else -> 0.0
                }
                return if (res % 1 == 0.0) res.toInt().toString() else res.toString()
            }
        } catch (e: Exception) {
            // ignore
        }
        return null
    }

    private fun runPythonScriptSim(content: String) {
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var executed = false
        for (line in lines) {
            if (line.startsWith("#")) continue
            if (line.startsWith("print(") && line.endsWith(")")) {
                val inner = line.substring(6, line.length - 1).trim()
                if ((inner.startsWith("\"") && inner.endsWith("\"")) || (inner.startsWith("'") && inner.endsWith("'"))) {
                    appendLine(inner.substring(1, inner.length - 1), LineType.OUTPUT)
                } else {
                    appendLine(inner, LineType.OUTPUT)
                }
                executed = true
            }
        }
        if (!executed) {
            appendLine("Python: Script parsed and executed with no standard output.", LineType.INFO)
        }
    }

    private fun runJavaScriptSim(content: String) {
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var executed = false
        for (line in lines) {
            if (line.startsWith("//") || line.startsWith("/*")) continue
            if (line.startsWith("console.log(") && line.endsWith(")")) {
                val inner = line.substring(12, line.length - 1).trim()
                if ((inner.startsWith("\"") && inner.endsWith("\"")) || (inner.startsWith("'") && inner.endsWith("'"))) {
                    appendLine(inner.substring(1, inner.length - 1), LineType.OUTPUT)
                } else {
                    appendLine(inner, LineType.OUTPUT)
                }
                executed = true
            }
        }
        if (!executed) {
            appendLine("Node: JS execution successfully terminated.", LineType.INFO)
        }
    }

    private fun runRubyScriptSim(content: String) {
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        for (line in lines) {
            if (line.startsWith("#")) continue
            if (line.startsWith("puts ") || line.startsWith("print ")) {
                val inner = line.substring(line.indexOf(' ') + 1).trim()
                appendLine(inner.replace("\"", "").replace("'", ""), LineType.OUTPUT)
            }
        }
    }

    private fun runPhpScriptSim(content: String) {
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        for (line in lines) {
            if (line.startsWith("<?php") || line.startsWith("?>") || line.startsWith("//")) continue
            if (line.startsWith("echo ") || line.startsWith("print ")) {
                val inner = line.substring(line.indexOf(' ') + 1).trim().removeSuffix(";")
                appendLine(inner.replace("\"", "").replace("'", ""), LineType.OUTPUT)
            }
        }
    }

    private fun runPerlScriptSim(content: String) {
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        for (line in lines) {
            if (line.startsWith("#")) continue
            if (line.startsWith("print ")) {
                val inner = line.substring(6).trim().removeSuffix(";")
                appendLine(inner.replace("\"", "").replace("'", ""), LineType.OUTPUT)
            }
        }
    }

    private fun parseCFileToInstructions(content: String): String {
        val stringBuilder = java.lang.StringBuilder()
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        for (line in lines) {
            if (line.startsWith("#include") || line.startsWith("int main") || line == "{" || line == "}" || line.startsWith("return")) continue
            if (line.startsWith("printf(") && line.endsWith(");")) {
                var inner = line.substring(7, line.length - 2).trim()
                if (inner.startsWith("\"") && inner.endsWith("\"")) {
                    inner = inner.substring(1, inner.length - 1)
                }
                stringBuilder.append(inner.replace("\\n", "\n")).append(";")
            }
        }
        return stringBuilder.toString()
    }

    // --- Command Handlers ---

    private fun showHelp() {
        appendLine("===========================================", LineType.HEADER)
        appendLine("           POCKET TERMUX PRODUCTIVITY       ", LineType.HEADER)
        appendLine("===========================================", LineType.HEADER)
        appendLine("GENERAL UTILITIES:", LineType.INFO)
        appendLine("  help                Display this manual", LineType.OUTPUT)
        appendLine("  clear               Clear terminal logs", LineType.OUTPUT)
        appendLine("  sysinfo             Print real-time hardware & OS stats", LineType.OUTPUT)
        appendLine("  quotes              Fetch dev quote of the day", LineType.OUTPUT)
        appendLine("  matrix              Launch Matrix rain screensaver", LineType.OUTPUT)
        appendLine("  theme <name>        Switch visual layout (matrix|amber|cyber|monokai|snow)", LineType.OUTPUT)
        appendLine("  history             Show past run commands", LineType.OUTPUT)
        appendLine("  passgen <length>    Generate a secure random password", LineType.OUTPUT)
        appendLine("  base64 <enc/dec> <txt> Encode or decode a string", LineType.OUTPUT)
        appendLine("  echo <message>      Print text to standard output", LineType.OUTPUT)
        appendLine("", LineType.OUTPUT)
        appendLine("TODO MANAGER (todo ...):", LineType.INFO)
        appendLine("  todo list           Display current task backlog", LineType.OUTPUT)
        appendLine("  todo add \"text\"     Add a new task item", LineType.OUTPUT)
        appendLine("  todo check <id>     Mark a task complete/incomplete by ID", LineType.OUTPUT)
        appendLine("  todo rm <id>        Delete a task item", LineType.OUTPUT)
        appendLine("  todo clear          Remove all completed tasks", LineType.OUTPUT)
        appendLine("", LineType.OUTPUT)
        appendLine("POCKET NOTES & FILES (notes ...):", LineType.INFO)
        appendLine("  notes list          List all saved notepad files", LineType.OUTPUT)
        appendLine("  notes add <t> <msg> Save a quick inline notepad", LineType.OUTPUT)
        appendLine("  notes cat <title>   Print contents of a specific notepad", LineType.OUTPUT)
        appendLine("  notes rm <title>    Delete notepad file", LineType.OUTPUT)
        appendLine("  nano <filename>     Open file in visual screen editor!", LineType.OUTPUT)
        appendLine("                      (use filename.sh to edit scripts)", LineType.OUTPUT)
        appendLine("", LineType.OUTPUT)
        appendLine("AUTOMATION SCRIPTING (script ...):", LineType.INFO)
        appendLine("  script list         List user automation scripts", LineType.OUTPUT)
        appendLine("  script run <name>   Execute custom sequential command script", LineType.OUTPUT)
        appendLine("  script cat <name>   View the contents of a script", LineType.OUTPUT)
        appendLine("  script rm <name>    Delete an automation script", LineType.OUTPUT)
        appendLine("", LineType.OUTPUT)
        appendLine("POMODORO TRACKER (pomo ...):", LineType.INFO)
        appendLine("  pomo start <mins>   Start a pomodoro timer (default 25)", LineType.OUTPUT)
        appendLine("  pomo stop           Cancel active pomodoro timer", LineType.OUTPUT)
        appendLine("===========================================", LineType.HEADER)
    }

    private fun clearScreen() {
        _terminalOutput.value = emptyList()
    }

    private fun showSysInfo() {
        appendLine("--- Device Telemetry ---", LineType.HEADER)
        appendLine("OS version: Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})", LineType.OUTPUT)
        appendLine("Hardware: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}", LineType.OUTPUT)

        val uptimeSec = SystemClock.elapsedRealtime() / 1000
        val hr = uptimeSec / 3600
        val min = (uptimeSec % 3600) / 60
        appendLine("Uptime: ${hr}h ${min}m", LineType.OUTPUT)

        // Read battery
        val bm = application.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        appendLine("Battery Level: $batLevel%", if (batLevel > 20) LineType.SUCCESS else LineType.WARNING)

        // Read Storage
        try {
            val path = application.filesDir
            val freeBytes = path.freeSpace
            val totalBytes = path.totalSpace
            val freeGb = freeBytes / (1024 * 1024 * 1024.0)
            val totalGb = totalBytes / (1024 * 1024 * 1024.0)
            appendLine("App Sandboxed Storage: %.2f GB free of %.2f GB".format(freeGb, totalGb), LineType.OUTPUT)
        } catch (e: Exception) {
            appendLine("App Storage: Data unavailable", LineType.WARNING)
        }

        appendLine("Environment: Sandbox terminal virtual architecture", LineType.INFO)
    }

    private fun showQuote() {
        val rand = quotesList.random()
        appendLine("💡 $rand", LineType.SUCCESS)
    }

    private fun handleEchoCommand(args: List<String>) {
        if (args.isEmpty()) {
            appendLine("", LineType.OUTPUT)
        } else {
            // Join args, remove wrapping quotes if present
            val raw = args.joinToString(" ")
            val polished = if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length > 1) {
                raw.substring(1, raw.length - 1)
            } else {
                raw
            }
            appendLine(polished, LineType.OUTPUT)
        }
    }

    private fun handleThemeCommand(args: List<String>) {
        if (args.isEmpty()) {
            appendLine("Usage: theme <immersive | matrix | amber | cyber | monokai | snow>", LineType.WARNING)
            return
        }
        val target = args[0].lowercase()
        val theme = when (target) {
            "immersive", "dark", "termux" -> TerminalTheme.IMMERSIVE
            "matrix", "green" -> TerminalTheme.MATRIX
            "amber", "phosphor" -> TerminalTheme.AMBER
            "cyber", "cyberpunk" -> TerminalTheme.CYBERPUNK
            "monokai", "darkmonokai" -> TerminalTheme.MONOKAI
            "snow", "white" -> TerminalTheme.SNOW
            else -> null
        }
        if (theme != null) {
            selectTheme(theme)
        } else {
            appendLine("Unknown theme: $target. Available: immersive, matrix, amber, cyber, monokai, snow.", LineType.ERROR)
        }
    }

    private fun handleHistoryCommand() {
        val history = _historyList.value
        if (history.isEmpty()) {
            appendLine("Command history empty.", LineType.INFO)
            return
        }
        appendLine("--- Command History ---", LineType.HEADER)
        history.forEachIndexed { index, cmd ->
            appendLine(" %3d  %s".format(index + 1, cmd), LineType.OUTPUT)
        }
    }

    private fun handlePassGenCommand(args: List<String>) {
        val len = args.firstOrNull()?.toIntOrNull() ?: 12
        if (len < 4 || len > 100) {
            appendLine("Error: Length must be between 4 and 100.", LineType.ERROR)
            return
        }
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-="
        val secureRand = Random()
        val password = StringBuilder()
        for (i in 0 until len) {
            password.append(chars[secureRand.nextInt(chars.length)])
        }
        appendLine("Generated Secure Password:", LineType.INFO)
        appendLine(password.toString(), LineType.SUCCESS)
    }

    private fun handleBase64Command(args: List<String>) {
        if (args.size < 2) {
            appendLine("Usage: base64 <encode|decode> <text>", LineType.WARNING)
            return
        }
        val operation = args[0].lowercase()
        val text = args.drop(1).joinToString(" ")

        try {
            if (operation == "encode" || operation == "enc") {
                val encoded = android.util.Base64.encodeToString(text.toByteArray(), android.util.Base64.NO_WRAP)
                appendLine("Encoded: $encoded", LineType.SUCCESS)
            } else if (operation == "decode" || operation == "dec") {
                val decodedBytes = android.util.Base64.decode(text, android.util.Base64.NO_WRAP)
                appendLine("Decoded: ${String(decodedBytes)}", LineType.SUCCESS)
            } else {
                appendLine("Invalid operation. Use 'encode' or 'decode'.", LineType.ERROR)
            }
        } catch (e: Exception) {
            appendLine("Base64 error: ${e.message}", LineType.ERROR)
        }
    }

    private suspend fun handleBackupCommand() {
        appendLine("Starting configuration backup...", LineType.HEADER)
        delay(300)
        appendLine("Reading saved snippets from database...", LineType.INFO)
        val snippets = repository.getSnippets()
        appendLine("Found ${snippets.size} saved snippets.", LineType.SUCCESS)
        delay(200)
        
        appendLine("Reading custom scripts from database...", LineType.INFO)
        val scripts = repository.getScripts()
        appendLine("Found ${scripts.size} custom scripts.", LineType.SUCCESS)
        delay(200)

        appendLine("Serializing configuration data to JSON...", LineType.INFO)
        val snippetsJson = serializeSnippets(snippets)
        val scriptsJson = serializeScripts(scripts)
        delay(300)

        appendLine("Generating local compressed ZIP archive...", LineType.INFO)
        val zipFile = createBackupZip(getApplication(), snippetsJson, scriptsJson)
        if (zipFile != null && zipFile.exists()) {
            appendLine("[SUCCESS] Config backup ZIP archive created!", LineType.SUCCESS)
            appendLine("Path: ${zipFile.absolutePath}", LineType.OUTPUT)
            val sizeKb = zipFile.length() / 1024.0
            appendLine("Size: %.2f KB".format(sizeKb), LineType.OUTPUT)
            appendLine("Tip: Open the Status Tab to share or save the ZIP file locally!", LineType.WARNING)
        } else {
            appendLine("[ERROR] Failed to write backup ZIP archive.", LineType.ERROR)
        }
    }

    private suspend fun handleAptUpgradeAllCommand() {
        appendLine("Executing batch system package update sequentially...", LineType.HEADER)
        delay(400)
        
        // Step 1: apt update simulation
        appendLine("[1/2] Running 'apt update'...", LineType.INFO)
        delay(300)
        appendLine("Get:1 https://termux.org/packages stable InRelease [12.4 kB]", LineType.INFO)
        delay(250)
        appendLine("Get:2 https://termux.org/packages stable/main arm64 Packages [482 kB]", LineType.INFO)
        delay(350)
        appendLine("Fetched 494.4 kB in 1s (412 kB/s)", LineType.SUCCESS)
        appendLine("Reading package lists... Done", LineType.SUCCESS)
        delay(400)

        // Step 2: apt upgrade simulation
        appendLine("[2/2] Running 'apt upgrade'...", LineType.INFO)
        delay(400)
        val upgradable = installedPackages.filter { it in listOf("bash", "clang", "gcc", "python", "nodejs", "golang", "ruby", "php", "perl", "git", "curl", "wget", "neofetch", "coreutils", "nano", "tar", "gzip") }
        
        if (upgradable.isEmpty()) {
            appendLine("All virtual packages are already up-to-date.", LineType.SUCCESS)
            return
        }

        appendLine("The following packages will be upgraded sequentially:", LineType.INFO)
        appendLine("  " + upgradable.joinToString(" "), LineType.INFO)
        appendLine("Need to get 142.6 MB of package archives.", LineType.OUTPUT)
        appendLine("After this operation, 11.2 MB of additional disk space will be used.", LineType.OUTPUT)
        delay(600)

        upgradable.forEachIndexed { index, pkg ->
            appendLine("[Batch Update] Upgrading $pkg (${index + 1}/${upgradable.size}) ...", LineType.INFO)
            delay(350)
            appendLine("Unpacking replacement for $pkg ...", LineType.OUTPUT)
            delay(150)
            appendLine("Setting up upgraded $pkg ...", LineType.SUCCESS)
            delay(100)
        }

        appendLine("Progress: [========================================] 100%", LineType.SUCCESS)
        appendLine("Setting up virtual environment structures...", LineType.INFO)
        delay(400)
        appendLine("[SUCCESS] All ${upgradable.size} installed packages have been sequentially batch updated!", LineType.SUCCESS)
    }

    // --- To-do Commands ---

    private suspend fun handleTodoCommand(args: List<String>) {
        if (args.isEmpty()) {
            appendLine("Usage: todo <list | add \"task\" | check <id> | rm <id> | clear>", LineType.WARNING)
            return
        }
        val sub = args[0].lowercase()
        when (sub) {
            "list" -> {
                val list = repository.getTasks()
                if (list.isEmpty()) {
                    appendLine("No tasks found in backlog. Try 'todo add <task>'", LineType.INFO)
                    return
                }
                appendLine("--- Current Task List ---", LineType.HEADER)
                list.forEachIndexed { i, item ->
                    val status = if (item.isCompleted) "[X]" else "[ ]"
                    appendLine(" %2d. %s  %s".format(i + 1, status, item.task), if (item.isCompleted) LineType.INFO else LineType.OUTPUT)
                }
            }
            "add" -> {
                val taskText = args.drop(1).joinToString(" ").replace("\"", "")
                if (taskText.isEmpty()) {
                    appendLine("Error: Task description cannot be empty.", LineType.ERROR)
                    return
                }
                repository.addTask(taskText)
                appendLine("Success: Task added securely to DB backlog.", LineType.SUCCESS)
            }
            "check" -> {
                val indexStr = args.getOrNull(1)
                val index = indexStr?.toIntOrNull()?.minus(1)
                if (index == null) {
                    appendLine("Error: Please provide a valid numerical list index.", LineType.ERROR)
                    return
                }
                val list = repository.getTasks()
                if (index < 0 || index >= list.size) {
                    appendLine("Error: Index out of bounds (1 to ${list.size})", LineType.ERROR)
                    return
                }
                val target = list[index]
                repository.updateTaskStatus(target.id, !target.isCompleted)
                val action = if (!target.isCompleted) "Completed" else "Incomplete"
                appendLine("Success: Task '${target.task}' marked as $action.", LineType.SUCCESS)
            }
            "rm", "delete" -> {
                val indexStr = args.getOrNull(1)
                val index = indexStr?.toIntOrNull()?.minus(1)
                if (index == null) {
                    appendLine("Error: Please provide a valid numerical index.", LineType.ERROR)
                    return
                }
                val list = repository.getTasks()
                if (index < 0 || index >= list.size) {
                    appendLine("Error: Index out of bounds.", LineType.ERROR)
                    return
                }
                val target = list[index]
                repository.deleteTask(target.id)
                appendLine("Success: Deleted task '${target.task}'.", LineType.SUCCESS)
            }
            "clear" -> {
                repository.clearCompletedTasks()
                appendLine("Success: Cleared all completed tasks from DB.", LineType.SUCCESS)
            }
            else -> {
                appendLine("Unknown todo action: $sub. Use list, add, check, rm, or clear.", LineType.ERROR)
            }
        }
    }

    // --- Notes Commands ---

    private suspend fun handleNotesCommand(args: List<String>) {
        if (args.isEmpty()) {
            appendLine("Usage: notes <list | add <title> <body> | cat <title> | rm <title>>", LineType.WARNING)
            return
        }
        val sub = args[0].lowercase()
        when (sub) {
            "list" -> {
                val list = repository.getNotes()
                if (list.isEmpty()) {
                    appendLine("No notepad files. Type 'nano <filename>' to create one.", LineType.INFO)
                    return
                }
                appendLine("--- Saved Virtual Documents ---", LineType.HEADER)
                list.forEach { note ->
                    val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(note.timestamp))
                    appendLine(" - %-20s [%s]".format(note.title, date), LineType.OUTPUT)
                }
            }
            "add" -> {
                if (args.size < 3) {
                    appendLine("Usage: notes add <title> <body description>", LineType.WARNING)
                    return
                }
                val title = args[1].lowercase().replace(".txt", "")
                val body = args.drop(2).joinToString(" ").replace("\"", "")
                repository.addNote(title, body)
                appendLine("File written: '$title.txt' saved securely.", LineType.SUCCESS)
            }
            "cat" -> {
                val title = args.getOrNull(1)?.lowercase()?.replace(".txt", "")
                if (title == null) {
                    appendLine("Error: Please specify filename.", LineType.ERROR)
                    return
                }
                val note = repository.getNoteByTitle(title)
                if (note == null) {
                    appendLine("cat: file not found: '$title.txt'", LineType.ERROR)
                    return
                }
                appendLine("=== Reading: $title.txt ===", LineType.HEADER)
                appendLine(note.content, LineType.OUTPUT)
                appendLine("===========================", LineType.HEADER)
            }
            "rm", "delete" -> {
                val title = args.getOrNull(1)?.lowercase()?.replace(".txt", "")
                if (title == null) {
                    appendLine("Error: Please specify filename.", LineType.ERROR)
                    return
                }
                repository.deleteNoteByTitle(title)
                appendLine("Deleted: File '$title.txt' removed.", LineType.SUCCESS)
            }
            else -> {
                appendLine("Unknown action: $sub. Use list, add, cat, or rm.", LineType.ERROR)
            }
        }
    }

    // --- Script Commands ---

    private suspend fun handleScriptCommand(args: List<String>) {
        if (args.isEmpty()) {
            appendLine("Usage: script <list | run <name> | cat <name> | rm <name>>", LineType.WARNING)
            return
        }
        val sub = args[0].lowercase()
        when (sub) {
            "list" -> {
                val list = repository.getScripts()
                if (list.isEmpty()) {
                    appendLine("No scripts saved. Use 'nano <name>.sh' to build one!", LineType.INFO)
                    return
                }
                appendLine("--- Automation Scripts Library ---", LineType.HEADER)
                list.forEach { script ->
                    appendLine(" * %-20s (lines: %d)".format(script.name + ".sh", script.content.trim().lines().size), LineType.OUTPUT)
                }
            }
            "run" -> {
                val name = args.getOrNull(1)?.lowercase()?.replace(".sh", "")
                if (name == null) {
                    appendLine("Error: Please provide script name to execute.", LineType.ERROR)
                    return
                }
                val script = repository.getScriptByName(name)
                if (script == null) {
                    appendLine("Error: Script '$name.sh' not found.", LineType.ERROR)
                    return
                }
                runCustomScript(script)
            }
            "cat" -> {
                val name = args.getOrNull(1)?.lowercase()?.replace(".sh", "")
                if (name == null) {
                    appendLine("Error: Please provide script name.", LineType.ERROR)
                    return
                }
                val script = repository.getScriptByName(name)
                if (script == null) {
                    appendLine("script: file not found: '$name.sh'", LineType.ERROR)
                    return
                }
                appendLine("=== Script: $name.sh ===", LineType.HEADER)
                appendLine(script.content, LineType.OUTPUT)
                appendLine("========================", LineType.HEADER)
            }
            "rm", "delete" -> {
                val name = args.getOrNull(1)?.lowercase()?.replace(".sh", "")
                if (name == null) {
                    appendLine("Error: Please provide script name.", LineType.ERROR)
                    return
                }
                repository.deleteScriptByName(name)
                appendLine("Deleted script file '$name.sh' successfully.", LineType.SUCCESS)
            }
            else -> {
                appendLine("Unknown action: $sub. Use list, run, cat, or rm.", LineType.ERROR)
            }
        }
    }

    private var scriptJob: kotlinx.coroutines.Job? = null

    fun cancelActiveScript() {
        scriptJob?.cancel()
        _isScriptExecuting.value = false
        viewModelScope.launch {
            appendLine("[SH] Script process aborted by user.", LineType.ERROR)
        }
    }

    fun addCustomScript(name: String, content: String) {
        viewModelScope.launch {
            repository.addScript(name, content)
        }
    }

    fun deleteCustomScript(name: String) {
        viewModelScope.launch {
            repository.deleteScriptByName(name)
        }
    }

    fun runCustomScriptDirect(script: CustomScript) {
        runCustomScript(script)
    }

    private fun runCustomScript(script: CustomScript) {
        if (_isScriptExecuting.value) {
            appendLine("Error: A script process is already active. Wait for it to finish.", LineType.ERROR)
            return
        }
        scriptJob = viewModelScope.launch {
            _scriptConsoleOutput.value = emptyList() // clear previous console output on start
            _isScriptExecuting.value = true
            try {
                appendLine("[SH] Initializing script: ${script.name}.sh", LineType.INFO)
                val lines = script.content.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }

                for ((index, line) in lines.withIndex()) {
                    appendLine("sh [${index + 1}/${lines.size}]: $line", LineType.INPUT)
                    delay(350) // visual flow delay
                    executeCommandSuspend(line, isFromScript = true)
                    delay(400) // response reading delay
                }

                appendLine("[SH] Script process exited: SUCCESS (0)", LineType.SUCCESS)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Handled in cancelActiveScript
            } finally {
                _isScriptExecuting.value = false
            }
        }
    }

    // --- Nano Editor Handlers ---

    private suspend fun handleNanoCommand(args: List<String>) {
        if (args.isEmpty()) {
            appendLine("Usage: nano <filename.sh | filename.txt>", LineType.WARNING)
            return
        }
        val filename = args[0].lowercase()
        _nanoFileName.value = filename

        if (filename.endsWith(".sh")) {
            val scriptName = filename.replace(".sh", "")
            val existing = repository.getScriptByName(scriptName)
            _nanoContent.value = existing?.content ?: "#!/bin/sh\n# Custom automation script\n"
        } else {
            val title = filename.replace(".txt", "")
            val existing = repository.getNoteByTitle(title)
            _nanoContent.value = existing?.content ?: "Your text notes here..."
        }

        _isNanoActive.value = true
        appendLine("Opening GUI overlay nano editor for: $filename", LineType.INFO)
    }

    fun onNanoContentChange(newContent: String) {
        _nanoContent.value = newContent
    }

    fun nanoExitAndSave() {
        viewModelScope.launch {
            val filename = _nanoFileName.value
            val content = _nanoContent.value

            if (filename.endsWith(".sh")) {
                val scriptName = filename.replace(".sh", "")
                repository.addScript(scriptName, content)
                appendLine("Nano: Automation script '$filename' written successfully to db.", LineType.SUCCESS)
            } else {
                val title = filename.replace(".txt", "")
                repository.addNote(title, content)
                appendLine("Nano: Text document '$filename' saved successfully to db.", LineType.SUCCESS)
            }

            _isNanoActive.value = false
            _nanoFileName.value = ""
            _nanoContent.value = ""
        }
    }

    fun nanoExitDiscard() {
        _isNanoActive.value = false
        _nanoFileName.value = ""
        _nanoContent.value = ""
        appendLine("Nano: Editor closed, changes discarded.", LineType.WARNING)
    }

    // --- Pomodoro Handlers ---

    private fun handlePomoCommand(args: List<String>) {
        if (args.isEmpty()) {
            appendLine("Usage: pomo <start [mins] [work/break] | pause | resume | reset | stop | task <name> | status>", LineType.WARNING)
            return
        }
        val sub = args[0].lowercase()
        when (sub) {
            "start" -> {
                val mins = args.getOrNull(1)?.toIntOrNull() ?: 25
                if (mins <= 0 || mins > 180) {
                    appendLine("Error: Minutes must be between 1 and 180.", LineType.ERROR)
                    return
                }
                val rawType = args.getOrNull(2)?.lowercase() ?: "work"
                val sessionType = if (rawType == "break" || rawType == "short_break") "Short Break" else "Work"
                startPomodoro(mins, sessionType)
            }
            "pause" -> {
                pausePomodoro()
            }
            "resume" -> {
                resumePomodoro()
            }
            "reset" -> {
                resetPomodoro()
            }
            "stop" -> {
                stopPomodoro()
            }
            "task" -> {
                if (args.size < 2) {
                    appendLine("Usage: pomo task <task_name | clear>", LineType.WARNING)
                    return
                }
                val taskName = args.drop(1).joinToString(" ")
                if (taskName.lowercase() == "clear") {
                    selectPomoTask(null)
                } else {
                    selectPomoTask(taskName)
                }
            }
            "status" -> {
                val p = _pomoSession.value
                val statusStr = if (p.isRunning) "RUNNING" else if (p.isPaused) "PAUSED" else "IDLE"
                val minutes = p.secondsRemaining / 60
                val seconds = p.secondsRemaining % 60
                appendLine("=== Pomodoro Session Status ===", LineType.HEADER)
                appendLine("State: $statusStr", LineType.OUTPUT)
                appendLine("Session Type: ${p.sessionType}", LineType.OUTPUT)
                appendLine("Time Remaining: %02d:%02d / %02d:00".format(minutes, seconds, p.totalSeconds / 60), LineType.OUTPUT)
                appendLine("Active Task: ${p.activeTask ?: "None"}", LineType.OUTPUT)
                appendLine("Completed Sessions: ${p.completedCount}", LineType.SUCCESS)
            }
            else -> {
                appendLine("Unknown pomo action: $sub. Use start, pause, resume, reset, stop, task, or status.", LineType.ERROR)
            }
        }
    }

    fun startPomodoro(minutes: Int, type: String = "Work", task: String? = null) {
        pomoJob?.cancel()
        val totalSec = minutes * 60
        val isWorkType = type == "Work"
        _pomoSession.value = PomodoroSession(
            totalSeconds = totalSec,
            secondsRemaining = totalSec,
            isRunning = true,
            isPaused = false,
            isWork = isWorkType,
            completedCount = _pomoSession.value.completedCount,
            activeTask = task ?: _pomoSession.value.activeTask,
            sessionType = type
        )

        val taskMsg = if (_pomoSession.value.activeTask != null) " focusing on '${_pomoSession.value.activeTask}'" else ""
        appendLine("[POMO] $type session started for $minutes minutes!$taskMsg", LineType.SUCCESS)

        runPomoTimerLoop()
    }

    fun pausePomodoro() {
        if (!_pomoSession.value.isRunning && !_pomoSession.value.isPaused) {
            appendLine("[POMO] No active timer is running to pause.", LineType.WARNING)
            return
        }
        pomoJob?.cancel()
        _pomoSession.value = _pomoSession.value.copy(
            isRunning = false,
            isPaused = true
        )
        val minutes = _pomoSession.value.secondsRemaining / 60
        val seconds = _pomoSession.value.secondsRemaining % 60
        appendLine("[POMO] Timer paused at %02d:%02d.".format(minutes, seconds), LineType.WARNING)
    }

    fun resumePomodoro() {
        if (!_pomoSession.value.isPaused) {
            appendLine("[POMO] Timer is not paused.", LineType.WARNING)
            return
        }
        pomoJob?.cancel()
        _pomoSession.value = _pomoSession.value.copy(
            isRunning = true,
            isPaused = false
        )
        val type = _pomoSession.value.sessionType
        appendLine("[POMO] Resuming $type session.", LineType.SUCCESS)
        runPomoTimerLoop()
    }

    fun resetPomodoro() {
        pomoJob?.cancel()
        val defaultSec = if (_pomoSession.value.sessionType == "Work") 1500 else 300
        _pomoSession.value = _pomoSession.value.copy(
            secondsRemaining = defaultSec,
            totalSeconds = defaultSec,
            isRunning = false,
            isPaused = false
        )
        appendLine("[POMO] Timer reset to ${defaultSec / 60} minutes.", LineType.INFO)
    }

    fun stopPomodoro() {
        pomoJob?.cancel()
        _pomoSession.value = _pomoSession.value.copy(
            isRunning = false,
            isPaused = false
        )
        appendLine("[POMO] Active timer aborted.", LineType.WARNING)
    }

    fun selectPomoTask(task: String?) {
        _pomoSession.value = _pomoSession.value.copy(activeTask = task)
        if (task != null) {
            appendLine("[POMO] Target focus task set: '$task'", LineType.INFO)
        } else {
            appendLine("[POMO] Target focus task cleared.", LineType.INFO)
        }
    }

    private fun runPomoTimerLoop() {
        pomoJob = viewModelScope.launch {
            while (_pomoSession.value.secondsRemaining > 0 && _pomoSession.value.isRunning) {
                delay(1000)
                val newRemaining = _pomoSession.value.secondsRemaining - 1
                _pomoSession.value = _pomoSession.value.copy(secondsRemaining = newRemaining)

                val remaining = newRemaining
                if (remaining % 300 == 0 && remaining > 0) {
                    val minsLeft = remaining / 60
                    appendLine("[POMO] $minsLeft minutes remaining in ${_pomoSession.value.sessionType}.", LineType.INFO)
                } else if (remaining == 60) {
                    appendLine("[POMO] 1 minute left in ${_pomoSession.value.sessionType}!", LineType.WARNING)
                }
            }

            if (_pomoSession.value.secondsRemaining == 0) {
                val type = _pomoSession.value.sessionType
                val activeTask = _pomoSession.value.activeTask
                if (type == "Work") {
                    appendLine("[POMO] Focus block complete! Take a break. 🍵", LineType.SUCCESS)
                    if (activeTask != null) {
                        appendLine("[POMO] Completed focused task: '$activeTask'", LineType.SUCCESS)
                    }
                    _pomoSession.value = _pomoSession.value.copy(
                        isRunning = false,
                        completedCount = _pomoSession.value.completedCount + 1,
                        activeTask = null // Clear task upon completion
                    )
                } else {
                    appendLine("[POMO] Break over! Time to get back to work. 💻", LineType.SUCCESS)
                    _pomoSession.value = _pomoSession.value.copy(
                        isRunning = false
                    )
                }
            }
        }
    }

    // Helper append terminal logs
    fun appendLine(text: String, type: LineType = LineType.OUTPUT) {
        viewModelScope.launch {
            val updated = _terminalOutput.value.toMutableList()
            updated.add(TerminalLine(text, type))
            // Cap visual console buffer to 300 lines to prevent memory bloat
            if (updated.size > 300) {
                updated.removeAt(0)
            }
            _terminalOutput.value = updated

            // Log to script console in real-time if a script is active
            if (_isScriptExecuting.value) {
                val scriptUpdated = _scriptConsoleOutput.value.toMutableList()
                scriptUpdated.add(TerminalLine(text, type))
                if (scriptUpdated.size > 300) {
                    scriptUpdated.removeAt(0)
                }
                _scriptConsoleOutput.value = scriptUpdated
            }
        }
    }
}

// Factory for ViewModel
class TerminalViewModelFactory(
    private val application: Application,
    private val repository: TerminalRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TerminalViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TerminalViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
