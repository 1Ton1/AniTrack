package com.darren.anitrackpulse.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.darren.anitrackpulse.data.AnimeEntry
import com.darren.anitrackpulse.data.AnimeStatus
import com.darren.anitrackpulse.data.AppDatabase
import com.darren.anitrackpulse.data.SettingsRepository
import com.darren.anitrackpulse.network.AniListApi
import com.darren.anitrackpulse.network.AnimeSearchResult
import com.darren.anitrackpulse.repo.AnimeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiNotification(
    val id: Long,
    val animeId: Int,
    val title: String,
    val message: String,
    val timestamp: Long
)

data class AniTrackUiState(
    val savedAnime: List<AnimeEntry> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<AnimeSearchResult> = emptyList(),
    val selectedStatus: AnimeStatus = AnimeStatus.WATCHING,
    val isSearching: Boolean = false,
    val isRefreshing: Boolean = false,
    val darkMode: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val focusedAnimeId: Int? = null,
    val notifications: List<UiNotification> = emptyList(),
    val isNotificationPanelOpen: Boolean = false
) {
    val hasUnreadNotifications: Boolean get() = notifications.isNotEmpty()
}

class AniTrackViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsRepository(application)
    private val dao = AppDatabase.getDatabase(application).animeDao()
    private val repo = AnimeRepository(dao, AniListApi())
    private val _uiState = MutableStateFlow(AniTrackUiState())
    val uiState: StateFlow<AniTrackUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { repo.observeSavedAnime().collect { _uiState.value = _uiState.value.copy(savedAnime = it) } }
        viewModelScope.launch { settings.darkModeFlow.collect { _uiState.value = _uiState.value.copy(darkMode = it) } }
        viewModelScope.launch { settings.notificationsEnabledFlow.collect { _uiState.value = _uiState.value.copy(notificationsEnabled = it) } }
    }

    fun updateSearchQuery(query: String) { _uiState.value = _uiState.value.copy(searchQuery = query) }
    fun clearSearch() { _uiState.value = _uiState.value.copy(searchQuery = "", searchResults = emptyList()) }
    fun selectStatus(status: AnimeStatus) { _uiState.value = _uiState.value.copy(selectedStatus = status) }

    fun search() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isSearching = true)
        try {
            _uiState.value = _uiState.value.copy(searchResults = repo.searchAnime(_uiState.value.searchQuery))
        } finally {
            _uiState.value = _uiState.value.copy(isSearching = false)
        }
    }

    fun addAnime(result: AnimeSearchResult, status: AnimeStatus) = viewModelScope.launch { repo.saveAnime(result, status) }
    fun deleteAnime(entry: AnimeEntry) = viewModelScope.launch { repo.deleteAnime(entry) }
    fun moveAnime(id: Long, status: AnimeStatus) = viewModelScope.launch { repo.moveAnime(id.toInt(), status) }

    fun refreshAnime(id: Long) = viewModelScope.launch {
        val before = _uiState.value.savedAnime.firstOrNull { it.id.toLong() == id }
        val updated = repo.refreshAnime(id.toInt()) ?: return@launch
        maybeAddReleaseNotification(before, updated)
    }

    fun refreshAll() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        try {
            val beforeMap = _uiState.value.savedAnime.associateBy { it.id }
            repo.refreshAll().forEach { updated ->
                maybeAddReleaseNotification(beforeMap[updated.id], updated)
            }
        } finally {
            _uiState.value = _uiState.value.copy(isRefreshing = false) }
    }

    fun updateWatchedEpisodes(animeId: Long, newCount: Int) = viewModelScope.launch {
        val entry = _uiState.value.savedAnime.firstOrNull { it.id.toLong() == animeId } ?: return@launch
        val maxEpisodes = entry.totalEpisodes ?: Int.MAX_VALUE
        val safeCount = newCount.coerceAtMost(maxEpisodes)
        repeat((safeCount - entry.watchedEpisodes).coerceAtLeast(0)) { repo.incrementWatched(animeId.toInt()) }
        _uiState.value = _uiState.value.copy(focusedAnimeId = animeId.toInt())
    }

    private fun maybeAddReleaseNotification(before: AnimeEntry?, after: AnimeEntry) {
        if (!_uiState.value.notificationsEnabled) return
        val beforeEpisode = before?.nextEpisode
        val afterEpisode = after.nextEpisode
        val hasNewEpisode = afterEpisode != null && (beforeEpisode == null || afterEpisode > beforeEpisode)
        if (!hasNewEpisode) return
        val notification = UiNotification(
            id = System.currentTimeMillis(),
            animeId = after.id,
            title = after.title,
            message = "Episode ${afterEpisode} is now scheduled.",
            timestamp = System.currentTimeMillis()
        )
        val existing = _uiState.value.notifications.filterNot { it.animeId == after.id && it.message == notification.message }
        _uiState.value = _uiState.value.copy(notifications = listOf(notification) + existing)
    }

    fun clearFocus() { _uiState.value = _uiState.value.copy(focusedAnimeId = null) }
    fun toggleDarkMode(enabled: Boolean) = viewModelScope.launch { settings.setDarkMode(enabled) }
    fun toggleNotifications(enabled: Boolean) = viewModelScope.launch { settings.setNotificationsEnabled(enabled) }
    fun clearAllNotifications() { _uiState.value = _uiState.value.copy(notifications = emptyList()) }
    fun removeNotification(id: Long) { _uiState.value = _uiState.value.copy(notifications = _uiState.value.notifications.filterNot { it.id == id }) }
    fun toggleNotificationPanel() { _uiState.value = _uiState.value.copy(isNotificationPanelOpen = !_uiState.value.isNotificationPanelOpen) }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = AniTrackViewModel(application) as T
    }
}
