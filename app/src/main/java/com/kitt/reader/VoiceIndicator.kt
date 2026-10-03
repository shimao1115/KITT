package com.kitt.reader

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.abs

@Composable
fun VoiceIndicator(state: JourneyState, detail: VoiceDetail, modifier: Modifier = Modifier) {
    when (voiceVisual(state)) {
        VoiceVisual.IDLE -> Unit
        VoiceVisual.LISTENING -> {
            val level by animateFloatAsState(if (detail.phase == VoicePhase.LISTENING) detail.level else 0f,
                tween(100), label = "microphone level")
            val color = MaterialTheme.colorScheme.primary
            Canvas(modifier.testTag("listening-indicator").semantics {
                contentDescription = "正在听，请说话"
                stateDescription = when (detail.phase) {
                    VoicePhase.PREPARING_LISTEN -> "正在准备麦克风"
                    VoicePhase.PROCESSING -> "正在识别"
                    else -> "语音强度 ${(detail.level * 100).toInt()}%"
                }
            }) {
                val gap = size.width / 13f; val width = gap * 0.6f
                repeat(7) { index ->
                    val shape = 1f - abs(index - 3) * 0.15f
                    val height = size.height * (0.12f + level * 0.78f * shape)
                    drawRoundRect(color, Offset(size.width / 2 + (index - 3) * gap - width / 2,
                        (size.height - height) / 2), Size(width, height), CornerRadius(width / 2))
                }
            }
        }
        VoiceVisual.SPEAKING -> {
            val transition = rememberInfiniteTransition(label = "speaking scanner")
            val position by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing),
                RepeatMode.Reverse), label = "scanner position")
            val color = MaterialTheme.colorScheme.tertiary
            Canvas(modifier.testTag("speaking-indicator").semantics { contentDescription = "正在讲述，语音播放中" }) {
                val width = size.width * 0.8f; val start = size.width * 0.1f
                drawRoundRect(color.copy(alpha = 0.18f), Offset(start, size.height * 0.42f),
                    Size(width, size.height * 0.16f), CornerRadius(size.height))
                drawRoundRect(color, Offset(start + position * width * 0.7f, size.height * 0.35f),
                    Size(width * 0.3f, size.height * 0.3f), CornerRadius(size.height))
            }
        }
    }
}
