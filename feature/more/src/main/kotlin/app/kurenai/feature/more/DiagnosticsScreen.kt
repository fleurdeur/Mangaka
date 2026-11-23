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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Column {
                    Text("Network & proxy", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Cloudflare, DoH, cookies and diagnostics", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
                Column(Modifier.padding(16.dp)) {
                    Text("Last challenge result", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (state.challenge.at == 0L) "No challenge resolved this session" else {
                            "${state.challenge.status.uppercase()} · ${state.challenge.host}\n${state.challenge.message}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.challenge.at > 0) {
                        Text(
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(state.challenge.at)),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
        item {
            ToggleRow("Automatic challenge handling", state.settings.challengeHandlingEnabled) { viewModel.setChallengeHandling(it) }
            ToggleRow("Encrypted DNS (DoH)", state.settings.dohEnabled) { viewModel.setDoh(it) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.clearCookies() }) {
                    Icon(Icons.Rounded.DeleteSweep, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Clear cookies")
                }
                OutlinedButton(onClick = { viewModel.refreshStats() }) {
                    Icon(Icons.Rounded.Security, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Re-check")
                }
            }
        }
        item {
            Text("Request log", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("Local only. Never uploaded automatically.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(viewModel.networkLog()) { entry ->
            Column(Modifier.fillMaxWidth()) {
                Text("[${entry.tag}] ${entry.message}", style = MaterialTheme.typography.bodySmall)
                Text(
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entry.at)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
