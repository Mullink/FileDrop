package com.liquorbee.mobile.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/*
 * Shared building blocks that follow the modern LiquorBee web pages (docs/liquorbee-ui-design-memory.md):
 * light #f8fafc page, flat white panels with #e5e7eb borders, 8dp panel / 6dp control radii,
 * navy primary actions, blue underline tabs, medium-weight (never bold) headings, gray labels,
 * and short-lived toasts for results instead of "Done" dialogs.
 */

val LbPanelShape = RoundedCornerShape(8.dp)
val LbControlShape = RoundedCornerShape(6.dp)
val LbFooter = Color(0xFFF9FAFB)
val LbWarn = Color(0xFFB45309)

/** A page: navy top bar with a medium-weight title, optional underline tabs, light page body. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LbScreen(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    tabs: (@Composable () -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    toast: LbToast? = null,
    onToastShown: () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = LbPage,
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(title, fontWeight = FontWeight.Medium, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        },
                        actions = actions,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = LiquorBeeNavy, titleContentColor = Color.White,
                            navigationIconContentColor = Color.White, actionIconContentColor = Color.White
                        )
                    )
                    tabs?.invoke()
                }
            },
            bottomBar = bottomBar,
            content = content
        )
        LbToastHost(toast, onToastShown, Modifier.align(Alignment.TopCenter).padding(top = 72.dp))
    }
}

/** White underline tabs with the web's blue active indicator; scrolls rather than wrapping. */
@Composable
fun LbTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            titles.forEachIndexed { i, t ->
                val active = i == selected
                Column(Modifier.clickable { onSelect(i) }.padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t, color = if (active) LbActive else LbMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, modifier = Modifier.padding(top = 12.dp, bottom = 10.dp))
                    Box(Modifier.height(2.dp).fillMaxWidth().background(if (active) LbActive else Color.Transparent))
                }
            }
        }
        HorizontalDivider(color = LbBorder)
    }
}

/** Flat white panel with a thin border. */
@Composable
fun LbPanel(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(14.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(Color.White, LbPanelShape).border(1.dp, LbBorder, LbPanelShape).padding(padding), content = content)
}

/** Medium-weight section heading with an optional gray count/status to its right. */
@Composable
fun LbSectionTitle(text: String, trailing: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = LbText, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = LbMuted, fontSize = 13.sp)
    }
}

@Composable
fun LbPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    Button(onClick = onClick, enabled = enabled && !loading, shape = LbControlShape, modifier = modifier.height(44.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LiquorBeeNavy, disabledContainerColor = LiquorBeeNavy.copy(alpha = 0.35f),
            disabledContentColor = Color.White)) {
        if (loading) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        else Text(text, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
fun LbSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = LbText) {
    OutlinedButton(onClick = onClick, enabled = enabled, shape = LbControlShape, modifier = modifier.height(44.dp),
        contentPadding = PaddingValues(horizontal = 14.dp), border = BorderStroke(1.dp, LbBorder),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)) {
        Text(text, color = if (enabled) color else LbFaint, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1)
    }
}

/** Small status pill (Open / Ready / Closed, Reorder, ...). */
@Composable
fun LbPill(text: String, color: Color) {
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        modifier = Modifier.background(color.copy(alpha = 0.10f), LbControlShape).padding(horizontal = 8.dp, vertical = 2.dp))
}

/** Bottom action bar: light footer, summary on the left, actions on the right. */
@Composable
fun LbFooterBar(caption: String, value: String, actions: @Composable RowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(LbFooter)) {
        HorizontalDivider(color = LbBorder)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(caption, color = LbMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value, color = LbText, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            actions()
        }
    }
}

/** Centered empty / loading / error message. */
@Composable
fun LbEmpty(title: String, body: String? = null, loading: Boolean = false, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (loading) CircularProgressIndicator(color = LbActive, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp).padding(bottom = 4.dp))
        Text(title, color = LbText, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        if (body != null) Text(body, color = LbMuted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        if (action != null) Box(Modifier.padding(top = 16.dp)) { action() }
    }
}

data class LbToast(val text: String, val isError: Boolean = false, val id: Long = System.nanoTime())

/** Green/red message at the top that hides itself (the web's AppNoticeService toasts). */
@Composable
fun LbToastHost(toast: LbToast?, onShown: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(toast?.id) {
        if (toast != null) { delay(if (toast.isError) 4500 else 3000); onShown() }
    }
    AnimatedVisibility(visible = toast != null, modifier = modifier.padding(horizontal = 16.dp),
        enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut() + slideOutVertically { -it / 2 }) {
        val t = toast ?: return@AnimatedVisibility
        val color = if (t.isError) LbBad else LbGood
        Text(t.text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth().background(color, LbPanelShape).clickable(onClick = onShown).padding(horizontal = 14.dp, vertical = 12.dp))
    }
}
