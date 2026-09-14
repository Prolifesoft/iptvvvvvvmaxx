package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.parser.M3uItem
import com.example.ui.theme.RedPrimary
import kotlinx.coroutines.launch

@Composable
fun SeriesDetailSheet(items: List<M3uItem>, onPlayStream: (M3uItem) -> Unit) {
    val seriesName = items.firstOrNull()?.seriesName ?: items.firstOrNull()?.title ?: stringResource(R.string.series_fallback)
    val groupedBySeason = remember(items) { items.groupBy { it.season ?: 1 }.toSortedMap() }
    val seasons = groupedBySeason.keys.toList()
    var selectedSeasonIndex by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = seriesName,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(16.dp)
        )

        if (seasons.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = selectedSeasonIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color.White,
                edgePadding = 16.dp,
                divider = { HorizontalDivider(color = Color.DarkGray) }
            ) {
                seasons.forEachIndexed { index, season ->
                    Tab(
                        selected = selectedSeasonIndex == index,
                        onClick = { selectedSeasonIndex = index },
                        text = { Text("${stringResource(R.string.season_prefix)} $season", fontWeight = FontWeight.Bold) }
                    )
                }
            }

            val selectedSeason = seasons.getOrNull(selectedSeasonIndex)
            val episodes = groupedBySeason[selectedSeason] ?: emptyList()

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(episodes.sortedBy { it.episode ?: 0 }) { episode ->
                    val epTitle = "${stringResource(R.string.episode_prefix)} ${episode.episode ?: "?"}: ${episode.title}"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayStream(episode) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Tv, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = epTitle,
                            color = Color.White,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        var showEpReportDialog by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showEpReportDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Report, contentDescription = stringResource(R.string.report_issue_btn), tint = Color(0xFFFF7043), modifier = Modifier.size(18.dp))
                        }
                        if (showEpReportDialog) {
                            com.example.ui.components.ReportConfirmDialog(
                                title = "Dizi Sorunu: $seriesName ($epTitle)",
                                channelName = "$seriesName ($epTitle)",
                                message = "'$seriesName - $epTitle' bölümünde oynatma sorunu bildirildi.",
                                onDismiss = { showEpReportDialog = false }
                            )
                        }
                    }
                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f), modifier = Modifier.padding(start = 56.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsSheetContent(onClose: () -> Unit, onOpenSupport: () -> Unit = {}) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { com.example.model.db.AppDatabase.getDatabase(context) }

    var showFaqDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showRateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_desc), tint = Color.Gray)
            }
        }
        HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))

        var showParentalDialog by remember { mutableStateOf(false) }
        var showCategoryDialog by remember { mutableStateOf(false) }
        val orientationMode by com.example.model.SettingsManager.orientationMode.collectAsState()

        Text("EKRAN VE GÖRÜNÜM", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
        SettingsItem(icon = Icons.Default.Category, title = "Kategori Yönetimi (Gizle / Göster)", iconTint = Color(0xFF00ACC1), onClick = { showCategoryDialog = true })
        if (showCategoryDialog) {
            com.example.ui.components.CategoryManagementDialog(onDismiss = { showCategoryDialog = false })
        }

        // Screen orientation selector
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(Color(0xFF161B22), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ScreenRotation, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text("Ekran Yönü (Yatay / Dikey)", color = Color.White, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val options = listOf(0 to "Otomatik", 1 to "Dikey", 2 to "Yatay")
                options.forEach { (mode, label) ->
                    val isSelected = orientationMode == mode
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) RedPrimary.copy(alpha = 0.2f) else Color(0xFF1E232A),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) RedPrimary else Color(0xFF333A44)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { com.example.model.SettingsManager.setOrientationMode(mode) }
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        Text(stringResource(R.string.settings_parental), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
        SettingsItem(icon = Icons.Default.Lock, title = stringResource(R.string.parental_control_title), iconTint = Color(0xFFE53935), onClick = { showParentalDialog = true })
        if (showParentalDialog) {
            com.example.ui.components.ParentalControlDialog(onDismiss = { showParentalDialog = false })
        }

        Text("SÜRÜM VE GÜNCELLEME", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
        SettingsItem(
            icon = Icons.Default.SystemUpdate,
            title = "Güncellemeleri Kontrol Et",
            iconTint = Color(0xFF42A5F5),
            onClick = {
                coroutineScope.launch {
                    com.example.model.UpdateManager.checkForUpdates(context, manual = true)
                    onClose()
                }
            }
        )

        Text(stringResource(R.string.settings_data), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
        SettingsItem(icon = Icons.Default.Delete, title = stringResource(R.string.settings_erase_recent), iconTint = Color(0xFF42A5F5), onClick = {
            coroutineScope.launch {
                db.iptvDao().clearRecentProgress()
                Toast.makeText(context, context.getString(R.string.toast_recent_cleared), Toast.LENGTH_SHORT).show()
                onClose()
            }
        })
        SettingsItem(icon = Icons.Default.Update, title = stringResource(R.string.settings_update_movies), iconTint = Color(0xFF42A5F5), onClick = {
            coroutineScope.launch {
                Toast.makeText(context, context.getString(R.string.toast_movies_updated), Toast.LENGTH_SHORT).show()
                onClose()
            }
        })
        SettingsItem(icon = Icons.Default.Update, title = stringResource(R.string.settings_update_series), iconTint = Color(0xFFAB47BC), onClick = {
            coroutineScope.launch {
                Toast.makeText(context, context.getString(R.string.toast_series_updated), Toast.LENGTH_SHORT).show()
                onClose()
            }
        })
        SettingsItem(icon = Icons.Default.Update, title = stringResource(R.string.settings_update_live), iconTint = Color(0xFF66BB6A), onClick = {
            coroutineScope.launch {
                Toast.makeText(context, context.getString(R.string.toast_live_updated), Toast.LENGTH_SHORT).show()
                onClose()
            }
        })

        Text(stringResource(R.string.settings_share), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
        SettingsItem(icon = Icons.Default.RateReview, title = stringResource(R.string.settings_review), iconTint = Color(0xFFFFA726), onClick = { showRateDialog = true })
        SettingsItem(icon = Icons.Default.Star, title = stringResource(R.string.settings_rate_us), iconTint = Color(0xFFEF5350), onClick = { showRateDialog = true })
        SettingsItem(icon = Icons.Default.Share, title = stringResource(R.string.settings_share_family), iconTint = Color(0xFFBDBDBD), onClick = {
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text))
                }
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.settings_share_family)))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        })

        Text(stringResource(R.string.settings_help), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
        SettingsItem(icon = Icons.Default.Help, title = stringResource(R.string.settings_faq), iconTint = Color(0xFFAB47BC), onClick = { showFaqDialog = true })

        Text(stringResource(R.string.settings_contact), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
        SettingsItem(icon = Icons.Default.Report, title = stringResource(R.string.settings_report), iconTint = Color(0xFFFF7043), onClick = { onClose(); onOpenSupport() })
        SettingsItem(icon = Icons.Default.Add, title = stringResource(R.string.settings_request), iconTint = Color(0xFFEF5350), onClick = { onClose(); onOpenSupport() })
        SettingsItem(icon = Icons.Default.Build, title = stringResource(R.string.settings_support), iconTint = Color(0xFF42A5F5), onClick = { onClose(); onOpenSupport() })
    }

    if (showFaqDialog) {
        AlertDialog(
            onDismissRequest = { showFaqDialog = false },
            title = { Text(stringResource(R.string.faq_title), fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.faq_q1), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.faq_a1), color = Color.LightGray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.faq_q2), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.faq_a2), color = Color.LightGray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.faq_q3), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.faq_a3), color = Color.LightGray, fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(onClick = { showFaqDialog = false }) {
                    Text(stringResource(R.string.close_desc))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showRateDialog) {
        var selectedStars by remember { mutableStateOf(5) }
        AlertDialog(
            onDismissRequest = { showRateDialog = false },
            title = { Text(stringResource(R.string.rate_title), fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.rate_desc), color = Color.LightGray, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.Center) {
                        (1..5).forEach { star ->
                            Icon(
                                imageVector = if (star <= selectedStars) Icons.Default.Star else Icons.Default.StarOutline,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { selectedStars = star }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showRateDialog = false
                    Toast.makeText(context, context.getString(R.string.rate_thanks), Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(R.string.rate_submit))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRateDialog = false }) {
                    Text(stringResource(R.string.close_desc), color = Color.Gray)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showReportDialog) {
        var reportText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text(stringResource(R.string.report_title), fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column {
                    Text(stringResource(R.string.report_desc), color = Color.LightGray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = reportText,
                        onValueChange = { reportText = it },
                        placeholder = { Text(stringResource(R.string.report_placeholder), color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReportDialog = false
                        Toast.makeText(context, context.getString(R.string.report_sent), Toast.LENGTH_SHORT).show()
                    },
                    enabled = reportText.isNotBlank()
                ) {
                    Text(stringResource(R.string.report_send))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text(stringResource(R.string.close_desc), color = Color.Gray)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, iconTint: Color, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(iconTint.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, color = Color.White, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsSheet(
    onClose: () -> Unit = {},
    onOpenSupport: () -> Unit = {},
    onNavigateToAuth: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentLang by com.example.model.AppLanguageManager.currentLanguage.collectAsState()
    val db = remember { com.example.model.db.AppDatabase.getDatabase(context) }
    val coroutineScope = rememberCoroutineScope()
    var currentUser by remember { mutableStateOf<com.example.model.db.UserEntity?>(null) }
    var showSupportInline by remember { mutableStateOf(false) }

    var showFaqDialog by remember { mutableStateOf(false) }
    var showRateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        currentUser = db.iptvDao().getFirstUser()
    }

    if (showSupportInline) {
        SupportTicketsSheet(onClose = { showSupportInline = false })
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        val isLandscape = maxWidth > 560.dp

        if (isLandscape) {
            // Horizontal / Landscape Layout: 2 Columns side-by-side with full scroll
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Left Column: Profile Card, Language, Screen Orientation & Category
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.personalization_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_desc), tint = Color.Gray)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    // Connected Google Account Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF263238), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = Color(0xFF4285F4),
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.google_account_connected), fontSize = 11.sp, color = Color(0xFF90CAF9))
                            Text(
                                currentUser?.email ?: currentUser?.name ?: "ncem0332006@gmail.com",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.language_selection),
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (currentLang == "tr") RedPrimary.copy(alpha = 0.2f) else Color(0xFF1E232A),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (currentLang == "tr") RedPrimary else Color(0xFF333A44)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { com.example.model.AppLanguageManager.setLanguage(context, "tr") }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = currentLang == "tr",
                                    onClick = { com.example.model.AppLanguageManager.setLanguage(context, "tr") }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Türkçe", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (currentLang == "en") RedPrimary.copy(alpha = 0.2f) else Color(0xFF1E232A),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (currentLang == "en") RedPrimary else Color(0xFF333A44)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { com.example.model.AppLanguageManager.setLanguage(context, "en") }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = currentLang == "en",
                                    onClick = { com.example.model.AppLanguageManager.setLanguage(context, "en") }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("English", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "Ekran Yönü ve Kategori Yönetimi",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    var showCategoryDialogProfile by remember { mutableStateOf(false) }
                    val orientationModeProfile by com.example.model.SettingsManager.orientationMode.collectAsState()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E232A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333A44)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showCategoryDialogProfile = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Category, contentDescription = null, tint = Color(0xFF00ACC1), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Kategoriler", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    if (showCategoryDialogProfile) {
                        com.example.ui.components.CategoryManagementDialog(onDismiss = { showCategoryDialogProfile = false })
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val options = listOf(0 to "Oto", 1 to "Dikey", 2 to "Yatay")
                        options.forEach { (mode, label) ->
                            val isSelected = orientationModeProfile == mode
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) RedPrimary.copy(alpha = 0.2f) else Color(0xFF1E232A),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) RedPrimary else Color(0xFF333A44)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { com.example.model.SettingsManager.setOrientationMode(mode) }
                            ) {
                                Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                    Text(label, color = if (isSelected) Color.White else Color.Gray, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }

                // Right Column: Version Update, Share, FAQ, Support & Account Management Actions
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "SÜRÜM VE MENÜLER",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Version Update
                    SettingsItem(
                        icon = Icons.Default.SystemUpdate,
                        title = "Güncellemeleri Kontrol Et",
                        iconTint = Color(0xFF42A5F5),
                        onClick = {
                            coroutineScope.launch {
                                com.example.model.UpdateManager.checkForUpdates(context, manual = true)
                                onClose()
                            }
                        }
                    )

                    // Review & Rate & Share
                    SettingsItem(
                        icon = Icons.Default.RateReview,
                        title = stringResource(R.string.settings_review),
                        iconTint = Color(0xFFFFA726),
                        onClick = { showRateDialog = true }
                    )
                    SettingsItem(
                        icon = Icons.Default.Star,
                        title = stringResource(R.string.settings_rate_us),
                        iconTint = Color(0xFFEF5350),
                        onClick = { showRateDialog = true }
                    )
                    SettingsItem(
                        icon = Icons.Default.Share,
                        title = stringResource(R.string.settings_share_family),
                        iconTint = Color(0xFFBDBDBD),
                        onClick = {
                            try {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text))
                                }
                                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.settings_share_family)))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    )

                    // FAQ
                    SettingsItem(
                        icon = Icons.Default.Help,
                        title = stringResource(R.string.settings_faq),
                        iconTint = Color(0xFFAB47BC),
                        onClick = { showFaqDialog = true }
                    )

                    // Support / Report / Request
                    SettingsItem(
                        icon = Icons.Default.Report,
                        title = stringResource(R.string.settings_report),
                        iconTint = Color(0xFFFF7043),
                        onClick = { onClose(); onOpenSupport() }
                    )
                    SettingsItem(
                        icon = Icons.Default.Add,
                        title = stringResource(R.string.settings_request),
                        iconTint = Color(0xFFEF5350),
                        onClick = { onClose(); onOpenSupport() }
                    )
                    SettingsItem(
                        icon = Icons.Default.Build,
                        title = stringResource(R.string.settings_support),
                        iconTint = Color(0xFF42A5F5),
                        onClick = { onClose(); onOpenSupport() }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.account_management),
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Switch Account
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E232A), RoundedCornerShape(8.dp))
                            .clickable {
                                coroutineScope.launch {
                                    db.iptvDao().clearUsers()
                                    onNavigateToAuth()
                                }
                            }
                            .padding(10.dp)
                    ) {
                        Icon(Icons.Default.SwitchAccount, contentDescription = null, tint = Color(0xFF42A5F5), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.switch_account), color = Color.White, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Sign Out
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E232A), RoundedCornerShape(8.dp))
                            .clickable {
                                coroutineScope.launch {
                                    db.iptvDao().clearUsers()
                                    Toast.makeText(context, context.getString(R.string.sign_out_account), Toast.LENGTH_SHORT).show()
                                    onNavigateToAuth()
                                }
                            }
                            .padding(10.dp)
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, tint = Color(0xFFFFA726), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.sign_out_account), color = Color.White, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Remove Account
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2E1C1C), RoundedCornerShape(8.dp))
                            .clickable {
                                coroutineScope.launch {
                                    db.iptvDao().clearUsers()
                                    db.iptvDao().clearRecentProgress()
                                    Toast.makeText(context, context.getString(R.string.account_removed_toast), Toast.LENGTH_SHORT).show()
                                    onNavigateToAuth()
                                }
                            }
                            .padding(10.dp)
                    ) {
                        Icon(Icons.Default.PersonRemove, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.remove_account), color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        } else {
            // Portrait Layout: Single vertical column
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.personalization_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_desc), tint = Color.Gray)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                Text(stringResource(R.string.language_selection), fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                    com.example.model.AppLanguageManager.setLanguage(context, "tr")
                }.padding(vertical = 6.dp)) {
                    RadioButton(selected = currentLang == "tr", onClick = { com.example.model.AppLanguageManager.setLanguage(context, "tr") })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Türkçe", color = Color.White)
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                    com.example.model.AppLanguageManager.setLanguage(context, "en")
                }.padding(vertical = 6.dp)) {
                    RadioButton(selected = currentLang == "en", onClick = { com.example.model.AppLanguageManager.setLanguage(context, "en") })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("English", color = Color.White)
                }

                HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))

                Text("SÜRÜM VE MENÜLER", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))

                SettingsItem(
                    icon = Icons.Default.SystemUpdate,
                    title = "Güncellemeleri Kontrol Et",
                    iconTint = Color(0xFF42A5F5),
                    onClick = {
                        coroutineScope.launch {
                            com.example.model.UpdateManager.checkForUpdates(context, manual = true)
                            onClose()
                        }
                    }
                )
                SettingsItem(
                    icon = Icons.Default.RateReview,
                    title = stringResource(R.string.settings_review),
                    iconTint = Color(0xFFFFA726),
                    onClick = { showRateDialog = true }
                )
                SettingsItem(
                    icon = Icons.Default.Star,
                    title = stringResource(R.string.settings_rate_us),
                    iconTint = Color(0xFFEF5350),
                    onClick = { showRateDialog = true }
                )
                SettingsItem(
                    icon = Icons.Default.Share,
                    title = stringResource(R.string.settings_share_family),
                    iconTint = Color(0xFFBDBDBD),
                    onClick = {
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                               putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text))
                            }
                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.settings_share_family)))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                )
                SettingsItem(
                    icon = Icons.Default.Help,
                    title = stringResource(R.string.settings_faq),
                    iconTint = Color(0xFFAB47BC),
                    onClick = { showFaqDialog = true }
                )
                SettingsItem(
                    icon = Icons.Default.Report,
                    title = stringResource(R.string.settings_report),
                    iconTint = Color(0xFFFF7043),
                    onClick = { onClose(); onOpenSupport() }
                )
                SettingsItem(
                    icon = Icons.Default.Add,
                    title = stringResource(R.string.settings_request),
                    iconTint = Color(0xFFEF5350),
                    onClick = { onClose(); onOpenSupport() }
                )
                SettingsItem(
                    icon = Icons.Default.Build,
                    title = stringResource(R.string.settings_support),
                    iconTint = Color(0xFF42A5F5),
                    onClick = { onClose(); onOpenSupport() }
                )

                HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))

                Text(stringResource(R.string.account_management), fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF263238), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color(0xFF4285F4), modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.google_account_connected), fontSize = 11.sp, color = Color.Gray)
                        Text(currentUser?.email ?: currentUser?.name ?: "ncem0332006@gmail.com", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            coroutineScope.launch {
                                db.iptvDao().clearUsers()
                                onNavigateToAuth()
                            }
                        }
                        .padding(vertical = 10.dp)
                ) {
                    Icon(Icons.Default.SwitchAccount, contentDescription = null, tint = Color(0xFF42A5F5))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.switch_account), color = Color.White, fontSize = 15.sp)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            coroutineScope.launch {
                                db.iptvDao().clearUsers()
                                Toast.makeText(context, context.getString(R.string.sign_out_account), Toast.LENGTH_SHORT).show()
                                onNavigateToAuth()
                            }
                        }
                        .padding(vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, tint = Color(0xFFFFA726))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.sign_out_account), color = Color.White, fontSize = 15.sp)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            coroutineScope.launch {
                                db.iptvDao().clearUsers()
                                db.iptvDao().clearRecentProgress()
                                Toast.makeText(context, context.getString(R.string.account_removed_toast), Toast.LENGTH_SHORT).show()
                                onNavigateToAuth()
                            }
                        }
                        .padding(vertical = 10.dp)
                ) {
                    Icon(Icons.Default.PersonRemove, contentDescription = null, tint = Color(0xFFEF5350))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.remove_account), color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Dialogs inside Profile Settings
    if (showFaqDialog) {
        AlertDialog(
            onDismissRequest = { showFaqDialog = false },
            title = { Text(stringResource(R.string.faq_title), fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.faq_q1), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.faq_a1), color = Color.LightGray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.faq_q2), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.faq_a2), color = Color.LightGray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.faq_q3), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.faq_a3), color = Color.LightGray, fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(onClick = { showFaqDialog = false }) {
                    Text(stringResource(R.string.close_desc))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showRateDialog) {
        var selectedStars by remember { mutableStateOf(5) }
        AlertDialog(
            onDismissRequest = { showRateDialog = false },
            title = { Text(stringResource(R.string.rate_title), fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.rate_desc), color = Color.LightGray, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.Center) {
                        (1..5).forEach { star ->
                            Icon(
                                imageVector = if (star <= selectedStars) Icons.Default.Star else Icons.Default.StarOutline,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { selectedStars = star }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showRateDialog = false
                    Toast.makeText(context, context.getString(R.string.rate_thanks), Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(R.string.rate_submit))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRateDialog = false }) {
                    Text(stringResource(R.string.close_desc), color = Color.Gray)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun SupportTicketsSheet(onClose: () -> Unit) {
    val context = LocalContext.current
    val tickets by com.example.model.SupportRepository.tickets.collectAsState()
    var newTitle by remember { mutableStateOf("") }
    var newMessage by remember { mutableStateOf("") }
    var showNewTicketForm by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                com.example.model.SupportRepository.syncTicketsFromOdoo()
            } catch (e: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(16.dp)
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SupportAgent, contentDescription = null, tint = Color(0xFF42A5F5), modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.support_tickets_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_desc), tint = Color.Gray)
            }
        }
        HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))

        if (showNewTicketForm) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF263238)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.ticket_new_btn), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        placeholder = { Text(stringResource(R.string.ticket_title_label), color = Color.Gray, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF42A5F5), unfocusedBorderColor = Color.DarkGray)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newMessage,
                        onValueChange = { newMessage = it },
                        placeholder = { Text(stringResource(R.string.ticket_msg_label), color = Color.Gray, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().height(90.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF42A5F5), unfocusedBorderColor = Color.DarkGray)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showNewTicketForm = false }) {
                            Text(stringResource(R.string.close_desc), color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newTitle.isNotBlank() && newMessage.isNotBlank()) {
                                    com.example.model.SupportRepository.createTicket(newTitle, null, newMessage)
                                    newTitle = ""
                                    newMessage = ""
                                    showNewTicketForm = false
                                    Toast.makeText(context, context.getString(R.string.report_sent), Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42A5F5))
                        ) {
                            Text(stringResource(R.string.report_send), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            Button(
                onClick = { showNewTicketForm = true },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42A5F5))
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.ticket_new_btn), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (tickets.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.uncategorized), color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tickets.size) { index ->
                    val ticket = tickets[index]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = ticket.title,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    color = if (ticket.status == "Yanıtlandı") Color(0xFF1B5E20) else Color(0xFFE65100),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = if (ticket.status == "Yanıtlandı") stringResource(R.string.ticket_status_resolved) else stringResource(R.string.ticket_status_open),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = ticket.timestamp, fontSize = 10.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = ticket.message, fontSize = 12.sp, color = Color.LightGray)

                            if (ticket.adminReply != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF263238), RoundedCornerShape(6.dp))
                                        .padding(8.dp)
                                ) {
                                    Icon(Icons.Default.SupportAgent, contentDescription = null, tint = Color(0xFF69F0AE), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(stringResource(R.string.ticket_admin_reply_label), fontWeight = FontWeight.Bold, color = Color(0xFF69F0AE), fontSize = 11.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = ticket.adminReply, color = Color.White, fontSize = 12.sp)
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
