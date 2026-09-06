package com.liquorbee.invoicescanner.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Imports an existing image (gallery/file picker) - zero hardware dependency, mirrors the desktop
 * app's FilePickerScanner.cs fallback (LiquorBeeInvoiceScanner/Scanning/FilePickerScanner.cs),
 * same idea: a no-hardware-required way to get a page in when there's no scanner/camera to use.
 *
 * This is the one guaranteed-working capture path on a camera-less device (e.g. an iMin Swan 1 Pro
 * POS terminal with no camera and, until Brother DS-640 support is real, no working scanner path
 * either) - always available regardless of what hardware is or isn't present.
 *
 * Must be constructed (and its launcher registered) in onCreate, before the host Activity is
 * STARTED - same ActivityResultLauncher requirement as CameraCaptureSource.
 */
class FilePickerCaptureSource(private val activity: ComponentActivity) : PageCaptureSource {

    override val displayName: String = "Import Image"

    // Always available - no hardware/permission dependency at all.
    override fun isAvailable(): Boolean = true

    private var pendingContinuation: ((CaptureResult) -> Unit)? = null

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val continuation = pendingContinuation
        pendingContinuation = null

        if (uri == null) {
            continuation?.invoke(CaptureResult.Cancelled)
            return@registerForActivityResult
        }

        val bitmap: Bitmap? = activity.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
        if (bitmap == null) {
            continuation?.invoke(CaptureResult.Failure("Could not read the selected image."))
        } else {
            continuation?.invoke(CaptureResult.Success(CapturedPage(bitmap)))
        }
    }

    suspend fun capture(): CaptureResult = suspendCancellableCoroutine { cont ->
        pendingContinuation = { result -> cont.resume(result) }
        launcher.launch("image/*")
    }
}
