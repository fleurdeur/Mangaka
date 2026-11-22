package app.kurenai.feature.translation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import app.kurenai.core.model.TranslationProviderKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationScreen(
    onBack: () -> Unit,
    viewModel: TranslationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::scan)
    }
    var cloudOpen by remember { mutableStateOf(false) }
    var glossaryOpen by remember { mutableStateOf(false) }
    var sourceMenu by remember { mutableStateOf(false) }
    var targetMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Translate", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { cloudOpen = true }) { Icon(Icons.Rounded.Cloud, "Cloud settings") }
                    IconButton(onClick = { glossaryOpen = true }) { Icon(Icons.Rounded.Language, "Glossary") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "On-device OCR and translation. Cloud providers run only when you press Translate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TranslationProviderKind.entries.toList()) { provider ->
                        FilterChip(
                            selected = state.settings.provider == provider,
                            onClick = { viewModel.setProvider(provider) },
                            label = { Text(providerLabel(provider), style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { sourceMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("From: ${TranslationViewModel.languageName(state.settings.sourceLanguage)}")
                        }
                        DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                            TranslationViewModel.languages.forEach { language ->
                                DropdownMenuItem(
                                    text = { Text(TranslationViewModel.languageName(language)) },
                                    onClick = { viewModel.setSource(language); sourceMenu = false },
                                )
                            }
                        }
                    }
                    Icon(Icons.Rounded.Translate, null, modifier = Modifier.size(18.dp))
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { targetMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("To: ${TranslationViewModel.languageName(state.settings.targetLanguage)}")
                        }
                        DropdownMenu(expanded = targetMenu, onDismissRequest = { targetMenu = false }) {
                            TranslationViewModel.languages.filter { it != "auto" }.forEach { language ->
                                DropdownMenuItem(
                                    text = { Text(TranslationViewModel.languageName(language)) },
                                    onClick = { viewModel.setTarget(language); targetMenu = false },
                                )
                            }
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(12.dp)) {
                        OutlinedTextField(
                            value = state.input,
                            onValueChange = viewModel::setInput,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            label = { Text("Original text") },
                            placeholder = { Text("Paste dialogue, or scan a page.") },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            Button(
                                onClick = viewModel::translate,
                                enabled = !state.busy && state.input.isNotBlank(),
                                modifier = Modifier.weight(1f),
                            ) {
                                if (state.busy) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Rounded.Translate, null, Modifier.size(18.dp))
                                }
                                Spacer(Modifier.size(6.dp))
                                Text("Translate")
                            }
                            OutlinedButton(onClick = { imagePicker.launch("image/*") }, enabled = !state.busy) {
                                Icon(Icons.Rounded.DocumentScanner, null, Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Scan")
                            }
                        }
                        state.status?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                        }
                        state.error?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
            if (state.output.isNotBlank()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                state.engine,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            )
                            Text(state.output, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = viewModel::clearOutput) { Text("Clear") }
                                TextButton(onClick = viewModel::translate) { Text("Retry") }
                            }
                        }
                    }
                }
            }
            if (state.ocrLines.isNotEmpty()) {
                item {
                    Text("OCR detail", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${state.ocrLines.size} text regions detected on-device. Bubble boxes are ready for the overlay editor.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Card {
                    Column(Modifier.padding(14.dp)) {
                        Text("Language learning", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = state.settings.languageLearning,
                                onClick = { viewModel.setLearning(!state.settings.languageLearning) },
                                label = { Text("Show original + translation") },
                            )
                        }
                        FilterChip(
                            selected = state.settings.showFurigana,
                            onClick = { viewModel.setFurigana(!state.settings.showFurigana) },
                            label = { Text("Furigana / readings when available") },
                        )
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp)) {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "  OCR and ML Kit translation run offline. Cloud keys are stored in encrypted preferences and are never logged.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (cloudOpen) {
        CloudSettingsDialog(
            initialEndpoint = state.settings.cloudEndpoint.orEmpty(),
            initialModel = state.settings.cloudModel.orEmpty(),
            cloudKeyPresent = state.cloudKeyPresent,
            onDismiss = { cloudOpen = false },
            onSave = { endpoint, model, key ->
                viewModel.setCloudConfig(endpoint, model, key)
                cloudOpen = false
            },
        )
    }
    if (glossaryOpen) {
        GlossaryDialog(
            initial = state.settings.glossary.entries.joinToString("\n") { "${it.key} = ${it.value}" },
            onDismiss = { glossaryOpen = false },
            onSave = { raw -> viewModel.saveGlossary(raw); glossaryOpen = false },
        )
    }
}

@Composable
private fun CloudSettingsDialog(
    initialEndpoint: String,
    initialModel: String,
    cloudKeyPresent: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var endpoint by remember { mutableStateOf(initialEndpoint) }
    var model by remember { mutableStateOf(initialModel) }
    var key by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cloud translation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("OpenAI-compatible endpoint. Works with local LLM servers too.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(endpoint, { endpoint = it }, label = { Text("Endpoint") }, placeholder = { Text("https://api.example.com/v1") }, singleLine = true)
                OutlinedTextField(model, { model = it }, label = { Text("Model") }, placeholder = { Text("gpt-4o-mini") }, singleLine = true)
                OutlinedTextField(key, { key = it }, label = { Text("API key") }, placeholder = { Text(if (cloudKeyPresent) "saved" else "sk-...") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(endpoint, model, key) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GlossaryDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var raw by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Glossary") },
        text = {
            Column {
                Text("One term per line: original = translation. Applied after translation.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    raw,
                    { raw = it },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("senpai = upperclassman\nnakama = crew") },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(raw) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun providerLabel(provider: TranslationProviderKind): String = when (provider) {
    TranslationProviderKind.ON_DEVICE_ML_KIT -> "On-device"
    TranslationProviderKind.LOCAL_LLM -> "Local LLM"
    TranslationProviderKind.CLOUD_OPENAI_COMPATIBLE -> "Cloud"
    TranslationProviderKind.NONE -> "Off"
}
