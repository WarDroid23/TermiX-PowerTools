package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.theme.MyApplicationTheme

import com.example.data.TerminalDatabase
import com.example.data.TerminalRepository
import com.example.ui.terminal.TerminalScreen
import com.example.ui.terminal.TerminalViewModel
import com.example.ui.terminal.TerminalViewModelFactory

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val database = TerminalDatabase.getDatabase(applicationContext)
    val repository = TerminalRepository(database.terminalDao())
    val factory = TerminalViewModelFactory(application, repository)

    setContent {
      MyApplicationTheme {
        val viewModel: TerminalViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          TerminalScreen(
            viewModel = viewModel,
            modifier = Modifier.padding(innerPadding)
          )
        }
      }
    }
  }
}
