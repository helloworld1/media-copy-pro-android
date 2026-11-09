package org.liberty.android.mediacopypro.ui.main

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
import kotlinx.coroutines.launch
import org.liberty.android.mediacopypro.data.CopyHistory
import org.liberty.android.mediacopypro.data.HistoryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryListScreen(
    onHistoryClick: (Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var histories by remember { mutableStateOf<List<CopyHistory>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        histories = HistoryManager.getHistories(context)
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Copy History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = org.liberty.android.mediacopypro.R.drawable.arrow_back_24px),
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                HistoryManager.clearAll(context)
                                histories = emptyList()
                            }
                        }
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = org.liberty.android.mediacopypro.R.drawable.delete_history_24px),
                            contentDescription = "Clear History"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (histories.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No history available.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(histories) { history ->
                    HistoryItemRow(history = history, onClick = { onHistoryClick(history.timestamp) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun HistoryItemRow(history: CopyHistory, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateStr = dateFormat.format(Date(history.timestamp))
        Text(text = dateStr, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        if (history.srcBaseDir.isNotEmpty()) {
            Text(text = "Src: ${history.srcBaseDir}", style = MaterialTheme.typography.bodySmall)
        }
        if (history.destBaseDir.isNotEmpty()) {
            Text(text = "Dest: ${history.destBaseDir}", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(4.dp))
        
        val total = history.details.size
        val copied = history.details.count { it.status == org.liberty.android.mediacopypro.data.CopyStatus.COPIED }
        val failed = history.details.count { it.status == org.liberty.android.mediacopypro.data.CopyStatus.FAILED }
        val skipped = history.details.count { it.status == org.liberty.android.mediacopypro.data.CopyStatus.SKIPPED }
        val renamed = history.details.count { it.status == org.liberty.android.mediacopypro.data.CopyStatus.RENAMED }
        
        Text(text = "Total: $total | Copied: $copied | Failed: $failed", style = MaterialTheme.typography.bodyMedium)
        Text(text = "Skipped: $skipped | Renamed: $renamed", style = MaterialTheme.typography.bodyMedium)
    }
}
