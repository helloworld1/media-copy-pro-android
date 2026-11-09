package org.liberty.android.mediacopypro.ui.smb

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.liberty.android.mediacopypro.data.SmbServer
import org.liberty.android.mediacopypro.data.SmbServerRepository
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEditScreen(
    serverId: String?,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { SmbServerRepository(context) }
    
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var share by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(serverId) {
        if (serverId != null) {
            val s = repository.getServer(serverId)
            if (s != null) {
                name = s.name
                host = s.host
                share = s.share
                domain = s.domain
                username = s.username
                password = s.password
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (serverId == null) "Add Server" else "Edit Server") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Cancel")
                    }
                },
                actions = {
                    if (serverId != null) {
                        TextButton(
                            onClick = {
                                repository.removeServer(serverId)
                                onDone()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Friendly Name (e.g. My NAS)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Host / IP Address") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = share,
                onValueChange = { share = it },
                label = { Text("Share Name (e.g. public)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = domain,
                onValueChange = { domain = it },
                label = { Text("Domain (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (name.isNotBlank() && host.isNotBlank() && share.isNotBlank()) {
                        val newServer = SmbServer(
                            id = serverId ?: UUID.randomUUID().toString(),
                            name = name,
                            host = host,
                            share = share,
                            domain = domain,
                            username = username,
                            password = password
                        )
                        if (serverId == null) {
                            repository.addServer(newServer)
                        } else {
                            repository.updateServer(newServer)
                        }
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && host.isNotBlank() && share.isNotBlank()
            ) {
                Text("Save")
            }
        }
    }
}
