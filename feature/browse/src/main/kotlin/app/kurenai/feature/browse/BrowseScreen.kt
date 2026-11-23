package app.kurenai.feature.browse

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kurenai.core.designsystem.components.EmptyState
import app.kurenai.core.designsystem.components.LoadingState
import app.kurenai.core.designsystem.components.MangaGridCard
import app.kurenai.core.designsystem.components.SourceBadge
import app.kurenai.core.designsystem.theme.LocalKurenaiColors
import app.kurenai.core.model.ContentKind
import app.kurenai.core.model.Manga
import app.kurenai.core.model.MangaStatus
import app.kurenai.core.model.SortOrder
import app.kurenai.core.model.SourceHealth
import app.kurenai.core.model.SourceInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onOpenManga: (String) -> Unit,
    onOpenSourceHealth: () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sourcePicker by remember { mutableStateOf(false) }
    var filtersOpen by remember { mutableStateOf(false) }
    var sourceFilter by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Discover", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (state.searchAllSources) "Searching every enabled source" else "Curated sources, one quiet place",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOpenSourceHealth) { Icon(Icons.Rounded.TravelExplore, "Source health") }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = { sourcePicker = true },
                label = {
                    Text(
                        state.selectedSource?.name ?: "Choose source",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingIcon = { Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = state.searchAllSources,
                onClick = { viewModel.setSearchAllSources(!state.searchAllSources) },
                label = { Text("All sources") },
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { filtersOpen = true }) { Icon(Icons.Rounded.FilterList, "Filters") }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search titles, authors, genres…") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                IconButton(onClick = { viewModel.search() }) { Icon(Icons.Rounded.Search, "Search") }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(SortOrder.entries.toList()) { sort ->
                FilterChip(
                    selected = state.filters.sort == sort,
                    onClick = { viewModel.setSort(sort) },
                    label = { Text(sort.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        if (state.searchAllSources && state.sourceChunks.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.sourceChunks, key = { it.sourceId }) { chunk ->
                    AssistChip(
                        onClick = { viewModel.selectSource(chunk.sourceId) },
                        label = { Text("${chunk.sourceName} · ${chunk.results.size}") },
                    )
                }
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                state.loading && state.results.isEmpty() -> LoadingState()
                state.results.isEmpty() -> EmptyState(
                    title = "Nothing here yet",
                    message = "Press search, or try a different source. Kurenai only shows sources you have enabled.",
                    icon = Icons.Rounded.Search,
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(132.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.results, key = { it.id }) { manga ->
                        Column {
                            MangaGridCard(manga, { viewModel.openManga(manga, onOpenManga) })
                            state.sources.firstOrNull { it.id == manga.sourceId }?.let { source ->
                                Text(
                                    source.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    if (!state.searchAllSources) {
                        item {
                            TextButton(
                                onClick = { viewModel.loadMore() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (state.loadingMore) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Load more")
                            }
                        }
                    }
                }
            }
            state.error?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                )
            }
        }
    }

    if (sourcePicker) {
        ModalBottomSheet(
            onDismissRequest = { sourcePicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text("Sources", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${state.sources.size} built-in sources · tap to switch",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = sourceFilter,
                    onValueChange = { sourceFilter = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    placeholder = { Text("Filter by name or language") },
                    singleLine = true,
                )
                val filtered = state.sources.filter {
                    sourceFilter.isBlank() || it.name.contains(sourceFilter, true) ||
                        it.language.contains(sourceFilter, true)
                }
                LazyColumn(Modifier.fillMaxWidth().height(440.dp)) {
                    items(filtered, key = { it.id }) { source ->
                        SourceRow(
                            source = source,
                            selected = source.id == state.selectedSourceId,
                            onClick = {
                                viewModel.selectSource(source.id)
                                sourcePicker = false
                            },
                        )
                    }
                }
            }
        }
    }

    if (filtersOpen) {
        ModalBottomSheet(
            onDismissRequest = { filtersOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Filters", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Genres", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.options.tags.take(60), key = { it.key }) { tag ->
                        FilterChip(
                            selected = tag.title in state.filters.tags,
                            onClick = { viewModel.toggleTag(tag.title) },
                            label = { Text(tag.title) },
                        )
                    }
                }
                Text("Status", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(MangaStatus.ONGOING, MangaStatus.FINISHED, MangaStatus.PAUSED).forEach { status ->
                        FilterChip(
                            selected = status in state.filters.statuses,
                            onClick = { viewModel.toggleStatus(status) },
                            label = { Text(status.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                Text("Type", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(ContentKind.MANGA, ContentKind.MANHWA, ContentKind.MANHUA, ContentKind.WEBTOON, ContentKind.COMIC, ContentKind.LIGHT_NOVEL)) { kind ->
                        FilterChip(
                            selected = kind in state.filters.kinds,
                            onClick = { viewModel.toggleKind(kind) },
                            label = { Text(kind.label) },
                        )
                    }
                }
                TextButton(
                    onClick = {
                        filtersOpen = false
                        viewModel.search()
                    },
                    modifier = Modifier.align(Alignment.End).padding(top = 16.dp),
                ) { Text("Apply filters") }
            }
        }
    }
}

@Composable
private fun SourceRow(source: SourceInfo, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(source.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "${source.languageLabel} · ${source.kind.label} · ${source.license.badge}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val dot = when (source.health) {
            SourceHealth.HEALTHY -> LocalKurenaiColors.current.success
            SourceHealth.DEGRADED -> LocalKurenaiColors.current.warning
            SourceHealth.BROKEN, SourceHealth.DISABLED -> MaterialTheme.colorScheme.error
            SourceHealth.UNKNOWN -> MaterialTheme.colorScheme.outline
        }
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(10.dp))
        TextButton(onClick = onClick) { Text(if (selected) "Selected" else "Use") }
    }
}
