package com.gios.lightpass.util

import android.content.Context
import android.provider.Settings

/**
 * Lifts the Light Phone's system grayscale (accessibility color-correction / daltonizer)
 * while a pass is on screen, then restores it. Requires WRITE_SECURE_SETTINGS, granted once via:
 *   adb shell pm grant com.gios.lightpass android.permission.WRITE_SECURE_SETTINGS
 * If not granted, calls fail silently and the pass just shows in grayscale.
 */
object Grayscale {
    private const val ENABLED = "accessibility_display_daltonizer_enabled"
    private var savedEnabled: Int? = null

    fun colorOn(context: Context) {
        runCatching {
            val cr = context.contentResolver
            savedEnabled = Settings.Secure.getInt(cr, ENABLED, 0)
            Settings.Secure.putInt(cr, ENABLED, 0)
        }
    }

    fun restore(context: Context) {
        runCatching {
            val cr = context.contentResolver
            Settings.Secure.putInt(cr, ENABLED, savedEnabled ?: 1)
        }
    }
}
