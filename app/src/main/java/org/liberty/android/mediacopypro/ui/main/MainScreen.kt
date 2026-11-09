package org.liberty.android.mediacopypro.ui.main

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import org.liberty.android.mediacopypro.ConflictStrategy
import org.liberty.android.mediacopypro.MainViewModel
import org.liberty.android.mediacopypro.ServerList
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.liberty.android.mediacopypro.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSelectionDialog(
    title: String,
    onLocalSelected: () -> Unit,
    onServerSelected: () -> Unit,
    onDismissRequest: () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
                Text(
                    text = "Local Device",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLocalSelected() }
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                )
                HorizontalDivider()
                Text(
                    text = "SMB Server",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onServerSelected() }
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    val isProfileModified = remember(state) {
        state.profiles.find { it.name == state.currentProfileName }?.let { savedProfile ->
            savedProfile.recursive != state.recursive ||
            savedProfile.conflictStrategy != state.conflictStrategy.name ||
            savedProfile.extensionPattern != state.extensionPattern ||
            savedProfile.fileNamePattern != state.fileNamePattern ||
            savedProfile.destFormat != state.destFormat
        } ?: false
    }

    var showExitWarningDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = isProfileModified) {
        showExitWarningDialog = true
    }

    if (showExitWarningDialog) {
        AlertDialog(
            onDismissRequest = { showExitWarningDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("The current options are not saved in the profile and exiting will not save the options.\n\nAre you sure you want to exit without saving?") },
            confirmButton = {
                TextButton(onClick = {
                    showExitWarningDialog = false
                    activity?.finish()
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showExitWarningDialog = false }) { Text("Cancel") }
            }
        )
    }

    var showSourceSelectionDialog by remember { mutableStateOf(false) }
    var showDestSelectionDialog by remember { mutableStateOf(false) }

    val srcLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            // Persist access permissions
            val takeFlags: Int = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            viewModel.updateSrcUri(it.toString())
        }
    }

    val destLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            // Persist access permissions
            val takeFlags: Int = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            viewModel.updateDestUri(it.toString())
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                
                var showAboutDialog by remember { mutableStateOf(false) }
                IconButton(onClick = { showAboutDialog = true }) {
                    Icon(painter = painterResource(id = R.drawable.info_24px), contentDescription = "About")
                }
                
                if (showAboutDialog) {
                    AlertDialog(
                        onDismissRequest = { showAboutDialog = false },
                        title = { Text("About") },
                        text = {
                            Column {
                                Text(stringResource(R.string.app_name))
                                Text("Version: ${org.liberty.android.mediacopypro.BuildConfig.VERSION_NAME}")
                                Text("Version Code: ${org.liberty.android.mediacopypro.BuildConfig.VERSION_CODE}")
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showAboutDialog = false }) { Text("OK") }
                        }
                    )
                }
            }

            if (showSourceSelectionDialog) {
                StorageSelectionDialog(
                    title = "Select Source Type",
                    onLocalSelected = {
                        showSourceSelectionDialog = false
                        srcLauncher.launch(null)
                    },
                    onServerSelected = {
                        showSourceSelectionDialog = false
                        onItemClick(ServerList(isSource = true))
                    },
                    onDismissRequest = { showSourceSelectionDialog = false }
                )
            }
            if (showDestSelectionDialog) {
                StorageSelectionDialog(
                    title = "Select Destination Type",
                    onLocalSelected = {
                        showDestSelectionDialog = false
                        destLauncher.launch(null)
                    },
                    onServerSelected = {
                        showDestSelectionDialog = false
                        onItemClick(ServerList(isSource = false))
                    },
                    onDismissRequest = { showDestSelectionDialog = false }
                )
            }
            // Source Directory
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.isRunning) { showSourceSelectionDialog = true }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.source_directory), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = state.srcUri ?: stringResource(R.string.none_selected), style = MaterialTheme.typography.bodySmall)
                }
            }

            // Destination Directory
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.isRunning) { showDestSelectionDialog = true }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.destination_directory), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = state.destUri ?: stringResource(R.string.none_selected), style = MaterialTheme.typography.bodySmall)
                }
            }

        // Options
        var optionsExpanded by remember { mutableStateOf(false) }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { optionsExpanded = !optionsExpanded }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = stringResource(R.string.options), style = MaterialTheme.typography.titleMedium)
                    Text(text = if (optionsExpanded) stringResource(R.string.hide) else stringResource(R.string.show), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                
                if (optionsExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    var profileExpanded by remember { mutableStateOf(false) }
                    var showSaveAsDialog by remember { mutableStateOf(false) }
                    var saveAsName by remember { mutableStateOf("") }
                    
                    var showProfileInfo by remember { mutableStateOf(false) }
                    var showFolderScanInfo by remember { mutableStateOf(false) }
                    var showConflictStrategyInfo by remember { mutableStateOf(false) }
                    
                    ExposedDropdownMenuBox(
                        expanded = profileExpanded,
                        onExpandedChange = { if (!state.isRunning) profileExpanded = !profileExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = state.currentProfileName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Profile") },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileExpanded)
                                    IconButton(onClick = { 
                                        profileExpanded = false
                                        showProfileInfo = true 
                                    }) {
                                        Icon(painter = painterResource(id = R.drawable.info_24px), contentDescription = "Info")
                                    }
                                }
                            },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !state.isRunning).fillMaxWidth(),
                            enabled = !state.isRunning
                        )
                        ExposedDropdownMenu(
                            expanded = profileExpanded,
                            onDismissRequest = { profileExpanded = false }
                        ) {
                            state.profiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = { Text(profile.name) },
                                    onClick = {
                                        viewModel.loadProfile(profile.name)
                                        profileExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = { viewModel.saveProfile() }, enabled = !state.isRunning, modifier = Modifier.weight(1f)) {
                            Text("Save")
                        }
                        Button(onClick = { showSaveAsDialog = true }, enabled = !state.isRunning, modifier = Modifier.weight(1f)) {
                            Text("Save As")
                        }
                        if (state.currentProfileName != "Default") {
                            OutlinedButton(
                                onClick = { viewModel.deleteProfile(state.currentProfileName) },
                                enabled = !state.isRunning, modifier = Modifier.weight(1f)
                            ) {
                                Text("Delete")
                            }
                        }
                    }
                    
                    if (showSaveAsDialog) {
                        AlertDialog(
                            onDismissRequest = { showSaveAsDialog = false },
                            title = { Text("Save As Profile") },
                            text = {
                                OutlinedTextField(
                                    value = saveAsName,
                                    onValueChange = { saveAsName = it },
                                    label = { Text("Profile Name") },
                                    singleLine = true
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.saveAsProfile(saveAsName)
                                    showSaveAsDialog = false
                                    saveAsName = ""
                                }) { Text("Save") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showSaveAsDialog = false }) { Text("Cancel") }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    var folderScanExpanded by remember { mutableStateOf(false) }
                    
                    ExposedDropdownMenuBox(
                        expanded = folderScanExpanded,
                        onExpandedChange = { if (!state.isRunning) folderScanExpanded = !folderScanExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = if (state.recursive) "Recursive" else "Current folder",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Folder scan") },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = folderScanExpanded)
                                    IconButton(onClick = { 
                                        folderScanExpanded = false
                                        showFolderScanInfo = true 
                                    }) {
                                        Icon(painter = painterResource(id = R.drawable.info_24px), contentDescription = "Info")
                                    }
                                }
                            },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !state.isRunning).fillMaxWidth(),
                            enabled = !state.isRunning
                        )
                        ExposedDropdownMenu(
                            expanded = folderScanExpanded,
                            onDismissRequest = { folderScanExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Current folder") },
                                onClick = {
                                    viewModel.updateRecursive(false)
                                    folderScanExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Recursive") },
                                onClick = {
                                    viewModel.updateRecursive(true)
                                    folderScanExpanded = false
                                }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    var strategyExpanded by remember { mutableStateOf(false) }
                    
                    ExposedDropdownMenuBox(
                        expanded = strategyExpanded,
                        onExpandedChange = { if (!state.isRunning) strategyExpanded = !strategyExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = state.conflictStrategy.name.lowercase().replaceFirstChar { it.uppercase() },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.conflict_strategy)) },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = strategyExpanded)
                                    IconButton(onClick = { 
                                        strategyExpanded = false
                                        showConflictStrategyInfo = true 
                                    }) {
                                        Icon(painter = painterResource(id = R.drawable.info_24px), contentDescription = "Info")
                                    }
                                }
                            },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !state.isRunning).fillMaxWidth(),
                            enabled = !state.isRunning
                        )
                        ExposedDropdownMenu(
                            expanded = strategyExpanded,
                            onDismissRequest = { strategyExpanded = false }
                        ) {
                            ConflictStrategy.entries.forEach { strategy ->
                                DropdownMenuItem(
                                    text = { Text(strategy.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        viewModel.updateConflictStrategy(strategy)
                                        strategyExpanded = false
                                    }
                                )
                            }
                        }
                    }
    
                    var showExtensionPatternInfo by remember { mutableStateOf(false) }
                    var showFileNamePatternInfo by remember { mutableStateOf(false) }
                    var showDestFormatInfo by remember { mutableStateOf(false) }

                    if (showProfileInfo) {
                        AlertDialog(
                            onDismissRequest = { showProfileInfo = false },
                            title = { Text(stringResource(R.string.profile_info_title)) },
                            text = { Text(stringResource(R.string.profile_info_text)) },
                            confirmButton = { TextButton(onClick = { showProfileInfo = false }) { Text(stringResource(R.string.ok)) } }
                        )
                    }

                    if (showFolderScanInfo) {
                        AlertDialog(
                            onDismissRequest = { showFolderScanInfo = false },
                            title = { Text(stringResource(R.string.folder_scan_info_title)) },
                            text = { Text(stringResource(R.string.folder_scan_info_text)) },
                            confirmButton = { TextButton(onClick = { showFolderScanInfo = false }) { Text(stringResource(R.string.ok)) } }
                        )
                    }

                    if (showConflictStrategyInfo) {
                        AlertDialog(
                            onDismissRequest = { showConflictStrategyInfo = false },
                            title = { Text(stringResource(R.string.conflict_strategy_info_title)) },
                            text = { Text(stringResource(R.string.conflict_strategy_info_text)) },
                            confirmButton = { TextButton(onClick = { showConflictStrategyInfo = false }) { Text(stringResource(R.string.ok)) } }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.extensionPattern,
                        onValueChange = { viewModel.updateExtensionPattern(it) },
                        label = { Text(stringResource(R.string.extension_pattern_label)) },
                        trailingIcon = {
                            IconButton(onClick = { showExtensionPatternInfo = true }) {
                                Icon(painter = painterResource(id = R.drawable.info_24px), contentDescription = "Info")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isRunning
                    )

                    if (showExtensionPatternInfo) {
                        AlertDialog(
                            onDismissRequest = { showExtensionPatternInfo = false },
                            title = { Text(stringResource(R.string.extension_pattern_info_title)) },
                            text = { Text(stringResource(R.string.extension_pattern_info_text)) },
                            confirmButton = { TextButton(onClick = { showExtensionPatternInfo = false }) { Text(stringResource(R.string.ok)) } },
                            dismissButton = { 
                                TextButton(onClick = { 
                                    viewModel.updateExtensionPattern("arw,cr*,dng,mov,mp4,jpg,heic")
                                    showExtensionPatternInfo = false
                                }) { 
                                    Text("Defaults") 
                                } 
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.fileNamePattern,
                        onValueChange = { viewModel.updateFileNamePattern(it) },
                        label = { Text(stringResource(R.string.file_name_pattern_label)) },
                        trailingIcon = {
                            IconButton(onClick = { showFileNamePatternInfo = true }) {
                                Icon(painter = painterResource(id = R.drawable.info_24px), contentDescription = "Info")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isRunning
                    )

                    if (showFileNamePatternInfo) {
                        AlertDialog(
                            onDismissRequest = { showFileNamePatternInfo = false },
                            title = { Text(stringResource(R.string.file_name_pattern_info_title)) },
                            text = { Text(stringResource(R.string.file_name_pattern_info_text)) },
                            confirmButton = { TextButton(onClick = { showFileNamePatternInfo = false }) { Text(stringResource(R.string.ok)) } },
                            dismissButton = { 
                                TextButton(onClick = { 
                                    viewModel.updateFileNamePattern("*")
                                    showFileNamePatternInfo = false
                                }) { 
                                    Text("Defaults") 
                                } 
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.destFormat,
                        onValueChange = { viewModel.updateDestFormat(it) },
                        label = { Text(stringResource(R.string.dest_format_label)) },
                        trailingIcon = {
                            IconButton(onClick = { showDestFormatInfo = true }) {
                                Icon(painter = painterResource(id = R.drawable.info_24px), contentDescription = "Info")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isRunning
                    )

                    if (showDestFormatInfo) {
                        AlertDialog(
                            onDismissRequest = { showDestFormatInfo = false },
                            title = { Text(stringResource(R.string.dest_format_info_title)) },
                            text = { 
                                Text(stringResource(R.string.dest_format_info_text))
                            },
                            confirmButton = {
                                TextButton(onClick = { showDestFormatInfo = false }) {
                                    Text(stringResource(R.string.ok))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { 
                                    viewModel.updateDestFormat("{file_name}")
                                    showDestFormatInfo = false
                                }) {
                                    Text("Defaults")
                                }
                            }
                        )
                    }
                }
            }
        }
            
        // History
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(org.liberty.android.mediacopypro.HistoryList) }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "View History", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Copy Button
        Button(
            onClick = {
                viewModel.startCopy(context)
                onItemClick(org.liberty.android.mediacopypro.CopyProgress)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isRunning && state.srcUri != null && state.destUri != null
        ) {
            Text(stringResource(R.string.start_copy))
        }
    }
}


