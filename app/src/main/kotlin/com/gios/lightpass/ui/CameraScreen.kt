package com.gios.lightpass.ui

import android.graphics.Bitmap
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.gios.lightpass.report.Trouble
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Frames become JPEGs here, one at a time, off the main thread.
 *
 * The encode used to run inside the capture button's onClick, which put a full-resolution
 * compress directly between the finger and the next preview frame — most of what "it lags
 * after three shots" was. One lane rather than plain IO so that even if a second frame ever
 * gets in, two full-size bitmaps are never being encoded at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private val encodeDispatcher = Dispatchers.IO.limitedParallelism(1)

@Composable
fun CameraScreen(newFile: () -> File, onCaptured: (File) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }
    var preview by remember { mutableStateOf<PreviewView?>(null) }
    // One capture per visit. The screen looks single-shot, but onClick fires again before the
    // nav pop lands, so three fast taps used to mean three full-size encodes and three ingests
    // at once — on this phone that is an OutOfMemoryError, and an OOM inside CameraX unbinds
    // the camera: the black preview that never comes back. The shutter goes quiet on the first
    // tap and wakes only if that shot failed and there is something to retry.
    var taking by remember { mutableStateOf(false) }
    // Bound in a DisposableEffect rather than a LaunchedEffect so leaving the screen releases
    // the camera. A controller left bound to a dead composition is the other way this panel
    // comes back black on the next visit.
    DisposableEffect(lifecycleOwner) {
        runCatching { controller.bindToLifecycle(lifecycleOwner) }
        onDispose { runCatching { controller.unbind() } }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.controller = controller
                    preview = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Button(
            enabled = !taking,
            onClick = {
                // Grab the frame already on screen — instant, no capture round-trip.
                // runCatching rather than a catch (Exception): the readback allocates a full
                // frame, and an OutOfMemoryError is an Error, which a catch (Exception) lets
                // straight through to CameraX.
                val bmp = runCatching { preview?.bitmap }
                    .onFailure { Trouble.record("take that photograph", it) }
                    .getOrNull() ?: return@Button
                taking = true
                scope.launch {
                    val out = withContext(encodeDispatcher) {
                        runCatching {
                            newFile().also { f ->
                                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                            }
                        }.onFailure { Trouble.record("save that photograph", it) }.getOrNull()
                    }
                    runCatching { bmp.recycle() }
                    // A shot that cannot be saved costs that shot, not the camera: the app
                    // says so, the shutter wakes again, and the preview is still live.
                    if (out != null) onCaptured(out) else taking = false
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .height(64.dp)
                .fillMaxWidth(0.7f),
        ) { Text("Capture", style = MaterialTheme.typography.titleLarge) }
    }
}
