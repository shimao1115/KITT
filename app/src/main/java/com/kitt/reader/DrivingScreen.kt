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
    onEnd: () -> Unit, onSettings: () -> Unit, onDeveloper: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onDeveloper) { Text("路上读山河", style = MaterialTheme.typography.titleLarge) }
            Spacer(Modifier.weight(1f)); TextButton(onSettings) { Text("设置") }
        }
        Text(sourceLabel, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Text(when (journey.state) {
            JourneyState.IDLE -> "开车上路后点一下开始"
            JourneyState.READING -> "读山河中"
            JourneyState.SPEAKING -> "正在讲述"
            JourneyState.LISTENING -> "正在听"
            JourneyState.QUIET -> "安静模式"
        }, style = MaterialTheme.typography.headlineMedium)
        Text(journey.fix?.area?.ifBlank { "GPS 已定位" } ?: if (journey.running) "等待可靠位置" else "其余的，跟它说就行。",
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        if (journey.isQuiet) {
            Text(if (journey.quietRemaining == Long.MAX_VALUE) "等你叫我" else
                "剩余 ${journey.quietRemaining / 60000}:${((journey.quietRemaining / 1000) % 60).toString().padStart(2, '0')}",
                style = MaterialTheme.typography.headlineSmall)
        } else Text(journey.topic, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (journey.notice.isNotBlank()) Text(journey.notice, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        if (!journey.running) {
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
