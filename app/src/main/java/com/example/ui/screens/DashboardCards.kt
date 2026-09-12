package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.R
import com.example.parser.M3uItem

@Composable
fun HeroBanner(item: M3uItem, onClick: () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clickable(onClick = onClick)
            .background(Color(0xFF0F0F17))
    ) {
        if (!item.logo.isNullOrEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.logo)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(0.2f),
                contentScale = ContentScale.Crop
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.9f)
                        )
                    )
                )
        )

        val favoriteUrls by com.example.model.FavoritesManager.favoriteUrls.collectAsState()
        val isFavorite = favoriteUrls.contains(item.url)
        val lockedChannels by com.example.model.ParentalControlManager.lockedChannels.collectAsState()
        val lockedGroups by com.example.model.ParentalControlManager.lockedGroups.collectAsState()
        val hasPin = com.example.model.ParentalControlManager.pinCode.collectAsState().value != null
        val isLocked = hasPin && (lockedChannels.contains(item.url) || lockedGroups.contains(item.group))
        var showUnlockDialog by remember { mutableStateOf(false) }
        var showReportDialog by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        ) {
            IconButton(
                onClick = { com.example.model.FavoritesManager.toggleFavorite(item.url) },
                modifier = Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            ) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isFavorite) Color.Red else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (!hasPin) {
                        Toast.makeText(context, context.getString(R.string.parental_control_desc), Toast.LENGTH_SHORT).show()
                    } else if (isLocked) {
                        showUnlockDialog = true
                    } else {
                        com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                        Toast.makeText(context, context.getString(R.string.parental_channel_locked), Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            ) {
                Icon(
                    if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = if (isLocked) Color(0xFFEF5350) else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { showReportDialog = true },
                modifier = Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Report, contentDescription = stringResource(R.string.report_issue_btn), tint = Color(0xFFFF7043), modifier = Modifier.size(20.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Text(
                    text = item.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.play), fontWeight = FontWeight.Bold)
                }
            }

            if (!item.logo.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.logo)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    modifier = Modifier
                        .height(160.dp)
                        .aspectRatio(0.7f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentScale = ContentScale.Crop
                )
            }
        }

        if (showUnlockDialog) {
            com.example.ui.components.PinUnlockDialog(
                onUnlock = {
                    com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                    showUnlockDialog = false
                    Toast.makeText(context, context.getString(R.string.parental_channel_unlocked), Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showUnlockDialog = false }
            )
        }
        if (showReportDialog) {
            com.example.ui.components.ReportConfirmDialog(
                title = "Yayın Sorunu: ${item.title}",
                channelName = item.title,
                message = "'${item.title}' akışında sorun bildirildi.",
                onDismiss = { showReportDialog = false }
            )
        }
    }
}

