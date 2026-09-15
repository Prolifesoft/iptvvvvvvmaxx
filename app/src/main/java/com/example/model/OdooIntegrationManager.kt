package com.example.model

import android.content.Context
import android.util.Log
import com.example.model.db.AppDatabase
import com.example.model.db.PlaylistEntity
import com.example.model.db.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class OdooCustomer(
    val id: String,
    val name: String,
    val email: String,
    val deviceId: String,
    val trialActive: Boolean,
    val trialDaysRemaining: Int,
    val isPro: Boolean
)

data class OdooPlaylistPayload(
    val name: String,
    val hostUrl: String,
    val username: String = "",
    val password: String = "",
    val isM3u: Boolean = false
)

object OdooIntegrationManager {
    private const val TAG = "Odoo19Integration"

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncMessage = MutableStateFlow<String?>(null)
    val lastSyncMessage: StateFlow<String?> = _lastSyncMessage.asStateFlow()

    private fun parseOdooPackageResponse(resultJson: JSONObject) {
        val status = resultJson.optString("status", "")
        val code = resultJson.optString("code", "")
        val upgradeRequired = resultJson.optBoolean("upgrade_required", false) || status == "package_required" || code == "upgrade_required"

        if (upgradeRequired) {
            val upgradeUrl = resultJson.optString("upgrade_url", resultJson.optString("url", ""))
            val packagesArray = resultJson.optJSONArray("packages")
            val pkgList = mutableListOf<DeviceManager.OdooPackageInfo>()
            if (packagesArray != null) {
                for (i in 0 until packagesArray.length()) {
                    val pObj = packagesArray.getJSONObject(i)
                    pkgList.add(
                        DeviceManager.OdooPackageInfo(
                            id = pObj.optString("id", pObj.optString("code", "$i")),
                            title = pObj.optString("title", pObj.optString("name", "Paket ${i + 1}")),
                            duration = pObj.optString("duration", pObj.optString("period", "")),
                            description = pObj.optString("description", pObj.optString("desc", "")),
                            price = pObj.optString("price", ""),
                            checkoutUrl = pObj.optString("checkout_url", pObj.optString("url", upgradeUrl))
                        )
                    )
                }
            }
            DeviceManager.setPackageRequired(true, upgradeUrl, pkgList)
        } else {
            DeviceManager.setPackageRequired(false, null, emptyList())
        }
    }

