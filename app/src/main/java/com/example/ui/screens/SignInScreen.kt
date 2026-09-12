package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.model.PlaylistRepository
import com.example.model.db.AppDatabase
import com.example.model.db.PlaylistEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(userId: String, isM3uMode: Boolean, editingPlaylistId: Int?, onLoginSuccess: () -> Unit) {
    var accountName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var hostUrl by remember { mutableStateOf("") }
    
    val isLoading by PlaylistRepository.isLoading.collectAsState()
    val error by PlaylistRepository.error.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val db = AppDatabase.getDatabase(context)
    LaunchedEffect(editingPlaylistId) {
        if (editingPlaylistId != null) {
            val playlist = db.iptvDao().getPlaylistById(editingPlaylistId)
            if (playlist != null) {
                accountName = playlist.name
                username = playlist.username
                password = playlist.password
                hostUrl = playlist.hostUrl
            }
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isM3uMode) Icons.Default.Link else Icons.Default.GridView,
            contentDescription = "Logo",
            tint = Color.White,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (editingPlaylistId != null) "Düzenle" else if (isM3uMode) "Url Ekle" else stringResource(R.string.add_playlist_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.add_playlist_desc),
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        OutlinedTextField(
            value = accountName,
            onValueChange = { accountName = it },
            placeholder = { Text(stringResource(R.string.account_name), color = Color.Gray) },
            trailingIcon = {
                Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.DarkGray
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        
        if (!isM3uMode) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                placeholder = { Text(stringResource(R.string.username), color = Color.Gray) },
                trailingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.DarkGray
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text(stringResource(R.string.password), color = Color.Gray) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.DarkGray
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        
        OutlinedTextField(
            value = hostUrl,
            onValueChange = { hostUrl = it },
            placeholder = { Text(if (isM3uMode) "M3U URL" else stringResource(R.string.url_placeholder), color = Color.Gray) },
            trailingIcon = {
                Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.DarkGray
            )
        )
        Spacer(modifier = Modifier.height(4.dp))

        if (error != null) {
            Text(text = error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                scope.launch {
                    val finalName = accountName.ifBlank { if (isM3uMode) "M3U Playlist" else "My Playlist" }
                    val finalHost = hostUrl.trim()
                    
                    val urlToLoad = if (isM3uMode) {
                        finalHost
                    } else {
                        val host = finalHost.ifBlank { "http://fix.fixekran.xyz:8080" }.trimEnd('/')
                        val user = username.ifBlank { "baki" }
                        val pass = password.ifBlank { "W8NYgWCpSWjd" }
                        "$host/get.php?username=$user&password=$pass&type=m3u_plus&output=mpegts"
                    }
                    
                    PlaylistRepository.loadPlaylist(context, urlToLoad)
                    
                    if (PlaylistRepository.error.value == null) {
                        val db = AppDatabase.getDatabase(context)
                        db.iptvDao().insertPlaylist(
                            PlaylistEntity(
                                id = editingPlaylistId ?: 0,
                                userId = userId,
                                name = finalName,
                                hostUrl = if (isM3uMode) finalHost else finalHost.ifBlank { "http://fix.fixekran.xyz:8080" }.trimEnd('/'),
                                username = if (isM3uMode) "" else username.ifBlank { "baki" },
                                password = if (isM3uMode) "" else password.ifBlank { "W8NYgWCpSWjd" }
                            )
                        )
                        onLoginSuccess()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text(if (editingPlaylistId != null) "Kaydet" else if (isM3uMode) "Ekle" else stringResource(R.string.add_user), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
