package com.example.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

class HapticSyncEngine(
    private val context: Context
) {
    private val pendingTransients = ConcurrentLinkedQueue<Long>()
    private var syncJob: Job? = null

    private val _hapticTriggerEvents = MutableSharedFlow<Long>(extraBufferCapacity = 16)
    val hapticTriggerEvents: SharedFlow<Long> = _hapticTriggerEvents.asSharedFlow()

    @Volatile
    var isHapticsEnabled: Boolean = true

    @Volatile
    var isHardwareActive: Boolean = false

    fun onTransientDetected(transientMediaTimeMs: Long) {
        if (!isHapticsEnabled || isHardwareActive) return
        pendingTransients.add(transientMediaTimeMs)
    }

    fun startSyncLoop(scope: CoroutineScope, playerProvider: () -> ExoPlayer?) {
        stopSyncLoop()
        syncJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val player = playerProvider()
                if (player != null && player.isPlaying && isHapticsEnabled && !isHardwareActive) {
                    val currentPos = player.currentPosition
                    val iterator = pendingTransients.iterator()

                    while (iterator.hasNext()) {
                        val transientTimeMs = iterator.next()
                        // Motor Latency Compensation: Trigger 30ms early
                        val targetTriggerTime = transientTimeMs - 30L

                        if (currentPos >= targetTriggerTime) {
                            if (currentPos < transientTimeMs + 200L) {
                                triggerHapticPulse()
                                _hapticTriggerEvents.tryEmit(System.currentTimeMillis())
                            }
                            iterator.remove()
                        } else if (targetTriggerTime - currentPos > 2000L) {
                            // Transient is far ahead, break iteration
                            break
                        }
                    }
                }
                delay(5) // Fast 5ms monitoring interval
            }
        }
    }

    fun stopSyncLoop() {
        syncJob?.cancel()
        syncJob = null
        clearTransients()
    }

    fun clearTransients() {
        pendingTransients.clear()
    }

    private fun triggerHapticPulse() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator ?: context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.let { v ->
                    if (v.hasVibrator()) {
                        v.vibrate(VibrationEffect.createOneShot(35L, 220))
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.let { v ->
                    if (v.hasVibrator()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            v.vibrate(VibrationEffect.createOneShot(35L, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            v.vibrate(35L)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            // Silently swallow vibration errors if permission or hardware fails
        }
    }
}
