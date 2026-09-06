package com.liquorbee.mobile.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Store devices are used indoors under fixed lighting and the brand is a light-navy/blue theme
// end to end on the web app - a single light scheme (no dynamic/dark-mode branching) keeps this
// consistent with that, rather than surprising users with a dark home screen at night.
private val LiquorBeeLightColors = lightColorScheme(
    primary = LiquorBeeNavy,
    onPrimary = Color.White,
    secondary = LiquorBeeBlue,
    onSecondary = Color.White,
    background = LiquorBeeSurface,
    surface = Color.White,
    error = LiquorBeeError
)

private val LiquorBeeTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
)

@Composable
fun LiquorBeeMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LiquorBeeLightColors,
        typography = LiquorBeeTypography,
        content = content
    )
}
