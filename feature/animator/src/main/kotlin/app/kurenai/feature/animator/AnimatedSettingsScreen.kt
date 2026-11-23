package app.kurenai.feature.animator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kurenai.core.model.AnimationQuality
import app.kurenai.core.model.MotionIntensity
import app.kurenai.core.model.TtsProviderKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedSettingsScreen(
    onBack: () -> Unit,
    viewModel: AnimatedSettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Animated chapters", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Panel detection, lightweight character rigs, director camera moves and narration. Everything is rendered on-device and cached per chapter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { ToggleRow("Enable animated chapters", "Show the animate button in the reader", settings.enabled) { value -> viewModel.update { it.copy(enabled = value) } } }
            item { ToggleRow("Narration", "Speak bubbles and captions", settings.narrationEnabled) { value -> viewModel.update { it.copy(narrationEnabled = value) } } }
            item { ToggleRow("Subtitles", "Show timed captions", settings.subtitles) { value -> viewModel.update { it.copy(subtitles = value) } } }
            item { ToggleRow("Karaoke highlight", "Highlight the spoken word", settings.karaoke) { value -> viewModel.update { it.copy(karaoke = value) } } }
            item { ToggleRow("Reduced motion", "Respect accessibility preferences", settings.reducedMotion) { value -> viewModel.update { it.copy(reducedMotion = value) } } }
            item { ToggleRow("Photosensitivity-safe", "Limit flashes and fast shakes", settings.photosensitivitySafe) { value -> viewModel.update { it.copy(photosensitivitySafe = value) } } }
            item {
                Text("Quality", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AnimationQuality.entries.toList()) { quality ->
                        FilterChip(
                            selected = settings.quality == quality,
                            onClick = { viewModel.update { it.copy(quality = quality) } },
                            label = { Text(quality.name.lowercase().replaceFirstChar { c -> c.uppercase() }) },
                        )
                    }
                }
            }
            item {
                Text("Motion intensity", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(MotionIntensity.entries.toList()) { intensity ->
                        FilterChip(
                            selected = settings.intensity == intensity,
                            onClick = { viewModel.update { it.copy(intensity = intensity) } },
                            label = { Text(intensity.name.lowercase().replaceFirstChar { c -> c.uppercase() }) },
                        )
                    }
                }
            }
            item {
                Text("Voice provider", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TtsProviderKind.entries.toList()) { provider ->
                        FilterChip(
                            selected = settings.ttsProvider == provider,
                            onClick = { viewModel.update { it.copy(ttsProvider = provider) } },
                            label = { Text(provider.name.lowercase().replace('_', ' ')) },
                        )
                    }
                }
            }
            item {
                Text("Narration speed: %.2fx".format(settings.speed), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = settings.speed,
                    onValueChange = { value -> viewModel.update { it.copy(speed = value) } },
                    valueRange = 0.5f..2f,
                )
            }
            item {
                Text(
                    "Animated chapters are transformative, personal-use renderings. They never upload your pages, and export stays local.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
