package com.liquorbee.mobile.carts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquorbee.mobile.core.ui.InterFontFamily
import com.liquorbee.mobile.core.ui.LbActive
import com.liquorbee.mobile.core.ui.LbBorder
import com.liquorbee.mobile.core.ui.LbFaint
import com.liquorbee.mobile.core.ui.LbMuted
import com.liquorbee.mobile.core.ui.LbText
import java.text.NumberFormat
import java.util.Locale

internal val CardShape = RoundedCornerShape(8.dp)
internal val FieldShape = RoundedCornerShape(6.dp)
internal val TabularNumbers = TextStyle(fontFeatureSettings = "tnum", fontFamily = InterFontFamily)
private val currencyFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US)
internal fun money(value: Double): String = currencyFormat.format(value)

@Composable
internal fun ScanSearchField(
    value: String,
    loading: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v ->
            // Some scanners end with Enter, which can arrive as a newline - treat it as submit.
            if (v.contains('\n')) { onValueChange(v.replace("\n", "")); onSubmit() } else onValueChange(v)
        },
        placeholder = { Text("Scan or search items", color = LbFaint) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = LbMuted, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (loading) CircularProgressIndicator(color = LbActive, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                if (value.isNotEmpty()) {
                    IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = "Clear", tint = LbFaint, modifier = Modifier.size(20.dp)) }
                }
            }
        },
        singleLine = true,
        textStyle = TextStyle(color = LbText, fontSize = 16.sp, fontFamily = InterFontFamily),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }, onDone = { onSubmit() }),
        shape = FieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LbFaint, unfocusedBorderColor = LbBorder,
            focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, cursorColor = LbActive
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
internal fun StepButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).border(1.dp, LbBorder, FieldShape).background(Color.White, FieldShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = label, tint = if (enabled) LbText else LbFaint, modifier = Modifier.size(18.dp)) }
}

/** [-] qty [+], tap the number to type a quantity. */
@Composable
internal fun QtyStepper(qty: Int, onQty: (Int) -> Unit, onEdit: () -> Unit, min: Int = 0) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton(Icons.Filled.Remove, "Decrease", enabled = qty > min) { onQty(qty - 1) }
        Text("$qty", color = LbText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, style = TabularNumbers,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(52.dp).clickable(onClick = onEdit).padding(vertical = 8.dp))
        StepButton(Icons.Filled.Add, "Increase") { onQty(qty + 1) }
    }
}

/** One cart row: name, a subtitle, a qty stepper, a trailing amount, and a remove button. */
@Composable
internal fun CartLineRow(
    title: String,
    subtitle: String,
    qty: Int,
    trailing: String?,
    onQty: (Int) -> Unit,
    onRemove: () -> Unit,
    /** Optional line under the subtitle (e.g. the reorder badge). */
    detail: (@Composable () -> Unit)? = null
) {
    var editing by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().background(Color.White, CardShape).border(1.dp, LbBorder, CardShape)
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(title, color = LbText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = LbFaint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                if (detail != null) Box(Modifier.padding(top = 6.dp)) { detail() }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove", tint = LbFaint, modifier = Modifier.size(20.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            QtyStepper(qty = qty, onQty = onQty, onEdit = { editing = true })
            Spacer(Modifier.weight(1f))
            if (trailing != null) {
                Text(trailing, color = LbText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, style = TabularNumbers,
                    modifier = Modifier.padding(end = 8.dp))
            }
        }
    }
    if (editing) QuantityDialog(title = title, initial = qty, onDismiss = { editing = false }, onSave = { onQty(it); editing = false })
}

@Composable
internal fun QuantityDialog(title: String, initial: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    val parsed = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(title, color = LbText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() }.take(5) },
                label = { Text("Quantity") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { parsed?.let(onSave) }),
                shape = FieldShape,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null) { Text("Save", color = LbActive) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = LbText) } }
    )
}

@Composable
internal fun CenteredMessage(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) { content() }
}

/**
 * Units on order from the distributor that haven't shipped yet for [code], loaded in the
 * background. Null while loading or when the server can't provide it - callers just omit it.
 */
@Composable
fun rememberQtyOnReserve(code: String?): Int? {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.liquorbee.mobile.LiquorBeeApplication
    val state = androidx.compose.runtime.produceState<Int?>(initialValue = null, code) {
        value = code?.takeIf { it.isNotBlank() }?.let { app.catalogRepository.qtyOnReserve(it) }
    }
    return state.value
}
