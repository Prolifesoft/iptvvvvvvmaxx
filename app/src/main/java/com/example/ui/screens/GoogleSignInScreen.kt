package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.BuildConfig
import com.example.auth.GoogleAuthManager
import com.example.model.DeviceManager
import com.example.model.OdooIntegrationManager
import com.example.model.db.AppDatabase
import com.example.model.db.UserEntity
import com.example.ui.theme.RedPrimary
import kotlinx.coroutines.launch

@Composable
fun GoogleSignInScreen(onSignInSuccess: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Initialize device identity
    LaunchedEffect(Unit) {
        DeviceManager.init(context)
    }
    
    val completeLoginWithEmail: (String, String) -> Unit = { email, displayName ->
        isLoading = true
        statusText = "Odoo 19 sunucusunda hesap kontrol ediliyor..."
        errorMessage = null
        scope.launch {
            try {
                // 1. Doğrulanan Google kullanıcısını yerel veritabanına ve DeviceManager'a kaydet
                val db = AppDatabase.getDatabase(context)
                db.iptvDao().insertUser(
                    UserEntity(
                        id = email,
                        name = displayName,
                        email = email
                    )
                )
                DeviceManager.setCustomerName(displayName)
                DeviceManager.setCurrentUser(email, displayName, email)

                // 2. Odoo 19 sunucusuyla müşteri/deneme senkronizasyonu
                try {
                    OdooIntegrationManager.registerCustomerAndTrial(
                        context = context,
                        userId = email,
                        userName = displayName,
                        userEmail = email
                    )
                } catch (e: Exception) {
                    android.util.Log.w("GoogleSignIn", "Odoo kayit uyarisi: ${e.message}")
                }

                // 3. Varsa mevcut Odoo çalma listelerini senkronize et
                try {
                    OdooIntegrationManager.syncPlaylistsFromOdoo(context, email)
                } catch (e: Exception) {}

                // 4. Başarılı şekilde Cihaz Bilgileri (QR Kod) ekranına geçiş yap
                onSignInSuccess(email)
            } catch (e: Exception) {
                isLoading = false
                statusText = null
                errorMessage = "Giriş tamamlanırken hata: ${e.localizedMessage}"
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 440.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Icon(
                imageVector = Icons.Default.GridView,
                contentDescription = null,
                tint = RedPrimary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.app_name),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.google_signin_title),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.google_signin_desc),
                color = Color.LightGray,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3E2723)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFEF5350))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = errorMessage!!, color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // Single Google Sign-In button (Tüm giriş/üye ol işlemleri sadece Google)
            Button(
                onClick = {
                    val configuredId = BuildConfig.GOOGLE_CLIENT_ID
                    val clientId = if (configuredId.isBlank() || configuredId == "YOUR_GOOGLE_CLIENT_ID" || configuredId == "mock-client-id") {
                        "65327632118-76n7d7o8brpa0f3do0s3jieivh7oqm6s.apps.googleusercontent.com"
                    } else {
                        configuredId
                    }
                    
                    isLoading = true
                    statusText = "Google ile giriş yapılıyor..."
                    errorMessage = null
                    
                    scope.launch {
                        try {
                            when (val result = GoogleAuthManager.signInWithGoogle(context, clientId)) {
                                is com.example.auth.GoogleAuthResult.Success -> {
                                    val user = result.user
                                    completeLoginWithEmail(user.email ?: user.id, user.displayName ?: "Kullanıcı")
                                }
                                is com.example.auth.GoogleAuthResult.NoAccountOnDevice -> {
                                    isLoading = false
                                    statusText = null
                                    errorMessage = "Cihazda Google hesabı bulunamadı. Lütfen bir Google hesabı ekleyin."
                                }
                                is com.example.auth.GoogleAuthResult.Cancelled -> {
                                    isLoading = false
                                    statusText = null
                                }
                                is com.example.auth.GoogleAuthResult.Error -> {
                                    isLoading = false
                                    statusText = null
                                    errorMessage = "Google girişi başarısız oldu: ${result.message}"
                                }
                            }
                        } catch (e: Exception) {
                            isLoading = false
                            statusText = null
                            errorMessage = "Google giriş hatası: ${e.localizedMessage}"
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(statusText ?: "Giriş yapılıyor...", fontSize = 14.sp, color = Color.White)
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.sign_in_google),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }


            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Bilgilendirme Kartı
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161A22)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF64B5F6),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Hızlı & Güvenli Google Girişi",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Oturum açma, kayıt olma ve üyelik işlemleri tek tıkla Google üzerinden gerçekleştirilir.\n• Giriş yapıldığında Odoo 15 günlük deneme paketiniz ve cihaz kimliğiniz otomatik eşleştirilir.",
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}
