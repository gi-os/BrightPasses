package com.gios.lightpass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.gios.lightpass.hw.WheelScroll
import java.io.File

/** Pinch-zoom + pan viewer for the original photo, on black. */
@Composable
fun ZoomableImage(file: File, modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offX by remember { mutableFloatStateOf(0f) }
    var offY by remember { mutableFloatStateOf(0f) }

    /*
     * The pan has no scroll state to hoist, so the wheel gets a scroller that writes
     * straight to the offset. Turning it walks down a zoomed ticket — the one thing you
     * want here and the one thing a thumb on a 3.9" screen is bad at. At 1x there is
     * nothing to pan, so nothing is consumed and the glide stops rather than dragging the
     * whole photo off the edge.
     */
    val wheelPan = remember {
        ScrollableState { delta ->
            if (scale > 1f) {
                offY -= delta
                delta
            } else {
                0f
            }
        }
    }
    WheelScroll(wheelPan)

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    if (scale > 1f) { offX += pan.x; offY += pan.y } else { offX = 0f; offY = 0f }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = file,
            contentDescription = "Original photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer(
                scaleX = scale, scaleY = scale, translationX = offX, translationY = offY,
            ),
        )
    }
}
