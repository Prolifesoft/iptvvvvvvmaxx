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
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    
    val effectiveUserId = remember(userId) {
        if (userId.isNotBlank()) userId else (DeviceManager.getCurrentUserId() ?: DeviceManager.getDeviceId())
    }
    val userPlaylists by if (effectiveUserId.isNotBlank()) {
        db.iptvDao().getPlaylistsForUser(effectiveUserId).collectAsState(initial = emptyList())
    } else {
        db.iptvDao().getAllPlaylists().collectAsState(initial = emptyList())
    }
    val allPlaylists by db.iptvDao().getAllPlaylists().collectAsState(initial = emptyList())
    val playlists = if (userPlaylists.isNotEmpty()) userPlaylists else allPlaylists

    val daysRemaining by DeviceManager.trialDaysLeft.collectAsState()
    val isPro by DeviceManager.isProState.collectAsState()
    val isProOrInTrial by DeviceManager.isProOrInTrialState.collectAsState()
    val isSyncing by OdooIntegrationManager.isSyncing.collectAsState()
    val lastSyncMsg by OdooIntegrationManager.lastSyncMessage.collectAsState()
    val customerName by DeviceManager.customerNameState.collectAsState()

    var showProDialog by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var showEditCredentialsDialog by remember { mutableStateOf(false) }

    // Background periodic Odoo sync to keep lists in sync with Odoo deletions/additions
    LaunchedEffect(effectiveUserId) {
        // Run an immediate sync on enter
        try {
            OdooIntegrationManager.syncPlaylistsFromOdoo(context, effectiveUserId)
        } catch (e: Exception) {
            // Ignore
        }
        while (true) {
            delay(15000)
            try {
                OdooIntegrationManager.syncPlaylistsFromOdoo(context, effectiveUserId)
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
                    // Manual Odoo Sync Button
                    IconButton(
                        onClick = {
                            scope.launch {
                                val count = OdooIntegrationManager.syncPlaylistsFromOdoo(context, effectiveUserId)
                                if (count > 0) {
                                    Toast.makeText(context, "$count adet çalma listesi güncellendi", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, OdooIntegrationManager.lastSyncMessage.value.orEmpty(), Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = "Odoo Senkronize Et", tint = Color.White)
                        }
                    }

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
                // Empty State: Direct user to web portal / device info & provide sync and bind actions
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Icon(
                            Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = RedPrimary,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.odoo_no_playlists_assigned),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.odoo_waiting_playlists_desc),
                            color = Color.Gray,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        // Current Device Credentials Badge
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth(0.95f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Cihaz ID: ${DeviceManager.getDeviceId()} | PIN: ${DeviceManager.getDeviceKey()}",
                                    color = Color(0xFFFFCA28),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                                val syncMsgText = lastSyncMsg.orEmpty()
                                if (syncMsgText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = syncMsgText,
                                        color = if (syncMsgText.contains("başarıyla", ignoreCase = true)) Color(0xFF81C784) else Color.LightGray,
                                        fontSize = 10.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(0.95f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val count = OdooIntegrationManager.syncPlaylistsFromOdoo(context, effectiveUserId)
                                        if (count > 0) {
                                            Toast.makeText(context, "$count adet çalma listesi başarıyla senkronize edildi!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, OdooIntegrationManager.lastSyncMessage.value.orEmpty(), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                enabled = !isSyncing,
                                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text("Odoo'dan Listeleri Şimdi Çek", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { showEditCredentialsDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Cihazı Bağla", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = onDeviceInfo,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Cihaz & Karekod", fontSize = 11.sp)
                                }
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

    if (showEditCredentialsDialog) {
        var inputId by remember { mutableStateOf(DeviceManager.getDeviceId()) }
        var inputPin by remember { mutableStateOf(DeviceManager.getDeviceKey()) }
        var isConnecting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isConnecting) showEditCredentialsDialog = false },
            title = {
                Text(
                    text = "Odoo Cihazını Bağla",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Odoo panelinizde, paketinizde veya siparişinizde kayıtlı olan Cihaz ID ve PIN bilgilerini girerek çalma listelerinizi bu cihaza bağlayabilirsiniz.",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = inputId,
                        onValueChange = { inputId = it.uppercase(java.util.Locale.ROOT).trim() },
                        label = { Text("Cihaz ID (Örn: MAX-XXXX-XXXX)", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF42A5F5),
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = inputPin,
                        onValueChange = { inputPin = it.trim() },
                        label = { Text("Cihaz PIN / Anahtarı (Örn: 123456)", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF42A5F5),
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanId = inputId.trim()
                        val cleanPin = inputPin.trim()
                        if (cleanId.isBlank() || cleanPin.isBlank()) {
                            Toast.makeText(context, "Lütfen Cihaz ID ve PIN giriniz", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isConnecting = true
                        DeviceManager.setDeviceCredentials(cleanId, cleanPin)

                        scope.launch {
                            val count = OdooIntegrationManager.syncPlaylistsFromOdoo(context, effectiveUserId)
                            isConnecting = false
                            showEditCredentialsDialog = false
                            if (count > 0) {
                                Toast.makeText(context, "$count adet çalma listesi başarıyla bağlandı ve yüklendi!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, OdooIntegrationManager.lastSyncMessage.value.orEmpty(), Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isConnecting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Bağla ve Listeleri Çek", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditCredentialsDialog = false },
                    enabled = !isConnecting
                ) {
                    Text("İptal", color = Color.LightGray, fontSize = 12.sp)
                }
            },
            containerColor = Color(0xFF1E232A)
        )
    }
}

