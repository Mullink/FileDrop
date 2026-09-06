package com.liquorbee.invoicescanner.capture

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Delegates to the system Camera app via the standard TakePicture contract + a FileProvider URI,
 * rather than building a custom CameraX preview screen. Chosen deliberately for reliability: this
 * is the lowest-risk, most-tested path for "take one photo into a file I own" on Android, and the
 * previous (MAUI) attempt's core problem was the app failing to even launch - minimizing custom
 * camera/lifecycle code here reduces the surface area for that class of bug. Must be constructed
 * (and its launcher registered) in onCreate, before the host Activity is STARTED - same
 * requirement as any ActivityResultLauncher.
 */
class CameraCaptureSource(private val activity: ComponentActivity) : PageCaptureSource {

    override val displayName: String = "Camera"

    override fun isAvailable(): Boolean =
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    private var pendingContinuation: ((CaptureResult) -> Unit)? = null
    private var pendingFile: File? = null

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingFile
        val continuation = pendingContinuation
        pendingContinuation = null
        pendingFile = null

        if (!success || file == null) {
            continuation?.invoke(CaptureResult.Cancelled)
            return@registerForActivityResult
        }

        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null) {
            continuation?.invoke(CaptureResult.Failure("Camera returned no usable image."))
        } else {
            continuation?.invoke(CaptureResult.Success(CapturedPage(bitmap)))
        }
    }

    suspend fun capture(): CaptureResult = suspendCancellableCoroutine { cont ->
        val dir = File(activity.cacheDir, "captured_pages").apply { mkdirs() }
        val file = File(dir, "page_${System.currentTimeMillis()}.jpg")
        val uri: Uri = FileProvider.getUriForFile(
            activity, "${activity.packageName}.fileprovider", file
        )

        pendingFile = file
        pendingContinuation = { result -> cont.resume(result) }

        launcher.launch(uri)
    }
}
