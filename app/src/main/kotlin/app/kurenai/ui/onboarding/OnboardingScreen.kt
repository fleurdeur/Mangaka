package app.kurenai.ui.onboarding

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kurenai.core.model.Palette
import app.kurenai.core.model.ThemeMode
import app.kurenai.ui.AppShellViewModel

@Composable
fun OnboardingScreen(
    settingsViewModel: AppShellViewModel,
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
    var palette by remember { mutableStateOf(Palette.KURENAI) }
    var dynamic by remember { mutableStateOf(true) }
    var amoled by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            LinearProgressIndicator(progress = { (state.step + 1) / 4f }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)))
            Spacer(Modifier.height(20.dp))
            when (state.step) {
                0 -> WelcomeStep(state.accepted) { viewModel.setAccepted(it) }
                1 -> SourceStep(state, viewModel)
                2 -> ThemeStep(themeMode, palette, dynamic, amoled, { themeMode = it }, { palette = it }, { dynamic = it }, { amoled = it })
                else -> FinishStep(state, viewModel, themeMode, palette, dynamic, amoled, settingsViewModel, onFinished)
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (state.step > 0) OutlinedButton(onClick = viewModel::back) { Text("Back") } else Spacer(Modifier.width(1.dp))
                if (state.step < 3) {
                    Button(onClick = viewModel::next, enabled = state.step != 0 || state.accepted) { Text("Continue") }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(accepted: Boolean, onAccepted: (Boolean) -> Unit) {
    Column {
        Icon(Icons.Rounded.AutoStories, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(56.dp))
        Spacer(Modifier.height(12.dp))
        Text("Welcome to Kurenai", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text(
            "A cozy, cinematic reader for manga, manhwa, manhua, webtoons, comics and light novels. Fast, private and built to disappear while you read.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)).padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Rounded.CloudOff, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Content comes from third-party websites", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Kurenai hosts and distributes no manga. Every page is fetched directly from a source you choose. Support official releases where available. Adult sources remain locked until you unlock them with a PIN.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = accepted, onCheckedChange = onAccepted)
            Text("I understand and will use Kurenai responsibly.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SourceStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    Column {
        Text("Pick your sources", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "All ${state.sources.size} built-in parsers ship with Kurenai. We enabled sources for your system language (${state.systemLanguage.uppercase()}); you can change this any time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = viewModel::selectAllLanguages) { Text("Enable every language") }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
            val groups = state.sources.groupBy { it.language.ifBlank { "multi" } }
            items(groups.entries.sortedByDescending { it.value.size }, key = { it.key }) { (language, sources) ->
                val selected = language in state.selectedLanguages || language == "multi"
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface)
                        .clickable { if (language != "multi") viewModel.toggleLanguage(language) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Language, null, Modifier.height(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            java.util.Locale.forLanguageTag(language).displayLanguage.ifBlank { if (language == "multi") "Multi-language" else language },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text("${sources.size} sources", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Text("${state.enabledCount} sources enabled", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ThemeStep(
    mode: ThemeMode,
    palette: Palette,
    dynamic: Boolean,
    amoled: Boolean,
    onMode: (ThemeMode) -> Unit,
    onPalette: (Palette) -> Unit,
    onDynamic: (Boolean) -> Unit,
    onAmoled: (Boolean) -> Unit,
) {
    Column {
        Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.primary)
        Text("Make it yours", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Kurenai uses Material 3 Expressive with a warm coffee base and a crimson accent. Monet colors are on by default.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { value ->
                FilterChip(selected = mode == value, onClick = { onMode(value) }, label = { Text(value.name.lowercase()) })
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Palette.entries.forEach { value ->
                FilterChip(selected = palette == value, onClick = { onPalette(value) }, label = { Text(value.name.lowercase()) })
            }
        }
        Spacer(Modifier.height(12.dp))
        ToggleLine("Dynamic Monet colors", dynamic, onDynamic)
        ToggleLine("AMOLED black", amoled, onAmoled)
    }
}

@Composable
private fun FinishStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
    themeMode: ThemeMode,
    palette: Palette,
    dynamic: Boolean,
    amoled: Boolean,
    settingsViewModel: AppShellViewModel,
    onFinished: () -> Unit,
) {
    Column {
        Icon(Icons.Rounded.Security, null, tint = MaterialTheme.colorScheme.primary)
        Text("Ready when you are", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "${state.enabledCount} sources are enabled. Adult content is locked. No ads, no analytics, no account. You can fine-tune everything in Settings.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                viewModel.finish(themeMode, palette, dynamic, amoled) {
                    settingsViewModel.completeOnboarding()
                    onFinished()
                }
            },
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.saving) "Preparing your shelf…" else "Enter Kurenai") }
    }
}

@Composable
private fun ToggleLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
