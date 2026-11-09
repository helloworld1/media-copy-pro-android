package org.liberty.android.mediacopypro

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.lifecycle.viewmodel.compose.viewModel
import org.liberty.android.mediacopypro.ui.main.MainScreen
import org.liberty.android.mediacopypro.ui.main.CopyProgressScreen
import org.liberty.android.mediacopypro.ui.smb.ServerListScreen
import org.liberty.android.mediacopypro.ui.smb.SmbDirectoryScreen
import org.liberty.android.mediacopypro.ui.main.CopiedFilesListScreen
import org.liberty.android.mediacopypro.ui.main.HistoryListScreen

@Composable
fun MainNavigation(viewModel: MainViewModel = viewModel()) {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(
              onItemClick = { navKey -> backStack.add(navKey) },
              modifier = Modifier.padding(16.dp),
              viewModel = viewModel
          )
        }
        entry<CopyProgress> {
          CopyProgressScreen(
              onDone = {
                  viewModel.resetProgress()
                  backStack.removeLastOrNull()
              },
              onViewCopiedFiles = {
                  val ts = viewModel.uiState.value.currentJobTimestamp
                  if (ts > 0) backStack.add(CopiedFilesList(ts))
              },
              modifier = Modifier.padding(16.dp),
              viewModel = viewModel
          )
        }
        entry<CopiedFilesList> {
          val key = it as CopiedFilesList
          CopiedFilesListScreen(
              timestamp = key.timestamp,
              onBack = { backStack.removeLastOrNull() }
          )
        }
        entry<HistoryList> {
          HistoryListScreen(
              onHistoryClick = { ts -> backStack.add(CopiedFilesList(ts)) },
              onBack = { backStack.removeLastOrNull() }
          )
        }
        entry<ServerList> {
          val key = it as ServerList
          org.liberty.android.mediacopypro.ui.smb.ServerListScreen(
              isSource = key.isSource,
              onAddServer = { backStack.add(ServerEdit(null, key.isSource)) },
              onEditServer = { serverId -> backStack.add(ServerEdit(serverId, key.isSource)) },
              onServerSelected = { serverId -> backStack.add(SmbDirectory(serverId, key.isSource)) },
              onBack = { backStack.removeLastOrNull() }
          )
        }
        entry<ServerEdit> {
          val key = it as ServerEdit
          org.liberty.android.mediacopypro.ui.smb.ServerEditScreen(
              serverId = key.serverId,
              onDone = { backStack.removeLastOrNull() },
              onBack = { backStack.removeLastOrNull() }
          )
        }
        entry<SmbDirectory> {
          val key = it as SmbDirectory
          org.liberty.android.mediacopypro.ui.smb.SmbDirectoryScreen(
              serverId = key.serverId,
              isSource = key.isSource,
              viewModel = viewModel,
              onFolderSelected = {
                  // We pop twice: back to ServerList, then back to Main. 
                  // But rememberNavBackStack doesn't have an easy popTo, so we can just pop until Main is reached
                  while (backStack.size > 1) {
                      backStack.removeLastOrNull()
                  }
              },
              onBack = { backStack.removeLastOrNull() }
          )
        }
      },
  )
}
