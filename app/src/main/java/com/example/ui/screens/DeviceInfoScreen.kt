package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.model.DeviceManager
import com.example.model.OdooIntegrationManager
import com.example.model.db.AppDatabase
import com.example.ui.components.ProUpgradeDialog
import com.example.ui.theme.RedPrimary
import com.example.util.QrCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(
    userId: String,
    onContinue: () -> Unit,
    onBack: (() -> Unit)? = null,
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val daysRemaining by DeviceManager.trialDaysLeft.collectAsState()
    val isPro by DeviceManager.isProState.collectAsState()
    val isProOrInTrial by DeviceManager.isProOrInTrialState.collectAsState()
    val isSyncing by OdooIntegrationManager.isSyncing.collectAsState()
    val customerName by DeviceManager.customerNameState.collectAsState()

    var deviceId by remember { mutableStateOf(DeviceManager.getDeviceId()) }
    var deviceKey by remember { mutableStateOf(DeviceManager.getDeviceKey()) }
    val deviceModel = remember { DeviceManager.getDeviceModel() }
    val osVersion = remember { DeviceManager.getOsVersion() }
    var webPortalUrl by remember { mutableStateOf(DeviceManager.getWebPortalUrl()) }

    val qrBitmap = remember(webPortalUrl) {
        QrCodeGenerator.generateQrImageBitmap(webPortalUrl, 380)
    }

    var showProDialog by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var currentUser by remember { mutableStateOf<com.example.model.db.UserEntity?>(null) }
    var autoDetectedWelcomeName by remember { mutableStateOf<String?>(null) }
    var isAutoRedirecting by remember { mutableStateOf(false) }
    var isOdooRegistered by remember { mutableStateOf(DeviceManager.isOdooCustomerSynced()) }

    // Automatic registration and polling effect: ensures Odoo has the device registered and checks for remote playlists
    LaunchedEffect(userId) {
        val db = AppDatabase.getDatabase(context)
        val user = withContext(Dispatchers.IO) {
            val fetched = if (userId.isNotBlank()) db.iptvDao().getUser(userId) else db.iptvDao().getFirstUser()
            if (fetched != null && (fetched.id == "demo@maxxbilisim.com" || fetched.name == "Demo User" || fetched.email == "demo@maxxbilisim.com")) {
                db.iptvDao().clearUsers()
                null
            } else {
                fetched
            }
        }
        if (user == null) {
            onSignOut()
            return@LaunchedEffect
        }
        currentUser = user
        val targetUserId = user.id
        val userEmail = user.email ?: ""
        val userName = user.name ?: "Kullanıcı"

        // Proactively register device in Odoo so the web portal finds the device immediately
        withContext(Dispatchers.IO) {
            val regSuccess = OdooIntegrationManager.registerCustomerAndTrial(
                context = context,
                userId = targetUserId,
                userName = userName,
                userEmail = userEmail
            )
            if (regSuccess) {
                isOdooRegistered = true
            }
        }

        // Refresh dynamic credentials in case registration updated/resolved conflict
        deviceId = DeviceManager.getDeviceId()
        deviceKey = DeviceManager.getDeviceKey()
        webPortalUrl = DeviceManager.getWebPortalUrl()

        // Keep polling Odoo in background while user is on this screen
        while (true) {
            if (!isAutoRedirecting) {
                try {
                    val syncedCount = OdooIntegrationManager.syncPlaylistsFromOdoo(context, targetUserId)
                    if (syncedCount > 0) {
                        val custName = DeviceManager.getCustomerName() ?: user?.name ?: "Değerli Müşterimiz"
                        autoDetectedWelcomeName = custName
                        isAutoRedirecting = true
                        delay(1800)
                        onContinue()
                        break
                    }
                } catch (e: Exception) {
                    // Continue polling next cycle
                }
            }
            delay(5000)
        }
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label ${context.getString(R.string.copy_copied)}", Toast.LENGTH_SHORT).show()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            // Compact Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.close_desc),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = stringResource(R.string.device_info_title),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Profile Button
                    FilledTonalButton(
                        onClick = { showProfileSheet = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF263238),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color(0xFF42A5F5), modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.profile_desc), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Sign Out Button
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val db = AppDatabase.getDatabase(context)
                                db.iptvDao().clearUsers()
                                Toast.makeText(context, context.getString(R.string.sign_out_account), Toast.LENGTH_SHORT).show()
                                onSignOut()
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFA726)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFA726).copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, tint = Color(0xFFFFA726), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Çıkış", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main 2-Column Split: LEFT = Device Info & Trial Status, RIGHT = QR Code & Web Portal
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                // ==================== LEFT COLUMN: Device Info & Trial ====================
                Column(
                    modifier = Modifier
                        .weight(1.05f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 0. User Profile & Account Card
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { showProfileSheet = true },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2838)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(Color(0xFF1565C0), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentUser?.name ?: customerName ?: autoDetectedWelcomeName ?: "Kullanıcı",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = currentUser?.email ?: (if (userId.contains("@")) userId else "ncem0332006@gmail.com"),
                                        color = Color(0xFF90CAF9),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalButton(
                                    onClick = { showProfileSheet = true },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color(0xFF263238),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                                    modifier = Modifier.height(24.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Profil", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val db = AppDatabase.getDatabase(context)
                                            db.iptvDao().clearUsers()
                                            Toast.makeText(context, context.getString(R.string.sign_out_account), Toast.LENGTH_SHORT).show()
                                            onSignOut()
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Logout,
                                        contentDescription = stringResource(R.string.sign_out_account),
                                        tint = Color(0xFFFFA726),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                    // 1. Trial / License Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPro) Color(0xFF1B3A24) else if (isProOrInTrial) Color(0xFF1E2838) else Color(0xFF3E2723)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(
                                        if (isPro) Color(0xFF2E7D32) else if (isProOrInTrial) RedPrimary else Color(0xFFD32F2F),
                                        RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isPro) Icons.Default.Verified else if (isProOrInTrial) Icons.Default.Timer else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (isPro) "PRO Sürüm Aktif" else if (isProOrInTrial) stringResource(R.string.trial_package_active) else stringResource(R.string.trial_expired_title),
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                    if (!isPro && isProOrInTrial) {
                                        Text(
                                            text = stringResource(R.string.trial_remaining_days, daysRemaining),
                                            color = Color(0xFF64B5F6),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Text(
                                    text = if (isPro) "Sınırsız cihaz ve liste aktif." else if (isProOrInTrial) "İlk 15 gün tüm özellikler ve sınırsız liste açık." else "15 günlük deneme bitti. Sadece 1 liste etkin.",
                                    color = Color.LightGray,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // 2. Device Identity Identifiers (ID & Key)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            // Device ID Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E232A), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.device_id_label), color = Color.Gray, fontSize = 9.sp)
                                    Text(
                                        text = deviceId,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { copyToClipboard("Cihaz ID", deviceId) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Kopyala", tint = RedPrimary, modifier = Modifier.size(15.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Device Key / PIN Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E232A), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.device_key_label), color = Color.Gray, fontSize = 9.sp)
                                    Text(
                                        text = deviceKey,
                                        color = Color(0xFFFFCA28),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                }
                                IconButton(
                                    onClick = { copyToClipboard("Cihaz Anahtarı", deviceKey) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Kopyala", tint = Color(0xFFFFCA28), modifier = Modifier.size(15.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Device Hardware & OS Details
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.device_model_label), color = Color.Gray, fontSize = 9.sp)
                                    Text(deviceModel, color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.device_os_label), color = Color.Gray, fontSize = 9.sp)
                                    Text(osVersion, color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }

                    // 3. Quick Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (!isPro) {
                            Button(
                                onClick = { showProDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.btn_upgrade_package), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }

                // ==================== RIGHT COLUMN: QR Code & Web Management ====================
                Card(
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Title
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.QrCode2, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.qr_code_title),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }

                        // QR Code Image in Center (scales nicely in landscape)
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .aspectRatio(1f)
                                .padding(vertical = 2.dp)
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .border(1.5.dp, Color(0xFF555555), RoundedCornerShape(8.dp))
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap,
                                    contentDescription = "Web Yönetim QR Kodu",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                CircularProgressIndicator(color = RedPrimary, modifier = Modifier.size(24.dp))
                            }
                        }

                        // Web Portal URL Link + Copy & Browser Buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E232A), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.web_portal_url_label), color = Color.Gray, fontSize = 8.sp)
                                Text(
                                    text = webPortalUrl,
                                    color = Color(0xFF64B5F6),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { copyToClipboard("Web Portalı", webPortalUrl) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Kopyala", tint = Color(0xFF64B5F6), modifier = Modifier.size(13.dp))
                            }
                            IconButton(
                                onClick = {
                                    try {
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webPortalUrl))
                                        context.startActivity(browserIntent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Tarayıcı açılamadı: $webPortalUrl", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = "Aç", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProDialog) {
        ProUpgradeDialog(onDismiss = { showProDialog = false })
    }

    // Auto-detection loading & welcome dialog
    if (isAutoRedirecting || autoDetectedWelcomeName != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { /* non-cancelable */ },
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E232A),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3846)),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color(0xFF2E7D32).copy(alpha = 0.2f), RoundedCornerShape(32.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF81C784),
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.odoo_customer_welcome, autoDetectedWelcomeName ?: customerName ?: "Değerli Müşterimiz"),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.odoo_auto_loading_playlists),
                        color = Color(0xFF90CAF9),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    CircularProgressIndicator(
                        color = RedPrimary,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.odoo_auto_redirecting),
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
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
