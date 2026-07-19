package com.darren.anitrackpulse

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.darren.anitrackpulse.data.AnimeEntry
import com.darren.anitrackpulse.data.AnimeStatus
import com.darren.anitrackpulse.network.AnimeDetails
import com.darren.anitrackpulse.network.AnimeSearchResult
import com.darren.anitrackpulse.network.RecommendedAnime
import com.darren.anitrackpulse.network.RelatedAnime
import com.darren.anitrackpulse.network.SearchFormat
import com.darren.anitrackpulse.network.SearchSort
import com.darren.anitrackpulse.network.SearchStatusFilter
import com.darren.anitrackpulse.ui.AniTrackUiState
import com.darren.anitrackpulse.ui.AniTrackViewModel
import com.darren.anitrackpulse.ui.theme.AniTrackPulseTheme
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askNotificationPermissionIfNeeded()
        setContent {
            val vm: AniTrackViewModel = viewModel(factory = AniTrackViewModel.Factory(application))
            val uiState by vm.uiState.collectAsState()
            AniTrackPulseTheme(darkTheme = uiState.darkMode) { AniTrackRoot(uiState, vm) }
        }
    }

    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
}

enum class ScreenDestination(val label: String) { WATCHLIST("Watchlist"), SEARCH("Search"), CALENDAR("Calendar"), STATS("Stats"), SETTINGS("Settings") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AniTrackRoot(uiState: AniTrackUiState, vm: AniTrackViewModel) {
    var currentScreen by rememberSaveable { mutableStateOf(ScreenDestination.WATCHLIST) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val detailStack = remember { mutableStateListOf<Int>() }
    val currentDetailId = detailStack.lastOrNull()
    val inDetails = currentDetailId != null

    val openDetails: (Int) -> Unit = { id -> detailStack.add(id) }
    val popDetails: () -> Unit = {
        if (detailStack.isNotEmpty()) detailStack.removeAt(detailStack.lastIndex)
        if (detailStack.isEmpty()) vm.clearDetails()
    }

    LaunchedEffect(currentDetailId) { currentDetailId?.let { vm.loadDetails(it) } }
    BackHandler(enabled = inDetails) { popDetails() }

    Scaffold(
        topBar = {
            var menuExpanded by remember { mutableStateOf(false) }
            TopAppBar(
                title = { Text(if (inDetails) "Details" else "AniTrackPulse", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (inDetails) {
                        IconButton(onClick = popDetails) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    } else {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(text = { Text("Settings") }, onClick = { menuExpanded = false; currentScreen = ScreenDestination.SETTINGS })
                            }
                        }
                    }
                },
                actions = { NotificationIcon(hasUnread = uiState.hasUnreadNotifications, count = uiState.notifications.size) { vm.toggleNotificationPanel() } }
            )
        },
        bottomBar = {
            NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                listOf(ScreenDestination.WATCHLIST to Icons.Default.Star, ScreenDestination.SEARCH to Icons.Default.Search, ScreenDestination.CALENDAR to Icons.Default.CalendarMonth, ScreenDestination.STATS to Icons.Default.Insights).forEach { (screen, icon) ->
                    NavigationBarItem(selected = !inDetails && currentScreen == screen, onClick = { detailStack.clear(); vm.clearDetails(); vm.exitSelectionMode(); currentScreen = screen }, icon = { Icon(icon, screen.label) }, label = { Text(screen.label, fontSize = 11.sp) })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (inDetails) {
                AnimeDetailsScreen(
                    uiState = uiState,
                    onOpenAnime = openDetails,
                    onRetry = { currentDetailId?.let { vm.loadDetails(it) } },
                    onAdd = { result, status -> vm.addAnime(result, status); scope.launch { snackbarHostState.showSnackbar("Added to ${status.displayName()}") } }
                )
            } else {
                AnimatedContent(targetState = currentScreen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { screen ->
                    when (screen) {
                        ScreenDestination.WATCHLIST -> WatchlistScreen(uiState, vm, openDetails)
                        ScreenDestination.SEARCH -> SearchScreen(uiState, vm, openDetails) { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                        ScreenDestination.CALENDAR -> CalendarScreen(uiState, vm)
                        ScreenDestination.STATS -> StatsScreen(uiState)
                        ScreenDestination.SETTINGS -> SettingsScreen(uiState, vm)
                    }
                }
            }
            if (uiState.isNotificationPanelOpen) NotificationPanel(uiState, vm)
        }
    }
}

@Composable
fun NotificationIcon(hasUnread: Boolean, count: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.padding(end = 4.dp)) {
        BadgedBox(
            badge = {
                if (count > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.error, contentColor = Color.White) {
                        Text(if (count > 9) "9+" else count.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        ) {
            Icon(
                if (hasUnread) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                contentDescription = "Notifications",
                tint = if (hasUnread) MaterialTheme.colorScheme.primary else LocalContentColor.current
            )
        }
    }
}

@Composable
fun NotificationPanel(uiState: AniTrackUiState, vm: AniTrackViewModel) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable(onClick = { vm.toggleNotificationPanel() }, indication = null, interactionSource = remember { MutableInteractionSource() }))
    Box(modifier = Modifier.fillMaxSize()) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp).wrapContentHeight().align(Alignment.TopCenter)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent releases", fontWeight = FontWeight.Bold)
                    TextButton(onClick = vm::clearAllNotifications, enabled = uiState.notifications.isNotEmpty()) { Text("Clear all") }
                }
                if (uiState.notifications.isEmpty()) {
                    Text("No recent released anime.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 320.dp)) {
                        items(uiState.notifications, key = { it.id }) { note ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(note.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(note.message, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { vm.removeNotification(note.id) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WatchlistScreen(uiState: AniTrackUiState, vm: AniTrackViewModel, onOpenDetails: (Int) -> Unit) {
    val filtered = remember(uiState.savedAnime, uiState.selectedStatus) {
        uiState.savedAnime.filter { it.status == uiState.selectedStatus }.sortedByDescending { it.isPinned }
    }
    val listState = rememberLazyListState()
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.focusedAnimeId, filtered) {
        val targetId = uiState.focusedAnimeId ?: return@LaunchedEffect
        val index = filtered.indexOfFirst { it.id == targetId }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            vm.clearFocus()
        }
    }
    BackHandler(enabled = uiState.isSelectionMode) { vm.exitSelectionMode() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            if (uiState.isSelectionMode) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = vm::exitSelectionMode) { Icon(Icons.Default.Close, contentDescription = "Exit selection") }
                        Text("${uiState.selectedIds.size} selected", fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AnimeStatus.entries) { status ->
                    FilterChip(selected = uiState.selectedStatus == status, onClick = { vm.selectStatus(status) }, label = { Text(status.displayName()) })
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::refreshAll) { Text(if (uiState.isRefreshing) "Refreshing..." else "Refresh saved") }
                AssistChip(onClick = {}, label = { Text("${filtered.size} in folder") })
            }
            Spacer(Modifier.height(12.dp))
            if (filtered.isEmpty()) EmptyPanel("No anime here yet", "Search for anime and save them to ${uiState.selectedStatus.displayName().lowercase()}.")
            else LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = if (uiState.isSelectionMode) 88.dp else 0.dp)) {
                items(filtered, key = { it.id }) { entry ->
                    AnimeCard(
                        entry = entry,
                        selectionMode = uiState.isSelectionMode,
                        selected = uiState.selectedIds.contains(entry.id),
                        onIncrementCapped = { newCount -> vm.updateWatchedEpisodes(entry.id.toLong(), newCount) },
                        onDelete = { vm.deleteAnime(entry) },
                        onMove = { vm.moveAnime(entry.id.toLong(), it) },
                        onRefresh = { vm.refreshAnime(entry.id.toLong()) },
                        onOpenDetails = { onOpenDetails(entry.id) },
                        onTogglePin = { vm.togglePin(entry) },
                        onStartRewatch = { vm.startRewatch(entry.id) },
                        onStopRewatch = { vm.stopRewatch(entry.id) },
                        onToggleSelect = { vm.toggleSelection(entry.id) },
                        onEnterSelection = { vm.enterSelectionMode(entry.id) }
                    )
                }
            }
        }

        if (uiState.isSelectionMode && uiState.selectedIds.isNotEmpty()) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { showMoveDialog = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Move to...")
                    }
                    OutlinedButton(onClick = { showDeleteDialog = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }

    if (showMoveDialog) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("Move ${uiState.selectedIds.size} to folder") },
            text = {
                Column {
                    AnimeStatus.entries.forEach { status ->
                        Text(
                            status.displayName(),
                            modifier = Modifier.fillMaxWidth().clickable { showMoveDialog = false; vm.bulkMove(status) }.padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMoveDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${uiState.selectedIds.size} anime?") },
            text = { Text("This removes the selected anime from your watchlist. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { showDeleteDialog = false; vm.bulkDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(uiState: AniTrackUiState, vm: AniTrackViewModel, onOpenDetails: (Int) -> Unit, onAddedMessage: (String) -> Unit) {
    val savedIds = remember(uiState.savedAnime) { uiState.savedAnime.map { it.id }.toSet() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { vm.loadSeasonal() }
    val onAdd: (AnimeSearchResult, AnimeStatus) -> Unit = { result, status ->
        vm.addAnime(result, status); onAddedMessage("Added to ${status.displayName()}")
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = vm::updateSearchQuery,
            label = { Text("Search anime") },
            placeholder = { Text("e.g. Frieren, One Piece") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (uiState.isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.4.dp)
                } else if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = vm::clearSearch) { Icon(Icons.Default.Close, contentDescription = "Clear") }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); vm.search() })
        )
        Spacer(Modifier.height(10.dp))
        SearchFilterRow(uiState, vm)
        Spacer(Modifier.height(12.dp))
        when {
            uiState.isSearching && uiState.searchResults.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            uiState.searchQuery.isBlank() ->
                SeasonalDiscovery(uiState, savedIds, onOpenDetails, onAdd)
            uiState.searchResults.isEmpty() ->
                EmptyPanel("No matches", "Nothing found for \"${uiState.searchQuery.trim()}\". Try a different title or adjust filters.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(uiState.searchResults, key = { it.id }) { result ->
                    SearchResultCard(
                        result = result,
                        alreadyAdded = savedIds.contains(result.id),
                        onOpenDetails = { onOpenDetails(result.id) },
                        onAdd = { status -> onAdd(result, status) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFilterRow(uiState: AniTrackUiState, vm: AniTrackViewModel) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterDropdownChip(
                label = "Format",
                selectedLabel = uiState.searchFormat.label,
                options = SearchFormat.entries.map { it.label to it },
                isActive = uiState.searchFormat != SearchFormat.ANY,
                onSelect = vm::setSearchFormat
            )
        }
        item {
            FilterDropdownChip(
                label = "Status",
                selectedLabel = uiState.searchStatus.label,
                options = SearchStatusFilter.entries.map { it.label to it },
                isActive = uiState.searchStatus != SearchStatusFilter.ANY,
                onSelect = vm::setSearchStatus
            )
        }
        item {
            FilterDropdownChip(
                label = "Sort",
                selectedLabel = uiState.searchSort.label,
                options = SearchSort.entries.map { it.label to it },
                isActive = uiState.searchSort != SearchSort.RELEVANCE,
                onSelect = vm::setSearchSort
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> FilterDropdownChip(
    label: String,
    selectedLabel: String,
    options: List<Pair<String, T>>,
    isActive: Boolean,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = isActive,
            onClick = { expanded = true },
            label = { Text("$label: $selectedLabel") },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (optionLabel, value) ->
                DropdownMenuItem(text = { Text(optionLabel) }, onClick = { expanded = false; onSelect(value) })
            }
        }
    }
}

@Composable
fun SeasonalDiscovery(
    uiState: AniTrackUiState,
    savedIds: Set<Int>,
    onOpenDetails: (Int) -> Unit,
    onAdd: (AnimeSearchResult, AnimeStatus) -> Unit
) {
    when {
        uiState.isSeasonalLoading && uiState.seasonalResults.isEmpty() ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        uiState.seasonalResults.isEmpty() ->
            EmptyPanel("Discover anime", "Type part of a title to search AniList, or check back for this season's popular titles.")
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Column {
                    Text(
                        "Popular this season",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (uiState.seasonLabel.isNotBlank()) {
                        Text(uiState.seasonLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            items(uiState.seasonalResults, key = { it.id }) { result ->
                SearchResultCard(
                    result = result,
                    alreadyAdded = savedIds.contains(result.id),
                    onOpenDetails = { onOpenDetails(result.id) },
                    onAdd = { status -> onAdd(result, status) }
                )
            }
        }
    }
}

@Composable
fun CalendarScreen(uiState: AniTrackUiState, vm: AniTrackViewModel) {
    val today = remember { LocalDate.now() }
    var selectedDay by remember { mutableStateOf(today) }
    val weekDays = remember(today) { (0..6).map { today.plusDays(it.toLong()) } }
    val upcoming = uiState.savedAnime
        .filter { it.nextAiringAt != null }
        .filter { entry -> entry.status != AnimeStatus.FINISHED || entry.nextAiringAt != null }
        .filter { entry -> entry.nextAiringAt?.let { epochToLocalDate(it) == selectedDay } ?: false }
        .sortedBy { it.nextAiringAt }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::refreshAll) { Text("Refresh schedule") }
            AssistChip(onClick = {}, label = { Text(selectedDayTitle(selectedDay, today)) })
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(weekDays) { day -> FilterChip(selected = selectedDay == day, onClick = { selectedDay = day }, label = { Text(dayChipLabel(day, today)) }) }
        }
        Spacer(Modifier.height(12.dp))
        if (upcoming.isEmpty()) EmptyPanel("No releases for ${selectedDayTitle(selectedDay, today)}", "Saved anime airing on this day will appear here.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(upcoming, key = { it.id }) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PosterImage(entry.coverImage)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = { Text("Episode ${entry.nextEpisode ?: "?"}") },
                                colors = AssistChipDefaults.assistChipColors(
                                    disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                            Text(airingCountdown(entry.nextAiringAt), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                            Text(formatAiring(entry.nextAiringAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Folder: ${entry.status.displayName()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

data class WatchStats(
    val totalAnime: Int,
    val episodesWatched: Int,
    val hoursWatched: Int,
    val perStatus: List<Pair<AnimeStatus, Int>>,
    val completionRate: Int?,
    val ratedCount: Int
)

private const val MINUTES_PER_EPISODE = 24

fun computeWatchStats(entries: List<AnimeEntry>): WatchStats {
    val totalAnime = entries.size
    val episodesWatched = entries.sumOf { it.watchedEpisodes }
    val hoursWatched = episodesWatched * MINUTES_PER_EPISODE / 60
    val perStatus = AnimeStatus.entries.map { status -> status to entries.count { it.status == status } }
    val withTotals = entries.filter { it.totalEpisodes != null }
    val completed = withTotals.count { it.watchedEpisodes >= (it.totalEpisodes ?: 0) }
    val completionRate = if (withTotals.isEmpty()) null else completed * 100 / withTotals.size
    return WatchStats(totalAnime, episodesWatched, hoursWatched, perStatus, completionRate, withTotals.size)
}

@Composable
fun StatsScreen(uiState: AniTrackUiState) {
    val stats = remember(uiState.savedAnime) { computeWatchStats(uiState.savedAnime) }
    if (uiState.savedAnime.isEmpty()) {
        EmptyPanel("No stats yet", "Save some anime to your watchlist to see your viewing stats.")
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Your stats", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(Modifier.weight(1f), "${stats.totalAnime}", "Anime tracked", Icons.Default.Star)
            StatTile(Modifier.weight(1f), "${stats.episodesWatched}", "Episodes watched", Icons.Default.PlayArrow)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(Modifier.weight(1f), "${stats.hoursWatched}h", "Hours watched", Icons.Default.Schedule)
            StatTile(
                Modifier.weight(1f),
                stats.completionRate?.let { "$it%" } ?: "—",
                "Completion rate",
                Icons.Default.CheckCircle
            )
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("By folder", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                stats.perStatus.forEach { (status, count) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(status.displayName(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$count", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (stats.completionRate != null) {
            Text(
                "Completion rate is based on ${stats.ratedCount} anime with a known episode count. Hours assume ${MINUTES_PER_EPISODE} minutes per episode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                "Add anime with known episode counts to see a completion rate. Hours assume ${MINUTES_PER_EPISODE} minutes per episode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StatTile(modifier: Modifier, value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsScreen(uiState: AniTrackUiState, vm: AniTrackViewModel) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Settings", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Dark mode")
                    Switch(checked = uiState.darkMode, onCheckedChange = vm::toggleDarkMode)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Notifications")
                    Switch(checked = uiState.notificationsEnabled, onCheckedChange = vm::toggleNotifications)
                }
                OutlinedButton(onClick = {
                    vm.refreshAll()
                    Toast.makeText(context, "Refresh started", Toast.LENGTH_SHORT).show()
                }) { Text("Check for releases now") }
                OutlinedButton(onClick = {
                    vm.sendTestNotification()
                    Toast.makeText(context, "Test notification sent", Toast.LENGTH_SHORT).show()
                }) { Text("Send test release notification") }
                Text(
                    "Sends a sample \"new episode\" alert as both a phone notification and an in-app notification, so you can confirm both are working.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun SearchResultCard(result: AnimeSearchResult, alreadyAdded: Boolean, onOpenDetails: () -> Unit, onAdd: (AnimeStatus) -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDetails)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            PosterImage(result.coverImage)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(result.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (result.isOngoing) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Ongoing", color = MaterialTheme.colorScheme.onPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text("Episodes: ${result.totalEpisodes ?: "Unknown"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                FilledIconButton(onClick = { if (!alreadyAdded) menuExpanded = true }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (alreadyAdded) Color(0xFF22C55E) else MaterialTheme.colorScheme.primary, contentColor = Color.White)) {
                    Icon(if (alreadyAdded) Icons.Default.Check else Icons.Default.Add, contentDescription = null)
                }
                if (!alreadyAdded) {
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        AnimeStatus.entries.forEach { status ->
                            DropdownMenuItem(text = { Text(status.displayName()) }, onClick = { menuExpanded = false; onAdd(status) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimeCard(
    entry: AnimeEntry,
    selectionMode: Boolean,
    selected: Boolean,
    onIncrementCapped: (Int) -> Unit,
    onDelete: () -> Unit,
    onMove: (AnimeStatus) -> Unit,
    onRefresh: () -> Unit,
    onOpenDetails: () -> Unit,
    onTogglePin: () -> Unit,
    onStartRewatch: () -> Unit,
    onStopRewatch: () -> Unit,
    onToggleSelect: () -> Unit,
    onEnterSelection: () -> Unit
) {
    val maxEpisodes = entry.totalEpisodes ?: Int.MAX_VALUE
    val canIncrease = entry.watchedEpisodes < maxEpisodes
    val cardModifier = if (selectionMode && selected) {
        Modifier.fillMaxWidth().border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
    } else {
        Modifier.fillMaxWidth()
    }
    Card(modifier = cardModifier) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth().combinedClickable(
                    onClick = { if (selectionMode) onToggleSelect() else onOpenDetails() },
                    onLongClick = { if (selectionMode) onToggleSelect() else onEnterSelection() }
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selectionMode) {
                    Icon(
                        if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (selected) "Selected" else "Not selected",
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
                PosterImage(entry.coverImage)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(entry.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (entry.isRewatching) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable(enabled = !selectionMode, onClick = onStopRewatch)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Rewatching", color = MaterialTheme.colorScheme.onPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text("Watched ${entry.watchedEpisodes}${entry.totalEpisodes?.let { " / $it" } ?: ""}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(remainingEpisodesLabel(entry), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(entry.nextAiringAt?.let { "Episode ${entry.nextEpisode ?: "?"} airs ${formatAiring(it)}" } ?: "No airing date available", color = MaterialTheme.colorScheme.primary)
                }
                if (!selectionMode) {
                    IconButton(onClick = onTogglePin) {
                        Icon(
                            if (entry.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (entry.isPinned) "Unpin" else "Pin",
                            tint = if (entry.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (!selectionMode) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onIncrementCapped((entry.watchedEpisodes + 1).coerceAtMost(maxEpisodes)) }, enabled = canIncrease, modifier = Modifier.widthIn(min = 120.dp)) { Text(if (canIncrease) "+ Episode" else "Max reached") }
                    OutlinedButton(onClick = onRefresh) { Text("Refresh") }
                    OutlinedButton(onClick = onDelete) { Text("Delete") }
                }
                if (entry.status == AnimeStatus.FINISHED && !entry.isRewatching) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onStartRewatch) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Rewatch")
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AnimeStatus.entries) { status -> FilterChip(selected = entry.status == status, onClick = { onMove(status) }, label = { Text(status.shortName()) }) }
                }
            }
        }
    }
}

fun remainingEpisodesLabel(entry: AnimeEntry): String {
    val total = entry.totalEpisodes ?: return "Total episodes unknown"
    val remaining = (total - entry.watchedEpisodes).coerceAtLeast(0)
    return if (remaining == 0) "All caught up" else "$remaining episode${if (remaining == 1) "" else "s"} remaining"
}

@Composable
fun PosterImage(url: String?) {
    AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(width = 82.dp, height = 112.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeDetailsScreen(
    uiState: AniTrackUiState,
    onOpenAnime: (Int) -> Unit,
    onRetry: () -> Unit,
    onAdd: (AnimeSearchResult, AnimeStatus) -> Unit
) {
    when {
        uiState.isDetailsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        uiState.detailsError != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Something went wrong", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(uiState.detailsError, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onRetry) { Text("Retry") }
            }
        }
        uiState.animeDetails != null -> AnimeDetailsContent(uiState.animeDetails, uiState.savedAnime, onOpenAnime, onAdd)
        else -> EmptyPanel("No details", "Open an anime to see its details.")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeDetailsContent(
    details: AnimeDetails,
    savedAnime: List<AnimeEntry>,
    onOpenAnime: (Int) -> Unit,
    onAdd: (AnimeSearchResult, AnimeStatus) -> Unit
) {
    val alreadyAdded = remember(savedAnime, details.id) { savedAnime.any { it.id == details.id } }
    var menuExpanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (!details.bannerImage.isNullOrBlank()) {
            AsyncImage(
                model = details.bannerImage,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(160.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PosterImage(details.coverImage)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(details.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    details.averageScore?.let {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                            Text("$it%", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    val meta = detailsMetaLine(details)
                    if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Box {
                Button(onClick = { menuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(if (alreadyAdded) Icons.Default.Check else Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (alreadyAdded) "In your list — change folder" else "Add to list")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    AnimeStatus.entries.forEach { status ->
                        DropdownMenuItem(text = { Text(status.displayName()) }, onClick = {
                            menuExpanded = false
                            onAdd(details.toSearchResult(), status)
                        })
                    }
                }
            }

            if (details.genres.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(details.genres) { genre -> AssistChip(onClick = {}, label = { Text(genre) }) }
                }
            }

            if (!details.description.isNullOrBlank()) {
                Text("Synopsis", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(details.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (details.relations.isNotEmpty()) {
                SectionHeader("Related", details.relations.size)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 4.dp)) {
                    items(details.relations, key = { it.id }) { rel -> RelatedCard(rel) { onOpenAnime(rel.id) } }
                }
            }

            if (details.recommendations.isNotEmpty()) {
                SectionHeader("Recommended", details.recommendations.size)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 4.dp)) {
                    items(details.recommendations, key = { it.id }) { rec -> RecommendationCard(rec) { onOpenAnime(rec.id) } }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun SectionHeader(title: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun RelatedCard(rel: RelatedAnime, onClick: () -> Unit) {
    Column(Modifier.width(120.dp).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AsyncImage(
            model = rel.coverImage,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        if (rel.relationType.isNotBlank()) Text(rel.relationType, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(rel.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun RecommendationCard(rec: RecommendedAnime, onClick: () -> Unit) {
    Column(Modifier.width(120.dp).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AsyncImage(
            model = rec.coverImage,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        rec.averageScore?.let { Text("$it%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
        Text(rec.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun EmptyPanel(title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun AnimeStatus.displayName(): String = when (this) {
    AnimeStatus.WATCHING -> "Watching"
    AnimeStatus.PLAN_TO_WATCH -> "Plan to Watch"
    AnimeStatus.FINISHED -> "Finished"
    AnimeStatus.DROPPED -> "Dropped"
}

fun AnimeStatus.shortName(): String = when (this) {
    AnimeStatus.WATCHING -> "Watching"
    AnimeStatus.PLAN_TO_WATCH -> "Plan"
    AnimeStatus.FINISHED -> "Finished"
    AnimeStatus.DROPPED -> "Dropped"
}

fun formatAiring(epochSeconds: Long?): String {
    if (epochSeconds == null) return "Unknown time"
    return Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, d MMM • HH:mm"))
}

fun airingCountdown(epochSeconds: Long?): String {
    if (epochSeconds == null) return "No airing date"
    val now = Instant.now()
    val target = Instant.ofEpochSecond(epochSeconds)
    if (!target.isAfter(now)) return "Aired"
    val duration = Duration.between(now, target)
    val totalMinutes = duration.toMinutes()
    if (totalMinutes < 60) return "Airs in ${totalMinutes.coerceAtLeast(1)}m"
    if (duration.toHours() < 24) {
        val hours = duration.toHours()
        val minutes = totalMinutes - hours * 60
        return "Airs in ${hours}h ${minutes}m"
    }
    val today = LocalDate.now()
    val airingDate = epochToLocalDate(epochSeconds)
    return when (val days = java.time.temporal.ChronoUnit.DAYS.between(today, airingDate)) {
        0L -> "Airing today"
        1L -> "Airing tomorrow"
        else -> "In $days days"
    }
}

fun detailsMetaLine(details: AnimeDetails): String {
    val parts = mutableListOf<String>()
    details.format?.takeIf { it.isNotBlank() }?.let { parts += it }
    val seasonPart = listOfNotNull(details.season?.takeIf { it.isNotBlank() }, details.seasonYear?.toString()).joinToString(" ")
    if (seasonPart.isNotBlank()) parts += seasonPart
    details.episodes?.let { parts += "$it eps" }
    details.studio?.takeIf { it.isNotBlank() }?.let { parts += it }
    details.status?.takeIf { it.isNotBlank() }?.let { parts += it }
    return parts.joinToString(" • ")
}

fun AnimeDetails.toSearchResult(): AnimeSearchResult = AnimeSearchResult(
    id = id,
    title = title,
    coverImage = coverImage,
    totalEpisodes = episodes
)
fun epochToLocalDate(epochSeconds: Long): LocalDate = Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDate()
fun dayChipLabel(date: LocalDate, today: LocalDate): String = if (date == today) "Today" else date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
fun selectedDayTitle(date: LocalDate, today: LocalDate): String {
    val dayName = if (date == today) "Today" else date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return "$dayName • ${date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))}"
}

private const val RELEASE_NOTIFICATION_CHANNEL_ID = "anime_releases"

/** Posts a real phone/system notification for an anime episode release. Used by the live release check and by the Settings test action. */
fun sendReleaseSystemNotification(context: Context, animeTitle: String, message: String) {
    if (Build.VERSION.SDK_INT >= 33) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            RELEASE_NOTIFICATION_CHANNEL_ID,
            "Anime releases",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Alerts when a saved anime has a new episode" }
        manager.createNotificationChannel(channel)
    }

    val notification = NotificationCompat.Builder(context, RELEASE_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(animeTitle)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify((animeTitle + message).hashCode(), notification)
}
