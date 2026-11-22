package app.kurenai.feature.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MoreScreen(
    onOpenDownloads: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenTaste: () -> Unit,
    onOpenTrackers: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenWhatsNew: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val items = listOf(
        MoreItem("Downloads", "Queue, offline chapters and storage", Icons.Rounded.Download, onOpenDownloads),
        MoreItem("Sources", "Catalogue, languages, health and updates", Icons.Rounded.TravelExplore, onOpenSources),
        MoreItem("Reading stats", "Streaks, pages and your year in review", Icons.Rounded.Insights, onOpenStats),
        MoreItem("Taste profile", "What Kurenai has learned, and why", Icons.Rounded.AutoAwesome, onOpenTaste),
        MoreItem("Tracking & sync", "AniList, MyAnimeList, Shikimori, Kitsu", Icons.Rounded.Sync, onOpenTrackers),
        MoreItem("Backup & restore", "Encrypted local export or your own cloud", Icons.Rounded.Backup, onOpenBackup),
        MoreItem("Network diagnostics", "Last challenge, DoH, proxy and request log", Icons.Rounded.NetworkCheck, onOpenDiagnostics),
        MoreItem("Help Center", "Guides, FAQ and source troubleshooting", Icons.Rounded.Help, onOpenHelp),
        MoreItem("What's New", "Release notes and roadmap", Icons.Rounded.Info, onOpenWhatsNew),
        MoreItem("About Kurenai", "Privacy, licenses, DMCA and credits", Icons.Rounded.Info, onOpenAbout),
    )
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Kurenai", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                "A cozy, cinematic reader. No ads, no analytics, no hosted content.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        items(items.size) { index ->
            val item = items[index]
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f))
                    .clickable(onClick = item.onClick)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(item.icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                    Text(item.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primaryContainer).clickable(onClick = onOpenSettings).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("All settings", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("Open", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private data class MoreItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)
