package com.darren.anitrackpulse

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.darren.anitrackpulse.data.AnimeEntry
import com.darren.anitrackpulse.data.AnimeStatus
import com.darren.anitrackpulse.network.AnimeSearchResult
import com.darren.anitrackpulse.ui.AniTrackUiState
import com.darren.anitrackpulse.ui.AniTrackViewModel
import com.darren.anitrackpulse.ui.theme.AniTrackPulseTheme
import kotlinx.coroutines.launch
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
            LaunchedEffect(uiState.darkMode) {
                AppCompatDelegate.setDefaultNightMode(if (uiState.darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
            }
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

enum class ScreenDestination(val label: String) { WATCHLIST("Watchlist"), SEARCH("Search"), CALENDAR("Calendar"), SETTINGS("Settings") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AniTrackRoot(uiState: AniTrackUiState, vm: AniTrackViewModel) {
    var currentScreen by rememberSaveable { mutableStateOf(ScreenDestination.WATCHLIST) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            var menuExpanded by remember { mutableStateOf(false) }
            TopAppBar(
                title = { Text("AniTrackPulse", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("Settings") }, onClick = { menuExpanded = false; currentScreen = ScreenDestination.SETTINGS })
                        }
                    }
                },
                actions = { NotificationIcon(hasUnread = uiState.hasUnreadNotifications, count = uiState.notifications.size) { vm.toggleNotificationPanel() } }
            )
        },
        bottomBar = {
            NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                listOf(ScreenDestination.WATCHLIST to Icons.Default.Star, ScreenDestination.SEARCH to Icons.Default.Search, ScreenDestination.CALENDAR to Icons.Default.CalendarMonth).forEach { (screen, icon) ->
                    NavigationBarItem(selected = currentScreen == screen, onClick = { currentScreen = screen }, icon = { Icon(icon, screen.label) }, label = { Text(screen.label, fontSize = 11.sp) })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(targetState = currentScreen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { screen ->
                when (screen) {
                    ScreenDestination.WATCHLIST -> WatchlistScreen(uiState, vm)
                    ScreenDestination.SEARCH -> SearchScreen(uiState, vm) { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                    ScreenDestination.CALENDAR -> CalendarScreen(uiState, vm)
                    ScreenDestination.SETTINGS -> SettingsScreen(uiState, vm)
                }
            }
            if (uiState.isNotificationPanelOpen) NotificationPanel(uiState, vm)
        }
    }
}

@Composable
fun NotificationIcon(hasUnread: Boolean, count: Int, onClick: () -> Unit) {
    Box(modifier = Modifier.padding(end = 8.dp).size(36.dp), contentAlignment = Alignment.Center) {
        IconButton(onClick = onClick) {
            Icon(
                if (hasUnread) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                contentDescription = "Notifications",
                tint = if (hasUnread) MaterialTheme.colorScheme.primary else LocalContentColor.current
            )
        }
        if (count > 0) {
            Box(modifier = Modifier.align(Alignment.TopEnd).size(16.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.error), contentAlignment = Alignment.Center) {
                Text(if (count > 9) "9+" else count.toString(), color = Color.White, fontSize = 10.sp)
            }
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
fun WatchlistScreen(uiState: AniTrackUiState, vm: AniTrackViewModel) {
    val filtered = uiState.savedAnime.filter { it.status == uiState.selectedStatus }
    val listState = rememberLazyListState()
    LaunchedEffect(uiState.focusedAnimeId, filtered) {
        val targetId = uiState.focusedAnimeId ?: return@LaunchedEffect
        val index = filtered.indexOfFirst { it.id == targetId }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            vm.clearFocus()
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
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
        else LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(filtered, key = { it.id }) { entry ->
                AnimeCard(entry, { newCount -> vm.updateWatchedEpisodes(entry.id.toLong(), newCount) }, { vm.deleteAnime(entry) }, { vm.moveAnime(entry.id.toLong(), it) }, { vm.refreshAnime(entry.id.toLong()) })
            }
        }
    }
}

@Composable
fun SearchScreen(uiState: AniTrackUiState, vm: AniTrackViewModel, onAddedMessage: (String) -> Unit) {
    val savedIds = remember(uiState.savedAnime) { uiState.savedAnime.map { it.id }.toSet() }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(value = uiState.searchQuery, onValueChange = vm::updateSearchQuery, label = { Text("Search anime") }, modifier = Modifier.fillMaxWidth(), singleLine = true, trailingIcon = {
            IconButton(onClick = vm::search) {
                if (uiState.isSearching) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.4.dp) else Icon(Icons.Default.Search, contentDescription = "Search")
            }
        })
        Spacer(Modifier.height(14.dp))
        if (uiState.searchResults.isEmpty()) EmptyPanel("Search results appear here", "Type part of a title and matching anime will show automatically.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(uiState.searchResults, key = { it.id }) { result ->
                SearchResultCard(result, savedIds.contains(result.id)) { status -> vm.addAnime(result, status); onAddedMessage("Added to ${status.displayName()}") }
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
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(upcoming, key = { it.id }) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PosterImage(entry.coverImage)
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Episode ${entry.nextEpisode ?: "?"}", color = MaterialTheme.colorScheme.primary)
                            Text(formatAiring(entry.nextAiringAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Folder: ${entry.status.displayName()}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
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
            }
        }
    }
}

@Composable
fun SearchResultCard(result: AnimeSearchResult, alreadyAdded: Boolean, onAdd: (AnimeStatus) -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            PosterImage(result.coverImage)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(result.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
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

@Composable
fun AnimeCard(entry: AnimeEntry, onIncrementCapped: (Int) -> Unit, onDelete: () -> Unit, onMove: (AnimeStatus) -> Unit, onRefresh: () -> Unit) {
    val maxEpisodes = entry.totalEpisodes ?: Int.MAX_VALUE
    val canIncrease = entry.watchedEpisodes < maxEpisodes
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PosterImage(entry.coverImage)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Watched ${entry.watchedEpisodes}${entry.totalEpisodes?.let { " / $it" } ?: ""}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(entry.nextAiringAt?.let { "Episode ${entry.nextEpisode ?: "?"} airs ${formatAiring(it)}" } ?: "No airing date available", color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onIncrementCapped((entry.watchedEpisodes + 1).coerceAtMost(maxEpisodes)) }, enabled = canIncrease, modifier = Modifier.widthIn(min = 120.dp)) { Text(if (canIncrease) "+ Episode" else "Max reached") }
                OutlinedButton(onClick = onRefresh) { Text("Refresh") }
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AnimeStatus.entries) { status -> FilterChip(selected = entry.status == status, onClick = { onMove(status) }, label = { Text(status.shortName()) }) }
            }
        }
    }
}

@Composable
fun PosterImage(url: String?) {
    AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(width = 82.dp, height = 112.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop)
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
fun epochToLocalDate(epochSeconds: Long): LocalDate = Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDate()
fun dayChipLabel(date: LocalDate, today: LocalDate): String = if (date == today) "Today" else date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
fun selectedDayTitle(date: LocalDate, today: LocalDate): String {
    val dayName = if (date == today) "Today" else date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return "$dayName • ${date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))}"
}
