package app.kurenai.feature.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GTranslate
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kurenai.core.designsystem.components.EmptyState
import app.kurenai.core.designsystem.components.LoadingState
import app.kurenai.core.model.ColorFilterMode
import app.kurenai.core.model.ReaderAction
import app.kurenai.core.model.ReaderBackground
import app.kurenai.core.model.ReadingMode
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    onOpenAnimated: (String, String) -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var settingsOpen by remember { mutableStateOf(false) }
    var sleepMinutes by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    val settings = state.settings
    val isWebtoon = settings.mode == ReadingMode.WEBTOON || settings.mode == ReadingMode.CONTINUOUS_VERTICAL
    val pagerState = rememberPagerState(initialPage = state.initialPage, pageCount = { state.pages.size })
    val listState = rememberLazyListState()

    BackHandler {
        viewModel.finishSession()
        onBack()
    }

    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            view.keepScreenOn = false
            viewModel.finishSession()
        }
    }

    LaunchedEffect(settings.keepScreenOn) { view.keepScreenOn = settings.keepScreenOn }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(state.initialPage, state.pages.size) {
        if (state.pages.isNotEmpty() && pagerState.currentPage != state.initialPage) {
            pagerState.scrollToPage(state.initialPage.coerceIn(0, state.pages.lastIndex))
        }
    }
    LaunchedEffect(pagerState, state.pages.size, isWebtoon) {
        if (!isWebtoon && state.pages.isNotEmpty()) {
            snapshotFlow { pagerState.currentPage }.collect { page -> viewModel.onPageChanged(page) }
        }
    }
    LaunchedEffect(listState, state.pages.size, isWebtoon) {
        if (isWebtoon && state.pages.isNotEmpty()) {
            snapshotFlow { listState.firstVisibleItemIndex }.collect { page -> viewModel.onPageChanged(page) }
        }
    }
    LaunchedEffect(state.autoScrolling, isWebtoon) {
        if (!state.autoScrolling || state.pages.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(if (isWebtoon) 42L else 4_500L)
            if (isWebtoon) {
                if (listState.layoutInfo.visibleItemsInfo.isEmpty()) continue
                val amount = (14f * settings.autoScrollSpeed).toInt().coerceAtLeast(2)
                listState.scrollBy(amount.toFloat())
            } else if (pagerState.currentPage < state.pages.lastIndex) {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        }
    }
    LaunchedEffect(sleepMinutes) {
        if (sleepMinutes > 0) {
            delay(sleepMinutes * 60_000L)
            viewModel.finishSession()
            onBack()
        }
    }

    val background = when (settings.background) {
        ReaderBackground.BLACK -> Color.Black
        ReaderBackground.WHITE -> Color.White
        ReaderBackground.GRAY -> Color(0xFF2A2A2A)
        ReaderBackground.AUTOMATIC -> MaterialTheme.colorScheme.background
    }

    Box(
        Modifier.fillMaxSize()
            .background(background)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.VolumeDown -> {
                        if (settings.volumeKeysEnabled) {
                            if (settings.volumeKeysInvert) viewModel.nextChapter() else viewModel.previousChapter()
                        }
                        true
                    }
                    Key.VolumeUp -> {
                        if (settings.volumeKeysEnabled) {
                            if (settings.volumeKeysInvert) viewModel.previousChapter() else viewModel.nextChapter()
                        }
                        true
                    }
                    Key.DirectionRight, Key.Spacebar -> {
                        if (!isWebtoon) scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(state.pages.lastIndex)) }
                        true
                    }
                    Key.DirectionLeft -> {
                        if (!isWebtoon) scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) }
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(settings.tapZones, state.pages.size, isWebtoon) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()
                    val zones = settings.tapZones
                    when {
                        offset.x < width * zones.leftWidthFraction -> {
                            scope.launch {
                                if (isWebtoon) listState.scrollBy(-height * 0.8f) else if (pagerState.currentPage > 0) {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }
                        }
                        offset.x > width * (1f - zones.rightWidthFraction) -> {
                            scope.launch {
                                if (isWebtoon) listState.scrollBy(height * 0.8f) else if (pagerState.currentPage < state.pages.lastIndex) {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        }
                        offset.y < height * 0.2f -> viewModel.setUiVisible(true)
                        else -> viewModel.toggleUi()
                    }
                }
            },
    ) {
        when {
            state.loading -> LoadingState()
            state.pages.isEmpty() -> EmptyState(
                title = "This chapter is empty",
                message = state.error ?: "Try another source or refresh the series.",
            )
            isWebtoon -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(state.pages, key = { index, page -> "${page.url}-$index" }) { index, page ->
                    ReaderPage(page.url, index, settings, Modifier.fillMaxWidth())
                }
            }
            else -> {
                val doublePage = settings.doublePage && LocalConfiguration.current.screenWidthDp > 600
                if (doublePage) {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = state.prefetchPages,
                        modifier = Modifier.fillMaxSize(),
                    ) { pageIndex ->
                        Row(Modifier.fillMaxSize()) {
                            val first = state.pages.getOrNull(pageIndex * 2)
                            val second = state.pages.getOrNull(pageIndex * 2 + 1)
                            if (first != null) ReaderPage(first.url, pageIndex * 2, settings, Modifier.weight(1f).fillMaxSize())
                            if (second != null) ReaderPage(second.url, pageIndex * 2 + 1, settings, Modifier.weight(1f).fillMaxSize())
                        }
                    }
                } else {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = state.prefetchPages,
                        modifier = Modifier.fillMaxSize(),
                    ) { pageIndex ->
                        ReaderPage(state.pages[pageIndex].url, pageIndex, settings, Modifier.fillMaxSize())
                    }
                }
            }
        }

        ReaderLightOverlay(settings)
        if (settings.brightness > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = settings.brightness.coerceIn(0f, 0.8f))))
        }

        AnimatedVisibility(visible = state.uiVisible, modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.finishSession(); onBack() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        state.chapter?.displayTitle ?: "Chapter",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        state.manga?.title.orEmpty(),
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                IconButton(onClick = { viewModel.setTranslationVisible(!state.translationVisible) }) {
                    Icon(Icons.Rounded.GTranslate, "Translate", tint = if (state.translationVisible) MaterialTheme.colorScheme.primary else Color.White)
                }
                IconButton(onClick = { state.manga?.let { state.chapter?.let { c -> onOpenAnimated(it.id, c.id) } } }) {
                    Icon(Icons.Rounded.AutoAwesome, "Animated chapter", tint = Color.White)
                }
                IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Rounded.Settings, "Reader settings", tint = Color.White) }
            }
        }

        AnimatedVisibility(visible = state.uiVisible, modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
                    .padding(horizontal = 16.dp, vertical = 18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${(state.currentPage + 1).coerceAtMost(state.pages.size)} / ${state.pages.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.width(12.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.weight(1f).height(3.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.previousChapter() }) { Icon(Icons.AutoMirrored.Rounded.MenuBook, "Previous chapter", tint = Color.White) }
                    IconButton(onClick = { viewModel.setAutoScrolling(!state.autoScrolling) }) {
                        Icon(if (state.autoScrolling) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Auto scroll", tint = Color.White)
                    }
                    IconButton(onClick = { viewModel.nextChapter() }) { Icon(Icons.AutoMirrored.Rounded.MenuBook, "Next chapter", tint = Color.White) }
                    Spacer(Modifier.weight(1f))
                    Text(
                        state.settings.mode.name.lowercase().replace('_', ' '),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    IconButton(onClick = { sleepMinutes = if (sleepMinutes == 0) 20 else 0 }) {
                        Icon(Icons.Rounded.Timer, "Sleep timer", tint = if (sleepMinutes > 0) MaterialTheme.colorScheme.primary else Color.White)
                    }
                }
            }
        }

        if (state.translationVisible) {
            TranslationOverlay(state, onClose = { viewModel.setTranslationVisible(false) })
        }
    }

    if (settingsOpen) {
        ReaderSettingsSheet(
            state = state,
            onDismiss = { settingsOpen = false },
            onUpdate = viewModel::updateSettings,
        )
    }
}

