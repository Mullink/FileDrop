package com.liquorbee.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
                LiquorBeeNavHost(app = app)
            }
        }
    }
}
