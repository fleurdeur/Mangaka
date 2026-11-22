package app.kurenai.feature.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kurenai.core.data.MangaRepository
import app.kurenai.core.data.UpdateRepository
import app.kurenai.core.model.UpdateEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdatesUiState(
    val updates: List<UpdateEntry> = emptyList(),
    val unread: Int = 0,
    val checking: Boolean = false,
    val progress: Float = 0f,
    val message: String? = null,
)

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
    private val mangaRepository: MangaRepository,
) : ViewModel() {
    private val checking = MutableStateFlow(false)
    private val progress = MutableStateFlow(0f)
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<UpdatesUiState> = combine(
        updateRepository.updates,
        updateRepository.unreadCount,
        checking,
        progress,
        message,
    ) { updates, unread, isChecking, p, msg ->
        UpdatesUiState(updates, unread, isChecking, p, msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdatesUiState())

    fun refresh() {
        if (checking.value) return
        viewModelScope.launch {
            checking.value = true
            message.value = null
            val found = updateRepository.checkForUpdates { current, total ->
                progress.value = if (total == 0) 0f else current.toFloat() / total
            }
            checking.value = false
            progress.value = 0f
            message.value = if (found == 0) "You are all caught up." else "$found new chapters found."
        }
    }

    fun markSeen(chapterId: String) = viewModelScope.launch { updateRepository.markSeen(chapterId) }

    fun openManga(mangaId: String, onReady: (String) -> Unit) = viewModelScope.launch {
        if (mangaRepository.manga(mangaId) != null) onReady(mangaId)
    }

    fun read(mangaId: String, chapterId: String, onReady: (String, String) -> Unit) =
        viewModelScope.launch {
            markSeen(chapterId)
            onReady(mangaId, chapterId)
        }
}
