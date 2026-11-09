package org.liberty.android.mediacopypro.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.liberty.android.mediacopypro.MainViewModel

internal fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun CopyProgressScreen(
    onDone: () -> Unit,
    onViewCopiedFiles: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    DisposableEffect(state.isRunning, state.isDone) {
        val activity = context.findActivity()
        if (state.isRunning && !state.isDone) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Copy Progress", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Status: ${state.statusText}", style = MaterialTheme.typography.titleMedium)
                
                Spacer(modifier = Modifier.height(16.dp))

                if (state.totalFiles > 0) {
                    val progress = if (state.totalFiles > 0) state.filesCopied.toFloat() / state.totalFiles else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Files: ${state.filesCopied} / ${state.totalFiles}")
                    
                    val mbCopied = state.bytesCopied / (1024.0 * 1024.0)
                    val mbTotal = state.totalBytesToCopy / (1024.0 * 1024.0)
                    
                    Text("Sizes: ${String.format("%.1f", mbCopied)} MB / ${String.format("%.1f", mbTotal)} MB")
                    if (state.isDone) {
                        Text("Average Speed: ${state.averageSpeed}")
                        Text("Total Time: ${state.elapsedTime}")
                    } else {
                        Text("Speed: ${state.copySpeed}")
                        Text("Elapsed Time: ${state.elapsedTime}")
                        Text("Estimated Time: ${state.estimatedTime}")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Skipped: ${state.skippedFilesCount}")
                    Text("Renamed: ${state.renamedFilesCount}")
                    Text("Failed: ${state.failedFilesCount}")
                } else if (state.totalBytesToCopy > 0) {
                    // Scanning phase
                    val mbTotal = state.totalBytesToCopy / (1024.0 * 1024.0)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Files Found: ${state.totalFiles}")
                    Text("Accumulated Size: ${String.format("%.1f", mbTotal)} MB")
                }
            }
        }

        if (state.isDone) {
            if (state.currentJobTimestamp > 0) {
                Button(
                    onClick = { onViewCopiedFiles() },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("View Copied Files Details")
                }
            }

            Button(
                onClick = { onDone() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }
        } else {
            Button(
                onClick = { viewModel.cancelCopy() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isCancelled
            ) {
                Text(if (state.isCancelled) "Cancelling..." else "Cancel")
            }
        }
        
        if (state.isDone && state.warnings.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Warnings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(modifier = Modifier.height(8.dp))
                    for (warning in state.warnings) {
                        Text(text = warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}
