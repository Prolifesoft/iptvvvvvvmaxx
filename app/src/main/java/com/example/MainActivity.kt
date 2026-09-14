package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.model.PlaylistRepository
import com.example.model.db.AppDatabase
import com.example.ui.NavRoutes
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.DarkBackground
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            com.example.auth.GoogleAuthManager.currentActivity = java.lang.ref.WeakReference(this)
            com.example.model.SettingsManager.init(this)
            com.example.model.AppLanguageManager.init(this)
            com.example.model.ParentalControlManager.init(this)
            com.example.model.FavoritesManager.init(this)
            com.example.model.CategoryManager.init(this)
            com.example.model.DeviceManager.init(this)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error during initialization", e)
        }
        
        enableEdgeToEdge()
        setContent {
            val language by com.example.model.AppLanguageManager.currentLanguage.collectAsState()
            
            com.example.ui.ProvideAppLocale(language) {
                MyApplicationTheme {
                    Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    val navController = rememberNavController()
                    var currentUserId by remember { mutableStateOf("") }
                    val scope = rememberCoroutineScope()
                    
                    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                        NavHost(navController = navController, startDestination = NavRoutes.SPLASH) {
                            composable(NavRoutes.SPLASH) {
                                SplashScreen(onNavigateToNext = { targetRoute, existingUserId ->
                                    if (!existingUserId.isNullOrEmpty()) {
                                        currentUserId = existingUserId
                                    }
                                    navController.navigate(targetRoute) {
                                        popUpTo(NavRoutes.SPLASH) { inclusive = true }
                                    }
                                })
                            }
                            composable(NavRoutes.GOOGLE_SIGN_IN) {
                                GoogleSignInScreen(onSignInSuccess = { userId ->
                                    currentUserId = userId
                                    navController.navigate("${NavRoutes.PACKAGE_SELECTION}/$userId") {
                                        popUpTo(NavRoutes.GOOGLE_SIGN_IN) { inclusive = true }
                                    }
                                })
                            }
                            composable("${NavRoutes.PACKAGE_SELECTION}/{userId}") { backStackEntry ->
                                val userId = backStackEntry.arguments?.getString("userId") ?: currentUserId
                                PackageSelectionScreen(
                                    userId = userId,
                                    onPackageSelected = { packageId ->
                                        navController.navigate(NavRoutes.DEVICE_INFO) {
                                            popUpTo(NavRoutes.PACKAGE_SELECTION) { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable(NavRoutes.DEVICE_INFO) {
                                DeviceInfoScreen(
                                    userId = currentUserId,
                                    onContinue = {
                                        navController.navigate(NavRoutes.PLAYLISTS) {
                                            popUpTo(NavRoutes.DEVICE_INFO) { inclusive = true }
                                        }
                                    },
                                    onBack = {
                                        if (navController.previousBackStackEntry != null) {
                                            navController.popBackStack()
                                        } else {
                                            navController.navigate(NavRoutes.WELCOME)
                                        }
                                    },
                                    onSignOut = {
                                        scope.launch {
                                            val db = AppDatabase.getDatabase(this@MainActivity)
                                            db.iptvDao().clearUsers()
                                            currentUserId = ""
                                            navController.navigate(NavRoutes.GOOGLE_SIGN_IN) {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        }
                                    }
                                )
                            }
                            composable(NavRoutes.WELCOME) {
                                WelcomeScreen(
                                    onPlaylists = { navController.navigate(NavRoutes.PLAYLISTS) },
                                    onDeviceInfo = { navController.navigate(NavRoutes.DEVICE_INFO) }
                                )
                            }
                            composable(NavRoutes.PLAYLISTS) {
                                PlayListsScreen(
                                    userId = currentUserId,
                                    onBack = { navController.popBackStack() },
                                    onSelectPlaylist = { host, user, pass -> 
                                        scope.launch {
                                            val urlToLoad = if (user.isEmpty() && pass.isEmpty()) host else "$host/get.php?username=$user&password=$pass&type=m3u_plus&output=mpegts"
                                            PlaylistRepository.loadPlaylist(this@MainActivity, urlToLoad)
                                            if (PlaylistRepository.error.value == null) {
                                                navController.navigate(NavRoutes.DASHBOARD)
                                            } else {
                                                android.widget.Toast.makeText(this@MainActivity, PlaylistRepository.error.value, android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    onDeviceInfo = {
                                        navController.navigate(NavRoutes.DEVICE_INFO) {
                                            popUpTo(NavRoutes.PLAYLISTS) { inclusive = true }
                                        }
                                    },
                                    onSignOut = {
                                        scope.launch {
                                            val db = AppDatabase.getDatabase(this@MainActivity)
                                            db.iptvDao().clearUsers()
                                            currentUserId = ""
                                            navController.navigate(NavRoutes.GOOGLE_SIGN_IN) {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        }
                                    }
                                )
                            }
                            composable(NavRoutes.DASHBOARD) {
                                DashboardScreen(
                                    onPlayStream = { item ->
                                        com.example.model.PlayerRepository.currentlyPlayingItem = item
                                        navController.navigate(NavRoutes.PLAYER)
                                    },
                                    onNavigateToAuth = {
                                        scope.launch {
                                            val db = AppDatabase.getDatabase(this@MainActivity)
                                            db.iptvDao().clearUsers()
                                            currentUserId = ""
                                            navController.navigate(NavRoutes.GOOGLE_SIGN_IN) {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        }
                                    },
                                    onNavigateToPlaylists = {
                                        navController.navigate(NavRoutes.PLAYLISTS)
                                    },
                                    onNavigateToDeviceInfo = {
                                        navController.navigate(NavRoutes.DEVICE_INFO)
                                    }
                                )
                            }
                            composable(NavRoutes.PLAYER) {
                                PlayerScreen()
                            }
                        }
                        
                        PlaylistLoadingDialog()
                    }
                }
            }
        }
    }
}

    override fun onResume() {
        super.onResume()
        com.example.auth.GoogleAuthManager.currentActivity = java.lang.ref.WeakReference(this)
    }
}
