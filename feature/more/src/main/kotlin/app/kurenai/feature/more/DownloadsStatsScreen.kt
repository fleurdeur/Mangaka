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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kurenai.core.designsystem.components.CoverImage
import app.kurenai.core.designsystem.components.EmptyState
import app.kurenai.core.model.DownloadState

@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("Downloads", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${state.downloads.size} tasks · polite per-host queue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { viewModel.clearFinishedDownloads() }) { Text("Clear finished") }
        }
        if (state.downloads.isEmpty()) {
            EmptyState(
                title = "No downloads yet",
                message = "Queue chapters from a series page. Automatic rules can fetch the next five chapters on Wi-Fi while charging.",
                icon = Icons.Rounded.Download,
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.downloads, key = { it.id }) { task ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoverImage(task.manga.coverUrl, task.manga.title, Modifier.size(48.dp, 66.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(task.manga.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${task.chapter.displayTitle} · ${task.state.name.lowercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (task.total > 0) {
                                LinearProgressIndicator(
                                    progress = { task.progress.toFloat() / task.total },
                                    modifier = Modifier.fillMaxWidth().height(3.dp).padding(top = 4.dp),
                                )
                            }
                            task.error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 1) }
                        }
                        when (task.state) {
                            DownloadState.RUNNING, DownloadState.QUEUED -> IconButton(onClick = { viewModel.cancelDownload(task.id) }) {
                                Icon(Icons.Rounded.Cancel, "Cancel")
                            }
                            DownloadState.FAILED -> IconButton(onClick = { viewModel.retryDownload(task.id) }) {
                                Icon(Icons.Rounded.Refresh, "Retry")
                            }
                            else -> IconButton(onClick = { viewModel.removeDownload(task.id) }) {
                                Icon(Icons.Rounded.Delete, "Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Column {
                    Text("Reading stats", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Private, on-device, yours", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Streak", "${state.stats.streakDays} days", Modifier.weight(1f))
                StatCard("Pages", "${state.stats.pagesRead}", Modifier.weight(1f))
                StatCard("Minutes", "${state.stats.minutesRead}", Modifier.weight(1f))
            }
        }
        item {
            Text("Year in review ${state.yearReview?.year ?: ""}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            state.yearReview?.let { review ->
                StatCard("Your year", "${review.chapters} chapters · ${review.titles} titles · ${review.minutes} minutes", Modifier.fillMaxWidth())
            } ?: Text("Read a little to unlock your year in review.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            OutlinedButton(onClick = { viewModel.refreshStats() }, modifier = Modifier.fillMaxWidth()) { Text("Refresh stats") }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun TasteScreen(
    onBack: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Column {
                    Text("Taste profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Learned locally from favorites and reading time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recommendations", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                androidx.compose.material3.Switch(checked = state.settings.recommendationsEnabled, onCheckedChange = { viewModel.setRecommendations(it) })
            }
        }
        if (state.taste.isEmpty()) {
            item { Text("Favorite a few series and your taste profile appears here.", style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(state.taste.entries.sortedByDescending { it.value }.take(24).toList()) { (tag, weight) ->
                Column(Modifier.fillMaxWidth()) {
                    Row {
                        Text(tag, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("%.0f".format(weight), style = MaterialTheme.typography.labelSmall)
                    }
                    LinearProgressIndicator(
                        progress = { (weight / (state.taste.values.maxOrNull() ?: 1f)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                    )
                }
            }
            item {
                OutlinedButton(onClick = { viewModel.refreshTaste() }, modifier = Modifier.fillMaxWidth()) { Text("Recompute") }
            }
        }
    }
}
