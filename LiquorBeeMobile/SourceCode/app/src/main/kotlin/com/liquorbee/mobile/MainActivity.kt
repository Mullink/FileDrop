package com.liquorbee.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.liquorbee.mobile.core.ui.LoginSplashOverlay
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.liquorbee.mobile.core.ui.LiquorBeeMobileTheme
import com.liquorbee.mobile.navigation.LiquorBeeNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val app = application as LiquorBeeApplication

        setContent {
            LiquorBeeMobileTheme {
                var opening by rememberSaveable { mutableStateOf(true) }
                Box {
                    LiquorBeeNavHost(app = app)
                    if (opening) LoginSplashOverlay { opening = false }
                }
            }
        }
    }
}
