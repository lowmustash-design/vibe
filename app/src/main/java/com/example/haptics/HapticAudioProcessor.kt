package com.example.haptics

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.sqrt

class HapticAudioProcessor(
    private val onTransientDetected: (transientMediaTimeMs: Long) -> Unit
) : BaseAudioProcessor() {

    @Volatile
    var isHapticsEnabled: Boolean = true

    @Volatile
    var isHardwareHapticsActive: Boolean = false

    @Volatile
    private var baseMediaTimeMs: Long = 0L

    private var processedFrames: Long = 0L
    private var sampleRate: Int = 44100
    private var channelCount: Int = 2
    private var bytesPerFrame: Int = 4

    private var lastTransientTimeMs: Long = -1L
    private val minTransientIntervalMs: Long = 80L

    fun updateBaseMediaTime(mediaTimeMs: Long) {
        baseMediaTimeMs = mediaTimeMs
        processedFrames = 0L
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        bytesPerFrame = if (channelCount > 0) channelCount * 2 else 4
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val count = inputBuffer.remaining()
        if (count == 0) return

        // 1. Instant Passthrough to Output Buffer
        // CRITICAL: Prevent ExoPlayer thread blocking (The 00:00 Freeze).
        // Pass buffer immediately to output so master clock never stalls.
        val outputBuffer = replaceOutputBuffer(count)
        val analysisBuffer = inputBuffer.duplicate()
        analysisBuffer.order(inputBuffer.order())

        outputBuffer.put(inputBuffer)
        outputBuffer.flip()

        // 2. Fast PCM Analysis for Bass Transient Detection
        val totalBufferFrames = count / bytesPerFrame
        if (!isHapticsEnabled || isHardwareHapticsActive || sampleRate <= 0 || totalBufferFrames <= 0) {
            processedFrames += totalBufferFrames
            return
        }

        analyzePcmBuffer(analysisBuffer, totalBufferFrames)
    }

    private fun analyzePcmBuffer(buffer: ByteBuffer, totalBufferFrames: Int) {
        try {
            val currentStreamOffsetMs = (processedFrames * 1000L) / sampleRate

            // Process in 256-frame (~5-6ms) chunk windows
            val windowFrames = 256
            var frameOffset = 0

            while (frameOffset < totalBufferFrames && buffer.remaining() >= 2) {
                val framesToRead = minOf(windowFrames, totalBufferFrames - frameOffset)
                var sumSquare = 0.0
                var sampleCount = 0

                for (i in 0 until framesToRead) {
                    for (ch in 0 until channelCount) {
                        if (buffer.remaining() >= 2) {
                            val sampleShort = buffer.short
                            val sampleNorm = sampleShort / 32768.0
                            sumSquare += sampleNorm * sampleNorm
                            sampleCount++
                        }
                    }
                }

                if (sampleCount > 0) {
                    val rms = sqrt(sumSquare / sampleCount)
                    // RMS Threshold for Bass / Impact Transients
                    if (rms > 0.28) {
                        val frameIndex = frameOffset + (framesToRead / 2)
                        val windowOffsetMs = (frameIndex * 1000L) / sampleRate
                        val transientTimestampMs = baseMediaTimeMs + currentStreamOffsetMs + windowOffsetMs

                        if (lastTransientTimeMs < 0 || (transientTimestampMs - lastTransientTimeMs) >= minTransientIntervalMs) {
                            lastTransientTimeMs = transientTimestampMs
                            onTransientDetected(transientTimestampMs)
                        }
                    }
                }
                frameOffset += framesToRead
            }
        } catch (t: Throwable) {
            // Guarantee ExoPlayer thread safety against any buffer underflow or analysis error
        }

        processedFrames += totalBufferFrames
    }

    override fun onFlush() {
        super.onFlush()
        processedFrames = 0L
        lastTransientTimeMs = -1L
    }

    override fun onReset() {
        super.onReset()
        processedFrames = 0L
        lastTransientTimeMs = -1L
    }
}
