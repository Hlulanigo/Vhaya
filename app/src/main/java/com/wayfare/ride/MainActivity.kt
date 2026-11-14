package com.wayfare.ride

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wayfare.ride.notifications.RideNotificationHelper
import com.wayfare.ride.ui.WayfareApp
import com.wayfare.ride.ui.WayfareViewModel
import com.wayfare.ride.ui.theme.AppThemeMode
import com.wayfare.ride.ui.theme.WayfareTheme

class MainActivity : ComponentActivity() {
    private val viewModel: WayfareViewModel by viewModels {
        WayfareViewModel.Factory((application as WayfareApplication).repository)
    }

    override fun onStart() {
        super.onStart()
        (application as WayfareApplication).repository.setAppInForeground(true)
    }

    override fun onStop() {
        (application as WayfareApplication).repository.setAppInForeground(false)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        handleIncomingIntent(intent)
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (uiState.themeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> systemDark
            }
            DisposableEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                )
                onDispose {}
            }
            WayfareTheme(darkTheme = isDark) {
                WayfareApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(incoming: Intent?) {
        val uri = incoming?.data ?: return
        if (uri.scheme.equals("vhaya", ignoreCase = true) &&
            uri.host.equals("ride", ignoreCase = true) &&
            uri.getQueryParameter("action").equals("accept", ignoreCase = true)
        ) {
            RideNotificationHelper.dismissDriverOfferNotification(this)
        }
        viewModel.handleDeepLink(uri)
    }
}
