package com.Shyam.xplayer

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.videolan.libvlc.MediaPlayer
import kotlinx.coroutines.delay
import kotlin.math.abs

fun formatTime(ms: Long): String {
    if (ms < 0) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

/**
 * Reusable Glass Circle Icon Button from Player 2 design.
 */
@Composable
fun GlassCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    containerColor: Color = Color(0x331E293B),
    borderColor: Color = Color(0x33FFFFFF),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor)
            .border(1.dp, borderColor, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true, color = Color.White),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun XPlayerComposeControls(
    player: MediaPlayer?,
    showUi: Boolean,
    isLocked: Boolean = false, 
    onSwipe: () -> Unit = {},
    onTapScreen: () -> Unit = {},
    onInteract: () -> Unit = {}, 
    onNextClick: () -> Unit = {}, 
    onPrevClick: () -> Unit = {}, 
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    var currentTime by remember { mutableStateOf(0L) }
    var totalTime by remember { mutableStateOf(0L) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var isPlayReadyState by remember { mutableStateOf(player?.isPlaying ?: false) }

    // Feedback overlay states
    var swipeFeedbackText by remember { mutableStateOf("") }
    var showFeedback by remember { mutableStateOf(false) }
    var feedbackTrigger by remember { mutableStateOf(0) }

    // 🟢 Long press 2X Speed state
    var isLongPress2X by remember { mutableStateOf(false) }
    var savedSpeedBeforeLongPress by remember { mutableStateOf(1.0f) }

    LaunchedEffect(player) {
        while (true) {
            if (player != null) {
                if (!isDraggingSlider) {
                    currentTime = player.time.coerceAtLeast(0L)
                }
                totalTime = player.length.coerceAtLeast(0L)
                isPlayReadyState = player.isPlaying 
            }
            delay(500L)
        }
    }

    LaunchedEffect(feedbackTrigger) {
        if (feedbackTrigger > 0) {
            delay(800)
            showFeedback = false
        }
    }

    var swipeDirection by remember { mutableStateOf(0) } 
    var startX by remember { mutableStateOf(0f) }
    var startY by remember { mutableStateOf(0f) }
    var startBrightness by remember { mutableStateOf(0f) }
    var startVolume by remember { mutableStateOf(0) }
    var startSeekPos by remember { mutableStateOf(0L) }
    var targetSeekPos by remember { mutableStateOf(0L) }

    var gestureModifier = Modifier.fillMaxSize().background(Color.Transparent)
    
    if (isLocked) {
        gestureModifier = gestureModifier.pointerInput(isLocked) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                }
            }
        }
    } else {
        gestureModifier = gestureModifier
            .pointerInput(isLocked) {
                detectTapGestures(
                    onTap = { onTapScreen() },
                    onDoubleTap = { offset ->
                        onInteract() 
                        val currentPos = player?.time ?: 0L
                        if (offset.x < size.width / 2) {
                            val target = (currentPos - 10000L).coerceAtLeast(0L)
                            player?.time = target
                            swipeFeedbackText = "⏪ -10s"
                        } else {
                            val target = (currentPos + 10000L).coerceAtMost(totalTime)
                            player?.time = target
                            swipeFeedbackText = "⏩ +10s"
                        }
                        showFeedback = true
                        feedbackTrigger++ 
                    },
                    // 🟢 Long press to play at 2X speed!
                    onLongPress = {
                        if (player?.isPlaying == true) {
                            savedSpeedBeforeLongPress = player.rate
                            player.rate = 2.0f
                            isLongPress2X = true
                        }
                    },
                    onPress = {
                        try {
                            tryAwaitRelease()
                        } finally {
                            if (isLongPress2X) {
                                player?.rate = savedSpeedBeforeLongPress
                                isLongPress2X = false
                            }
                        }
                    }
                )
            }
            .pointerInput(isLocked) {
                detectDragGestures(
                    onDragStart = { offset: Offset ->
                        onSwipe()
                        startX = offset.x
                        startY = offset.y
                        swipeDirection = 0
                        startSeekPos = player?.time ?: 0L
                        targetSeekPos = startSeekPos
                        startBrightness = activity?.window?.attributes?.screenBrightness ?: 0.5f
                        if (startBrightness < 0) startBrightness = 0.5f
                        startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        showFeedback = true
                    },
                    onDragEnd = {
                        showFeedback = false
                        if (swipeDirection == 1) {
                            player?.time = targetSeekPos
                            currentTime = targetSeekPos
                        }
                        swipeDirection = 0
                    },
                    onDragCancel = {
                        showFeedback = false
                        swipeDirection = 0
                    },
                    onDrag = { change: PointerInputChange, _: Offset ->
                        change.consume()
                        val deltaX = change.position.x - startX
                        val deltaY = change.position.y - startY

                        if (swipeDirection == 0) {
                            if (abs(deltaX) > abs(deltaY) && abs(deltaX) > 20) swipeDirection = 1 
                            else if (abs(deltaY) > abs(deltaX) && abs(deltaY) > 20) {
                                if (startX < size.width / 2) swipeDirection = 2 
                                else swipeDirection = 3 
                            }
                        }

                        when (swipeDirection) {
                            1 -> { 
                                val seekFactor = 100L 
                                targetSeekPos = (startSeekPos + (deltaX * seekFactor).toLong()).coerceIn(0L, totalTime)
                                swipeFeedbackText = "${formatTime(targetSeekPos)} / ${formatTime(totalTime)}"
                            }
                            2 -> { 
                                val newBrightness = (startBrightness - (deltaY * 0.002f)).coerceIn(0.01f, 1f)
                                activity?.window?.let { window ->
                                    val lp = window.attributes
                                    lp.screenBrightness = newBrightness
                                    window.attributes = lp
                                }
                                swipeFeedbackText = "Brightness: ${(newBrightness * 100).toInt()}%"
                            }
                            3 -> { 
                                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val newVol = (startVolume - (deltaY * 0.002f * maxVol)).toInt().coerceIn(0, maxVol)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                swipeFeedbackText = "Volume: ${(newVol.toFloat() / maxVol * 100).toInt()}%"
                            }
                        }
                    }
                )
            }
    }

    Box(modifier = modifier.then(gestureModifier)) {
        
        // 🟢 Floating 2X Speed HUD at Top Center
        AnimatedVisibility(
            visible = isLongPress2X,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xD91E293B))
                    .border(1.dp, Color(0x666366F1), RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painterResource(android.R.drawable.ic_media_ff),
                        contentDescription = null,
                        tint = Color(0xFF818CF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "2× Playback Speed",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Gesture feedback overlay (seek, volume, brightness)
        if (showFeedback) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xB3000000))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = swipeFeedbackText,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Bottom Controls Bar with Glass Circle Buttons
        AnimatedVisibility(
            visible = showUi && !isLocked, 
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                
                // 🟢 Previous Button in Glass Circle
                GlassCircleButton(
                    size = 42.dp,
                    onClick = { onInteract(); onPrevClick() }
                ) {
                    Icon(
                        painterResource(android.R.drawable.ic_media_previous),
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
                
                // 🟢 Play / Pause Button in Elegant Glass Circle
                GlassCircleButton(
                    size = 52.dp,
                    containerColor = Color(0x4D1E293B),
                    borderColor = Color(0x4DFFFFFF),
                    onClick = { 
                        onInteract()
                        if (player?.isPlaying == true) {
                            player.pause()
                            isPlayReadyState = false
                        } else {
                            player?.play()
                            isPlayReadyState = true
                        }
                    }
                ) {
                    Icon(
                        painterResource(if (isPlayReadyState) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play), 
                        contentDescription = "Play/Pause", 
                        tint = Color.White,
                        modifier = Modifier.size(28.dp) 
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 🟢 Next Button in Glass Circle
                GlassCircleButton(
                    size = 42.dp,
                    onClick = { onInteract(); onNextClick() }
                ) {
                    Icon(
                        painterResource(android.R.drawable.ic_media_next),
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = formatTime(currentTime),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Slider(
                    value = if (totalTime > 0) currentTime.toFloat() / totalTime.toFloat() else 0f,
                    onValueChange = { percent ->
                        onInteract() 
                        isDraggingSlider = true
                        currentTime = (percent * totalTime).toLong()
                    },
                    onValueChangeFinished = {
                        isDraggingSlider = false
                        player?.time = currentTime
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFFAECBFA), 
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    )
                )

                Text(
                    text = formatTime(totalTime),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
