package app.kurenai.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kurenai.core.designsystem.components.CoverImage
import app.kurenai.core.designsystem.components.LoadingState
import app.kurenai.core.designsystem.components.MangaGridCard
import app.kurenai.core.designsystem.components.SectionHeader
import app.kurenai.core.designsystem.components.SourceBadge
import app.kurenai.core.model.MangaChapter
import app.kurenai.core.model.SourceHealth
import app.kurenai.core.model.SourceInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    onBack: () -> Unit,
    onRead: (String, String) -> Unit,
    onOpenManga: (String) -> Unit,
    viewModel: DetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var categoriesOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(state.manga?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Rounded.Refresh, "Refresh") }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Manage shelves") }, onClick = { menuOpen = false; categoriesOpen = true })
                        DropdownMenuItem(text = { Text("Sync trackers") }, onClick = { menuOpen = false; viewModel.syncTrackers() })
                        DropdownMenuItem(text = { Text("Download next 5") }, onClick = { menuOpen = false; viewModel.downloadNext() })
                        DropdownMenuItem(text = { Text("Mark all read") }, onClick = {
                            menuOpen = false
                            state.chapters.forEach { viewModel.markRead(it) }
                        })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        val manga = state.manga
        if (state.loading && manga == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        if (manga == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(state.error ?: "Series unavailable")
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(bottom = 40.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item {
                Box {
                    CoverImage(
                        manga.largeCoverUrl ?: manga.coverUrl,
                        manga.title,
                        Modifier.fillMaxWidth().height(320.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                    Box(
                        Modifier.fillMaxWidth().height(320.dp).background(
                            Brush.verticalGradient(
                                0.15f to Color.Transparent,
                                1f to MaterialTheme.colorScheme.background,
                            ),
                        ),
                    )
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(manga.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    if (manga.altTitles.isNotEmpty()) {
                        Text(
                            manga.altTitles.joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = {}, label = { Text(manga.kind.label) })
                        AssistChip(onClick = {}, label = { Text(manga.status.name.lowercase().replaceFirstChar { it.uppercase() }) })
                        if (manga.hasRating) AssistChip(onClick = {}, label = { Text("%.1f".format(manga.rating * 10)) })
                    }
                    Spacer(Modifier.height(10.dp))
                    state.source?.let { SourceBadge(it) }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { viewModel.startReading(onRead) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.readCount == 0) "Start reading" else "Continue")
                        }
                        FilledTonalButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                if (manga.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                "Favorite",
                            )
                        }
                        FilledTonalButton(onClick = { viewModel.downloadNext() }) {
                            Icon(Icons.Rounded.CloudDownload, "Download next")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { if (state.chapters.isEmpty()) 0f else state.readCount.toFloat() / state.chapters.size },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    )
                    Text(
                        "${state.readCount} of ${state.chapters.size} chapters read · ${state.unreadCount} unread",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    if (!manga.description.isNullOrBlank()) {
                        Text(
                            manga.description.orEmpty().replace(Regex("<[^>]+>"), ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(manga.tags.toList()) { tag -> AssistChip(onClick = {}, label = { Text(tag) }) }
                    }
                    if (state.trackers.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Trackers", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.width(8.dp))
                            state.trackers.forEach { tracker ->
                                AssistChip(onClick = { viewModel.syncTrackers() }, label = { Text("${tracker.tracker.title} ${tracker.progress}") })
                            }
                            IconButton(onClick = { viewModel.syncTrackers() }) { Icon(Icons.Rounded.Sync, "Sync") }
                        }
                    }
                }
            }
            item { SectionHeader("Chapters", action = "Download next", onAction = { viewModel.downloadNext() }) }
            items(state.chapters, key = { it.id }) { chapter ->
                ChapterRow(
                    chapter = chapter,
                    onRead = { viewModel.read(chapter, onRead) },
                    onDownload = { viewModel.download(chapter) },
                    onToggleRead = { viewModel.markRead(chapter, !chapter.isRead) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            }
            if (state.recommendations.isNotEmpty()) {
                item { SectionHeader("More like this") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.recommendations, key = { it.manga.id }) { rec ->
                            Column(Modifier.width(132.dp)) {
                                MangaGridCard(rec.manga, { onOpenManga(rec.manga.id) }, showSource = false)
                                Text(rec.reasons.firstOrNull().orEmpty(), style = MaterialTheme.typography.labelSmall, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }

    if (categoriesOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { categoriesOpen = false },
            title = { Text("Shelves") },
            text = {
                Column {
                    state.categories.forEach { category ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                state.manga?.let { viewModel.addToCategory(it.id, category.id) }
                                categoriesOpen = false
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(category.name)
                        }
                    }
                    TextButton(onClick = { state.manga?.let { viewModel.createCategory("New shelf") } }) {
                        Text("Create new shelf")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { categoriesOpen = false }) { Text("Done") } },
        )
    }
}

@Composable
private fun ChapterRow(
    chapter: MangaChapter,
    onRead: () -> Unit,
    onDownload: () -> Unit,
    onToggleRead: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onRead).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(10.dp).clip(CircleShape).background(
                if (chapter.isRead) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
            ),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(chapter.displayTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    append(chapter.branchLabel)
                    if (chapter.pageCount > 0) append(" · ${chapter.pageCount} pages")
                    if (chapter.isDownloaded) append(" · offline")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (chapter.isDownloaded) {
            Icon(Icons.Rounded.Download, "Downloaded", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        } else {
            IconButton(onClick = onDownload) { Icon(Icons.Rounded.CloudDownload, "Download") }
        }
        IconButton(onClick = onToggleRead) {
            Icon(Icons.Rounded.Check, if (chapter.isRead) "Mark unread" else "Mark read", tint = if (chapter.isRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        }
    }
}
