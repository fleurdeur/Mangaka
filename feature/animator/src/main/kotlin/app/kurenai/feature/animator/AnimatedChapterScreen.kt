package app.kurenai.feature.animator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kurenai.core.model.MotionIntensity
import app.kurenai.core.model.TtsProviderKind
import app.kurenai.feature.animator.render.AnimatedGLSurfaceView
import app.kurenai.feature.animator.render.AnimRenderer

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AnimatedChapterScreen(
    onBack: () -> Unit,
    viewModel: AnimatedChapterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var settingsOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val renderer = remember { AnimRenderer() }
        var glView by remember { mutableStateOf<AnimatedGLSurfaceView?>(null) }
        val playback = state.playback
        LaunchedEffect(playback.playing) {
            renderer.playing = playback.playing
            glView?.setAnimating(playback.playing)
        }
        LaunchedEffect(state.bitmap) {
            renderer.updateBitmap(state.bitmap)
            glView?.requestRender()
        }
        LaunchedEffect(state.plan) {
            renderer.updatePlan(state.plan)
            glView?.requestRender()
        }
        LaunchedEffect(playback.currentPanel) {
            renderer.focusPanel(playback.currentPanel)
            glView?.requestRender()
        }

        AndroidView(
            factory = { context -> AnimatedGLSurfaceView(context, renderer).also { glView = it } },
            modifier = Modifier.fillMaxSize(),
            update = { it.setAnimating(playback.playing) },
            onRelease = { it.onPause() },
        )

        if (playback.processing) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFFE23D4F))
                Text("Detecting panels and building rigs…", color = Color.White, style = MaterialTheme.typography.labelMedium)
                Text("This chapter is processed once and cached.", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
            }
        }
        playback.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center).padding(24.dp))
        }

        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                .statusBarsPadding()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("Animated chapter", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(state.chapter?.displayTitle.orEmpty(), color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { viewModel.toggleCasting() }) { Icon(Icons.Rounded.Cast, "Voice casting", tint = Color.White) }
            IconButton(onClick = { viewModel.toggleEditor() }) { Icon(Icons.Rounded.Edit, "Manual editor", tint = Color.White) }
            IconButton(onClick = { viewModel.exportVideo() }) { Icon(Icons.Rounded.FileDownload, "Export video", tint = Color.White) }
            IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Rounded.Settings, "Settings", tint = Color.White) }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            state.subtitle.takeIf { state.settings.subtitles && it.isNotBlank() }?.let { text ->
                val active = state.activeWord?.word?.takeIf { state.settings.karaoke }
                val annotated = buildAnnotatedString {
                    if (active.isNullOrBlank()) {
                        append(text)
                    } else {
                        val index = text.indexOf(active, ignoreCase = true)
                        if (index < 0) append(text) else {
                            append(text.substring(0, index))
                            withStyle(SpanStyle(color = Color(0xFFFFD27A), fontWeight = FontWeight.Bold)) {
                                append(text.substring(index, index + active.length))
                            }
                            append(text.substring(index + active.length))
                        }
                    }
                }
                Text(annotated, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.play() }) {
                    Icon(if (playback.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play", tint = Color.White)
                }
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { playback.progress },
                    modifier = Modifier.weight(1f).height(3.dp),
                )
                Text(
                    "${playback.currentPanel + 1}/${state.plan?.panelCount ?: 0}",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Rounded.AutoAwesome, "Motion", tint = Color.White) }
            }
            state.plan?.warnings?.firstOrNull()?.let {
                Text(it, color = Color(0xFFFFD27A), style = MaterialTheme.typography.labelSmall)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).padding(top = 64.dp))
    }

    if (settingsOpen) {
        ModalBottomSheet(onDismissRequest = { settingsOpen = false }) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Motion & narration", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Still respects reduced-motion and photosensitivity-safe settings.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Motion intensity", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(MotionIntensity.entries.toList()) { intensity ->
                        FilterChip(
                            selected = state.intensity == intensity,
                            onClick = { viewModel.setIntensity(intensity) },
                            label = { Text(intensity.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                Text("Voice provider", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TtsProviderKind.entries.toList()) { provider ->
                        FilterChip(
                            selected = state.settings.ttsProvider == provider,
                            onClick = { viewModel.setProvider(provider) },
                            label = { Text(provider.name.lowercase().replace('_', ' ')) },
                        )
                    }
                }
                Text("Narration speed", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                Slider(value = state.settings.speed, onValueChange = viewModel::setSpeed, valueRange = 0.5f..2f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { viewModel.setIntensity(MotionIntensity.STILL) }, label = { Text("Reduced motion") })
                    AssistChip(onClick = { viewModel.autoCast() }, label = { Text("Auto-cast voices") })
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (state.castingOpen) {
        ModalBottomSheet(onDismissRequest = { viewModel.toggleCasting() }) {
            LazyColumn(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("Voice casting", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("Assign a distinct voice to each character. Suggestions use detected age, gender and emotion.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    CastRow("Narrator", state.casting.narrator.voiceName, state.voices.take(30)) { voice ->
                        viewModel.assignVoice("narrator", voice)
                    }
                }
                items(state.casting.characters, key = { it.id }) { character ->
                    CastRow(character.name, character.voiceName, state.voices.take(30)) { voice ->
                        viewModel.assignVoice(character.id, voice)
                    }
                }
                item { Button(onClick = { viewModel.autoCast() }, modifier = Modifier.fillMaxWidth()) { Text("Auto-cast suggestions") } }
            }
        }
    }

    if (state.editorOpen) {
        ModalBottomSheet(onDismissRequest = { viewModel.toggleEditor() }) {
            LazyColumn(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("Manual editor", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("Fix panel order, speaker assignments and layer timing. Changes are local to this chapter.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                itemsIndexed(state.plan?.panels.orEmpty()) { index, panel ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Panel ${index + 1}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { viewModel.movePanel(index, index - 1) }) { Icon(Icons.Rounded.ArrowUpward, "Move up") }
                        IconButton(onClick = { viewModel.movePanel(index, index + 1) }) { Icon(Icons.Rounded.ArrowDownward, "Move down") }
                    }
                    Text(
                        "%.0f%% × %.0f%%".format(panel.width * 100, panel.height * 100),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    Text("Panel ${(state.plan?.bubbles?.size ?: 0)} bubbles detected. OCR text can be corrected in the translation editor.", style = MaterialTheme.typography.labelSmall)
                }
                item {
                    Button(onClick = { viewModel.exportVideo() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.FileDownload, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Export personal-use video")
                    }
                }
            }
        }
    }
}

@Composable
private fun CastRow(
    name: String,
    selectedVoice: String?,
    voices: List<app.kurenai.feature.animator.tts.VoiceOption>,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(name, style = MaterialTheme.typography.titleSmall)
        Text(selectedVoice ?: "System default", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            items(voices, key = { it.id }) { voice ->
                AssistChip(
                    onClick = { onSelect(voice.id) },
                    label = { Text(voice.name.take(20), style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}
