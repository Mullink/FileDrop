package com.liquorbee.mobile.core.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.liquorbee.mobile.itemresolve.ItemMatching
import kotlinx.coroutines.delay

/**
 * The handheld's scanner types into whatever field has focus. Screens that don't use scans themselves
 * get this invisible focused field so a scan is never lost: it hands the code to [onScan] (which opens
 * Scan already looking it up). [key] changes per screen so focus is re-taken on every navigation.
 *
 * While the soft keyboard is closed and nothing else holds focus, the field takes focus back, so a
 * scan still lands after a dialog or search box was used. While someone is typing (keyboard open),
 * it leaves their field alone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScanCapture(key: Any, onScan: (String) -> Unit) {
    val currentOnScan by rememberUpdatedState(onScan)
    var buffer by remember(key) { mutableStateOf("") }
    var focused by remember(key) { mutableStateOf(false) }
    val focus = remember(key) { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible

    fun submit(raw: String) {
        val code = raw.trim()
        buffer = ""
        keyboard?.hide()
        if (code.length >= 4) currentOnScan(code)
    }

    LaunchedEffect(key) {
        runCatching { focus.requestFocus() }
        // The IME shows itself shortly after a field gains focus; keep it hidden - this field is only for the scanner.
        repeat(4) { keyboard?.hide(); delay(150) }
    }
    // Take focus back once nobody is typing.
    LaunchedEffect(key, focused, imeVisible) {
        if (!focused && !imeVisible) {
            delay(600)
            runCatching { focus.requestFocus() }
            delay(100)
            keyboard?.hide()
        }
    }
    // Scanners normally end with Enter; if this one doesn't, a complete barcode followed by a short pause still counts.
    LaunchedEffect(buffer) {
        if (buffer.isNotEmpty()) {
            delay(400)
            if (ItemMatching.looksLikeBarcode(buffer.trim())) submit(buffer)
        }
    }
    BasicTextField(
        value = buffer,
        onValueChange = { v -> if (v.contains('\n')) submit(v.substringBefore('\n')) else buffer = v },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit(buffer) }),
        modifier = Modifier.size(1.dp).alpha(0f).focusRequester(focus).onFocusChanged { focused = it.isFocused }
    )
}
