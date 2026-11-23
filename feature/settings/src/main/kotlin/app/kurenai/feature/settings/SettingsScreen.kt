package app.kurenai.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.GTranslate
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kurenai.core.model.AppSettings
import app.kurenai.core.model.Palette
import app.kurenai.core.model.ThemeMode

private data class SettingRow(
    val title: String,
    val subtitle: String,
    val section: String,
    val icon: ImageVector,
    val checked: Boolean? = null,
    val onClick: (() -> Unit)? = null,
    val onCheckedChange: ((Boolean) -> Unit)? = null,
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenSources: () -> Unit,
    onOpenAnimated: () -> Unit,
    onOpenTranslation: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenTrackers: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenWhatsNew: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenTaste: () -> Unit,
    onOpenAiSettings: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("All") }
    var appearanceOpen by remember { mutableStateOf(false) }
    val settings = state.settings

    val rows = buildList {
        add(SettingRow("Appearance", "Palette, dark mode, dynamic color, font size", "Appearance", Icons.Rounded.Palette, onClick = { appearanceOpen = true }))
        add(SettingRow("Dynamic color", "Use Material You Monet colors", "Appearance", Icons.Rounded.ColorLens, settings.theme.dynamicColor) { value ->
            viewModel.update { it.copy(theme = it.theme.copy(dynamicColor = value)) }
        })
        add(SettingRow("AMOLED black", "Pure black surfaces for OLED panels", "Appearance", Icons.Rounded.DarkMode, settings.theme.amoled) { value ->
            viewModel.update { it.copy(theme = it.theme.copy(amoled = value)) }
        })
        add(SettingRow("Kurenai crimson", "Warm coffee base with crimson accent", "Appearance", Icons.Rounded.Brightness6) {
            viewModel.setPalette(Palette.KURENAI)
        })
        add(SettingRow("Animated Chapters", "Quality, motion, voices, cache", "Reader", Icons.Rounded.Animation, onClick = onOpenAnimated))
        add(SettingRow("Translation & learning", "OCR, overlays, vocabulary, SRS", "Reader", Icons.Rounded.GTranslate, onClick = onOpenTranslation))
        add(SettingRow("Downloads & storage", "Queue, smart rules, cleanup", "Downloads", Icons.Rounded.Download, onClick = onOpenDownloads))
        add(SettingRow("Wi-Fi only downloads", "Wait for an unmetered network", "Downloads", Icons.Rounded.Wifi, settings.wifiOnlyDownloads) { value ->
            viewModel.update { it.copy(wifiOnlyDownloads = value) }
        })
        add(SettingRow("Battery-aware work", "Pause background work on low battery", "Downloads", Icons.Rounded.BatterySaver, settings.batteryAwareWork) { value ->
            viewModel.update { it.copy(batteryAwareWork = value) }
        })
        add(SettingRow("Low-memory mode", "Smaller caches, lighter prefetch, reduced effects", "Downloads", Icons.Rounded.Memory, settings.lowMemoryMode) { value ->
            viewModel.update { it.copy(lowMemoryMode = value) }
        })
        add(SettingRow("Sources", "Enable by language, type, rating; health dashboard", "Sources", Icons.Rounded.Language, onClick = onOpenSources))
        add(SettingRow("Network & proxy", "Cloudflare handling, DoH, timeouts, diagnostics", "Network", Icons.Rounded.Security, onClick = onOpenDiagnostics))
        add(SettingRow("Automatic challenge handling", "Resolve Cloudflare challenges in a hidden WebView", "Network", Icons.Rounded.CloudQueue, settings.challengeHandlingEnabled) { value ->
            viewModel.update { it.copy(challengeHandlingEnabled = value) }
        })
        add(SettingRow("Encrypted DNS", "DoH with system-DNS fallback", "Network", Icons.Rounded.Security, settings.dohEnabled) { value ->
            viewModel.update { it.copy(dohEnabled = value) }
        })
        add(SettingRow("Tracking & sync", "AniList, MyAnimeList, Shikimori, Kitsu", "Tracking", Icons.Rounded.Sync, onClick = onOpenTrackers))
        add(SettingRow("Backup & restore", "Local, encrypted, or your own cloud", "Advanced", Icons.Rounded.Backup, onClick = onOpenBackup))
        add(SettingRow("Incognito mode", "Do not record history or progress", "Privacy", Icons.Rounded.VisibilityOff, settings.incognito) { value ->
            viewModel.update { it.copy(incognito = value) }
        })
        add(SettingRow("App lock", "Require device authentication when opening Kurenai", "Privacy", Icons.Rounded.Lock, settings.appLockEnabled) { value ->
            viewModel.update { it.copy(appLockEnabled = value) }
        })
        add(SettingRow("Biometric unlock", "Use fingerprint or face where available", "Privacy", Icons.Rounded.Fingerprint, settings.biometricLockEnabled) { value ->
            viewModel.update { it.copy(biometricLockEnabled = value) }
        })
        add(SettingRow("Adult content", "19+ sources stay hidden until unlocked", "Privacy", Icons.Rounded.Security, settings.adultContentUnlocked) { value ->
            viewModel.update { it.copy(adultContentUnlocked = value) }
        })
        add(SettingRow("Recommendations", "On-device taste profile and suggestions", "Privacy", Icons.Rounded.AutoAwesome, settings.recommendationsEnabled) { value ->
            viewModel.update { it.copy(recommendationsEnabled = value) }
        })
        add(SettingRow("Taste profile", "Edit or reset what Kurenai has learned", "Privacy", Icons.Rounded.AutoAwesome, onClick = onOpenTaste))
        add(SettingRow("AI librarian", "Optional cloud provider and keys", "Privacy", Icons.Rounded.AddCircle, onClick = onOpenAiSettings))
        add(SettingRow("Notifications", "New chapter alerts and update digest", "Notifications", Icons.Rounded.Notifications, settings.notificationsEnabled) { value ->
            viewModel.update { it.copy(notificationsEnabled = value) }
        })
        add(SettingRow("Reading stats", "Streaks, pages, year in review", "Advanced", Icons.Rounded.Tune, onClick = onOpenStats))
        add(SettingRow("Help Center", "Guides, FAQ and source troubleshooting", "About", Icons.Rounded.Help, onClick = onOpenHelp))
        add(SettingRow("What's New", "Recent changes and roadmap highlights", "About", Icons.Rounded.Info, onClick = onOpenWhatsNew))
        add(SettingRow("About Kurenai", "Version, licenses, privacy and DMCA", "About", Icons.Rounded.Info, onClick = onOpenAbout))
    }

    val sections = listOf("All", "Appearance", "Reader", "Downloads", "Sources", "Network", "Tracking", "Privacy", "Notifications", "Language", "Advanced", "About")
    val filtered = rows.filter { row ->
        (section == "All" || row.section == section) &&
            (query.isBlank() || row.title.contains(query, true) || row.subtitle.contains(query, true) || row.section.contains(query, true))
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("A searchable tree for everything Kurenai can do.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search settings") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )
        }
        PrimaryTabRow(selectedTabIndex = sections.indexOf(section).coerceAtLeast(0)) {
            sections.forEach { label ->
                Tab(selected = section == label, onClick = { section = label }, text = { Text(label, style = MaterialTheme.typography.labelSmall) })
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered, key = { it.title + it.section }) { row ->
                SettingRowView(row)
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (appearanceOpen) {
        AppearanceDialog(
            settings = settings,
            onMode = viewModel::setThemeMode,
            onPalette = viewModel::setPalette,
            onDynamic = { value -> viewModel.update { it.copy(theme = it.theme.copy(dynamicColor = value)) } },
            onAmoled = { value -> viewModel.update { it.copy(theme = it.theme.copy(amoled = value)) } },
            onDismiss = { appearanceOpen = false },
        )
    }
}

@Composable
private fun AppearanceDialog(
    settings: AppSettings,
    onMode: (ThemeMode) -> Unit,
    onPalette: (Palette) -> Unit,
    onDynamic: (Boolean) -> Unit,
    onAmoled: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Appearance") },
        text = {
            Column {
                Text("Mode", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.theme.mode == mode,
                            onClick = { onMode(mode) },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Palette", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Palette.entries.take(3).forEach { palette ->
                        FilterChip(
                            selected = !settings.theme.dynamicColor && settings.theme.palette == palette,
                            onClick = { onPalette(palette) },
                            label = { Text(palette.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Palette.entries.drop(3).forEach { palette ->
                        FilterChip(
                            selected = !settings.theme.dynamicColor && settings.theme.palette == palette,
                            onClick = { onPalette(palette) },
                            label = { Text(palette.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Dynamic Monet colors", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = settings.theme.dynamicColor, onCheckedChange = onDynamic)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("AMOLED black", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = settings.theme.amoled, onCheckedChange = onAmoled)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun SettingRowView(row: SettingRow) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = row.onClick != null || row.onCheckedChange != null) {
                if (row.checked != null && row.onCheckedChange != null) {
                    row.onCheckedChange(!row.checked)
                } else {
                    row.onClick?.invoke()
                }
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(row.icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.titleSmall)
            Text(row.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (row.checked != null) {
            Switch(checked = row.checked, onCheckedChange = row.onCheckedChange)
        }
    }
}