@Composable
fun MovieCard(item: M3uItem, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .width(120.dp)
            .height(180.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!item.logo.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.logo)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = rememberAsyncImagePainter(model = android.R.drawable.ic_menu_gallery)
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.VideoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = Color.Gray.copy(alpha = 0.5f)
                    )
                }
            }

            val favoriteUrls by com.example.model.FavoritesManager.favoriteUrls.collectAsState()
            val isFavorite = favoriteUrls.contains(item.url)
            val lockedChannels by com.example.model.ParentalControlManager.lockedChannels.collectAsState()
            val lockedGroups by com.example.model.ParentalControlManager.lockedGroups.collectAsState()
            val hasPin = com.example.model.ParentalControlManager.pinCode.collectAsState().value != null
            val isLocked = hasPin && (lockedChannels.contains(item.url) || lockedGroups.contains(item.group))
            var showUnlockDialog by remember { mutableStateOf(false) }
            var showReportDialog by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
            ) {
                IconButton(
                    onClick = { com.example.model.FavoritesManager.toggleFavorite(item.url) },
                    modifier = Modifier.size(28.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                ) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) Color.Red else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
                IconButton(
                    onClick = {
                        if (!hasPin) {
                            Toast.makeText(context, context.getString(R.string.parental_control_desc), Toast.LENGTH_SHORT).show()
                        } else if (isLocked) {
                            showUnlockDialog = true
                        } else {
                            com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                            Toast.makeText(context, context.getString(R.string.parental_channel_locked), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(28.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                ) {
                    Icon(
                        if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = if (isLocked) Color(0xFFEF5350) else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
                IconButton(
                    onClick = { showReportDialog = true },
                    modifier = Modifier.size(28.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                ) {
                    Icon(Icons.Default.Report, contentDescription = stringResource(R.string.report_issue_btn), tint = Color(0xFFFF7043), modifier = Modifier.size(14.dp))
                }
            }

            if (showUnlockDialog) {
                com.example.ui.components.PinUnlockDialog(
                    onUnlock = {
                        com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                        showUnlockDialog = false
                        Toast.makeText(context, context.getString(R.string.parental_channel_unlocked), Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { showUnlockDialog = false }
                )
            }
            if (showReportDialog) {
                com.example.ui.components.ReportConfirmDialog(
                    title = "Yayın Sorunu: ${item.title}",
                    channelName = item.title,
                    message = "'${item.title}' filminde sorun bildirildi.",
                    onDismiss = { showReportDialog = false }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(4.dp)
            ) {
                Text(
                    text = item.title,
                    fontSize = 10.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun ChannelCard(item: M3uItem, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (!item.logo.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.logo)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit,
                        error = rememberAsyncImagePainter(model = android.R.drawable.ic_menu_gallery)
                    )
                } else {
                    Icon(
                        Icons.Default.LiveTv,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            val favoriteUrls by com.example.model.FavoritesManager.favoriteUrls.collectAsState()
            val isFavorite = favoriteUrls.contains(item.url)
            val lockedChannels by com.example.model.ParentalControlManager.lockedChannels.collectAsState()
            val lockedGroups by com.example.model.ParentalControlManager.lockedGroups.collectAsState()
            val hasPin = com.example.model.ParentalControlManager.pinCode.collectAsState().value != null
            val isLocked = hasPin && (lockedChannels.contains(item.url) || lockedGroups.contains(item.group))
            var showUnlockDialog by remember { mutableStateOf(false) }
            var showReportDialog by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
            ) {
                IconButton(
                    onClick = { com.example.model.FavoritesManager.toggleFavorite(item.url) },
                    modifier = Modifier.size(26.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                ) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) Color.Red else Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
                IconButton(
                    onClick = {
                        if (!hasPin) {
                            Toast.makeText(context, context.getString(R.string.parental_control_desc), Toast.LENGTH_SHORT).show()
                        } else if (isLocked) {
                            showUnlockDialog = true
                        } else {
                            com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                            Toast.makeText(context, context.getString(R.string.parental_channel_locked), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(26.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                ) {
                    Icon(
                        if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = if (isLocked) Color(0xFFEF5350) else Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
                IconButton(
                    onClick = { showReportDialog = true },
                    modifier = Modifier.size(26.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                ) {
                    Icon(Icons.Default.Report, contentDescription = stringResource(R.string.report_issue_btn), tint = Color(0xFFFF7043), modifier = Modifier.size(13.dp))
                }
            }

            if (showUnlockDialog) {
                com.example.ui.components.PinUnlockDialog(
                    onUnlock = {
                        com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                        showUnlockDialog = false
                        Toast.makeText(context, context.getString(R.string.parental_channel_unlocked), Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { showUnlockDialog = false }
                )
            }
            if (showReportDialog) {
                com.example.ui.components.ReportConfirmDialog(
                    title = "Canlı Yayın Sorunu: ${item.title}",
                    channelName = item.title,
                    message = "'${item.title}' canlı yayınında kesinti bildirildi.",
                    onDismiss = { showReportDialog = false }
                )
            }
        }
    }
}

@Composable
fun SeriesCard(seriesName: String, item: M3uItem, episodeCount: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!item.logo.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.logo)
                        .crossfade(true)
                        .build(),
                    contentDescription = seriesName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = rememberAsyncImagePainter(model = android.R.drawable.ic_menu_gallery)
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Tv,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = Color.Gray.copy(alpha = 0.5f)
                    )
                }
            }

            val lockedChannels by com.example.model.ParentalControlManager.lockedChannels.collectAsState()
            val lockedGroups by com.example.model.ParentalControlManager.lockedGroups.collectAsState()
            val hasPin = com.example.model.ParentalControlManager.pinCode.collectAsState().value != null
            val isLocked = hasPin && (lockedChannels.contains(item.url) || lockedGroups.contains(item.group))
            var showUnlockDialog by remember { mutableStateOf(false) }

            IconButton(
                onClick = {
                    if (!hasPin) {
                        Toast.makeText(context, context.getString(R.string.parental_control_desc), Toast.LENGTH_SHORT).show()
                    } else if (isLocked) {
                        showUnlockDialog = true
                    } else {
                        com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                        Toast.makeText(context, context.getString(R.string.parental_channel_locked), Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            ) {
                Icon(
                    if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = if (isLocked) Color(0xFFEF5350) else Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }

            if (showUnlockDialog) {
                com.example.ui.components.PinUnlockDialog(
                    onUnlock = {
                        com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                        showUnlockDialog = false
                        Toast.makeText(context, context.getString(R.string.parental_channel_unlocked), Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { showUnlockDialog = false }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(6.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = seriesName,
                        fontSize = 11.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "$episodeCount ${stringResource(R.string.episodes_suffix)}",
                        fontSize = 9.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun ProgressCard(progress: com.example.model.db.PlaybackProgressEntity, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .width(160.dp)
            .aspectRatio(16f / 9f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!progress.logo.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(progress.logo)
                        .crossfade(true)
                        .build(),
                    contentDescription = progress.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = rememberAsyncImagePainter(model = android.R.drawable.ic_menu_gallery)
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.VideoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = Color.Gray.copy(alpha = 0.5f)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(6.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = progress.title,
                        fontSize = 11.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { if (progress.durationMs > 0) (progress.positionMs.toFloat() / progress.durationMs.toFloat()) else 0f },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.DarkGray
                    )
                }
            }
        }
    }
}

@Composable
fun MatchFixtureCard(fixture: MatchFixture, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(220.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
        border = BorderStroke(1.dp, if (fixture.isLive) Color(0xFFE50914) else Color(0xFF2E2E42))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = fixture.league,
                    fontSize = 10.sp,
                    color = Color.LightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (fixture.isLive) {
                    Surface(
                        color = Color(0xFFE50914),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "CANLI",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Text(
                        text = fixture.dateText,
                        fontSize = 10.sp,
                        color = Color(0xFFFFB300),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    if (fixture.homeLogo.isNotBlank()) {
                        AsyncImage(
                            model = fixture.homeLogo,
                            contentDescription = fixture.homeTeam,
                            modifier = Modifier.size(32.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.SportsSoccer,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = fixture.homeTeam,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                Text(
                    text = "VS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    if (fixture.awayLogo.isNotBlank()) {
                        AsyncImage(
                            model = fixture.awayLogo,
                            contentDescription = fixture.awayTeam,
                            modifier = Modifier.size(32.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.SportsSoccer,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = fixture.awayTeam,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2A2A3E), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LiveTv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Kanala Git",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ImdbCard(
    item: ImdbUpcomingItem,
    isNotified: Boolean,
    onNotifyToggle: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A28)),
        border = BorderStroke(1.dp, Color(0xFF2A2A3E))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.67f)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.posterUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = rememberAsyncImagePainter(model = android.R.drawable.ic_menu_gallery)
                )

                if (item.trailerKey != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${item.trailerKey}"))
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "YouTube uygulaması bulunamadı", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(4.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Fragman",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Surface(
                    color = if (item.type == "Film") Color(0xFF1E88E5) else Color(0xFF8E24AA),
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = item.type,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    color = Color(0xFFF5C518),
                    shape = RoundedCornerShape(topEnd = 8.dp),
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = item.imdbRating,
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.releaseDate,
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = onNotifyToggle,
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isNotified) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isNotified) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (isNotified) "Haber Ver" else "Haber Ver",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(
                                color = if (item.trailerKey != null) Color(0xFFE50914) else Color.DarkGray,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                if (item.trailerKey != null) {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${item.trailerKey}"))
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "YouTube uygulaması bulunamadı", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Fragman bulunamadı", Toast.LENGTH_SHORT).show()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (item.trailerKey != null) Icons.Default.PlayArrow else Icons.Default.PlayDisabled,
                            contentDescription = if (item.trailerKey != null) "Fragman İzle" else "Fragman Yok",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ImdbDetailDialog(
    item: ImdbUpcomingItem,
    isNotified: Boolean,
    onNotifyToggle: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A28)),
            border = BorderStroke(1.dp, Color(0xFF2E2E42))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.posterUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = rememberAsyncImagePainter(model = android.R.drawable.ic_menu_gallery)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xFF1A1A28)),
                                    startY = 100f
                                )
                            )
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = Color.White
                        )
                    }

                    Surface(
                        color = Color(0xFFF5C518),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${item.imdbRating} IMDb",
                                color = Color.Black,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = if (item.type == "Film") Color(0xFF1E88E5) else Color(0xFF8E24AA),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = item.type,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = "📅 ${item.releaseDate}",
                            color = Color(0xFFFFB300),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Türler: ${item.genres.joinToString(", ")}",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Konu / Özet",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = item.overview,
                        color = Color.Gray,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onNotifyToggle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isNotified) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isNotified) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isNotified) "Haber Verilecek (Aktif ✓)" else "Gelince Haber Ver",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
