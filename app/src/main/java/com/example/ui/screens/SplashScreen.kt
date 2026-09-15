package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.RedPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SplashScreen(
    onNavigateToNext: (targetRoute: String, existingUserId: String?) -> Unit
) {
    val context = LocalContext.current
    var hasNavigated by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Smooth pulse animation for logo
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    fun navigateNext() {
        if (!hasNavigated) {
            hasNavigated = true
            scope.launch {
                try {
                    val existingUser = withContext(Dispatchers.IO) {
                        val db = com.example.model.db.AppDatabase.getDatabase(context)
                        val user = db.iptvDao().getFirstUser()
                        if (user != null && user.email.isNullOrBlank()) {
                            db.iptvDao().clearUsers()
                            null
                        } else {
                            user
                        }
                    }
                    if (existingUser != null) {
                        onNavigateToNext(com.example.ui.NavRoutes.DEVICE_INFO, existingUser.id)
                    } else {
                        onNavigateToNext(com.example.ui.NavRoutes.GOOGLE_SIGN_IN, null)
                    }
                } catch (e: Exception) {
                    onNavigateToNext(com.example.ui.NavRoutes.GOOGLE_SIGN_IN, null)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                com.example.model.DeviceManager.init(context)
            } catch (e: Exception) {
                // Ignore
            }
        }
        delay(900)
        navigateNext()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF020617)
                    )
                )
            )
            .clickable { navigateNext() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(pulseScale)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(RedPrimary, Color(0xFFB71C1C))
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "MAXX IPTV",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Odoo 19 Powered Player",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            CircularProgressIndicator(
                color = RedPrimary,
                modifier = Modifier.size(26.dp),
                strokeWidth = 2.5.dp
            )
        }
    }
}
