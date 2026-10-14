package com.liquorbee.mobile.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.liquorbee.mobile.R
import androidx.compose.ui.unit.sp

// Store devices are used indoors under fixed lighting and the brand is a light-navy/blue theme
// end to end on the web app - a single light scheme (no dynamic/dark-mode branching) keeps this
// consistent with that, rather than surprising users with a dark home screen at night.
private val LiquorBeeLightColors = lightColorScheme(
    primary = LiquorBeeNavy,
    onPrimary = Color.White,
    secondary = LbActive,
    onSecondary = Color.White,
    background = LbPage,
    onBackground = LbText,
    surface = LbPage,
    onSurface = LbText,
    onSurfaceVariant = LbMuted,
    outline = LbBorder,
    outlineVariant = LbBorder,
    error = LiquorBeeError
)

val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

private val BaseTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
)

private val LiquorBeeTypography = BaseTypography.run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = InterFontFamily),
        displayMedium = displayMedium.copy(fontFamily = InterFontFamily),
        displaySmall = displaySmall.copy(fontFamily = InterFontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = InterFontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = InterFontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = InterFontFamily),
        titleLarge = titleLarge.copy(fontFamily = InterFontFamily),
        titleMedium = titleMedium.copy(fontFamily = InterFontFamily),
        titleSmall = titleSmall.copy(fontFamily = InterFontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = InterFontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = InterFontFamily),
        bodySmall = bodySmall.copy(fontFamily = InterFontFamily),
        labelLarge = labelLarge.copy(fontFamily = InterFontFamily),
        labelMedium = labelMedium.copy(fontFamily = InterFontFamily),
        labelSmall = labelSmall.copy(fontFamily = InterFontFamily)
    )
}

@Composable
fun LiquorBeeMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LiquorBeeLightColors,
        typography = LiquorBeeTypography,
        content = content
    )
}
