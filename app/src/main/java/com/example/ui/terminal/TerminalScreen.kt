package com.example.ui.terminal

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CustomScript
import com.example.data.PocketNote
import com.example.data.TodoTask
import com.example.data.CommandSnippet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    modifier: Modifier = Modifier
) {
    val terminalOutput by viewModel.terminalOutput.collectAsStateWithLifecycle()
    val inputBuffer by viewModel.inputBuffer.collectAsStateWithLifecycle()
    val theme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    val isMatrixActive by viewModel.isMatrixActive.collectAsStateWithLifecycle()
    val isScriptExecuting by viewModel.isScriptExecuting.collectAsStateWithLifecycle()
    val isNanoActive by viewModel.isNanoActive.collectAsStateWithLifecycle()
    val pomoSession by viewModel.pomoSession.collectAsStateWithLifecycle()

    val colors = remember(theme) { getColorsForTheme(theme) }

    // Overlays take precedence
    if (isMatrixActive) {
        MatrixRain(colors = colors, onExit = { viewModel.toggleMatrix(false) })
        return
    }

    if (isNanoActive) {
        NanoEditor(viewModel = viewModel, colors = colors)
        return
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val isTablet = maxWidth > 720.dp
        var activeTabMobile by remember { mutableStateOf(0) } // 0: CLI, 1: Scripts, 2: Tasks, 3: Files, 4: Status

        Column(modifier = Modifier.fillMaxSize()) {
            // Header Stats Bar
            TerminalHeader(
                pomoSession = pomoSession,
                isScriptExecuting = isScriptExecuting,
                viewModel = viewModel,
                colors = colors,
                theme = theme,
                isTablet = isTablet,
                activeTabMobile = activeTabMobile,
                onTabChangeMobile = { activeTabMobile = it }
            )

            HorizontalDivider(color = Color(0xFF49454F).copy(alpha = 0.5f), thickness = 1.dp)

            // Split or Dual Panel
            if (isTablet) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Left Column: Interactive Terminal CLI (55% width)
                    Column(modifier = Modifier.weight(0.55f).fillMaxHeight()) {
                        TerminalConsoleCard(colors = colors) {
                            TerminalConsole(
                                terminalOutput = terminalOutput,
                                inputBuffer = inputBuffer,
                                colors = colors,
                                viewModel = viewModel
                            )
                        }
                    }

                    // Vertical Divider
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(Color(0xFF49454F).copy(alpha = 0.5f))
                    )

                    // Right Column: Dashboard GUI (45% width)
                    Column(modifier = Modifier.weight(0.45f).fillMaxHeight()) {
                        DashboardPanel(viewModel = viewModel, colors = colors, onExecuteSnippet = { activeTabMobile = 0 })
                    }
                }
            } else {
                // Mobile layout: swap between Terminal Console and Dashboard tabs
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (activeTabMobile) {
                        0 -> {
                            TerminalConsoleCard(colors = colors) {
                                TerminalConsole(
                                    terminalOutput = terminalOutput,
                                    inputBuffer = inputBuffer,
                                    colors = colors,
                                    viewModel = viewModel
                                )
                            }
                        }
                        1 -> Box(modifier = Modifier.fillMaxSize().padding(12.dp)) { SnippetsTab(viewModel = viewModel, colors = colors, onExecuteSnippet = { activeTabMobile = 0 }) }
                        2 -> Box(modifier = Modifier.fillMaxSize().padding(12.dp)) { ScriptsTab(viewModel = viewModel, colors = colors) }
                        3 -> Box(modifier = Modifier.fillMaxSize().padding(12.dp)) { TasksTab(viewModel = viewModel, colors = colors) }
                        4 -> Box(modifier = Modifier.fillMaxSize().padding(12.dp)) { FilesTab(viewModel = viewModel, colors = colors) }
                        5 -> Box(modifier = Modifier.fillMaxSize().padding(12.dp)) { StatusTab(viewModel = viewModel, colors = colors) }
                    }
                }

                // Bottom Navigation Bar matching Immersive UI Design HTML
                BottomNavBar(
                    activeTab = activeTabMobile,
                    onTabSelected = { activeTabMobile = it },
                    colors = colors
                )
            }
        }
    }
}

@Composable
fun TerminalConsoleCard(
    colors: ConsoleColors,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        border = BorderStroke(1.dp, Color(0xFF49454F)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Mac-style command bar header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF49454F).copy(alpha = 0.3f))
                    .border(BorderStroke(0.dp, Color.Transparent))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFFFF5F56), androidx.compose.foundation.shape.CircleShape))
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFFFFBD2E), androidx.compose.foundation.shape.CircleShape))
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFF27C93F), androidx.compose.foundation.shape.CircleShape))
                }
                Text(
                    text = "CONSOLE",
                    color = colors.text.copy(alpha = 0.6f),
                    fontFamily = colors.fontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.width(30.dp))
            }

            Box(modifier = Modifier.weight(1f)) {
                content()
            }
        }
    }
}

@Composable
fun BottomNavBar(
    activeTab: Int,
    onTabSelected: (Int) -> Unit,
    colors: ConsoleColors
) {
    Surface(
        color = Color(0xFF1C1B1F),
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, Color(0xFF49454F).copy(alpha = 0.5f)), shape = RectangleShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val items = listOf(
                Triple("Home", Icons.Default.Terminal, 0),
                Triple("Snippets", Icons.Default.Bolt, 1),
                Triple("Scripts", Icons.Default.Code, 2),
                Triple("Tasks", Icons.Default.PlaylistAddCheck, 3),
                Triple("Files", Icons.Default.Description, 4),
                Triple("Status", Icons.Default.Info, 5)
            )

            items.forEach { (label, icon, index) ->
                val isActive = activeTab == index
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTabSelected(index) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (isActive) Color(0xFFE8DEF8) else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isActive) Color(0xFF1D192B) else Color(0xFFCAC4D0),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = label,
                        color = if (isActive) Color.White else Color(0xFFCAC4D0),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                    )
                }
            }
        }
    }
}

