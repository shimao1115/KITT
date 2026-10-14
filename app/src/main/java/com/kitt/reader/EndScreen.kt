package com.kitt.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EndScreen(summary: TripSummary, onReturn: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("这一程，读过的山河", style = MaterialTheme.typography.headlineMedium)
        summary.destination.trim().takeUnless { it.isBlank() || it in setOf("未询问", "未提供", "等待回答") }
            ?.let { Text("旅程意图：$it") }
        Text("时长约 ${((summary.ended - summary.started) / 60000).coerceAtLeast(0)} 分钟 · 跳过 ${summary.skipped} 次")
        Text(summary.topics.joinToString("、").ifBlank { "这一程以安静相伴。" }, Modifier.padding(vertical = 16.dp))
        TextButton(onReturn, Modifier.fillMaxWidth().height(56.dp)) { Text("回到出发页") }
    }
}
