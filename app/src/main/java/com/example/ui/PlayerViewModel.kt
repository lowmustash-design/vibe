package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.haptics.HapticAudioProcessor
import com.example.haptics.HapticSyncEngine
import com.example.haptics.HardwareHapticManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@UnstableApi
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    val hapticSyncEngine = HapticSyncEngine(application)
    val hardwareHapticManager = HardwareHapticManager()

    lateinit var hapticAudioProcessor: HapticAudioProcessor
        private set

    var exoPlayer: ExoPlayer? = null
        private set

    private val _isHapticsEnabled = MutableStateFlow(true)
    val isHapticsEnabled: StateFlow<Boolean> = _isHapticsEnabled.asStateFlow()

    private val _isHardwareHapticsActive = MutableStateFlow(false)
    val isHardwareHapticsActive: StateFlow<Boolean> = _isHardwareHapticsActive.asStateFlow()

    private val _currentMediaTitle = MutableStateFlow("Sample Video (Bass Test)")
    val currentMediaTitle: StateFlow<String> = _currentMediaTitle.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _lastHapticTime = MutableStateFlow(0L)
    val lastHapticTime: StateFlow<Long> = _lastHapticTime.asStateFlow()

    // Default high quality bass sample video
    private val defaultVideoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

    init {
        initializeProcessorAndEngine()
        setupPositionTicker()
        observeHapticPulseEvents()
    }

    private fun initializeProcessorAndEngine() {
        hapticAudioProcessor = HapticAudioProcessor { transientMediaTimeMs ->
            hapticSyncEngine.onTransientDetected(transientMediaTimeMs)
        }
    }

    fun getOrCreatePlayer(): ExoPlayer {
        exoPlayer?.let { return it }

        val context = getApplication<Application>()

        val audioSink = DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(hapticAudioProcessor))
            .build()

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return audioSink
            }
        }

        val player = ExoPlayer.Builder(context, renderersFactory).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ALL
            addListener(object : Player.Listener {
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    val newPosMs = newPosition.positionMs
                    hapticAudioProcessor.updateBaseMediaTime(newPosMs)
                    hapticSyncEngine.clearTransients()
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    val active = hardwareHapticManager.attachAudioSession(
                        audioSessionId,
                        _isHapticsEnabled.value
                    )
                    _isHardwareHapticsActive.value = active
                    hapticSyncEngine.isHardwareActive = active
                    hapticAudioProcessor.isHardwareHapticsActive = active
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        _duration.value = duration.coerceAtLeast(0L)
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("PlayerViewModel", "ExoPlayer playback error: ${error.message}", error)
                }
            })
        }

        exoPlayer = player
        hapticSyncEngine.startSyncLoop(viewModelScope) { exoPlayer }

        // Load initial video
        playMedia(Uri.parse(defaultVideoUrl), "Sample Video (Bass Test)")

        return player
    }

    fun playMedia(uri: Uri, title: String? = null) {
        val player = exoPlayer ?: getOrCreatePlayer()
        _currentMediaTitle.value = title ?: uri.lastPathSegment ?: "Local Video"

        val mediaItem = MediaItem.fromUri(uri)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        hapticAudioProcessor.updateBaseMediaTime(0L)
        hapticSyncEngine.clearTransients()
    }

    fun toggleHaptics(enabled: Boolean) {
        _isHapticsEnabled.value = enabled
        hapticAudioProcessor.isHapticsEnabled = enabled
        hapticSyncEngine.isHapticsEnabled = enabled

        exoPlayer?.audioSessionId?.let { sessionId ->
            val active = hardwareHapticManager.attachAudioSession(sessionId, enabled)
            _isHardwareHapticsActive.value = active
            hapticSyncEngine.isHardwareActive = active
            hapticAudioProcessor.isHardwareHapticsActive = active
        }

        if (!enabled) {
            hapticSyncEngine.clearTransients()
        }
    }

    fun playPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.let { player ->
            player.seekTo(positionMs)
            _currentPosition.value = positionMs
            hapticAudioProcessor.updateBaseMediaTime(positionMs)
            hapticSyncEngine.clearTransients()
        }
    }

    private fun setupPositionTicker() {
        viewModelScope.launch {
            while (true) {
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        _currentPosition.value = player.currentPosition.coerceAtLeast(0L)
                        _duration.value = player.duration.coerceAtLeast(0L)
                    }
                }
                delay(200)
            }
        }
    }

    private fun observeHapticPulseEvents() {
        viewModelScope.launch {
            hapticSyncEngine.hapticTriggerEvents.collect { time ->
                _lastHapticTime.value = time
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        hapticSyncEngine.stopSyncLoop()
        hardwareHapticManager.release()
        exoPlayer?.release()
        exoPlayer = null
    }
}
