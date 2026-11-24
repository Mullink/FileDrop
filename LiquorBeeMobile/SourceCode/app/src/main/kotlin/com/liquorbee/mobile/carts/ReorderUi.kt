package com.liquorbee.mobile.carts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquorbee.mobile.core.ui.LbActive
import com.liquorbee.mobile.core.ui.LbBad
import com.liquorbee.mobile.core.ui.LbGood
import com.liquorbee.mobile.core.ui.LbMuted
import com.liquorbee.mobile.core.ui.LbText
import com.liquorbee.mobile.core.ui.LiquorBeeNavy

private val ReorderWarn = Color(0xFFB45309)

/**
 * One-line reorder status for an item: amber "Reorder · ..." when it needs ordering, green when
 * stock covers the setting, muted while loading or when the item isn't in the report.
 */
@Composable
fun ReorderBadge(code: String?, state: ReorderState, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val (text, color) = reorderStatus(code, state) ?: return
    val info = state.infoFor(code)
    Text(text, color = color, fontSize = 12.sp, fontWeight = if (info?.needsReorder == true) FontWeight.Medium else FontWeight.Normal,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(color.copy(alpha = 0.08f), FieldShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp))
}

/** The reorder line for an item, or null when the reorder check doesn't apply to this store. */
fun reorderStatus(code: String?, state: ReorderState): Pair<String, Color>? {
    if (!state.supported) return null
    val info = state.infoFor(code)
    return when {
        info != null -> info.summary to when {
            info.needsReorder -> ReorderWarn
            info.mode == ReorderMode.ReorderPoint && (info.reorderPoint ?: 0) <= 0 -> LbMuted
            else -> LbGood
        }
        state.loading -> "Checking reorder…" to LbMuted
        state.error != null && !state.loaded -> "Reorder check unavailable" to LbMuted
        // The report only has items that sold in the last 8 weeks (all data or not), so a missing
        // item hasn't sold recently and nothing says to reorder it.
        state.loaded && state.settings.mode == ReorderMode.Forecast -> "No sales in 8 wks · no reorder" to LbMuted
        state.loaded -> "Not in the reorder point report" to LbMuted
        else -> null
    }
}

/** The chip that shows the current reorder setting and opens [ReorderSettingsDialog]. */
@Composable
fun ReorderSettingChip(state: ReorderState, onClick: () -> Unit) {
    if (!state.supported) return
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.background(Color.White, FieldShape).clickable(onClick = onClick).padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
        if (state.loading) CircularProgressIndicator(color = LbActive, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
        else Icon(Icons.Filled.Autorenew, null, tint = LbMuted, modifier = Modifier.size(16.dp))
        Text("  ${state.settings.label}", color = LbText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Icon(Icons.Filled.ArrowDropDown, null, tint = LbMuted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReorderSettingsDialog(state: ReorderState, onSave: (ReorderSettings) -> Unit, onRefresh: () -> Unit, onDismiss: () -> Unit) {
    var mode by remember { mutableStateOf(state.settings.mode) }
    var weeks by remember { mutableIntStateOf(state.settings.weeks) }
    val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = LiquorBeeNavy, selectedLabelColor = Color.White, containerColor = Color.White)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Reorder check", color = LbText, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == ReorderMode.Forecast, onClick = { mode = ReorderMode.Forecast },
                        label = { Text("Forecast") }, colors = chipColors)
                    if (state.canUseReorderPoint) {
                        FilterChip(selected = mode == ReorderMode.ReorderPoint, onClick = { mode = ReorderMode.ReorderPoint },
                            label = { Text("Reorder point") }, colors = chipColors)
                    }
                }
                if (mode == ReorderMode.Forecast) {
                    Text("Weeks of sales to cover", color = LbMuted, fontSize = 12.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.weekOptions.forEach { w ->
                            FilterChip(selected = weeks == w, onClick = { weeks = w }, label = { Text("$w") }, colors = chipColors)
                        }
                    }
                    Text("Flags an item when what sold in the last $weeks ${if (weeks == 1) "week" else "weeks"} would use up everything on hand and on order.",
                        color = LbMuted, fontSize = 12.sp)
                } else {
                    Text("Flags an item when on hand is below its reorder point.", color = LbMuted, fontSize = 12.sp)
                }
                state.reportDate?.let { Text("Report date $it", color = LbMuted, fontSize = 12.sp) }
                state.error?.let { Text(it, color = LbBad, fontSize = 13.sp) }
                TextButton(onClick = onRefresh, enabled = !state.loading, modifier = Modifier.padding(top = 0.dp)) {
                    Text(if (state.loading) "Loading…" else "Reload report", color = LbActive)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(ReorderSettings(mode, weeks)) }, shape = FieldShape,
                colors = ButtonDefaults.buttonColors(containerColor = LiquorBeeNavy)) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = LbText) } }
    )
}
