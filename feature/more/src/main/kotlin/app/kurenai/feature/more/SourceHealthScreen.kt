package app.kurenai.feature.more

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kurenai.core.designsystem.theme.LocalKurenaiColors
import app.kurenai.core.model.SourceHealth
import app.kurenai.core.model.SourceInfo
import app.kurenai.core.source.SourceCatalog

@Composable
fun SourceHealthScreen(
    onBack: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var language by remember { mutableStateOf<String?>(null) }

    val languages = SourceCatalog.PopularLanguages
    val filtered = state.sources.filter { source ->
        (language == null || source.language == language) &&
            (query.isBlank() || source.name.contains(query, true) || source.language.contains(query, true)) &&
            (!source.nsfw || state.settings.adultContentUnlocked)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("Source health", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${state.sources.size} built-in parsers · ${state.sources.count { it.health == SourceHealth.HEALTHY }} healthy", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = viewModel::repairSources) { Text("Repair") }
            TextButton(onClick = { viewModel.setAdultUnlocked(!state.settings.adultContentUnlocked) }) {
                Text(if (state.settings.adultContentUnlocked) "Lock 18+" else "Unlock 18+")
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search sources") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(selected = language == null, onClick = { language = null }, label = { Text("All languages") })
            }
            items(languages) { lang ->
                FilterChip(selected = language == lang, onClick = { language = if (language == lang) null else lang }, label = { Text(lang.uppercase()) })
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(filtered, key = { it.id }) { source ->
                SourceHealthRow(
                    source = source,
                    enabled = source.health != SourceHealth.DISABLED,
                    onToggle = { viewModel.setSourceEnabled(source.id, it) },
                    onReset = { viewModel.resetSourceHealth(source.id) },
                    onReport = {
                        val report = buildString {
                            appendLine("Kurenai source report")
                            appendLine("source=${source.id} (${source.name})")
                            appendLine("language=${source.language} domain=${source.domain} kind=${source.kind}")
                            appendLine("health=${source.health} license=${source.license}")
                            appendLine("challenge=${state.challenge.status} ${state.challenge.message}")
                            appendLine("network log:")
                            viewModel.networkLog().forEach { appendLine("${it.tag}: ${it.message}") }
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Broken source: ${source.name}")
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(intent, "Report broken source"))
                    },
                )
            }
        }
    }
}

@Composable
private fun SourceHealthRow(
    source: SourceInfo,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onReset: () -> Unit,
    onReport: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = when (source.health) {
            SourceHealth.HEALTHY -> LocalKurenaiColors.current.success
            SourceHealth.DEGRADED -> LocalKurenaiColors.current.warning
            SourceHealth.BROKEN, SourceHealth.DISABLED -> MaterialTheme.colorScheme.error
            SourceHealth.UNKNOWN -> MaterialTheme.colorScheme.outline
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(Modifier.size(7.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
                Text(source.name, style = MaterialTheme.typography.titleSmall)
            }
            Text(
                "${source.languageLabel} · ${source.kind.label} · ${source.license.badge} · ${source.health.name.lowercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                TextButton(onClick = onReset, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Reset health", style = MaterialTheme.typography.labelSmall) }
                TextButton(onClick = onReport, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Icon(Icons.Rounded.Report, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Report", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}
