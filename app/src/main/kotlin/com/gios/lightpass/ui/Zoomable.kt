package com.gios.lightpass.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.gios.light.common.hw.WheelScroll
import java.io.File

/** Pinch-zoom + pan viewer for the original photo, on black. */
@Composable
fun ZoomableImage(file: File, modifier: Modifier = Modifier) {
    ZoomableSurface(modifier, Color.Black) { layer ->
        AsyncImage(
            model = file,
            contentDescription = "Original photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().then(layer),
        )
    }
}

/**
 * The same viewer over a bitmap we drew ourselves — the generated booking code.
 *
 * On white, because a code held up on a black field is a code a scanner won't find, and at a
 * fixed [width] by [height] rather than filling the box, because the bitmap was generated at
 * exactly those pixels and any scaling undoes that. [FilterQuality.None] keeps a magnified module
 * square instead of blurring it into grey, which is the difference between zooming in to read the
 * code and zooming in to ruin it.
 */
@Composable
fun ZoomableBitmap(
    bitmap: ImageBitmap,
    contentDescription: String,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    ZoomableSurface(modifier, Color.White) { layer ->
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.None,
            modifier = Modifier.size(width, height).then(layer),
        )
    }
}

/**
 * Pinch, pan and wheel, shared by both viewers.
 *
 * The [content] is handed the `graphicsLayer` to apply itself rather than being wrapped in it,
 * because the two callers disagree about sizing — the photo fills the screen, the barcode must
 * not — and that decision belongs to them.
 */
@Composable
private fun ZoomableSurface(
    modifier: Modifier,
    background: Color,
    content: @Composable (Modifier) -> Unit,
) {
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
            .background(background)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    if (scale > 1f) { offX += pan.x; offY += pan.y } else { offX = 0f; offY = 0f }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        content(
            Modifier.graphicsLayer(
                scaleX = scale, scaleY = scale, translationX = offX, translationY = offY,
            ),
        )
    }
}
