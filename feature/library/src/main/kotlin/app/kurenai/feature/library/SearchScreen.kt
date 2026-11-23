package app.kurenai.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kurenai.core.designsystem.components.EmptyState
import app.kurenai.core.designsystem.components.MangaGridCard
import app.kurenai.core.model.Manga

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenManga: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("Search everything", style = MaterialTheme.typography.headlineSmall)
                Text("Library FTS4, then optional source search", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("short romances I haven't finished") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                TextButton(onClick = { viewModel.search() }) { Text("Go") }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { viewModel.search() }, label = { Text("Library") })
            AssistChip(onClick = { viewModel.search(includeSources = true) }, label = { Text("Library + sources") })
            if (recent.isNotEmpty()) TextButton(onClick = { viewModel.clearHistory() }) { Text("Clear history") }
        }
        if (searching && results.isNotEmpty()) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            Text(
                "Searching sources as they answer…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
        if (recent.isNotEmpty() && query.isBlank()) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Recent", style = MaterialTheme.typography.titleSmall)
                recent.take(6).forEach { value ->
                    Text(
                        value,
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.useRecent(value) }.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        when {
            searching && results.isEmpty() -> CircularProgressIndicator(Modifier.padding(24.dp).align(Alignment.CenterHorizontally))
            results.isEmpty() -> EmptyState(
                title = "Describe what you want",
                message = "Ask in plain language. Kurenai matches titles, tags, authors and reading state locally.",
                icon = Icons.Rounded.Search,
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(132.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(results, key = { it.id }) { manga ->
                    MangaGridCard(manga, { onOpenManga(manga.id) })
                }
            }
        }
    }
}
