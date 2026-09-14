package com.example.model

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val status: String,
    val updateAvailable: Boolean,
    val forceUpdate: Boolean,
    val versionName: String,
    val versionCode: Int,
    val releaseNotes: String,
    val downloadUrl: String
)

object UpdateManager {
    private const val TAG = "UpdateManager"
    private const val ENDPOINT = "https://maxxbilisim.com/api/v1/release/latest"

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking

    private val _downloadProgress = MutableStateFlow(0) // 0 to 100
    val downloadProgress: StateFlow<Int> = _downloadProgress

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo

    private val _errorMsg = MutableStateFlow<String?>(null)
    val errorMsg: StateFlow<String?> = _errorMsg

    private val _upToDateMessage = MutableStateFlow<String?>(null)
    val upToDateMessage: StateFlow<String?> = _upToDateMessage

    fun clearMessages() {
        _errorMsg.value = null
        _upToDateMessage.value = null
    }

    fun dismissUpdate() {
        val current = _updateInfo.value
        if (current != null && !current.forceUpdate) {
            _updateInfo.value = null
        }
    }

    suspend fun checkForUpdates(context: Context, manual: Boolean = false) = withContext(Dispatchers.IO) {
        _isChecking.value = true
        _errorMsg.value = null
        _upToDateMessage.value = null
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }

            val url = URL(ENDPOINT)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            val params = JSONObject().apply {
                put("version_code", currentVersionCode)
            }
            val payload = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("method", "call")
                put("params", params)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
            val code = conn.responseCode

            if (code in 200..299) {
                val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val rootJson = JSONObject(responseStr)
                val resultObj = rootJson.optJSONObject("result") ?: rootJson

                val status = resultObj.optString("status", "success")
                val updateAvailable = resultObj.optBoolean("update_available", false)
                val forceUpdate = resultObj.optBoolean("force_update", false)
                val versionName = resultObj.optString("version_name", "1.0.0")
                val versionCode = resultObj.optInt("version_code", currentVersionCode)
                val rawReleaseNotes = resultObj.optString("release_notes", "")
                val releaseNotes = cleanHtml(rawReleaseNotes)
                val downloadUrl = resultObj.optString("download_url", "")

                val info = UpdateInfo(
                    status = status,
                    updateAvailable = updateAvailable,
                    forceUpdate = forceUpdate,
                    versionName = versionName,
                    versionCode = versionCode,
                    releaseNotes = releaseNotes,
                    downloadUrl = downloadUrl
                )

                _updateInfo.value = info
                if (!updateAvailable && manual) {
                    _upToDateMessage.value = "Uygulamanız güncel"
                }
            } else {
                val errorText = when (code) {
                    401 -> "Yetkilendirme hatası (401)"
                    403 -> "Erişim reddedildi (403)"
                    404 -> "Güncelleme sunucusu bulunamadı (404)"
                    500 -> "Sunucu hatası (500)"
                    else -> "Bağlantı hatası ($code)"
                }
                _errorMsg.value = errorText
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _errorMsg.value = "Ağ bağlantı hatası oluştu. Lütfen internet bağlantınızı kontrol edin."
        } finally {
            _isChecking.value = false
        }
    }

    private fun cleanHtml(html: String): String {
        if (html.isBlank()) return ""
        var cleaned = html.replace(Regex("<[^>]*>"), "")
        cleaned = cleaned.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&ouml;", "ö").replace("&Ouml;", "Ö")
            .replace("&uuml;", "ü").replace("&Uuml;", "Ü")
            .replace("&İ;", "İ").replace("&ı;", "ı")
            .replace("&ş;", "ş").replace("&Ş;", "Ş")
            .replace("&ğ;", "ğ").replace("&Ğ;", "Ğ")
            .replace("&ç;", "ç").replace("&Ç;", "Ç")
        return cleaned.trim()
    }

    suspend fun downloadAndInstallApk(context: Context, downloadUrl: String) = withContext(Dispatchers.IO) {
        if (downloadUrl.isBlank()) {
            _errorMsg.value = "Geçersiz indirme bağlantısı."
            return@withContext
        }
        _isDownloading.value = true
        _downloadProgress.value = 0
        try {
            val url = URL(downloadUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
            }
            val fileLength = conn.contentLength
            val inputStream = conn.inputStream

            val cacheDir = File(context.cacheDir, "updates")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val apkFile = File(cacheDir, "update.apk")
            if (apkFile.exists()) apkFile.delete()

            val outputStream = FileOutputStream(apkFile)
            val data = ByteArray(4096)
            var total: Long = 0
            var count: Int
            while (inputStream.read(data).also { count = it } != -1) {
                total += count.toLong()
                if (fileLength > 0) {
                    val progress = ((total * 100) / fileLength).toInt()
                    _downloadProgress.value = progress
                }
                outputStream.write(data, 0, count)
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            conn.disconnect()

            if (!apkFile.exists() || apkFile.length() < 1000) {
                _errorMsg.value = "İndirilen APK dosyası geçersiz veya çok küçük."
                _isDownloading.value = false
                return@withContext
            }

            // Verify zip/apk header (PK - 0x50, 0x4B)
            val header = ByteArray(4)
            apkFile.inputStream().use { it.read(header) }
            if (header[0] != 0x50.toByte() || header[1] != 0x4B.toByte()) {
                _errorMsg.value = "İndirilen dosya geçerli bir APK değil (Lütfen Odoo'da doğrudan APK dosya bağlantısını girdiğinizden emin olun)."
                _isDownloading.value = false
                return@withContext
            }

            _downloadProgress.value = 100
            _isDownloading.value = false

            withContext(Dispatchers.Main) {
                installApk(context, apkFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _isDownloading.value = false
            _errorMsg.value = "APK indirilirken hata oluştu: ${e.localizedMessage}"
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            _errorMsg.value = "Kurulum başlatılamadı: ${e.localizedMessage}"
        }
    }
}