@Composable
fun TerminalHeader(
    pomoSession: PomodoroSession,
    isScriptExecuting: Boolean,
    viewModel: TerminalViewModel,
    colors: ConsoleColors,
    theme: TerminalTheme,
    isTablet: Boolean,
    activeTabMobile: Int,
    onTabChangeMobile: (Int) -> Unit
) {
    var themeMenuExpanded by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Title & Online Status
        Column {
            Text(
                text = "Own Termux",
                color = Color(0xFFD0BCFF),
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF4ADE80).copy(alpha = alpha), androidx.compose.foundation.shape.CircleShape)
                )
                Text(
                    text = "SESSION_01: ACTIVE",
                    color = Color(0xFF4ADE80),
                    fontFamily = colors.fontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Pomo Countdown Header Badge
            if (pomoSession.isRunning) {
                val minutes = pomoSession.secondsRemaining / 60
                val seconds = pomoSession.secondsRemaining % 60
                val formattedTime = "%02d:%02d".format(minutes, seconds)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(colors.error.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .border(1.dp, colors.error.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Pomo Icon",
                        tint = colors.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "pomo: $formattedTime",
                        color = colors.error,
                        fontFamily = colors.fontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Circular Settings / Palette Selector Button
            Box {
                IconButton(
                    onClick = { themeMenuExpanded = true },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF353439), androidx.compose.foundation.shape.CircleShape)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Select Theme",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = themeMenuExpanded,
                    onDismissRequest = { themeMenuExpanded = false },
                    modifier = Modifier.background(colors.cardBackground)
                ) {
                    TerminalTheme.values().forEach { t ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = t.displayName,
                                    color = if (t == theme) colors.primary else colors.text,
                                    fontFamily = colors.fontFamily,
                                    fontSize = 13.sp
                                )
                            },
                            onClick = {
                                viewModel.selectTheme(t)
                                themeMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalConsole(
    terminalOutput: List<TerminalLine>,
    inputBuffer: String,
    colors: ConsoleColors,
    viewModel: TerminalViewModel
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()

    // Auto-scroll when output adds lines
    LaunchedEffect(terminalOutput.size) {
        if (terminalOutput.isNotEmpty()) {
            listState.animateScrollToItem(terminalOutput.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Output Logs Area
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            items(terminalOutput) { line ->
                val color = when (line.type) {
                    LineType.INPUT -> colors.primary
                    LineType.OUTPUT -> colors.text
                    LineType.ERROR -> colors.error
                    LineType.SUCCESS -> colors.success
                    LineType.INFO -> colors.info
                    LineType.HEADER -> colors.primary
                    LineType.WARNING -> colors.warning
                }
                val prefix = if (line.type == LineType.INPUT) "" else ""
                Text(
                    text = prefix + line.text,
                    color = color,
                    fontFamily = colors.fontFamily,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }

        // Prompt Input Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .border(1.dp, colors.primary.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = prompt,
                color = colors.primary,
                fontFamily = colors.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            BasicTextField(
                value = inputBuffer,
                onValueChange = { viewModel.onInputChange(it) },
                textStyle = TextStyle(
                    color = colors.text,
                    fontFamily = colors.fontFamily,
                    fontSize = 14.sp
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.cursorColor),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Go,
                    autoCorrectEnabled = false
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        viewModel.executeCommandBuffer()
                        keyboardController?.hide()
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("terminal_input_field")
                    .padding(horizontal = 4.dp)
            )

            // Exec Run Button
            IconButton(
                onClick = {
                    viewModel.executeCommandBuffer()
                    keyboardController?.hide()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Execute",
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Visual Auxiliary Keyboard Buttons for Easy Mobile CLI Typing
        AuxiliaryKeyboard(viewModel = viewModel, inputBuffer = inputBuffer, colors = colors)
    }
}

@Composable
fun AuxiliaryKeyboard(
    viewModel: TerminalViewModel,
    inputBuffer: String,
    colors: ConsoleColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.cardBackground)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Tab Autocomplete suggestion
        fun runTab() {
            if (inputBuffer.isEmpty()) return
            val commands = listOf("help", "clear", "sysinfo", "quotes", "matrix", "todo ", "notes ", "script ", "nano ", "pomo ", "passgen ", "base64 ", "theme ")
            val match = commands.firstOrNull { it.startsWith(inputBuffer.trim()) }
            if (match != null) {
                viewModel.onInputChange(match)
            }
        }

        // Grid of quick buttons
        val btnModifier = Modifier
            .weight(1f)
            .padding(horizontal = 2.dp)
            .height(34.dp)

        Button(
            onClick = { runTab() },
            colors = ButtonDefaults.buttonColors(containerColor = colors.background),
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(0.dp),
            modifier = btnModifier
        ) {
            Text("TAB", color = colors.primary, fontFamily = colors.fontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = { viewModel.historyUp() },
            colors = ButtonDefaults.buttonColors(containerColor = colors.background),
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(0.dp),
            modifier = btnModifier
        ) {
            Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = "Up", tint = colors.primary, modifier = Modifier.size(16.dp))
        }

        Button(
            onClick = { viewModel.historyDown() },
            colors = ButtonDefaults.buttonColors(containerColor = colors.background),
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(0.dp),
            modifier = btnModifier
        ) {
            Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = "Down", tint = colors.primary, modifier = Modifier.size(16.dp))
        }

        Button(
            onClick = {
                // Ctrl+C implementation: Cancels scripts, cancels pomodoros, cancels Matrix
                viewModel.toggleMatrix(false)
                viewModel.stopPomodoro()
                viewModel.appendLine("^C Process terminated.", LineType.WARNING)
            },
            colors = ButtonDefaults.buttonColors(containerColor = colors.background),
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(0.dp),
            modifier = btnModifier
        ) {
            Text("CTRL+C", color = colors.error, fontFamily = colors.fontFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = {
                viewModel.executeCommand("clear")
            },
            colors = ButtonDefaults.buttonColors(containerColor = colors.background),
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(0.dp),
            modifier = btnModifier
        ) {
            Text("CLEAR", color = colors.text, fontFamily = colors.fontFamily, fontSize = 10.sp)
        }
    }
}

@Composable
fun DashboardPanel(
    viewModel: TerminalViewModel,
    colors: ConsoleColors,
    onExecuteSnippet: () -> Unit
) {
    var dashboardSubTab by remember { mutableStateOf(0) } // 0: Snippets, 1: Scripts, 2: Tasks, 3: Files, 4: Status

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background.copy(alpha = 0.95f))
        ) {
            // Tab Headers
            TabRow(
                selectedTabIndex = dashboardSubTab,
                containerColor = colors.cardBackground,
                contentColor = colors.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[dashboardSubTab]),
                        color = colors.primary
                    )
                }
            ) {
                val tabs = listOf("Snippets", "Scripts", "Tasks", "Files", "Status")
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = dashboardSubTab == i,
                        onClick = { dashboardSubTab = i },
                        text = {
                            Text(
                                text = title,
                                color = if (dashboardSubTab == i) colors.primary else colors.text.copy(alpha = 0.6f),
                                fontFamily = colors.fontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 84.dp)
            ) {
                when (dashboardSubTab) {
                    0 -> SnippetsTab(viewModel = viewModel, colors = colors, onExecuteSnippet = onExecuteSnippet)
                    1 -> ScriptsTab(viewModel = viewModel, colors = colors)
                    2 -> TasksTab(viewModel = viewModel, colors = colors)
                    3 -> FilesTab(viewModel = viewModel, colors = colors)
                    4 -> StatusTab(viewModel = viewModel, colors = colors)
                }
            }
        }

        // Floating Action Buttons Dock
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.cardBackground.copy(alpha = 0.92f))
                .border(BorderStroke(1.dp, colors.primary.copy(alpha = 0.25f)), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickActionButton(
                    icon = Icons.Default.DeleteSweep,
                    label = "Clean Cache",
                    colors = colors,
                    testTag = "quick_clear_cache",
                    onClick = { viewModel.executeCommand("clean-cache") }
                )
                QuickActionButton(
                    icon = Icons.Default.Refresh,
                    label = "Update Repos",
                    colors = colors,
                    testTag = "quick_update_repos",
                    onClick = { viewModel.executeCommand("update-repos") }
                )
                QuickActionButton(
                    icon = Icons.Default.Build,
                    label = "Diagnose",
                    colors = colors,
                    testTag = "quick_diagnose",
                    onClick = { viewModel.executeCommand("sys-diagnose") }
                )
                QuickActionButton(
                    icon = Icons.Default.Info,
                    label = "Neofetch",
                    colors = colors,
                    testTag = "quick_neofetch",
                    onClick = { viewModel.executeCommand("neofetch") }
                )
            }
        }
    }
}

