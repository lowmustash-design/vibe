package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.ui.components.PlayerControlsOverlay
import kotlinx.coroutines.delay

@UnstableApi
@Composable
fun VideoPlayerScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val currentTitle by viewModel.currentMediaTitle.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isHapticsEnabled by viewModel.isHapticsEnabled.collectAsState()
    val isHardwareActive by viewModel.isHardwareHapticsActive.collectAsState()
    val currentPositionMs by viewModel.currentPosition.collectAsState()
    val durationMs by viewModel.duration.collectAsState()
    val lastHapticTime by viewModel.lastHapticTime.collectAsState()

    var isControlsVisible by remember { mutableStateOf(true) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                viewModel.exoPlayer?.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Auto-hide controls overlay after 3.5s of inactivity
    LaunchedEffect(isControlsVisible, isPlaying) {
        if (isControlsVisible && isPlaying) {
            delay(3500)
            isControlsVisible = false
        }
    }

    // Local Video File Picker Launcher
    val openVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    selectedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Silently ignore if persistable permission is not granted
            }
            viewModel.playMedia(selectedUri)
            isControlsVisible = true
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF1C1B1F),
        floatingActionButton = {
            AnimatedVisibility(
                visible = isControlsVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        openVideoLauncher.launch(arrayOf("video/*"))
                    },
                    containerColor = Color(0xFFD0BCFF),
                    contentColor = Color(0xFF381E72),
                    shape = RoundedCornerShape(16.dp),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .padding(bottom = 80.dp, end = 8.dp)
                        .testTag("open_video_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoFile,
                        contentDescription = "Open Local Video"
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF1C1B1F))
        ) {
            // Native ExoPlayer View
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false // Custom Compose Controls Overlay
                        player = viewModel.getOrCreatePlayer()
                    }
                },
                update = { playerView ->
                    playerView.player = viewModel.exoPlayer
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("exo_player_view")
            )

            // Compose Player Controls Overlay
            PlayerControlsOverlay(
                isVisible = isControlsVisible,
                title = currentTitle,
                isPlaying = isPlaying,
                isHapticsEnabled = isHapticsEnabled,
                isHardwareActive = isHardwareActive,
                lastHapticTime = lastHapticTime,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                onPlayPauseToggle = {
                    viewModel.playPause()
                    isControlsVisible = true
                },
                onSeek = { pos ->
                    viewModel.seekTo(pos)
                    isControlsVisible = true
                },
                onHapticsToggle = { enabled ->
                    viewModel.toggleHaptics(enabled)
                    isControlsVisible = true
                },
                onOverlayClick = {
                    isControlsVisible = !isControlsVisible
                },
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
