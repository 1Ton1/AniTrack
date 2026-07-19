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
import com.darren.anitrackpulse.network.AnimeDetails
import com.darren.anitrackpulse.network.AnimeSearchResult
import com.darren.anitrackpulse.network.AnimeSeason
import com.darren.anitrackpulse.network.SearchFormat
import com.darren.anitrackpulse.network.SearchSort
import com.darren.anitrackpulse.network.SearchStatusFilter
import com.darren.anitrackpulse.repo.AnimeRepository
import java.time.LocalDate
import com.darren.anitrackpulse.sendReleaseSystemNotification
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val searchFormat: SearchFormat = SearchFormat.ANY,
    val searchStatus: SearchStatusFilter = SearchStatusFilter.ANY,
    val searchSort: SearchSort = SearchSort.POPULARITY,
    val seasonalResults: List<AnimeSearchResult> = emptyList(),
    val isSeasonalLoading: Boolean = false,
    val seasonLabel: String = "",
    val selectedStatus: AnimeStatus = AnimeStatus.WATCHING,
    val isSearching: Boolean = false,
    val isRefreshing: Boolean = false,
    val darkMode: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val focusedAnimeId: Int? = null,
    val notifications: List<UiNotification> = emptyList(),
    val isNotificationPanelOpen: Boolean = false,
    val animeDetails: AnimeDetails? = null,
    val isDetailsLoading: Boolean = false,
    val detailsError: String? = null
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

    private var searchJob: Job? = null

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), isSearching = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            runSearch()
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(searchQuery = "", searchResults = emptyList(), isSearching = false)
    }
    fun selectStatus(status: AnimeStatus) { _uiState.value = _uiState.value.copy(selectedStatus = status) }

    fun search() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch() }
    }

    private suspend fun runSearch() {
        _uiState.value = _uiState.value.copy(isSearching = true)
        try {
            val state = _uiState.value
            _uiState.value = _uiState.value.copy(
                searchResults = repo.searchAnime(
                    state.searchQuery,
                    state.searchFormat,
                    state.searchStatus,
                    state.searchSort
                )
            )
        } finally {
            _uiState.value = _uiState.value.copy(isSearching = false)
        }
    }

    private fun rerunSearchIfActive() {
        if (_uiState.value.searchQuery.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch() }
    }

    fun setSearchFormat(format: SearchFormat) {
        if (_uiState.value.searchFormat == format) return
        _uiState.value = _uiState.value.copy(searchFormat = format)
        rerunSearchIfActive()
    }

    fun setSearchStatus(status: SearchStatusFilter) {
        if (_uiState.value.searchStatus == status) return
        _uiState.value = _uiState.value.copy(searchStatus = status)
        rerunSearchIfActive()
    }

    fun setSearchSort(sort: SearchSort) {
        if (_uiState.value.searchSort == sort) return
        _uiState.value = _uiState.value.copy(searchSort = sort)
        rerunSearchIfActive()
    }

    fun loadSeasonal() {
        if (_uiState.value.isSeasonalLoading || _uiState.value.seasonalResults.isNotEmpty()) return
        val today = LocalDate.now()
        val season = AnimeSeason.fromMonth(today.monthValue)
        val year = today.year
        _uiState.value = _uiState.value.copy(
            isSeasonalLoading = true,
            seasonLabel = "${season.label} $year"
        )
        viewModelScope.launch {
            try {
                val results = repo.getSeasonalPopular(season, year)
                _uiState.value = _uiState.value.copy(seasonalResults = results, isSeasonalLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSeasonalLoading = false)
            }
        }
    }

    fun loadDetails(id: Int) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isDetailsLoading = true, detailsError = null, animeDetails = null)
        try {
            val details = repo.getAnimeDetails(id)
            _uiState.value = if (details != null) {
                _uiState.value.copy(isDetailsLoading = false, animeDetails = details, detailsError = null)
            } else {
                _uiState.value.copy(isDetailsLoading = false, animeDetails = null, detailsError = "Couldn't load details. Check your connection and try again.")
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isDetailsLoading = false, animeDetails = null, detailsError = "Couldn't load details. Check your connection and try again.")
        }
    }

    fun clearDetails() { _uiState.value = _uiState.value.copy(animeDetails = null, isDetailsLoading = false, detailsError = null) }

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
        sendReleaseSystemNotification(getApplication<Application>(), after.title, notification.message)
    }

    /** Fires both a phone notification and an in-app notification with fake data, used by the Settings "Test notification" action. */
    fun sendTestNotification() {
        val title = "Test Anime"
        val message = "Episode 1 just aired \u2014 this is a test notification."
        val notification = UiNotification(
            id = System.currentTimeMillis(),
            animeId = -1,
            title = title,
            message = message,
            timestamp = System.currentTimeMillis()
        )
        _uiState.value = _uiState.value.copy(notifications = listOf(notification) + _uiState.value.notifications)
        sendReleaseSystemNotification(getApplication<Application>(), title, message)
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
