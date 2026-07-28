package com.gios.lightpass.ui

import androidx.compose.foundation.background
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
import java.io.File

/** Pinch-zoom + pan viewer for the original photo, on black. */
@Composable
fun ZoomableImage(file: File, modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offX by remember { mutableFloatStateOf(0f) }
    var offY by remember { mutableFloatStateOf(0f) }
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