    /**
     * Registers new user as an Odoo 19 customer with an active 15-day Trial Package.
     */
    suspend fun registerCustomerAndTrial(
        context: Context,
        userId: String,
        userName: String,
        userEmail: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val deviceId = DeviceManager.getDeviceId()
            val deviceKey = DeviceManager.getDeviceKey()
            val odooUrl = DeviceManager.getOdooServerUrl().trimEnd('/')

            Log.d(TAG, "Registering customer to Odoo 19: $userEmail, device: $deviceId, key: $deviceKey at $odooUrl")

            val endpoint = "$odooUrl/api/v1/customer/register"
            var isSuccess = false
            try {
                val url = URL(endpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 6000
                    readTimeout = 6000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                }
                val params = JSONObject().apply {
                    put("email", userEmail)
                    put("name", userName)
                    put("device_id", deviceId)
                    put("device_key", deviceKey)
                    put("device_model", DeviceManager.getDeviceModel())
                    put("trial_package", "15_DAYS_FULL_ACCESS")
                    put("platform", "Android IPTV Player")
                }
                val payload = JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("method", "call")
                    put("params", params)
                }
                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                val code = conn.responseCode
                val responseStr = if (code in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }
                Log.d(TAG, "Odoo customer register server response code: $code, body: $responseStr")
                conn.disconnect()

                if (responseStr.isNotBlank()) {
                    val rootJson = JSONObject(responseStr)
                    val resultJson = rootJson.optJSONObject("result") ?: rootJson
                    parseOdooPackageResponse(resultJson)
                    val status = resultJson.optString("status", "")
                    val errorObj = rootJson.optJSONObject("error")
                    val errorMsg = errorObj?.optJSONObject("data")?.optString("message") 
                        ?: errorObj?.optString("message") 
                        ?: resultJson.optString("message")

                    if (status == "success" || resultJson.has("package_type")) {
                        isSuccess = true
                        DeviceManager.setOdooCustomerSynced(true)
                        if (resultJson.optBoolean("is_pro", false)) {
                            DeviceManager.upgradeToPro()
                        }
                        val custName = resultJson.optString("customer_name", resultJson.optString("name", userName)).trim()
                        if (custName.isNotBlank()) {
                            DeviceManager.setCustomerName(custName)
                        }
                        if (resultJson.has("days_remaining")) {
                            val remaining = resultJson.optInt("days_remaining", -1)
                            if (remaining >= 0) {
                                DeviceManager.updateTrialDays(remaining)
                            }
                        }
                    } else if (errorMsg.contains("cihaz limiti", ignoreCase = true) ||
                               errorMsg.contains("limit", ignoreCase = true) ||
                               errorMsg.contains("zaten", ignoreCase = true) ||
                               errorMsg.contains("already exists", ignoreCase = true) ||
                               errorMsg.contains("ValidationError", ignoreCase = true)) {
                        // Mevcut Odoo müşterisi tespit edildi (cihaz limiti veya hesap mevcut)
                        Log.i(TAG, "Mevcut Odoo müşterisi doğrulandı: $userEmail ($errorMsg)")
                        isSuccess = true
                        DeviceManager.setOdooCustomerSynced(true)
                        DeviceManager.setCustomerName(userName)
                        if (DeviceManager.getDaysRemaining() <= 0) {
                            DeviceManager.updateTrialDays(15)
                        }
                        _lastSyncMessage.value = "Odoo 19 Müşteri Hesabı Doğrulandı"
                    } else if (status == "error" && errorMsg.contains("Bu cihaz baska bir hesaba kayitli", ignoreCase = true)) {
                        // Conflict: Generate clean new device credentials and retry registration
                        Log.w(TAG, "Device conflict detected. Generating fresh device ID and retrying registration...")
                        DeviceManager.generateNewDeviceCredentials()
                        val newDevId = DeviceManager.getDeviceId()
                        val newDevKey = DeviceManager.getDeviceKey()

                        val retryConn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = 6000
                            readTimeout = 6000
                            doOutput = true
                            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                        }
                        val retryParams = JSONObject().apply {
                            put("email", userEmail)
                            put("name", userName)
                            put("device_id", newDevId)
                            put("device_key", newDevKey)
                            put("device_model", DeviceManager.getDeviceModel())
                            put("trial_package", "15_DAYS_FULL_ACCESS")
                            put("platform", "Android IPTV Player")
                        }
                        val retryPayload = JSONObject().apply {
                            put("jsonrpc", "2.0")
                            put("method", "call")
                            put("params", retryParams)
                        }
                        OutputStreamWriter(retryConn.outputStream).use { it.write(retryPayload.toString()) }
                        val retryCode = retryConn.responseCode
                        val retryResponseStr = if (retryCode in 200..299) {
                            retryConn.inputStream.bufferedReader().use { it.readText() }
                        } else {
                            retryConn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                        }
                        retryConn.disconnect()
                        Log.d(TAG, "Retry register response code: $retryCode, body: $retryResponseStr")
                        if (retryResponseStr.isNotBlank()) {
                            val retryRoot = JSONObject(retryResponseStr)
                            val retryResult = retryRoot.optJSONObject("result") ?: retryRoot
                            val retryError = retryRoot.optJSONObject("error")
                            val retryErrorMsg = retryError?.optJSONObject("data")?.optString("message")
                                ?: retryError?.optString("message")
                                ?: retryResult.optString("message")

                            if (retryResult.optString("status") == "success" || retryResult.has("package_type") ||
                                retryErrorMsg.contains("cihaz limiti", ignoreCase = true) ||
                                retryErrorMsg.contains("ValidationError", ignoreCase = true)) {
                                isSuccess = true
                                DeviceManager.setOdooCustomerSynced(true)
                                DeviceManager.setCustomerName(userName)
                                if (retryResult.optBoolean("is_pro", false)) {
                                    DeviceManager.upgradeToPro()
                                }
                            }
                        }
                    }
                }
            } catch (networkEx: Exception) {
                Log.e(TAG, "Odoo server register attempt: ${networkEx.message}")
            }

            if (isSuccess) {
                _lastSyncMessage.value = "Odoo 19 Müşteri ve 15 Günlük Deneme Paketi Aktif Edildi"
            }
            isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Error in registerCustomerAndTrial", e)
            false
        }
    }

    /**
     * Synchronizes playlists from Odoo 19 linked to the user's account & device.
     * Pulls all playlists ("birden fazla çalma listesi varsa hepsini gösterecek").
     * Safely updates local playlists without deleting existing lists on network/sync errors.
     */
    suspend fun syncPlaylistsFromOdoo(context: Context, userId: String): Int = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        var resultCount = 0
        try {
            val db = AppDatabase.getDatabase(context)
            val effectiveUserId = if (userId.isNotBlank()) userId else (DeviceManager.getCurrentUserId() ?: DeviceManager.getDeviceId())

            // Ensure user exists in Room DB to satisfy foreign key constraints
            val existingUser = db.iptvDao().getUser(effectiveUserId)
            if (existingUser == null) {
                db.iptvDao().insertUser(
                    com.example.model.db.UserEntity(
                        id = effectiveUserId,
                        name = DeviceManager.getCustomerName() ?: DeviceManager.getCurrentUserName() ?: "IPTV Kullanıcısı",
                        email = DeviceManager.getCurrentUserEmail() ?: ""
                    )
                )
            }

            // 1. Fetch remote playlists from Odoo
            val odooPlaylists = fetchRemotePlaylistsFromOdoo(effectiveUserId)
            val localPlaylists = db.iptvDao().getPlaylistsForUserSync(effectiveUserId)

            if (odooPlaylists.isNotEmpty()) {
                val remoteUrls = odooPlaylists.map { it.hostUrl }.toSet()

                // Only remove local playlists if they were genuinely deleted on Odoo backend
                for (local in localPlaylists) {
                    if (!remoteUrls.contains(local.hostUrl)) {
                        db.iptvDao().deletePlaylist(local)
                    }
                }

                // Insert or update remote playlists from Odoo
                for (p in odooPlaylists) {
                    val existing = localPlaylists.find { it.hostUrl == p.hostUrl }
                    if (existing == null) {
                        val entity = PlaylistEntity(
                            userId = effectiveUserId,
                            name = p.name,
                            hostUrl = p.hostUrl,
                            username = p.username,
                            password = p.password
                        )
                        db.iptvDao().insertPlaylist(entity)
                    } else if (existing.name != p.name || existing.username != p.username || existing.password != p.password) {
                        val updated = existing.copy(
                            name = p.name,
                            username = p.username,
                            password = p.password
                        )
                        db.iptvDao().insertPlaylist(updated)
                    }
                }
                resultCount = odooPlaylists.size
                _lastSyncMessage.value = "$resultCount adet çalma listesi Odoo 19'dan başarıyla senkronize edildi."
            } else {
                // If remote returned 0 playlists, preserve existing local playlists (do not delete on potential error)
                val currentMsg = _lastSyncMessage.value.orEmpty()
                if (currentMsg.isBlank() || currentMsg.contains("senkronize", ignoreCase = true)) {
                    _lastSyncMessage.value = "Odoo üzerinde bu cihaza ait tanımlı çalma listesi bulunamadı."
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing playlists from Odoo", e)
            _lastSyncMessage.value = "Odoo senkronizasyon hatası: ${e.localizedMessage}"
        } finally {
            _isSyncing.value = false
        }
        resultCount
    }

    /**
     * Extracts playlists from any JSON structure returned by Odoo.
     */
    private fun extractPlaylistsFromJson(jsonObjOrArray: Any?): List<OdooPlaylistPayload> {
        val list = mutableListOf<OdooPlaylistPayload>()
        if (jsonObjOrArray == null) return list

        fun parseItem(item: JSONObject, index: Int): OdooPlaylistPayload? {
            val hostUrl = item.optString("hostUrl",
                item.optString("host_url",
                    item.optString("url",
                        item.optString("playlist_url",
                            item.optString("m3u_url",
                                item.optString("stream_url",
                                    item.optString("server_url",
                                        item.optString("server",
                                            item.optString("portal_url",
                                                item.optString("portal",
                                                    item.optString("dns",
                                                        item.optString("link", "")))))))))))).trim()
            if (hostUrl.isBlank()) return null

            var username = item.optString("username", item.optString("user", item.optString("account", ""))).trim()
            var password = item.optString("password", item.optString("pass", "")).trim()
            val name = item.optString("name", item.optString("title", item.optString("playlist_name", item.optString("package_name", "Odoo Çalma Listesi ${index + 1}")))).trim()
            val isM3u = item.optBoolean("isM3u", item.optBoolean("is_m3u", hostUrl.contains(".m3u", ignoreCase = true) || hostUrl.contains("type=m3u", ignoreCase = true)))

            // Extract credentials from URL query params if missing
            if (username.isBlank() && hostUrl.contains("username=")) {
                val userMatch = Regex("[?&]username=([^&]+)").find(hostUrl)
                if (userMatch != null) username = userMatch.groupValues[1]
            }
            if (password.isBlank() && hostUrl.contains("password=")) {
                val passMatch = Regex("[?&]password=([^&]+)").find(hostUrl)
                if (passMatch != null) password = passMatch.groupValues[1]
            }

            return OdooPlaylistPayload(
                name = if (name.isNotBlank()) name else "Odoo Çalma Listesi ${index + 1}",
                hostUrl = hostUrl,
                username = username,
                password = password,
                isM3u = isM3u
            )
        }

        if (jsonObjOrArray is org.json.JSONArray) {
            for (i in 0 until jsonObjOrArray.length()) {
                val itm = jsonObjOrArray.optJSONObject(i)
                if (itm != null) {
                    val parsed = parseItem(itm, i)
                    if (parsed != null) list.add(parsed)
                }
            }
            return list
        }

        if (jsonObjOrArray is JSONObject) {
            val arraysToCheck = listOf("playlists", "playlist", "data", "items", "list", "lines", "channels")
            for (key in arraysToCheck) {
                val arr = jsonObjOrArray.optJSONArray(key)
                if (arr != null && arr.length() > 0) {
                    for (i in 0 until arr.length()) {
                        val itm = arr.optJSONObject(i)
                        if (itm != null) {
                            val parsed = parseItem(itm, i)
                            if (parsed != null) list.add(parsed)
                        }
                    }
                    if (list.isNotEmpty()) return list
                }
            }

            // Check if "playlist" is a single JSONObject
            val singleObj = jsonObjOrArray.optJSONObject("playlist")
            if (singleObj != null) {
                val parsed = parseItem(singleObj, 0)
                if (parsed != null) list.add(parsed)
                if (list.isNotEmpty()) return list
            }

            // Check if jsonObjOrArray itself represents a playlist
            val selfParsed = parseItem(jsonObjOrArray, 0)
            if (selfParsed != null) {
                list.add(selfParsed)
            }
        }

        return list
    }

    /**
     * Queries Odoo 19 customer backend for assigned playlists.
     */
    private fun fetchRemotePlaylistsFromOdoo(userId: String): List<OdooPlaylistPayload> {
        val odooUrl = DeviceManager.getOdooServerUrl().trimEnd('/')
        val endpoint = "$odooUrl/api/v1/playlists/get"
        try {
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 7000
                readTimeout = 7000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            }
            val effectiveEmail = if (userId.contains("@")) userId else (DeviceManager.getCurrentUserEmail() ?: "")
            val params = JSONObject().apply {
                put("device_id", DeviceManager.getDeviceId())
                put("device_key", DeviceManager.getDeviceKey())
                put("pin", DeviceManager.getDeviceKey())
                put("key", DeviceManager.getDeviceKey())
                put("mac", DeviceManager.getMacAddress())
                put("mac_address", DeviceManager.getMacAddress())
                put("user_id", userId)
                put("email", effectiveEmail)
            }
            val payload = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("method", "call")
                put("params", params)
            }
            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
            val code = conn.responseCode
            val responseStr = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }
            conn.disconnect()
            Log.d(TAG, "Odoo playlists/get response code: $code, response: $responseStr")

            if (responseStr.isNotBlank()) {
                val rootJson = JSONObject(responseStr)
                val resultObj = rootJson.opt("result")
                val errorObj = rootJson.optJSONObject("error")
                val errorMsg = errorObj?.optJSONObject("data")?.optString("message")
                    ?: errorObj?.optString("message")

                if (resultObj is JSONObject) {
                    parseOdooPackageResponse(resultObj)
                    val status = resultObj.optString("status", "")
                    val message = resultObj.optString("message", "")
                    if (status == "error" && message.isNotBlank()) {
                        _lastSyncMessage.value = "Odoo: $message"
                    }

                    if (resultObj.optBoolean("is_pro", false)) {
                        DeviceManager.upgradeToPro()
                    }
                    if (resultObj.has("days_remaining")) {
                        val remaining = resultObj.optInt("days_remaining", -1)
                        if (remaining >= 0) {
                            DeviceManager.updateTrialDays(remaining)
                        }
                    }

                    val odooCustomerName = resultObj.optString("customer_name", resultObj.optString("partner_name", resultObj.optString("name", ""))).trim()
                    if (odooCustomerName.isNotBlank()) {
                        DeviceManager.setCustomerName(odooCustomerName)
                    }
                } else if (errorMsg != null && errorMsg.isNotBlank()) {
                    _lastSyncMessage.value = "Odoo: $errorMsg"
                }

                val playlists = extractPlaylistsFromJson(resultObj ?: rootJson)
                if (playlists.isNotEmpty()) {
                    return playlists
                }
            }
        } catch (e: Exception) {
            Log.i(TAG, "Live Odoo sync attempt failed: ${e.message}")
            _lastSyncMessage.value = "Bağlantı hatası: ${e.localizedMessage}"
        }

        return emptyList()
    }
}
