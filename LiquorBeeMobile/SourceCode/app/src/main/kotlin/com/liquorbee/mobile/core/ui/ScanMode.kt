package com.liquorbee.mobile.core.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What a scan is for. Item edits (the default) is the original Price Check flow: scan, and the item
 * opens ready to change price or details. Stock mgmt is the Scan screen with the Wholesale, LB cart and
 * Restock tiles. The choice is remembered on the device, and Home's Scan card and hardware scans follow it.
 */
enum class ScanMode(val label: String) { ItemEdits("Item edits"), StockMgmt("Stock mgmt") }

object ScanModePrefs {
    private const val FILE = "liquorbee_scan_mode"
    private const val KEY = "mode"

    fun get(context: Context): ScanMode =
        runCatching { ScanMode.valueOf(prefs(context).getString(KEY, null) ?: "") }.getOrDefault(ScanMode.ItemEdits)

    fun set(context: Context, mode: ScanMode) { prefs(context).edit().putString(KEY, mode.name).apply() }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

/** Two-way switch, sized for the toolbar title slot of Price Check and Scan (navy bar). */
@Composable
fun ScanModeToggle(current: ScanMode, onSelect: (ScanMode) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Row(modifier.height(36.dp).background(Color.White.copy(alpha = 0.12f), shape).padding(3.dp)) {
        ScanMode.values().forEach { mode ->
            val selected = mode == current
            Box(
                Modifier.fillMaxHeight().background(if (selected) Color.White else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable(enabled = !selected, role = Role.Tab) { onSelect(mode) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(mode.label, color = if (selected) LiquorBeeNavy else Color.White.copy(alpha = 0.85f), fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}
