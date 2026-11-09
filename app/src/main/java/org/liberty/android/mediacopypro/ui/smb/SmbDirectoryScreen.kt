package org.liberty.android.mediacopypro.ui.smb

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.liberty.android.mediacopypro.MainViewModel
import org.liberty.android.mediacopypro.data.SmbAppFile
import org.liberty.android.mediacopypro.data.SmbManager
import org.liberty.android.mediacopypro.data.SmbServer
import org.liberty.android.mediacopypro.data.SmbServerRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmbDirectoryScreen(
    serverId: String,
    isSource: Boolean,
    viewModel: MainViewModel,
    onFolderSelected: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { SmbServerRepository(context) }
    var server by remember { mutableStateOf<SmbServer?>(null) }

    var currentPath by remember { mutableStateOf("") }
    var folders by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(serverId) {
        server = repository.getServer(serverId)
    }

    LaunchedEffect(server, currentPath) {
        if (server == null) return@LaunchedEffect
        isLoading = true
        errorMsg = null
        try {
            val list = withContext(Dispatchers.IO) {
                val share = SmbManager.getShare(server!!)
                val rootFile = SmbAppFile(server!!, share, currentPath, true)
                rootFile.listFiles().filter { it.isDirectory }.map { it.name }
            }
            folders = list.sorted()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMsg = e.message ?: "Unknown error connecting to SMB"
        } finally {
            isLoading = false
        }
    }


    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCreateFolderDialog = false
                    val nameToCreate = newFolderName
                    if (nameToCreate.isNotBlank() && server != null) {
                        isLoading = true
                        coroutineScope.launch {
                            try {
                                val list = withContext(Dispatchers.IO) {
                                    val share = SmbManager.getShare(server!!)
                                    val rootFile = SmbAppFile(server!!, share, currentPath, true)
                                    rootFile.createDirectory(nameToCreate)
                                    rootFile.listFiles().filter { it.isDirectory }.map { it.name }
                                }
                                folders = list.sorted()
                            } catch (e: Exception) {
                                errorMsg = e.message ?: "Failed to create folder"
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                    newFolderName = ""
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showCreateFolderDialog = false 
                    newFolderName = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(server?.name ?: "SMB") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentPath.isEmpty()) {
                            onBack()
                        } else {
                            currentPath = currentPath.substringBeforeLast('\\', "")
                        }
                    }) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = org.liberty.android.mediacopypro.R.drawable.arrow_back_24px),
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showCreateFolderDialog = true }) {
                        Text("+ Folder")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Button(
                    onClick = {
                        val uriStr = "smb://${server?.id}/${currentPath.replace('\\', '/')}"
                        if (isSource) {
                            viewModel.updateSrcUri(uriStr)
                        } else {
                            viewModel.updateDestUri(uriStr)
                        }
                        onFolderSelected()
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Text("Use this folder")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                text = "Current Path: /${currentPath.replace('\\', '/')}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (errorMsg != null) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("Error: $errorMsg", color = MaterialTheme.colorScheme.error)
                }
            } else if (folders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Folder is empty")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(folders) { folderName ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentPath = if (currentPath.isEmpty()) folderName else "$currentPath\\$folderName"
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📁", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(folderName, style = MaterialTheme.typography.bodyLarge)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