@Composable
fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    colors: ConsoleColors,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = colors.primary,
            contentColor = colors.background,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .size(42.dp)
                .testTag(testTag),
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 4.dp,
                pressedElevation = 8.dp
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = colors.background
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = colors.text.copy(alpha = 0.8f),
            fontFamily = colors.fontFamily,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetsTab(
    viewModel: TerminalViewModel,
    colors: ConsoleColors,
    onExecuteSnippet: () -> Unit
) {
    val snippets by viewModel.allSnippets.collectAsStateWithLifecycle(initialValue = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    
    // Form fields state
    var showForm by remember { mutableStateOf(false) }
    var editingSnippet by remember { mutableStateOf<CommandSnippet?>(null) }
    
    var formTitle by remember { mutableStateOf("") }
    var formDescription by remember { mutableStateOf("") }
    var formCommand by remember { mutableStateOf("") }
    var formCategory by remember { mutableStateOf("Utilities") }

    val context = LocalContext.current

    // Launch side effect when editingSnippet changes to populate form
    LaunchedEffect(editingSnippet) {
        if (editingSnippet != null) {
            formTitle = editingSnippet!!.title
            formDescription = editingSnippet!!.description
            formCommand = editingSnippet!!.command
            formCategory = editingSnippet!!.category
            showForm = true
        } else {
            formTitle = ""
            formDescription = ""
            formCommand = ""
            formCategory = "Utilities"
        }
    }

    val categories = listOf("All", "Utilities", "Languages", "Network", "VCS", "Custom")

    // Filter snippets
    val filteredSnippets = snippets.filter { snippet ->
        val matchesSearch = snippet.title.contains(searchQuery, ignoreCase = true) ||
                snippet.description.contains(searchQuery, ignoreCase = true) ||
                snippet.command.contains(searchQuery, ignoreCase = true)
        val matchesCategory = selectedCategory == "All" || snippet.category == selectedCategory
        matchesSearch && matchesCategory
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Command Snippets",
                color = colors.primary,
                fontFamily = colors.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Button(
                onClick = {
                    editingSnippet = null
                    showForm = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Snippet", tint = colors.background, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Snippet", color = colors.background, fontFamily = colors.fontFamily, fontSize = 11.sp)
            }
        }

        if (showForm) {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        text = if (editingSnippet != null) "Edit Snippet" else "Create New Snippet",
                        color = colors.primary,
                        fontFamily = colors.fontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = formTitle,
                        onValueChange = { formTitle = it },
                        textStyle = TextStyle(color = colors.text, fontFamily = colors.fontFamily, fontSize = 12.sp),
                        label = { Text("Title", color = colors.text.copy(alpha = 0.5f), fontFamily = colors.fontFamily, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.primary.copy(alpha = 0.4f),
                            cursorColor = colors.primary
                        ),
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = formDescription,
                        onValueChange = { formDescription = it },
                        textStyle = TextStyle(color = colors.text, fontFamily = colors.fontFamily, fontSize = 12.sp),
                        label = { Text("Description", color = colors.text.copy(alpha = 0.5f), fontFamily = colors.fontFamily, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.primary.copy(alpha = 0.4f),
                            cursorColor = colors.primary
                        ),
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = formCommand,
                        onValueChange = { formCommand = it },
                        textStyle = TextStyle(color = colors.text, fontFamily = colors.fontFamily, fontSize = 12.sp),
                        label = { Text("Command / Script", color = colors.text.copy(alpha = 0.5f), fontFamily = colors.fontFamily, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.primary.copy(alpha = 0.4f),
                            cursorColor = colors.primary
                        ),
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Category",
                        color = colors.text.copy(alpha = 0.6f),
                        fontFamily = colors.fontFamily,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val categoriesForForm = listOf("Utilities", "Languages", "Network", "VCS", "Custom")
                        categoriesForForm.forEach { cat ->
                            val isSelected = formCategory == cat
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isSelected) colors.primary.copy(alpha = 0.15f) else Color.Transparent,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) colors.primary else colors.text.copy(alpha = 0.2f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable { formCategory = cat }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSelected) colors.primary else colors.text.copy(alpha = 0.6f),
                                    fontFamily = colors.fontFamily,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                showForm = false
                                editingSnippet = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.background),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.height(32.dp).padding(end = 8.dp)
                        ) {
                            Text("Cancel", color = colors.text, fontFamily = colors.fontFamily, fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                if (formTitle.trim().isNotEmpty() && formCommand.trim().isNotEmpty()) {
                                    if (editingSnippet != null) {
                                        // Edit snippet
                                        viewModel.addSnippet(
                                            title = formTitle.trim(),
                                            description = formDescription.trim(),
                                            command = formCommand.trim(),
                                            category = formCategory,
                                            id = editingSnippet!!.id
                                        )
                                    } else {
                                        // Save snippet
                                        viewModel.addSnippet(
                                            title = formTitle.trim(),
                                            description = formDescription.trim(),
                                            command = formCommand.trim(),
                                            category = formCategory
                                        )
                                    }
                                    showForm = false
                                    editingSnippet = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Save", color = colors.background, fontFamily = colors.fontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            textStyle = TextStyle(color = colors.text, fontFamily = colors.fontFamily, fontSize = 12.sp),
            placeholder = { Text("Search snippets...", color = colors.text.copy(alpha = 0.4f), fontFamily = colors.fontFamily, fontSize = 12.sp) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = colors.text.copy(alpha = 0.4f), modifier = Modifier.size(16.dp)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.primary.copy(alpha = 0.4f),
                cursorColor = colors.primary
            ),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(bottom = 6.dp),
            singleLine = true
        )

        // Filter chips row
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { cat ->
                val isSelected = selectedCategory == cat
                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) colors.primary.copy(alpha = 0.2f) else colors.cardBackground,
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            1.dp,
                            if (isSelected) colors.primary else Color(0xFF49454F).copy(alpha = 0.4f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) colors.primary else colors.text.copy(alpha = 0.7f),
                        fontFamily = colors.fontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (filteredSnippets.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No snippets found.", color = colors.text.copy(alpha = 0.4f), fontFamily = colors.fontFamily, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredSnippets) { snippet ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                        border = BorderStroke(1.dp, Color(0xFF49454F).copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Category Tag
                                    val tagColor = when (snippet.category) {
                                        "VCS" -> Color(0xFFF2B8B5)
                                        "Utilities" -> Color(0xFFA8C7FA)
                                        "Languages" -> Color(0xFFC4EED0)
                                        "Network" -> Color(0xFFF7C59F)
                                        else -> colors.primary.copy(alpha = 0.6f)
                                    }
                                    Text(
                                        text = snippet.category.uppercase(),
                                        color = tagColor,
                                        fontFamily = colors.fontFamily,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .background(tagColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .border(1.dp, tagColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )

                                    Text(
                                        text = snippet.title,
                                        color = colors.primary,
                                        fontFamily = colors.fontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                Row {
                                    IconButton(
                                        onClick = { editingSnippet = snippet },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Snippet", tint = colors.info, modifier = Modifier.size(14.dp))
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteSnippet(snippet.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Snippet", tint = colors.error, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }

                            if (snippet.description.isNotEmpty()) {
                                Text(
                                    text = snippet.description,
                                    color = colors.text.copy(alpha = 0.7f),
                                    fontFamily = colors.fontFamily,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Monospace Command Box
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .border(1.dp, Color(0xFF49454F).copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$ " + snippet.command,
                                    color = colors.success,
                                    fontFamily = colors.fontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Copy command
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("snippet_cmd", snippet.command)
                                            clipboard.setPrimaryClip(clip)
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Command", tint = colors.text.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                                    }

                                    // Quick Run
                                    IconButton(
                                        onClick = {
                                            viewModel.executeCommand(snippet.command)
                                            onExecuteSnippet()
                                        },
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(colors.success.copy(alpha = 0.2f), androidx.compose.foundation.shape.CircleShape)
                                    ) {
                                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Run Snippet", tint = colors.success, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun parseScriptMetadata(content: String): Pair<String, String> {
    var description = "No description available."
    var category = "Custom"
    content.lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("#")) {
            val comment = trimmed.removePrefix("#").trim()
            if (comment.startsWith("Description:", ignoreCase = true)) {
                description = comment.substringAfter("Description:", "").trim()
            } else if (comment.startsWith("Category:", ignoreCase = true)) {
                category = comment.substringAfter("Category:", "").trim()
            }
        }
    }
    return Pair(description, category)
}

@Composable
fun CodeHighlightText(text: String, colors: ConsoleColors) {
    val lines = text.lines()
    Column {
        lines.forEachIndexed { i, line ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = String.format("%2d │ ", i + 1),
                    color = colors.text.copy(alpha = 0.3f),
                    fontFamily = colors.fontFamily,
                    fontSize = 11.sp
                )
                val isComment = line.trim().startsWith("#") || line.trim().startsWith("//")
                val textColor = if (isComment) colors.success.copy(alpha = 0.8f) else colors.text
                Text(
                    text = line,
                    color = textColor,
                    fontFamily = colors.fontFamily,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun ScriptsTab(viewModel: TerminalViewModel, colors: ConsoleColors) {
    val scripts by viewModel.allScripts.collectAsStateWithLifecycle(initialValue = emptyList())
    val isScriptExecuting by viewModel.isScriptExecuting.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var expandedScriptId by remember { mutableStateOf<Long?>(null) }

    var showEditorDialog by remember { mutableStateOf(false) }
    var editorScriptName by remember { mutableStateOf("") }
    var editorScriptContent by remember { mutableStateOf("") }
    var editingScriptId by remember { mutableStateOf<Long?>(null) }
    var editorError by remember { mutableStateOf("") }

    var searchQuery by remember { mutableStateOf("") }

    val filteredScripts = remember(scripts, searchQuery) {
        if (searchQuery.isBlank()) {
            scripts
        } else {
            scripts.filter { script ->
                script.name.contains(searchQuery, ignoreCase = true) ||
                        script.content.contains(searchQuery, ignoreCase = true) ||
                        parseScriptMetadata(script.content).first.contains(searchQuery, ignoreCase = true) ||
                        parseScriptMetadata(script.content).second.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val quickInserts = listOf("echo \"\"", "sysinfo", "todo list", "pomo start 25", "delay 350", "clean-cache", "update-repos", "sys-diagnose")

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Automation Scripts",
                color = colors.primary,
                fontFamily = colors.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Button(
                onClick = {
                    editingScriptId = null
                    editorScriptName = ""
                    editorScriptContent = "#!/bin/sh\n# Description: Custom Termux shell script\n# Category: Maintenance\n"
                    editorError = ""
                    showEditorDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp).testTag("script_add_btn")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Script", tint = colors.background, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Script", color = colors.background, fontFamily = colors.fontFamily, fontSize = 11.sp)
            }
        }

        // Active Script Execution Feedback & Abort Button
        if (isScriptExecuting) {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.error.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, colors.error.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = colors.error,
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Executing active shell process...",
                            color = colors.error,
                            fontFamily = colors.fontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = { viewModel.cancelActiveScript() },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.error),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp).testTag("script_abort_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = "Abort Script", tint = colors.background, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ABORT", color = colors.background, fontFamily = colors.fontFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Horizontal Quick-Add Template Row
        Text(
            text = "Quick-Install Templates",
            color = colors.text.copy(alpha = 0.5f),
            fontFamily = colors.fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            val templates = listOf(
                Triple("Diagnose", "sys_diagnose", """
                    # Description: System diagnostic utility
                    # Category: Diagnostics
                    echo "Checking system load..."
                    sysinfo
                    echo "Checking sandbox resources..."
                    sys-diagnose
                """.trimIndent()),
                Triple("Update repos", "update_repos", """
                    # Description: Sync remote Termux repository
                    # Category: Maintenance
                    echo "Updating packages..."
                    update-repos
                """.trimIndent()),
                Triple("Clean disk", "clean_disk", """
                    # Description: Purge log cache and debris
                    # Category: Maintenance
                    echo "Purging sandbox junk..."
                    clean-cache
                """.trimIndent()),
                Triple("Git Sync", "git_backup", """
                    # Description: Fast backup to developer mirror
                    # Category: Security
                    echo "Staging tracking tree..."
                    echo "Auto-committing local workspace to git main"
                    echo "[SUCCESS] GitHub Sync complete."
                """.trimIndent()),
                Triple("Work Sprint", "pomo_sprint", """
                    # Description: Focus on tasks with pomodoro countdown
                    # Category: Productivity
                    echo "Listing today's backlog tasks:"
                    todo list
                    echo "Starting Pomodoro sprint..."
                    pomo start 25
                """.trimIndent())
            )

            items(templates) { (label, scriptName, code) ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .width(130.dp)
                        .clickable {
                            viewModel.addCustomScript(scriptName, code)
                            viewModel.executeCommand("echo \"Imported template '${label}' successfully.\"")
                        }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Build, contentDescription = null, tint = colors.primary, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = label, color = colors.primary, fontFamily = colors.fontFamily, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "Tap to load .sh", color = colors.text.copy(alpha = 0.4f), fontFamily = colors.fontFamily, fontSize = 8.sp)
                    }
                }
            }
        }

        // Active Saved Library List
        Text(
            text = "Saved Scripts Library",
            color = colors.text.copy(alpha = 0.5f),
            fontFamily = colors.fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        if (scripts.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No custom scripts in library.", color = colors.text.copy(alpha = 0.4f), fontFamily = colors.fontFamily, fontSize = 11.sp)
            }
        } else {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by name, category, or code...", color = colors.text.copy(alpha = 0.4f), fontFamily = colors.fontFamily, fontSize = 11.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = colors.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = colors.text.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                textStyle = TextStyle(color = colors.text, fontFamily = colors.fontFamily, fontSize = 11.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.primary.copy(alpha = 0.2f),
                    cursorColor = colors.primary,
                    focusedContainerColor = colors.cardBackground.copy(alpha = 0.3f),
                    unfocusedContainerColor = colors.cardBackground.copy(alpha = 0.1f)
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .testTag("script_search_bar")
            )

            if (filteredScripts.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No scripts match your search.",
                            color = colors.text.copy(alpha = 0.4f),
                            fontFamily = colors.fontFamily,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "Clear Search",
                            color = colors.primary,
                            fontFamily = colors.fontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { searchQuery = "" }
                                .padding(4.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredScripts) { script ->
                        val isExpanded = expandedScriptId == script.id
                        val (description, category) = parseScriptMetadata(script.content)

                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                            border = BorderStroke(1.dp, if (isExpanded) colors.primary.copy(alpha = 0.3f) else colors.primary.copy(alpha = 0.05f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                // Header Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f).clickable {
                                            expandedScriptId = if (isExpanded) null else script.id
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Expand/Collapse",
                                            tint = colors.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = script.name + ".sh",
                                                    color = colors.primary,
                                                    fontFamily = colors.fontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .background(colors.primary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = category,
                                                        color = colors.primary,
                                                        fontFamily = colors.fontFamily,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "Lines: ${script.content.trim().lines().size}",
                                                color = colors.text.copy(alpha = 0.5f),
                                                fontFamily = colors.fontFamily,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    Row {
                                        IconButton(
                                            onClick = { viewModel.runCustomScriptDirect(script) },
                                            modifier = Modifier.size(32.dp).testTag("run_script_${script.name}")
                                        ) {
                                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Run Script", tint = colors.success)
                                        }
                                        IconButton(
                                            onClick = {
                                                editingScriptId = script.id
                                                editorScriptName = script.name
                                                editorScriptContent = script.content
                                                editorError = ""
                                                showEditorDialog = true
                                            },
                                            modifier = Modifier.size(32.dp).testTag("edit_script_${script.name}")
                                        ) {
                                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Script", tint = colors.info)
                                        }
                                        IconButton(
                                            onClick = { viewModel.deleteCustomScript(script.name) },
                                            modifier = Modifier.size(32.dp).testTag("delete_script_${script.name}")
                                        ) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Script", tint = colors.error)
                                        }
                                    }
                                }

                                // Expanded Section
                                if (isExpanded) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = colors.text.copy(alpha = 0.1f), thickness = 1.dp)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "Description:",
                                        color = colors.text.copy(alpha = 0.5f),
                                        fontFamily = colors.fontFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = description,
                                        color = colors.text,
                                        fontFamily = colors.fontFamily,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Source Code:",
                                            color = colors.text.copy(alpha = 0.5f),
                                            fontFamily = colors.fontFamily,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                val clip = android.content.ClipData.newPlainText("termux_script", script.content)
                                                clipboard.setPrimaryClip(clip)
                                                viewModel.executeCommand("echo \"Copied script '${script.name}.sh' to clipboard.\"")
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Source Code", tint = colors.text.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF151418), RoundedCornerShape(6.dp))
                                            .border(BorderStroke(1.dp, colors.text.copy(alpha = 0.1f)), RoundedCornerShape(6.dp))
                                            .padding(8.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        CodeHighlightText(text = script.content, colors = colors)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modern Dialog Composer for adding/editing scripts
    if (showEditorDialog) {
        AlertDialog(
            onDismissRequest = { showEditorDialog = false },
            title = {
                Text(
                    text = if (editingScriptId == null) "Create Shell Script" else "Modify Shell Script",
                    color = colors.primary,
                    fontFamily = colors.fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            containerColor = Color(0xFF1C1B1F),
            shape = RoundedCornerShape(12.dp),
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (editorError.isNotEmpty()) {
                        Text(
                            text = editorError,
                            color = colors.error,
                            fontFamily = colors.fontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Name Field
                    OutlinedTextField(
                        value = editorScriptName,
                        onValueChange = { editorScriptName = it.replace(" ", "_").lowercase() },
                        textStyle = TextStyle(color = colors.text, fontFamily = colors.fontFamily, fontSize = 12.sp),
                        label = { Text("Script Name (auto_slug)", color = colors.primary, fontFamily = colors.fontFamily, fontSize = 11.sp) },
                        placeholder = { Text("e.g. system_update", color = colors.text.copy(alpha = 0.4f), fontFamily = colors.fontFamily, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.primary.copy(alpha = 0.4f),
                            cursorColor = colors.primary
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("script_name_input"),
                        enabled = editingScriptId == null
                    )

                    // Code Editor Text Area
                    OutlinedTextField(
                        value = editorScriptContent,
                        onValueChange = { editorScriptContent = it },
                        textStyle = TextStyle(color = colors.text, fontFamily = colors.fontFamily, fontSize = 11.sp),
                        label = { Text("Script Content", color = colors.primary, fontFamily = colors.fontFamily, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.primary.copy(alpha = 0.4f),
                            cursorColor = colors.primary
                        ),
                        minLines = 8,
                        maxLines = 14,
                        modifier = Modifier.fillMaxWidth().testTag("script_content_input")
                    )

                    // Quick-Insert command tray helper
                    Text(
                        text = "Quick command inserts:",
                        color = colors.text.copy(alpha = 0.5f),
                        fontFamily = colors.fontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(quickInserts) { insert ->
                            Box(
                                modifier = Modifier
                                    .background(colors.cardBackground, RoundedCornerShape(4.dp))
                                    .border(BorderStroke(1.dp, colors.primary.copy(alpha = 0.3f)), RoundedCornerShape(4.dp))
                                    .clickable {
                                        editorScriptContent = editorScriptContent + "\n" + insert
                                    }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = insert,
                                    color = colors.primary,
                                    fontFamily = colors.fontFamily,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val nameClean = editorScriptName.trim()
                        if (nameClean.isEmpty()) {
                            editorError = "Script name cannot be empty."
                            return@Button
                        }
                        if (editorScriptContent.trim().isEmpty()) {
                            editorError = "Script content cannot be empty."
                            return@Button
                        }

                        viewModel.addCustomScript(nameClean, editorScriptContent)
                        viewModel.executeCommand("echo \"Successfully saved script '$nameClean.sh'\"")
                        showEditorDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("Save", color = colors.background, fontFamily = colors.fontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showEditorDialog = false },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                    border = BorderStroke(1.dp, colors.text.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("Cancel", color = colors.text, fontFamily = colors.fontFamily, fontSize = 12.sp)
                }
            }
        )
    }
}

@Composable
fun TasksTab(viewModel: TerminalViewModel, colors: ConsoleColors) {
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle(initialValue = emptyList())
    var newTaskText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Productivity Backlog",
            color = colors.primary,
            fontFamily = colors.fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Quick Add Task Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newTaskText,
                onValueChange = { newTaskText = it },
                textStyle = TextStyle(color = colors.text, fontFamily = colors.fontFamily, fontSize = 13.sp),
                placeholder = { Text("Quick add new task...", color = colors.text.copy(alpha = 0.4f), fontFamily = colors.fontFamily, fontSize = 13.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.primary.copy(alpha = 0.4f),
                    cursorColor = colors.primary
                ),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.weight(1f).height(48.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(6.dp))
            Button(
                onClick = {
                    if (newTaskText.trim().isNotEmpty()) {
                        viewModel.executeCommand("todo add \"${newTaskText.trim()}\"")
                        newTaskText = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.height(48.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Task", tint = colors.background)
            }
        }

        if (tasks.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No active tasks.", color = colors.text.copy(alpha = 0.4f), fontFamily = colors.fontFamily, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(tasks) { task ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Checkbox(
                                    checked = task.isCompleted,
                                    onCheckedChange = {
                                        viewModel.executeCommand("todo check ${tasks.indexOf(task) + 1}")
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = colors.primary,
                                        checkmarkColor = colors.background,
                                        uncheckedColor = colors.primary.copy(alpha = 0.5f)
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = task.task,
                                    color = if (task.isCompleted) colors.text.copy(alpha = 0.4f) else colors.text,
                                    fontFamily = colors.fontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = if (task.isCompleted) FontWeight.Normal else FontWeight.Medium
                                )
                            }
                            IconButton(
                                onClick = { viewModel.executeCommand("todo rm ${tasks.indexOf(task) + 1}") },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Task", tint = colors.error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilesTab(viewModel: TerminalViewModel, colors: ConsoleColors) {
    val notes by viewModel.allNotes.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Secure Notepad Files",
                color = colors.primary,
                fontFamily = colors.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Button(
                onClick = { viewModel.executeCommand("nano notes_doc.txt") },
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "New Note", tint = colors.background, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Doc", color = colors.background, fontFamily = colors.fontFamily, fontSize = 11.sp)
            }
        }

        if (notes.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No files saved. Type 'nano <filename>'", color = colors.text.copy(alpha = 0.4f), fontFamily = colors.fontFamily, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notes) { note ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = note.title + ".txt",
                                    color = colors.primary,
                                    fontFamily = colors.fontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Words: ${note.content.split("\\s+".toRegex()).size}",
                                    color = colors.text.copy(alpha = 0.6f),
                                    fontFamily = colors.fontFamily,
                                    fontSize = 11.sp
                                )
                            }
                            Row {
                                IconButton(
                                    onClick = { viewModel.executeCommand("notes cat ${note.title}") },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Visibility, contentDescription = "View Note", tint = colors.success)
                                }
                                IconButton(
                                    onClick = { viewModel.executeCommand("nano ${note.title}.txt") },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Note", tint = colors.info)
                                }
                                IconButton(
                                    onClick = { viewModel.executeCommand("notes rm ${note.title}") },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Note", tint = colors.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusTab(viewModel: TerminalViewModel, colors: ConsoleColors) {
    val pomoSession by viewModel.pomoSession.collectAsStateWithLifecycle()
    val metrics by viewModel.deviceMetrics.collectAsStateWithLifecycle()
    var pomoMinutesInput by remember { mutableStateOf("25") }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Telemetry Status Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "System Telemetry & Controls",
                color = colors.primary,
                fontFamily = colors.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .background(colors.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .border(1.dp, colors.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(colors.success, androidx.compose.foundation.shape.CircleShape)
                )
                Text(
                    text = "TELEM ACTIVE",
                    color = colors.success,
                    fontFamily = colors.fontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 1. Overview & Terminal Efficiency Score
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            border = BorderStroke(1.dp, Color(0xFF49454F).copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Efficiency Gauge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(72.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { metrics.efficiencyScore / 100f },
                        modifier = Modifier.fillMaxSize(),
                        color = when {
                            metrics.efficiencyScore > 80 -> colors.success
                            metrics.efficiencyScore > 50 -> colors.warning
                            else -> colors.error
                        },
                        strokeWidth = 6.dp,
                        trackColor = colors.background
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${metrics.efficiencyScore}%",
                            color = colors.text,
                            fontFamily = colors.fontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "EFFICIENCY",
                            color = colors.text.copy(alpha = 0.5f),
                            fontFamily = colors.fontFamily,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // General details column
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Terminal Efficiency Monitor",
                        color = colors.primary,
                        fontFamily = colors.fontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Uptime: ${metrics.systemUptime}",
                        color = colors.text.copy(alpha = 0.8f),
                        fontFamily = colors.fontFamily,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    Text(
                        text = "Storage: %.1f GB free / %.1f GB".format(metrics.appStorageFreeGb, metrics.appStorageTotalGb),
                        color = colors.text.copy(alpha = 0.6f),
                        fontFamily = colors.fontFamily,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (metrics.appStorageTotalGb - metrics.appStorageFreeGb).toFloat() / metrics.appStorageTotalGb.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = colors.primary,
                        trackColor = colors.background
                    )
                }
            }
        }

        // 2. Real-time Telemetry Grid (CPU & RAM)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // CPU Card
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                border = BorderStroke(1.dp, Color(0xFF49454F).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Thermostat,
                            contentDescription = "CPU",
                            tint = colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "CPU Core",
                            color = colors.primary,
                            fontFamily = colors.fontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "%.1f °C".format(metrics.cpuTemp),
                        color = when {
                            metrics.cpuTemp < 45.0 -> colors.success
                            metrics.cpuTemp < 60.0 -> colors.warning
                            else -> colors.error
                        },
                        fontFamily = colors.fontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "%.0f MHz".format(metrics.cpuFrequencyMhz),
                        color = colors.text.copy(alpha = 0.7f),
                        fontFamily = colors.fontFamily,
                        fontSize = 11.sp
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (metrics.cpuTemp / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = when {
                            metrics.cpuTemp < 45.0 -> colors.success
                            metrics.cpuTemp < 60.0 -> colors.warning
                            else -> colors.error
                        },
                        trackColor = colors.background
                    )
                }
            }

            // RAM Card
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                border = BorderStroke(1.dp, Color(0xFF49454F).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "RAM",
                            tint = colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "RAM",
                            color = colors.primary,
                            fontFamily = colors.fontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "${metrics.usedRamPercent}%",
                        color = when {
                            metrics.usedRamPercent < 60 -> colors.success
                            metrics.usedRamPercent < 85 -> colors.warning
                            else -> colors.error
                        },
                        fontFamily = colors.fontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "%.1f / %.1f GB".format(metrics.totalRamGb - metrics.availRamGb, metrics.totalRamGb),
                        color = colors.text.copy(alpha = 0.7f),
                        fontFamily = colors.fontFamily,
                        fontSize = 11.sp
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { metrics.usedRamPercent / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = when {
                            metrics.usedRamPercent < 60 -> colors.success
                            metrics.usedRamPercent < 85 -> colors.warning
                            else -> colors.error
                        },
                        trackColor = colors.background
                    )
                }
            }
        }

        // Battery Power Card
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            border = BorderStroke(1.dp, Color(0xFF49454F).copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (metrics.isCharging) Icons.Default.Bolt else Icons.Default.BatteryStd,
                            contentDescription = "Power",
                            tint = if (metrics.isCharging) colors.success else colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Power & Battery Status",
                            color = colors.primary,
                            fontFamily = colors.fontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    
                    Text(
                        text = if (metrics.isCharging) "CHARGING" else "DISCHARGING",
                        color = if (metrics.isCharging) colors.success else colors.text.copy(alpha = 0.5f),
                        fontFamily = colors.fontFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(
                                if (metrics.isCharging) colors.success.copy(alpha = 0.15f) else colors.text.copy(alpha = 0.1f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Battery Level",
                            color = colors.text.copy(alpha = 0.5f),
                            fontFamily = colors.fontFamily,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "${metrics.batteryLevel}%",
                            color = colors.text,
                            fontFamily = colors.fontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Battery Temp",
                            color = colors.text.copy(alpha = 0.5f),
                            fontFamily = colors.fontFamily,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "%.1f °C".format(metrics.batteryTemp),
                            color = colors.text,
                            fontFamily = colors.fontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { metrics.batteryLevel / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = when {
                        metrics.batteryLevel > 30 -> colors.success
                        metrics.batteryLevel > 15 -> colors.warning
                        else -> colors.error
                    },
                    trackColor = colors.background
                )
            }
        }

        // Pomodoro Manager Box
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pomodoro Timer Controller",
                        color = colors.primary,
                        fontFamily = colors.fontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Badge(
                        containerColor = if (pomoSession.isRunning) colors.error else colors.text.copy(alpha = 0.2f),
                        contentColor = if (pomoSession.isRunning) Color.White else colors.text
                    ) {
                        Text(
                            text = if (pomoSession.isRunning) "ACTIVE" else "IDLE",
                            fontFamily = colors.fontFamily,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (pomoSession.isRunning) {
                    val minutes = pomoSession.secondsRemaining / 60
                    val seconds = pomoSession.secondsRemaining % 60
                    val progress = 1f - (pomoSession.secondsRemaining.toFloat() / pomoSession.totalSeconds)

                    Text(
                        text = "Session: %02d:%02d".format(minutes, seconds),
                        color = colors.text,
                        fontFamily = colors.fontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = colors.error,
                        trackColor = colors.background
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.stopPomodoro() },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.error),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Stop Timer", color = Color.White, fontFamily = colors.fontFamily, fontSize = 11.sp)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = pomoMinutesInput,
                            onValueChange = { pomoMinutesInput = it },
                            textStyle = TextStyle(color = colors.text, fontFamily = colors.fontFamily, fontSize = 12.sp),
                            label = { Text("Duration (mins)", color = colors.text.copy(alpha = 0.5f), fontFamily = colors.fontFamily, fontSize = 10.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.primary,
                                unfocusedBorderColor = colors.primary.copy(alpha = 0.4f),
                                cursorColor = colors.primary
                            ),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.weight(1f).height(50.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = {
                                val mins = pomoMinutesInput.toIntOrNull() ?: 25
                                viewModel.startPomodoro(mins)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Start Focus", color = colors.background, fontFamily = colors.fontFamily, fontSize = 11.sp)
                        }
                    }
                    Text(
                        text = "Completed Sessions: ${pomoSession.completedCount}",
                        color = colors.text.copy(alpha = 0.6f),
                        fontFamily = colors.fontFamily,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // Quick Command Run Box
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Quick Process Shortcuts",
                    color = colors.primary,
                    fontFamily = colors.fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val quickActionModifier = Modifier.weight(1f).height(38.dp)
                    Button(
                        onClick = { viewModel.executeCommand("sysinfo") },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.background),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(0.dp),
                        modifier = quickActionModifier
                    ) {
                        Text("SYSINFO", color = colors.primary, fontFamily = colors.fontFamily, fontSize = 10.sp)
                    }
                    Button(
                        onClick = { viewModel.executeCommand("quotes") },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.background),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(0.dp),
                        modifier = quickActionModifier
                    ) {
                        Text("DEV QUOTES", color = colors.primary, fontFamily = colors.fontFamily, fontSize = 10.sp)
                    }
                    Button(
                        onClick = { viewModel.toggleMatrix(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.background),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(0.dp),
                        modifier = quickActionModifier
                    ) {
                        Text("MATRIX", color = colors.success, fontFamily = colors.fontFamily, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun NanoEditor(
    viewModel: TerminalViewModel,
    colors: ConsoleColors
) {
    val filename by viewModel.nanoFileName.collectAsStateWithLifecycle()
    val content by viewModel.nanoContent.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030303))
    ) {
        // Nano Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFFFFF))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GNU nano 7.2",
                color = Color.Black,
                fontFamily = colors.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Text(
                text = "File: $filename",
                color = Color.Black,
                fontFamily = colors.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Text(
                text = "Modified",
                color = Color.Black,
                fontFamily = colors.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }

        // Nano Text Editor body
        OutlinedTextField(
            value = content,
            onValueChange = { viewModel.onNanoContentChange(it) },
            textStyle = TextStyle(
                color = colors.text,
                fontFamily = colors.fontFamily,
                fontSize = 13.sp,
                lineHeight = 16.sp
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF070707),
                unfocusedContainerColor = Color(0xFF070707),
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = colors.primary
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(0.dp)
        )

        // Nano Shortcuts / Keyboard Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF151515))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "^G Get Help  ^O Write Out", color = Color.White, fontFamily = colors.fontFamily, fontSize = 11.sp)
                Text(text = "^X Exit      ^R Read File", color = Color.White, fontFamily = colors.fontFamily, fontSize = 11.sp)
            }
            Row {
                Button(
                    onClick = { viewModel.nanoExitDiscard() },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.error),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(36.dp).padding(end = 8.dp)
                ) {
                    Text("Discard", color = Color.White, fontFamily = colors.fontFamily, fontSize = 11.sp)
                }
                Button(
                    onClick = { viewModel.nanoExitAndSave() },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.success),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Save & Exit", color = Color.Black, fontFamily = colors.fontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MatrixRain(
    colors: ConsoleColors,
    onExit: () -> Unit
) {
    val charList = remember { "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz!@#$%^&*()_+-=".toList() }
    var ticks by remember { mutableStateOf(0) }

    // Animation Tick
    LaunchedEffect(Unit) {
        while (true) {
            delay(50)
            ticks++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onExit() }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val columnSize = 35f // Stream columns
            val columns = (width / columnSize).toInt() + 1

            for (col in 0 until columns) {
                val seed = col * 7919
                val speed = 6 + (seed % 14) // Speed: 6 to 20 pixels per tick
                val startDelay = (seed % 150)

                val totalTravel = (ticks - startDelay) * speed
                val headY = totalTravel % (height + 400) - 200

                val streamLen = 10 + (seed % 10)
                for (i in 0 until streamLen) {
                    val charY = headY - (i * 26f)
                    if (charY in 0f..height) {
                        val charIndex = (ticks + col + i) % charList.size
                        val charStr = charList[charIndex].toString()

                        val alpha = (1f - (i.toFloat() / streamLen)).coerceIn(0f, 1f)
                        val color = if (i == 0) Color.White else colors.text.copy(alpha = alpha)

                        drawContext.canvas.nativeCanvas.drawText(
                            charStr,
                            col * columnSize,
                            charY,
                            android.graphics.Paint().apply {
                                this.color = color.toArgb()
                                this.textSize = 30f
                                this.typeface = android.graphics.Typeface.MONOSPACE
                                this.textAlign = android.graphics.Paint.Align.CENTER
                            }
                        )
                    }
                }
            }
        }

        // Tap HUD overlay
        Text(
            text = "[TAP ANYWHERE OR PRESS CTRL+C TO CLOSE SYSTEM MATRIX]",
            color = colors.text.copy(alpha = 0.5f),
            fontFamily = colors.fontFamily,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}
