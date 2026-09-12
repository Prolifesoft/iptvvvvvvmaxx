@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)
package com.example.ui.screens
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.content.Context
import android.media.AudioManager
import android.app.Activity
import android.net.Uri
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import kotlin.math.roundToInt

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.List
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.model.PlayerRepository
import com.example.model.db.AppDatabase
import com.example.model.db.PlaybackProgressEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen() {
    val context = LocalContext.current
    
    val activity = context as? Activity
    DisposableEffect(Unit) {
        val window = activity?.window
        val insetsController = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
        insetsController?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        insetsController?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            insetsController?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
    
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    var playingItem by remember { mutableStateOf(PlayerRepository.currentlyPlayingItem) }
    
    val epPrefix = stringResource(R.string.episode_dash_prefix)
    var showReportDialog by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var isControllerVisible by remember { mutableStateOf(true) }

    val exoPlayer = remember {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)
            .setEnableAudioTrackPlaybackParams(true)

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .setExceedAudioConstraintsIfNecessary(true)
                    .setExceedVideoConstraintsIfNecessary(true)
                    .setAllowAudioMixedChannelCountAdaptiveness(true)
                    .setAllowAudioMixedSampleRateAdaptiveness(true)
            )
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val dataSourceFactory = DefaultDataSource.Factory(
            context,
            DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        )
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()
            .apply {
                setAudioAttributes(audioAttributes, true)
                val playlist = PlayerRepository.currentPlaylist
                if (playlist.isNotEmpty()) {
                    val validPlaylist = playlist.filter { it.url.isNotBlank() }
                    val mediaItems = validPlaylist.map { 
                        val title = if (it.type == com.example.parser.ItemType.SERIES) {
                            (it.seriesName ?: "") + " $epPrefix${it.episode ?: "?"}: ${it.title}"
                        } else {
                            it.title
                        }
                        val cleanUrl = it.url.trim().let { u -> if (u.isNotEmpty() && !u.contains("://")) "http://$u" else u }
                        MediaItem.Builder()
                            .setUri(Uri.parse(cleanUrl))
                            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).setDisplayTitle(title).build())
                            .build()
                    }
                    setMediaItems(mediaItems)
                    val startIndex = validPlaylist.indexOfFirst { it.url == (playingItem?.url ?: "") }.coerceAtLeast(0)
                    seekTo(startIndex, C.TIME_UNSET)
                    prepare()
                    playWhenReady = true
                } else {
                    val currentItem = playingItem
                    val playUrl = playingItem?.url ?: ""
                    if (playUrl.isNotBlank()) {
                        val title = if (currentItem?.type == com.example.parser.ItemType.SERIES) {
                            (currentItem?.seriesName ?: "") + " $epPrefix${currentItem?.episode ?: "?"}: ${currentItem?.title ?: ""}"
                        } else {
                            currentItem?.title ?: ""
                        }
                        val cleanPlayUrl = playUrl.trim().let { u -> if (u.isNotEmpty() && !u.contains("://")) "http://$u" else u }
                        setMediaItem(
                            MediaItem.Builder()
                                .setUri(Uri.parse(cleanPlayUrl))
                                .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).setDisplayTitle(title).build())
                                .build()
                        )
                        prepare()
                        playWhenReady = true
                    }
                }
                
                addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        super.onMediaItemTransition(mediaItem, reason)
                        val index = currentMediaItemIndex
                        val validPlaylist = playlist.filter { it.url.isNotBlank() }
                        if (validPlaylist.isNotEmpty() && index in validPlaylist.indices) {
                            playingItem = validPlaylist[index]
                            PlayerRepository.currentlyPlayingItem = validPlaylist[index]
                        }
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        super.onPlayerError(error)
                        android.widget.Toast.makeText(context, "Oynatma Hatası: ${error.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                })
            }
    }
    
    LaunchedEffect((playingItem?.url ?: "")) {
        val currentItem = playingItem
        if (currentItem != null && (currentItem.type == com.example.parser.ItemType.MOVIE || currentItem.type == com.example.parser.ItemType.SERIES)) {
            val progress = db.iptvDao().getProgressForUrl((playingItem?.url ?: ""))
            if (progress != null && progress.positionMs > 0 && progress.positionMs < progress.durationMs - 10000) { // Don't resume if almost finished
                exoPlayer.seekTo(progress.positionMs)
            }
        }
    }
    
    // Periodically save progress
    LaunchedEffect((playingItem?.url ?: "")) {
        val currentItem = playingItem
        if (currentItem != null && (currentItem.type == com.example.parser.ItemType.MOVIE || currentItem.type == com.example.parser.ItemType.SERIES)) {
            while(true) {
                delay(5000)
                val position = exoPlayer.currentPosition
                val duration = exoPlayer.duration
                if (position > 0 && duration > 0) {
                    val progress = PlaybackProgressEntity(
                        url = (playingItem?.url ?: ""),
                        title = if (currentItem.type == com.example.parser.ItemType.SERIES) {
                            currentItem.seriesName + "$epPrefix${currentItem.episode ?: "?"}: ${currentItem.title}"
                        } else {
                            currentItem.title
                        },
                        logo = currentItem.logo,
                        type = currentItem.type.name,
                        positionMs = position,
                        durationMs = duration
                    )
                    db.iptvDao().insertPlaybackProgress(progress)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val currentItem = playingItem
            if (currentItem != null && (currentItem.type == com.example.parser.ItemType.MOVIE || currentItem.type == com.example.parser.ItemType.SERIES)) {
                val position = exoPlayer.currentPosition
                val duration = exoPlayer.duration
                if (position > 0 && duration > 0) {
                    scope.launch {
                        val progress = PlaybackProgressEntity(
                            url = (playingItem?.url ?: ""),
                            title = if (currentItem.type == com.example.parser.ItemType.SERIES) {
                                currentItem.seriesName + "$epPrefix${currentItem.episode ?: "?"}: ${currentItem.title}"
                            } else {
                                currentItem.title
                            },
                            logo = currentItem.logo,
                            type = currentItem.type.name,
                            positionMs = position,
                            durationMs = duration
                        )
                        db.iptvDao().insertPlaybackProgress(progress)
                    }
                }
            }
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
        val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }
        var currentVolume by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume) }
        var showVolumeIndicator by remember { mutableStateOf(false) }

        val activity = context as? Activity
        var currentBrightness by remember { mutableStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f) }
        var showBrightnessIndicator by remember { mutableStateOf(false) }

        LaunchedEffect(showVolumeIndicator) {
            if (showVolumeIndicator) {
                delay(2000)
                showVolumeIndicator = false
            }
        }
        
        LaunchedEffect(showBrightnessIndicator) {
            if (showBrightnessIndicator) {
                delay(2000)
                showBrightnessIndicator = false
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = {
                    PlayerView(context).apply {
                        player = exoPlayer
                        setControllerVisibilityListener(androidx.media3.ui.PlayerView.ControllerVisibilityListener { visibility ->
                            isControllerVisible = visibility == android.view.View.VISIBLE
                        })
                        setShowSubtitleButton(true)
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        setShowFastForwardButton(true)
                        setShowRewindButton(true)
                        setShowNextButton(true)
                        setShowPreviousButton(true)
                        controllerShowTimeoutMs = 4000
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // Left edge for Volume
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.2f)
                    .align(Alignment.CenterStart)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { showVolumeIndicator = true },
                            onDragEnd = { showVolumeIndicator = false },
                            onDragCancel = { showVolumeIndicator = false }
                        ) { change, dragAmount ->
                            change.consume()
                            val delta = -dragAmount / size.height
                            currentVolume = (currentVolume + delta).coerceIn(0f, 1f)
                            try {
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (currentVolume * maxVolume).roundToInt(), 0)
                            } catch (e: SecurityException) {
                                e.printStackTrace()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            showVolumeIndicator = true
                        }
                    }
            )
            
            // Right edge for Brightness
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.2f)
                    .align(Alignment.CenterEnd)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { showBrightnessIndicator = true },
                            onDragEnd = { showBrightnessIndicator = false },
                            onDragCancel = { showBrightnessIndicator = false }
                        ) { change, dragAmount ->
                            change.consume()
                            val delta = -dragAmount / size.height
                            currentBrightness = (currentBrightness + delta).coerceIn(0.01f, 1f)
                            activity?.let {
                                val attrs = it.window.attributes
                                attrs.screenBrightness = currentBrightness
                                it.window.attributes = attrs
                            }
                            showBrightnessIndicator = true
                        }
                    }
            )
            
            // Volume Indicator
            AnimatedVisibility(
                visible = showVolumeIndicator,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.height(100.dp).width(4.dp).background(Color.Gray.copy(alpha = 0.5f), CircleShape)) {
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(currentVolume)
                            .background(Color.White, CircleShape)
                            .align(Alignment.BottomCenter)
                        )
                    }
                }
            }
            
            // Brightness Indicator
            AnimatedVisibility(
                visible = showBrightnessIndicator,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.BrightnessMedium, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.height(100.dp).width(4.dp).background(Color.Gray.copy(alpha = 0.5f), CircleShape)) {
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(currentBrightness)
                            .background(Color.White, CircleShape)
                            .align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.TopEnd
        ) {

            AnimatedVisibility(
                visible = isControllerVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                val currentItem = playingItem
                val title = if (currentItem?.type == com.example.parser.ItemType.SERIES) {
                    (currentItem.seriesName ?: "") + " " + epPrefix + (currentItem.episode ?: "?") + ": " + (currentItem.title ?: "")
                } else {
                    currentItem?.title ?: ""
                }
                Text(
                    text = title,
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 64.dp, top = 24.dp, bottom = 24.dp)
                )
            }

            AnimatedVisibility(
                visible = isControllerVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FloatingActionButton(
                        onClick = { showPlaylistSheet = true },
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        contentColor = Color.White,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.List, contentDescription = "Kategori Listesi", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    FloatingActionButton(
                        onClick = { showReportDialog = true },
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        contentColor = Color.White,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Report, contentDescription = stringResource(R.string.report_issue_btn), tint = Color(0xFFFF7043), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }


        AnimatedVisibility(
            visible = showPlaylistSheet,
            enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showPlaylistSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(320.dp)
                        .background(Color(0xFF12121A).copy(alpha = 0.95f))
                        .clickable(enabled = false) {}
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Kanal Listesi",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                            val playlist = PlayerRepository.currentPlaylist
                            Text(
                                "${playlist.size} Yayın",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                        androidx.compose.material3.IconButton(
                            onClick = { showPlaylistSheet = false }
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Kapat",
                                tint = Color.White
                            )
                        }
                    }

                    androidx.compose.material3.HorizontalDivider(
                        color = Color.DarkGray.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    var searchQuery by remember { mutableStateOf("") }
                    androidx.compose.material3.OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Kanal Ara...", color = Color.Gray, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                androidx.compose.material3.IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        singleLine = true,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedContainerColor = Color(0xFF1E1E28),
                            unfocusedContainerColor = Color(0xFF1E1E28),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    val playlist = PlayerRepository.currentPlaylist
                    val filteredList = remember(playlist, searchQuery) {
                        if (searchQuery.isBlank()) playlist
                        else playlist.filter { (it.title ?: "").contains(searchQuery, ignoreCase = true) }
                    }

                    androidx.compose.foundation.lazy.LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredList.size) { index ->
                            val item = filteredList[index]
                            val isSelected = item.url == playingItem?.url

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                        else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        playingItem = item
                                        PlayerRepository.currentlyPlayingItem = item
                                        if (item.url.isNotBlank()) {
                                            val cUrl = item.url.trim().let { u -> if (u.isNotEmpty() && !u.contains("://")) "http://$u" else u }
                                            exoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.parse(cUrl)))
                                            exoPlayer.prepare()
                                            exoPlayer.playWhenReady = true
                                        }
                                        showPlaylistSheet = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp).padding(end = 6.dp)
                                    )
                                }
                                Text(
                                    text = item.title ?: "Bilinmeyen",
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showReportDialog) {
            val channelName = playingItem?.title ?: "Bilinmeyen Yayın"
            com.example.ui.components.ReportConfirmDialog(
                title = "Yayın Sorunu: $channelName",
                channelName = channelName,
                message = "'$channelName' akışında problem bildirildi.",
                onDismiss = { showReportDialog = false }
            )
        }
    }
}
