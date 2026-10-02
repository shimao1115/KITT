package com.kitt.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EndScreen(summary: TripSummary, store: TripStore, onReturn: () -> Unit) {
    var ratings by remember(summary.started) { mutableStateOf(List(5) { 3 }) }
    var feedback by remember(summary.started) { mutableStateOf("") }
    var saved by remember(summary.started) { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("这一程，读过的山河", style = MaterialTheme.typography.headlineMedium)
        Text("旅程意图：${summary.destination}")
        Text("时长约 ${((summary.ended - summary.started) / 60000).coerceAtLeast(0)} 分钟 · 跳过 ${summary.skipped} 次")
        Text(summary.topics.joinToString("、").ifBlank { "这一程以安静相伴。" }, Modifier.padding(vertical = 16.dp))
        listOf("内容值得听吗？", "开口时机合适吗？", "长短合适吗？", "讲得清楚/可信吗？", "整体体验怎么样？").forEachIndexed { index, label ->
            Text(label)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (1..5).forEach { score ->
                    FilterChip(ratings[index] == score, { ratings = ratings.toMutableList().also { it[index] = score }; saved = false }, { Text("$score") })
                }
            }
        }
        OutlinedTextField(feedback, { feedback = it.take(800); saved = false }, Modifier.fillMaxWidth(), label = { Text("想说的话（可选）") })
        Button({ saved = store.feedback(summary.started, ratings, feedback); error = if (saved) "" else store.lastError.orEmpty() }, Modifier.fillMaxWidth().height(64.dp)) { Text(if (saved) "已保存在本机" else "保存评分") }
        if (error.isNotBlank()) Text(error)
        TextButton(onReturn, Modifier.fillMaxWidth().height(56.dp)) { Text("回到出发页") }
    }
}
