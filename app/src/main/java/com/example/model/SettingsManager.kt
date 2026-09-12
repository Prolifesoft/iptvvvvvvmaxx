package com.example.model

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SettingsManager {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_IS_PORTRAIT_ENABLED = "is_portrait_enabled"

    private lateinit var prefs: SharedPreferences

    private val _isPortraitEnabled = MutableStateFlow(false)
    val isPortraitEnabled: StateFlow<Boolean> = _isPortraitEnabled.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isPortraitEnabled.value = prefs.getBoolean(KEY_IS_PORTRAIT_ENABLED, false)
    }

    fun setPortraitEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_PORTRAIT_ENABLED, enabled).apply()
        _isPortraitEnabled.value = enabled
    }
}
