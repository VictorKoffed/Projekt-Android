package com.victorkoffed.projektandroid.ui.screens.brew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.victorkoffed.projektandroid.ui.screens.bean.ArchiveConfirmationDialog
import com.victorkoffed.projektandroid.ui.screens.brew.composable.BrewEditCard
import com.victorkoffed.projektandroid.ui.screens.brew.composable.BrewImageSection
import com.victorkoffed.projektandroid.ui.screens.brew.composable.BrewMetricsCard
import com.victorkoffed.projektandroid.ui.screens.brew.composable.BrewNotesSection
import com.victorkoffed.projektandroid.ui.screens.brew.composable.BrewSamplesGraph
import com.victorkoffed.projektandroid.ui.screens.brew.composable.BrewSummaryCard
import com.victorkoffed.projektandroid.ui.viewmodel.brew.BrewDetailViewModel
import kotlinx.coroutines.launch

/**
 * Screen presenting comprehensive analytics and telemetry for a specific brewing session.
 * Orchestrates layout composition across summary metrics, interactive progress graphs,
 * photo attachments, and administrative actions such as editing or deletion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrewDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToImageFullscreen: (String) -> Unit,
    viewModel: BrewDetailViewModel,
    snackbarHostState: SnackbarHostState
) {
    val state by viewModel.brewDetailState.collectAsState()
    val isEditing by remember { derivedStateOf { viewModel.isEditing } }
    val showArchivePromptOnEntry by viewModel.showArchivePromptOnEntry.collectAsState()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val availableGrinders by viewModel.availableGrinders.collectAsState()
    val availableMethods by viewModel.availableMethods.collectAsState()
    var showWeightLine by remember { mutableStateOf(true) }
    var showFlowLine by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    val beanForPrompt = state.bean
    val savedNotes = state.brew?.notes ?: ""
    val hasUnsavedQuickNotes = viewModel.quickEditNotes.trim() != savedNotes.trim()

    LaunchedEffect(state.error) {
        if (state.error != null) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = state.error!!,
                    duration = SnackbarDuration.Long
                )
            }
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditing) "Edit Brew"
                        else state.brew?.let { "Brew: ${state.bean?.name ?: "Unknown"}" } ?: "Loading..."
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (isEditing) viewModel.cancelEditing() else onNavigateBack() }) {
                        Icon(
                            if (isEditing) Icons.Default.Cancel else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEditing) "Cancel" else "Back"
                        )
                    }
                },
                actions = {
                    if (isEditing) {
                        IconButton(
                            onClick = { viewModel.saveChanges() },
                            enabled = state.brew != null
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        IconButton(onClick = { viewModel.startEditing() }, enabled = state.brew != null) {
                            Icon(Icons.Default.Edit, "Edit")
                        }
                        IconButton(onClick = { showDeleteConfirmDialog = true }, enabled = state.brew != null) {
                            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
    ) { paddingValues ->
        when {
            state.isLoading && !isEditing -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                val currentBrew = state.brew
                if (currentBrew != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Spacer(modifier = Modifier.height(0.dp))

                        BrewImageSection(
                            imageUri = currentBrew.imageUri,
                            isEditing = isEditing,
                            onNavigateToCamera = onNavigateToCamera,
                            onNavigateToImageFullscreen = onNavigateToImageFullscreen,
                            onDeleteImage = { viewModel.updateBrewImageUri(null) }
                        )

                        if (isEditing) {
                            BrewEditCard(
                                viewModel = viewModel,
                                availableGrinders = availableGrinders,
                                availableMethods = availableMethods
                            )
                        } else {
                            BrewSummaryCard(state = state)
                        }

                        state.metrics?.let { metrics ->
                            BrewMetricsCard(metrics = metrics)
                        } ?: Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Text("No ratio/water data available.", modifier = Modifier.padding(16.dp))
                        }

                        Text("Brew Progress", style = MaterialTheme.typography.titleMedium)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = showWeightLine,
                                onClick = { showWeightLine = !showWeightLine },
                                label = { Text("Weight") },
                                leadingIcon = { Icon(if (showWeightLine) Icons.Filled.Check else Icons.Filled.Close, if (showWeightLine) "Visible" else "Hidden") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                )
                            )
                            FilterChip(
                                selected = showFlowLine,
                                onClick = { showFlowLine = !showFlowLine },
                                label = { Text("Flow") },
                                leadingIcon = { Icon(if (showFlowLine) Icons.Filled.Check else Icons.Filled.Close, if (showFlowLine) "Visible" else "Hidden") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondary,
                                )
                            )
                        }

                        if (state.samples.isNotEmpty()) {
                            BrewSamplesGraph(
                                samples = state.samples,
                                showWeightLine = showWeightLine,
                                showFlowLine = showFlowLine,
                                modifier = Modifier.fillMaxWidth().height(300.dp)
                            )
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Text("No graph data saved for this brew.", modifier = Modifier.padding(16.dp))
                            }
                        }

                        BrewNotesSection(
                            isEditing = isEditing,
                            quickEditNotesValue = viewModel.quickEditNotes,
                            fullEditNotesValue = viewModel.editNotes,
                            hasUnsavedQuickNotes = hasUnsavedQuickNotes,
                            onQuickEditNotesChanged = viewModel::onQuickEditNotesChanged,
                            onFullEditNotesChanged = viewModel::onEditNotesChanged,
                            onSaveQuickEditNotes = viewModel::saveQuickEditNotes
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                    }

                    if (showDeleteConfirmDialog) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirmDialog = false },
                            title = { Text("Delete brew?") },
                            text = { Text("Are you sure you want to delete the brew for '${state.bean?.name ?: "this bean"}'? This action cannot be undone.") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        viewModel.deleteCurrentBrew {
                                            onNavigateBack()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) { Text("Delete") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    if (showArchivePromptOnEntry != null && beanForPrompt != null && showArchivePromptOnEntry == beanForPrompt.id) {
                        ArchiveConfirmationDialog(
                            beanName = beanForPrompt.name,
                            onConfirm = { viewModel.archiveBeanFromPrompt(showArchivePromptOnEntry!!) },
                            onDismiss = { viewModel.dismissArchivePromptOnEntry() }
                        )
                    }

                } else {
                    Box(Modifier.fillMaxSize().padding(paddingValues), Alignment.Center) {
                        Text(state.error ?: "Brew data unavailable.")
                    }
                }
            }
        }
    }
}

/**
 * Presents captured session photographs in an immersive full-screen view with pan and zoom capabilities.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenImageScreen(
    uri: String,
    onNavigateBack: () -> Unit
) {
    val containerColor = Color.Black
    val contentColor = Color.White

    Scaffold(
        containerColor = containerColor,
        topBar = {
            TopAppBar(
                title = { Text("Brew Photo", color = contentColor) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = contentColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = uri,
                contentDescription = "Fullscreen brew photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}