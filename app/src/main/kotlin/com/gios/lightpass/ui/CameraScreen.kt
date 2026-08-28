package com.gios.lightpass.ui

import android.graphics.Bitmap
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Shoot the whole stack, then leave.
 *
 * The camera used to take one photograph and navigate away, so four tickets to one game meant
 * four trips through ADD. It now stays up for as long as you keep pressing: each frame is
 * handed off and the shutter is free again immediately, and DONE is what ends the burst.
 *
 * Two things had to change for that to be usable rather than merely possible. The JPEG encode
 * moved off the main thread — it was happening inside onClick, and a full-resolution encode
 * between the finger and the next frame is most of what "it lags after three shots" was. And
 * the reading of those shots is serialised in the view model, so three photographs no longer
 * mean three model calls, three decodes and three full-size bitmaps competing with the preview
 * for the same small phone.
 */
@Composable
fun CameraScreen(
    shotsPending: Int,
    burstProgress: Float,
    onShot: (Bitmap) -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }
    var preview by remember { mutableStateOf<PreviewView?>(null) }
    // Counted here rather than in the view model because it is about this visit to the camera:
    // how many times the finger went down, including shots still waiting to be read.
    var shotsTaken by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { controller.bindToLifecycle(lifecycleOwner) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.controller = controller
                    preview = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Top left: how many are still being read, Sony-style. Top right: the way out.
        if (shotsPending > 0) {
            Box(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                BufferIndicator(shotsPending, burstProgress)
            }
        }
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
            Text("DONE", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (shotsTaken > 0) {
                Text(
                    // Said plainly, because the grouping is not obvious from a viewfinder: these
                    // are going onto one shelf entry, not becoming four separate tickets.
                    if (shotsTaken == 1) "1 SHOT · ONE STACK"
                    else "$shotsTaken SHOTS · ONE STACK",
                    color = Color(0xFFB0B0B0), style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = {
                    // PreviewView.bitmap is already a snapshot copy, so it is safe to hand
                    // straight over; everything expensive about it happens elsewhere.
                    val bmp = preview?.bitmap ?: return@Button
                    shotsTaken++
                    onShot(bmp)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White, contentColor = Color.Black,
                ),
                modifier = Modifier.height(64.dp).fillMaxWidth(0.7f),
            ) {
                Text(if (shotsTaken == 0) "Capture" else "Capture next",
                    style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

/**
 * The buffer, drawn the way a Sony draws it: a thin vertical bar exactly as tall as the numeral
 * beside it, filling from the bottom as the queue drains, with the count going down rather than up.
 *
 * A spinner would say "something is happening"; this says how much is left, which is the only
 * question anyone has while standing at a door holding a phone.
 */
@Composable
private fun BufferIndicator(remaining: Int, progress: Float) {
    val style = MaterialTheme.typography.titleLarge
    // Tied to the font's own size so the bar tracks the numeral if the type scale ever moves.
    val barHeight = with(LocalDensity.current) { style.fontSize.toDp() }
    val fill by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 450),
        label = "buffer",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(4.dp).height(barHeight).background(Color(0x66FFFFFF)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // One child of the track, so the fraction is of the track and nothing compounds.
            if (fill > 0f) Box(Modifier.fillMaxWidth().fillMaxHeight(fill).background(Color.White))
        }
        Spacer(Modifier.width(6.dp))
        Text("$remaining", style = style, color = Color.White)
    }
}
