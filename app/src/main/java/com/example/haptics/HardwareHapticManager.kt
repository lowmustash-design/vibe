package com.example.haptics

import android.media.audiofx.HapticGenerator
import android.os.Build
import android.util.Log

class HardwareHapticManager {

    private var hapticGenerator: HapticGenerator? = null

    @Volatile
    var isHardwareHapticsActive: Boolean = false
        private set

    fun attachAudioSession(audioSessionId: Int, isEnabled: Boolean): Boolean {
        release()
        if (!isEnabled || audioSessionId <= 0) {
            isHardwareHapticsActive = false
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (HapticGenerator.isAvailable()) {
                    val generator = HapticGenerator.create(audioSessionId)
                    generator.enabled = true
                    hapticGenerator = generator
                    isHardwareHapticsActive = true
                    Log.d("HardwareHapticManager", "Attached HapticGenerator to audioSession $audioSessionId")
                    return true
                }
            } catch (t: Throwable) {
                Log.w("HardwareHapticManager", "Failed to create HapticGenerator: ${t.message}")
            }
        }
        isHardwareHapticsActive = false
        return false
    }

    fun setEnabled(enabled: Boolean) {
        try {
            hapticGenerator?.enabled = enabled
        } catch (e: Exception) {
            Log.w("HardwareHapticManager", "Failed to set HapticGenerator enabled: ${e.message}")
        }
    }

    fun release() {
        try {
            hapticGenerator?.enabled = false
            hapticGenerator?.release()
        } catch (e: Exception) {
            // Silently ignore release errors
        } finally {
            hapticGenerator = null
            isHardwareHapticsActive = false
        }
    }
}
