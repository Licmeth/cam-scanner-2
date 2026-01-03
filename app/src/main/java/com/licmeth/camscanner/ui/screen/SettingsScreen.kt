package com.licmeth.camscanner.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.licmeth.camscanner.model.DebugOutputLevel
import com.licmeth.camscanner.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDebugLevelDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Debug Overlay Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Debug Overlay",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = uiState.enableDebugOverlay,
                    onCheckedChange = { viewModel.setDebugOverlay(it) }
                )
            }

            Divider()

            // Debug Output Level
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDebugLevelDialog = true }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Debug Output Level",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = uiState.debugOutputLevel.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showDebugLevelDialog) {
                AlertDialog(
                    onDismissRequest = { showDebugLevelDialog = false },
                    title = { Text("Debug Output Level") },
                    text = {
                        Column {
                            DebugOutputLevel.entries.forEach { level ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.setDebugOutputLevel(level)
                                            showDebugLevelDialog = false
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = uiState.debugOutputLevel == level,
                                        onClick = {
                                            viewModel.setDebugOutputLevel(level)
                                            showDebugLevelDialog = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(level.name)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showDebugLevelDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}
