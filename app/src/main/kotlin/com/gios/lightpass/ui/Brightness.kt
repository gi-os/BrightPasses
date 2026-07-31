package com.gios.lightpass.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Full brightness, and the screen kept awake, for as long as this is in the composition.
 *
 * A window attribute rather than `Settings.System.SCREEN_BRIGHTNESS`: no permission, it applies
 * the instant the overlay appears, and the window manager gives it back on its own if the app
 * dies holding it. Writing the system setting would change the phone's own brightness and leave
 * it changed — the app has [com.gios.lightpass.util.Grayscale]'s secure-settings grant and could,
 * which is exactly why it shouldn't.
 *
 * The LPIII panel is dim by design, and it is the difference between the scanner reading the
 * code and a queue forming behind you. `FLAG_KEEP_SCREEN_ON` goes with it because the phone's
 * screen timeout is short and the code is on screen precisely when nobody is touching it.
 */
@Composable
fun BrightWhileVisible() {
    val window = (LocalContext.current as? Activity)?.window ?: return
    DisposableEffect(window) {
        // Normally BRIGHTNESS_OVERRIDE_NONE (-1f), meaning "whatever the user's slider says".
        // Saved and put back rather than assumed, so nothing here decides that for them.
        val previous = window.attributes.screenBrightness
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = previous }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
