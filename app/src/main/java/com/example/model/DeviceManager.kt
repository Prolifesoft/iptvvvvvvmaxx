package com.example.model

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

object DeviceManager {
    private const val PREFS_NAME = "device_license_prefs"
    private const val KEY_DEVICE_ID = "pref_device_id"
    private const val KEY_DEVICE_KEY = "pref_device_key"
    private const val KEY_TRIAL_START = "pref_trial_start"
    private const val KEY_IS_PRO = "pref_is_pro"
    private const val KEY_EXPIRED_TEST = "pref_expired_test_mode"
    private const val KEY_ODOO_SYNCED = "pref_odoo_customer_synced"
    private const val KEY_ODOO_SERVER = "pref_odoo_server_url"
    private const val KEY_CUSTOMER_NAME = "pref_odoo_customer_name"

    private const val TRIAL_DURATION_DAYS = 15

    private lateinit var prefs: SharedPreferences

    private val _customerNameState = MutableStateFlow<String?>(null)
    val customerNameState: StateFlow<String?> = _customerNameState.asStateFlow()

    private val _trialDaysLeft = MutableStateFlow(15)
    val trialDaysLeft: StateFlow<Int> = _trialDaysLeft.asStateFlow()

    private val _isProOrInTrialState = MutableStateFlow(true)
    val isProOrInTrialState: StateFlow<Boolean> = _isProOrInTrialState.asStateFlow()

