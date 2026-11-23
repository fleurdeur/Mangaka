package app.kurenai.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import app.kurenai.core.data.ContinueReadingItem
import app.kurenai.core.designsystem.components.CoverImage
import app.kurenai.core.designsystem.components.EmptyState
import app.kurenai.core.designsystem.components.LoadingState
import app.kurenai.core.designsystem.components.MangaGridCard
import app.kurenai.core.designsystem.components.SectionHeader
import app.kurenai.core.model.Manga

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onMangaClick: (Manga) -> Unit,
    onContinueReading: (ContinueReadingItem) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTaste: () -> Unit,
    onOpenStats: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Your library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${state.favorites.size} series · ${state.stats.streakDays} day streak",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOpenTaste) { Icon(Icons.Rounded.AutoAwesome, "Taste profile") }
            IconButton(onClick = onOpenStats) { Icon(Icons.Rounded.History, "Reading stats") }
            IconButton(onClick = onOpenSearch) { Icon(Icons.Rounded.Search, "Search library") }
        }

        if (state.continueReading.isNotEmpty()) {
            FeaturedContinueCard(
                item = state.continueReading.first(),
                onClick = { onContinueReading(state.continueReading.first()) },
            )
            if (state.continueReading.size > 1) {
                SectionHeader("Also in progress")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.continueReading.drop(1), key = { it.manga.id }) { item ->
                        ContinueCard(item) { onContinueReading(item) }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = state.selectedCategoryId == null,
                onClick = { viewModel.selectCategory(null) },
                label = { Text("All") },
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.categories, key = { it.id }) { category ->
                    FilterChip(
                        selected = state.selectedCategoryId == category.id,
                        onClick = { viewModel.selectCategory(category.id) },
                        label = { Text(category.name) },
                    )
                }
            }
        }

        if (state.searchQuery.isNotBlank()) {
            SectionHeader("Library matches")
            LazyVerticalGrid(
                columns = GridCells.Adaptive(132.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(state.searchResults, key = { it.id }) { manga ->
                    MangaGridCard(manga, { onMangaClick(manga) })
                }
            }
        } else if (state.loading) {
            LoadingState()
        } else if (state.favorites.isEmpty()) {
            EmptyState(
                title = "A quiet shelf, waiting",
                message = "Browse the catalogue and favorite a few series. Kurenai will keep your place, downloads and reading streaks tidy.",
                icon = Icons.Rounded.AutoAwesome,
            )
        } else {
            val filtered = if (state.selectedCategoryId == null) {
                state.favorites
            } else {
                state.favorites.filter { state.selectedCategoryId in it.categoryIds }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(132.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(filtered, key = { it.manga.id }) { entry ->
                    MangaGridCard(entry.manga, { onMangaClick(entry.manga) })
                }
                item { AddCategoryCard { viewModel.createCategory("New shelf") } }
            }
        }
    }
}

@Composable
private fun FeaturedContinueCard(item: ContinueReadingItem, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .height(204.dp)
            .clip(RoundedCornerShape(26.dp)),
    ) {
        CoverImage(
            url = item.manga.largeCoverUrl ?: item.manga.coverUrl,
            contentDescription = item.manga.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.08f),
                    0.5f to Color.Black.copy(alpha = 0.42f),
                    1f to Color.Black.copy(alpha = 0.92f),
                ),
            ),
        )
        Column(
            Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                "CONTINUE READING",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFFD9A0),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                item.manga.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.chapter?.displayTitle ?: "Start from the beginning",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { item.fraction },
                    modifier = Modifier.weight(1f).height(5.dp).clip(CircleShape),
                    color = Color(0xFFFFD9A0),
                    trackColor = Color.White.copy(alpha = 0.22f),
                )
                Spacer(Modifier.width(12.dp))
                Button(onClick = onClick) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Resume")
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(item: ContinueReadingItem, onClick: () -> Unit) {
    Column(
        Modifier.width(240.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(item.manga.coverUrl, item.manga.title, Modifier.size(64.dp, 88.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.manga.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(item.chapter?.displayTitle ?: "Start reading", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { item.fraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onClick, modifier = Modifier.align(Alignment.End)) { Text("Resume") }
    }
}

@Composable
private fun AddCategoryCard(onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().height(180.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onClick) { Icon(Icons.Rounded.Add, "Create category") }
        Text("New shelf", style = MaterialTheme.typography.labelMedium)
    }
}
