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
import org.liberty.android.mediacopypro.data.SmbServerRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    isSource: Boolean,
    onAddServer: () -> Unit,
    onEditServer: (String) -> Unit,
    onServerSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { SmbServerRepository(context) }
    val servers by repository.servers.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select SMB Server") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = org.liberty.android.mediacopypro.R.drawable.arrow_back_24px),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddServer) {
                Text("+")
            }
        }
    ) { padding ->
        if (servers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No servers added. Tap + to add one.")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(servers) { server ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .clickable { onServerSelected(server.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(server.name, style = MaterialTheme.typography.titleMedium)
                                Text("${server.host}/${server.share}", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { onEditServer(server.id) }) {
                                Text("Edit")
                            }
                        }
                    }
                }
            }
        }
    }
}
