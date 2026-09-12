package com.example.ui.screens

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

@OptIn(UnstableApi::class)
@Composable
fun SplashScreen(
    onNavigateToNext: (targetRoute: String, existingUserId: String?) -> Unit
) {
    val context = LocalContext.current
    var hasNavigated by remember { mutableStateOf(false) }

    fun navigateNext() {
        if (!hasNavigated) {
            hasNavigated = true
            // Determine user state
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                try {
                    val db = com.example.model.db.AppDatabase.getDatabase(context)
                    val existingUser = db.iptvDao().getFirstUser()
                    if (existingUser != null) {
                        // User exists -> Always go to QR code / device info screen first step-by-step
                        onNavigateToNext(com.example.ui.NavRoutes.DEVICE_INFO, existingUser.id)
                    } else {
                        // No user account yet -> go to Google Sign-In / Login
                        onNavigateToNext(com.example.ui.NavRoutes.GOOGLE_SIGN_IN, null)
                    }
                } catch (e: Exception) {
                    onNavigateToNext(com.example.ui.NavRoutes.GOOGLE_SIGN_IN, null)
                }
            }
        }
    }

    // Fast safety timeout in case video hangs or fails to finish
    LaunchedEffect(Unit) {
        delay(4000)
        navigateNext()
    }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            val rawResId = context.resources.getIdentifier("intro2", "raw", context.packageName)
            val videoUri = if (rawResId != 0) {
                Uri.parse("android.resource://${context.packageName}/$rawResId")
            } else {
                Uri.parse("asset:///intro2.mp4")
            }
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        navigateNext()
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("SplashScreen", "Video error: ${error.message}", error)
                    navigateNext()
                }
            })
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { navigateNext() },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Skip Button in top right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 28.dp, end = 20.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                .clickable { navigateNext() }
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Geç >>",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
