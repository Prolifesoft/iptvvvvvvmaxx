package com.example.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@Composable
fun ProvideAppLocale(language: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val newContext = remember(context, language) {
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        context.createConfigurationContext(config)
    }
    
    val configuration = remember(newContext) {
        Configuration(newContext.resources.configuration)
    }
    
    CompositionLocalProvider(
        LocalContext provides newContext,
        LocalConfiguration provides configuration
    ) {
        content()
    }
}
