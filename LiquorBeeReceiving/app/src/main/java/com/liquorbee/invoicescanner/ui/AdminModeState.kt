package com.liquorbee.invoicescanner.ui

/**
 * In-memory only (deliberately not persisted to SessionManager) - resets to Standard Mode every
 * time the app process starts, so admin/diagnostic access never silently stays unlocked across
 * app restarts if the device changes hands. Unlocked via HomeActivity's 5-tap + "LiquorBee444"
 * password gate; gates ScanActivity's USB Diagnostics button.
 */
object AdminModeState {
    var isEnabled: Boolean = false
}