@Composable
private fun ReaderPage(url: String, index: Int, settings: app.kurenai.core.model.ReaderSettings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colorFilter = when {
        settings.einkMode || settings.colorFilter == ColorFilterMode.GRAYSCALE ->
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        settings.colorFilter == ColorFilterMode.SEPIA ->
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.35f) })
        settings.colorFilter == ColorFilterMode.INVERTED ->
            ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)))
        else -> null
    }
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(120)
            .build(),
        contentDescription = "Page ${index + 1}",
        modifier = modifier,
        contentScale = if (settings.mode == ReadingMode.WEBTOON || settings.mode == ReadingMode.CONTINUOUS_VERTICAL) ContentScale.FillWidth else ContentScale.Fit,
        colorFilter = colorFilter,
    )
}

@Composable
private fun ReaderLightOverlay(settings: app.kurenai.core.model.ReaderSettings) {
    val schedule = settings.readingLightSchedule ?: return
    if (!schedule.enabled) return
    val hour = LocalTime.now().hour
    val active = if (schedule.startHour <= schedule.endHour) {
        hour in schedule.startHour until schedule.endHour
    } else {
        hour >= schedule.startHour || hour < schedule.endHour
    }
    if (!active) return
    Box(
        Modifier.fillMaxSize().background(
            Color(0xFFFF9A3C).copy(alpha = (schedule.warmth * 0.28f).coerceIn(0f, 0.5f)),
        ),
    )
    if (schedule.dim > 0f) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = schedule.dim.coerceIn(0f, 0.6f))))
    }
}

