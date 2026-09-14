package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DeviceManager
import kotlinx.coroutines.launch

data class OdooPackageItem(
    val id: String,
    val title: String,
    val duration: String,
    val description: String,
    val price: String,
    val isPopular: Boolean = false
)

@Composable
fun PackageSelectionScreen(
    userId: String,
    onPackageSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedPackageId by remember { mutableStateOf("15_DAYS_FULL_ACCESS") }
    var isLoading by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val odooPackages by DeviceManager.odooPackages.collectAsState()
    val upgradeUrl by DeviceManager.upgradeUrl.collectAsState()

    val packages = remember(odooPackages) {
        if (odooPackages.isNotEmpty()) {
            odooPackages.map { p ->
                OdooPackageItem(
                    id = p.id,
                    title = p.title,
                    duration = p.duration,
                    description = p.description,
                    price = p.price,
                    isPopular = false
                )
            }
        } else {
            listOf(
                OdooPackageItem(
                    id = "PRO_MONTHLY",
                    title = "Pro IPTV Aylık Paket",
                    duration = "1 Ay",
                    description = "Sınırsız çalma listesi, gelişmiş kanal yönetimi ve öncelikli Odoo 19 bulut senkronizasyonu.",
                    price = "₺99.99 / Ay"
                ),
                OdooPackageItem(
                    id = "PRO_ANNUAL",
                    title = "Pro IPTV Yıllık Paket",
                    duration = "1 Yıl",
                    description = "12 ay kesintisiz IPTV deneyimi, özel VIP destek hattı ve %30 indirimli avantajlı fiyat.",
                    price = "₺899.99 / Yıl"
                )
            )
        }
    }

    val handlePackageConfirmation: () -> Unit = {
        isLoading = true
        scope.launch {
            val selectedPkg = odooPackages.find { it.id == selectedPackageId }
            val checkoutUrl = selectedPkg?.checkoutUrl ?: upgradeUrl ?: DeviceManager.getOdooShopTrialUrl()
            
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // ignore
            }

            if (selectedPackageId.contains("15") || selectedPackageId.contains("trial", ignoreCase = true) || selectedPackageId == "15_DAYS_FULL_ACCESS") {
                DeviceManager.updateTrialDays(15)
            } else {
                DeviceManager.upgradeToPro()
            }
            DeviceManager.setPackageRequired(false, null, emptyList())
            isLoading = false
            onPackageSelected(selectedPackageId)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF090D16))
                )
            )
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(16.dp)
    ) {
        if (isLandscape) {
            // Landscape Layout: 2 Columns (Left: Info, Right: Packages & Action)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 900.dp)
                    .align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.9f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFD32F2F).copy(alpha = 0.15f),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Odoo 19 Paket Seçimi",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Cihazınız için aktif etmek istediğiniz Odoo paketini seçin (Cihaz Limiti: 1 Cihaz)",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (pkg in packages) {
                            val isSelected = selectedPackageId == pkg.id
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0xFF1E293B) else Color(0xFF131B2E)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) Color(0xFFEF4444) else Color(0xFF334155),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedPackageId = pkg.id }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pkg.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = pkg.price,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFEF4444)
                                        )
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedPackageId = pkg.id },
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFEF4444))
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = handlePackageConfirmation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = "Paketi Onayla ve QR Koda Geç",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        } else {
            // Portrait Layout: Vertical stack
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .align(Alignment.Center),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFD32F2F).copy(alpha = 0.15f),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Odoo 19 Paket Seçimi",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Cihazınız için aktif etmek istediğiniz Odoo paketini seçin (Cihaz Limiti: 1 Cihaz)",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(packages) { pkg ->
                        val isSelected = selectedPackageId == pkg.id
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF1E293B) else Color(0xFF131B2E)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Color(0xFFEF4444) else Color(0xFF334155),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { selectedPackageId = pkg.id }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = pkg.title,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        if (pkg.isPopular) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFEF4444)
                                            ) {
                                                Text(
                                                    text = "Önerilen",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = pkg.description,
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = pkg.price,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEF4444)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedPackageId = pkg.id },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFFEF4444),
                                        unselectedColor = Color.Gray
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = handlePackageConfirmation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = "Paketi Onayla ve QR Koda Geç",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

