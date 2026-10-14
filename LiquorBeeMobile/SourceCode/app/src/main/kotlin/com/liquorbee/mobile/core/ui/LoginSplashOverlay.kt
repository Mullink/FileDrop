package com.liquorbee.mobile.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.liquorbee.mobile.R
import kotlinx.coroutines.delay

/** Unmasked artwork on a plain navy screen for launch and login. */
@Composable
fun LoginSplashOverlay(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(900)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LiquorBeeNavy)
            // Swallow all touches while the splash is up so a stray tap can't reach the Home
            // content animating in underneath it.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Image(painter = painterResource(R.drawable.lb_mobile_icon),
            contentDescription = "Liquor Bee Mobile",
            modifier = Modifier.size(280.dp))
    }
}