    private val _isProState = MutableStateFlow(false)
    val isProState: StateFlow<Boolean> = _isProState.asStateFlow()

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ensureDeviceId(context.applicationContext)
            refreshLicenseState()
        }
    }

    @SuppressLint("HardwareIds")
    private fun ensureDeviceId(context: Context) {
        if (!prefs.contains(KEY_DEVICE_ID)) {
            val androidId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            } catch (e: Exception) {
                null
            }
            val cleanId = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
                val formatted = androidId.uppercase(Locale.ROOT).takeLast(8)
                "MAX-${formatted.take(4)}-${formatted.takeLast(4)}"
            } else {
                val uuid = UUID.randomUUID().toString().replace("-", "").uppercase(Locale.ROOT).take(8)
                "MAX-${uuid.take(4)}-${uuid.takeLast(4)}"
            }

            // Generate 6-digit PIN key
            val pin = (100000..999999).random().toString()

            prefs.edit()
                .putString(KEY_DEVICE_ID, cleanId)
                .putString(KEY_DEVICE_KEY, pin)
                .putLong(KEY_TRIAL_START, System.currentTimeMillis())
                .apply()
        }
    }

    fun setDeviceCredentials(newId: String, pin: String) {
        prefs.edit()
            .putString(KEY_DEVICE_ID, newId)
            .putString(KEY_DEVICE_KEY, pin)
            .putBoolean(KEY_ODOO_SYNCED, true)
            .apply()
    }

    fun generateNewDeviceCredentials() {
        val uuid = UUID.randomUUID().toString().replace("-", "").uppercase(Locale.ROOT).take(8)
        val newId = "MAX-${uuid.take(4)}-${uuid.takeLast(4)}"
        val pin = (100000..999999).random().toString()
        prefs.edit()
            .putString(KEY_DEVICE_ID, newId)
            .putString(KEY_DEVICE_KEY, pin)
            .putBoolean(KEY_ODOO_SYNCED, false)
            .apply()
    }

    fun getDeviceId(): String {
        var id = if (::prefs.isInitialized) prefs.getString(KEY_DEVICE_ID, "") ?: "" else ""
        if (id.isBlank() || id == "MAX-0000-0000") {
            generateNewDeviceCredentials()
            id = prefs.getString(KEY_DEVICE_ID, "") ?: ""
        }
        return id
    }

    fun getDeviceKey(): String {
        var pin = if (::prefs.isInitialized) prefs.getString(KEY_DEVICE_KEY, "") ?: "" else ""
        if (pin.isBlank() || pin == "123456") {
            generateNewDeviceCredentials()
            pin = prefs.getString(KEY_DEVICE_KEY, "") ?: ""
        }
        return pin
    }

    fun getDeviceModel(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        val model = Build.MODEL
        return "$manufacturer $model"
    }

    fun getOsVersion(): String {
        return "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    fun getMacAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val mac = networkInterface.hardwareAddress
                if (mac != null && mac.isNotEmpty()) {
                    val sb = StringBuilder()
                    for (b in mac) {
                        sb.append(String.format("%02X:", b))
                    }
                    if (sb.isNotEmpty()) {
                        sb.deleteCharAt(sb.length - 1)
                    }
                    val macStr = sb.toString()
                    if (macStr != "02:00:00:00:00:00") {
                        return macStr
                    }
                }
            }
        } catch (e: Exception) {
            // fallback
        }
        val id = getDeviceId().replace("MAX-", "").replace("-", "")
        return if (id.length >= 8) {
            "${id.substring(0,2)}:${id.substring(2,4)}:${id.substring(4,6)}:${id.substring(6,8)}:5E:21"
        } else {
            "02:42:AC:11:00:22"
        }
    }

    fun getTrialStartDate(): Long {
        return prefs.getLong(KEY_TRIAL_START, System.currentTimeMillis())
    }

    fun getDaysRemaining(): Int {
        if (isExpiredTestMode()) return 0
        val start = getTrialStartDate()
        val now = System.currentTimeMillis()
        val diff = now - start
        val daysPassed = (diff / (1000L * 60 * 60 * 24)).toInt()
        val remaining = TRIAL_DURATION_DAYS - daysPassed
        return if (remaining < 0) 0 else remaining
    }

    fun isTrialActive(): Boolean {
        if (isExpiredTestMode()) return false
        return getDaysRemaining() > 0
    }

    fun isProPurchased(): Boolean {
        return prefs.getBoolean(KEY_IS_PRO, false)
    }

    fun isProOrInTrial(): Boolean {
        return isProPurchased() || isTrialActive()
    }

    fun isExpiredTestMode(): Boolean {
        return prefs.getBoolean(KEY_EXPIRED_TEST, false)
    }

    fun setExpiredTestMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EXPIRED_TEST, enabled).apply()
        refreshLicenseState()
    }

    fun upgradeToPro() {
        prefs.edit()
            .putBoolean(KEY_IS_PRO, true)
            .putBoolean(KEY_EXPIRED_TEST, false)
            .apply()
        refreshLicenseState()
    }

    fun resetTrial() {
        prefs.edit()
            .putLong(KEY_TRIAL_START, System.currentTimeMillis())
            .putBoolean(KEY_EXPIRED_TEST, false)
            .putBoolean(KEY_IS_PRO, false)
            .apply()
        refreshLicenseState()
    }

    fun canAddMorePlaylists(currentCount: Int): Boolean {
        if (isProOrInTrial()) return true
        // After 15 days, customer has 1 device and 1 playlist limit
        return currentCount < 1
    }

    fun isOdooCustomerSynced(): Boolean {
        return prefs.getBoolean(KEY_ODOO_SYNCED, false)
    }

    fun setOdooCustomerSynced(synced: Boolean) {
        prefs.edit().putBoolean(KEY_ODOO_SYNCED, synced).apply()
    }

    fun getOdooServerUrl(): String {
        return prefs.getString(KEY_ODOO_SERVER, "https://maxxbilisim.com") ?: "https://maxxbilisim.com"
    }

    fun setOdooServerUrl(url: String) {
        prefs.edit().putString(KEY_ODOO_SERVER, url).apply()
    }

    fun getWebPortalUrl(): String {
        val server = getOdooServerUrl().trimEnd('/')
        val deviceId = getDeviceId()
        val deviceKey = getDeviceKey()
        // KRİTİK GÜVENLİK: QR URL sadece device_id ve one_time_token (device_key) taşımalıdır.
        // Google e-posta, isim veya userId QR URL'ye asla yazılmaz.
        return "$server/device/upload?id=$deviceId&key=$deviceKey"
    }

    fun setCurrentUser(id: String, name: String, email: String) {
        prefs.edit()
            .putString("current_user_id", id)
            .putString("current_user_name", name)
            .putString("current_user_email", email)
            .apply()
    }

    fun getOdooShopTrialUrl(): String {
        val server = getOdooServerUrl().trimEnd('/')
        return "$server/shop/trial/activate?device=${getDeviceId()}&key=${getDeviceKey()}"
    }

    data class OdooPackageInfo(
        val id: String,
        val title: String,
        val duration: String,
        val description: String,
        val price: String,
        val checkoutUrl: String
    )

    private val _packageRequired = MutableStateFlow(false)
    val packageRequired: StateFlow<Boolean> = _packageRequired.asStateFlow()

    private val _upgradeUrl = MutableStateFlow<String?>(null)
    val upgradeUrl: StateFlow<String?> = _upgradeUrl.asStateFlow()

    private val _odooPackages = MutableStateFlow<List<OdooPackageInfo>>(emptyList())
    val odooPackages: StateFlow<List<OdooPackageInfo>> = _odooPackages.asStateFlow()

    fun setPackageRequired(required: Boolean, url: String?, packages: List<OdooPackageInfo>) {
        _packageRequired.value = required
        _upgradeUrl.value = url
        _odooPackages.value = packages
    }

    fun getCustomerName(): String? {
        return prefs.getString(KEY_CUSTOMER_NAME, null)
    }

    fun setCustomerName(name: String?) {
        prefs.edit().putString(KEY_CUSTOMER_NAME, name).apply()
        _customerNameState.value = name
    }

    fun clearDeviceRegistration() {
        prefs.edit()
            .remove(KEY_CUSTOMER_NAME)
            .putBoolean(KEY_ODOO_SYNCED, false)
            .apply()
        _customerNameState.value = null
    }

    fun updateTrialDays(days: Int) {
        if (days >= 0) {
            val calculatedStart = System.currentTimeMillis() - ((15 - days).coerceAtLeast(0) * 24L * 60 * 60 * 1000)
            prefs.edit().putLong(KEY_TRIAL_START, calculatedStart).apply()
            refreshLicenseState()
        }
    }

    fun refreshLicenseState() {
        val days = getDaysRemaining()
        val pro = isProPurchased()
        _customerNameState.value = getCustomerName()
        _trialDaysLeft.value = days
        _isProState.value = pro
        _isProOrInTrialState.value = pro || (days > 0 && !isExpiredTestMode())
    }
}
