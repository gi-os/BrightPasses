package com.lightpass.reader

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightLazyScrollView
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import java.io.File

private const val PASS_ROW_HEIGHT = 3f

@Composable
fun PassList(
    passes: List<PassEntity>,
    emptyMessage: String,
    onOpen: (PassEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (passes.isEmpty()) {
        EmptyState(emptyMessage, modifier)
        return
    }
    LightLazyScrollView(
        modifier = modifier,
        uniformItemHeightGridUnits = PASS_ROW_HEIGHT,
    ) {
        items(passes, key = { it.id }) { pass -> PassRow(pass, onOpen) }
    }
}

@Composable
private fun PassRow(pass: PassEntity, onOpen: (PassEntity) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(PASS_ROW_HEIGHT.gridUnitsAsDp())
            .lightClickable(onClickLabel = "Open pass", role = Role.Button) { onOpen(pass) }
            .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.45f.gridUnitsAsDp()),
        verticalArrangement = Arrangement.Center,
    ) {
        LightText(pass.movieTitle, variant = LightTextVariant.Copy, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val sub = listOfNotNull(pass.theater, pass.date, pass.time, pass.seat?.let { "Seat $it" }).joinToString(" · ")
        if (sub.isNotBlank()) {
            LightText(sub, variant = LightTextVariant.Detail, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Full-screen ORIGINAL image. Decoded straight from disk — no downscaling of the stored file. */
@Composable
fun FullscreenPass(imagePath: String, modifier: Modifier = Modifier) {
    val bitmap = remember(imagePath) {
        runCatching { BitmapFactory.decodeFile(imagePath) }.getOrNull()
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LightThemeTokens.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Pass",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LightText("Image missing", variant = LightTextVariant.Copy)
        }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LightThemeTokens.colors.background)
            .padding(2f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        LightText(message, variant = LightTextVariant.Copy)
    }
}
