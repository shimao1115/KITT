package com.kitt.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun DrivingScreen(journey: Journey, sourceLabel: String, onStart: () -> Unit, onSpeak: () -> Unit,
    onEnd: () -> Unit, onSettings: () -> Unit, onDeveloper: () -> Unit, voiceDetail: VoiceDetail = VoiceDetail(),
    onRouteImage: () -> Unit = {}, routeNotice: String = "", onClearRoute: () -> Unit = {}, onVisualTalk: () -> Unit = {},
    transcript: TranscriptText = TranscriptText(), onCancelReply: () -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableHeight = maxHeight
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f)) {
                    Row { TextButton(onDeveloper) { Text("路上读山河") }; TextButton(onSettings) { Text("设置") } }
                    Text(sourceLabel, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                    JourneyStatus(journey)
                    Text(placeLabel(journey), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    VoiceIndicator(journey.state, voiceDetail, Modifier.fillMaxWidth().height(64.dp))
                    Text(if (journey.isQuiet) quietLabel(quietRemainingNow(journey)) else journey.topic, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                    if (journey.notice.isNotBlank()) Text(journey.notice, maxLines = 2, modifier = Modifier.testTag("journey-notice"))
                    ReplySurface(journey, transcript, onCancelReply, compact = availableHeight < 400.dp)
                }
                Column(Modifier.weight(1.2f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                    DrivingControls(journey, onStart, onSpeak, onEnd, onRouteImage, routeNotice, onClearRoute, onVisualTalk, compact = availableHeight < 400.dp)
                }
            }
        } else PortraitDrivingScreen(journey, sourceLabel, onStart, onSpeak, onEnd, onSettings, onDeveloper, voiceDetail,
            onRouteImage, routeNotice, onClearRoute, onVisualTalk, compact = maxHeight < 700.dp,
            transcript = transcript, onCancelReply = onCancelReply)
    }
}

private fun stateLabel(journey: Journey) = when (journey.state) {
    JourneyState.IDLE -> "开车上路后点一下开始"
    JourneyState.READING -> "读山河中"
    JourneyState.SPEAKING -> "正在讲述"
    JourneyState.LISTENING -> "正在听，请说话"
    JourneyState.QUIET -> "安静模式"
}
private fun placeLabel(journey: Journey) = journey.fix?.let {
    it.administrative?.label ?: it.area.ifBlank {
        java.lang.String.format(java.util.Locale.ROOT, "GPS %.3f, %.3f", it.latitude, it.longitude)
    }
} ?: if (journey.running) "等待可靠位置" else "其余的，跟它说就行。"
private fun quietLabel(remaining: Long) = if (remaining == Long.MAX_VALUE) "等你叫我" else
    "剩余 ${remaining / 60000}:${((remaining / 1000) % 60).toString().padStart(2, '0')}"

/**
 * `quietUntil` is an absolute deadline, so no observed value changes while the seconds pass and the
 * countdown would freeze at whatever it first showed. Re-read the remaining time from composition.
 */
@Composable
private fun quietRemainingNow(journey: Journey): Long {
    val observed = journey.quietRemaining
    var remaining by remember(journey.isQuiet) { mutableLongStateOf(observed) }
    LaunchedEffect(journey.isQuiet) {
        if (!journey.isQuiet) return@LaunchedEffect
        remaining = journey.quietRemaining
        while (journey.isQuiet) { delay(1000); remaining = journey.quietRemaining }
    }
    return if (journey.isQuiet) remaining else observed
}

