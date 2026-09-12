package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.DeviceManager
import com.example.ui.theme.RedPrimary

@Composable
fun ProUpgradeDialog(
    onDismiss: () -> Unit,
    onUpgraded: () -> Unit = {}
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFFFFB300), Color(0xFFFF6F00))),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Diamond, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "PRO Sürüm Gerekli",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Exact warning required by user:
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF37271F)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(20.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.pro_required_warning),
                            color = Color(0xFFFFE082),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Odoo 19 Mağaza Paketi Avantajları:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text("• Sınırsız sayıda cihaz ve oynatma listesi", fontSize = 11.sp, color = Color.Gray)
                Text("• Tüm menü, tema ve gelişmiş özelleştirmeler", fontSize = 11.sp, color = Color.Gray)
                Text("• Web tarayıcısından kesintisiz uzaktan yönetim", fontSize = 11.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Try to open Odoo shop upgrade URL in browser
                    try {
                        val shopUrl = DeviceManager.getOdooShopTrialUrl()
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(shopUrl))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Fallback
                    }
                    // Activate PRO in-app
                    DeviceManager.upgradeToPro()
                    Toast.makeText(context, context.getString(R.string.pro_upgrade_success), Toast.LENGTH_LONG).show()
                    onUpgraded()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.btn_upgrade_package),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close_desc), color = Color.Gray)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
