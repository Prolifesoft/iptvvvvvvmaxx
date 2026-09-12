package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.PlaylistRepository
import com.example.model.SupportRepository
import com.example.model.db.PlaybackProgressEntity
import com.example.parser.ItemType
import com.example.parser.M3uItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onPlayStream: (M3uItem) -> Unit,
    onNavigateToAuth: () -> Unit = {},
    onNavigateToPlaylists: () -> Unit = {},
    onNavigateToDeviceInfo: () -> Unit = {}
) {
    val playWithPlaylist: (M3uItem, List<M3uItem>) -> Unit = { item, playlist ->
        com.example.model.PlayerRepository.currentPlaylist = playlist
        onPlayStream(item)
    }

    val context = LocalContext.current
    val currentLang by com.example.model.AppLanguageManager.currentLanguage.collectAsState()
    val allItems by PlaylistRepository.playlist.collectAsState()
    val favoriteUrls by com.example.model.FavoritesManager.favoriteUrls.collectAsState()
    val db = remember { com.example.model.db.AppDatabase.getDatabase(context) }
    val recentMovies by db.iptvDao().getRecentProgressForType("MOVIE").collectAsState(initial = emptyList())
    val recentSeries by db.iptvDao().getRecentProgressForType("SERIES").collectAsState(initial = emptyList())
    val recentAll: List<PlaybackProgressEntity> by db.iptvDao().getAllRecentProgress().collectAsState(initial = emptyList())

    // Tabs: 0: Home, 1: Movies, 2: Series, 3: Live TV, 4: Favorites
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.nav_home),
        stringResource(R.string.tab_movies),
        stringResource(R.string.tab_series),
        stringResource(R.string.tab_live),
        stringResource(R.string.nav_favorites)
    )
    val tabIcons = listOf(
        Icons.Default.Home,
        Icons.Default.Movie,
        Icons.Default.Tv,
        Icons.Default.LiveTv,
        Icons.Default.Favorite
    )

    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showCategoryDrawer by remember { mutableStateOf(false) }

    // Navigation and Sheets state
    var selectedSeries by remember { mutableStateOf<List<M3uItem>?>(null) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var showSupportSheet by remember { mutableStateOf(false) }
    var showGeneralReportDialog by remember { mutableStateOf(false) }
    var showProUpgradeDialog by remember { mutableStateOf(false) }

    var itemToUnlock by remember { mutableStateOf<M3uItem?>(null) }
    var seriesToUnlock by remember { mutableStateOf<List<M3uItem>?>(null) }

    // TMDb and ESPN state
    var tmdb30DaysItems by remember { mutableStateOf<List<ImdbUpcomingItem>>(emptyList()) }
    var tmdb60DaysItems by remember { mutableStateOf<List<ImdbUpcomingItem>>(emptyList()) }
    var liveFixtures by remember { mutableStateOf<List<MatchFixture>>(emptyList()) }
    var selectedImdbTimeFrame by remember { mutableStateOf(ImdbTimeFrame.ALL) }
    var selectedImdbItem by remember { mutableStateOf<ImdbUpcomingItem?>(null) }
    var notifiedTmdbIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // Fetch TMDB
            try {
                val apiKey = "15745c1c46c264d1170db38ea66049e0"
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val cal = Calendar.getInstance()
                val dateToday = sdf.format(cal.time)
                cal.add(Calendar.DAY_OF_YEAR, 30)
                val date30 = sdf.format(cal.time)
                cal.add(Calendar.DAY_OF_YEAR, 30)
                val date60 = sdf.format(cal.time)

                val url30 = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&language=tr-TR&primary_release_date.gte=$dateToday&primary_release_date.lte=$date30&sort_by=popularity.desc"
                val conn30 = URL(url30).openConnection() as HttpURLConnection
                conn30.connectTimeout = 8000
                conn30.readTimeout = 8000
                if (conn30.responseCode == 200) {
                    val json30 = conn30.inputStream.bufferedReader().use { it.readText() }
                    val parsed30 = parseTmdbList(json30, ImdbTimeFrame.THIRTY_DAYS)
                    if (parsed30.isNotEmpty()) {
                        withContext(Dispatchers.Main) { tmdb30DaysItems = parsed30 }
                    }
                }

                val url60 = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&language=tr-TR&primary_release_date.gte=$dateToday&primary_release_date.lte=$date60&sort_by=popularity.desc"
                val conn60 = URL(url60).openConnection() as HttpURLConnection
                conn60.connectTimeout = 8000
                conn60.readTimeout = 8000
                if (conn60.responseCode == 200) {
                    val json60 = conn60.inputStream.bufferedReader().use { it.readText() }
                    val parsed60 = parseTmdbList(json60, ImdbTimeFrame.SIXTY_DAYS)
                    if (parsed60.isNotEmpty()) {
                        withContext(Dispatchers.Main) { tmdb60DaysItems = parsed60 }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Fetch ESPN
            try {
                val calEspn = Calendar.getInstance()
                calEspn.add(Calendar.DAY_OF_YEAR, -2)
                val sdfEspn = SimpleDateFormat("yyyyMMdd", Locale.US)
                val dateStart = sdfEspn.format(calEspn.time)
                calEspn.add(Calendar.DAY_OF_YEAR, 30)
                val dateEnd = sdfEspn.format(calEspn.time)

                val espnUrl = "https://site.api.espn.com/apis/site/v2/sports/soccer/tur.1/scoreboard?dates=$dateStart-$dateEnd&limit=100"
                val espnConn = URL(espnUrl).openConnection() as HttpURLConnection
                espnConn.connectTimeout = 8000
                espnConn.readTimeout = 8000
                if (espnConn.responseCode == 200) {
                    val espnJson = espnConn.inputStream.bufferedReader().use { it.readText() }
                    val parsedFixtures = parseEspnFixtures(espnJson)
                    if (parsedFixtures.isNotEmpty()) {
                        withContext(Dispatchers.Main) { liveFixtures = parsedFixtures }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val currentType: ItemType? = remember(selectedTabIndex) {
        when (selectedTabIndex) {
            1 -> ItemType.MOVIE
            2 -> ItemType.SERIES
            3 -> ItemType.LIVE
            else -> null
        }
    }

    val displayGroups = remember(allItems, currentType) {
        if (currentType != null) PlaylistRepository.getGroups(currentType) else emptyList()
    }
    var selectedGroup by remember(selectedTabIndex) { mutableStateOf<String?>(null) }

    val currentTabItems = remember(allItems, selectedTabIndex, selectedGroup, searchQuery, favoriteUrls) {
        when (selectedTabIndex) {
            1 -> {
                PlaylistRepository.getItemsForGroup(selectedGroup, ItemType.MOVIE).filter {
                    it.title.contains(searchQuery, ignoreCase = true)
                }
            }
            2 -> {
                PlaylistRepository.getItemsForGroup(selectedGroup, ItemType.SERIES).filter {
                    it.title.contains(searchQuery, ignoreCase = true) || (it.seriesName?.contains(searchQuery, ignoreCase = true) == true)
                }
            }
            3 -> {
                PlaylistRepository.getItemsForGroup(selectedGroup, ItemType.LIVE).filter {
                    it.title.contains(searchQuery, ignoreCase = true)
                }
            }
            4 -> {
                allItems.filter { favoriteUrls.contains(it.url) && it.title.contains(searchQuery, ignoreCase = true) }
            }
            else -> emptyList()
        }
    }

    val groupCounts = remember(allItems, currentType) {
        if (currentType != null) {
            allItems.filter { it.type == currentType }
                .groupingBy { it.group ?: "" }
                .eachCount()
        } else emptyMap()
    }
    val totalTypeCount = remember(allItems, currentType) {
        if (currentType != null) allItems.count { it.type == currentType } else 0
    }

    val imdbReleases = remember(tmdb30DaysItems, tmdb60DaysItems, selectedImdbTimeFrame) {
        val all = tmdb30DaysItems + tmdb60DaysItems
        when (selectedImdbTimeFrame) {
            ImdbTimeFrame.ALL -> all.distinctBy { it.id }
            ImdbTimeFrame.THIRTY_DAYS -> tmdb30DaysItems
            ImdbTimeFrame.SIXTY_DAYS -> tmdb60DaysItems
        }
    }

    if (itemToUnlock != null) {
        com.example.ui.components.PinUnlockDialog(
            onUnlock = {
                val item = itemToUnlock
                itemToUnlock = null
                if (item != null) onPlayStream(item)
            },
            onDismiss = { itemToUnlock = null }
        )
    }

    if (seriesToUnlock != null) {
        com.example.ui.components.PinUnlockDialog(
            onUnlock = {
                val s = seriesToUnlock
                seriesToUnlock = null
                if (s != null) selectedSeries = s
            },
            onDismiss = { seriesToUnlock = null }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            // Top App Bar / Navigation Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 8.dp).clickable { selectedTabIndex = 0 }
                ) {
                    Icon(
                        Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 9.sp
                    )
                }

                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.weight(1f),
                    containerColor = Color.Transparent,
                    edgePadding = 0.dp,
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = {
                                selectedTabIndex = index
                                selectedGroup = null
                                searchQuery = ""
                            },
                            text = { Text(title, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            icon = { Icon(tabIcons[index], contentDescription = title, modifier = Modifier.size(18.dp)) },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = Color.Gray
                        )
                    }
                }

                IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.search_placeholder),
                        tint = if (isSearchExpanded) MaterialTheme.colorScheme.primary else Color.White
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu_desc), tint = Color.White)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.profile_desc)) },
                            onClick = {
                                showProfileSheet = true
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.support_tickets_title)) },
                            onClick = { showSupportSheet = true; showMenu = false },
                            leadingIcon = { Icon(Icons.Default.SupportAgent, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.report_issue_btn)) },
                            onClick = { showGeneralReportDialog = true; showMenu = false },
                            leadingIcon = { Icon(Icons.Default.Report, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.playlists_title)) },
                            onClick = { onNavigateToPlaylists(); showMenu = false },
                            leadingIcon = { Icon(Icons.Default.Subscriptions, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.device_info_title)) },
                            onClick = { onNavigateToDeviceInfo(); showMenu = false },
                            leadingIcon = { Icon(Icons.Default.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_desc)) },
                            onClick = {
                                showSettingsSheet = true
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                        )
                        if (!com.example.model.DeviceManager.isProPurchased()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.btn_upgrade_package), color = Color(0xFFFFB300), fontWeight = FontWeight.Bold) },
                                onClick = { showProUpgradeDialog = true; showMenu = false },
                                leadingIcon = { Icon(Icons.Default.Diamond, contentDescription = null, tint = Color(0xFFFFB300)) }
                            )
                        }
                    }
                }
            }

            // Support Notification Banner
            val notification by SupportRepository.unreadNotification.collectAsState()
            if (notification != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable {
                            showSupportSheet = true
                            SupportRepository.dismissNotification()
                        },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFF69F0AE), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.notification_banner_title), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(stringResource(R.string.notification_banner_msg), color = Color.LightGray, fontSize = 10.sp)
                        }
                        TextButton(onClick = {
                            showSupportSheet = true
                            SupportRepository.dismissNotification()
                        }) {
                            Text(stringResource(R.string.view_ticket_btn), color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        IconButton(onClick = { SupportRepository.dismissNotification() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Search Bar & Category Filter Trigger
            AnimatedVisibility(visible = isSearchExpanded || (selectedTabIndex in 1..3 && displayGroups.isNotEmpty())) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSearchExpanded) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.search_placeholder), color = Color.Gray, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_placeholder), tint = Color.Gray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp),
                            singleLine = true
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    if (selectedTabIndex in 1..3 && displayGroups.isNotEmpty()) {
                        IconButton(
                            onClick = { showCategoryDrawer = true },
                            modifier = Modifier
                                .size(42.dp)
                                .background(
                                    if (selectedGroup != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.List,
                                contentDescription = stringResource(R.string.categories),
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // Screen Content by Tab
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTabIndex) {
                    0 -> {
                        // TAB 0: HOME / ANASAYFA
                        val heroItem = remember(allItems) { allItems.firstOrNull { it.type == ItemType.MOVIE && !it.logo.isNullOrEmpty() } ?: allItems.firstOrNull() }
                        val popularMovies = remember(allItems) { allItems.filter { it.type == ItemType.MOVIE }.take(15) }
                        val liveChannels = remember(allItems) { allItems.filter { it.type == ItemType.LIVE }.take(15) }
                        val seriesItems = remember(allItems) { allItems.filter { it.type == ItemType.SERIES } }
                        val seriesGroups = remember(seriesItems) { seriesItems.groupBy { it.seriesName ?: it.title } }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            // Hero Banner
                            if (heroItem != null) {
                                item {
                                    HeroBanner(item = heroItem, onClick = {
                                        if (com.example.model.ParentalControlManager.isItemLocked(heroItem)) {
                                            itemToUnlock = heroItem
                                        } else {
                                            playWithPlaylist(heroItem, listOf(heroItem))
                                        }
                                    })
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }

                            // ESPN Match Fixtures
                            if (liveFixtures.isNotEmpty()) {
                                item {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.SportsSoccer, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Günün Maçları & Fikstür",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(liveFixtures) { fixture ->
                                                MatchFixtureCard(fixture = fixture, onClick = {
                                                    // Find matching live sports channel
                                                    val matchedChannel = allItems.find { channel ->
                                                        channel.type == ItemType.LIVE && fixture.channelKeywords.any { kw -> channel.title.contains(kw, ignoreCase = true) }
                                                    }
                                                    if (matchedChannel != null) {
                                                        if (com.example.model.ParentalControlManager.isItemLocked(matchedChannel)) {
                                                            itemToUnlock = matchedChannel
                                                        } else {
                                                            playWithPlaylist(matchedChannel, listOf(matchedChannel))
                                                        }
                                                    } else {
                                                        Toast.makeText(context, "${fixture.homeTeam} - ${fixture.awayTeam} maç yayını bulunamadı", Toast.LENGTH_SHORT).show()
                                                    }
                                                })
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(18.dp))
                                    }
                                }
                            }

                            // IMDb / TMDb Upcoming Releases
                            if (imdbReleases.isNotEmpty()) {
                                item {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    color = Color(0xFFF5C518),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        "TMDB",
                                                        color = Color.Black,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 11.sp,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Yakında Vizyondakiler",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp,
                                                    color = Color.White
                                                )
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                ImdbTimeFrame.values().forEach { tf ->
                                                    FilterChip(
                                                        selected = selectedImdbTimeFrame == tf,
                                                        onClick = { selectedImdbTimeFrame = tf },
                                                        label = { Text(tf.label, fontSize = 10.sp) },
                                                        colors = FilterChipDefaults.filterChipColors(
                                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                            selectedLabelColor = Color.White
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(imdbReleases) { item ->
                                                val isNotified = notifiedTmdbIds.contains(item.id)
                                                ImdbCard(
                                                    item = item,
                                                    isNotified = isNotified,
                                                    onNotifyToggle = {
                                                        notifiedTmdbIds = if (isNotified) {
                                                            notifiedTmdbIds - item.id
                                                        } else {
                                                            Toast.makeText(context, "'${item.title}' için bildirim planlandı", Toast.LENGTH_SHORT).show()
                                                            notifiedTmdbIds + item.id
                                                        }
                                                    },
                                                    onClick = { selectedImdbItem = item }
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(18.dp))
                                    }
                                }
                            }

                            // Recent Progress (Son İzlenenler)
                            if (recentAll.isNotEmpty()) {
                                item {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = stringResource(R.string.continue_watching),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                        )
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(recentAll) { progress ->
                                                ProgressCard(progress = progress, onClick = {
                                                    val matched = allItems.find { it.url == progress.url }
                                                    if (matched != null) {
                                                        if (com.example.model.ParentalControlManager.isItemLocked(matched)) {
                                                            itemToUnlock = matched
                                                        } else {
                                                            playWithPlaylist(matched, listOf(matched))
                                                        }
                                                    }
                                                })
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(18.dp))
                                    }
                                }
                            }

                            // Popular Movies
                            if (popularMovies.isNotEmpty()) {
                                item {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = stringResource(R.string.tab_movies),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = Color.White
                                            )
                                            Text(
                                                text = stringResource(R.string.filter_all),
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 12.sp,
                                                modifier = Modifier.clickable { selectedTabIndex = 1 }
                                            )
                                        }
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(popularMovies) { movie ->
                                                MovieCard(item = movie, onClick = {
                                                    if (com.example.model.ParentalControlManager.isItemLocked(movie)) {
                                                        itemToUnlock = movie
                                                    } else {
                                                        playWithPlaylist(movie, popularMovies)
                                                    }
                                                })
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(18.dp))
                                    }
                                }
                            }

                            // Popular Series
                            if (seriesGroups.isNotEmpty()) {
                                item {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = stringResource(R.string.tab_series),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = Color.White
                                            )
                                            Text(
                                                text = stringResource(R.string.filter_all),
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 12.sp,
                                                modifier = Modifier.clickable { selectedTabIndex = 2 }
                                            )
                                        }
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(seriesGroups.keys.take(15).toList()) { sName ->
                                                val episodes = seriesGroups[sName] ?: emptyList()
                                                val posterItem = episodes.firstOrNull { !it.logo.isNullOrEmpty() } ?: episodes.first()
                                                SeriesCard(
                                                    seriesName = sName,
                                                    item = posterItem,
                                                    episodeCount = episodes.size,
                                                    onClick = {
                                                        if (com.example.model.ParentalControlManager.isItemLocked(posterItem)) {
                                                            seriesToUnlock = episodes
                                                        } else {
                                                            selectedSeries = episodes
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(18.dp))
                                    }
                                }
                            }

                            // Live TV Channels
                            if (liveChannels.isNotEmpty()) {
                                item {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = stringResource(R.string.tab_live),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = Color.White
                                            )
                                            Text(
                                                text = stringResource(R.string.filter_all),
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 12.sp,
                                                modifier = Modifier.clickable { selectedTabIndex = 3 }
                                            )
                                        }
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(liveChannels) { channel ->
                                                Box(modifier = Modifier.width(130.dp)) {
                                                    ChannelCard(item = channel, onClick = {
                                                        if (com.example.model.ParentalControlManager.isItemLocked(channel)) {
                                                            itemToUnlock = channel
                                                        } else {
                                                            playWithPlaylist(channel, liveChannels)
                                                        }
                                                    })
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // TAB 1: MOVIES
                        val uncategorizedText = stringResource(R.string.uncategorized)
                        val itemsByGroup = remember(currentTabItems, uncategorizedText) { currentTabItems.groupBy { it.group ?: uncategorizedText } }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            if (currentTabItems.isNotEmpty() && selectedGroup == null && searchQuery.isEmpty()) {
                                val heroMovie = currentTabItems.randomOrNull() ?: currentTabItems.first()
                                item {
                                    HeroBanner(item = heroMovie, onClick = {
                                        if (com.example.model.ParentalControlManager.isItemLocked(heroMovie)) {
                                            itemToUnlock = heroMovie
                                        } else {
                                            playWithPlaylist(heroMovie, currentTabItems)
                                        }
                                    })
                                }
                            }

                            if (recentMovies.isNotEmpty() && selectedGroup == null && searchQuery.isEmpty()) {
                                item {
                                    Text(
                                        text = stringResource(R.string.continue_watching),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp, end = 16.dp)
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(recentMovies) { progress ->
                                            ProgressCard(progress = progress, onClick = {
                                                val matchedItem = allItems.find { it.url == progress.url }
                                                if (matchedItem != null) {
                                                    if (com.example.model.ParentalControlManager.isItemLocked(matchedItem)) {
                                                        itemToUnlock = matchedItem
                                                    } else {
                                                        playWithPlaylist(matchedItem, listOf(matchedItem))
                                                    }
                                                }
                                            })
                                        }
                                    }
                                }
                            }

                            itemsByGroup.forEach { (groupName, groupItems) ->
                                if (groupItems.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = groupName.uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp, end = 16.dp)
                                        )
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(groupItems) { movie ->
                                                MovieCard(item = movie, onClick = {
                                                    if (com.example.model.ParentalControlManager.isItemLocked(movie)) {
                                                        itemToUnlock = movie
                                                    } else {
                                                        playWithPlaylist(movie, groupItems)
                                                    }
                                                })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // TAB 2: SERIES
                        val seriesGroups = remember(currentTabItems) { currentTabItems.groupBy { it.seriesName ?: it.title } }

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 110.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (recentSeries.isNotEmpty() && selectedGroup == null && searchQuery.isEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Column {
                                        Text(
                                            text = stringResource(R.string.continue_watching),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(recentSeries) { progress ->
                                                ProgressCard(progress = progress, onClick = {
                                                    val matchedItem = allItems.find { it.url == progress.url }
                                                    if (matchedItem != null) {
                                                        if (com.example.model.ParentalControlManager.isItemLocked(matchedItem)) {
                                                            itemToUnlock = matchedItem
                                                        } else {
                                                            playWithPlaylist(matchedItem, listOf(matchedItem))
                                                        }
                                                    }
                                                })
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                }
                            }

                            items(seriesGroups.keys.toList()) { seriesName ->
                                val episodes = seriesGroups[seriesName] ?: emptyList()
                                val posterItem = episodes.firstOrNull { !it.logo.isNullOrEmpty() } ?: episodes.first()
                                SeriesCard(
                                    seriesName = seriesName,
                                    item = posterItem,
                                    episodeCount = episodes.size,
                                    onClick = {
                                        if (com.example.model.ParentalControlManager.isItemLocked(posterItem)) {
                                            seriesToUnlock = episodes
                                        } else {
                                            selectedSeries = episodes
                                        }
                                    }
                                )
                            }
                        }
                    }

                    3 -> {
                        // TAB 3: LIVE CHANNELS
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 120.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(currentTabItems) { channel ->
                                ChannelCard(item = channel, onClick = {
                                    if (com.example.model.ParentalControlManager.isItemLocked(channel)) {
                                        itemToUnlock = channel
                                    } else {
                                        playWithPlaylist(channel, currentTabItems)
                                    }
                                })
                            }
                        }
                    }

                    4 -> {
                        // TAB 4: FAVORITES
                        if (currentTabItems.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(stringResource(R.string.no_favorites_yet), color = Color.Gray, fontSize = 14.sp)
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 120.dp),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(currentTabItems) { item ->
                                    when (item.type) {
                                        ItemType.MOVIE -> MovieCard(item = item, onClick = {
                                            if (com.example.model.ParentalControlManager.isItemLocked(item)) {
                                                itemToUnlock = item
                                            } else {
                                                playWithPlaylist(item, currentTabItems)
                                            }
                                        })
                                        ItemType.SERIES -> {
                                            SeriesCard(
                                                seriesName = item.seriesName ?: item.title,
                                                item = item,
                                                episodeCount = 1,
                                                onClick = {
                                                    if (com.example.model.ParentalControlManager.isItemLocked(item)) {
                                                        itemToUnlock = item
                                                    } else {
                                                        playWithPlaylist(item, currentTabItems)
                                                    }
                                                }
                                            )
                                        }
                                        else -> ChannelCard(item = item, onClick = {
                                            if (com.example.model.ParentalControlManager.isItemLocked(item)) {
                                                itemToUnlock = item
                                            } else {
                                                playWithPlaylist(item, currentTabItems)
                                            }
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Category Drawer Panel Overlay
        androidx.compose.animation.AnimatedVisibility(
                    visible = showCategoryDrawer,
                    enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it }),
                    exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it }),
                    modifier = Modifier.fillMaxSize()
                ) {
                    BackHandler(enabled = showCategoryDrawer) {
                        showCategoryDrawer = false
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.7f))
                                .clickable { showCategoryDrawer = false }
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.82f)
                                .widthIn(min = 280.dp, max = 360.dp)
                                .clickable(enabled = false) {},
                            color = Color(0xFF141414),
                            shadowElevation = 16.dp
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.List,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = stringResource(R.string.categories).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    }
                                    IconButton(onClick = { showCategoryDrawer = false }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.close_desc),
                                            tint = Color.Gray
                                        )
                                    }
                                }

                                HorizontalDivider(color = Color(0xFF262626), thickness = 1.dp)

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    item {
                                        val isAllSelected = selectedGroup == null
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedGroup = null
                                                    showCategoryDrawer = false
                                                }
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = stringResource(R.string.filter_all).uppercase(),
                                                color = if (isAllSelected) MaterialTheme.colorScheme.primary else Color.White,
                                                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                fontSize = 15.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                                            )
                                            Text(
                                                text = "$totalTypeCount",
                                                color = if (isAllSelected) MaterialTheme.colorScheme.primary else Color(0xFFCCCCCC),
                                                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }

                                    items(displayGroups.size) { index ->
                                        val group = displayGroups[index]
                                        val isSelected = selectedGroup == group
                                        val count = groupCounts[group] ?: 0

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedGroup = group
                                                    showCategoryDrawer = false
                                                }
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = group.uppercase(),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "$count",
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFCCCCCC),
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                    fontSize = 13.sp,
                                                    modifier = Modifier.padding(end = 8.dp)
                                                )
                                                val hasPin = com.example.model.ParentalControlManager.pinCode.collectAsState().value != null
                                                val lockedGroups by com.example.model.ParentalControlManager.lockedGroups.collectAsState()
                                                val isGroupLocked = hasPin && lockedGroups.contains(group)
                                                var showGroupUnlockDialog by remember { mutableStateOf(false) }

                                                IconButton(
                                                    onClick = {
                                                        if (!hasPin) {
                                                            Toast.makeText(context, context.getString(R.string.parental_control_desc), Toast.LENGTH_SHORT).show()
                                                        } else if (isGroupLocked) {
                                                            showGroupUnlockDialog = true
                                                        } else {
                                                            com.example.model.ParentalControlManager.toggleGroupLock(group)
                                                            Toast.makeText(context, context.getString(R.string.parental_channel_locked), Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        if (isGroupLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                                        contentDescription = null,
                                                        tint = if (isGroupLocked) Color(0xFFEF5350) else Color.Gray,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                if (showGroupUnlockDialog) {
                                                    com.example.ui.components.PinUnlockDialog(
                                                        onUnlock = {
                                                            com.example.model.ParentalControlManager.toggleGroupLock(group)
                                                            showGroupUnlockDialog = false
                                                            Toast.makeText(context, context.getString(R.string.parental_channel_unlocked), Toast.LENGTH_SHORT).show()
                                                        },
                                                        onDismiss = { showGroupUnlockDialog = false }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

    // Series Detail Bottom Sheet
    if (selectedSeries != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedSeries = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            com.example.ui.ProvideAppLocale(currentLang) {
                SeriesDetailSheet(items = selectedSeries!!, onPlayStream = { item ->
                    selectedSeries = null
                    if (com.example.model.ParentalControlManager.isItemLocked(item)) {
                        itemToUnlock = item
                    } else {
                        playWithPlaylist(item, selectedSeries!!)
                    }
                })
            }
        }
    }

    // Settings Bottom Sheet
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            com.example.ui.ProvideAppLocale(currentLang) {
                SettingsSheetContent(
                    onClose = { showSettingsSheet = false },
                    onOpenSupport = { showSupportSheet = true }
                )
            }
        }
    }

    // Profile Settings Bottom Sheet
    if (showProfileSheet) {
        ModalBottomSheet(
            onDismissRequest = { showProfileSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            com.example.ui.ProvideAppLocale(currentLang) {
                ProfileSettingsSheet(onNavigateToAuth = {
                    showProfileSheet = false
                    onNavigateToAuth()
                })
            }
        }
    }

    // Support Tickets Bottom Sheet
    if (showSupportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSupportSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            com.example.ui.ProvideAppLocale(currentLang) {
                SupportTicketsSheet(onClose = { showSupportSheet = false })
            }
        }
    }

    // General Report Dialog
    if (showGeneralReportDialog) {
        com.example.ui.components.ReportConfirmDialog(
            title = stringResource(R.string.report_general_title),
            channelName = stringResource(R.string.report_general_scope),
            message = stringResource(R.string.report_general_msg),
            onDismiss = { showGeneralReportDialog = false }
        )
    }

    // TMDb Detail Dialog
    if (selectedImdbItem != null) {
        val currentItem = selectedImdbItem!!
        val isNotified = notifiedTmdbIds.contains(currentItem.id)
        ImdbDetailDialog(
            item = currentItem,
            isNotified = isNotified,
            onNotifyToggle = {
                notifiedTmdbIds = if (isNotified) {
                    notifiedTmdbIds - currentItem.id
                } else {
                    Toast.makeText(context, "'${currentItem.title}' için bildirim planlandı", Toast.LENGTH_SHORT).show()
                    notifiedTmdbIds + currentItem.id
                }
            },
            onDismiss = { selectedImdbItem = null }
        )
    }

    // Pro Upgrade Restriction Dialog
    if (showProUpgradeDialog) {
        com.example.ui.components.ProUpgradeDialog(
            onDismiss = { showProUpgradeDialog = false }
        )
    }
}
}