@Composable
private fun JourneyStatus(journey: Journey) {
    val accent = when (journey.state) {
        JourneyState.SPEAKING -> MaterialTheme.colorScheme.tertiary
        JourneyState.LISTENING -> MaterialTheme.colorScheme.primary
        JourneyState.QUIET -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = accent.copy(alpha = 0.10f), shape = RoundedCornerShape(12.dp),
        modifier = Modifier.testTag("journey-state-${journey.state.name}")) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(8.dp).background(accent, CircleShape))
            Text(stateLabel(journey), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PortraitDrivingScreen(journey: Journey, sourceLabel: String, onStart: () -> Unit, onSpeak: () -> Unit,
    onEnd: () -> Unit, onSettings: () -> Unit, onDeveloper: () -> Unit, voiceDetail: VoiceDetail,
    onRouteImage: () -> Unit, routeNotice: String, onClearRoute: () -> Unit, onVisualTalk: () -> Unit, compact: Boolean,
    transcript: TranscriptText, onCancelReply: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onDeveloper) { Text("路上读山河", style = MaterialTheme.typography.titleLarge) }
            Spacer(Modifier.weight(1f)); TextButton(onSettings) { Text("设置") }
        }
        Text(sourceLabel, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
        JourneyStatus(journey)
        Text(placeLabel(journey), maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        VoiceIndicator(journey.state, voiceDetail, Modifier.fillMaxWidth().height(if (compact) 48.dp else 80.dp))
        if (journey.isQuiet) {
            Text(quietLabel(quietRemainingNow(journey)), style = MaterialTheme.typography.headlineSmall)
        } else Text(journey.topic, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (journey.notice.isNotBlank()) Text(journey.notice, maxLines = 2, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("journey-notice"))
        ReplySurface(journey, transcript, onCancelReply, compact)
        Spacer(Modifier.weight(1f))
        DrivingControls(journey, onStart, onSpeak, onEnd, onRouteImage, routeNotice, onClearRoute, onVisualTalk, compact)
    }
}

/**
 * The one-shot exchange as one surface: live recognition text plus a typed way to answer.
 * Recognition text is coloured as speech and notices render elsewhere, so a backend error can never
 * look like something the user said. Interim text is display-only — only 发送 or a real final
 * transcript submits, and submitting closes the exchange so nothing can submit twice.
 */
@Composable
private fun ReplySurface(journey: Journey, transcript: TranscriptText, onCancelReply: () -> Unit, compact: Boolean) {
    if (!journey.awaitingReply) return
    var draft by remember { mutableStateOf("") }
    // Submitting releases the microphone first, so a late transcript cannot overwrite or duplicate this answer.
    val submit: () -> Unit = {
        val text = draft.trim()
        if (text.isNotBlank()) { draft = ""; journey.stopListeningForTyping(); journey.submitReply(text) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
        if (transcript.present) Text(
            (if (transcript.final) "你说：" else "在听：") + transcript.text,
            maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("spoken-transcript"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(draft, { draft = it.take(400) },
                // Reaching for the keyboard hands the microphone back before anything is typed.
                Modifier.weight(1f).testTag("typed-reply")
                    .onFocusEvent { if (it.hasFocus) journey.stopListeningForTyping() },
                label = { Text("或打字回答") }, placeholder = { Text("想说但不方便开口时") },
                maxLines = if (compact) 1 else 2, textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { submit() }))
            Button(submit, Modifier.testTag("send-reply"), enabled = draft.isNotBlank()) { Text("发送") }
        }
        TextButton(onCancelReply, Modifier.testTag("cancel-reply")) { Text("取消") }
    }
}

@Composable
private fun DrivingControls(journey: Journey, onStart: () -> Unit, onSpeak: () -> Unit, onEnd: () -> Unit,
    onRouteImage: () -> Unit, routeNotice: String, onClearRoute: () -> Unit, onVisualTalk: () -> Unit, compact: Boolean = false) {
    Column {
        if (!journey.running) {
            TextButton(onRouteImage, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("添加路线参考图（可选）") }
            if (routeNotice.isNotBlank()) {
                Text(routeNotice, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                TextButton(onClearRoute) { Text("清除路线参考") }
            }
            Button(onStart, Modifier.fillMaxWidth().height(if (compact) 80.dp else 88.dp), shape = RoundedCornerShape(20.dp)) { Text("开始读山河", style = MaterialTheme.typography.titleLarge) }
        } else {
            Button(if (journey.isQuiet) journey::resume else onSpeak, Modifier.fillMaxWidth().height(if (compact) 80.dp else 88.dp), shape = RoundedCornerShape(20.dp)) {
                Text(if (journey.isQuiet) "结束安静" else "说点什么", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(journey::skip, Modifier.weight(1f).height(if (compact) 56.dp else 64.dp), shape = RoundedCornerShape(16.dp)) { Text("跳过") }
                OutlinedButton({ journey.quiet() }, Modifier.weight(1f).height(if (compact) 56.dp else 64.dp), shape = RoundedCornerShape(16.dp)) { Text("安静一会儿") }
            }
            Row {
                TextButton(onVisualTalk, Modifier.weight(1f).height(if (compact) 48.dp else 56.dp)) { Text("旅途看图", color = MaterialTheme.colorScheme.secondary) }
                TextButton(onEnd, Modifier.weight(1f).height(if (compact) 48.dp else 56.dp)) { Text("结束旅程") }
            }
        }
    }
}
