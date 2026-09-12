package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.ui.unit.sp
import com.example.model.DeviceManager
import com.example.model.OdooIntegrationManager
import com.example.model.db.AppDatabase
import com.example.ui.components.ProUpgradeDialog
import com.example.ui.theme.RedPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayListsScreen(
    userId: String,
    onBack: () -> Unit,
    onSelectPlaylist: (String, String, String) -> Unit,
    onDeviceInfo: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val playlists by db.iptvDao().getPlaylistsForUser(userId).collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    val daysRemaining by DeviceManager.trialDaysLeft.collectAsState()
    val isPro by DeviceManager.isProState.collectAsState()
    val isProOrInTrial by DeviceManager.isProOrInTrialState.collectAsState()
    val isSyncing by OdooIntegrationManager.isSyncing.collectAsState()
    val customerName by DeviceManager.customerNameState.collectAsState()

    var showProDialog by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }

    // Background periodic Odoo sync to keep lists in sync with Odoo deletions/additions
    LaunchedEffect(userId) {
        while (true) {
            delay(15000)
            try {
                OdooIntegrationManager.syncPlaylistsFromOdoo(context, userId)
            } catch (e: Exception) {
                // Ignore network glitches during background sync
            }
        }
    }

    val filteredPlaylists = playlists.filter { it.name.contains(searchQuery, ignoreCase = true) || it.username.contains(searchQuery, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.playlists), fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.close_desc), tint = Color.White)
                    }
                },
                actions = {
                    // Device Info & QR Code
                    IconButton(onClick = onDeviceInfo) {
                        Icon(Icons.Default.QrCode2, contentDescription = "Cihaz ve QR Kod", tint = Color.White)
                    }

                    // Profile & Settings
                    IconButton(onClick = { showProfileSheet = true }) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Profil ve Hesap", tint = Color(0xFF42A5F5))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Welcome Odoo Customer Greeting (if present)
            if (!customerName.isNullOrBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B3A24).copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color(0xFF81C784), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.odoo_customer_welcome, customerName!!),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // License & Trial Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { onDeviceInfo() },
                colors = CardDefaults.cardColors(
                    containerColor = if (isPro) Color(0xFF1B3A24) else if (isProOrInTrial) Color(0xFF1E2838) else Color(0xFF3E2723)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isPro) Icons.Default.Verified else if (isProOrInTrial) Icons.Default.Timer else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isPro) Color(0xFF81C784) else if (isProOrInTrial) Color(0xFF64B5F6) else Color(0xFFFFB74D),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isPro) "PRO Paket: Sınırsız Liste & Cihaz" else if (isProOrInTrial) "15 Günlük Deneme Paketi Aktif ($daysRemaining gün kaldı)" else "15 Günlük Deneme Sona Erdi (1 Cihaz / 1 Liste)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = if (isPro) "Odoo 19 Entegrasyonu Tam Yetkili" else if (isProOrInTrial) "İlk 15 gün tüm özellikler açıktır." else "Yeni liste eklemek için paketinizi yükseltin.",
                            color = Color.LightGray,
                            fontSize = 10.sp
                        )
                    }
                    if (!isProOrInTrial) {
                        Button(
                            onClick = { showProDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(stringResource(R.string.btn_upgrade_package), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (playlists.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_placeholder), color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = RedPrimary) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RedPrimary,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
            }
            
            if (filteredPlaylists.isEmpty() && playlists.isEmpty()) {
                // Empty State: Direct user to web portal / device info
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Default.QrCode2,
                            contentDescription = null,
                            tint = RedPrimary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.odoo_no_playlists_assigned),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.odoo_waiting_playlists_desc),
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onDeviceInfo,
                                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                            ) {
                                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Karekod & Cihaz ile Yönet")
                            }
                        }
                    }
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredPlaylists.size) { index ->
                        val playlist = filteredPlaylists[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPlaylist(playlist.hostUrl, playlist.username, playlist.password) }
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(RedPrimary, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(if (playlist.username.isEmpty()) Icons.Default.Link else Icons.Default.Person, contentDescription = null, tint = Color.White)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(playlist.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(playlist.hostUrl, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                            
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    db.iptvDao().deletePlaylist(playlist)
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Color.Gray)
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    if (showProDialog) {
        ProUpgradeDialog(onDismiss = { showProDialog = false })
    }

    if (showProfileSheet) {
        ModalBottomSheet(
            onDismissRequest = { showProfileSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF1E232A),
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            ProfileSettingsSheet(
                onNavigateToAuth = {
                    showProfileSheet = false
                    onSignOut()
                }
            )
        }
    }
}

