package com.yourteam.smartcare.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class VibrationHelper(private val context: Context) {

    // Optimized for newer Android versions (API 31+) using VibratorManager
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun vibrate(durationMillis: Long = 500) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE)
            vib.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(durationMillis)
        }
    }

    fun vibratePattern(pattern: LongArray, repeat: Int = -1) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(pattern, repeat)
            vib.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(pattern, repeat)
        }
    }

    fun cancel() {
        vibrator?.cancel()
    }
}