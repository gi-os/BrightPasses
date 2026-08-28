package com.gios.lightpass.ui

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
import java.io.File

@Composable
fun CameraScreen(newFile: () -> File, onCaptured: (File) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }
    var preview by remember { mutableStateOf<PreviewView?>(null) }
    LaunchedEffect(Unit) { controller.bindToLifecycle(lifecycleOwner) }

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
            onClick = {
                // Grab the frame already on screen — instant, no capture round-trip.
                val bmp = preview?.bitmap ?: return@Button
                val out = newFile()
                out.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
                onCaptured(out)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .height(64.dp)
                .fillMaxWidth(0.7f),
        ) { Text("Capture", style = MaterialTheme.typography.titleLarge) }
    }
}