@Composable
private fun TranslationOverlay(state: ReaderUiState, onClose: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .padding(16.dp)
            .background(Color(0xF21B1214), shape = MaterialTheme.shapes.large)
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.GTranslate, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("In-place translation", color = Color.White, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Close") }
            }
            Text(
                "OCR and translation run on-device by default. Enable a local or cloud provider in Settings → Translation & learning to see bubble-aligned overlays here.",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    state: ReaderUiState,
    onDismiss: () -> Unit,
    onUpdate: ((app.kurenai.core.model.ReaderSettings) -> app.kurenai.core.model.ReaderSettings) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
            Text("Reader", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Per-series settings are remembered for this title.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingMode.entries.forEach { mode ->
                    androidx.compose.material3.FilterChip(
                        selected = state.settings.mode == mode,
                        onClick = { onUpdate { it.copy(mode = mode) } },
                        label = { Text(mode.name.lowercase().replace('_', ' ').take(12)) },
                    )
                }
            }
            SettingSwitch("Double-page spreads", state.settings.doublePage) { value -> onUpdate { it.copy(doublePage = value) } }
            SettingSwitch("Auto-crop margins", state.settings.autoCrop) { value -> onUpdate { it.copy(autoCrop = value) } }
            SettingSwitch("Volume keys turn pages", state.settings.volumeKeysEnabled) { value -> onUpdate { it.copy(volumeKeysEnabled = value) } }
            SettingSwitch("Keep screen on", state.settings.keepScreenOn) { value -> onUpdate { it.copy(keepScreenOn = value) } }
            SettingSwitch("E-ink friendly", state.settings.einkMode) { value -> onUpdate { it.copy(einkMode = value) } }
            Text("Brightness", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Slider(value = state.settings.brightness, onValueChange = { value -> onUpdate { it.copy(brightness = value) } }, valueRange = 0f..0.8f)
            Text("Auto-scroll speed", style = MaterialTheme.typography.titleSmall)
            Slider(value = state.settings.autoScrollSpeed, onValueChange = { value -> onUpdate { it.copy(autoScrollSpeed = value) } }, valueRange = 0.4f..3f)
            Text("Color filter", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ColorFilterMode.entries.forEach { mode ->
                    androidx.compose.material3.FilterChip(
                        selected = state.settings.colorFilter == mode,
                        onClick = { onUpdate { it.copy(colorFilter = mode) } },
                        label = { Text(mode.name.lowercase()) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
