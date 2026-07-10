package com.yourteam.smartcare.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

class VibrationHelper(private val context: Context) {

    private val vibrator: Vibrator? = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    fun vibrate(durationMillis: Long = 500) {
        // Create a LOCAL variable. This makes Kotlin 100% sure it's not null.
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE)
            vib.vibrate(effect) // NO RED LINE!
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(durationMillis) // NO RED LINE!
        }
    }

    fun vibratePattern(pattern: LongArray, repeat: Int = -1) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(pattern, repeat)
            vib.vibrate(effect) // NO RED LINE!
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(pattern, repeat) // NO RED LINE!
        }
    }

    fun cancel() {
        vibrator?.cancel()
    }
}