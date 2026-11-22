package app.kurenai.feature.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kurenai.core.model.TrackerService

@Composable
fun TrackersScreen(
    onBack: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TrackerService?>(null) }
    var token by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text("Tracking & sync", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Two-way progress sync", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { viewModel.syncAllTrackers() }) { Icon(Icons.Rounded.Sync, "Sync all") }
            }
        }
        item {
            Text(
                "Tokens are stored in Android Keystore-backed encrypted preferences and are never included in backups or logs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(TrackerService.entries.size) { index ->
            val service = TrackerService.entries[index]
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(service.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (viewModel.getSecret("tracker_${service.name.lowercase()}_token").isNullOrBlank()) "Not connected" else "Token saved",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AssistChip(onClick = {
                        editing = service
                        token = ""
                    }, label = { Text(if (editing == service) "Editing" else "Set token") })
                }
                if (editing == service) {
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        placeholder = { Text("Paste ${service.title} access token") },
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        Button(onClick = {
                            viewModel.saveTrackerToken(service, token)
                            editing = null
                        }) { Text("Save securely") }
                        OutlinedButton(onClick = { editing = null }) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    var password by remember { mutableStateOf("") }
    var encrypt by remember { mutableStateOf(true) }
    var payload by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Column {
                    Text("Backup & restore", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Your data, your storage", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Passphrase (required when encrypting)") },
                singleLine = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AES-256-GCM encryption", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.Switch(checked = encrypt, onCheckedChange = { encrypt = it })
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    viewModel.exportBackup(encrypt, password.takeIf { it.isNotBlank() }) { data ->
                        payload = data
                        result = "Backup ready · ${data.length / 1024} KB. Copy it somewhere safe."
                    }
                }) { Text("Create backup") }
                OutlinedButton(onClick = {
                    if (payload.isBlank()) {
                        result = "Create a backup first, or paste one below."
                    } else {
                        viewModel.importBackup(payload, password.takeIf { it.isNotBlank() }) { result = it }
                    }
                }) { Text("Restore") }
            }
        }
        item {
            Text("Backup payload", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = payload,
                onValueChange = { payload = it },
                modifier = Modifier.fillMaxWidth().height(220.dp),
                placeholder = { Text("Encrypted KURENAI1 payload or plain JSON") },
                textStyle = MaterialTheme.typography.bodySmall,
            )
        }
        result?.let { item { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) } }
        item {
            Text(
                "Backups include favorites, chapters, shelves, progress and settings. Secrets, cookies and tracker tokens are deliberately excluded.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
