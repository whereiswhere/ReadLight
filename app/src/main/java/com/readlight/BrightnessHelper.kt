package com.readlight

import android.content.Context
import android.provider.Settings

object BrightnessHelper {
    private const val BRIGHTNESS_STEP = 15
    private const val MIN_BRIGHTNESS = 20
    private const val MAX_BRIGHTNESS = 255
    private const val COOLDOWN_MS = 50L

    private var lastBrightnessTime = 0L

    fun canWriteSettings(context: Context): Boolean {
        return Settings.System.canWrite(context)
    }

    fun handleScrollKey(context: Context, keyCode: Int) {
        val now = System.currentTimeMillis()
        if (now - lastBrightnessTime > COOLDOWN_MS) {
            lastBrightnessTime = now
            if (keyCode == LightPhoneKeys.SCROLL_UP) increaseBrightness(context)
            else decreaseBrightness(context)
        }
    }

    fun increaseBrightness(context: Context) {
        if (!canWriteSettings(context)) return

        try {
            val current = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )
            val newValue = (current + BRIGHTNESS_STEP).coerceAtMost(MAX_BRIGHTNESS)
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                newValue
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun decreaseBrightness(context: Context) {
        if (!canWriteSettings(context)) return

        try {
            val current = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )
            val newValue = (current - BRIGHTNESS_STEP).coerceAtLeast(MIN_BRIGHTNESS)
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                newValue
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
