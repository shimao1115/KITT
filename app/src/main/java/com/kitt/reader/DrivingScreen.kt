package com.kitt.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun DrivingScreen(journey: Journey, sourceLabel: String, onStart: () -> Unit, onSpeak: () -> Unit,
    onEnd: () -> Unit, onSettings: () -> Unit, onDeveloper: () -> Unit, voiceDetail: VoiceDetail = VoiceDetail(),
    onRouteImage: () -> Unit = {}, routeNotice: String = "", onClearRoute: () -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f)) {
                    Row { TextButton(onDeveloper) { Text("路上读山河") }; TextButton(onSettings) { Text("设置") } }
                    Text(sourceLabel, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                    Text(stateLabel(journey), style = MaterialTheme.typography.headlineSmall)
                    Text(placeLabel(journey), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    VoiceIndicator(journey.state, voiceDetail, Modifier.fillMaxWidth().height(64.dp))
                    Text(journey.topic, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (journey.isQuiet) Text(quietLabel(journey))
                    if (journey.notice.isNotBlank()) Text(journey.notice, maxLines = 2)
                }
                Column(Modifier.weight(1.2f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                    DrivingControls(journey, onStart, onSpeak, onEnd, onRouteImage, routeNotice, onClearRoute)
                }
            }
        } else PortraitDrivingScreen(journey, sourceLabel, onStart, onSpeak, onEnd, onSettings, onDeveloper, voiceDetail,
            onRouteImage, routeNotice, onClearRoute)
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
private fun quietLabel(journey: Journey) = if (journey.quietRemaining == Long.MAX_VALUE) "等你叫我" else
    "剩余 ${journey.quietRemaining / 60000}:${((journey.quietRemaining / 1000) % 60).toString().padStart(2, '0')}"

@Composable
private fun PortraitDrivingScreen(journey: Journey, sourceLabel: String, onStart: () -> Unit, onSpeak: () -> Unit,
    onEnd: () -> Unit, onSettings: () -> Unit, onDeveloper: () -> Unit, voiceDetail: VoiceDetail,
    onRouteImage: () -> Unit, routeNotice: String, onClearRoute: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onDeveloper) { Text("路上读山河", style = MaterialTheme.typography.titleLarge) }
            Spacer(Modifier.weight(1f)); TextButton(onSettings) { Text("设置") }
        }
        Text(sourceLabel, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Text(stateLabel(journey), style = MaterialTheme.typography.headlineMedium)
        Text(placeLabel(journey), maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        VoiceIndicator(journey.state, voiceDetail, Modifier.fillMaxWidth().height(80.dp))
        if (journey.isQuiet) {
            Text(quietLabel(journey), style = MaterialTheme.typography.headlineSmall)
        } else Text(journey.topic, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (journey.notice.isNotBlank()) Text(journey.notice, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        DrivingControls(journey, onStart, onSpeak, onEnd, onRouteImage, routeNotice, onClearRoute)
    }
}

@Composable
private fun DrivingControls(journey: Journey, onStart: () -> Unit, onSpeak: () -> Unit, onEnd: () -> Unit,
    onRouteImage: () -> Unit, routeNotice: String, onClearRoute: () -> Unit) {
    Column {
        if (!journey.running) {
            TextButton(onRouteImage, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("添加路线参考图（可选）") }
            if (routeNotice.isNotBlank()) {
                Text(routeNotice, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                TextButton(onClearRoute) { Text("清除路线参考") }
            }
            Button(onStart, Modifier.fillMaxWidth().height(88.dp)) { Text("开始读山河", style = MaterialTheme.typography.titleLarge) }
        } else {
            Button(if (journey.isQuiet) journey::resume else onSpeak, Modifier.fillMaxWidth().height(88.dp)) {
                Text(if (journey.isQuiet) "结束安静" else "说点什么", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(journey::skip, Modifier.weight(1f).height(64.dp)) { Text("跳过") }
                OutlinedButton({ journey.quiet() }, Modifier.weight(1f).height(64.dp)) { Text("安静一会儿") }
            }
            TextButton(onEnd, Modifier.fillMaxWidth().height(56.dp)) { Text("结束旅程") }
        }
    }
}
