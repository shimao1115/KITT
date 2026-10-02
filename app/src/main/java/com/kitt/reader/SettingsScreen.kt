package com.kitt.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(runtime: KittRuntime, notificationEnabled: Boolean, onNotification: () -> Unit, onReturn: () -> Unit) {
    var kind by remember { mutableStateOf(runtime.config.kind) }
    var endpoint by remember { mutableStateOf(runtime.config.endpoint) }
    var model by remember { mutableStateOf(runtime.config.model) }
    var effort by remember { mutableStateOf(runtime.config.effort) }
    var key by remember { mutableStateOf(runtime.config.apiKey) }
    var rate by remember { mutableFloatStateOf(runtime.settings.speechRate) }
    var message by remember { mutableStateOf("") }
    val draft = ProviderConfig(kind, endpoint.trim(), model.trim(), effort, key.trim())
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("设置", style = MaterialTheme.typography.headlineLarge)
        Text("KITT V0 · ${BuildConfig.VERSION_NAME}")
        ProviderKind.entries.forEach { choice ->
            Row {
                RadioButton(kind == choice, { kind = choice }); Text(when (choice) {
                    ProviderKind.FAKE -> "离线演示（固定内容）"
                    ProviderKind.OPENAI -> "OpenAI Responses"
                    ProviderKind.COMPATIBLE -> "兼容 API"
                }, Modifier.padding(top = 12.dp))
            }
        }
        if (kind != ProviderKind.FAKE) {
            Text(if (key.isBlank()) "认证状态：未配置" else "认证状态：已有本机 key（连接需测试）")
            Text("请求包含当前位置概要与本次输入。密钥只在本机加密保存。")
            OutlinedTextField(endpoint, { endpoint = it }, Modifier.fillMaxWidth(), label = { Text("API 地址（HTTPS，含 /v1）") }, singleLine = true)
            OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("模型") }, singleLine = true)
            OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("API key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            if (draft.supportsEffort) {
                Text("思考强度（Adapter 能力提示，以模型实际支持为准）")
                Row { listOf("", "low", "medium", "high").forEach { value ->
                    FilterChip(effort == value, { effort = value }, { Text(value.ifBlank { "默认" }) })
                } }
            }
            if (runtime.settings.credentialUnavailable) Text("本机密钥无法解密，请重新填写。")
        } else Text("无需登录。演示内容用于闭环验收，真实 AI 内容质量需接通 Provider 后验证。")
        Spacer(Modifier.height(12.dp)); Text("语音速度 ${"%.1f".format(rate)}×")
        Slider(rate, { rate = it }, valueRange = 0.5f..1.5f)
        TextButton(onNotification) { Text("通知：${if (notificationEnabled) "已允许" else "未允许"} · 系统设置") }
        Button({
            val saved = runtime.settings.save(draft, rate)
            if (saved.isSuccess) {
                runtime.loop.cancel(); runtime.journey.invalidateProvider()
                runtime.config = runtime.settings.read(); (runtime.voice as? AndroidVoice)?.speechRate = rate
                runtime.revision.intValue++; message = "已保存"
            } else message = saved.exceptionOrNull()?.message ?: "暂时无法保存"
        }, Modifier.fillMaxWidth().height(64.dp)) { Text("保存") }
        if (message.isNotBlank()) Text(message)
        TextButton(onReturn, Modifier.fillMaxWidth().height(56.dp)) { Text("返回") }
    }
}
