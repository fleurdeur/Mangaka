package app.kurenai.core.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/** A tiny in-memory event bus for cross-feature signals such as "library changed". */
class AppEventBus {
    private val _events = MutableStateFlow<AppEvent?>(null)
    val events: Flow<AppEvent?> = _events

    fun emit(event: AppEvent) {
        _events.value = event
    }
}

sealed interface AppEvent {
    data object LibraryChanged : AppEvent
    data object HistoryChanged : AppEvent
    data object DownloadsChanged : AppEvent
    data object SourcesChanged : AppEvent
    data class Message(val text: String) : AppEvent
}

fun <A, B, C> combine3(a: Flow<A>, b: Flow<B>, c: Flow<C>): Flow<Triple<A, B, C>> =
    combine(a, b, c) { x, y, z -> Triple(x, y, z) }
