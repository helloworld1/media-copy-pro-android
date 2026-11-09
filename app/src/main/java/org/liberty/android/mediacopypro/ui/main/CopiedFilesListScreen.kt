package org.liberty.android.mediacopypro.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.liberty.android.mediacopypro.data.CopyHistory
import org.liberty.android.mediacopypro.data.HistoryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.liberty.android.mediacopypro.data.CopyStatus
import org.liberty.android.mediacopypro.data.CopiedFileDetail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopiedFilesListScreen(
    timestamp: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var history by remember { mutableStateOf<CopyHistory?>(null) }
    
    LaunchedEffect(timestamp) {
        history = HistoryManager.getHistory(context, timestamp)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Copied Files Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = org.liberty.android.mediacopypro.R.drawable.arrow_back_24px),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        val currentHistory = history
        if (currentHistory == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (currentHistory.details.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No files processed.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                item {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val dateStr = dateFormat.format(Date(currentHistory.timestamp))
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Started at: $dateStr", 
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (currentHistory.srcBaseDir.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Src: ${currentHistory.srcBaseDir}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (currentHistory.destBaseDir.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Dest: ${currentHistory.destBaseDir}", style = MaterialTheme.typography.bodySmall)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val total = currentHistory.details.size
                        val copied = currentHistory.details.count { it.status == org.liberty.android.mediacopypro.data.CopyStatus.COPIED }
                        val failed = currentHistory.details.count { it.status == org.liberty.android.mediacopypro.data.CopyStatus.FAILED }
                        val skipped = currentHistory.details.count { it.status == org.liberty.android.mediacopypro.data.CopyStatus.SKIPPED }
                        val renamed = currentHistory.details.count { it.status == org.liberty.android.mediacopypro.data.CopyStatus.RENAMED }
                        
                        Text("Total: $total | Copied: $copied | Failed: $failed", style = MaterialTheme.typography.bodyMedium)
                        Text("Skipped: $skipped | Renamed: $renamed", style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider()
                }
                
                items(currentHistory.details) { detail ->
                    CopiedFileItemRow(detail)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun CopiedFileItemRow(detail: CopiedFileDetail) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = "Source: ${detail.srcName}", style = MaterialTheme.typography.bodyMedium)
        Text(text = "Dest: ${detail.destName}", style = MaterialTheme.typography.bodyMedium)
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val sizeMb = detail.sizeBytes / (1024.0 * 1024.0)
            Text(text = String.format("Size: %.2f MB", sizeMb), style = MaterialTheme.typography.bodySmall)
            
            val statusColor = when (detail.status) {
                CopyStatus.COPIED -> MaterialTheme.colorScheme.primary
                CopyStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                CopyStatus.FAILED -> MaterialTheme.colorScheme.error
                CopyStatus.RENAMED -> MaterialTheme.colorScheme.tertiary
            }
            Text(
                text = detail.status.name + (if (detail.errorMessage != null) " - ${detail.errorMessage}" else ""),
                style = MaterialTheme.typography.labelLarge,
                color = statusColor
            )
        }
    }
}
