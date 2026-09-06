package com.liquorbee.mobile.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.liquorbee.mobile.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The same "zoom burst" the web app plays on Home right after a fresh login (see home-page.css's
 * splashZoom/splashFadeOut keyframes: the logo scales 0.8 -> 1.1 -> 30 over 900ms while its own
 * opacity holds then fades in the back half, and the white overlay itself only starts fading at
 * 70% through). Reproduced here stage-for-stage rather than as a single blanket fade so the timing
 * feels identical to the site.
 */
@Composable
fun LoginSplashOverlay(onFinished: () -> Unit) {
    val scale = remember { Animatable(0.8f) }
    val logoAlpha = remember { Animatable(1f) }
    val overlayAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(1.1f, tween(450, easing = FastOutSlowInEasing))
            launch { logoAlpha.animateTo(0f, tween(450, easing = LinearEasing)) }
            scale.animateTo(30f, tween(450, easing = FastOutSlowInEasing))
        }
        launch {
            delay(630)
            overlayAlpha.animateTo(0f, tween(270, easing = LinearEasing))
        }
        delay(900)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = overlayAlpha.value))
            // Swallow all touches while the splash is up so a stray tap can't reach the Home
            // content animating in underneath it.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.liquor_bee_logo),
            contentDescription = null,
            modifier = Modifier
                .size(140.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    alpha = logoAlpha.value
                }
        )
    }
}
