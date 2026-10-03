package com.kitt.reader

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(runtime: KittRuntime, notificationEnabled: Boolean, onNotification: () -> Unit, onReturn: () -> Unit) {
    var kind by remember { mutableStateOf(runtime.config.kind) }
    var endpoint by remember { mutableStateOf(runtime.config.endpoint) }
    var model by remember { mutableStateOf(runtime.config.model) }
    var effort by remember { mutableStateOf(runtime.config.effort) }
    var key by remember { mutableStateOf(runtime.config.apiKey) }
    var rate by remember { mutableFloatStateOf(runtime.settings.speechRate) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val account = runtime.chatGpt
    val draft = ProviderConfig(kind, endpoint.trim(), model.trim(), effort, key.trim())
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("设置", style = MaterialTheme.typography.headlineLarge)
        Text("KITT V0 · ${BuildConfig.VERSION_NAME}")
        ProviderKind.entries.forEach { choice ->
            Row {
                RadioButton(kind == choice, { kind = choice }); Text(when (choice) {
                    ProviderKind.CHATGPT -> "ChatGPT 账号（推荐）"
                    ProviderKind.FAKE -> "离线演示（固定内容）"
                    ProviderKind.OPENAI -> "OpenAI Responses"
                    ProviderKind.COMPATIBLE -> "兼容 API"
                }, Modifier.padding(top = 12.dp))
            }
        }
        if (kind == ProviderKind.CHATGPT) {
            val registration = account.record
            LaunchedEffect(registration.clientId, registration.connected) {
                if (registration.planEnabled && account.models.isEmpty() && !account.busy) account.refreshModels()
            }
            LaunchedEffect(account.models) {
                if (account.models.isNotEmpty() && account.models.none { it.slug == model }) {
                    model = account.models.first().slug; effort = ""
                }
            }
            Text(if (registration.connected) "账号：${registration.email.ifBlank { "ChatGPT 用户" }} · ${registration.clientId.takeLast(8)}" else "ChatGPT 未连接")
            Text("ChatGPT 计划使用：${if (registration.planEnabled) "已启用" else "未启用"}")
            Text("授权后发送当前位置概要与本次输入。凭据仅在本机加密保存。")
            Button({
                account.signIn(runtime.scope, consent = registration.connected && !registration.planEnabled) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                }
            }, enabled = !account.busy, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(if (!registration.connected) "Continue with ChatGPT" else if (!registration.planEnabled) "授权 ChatGPT 计划使用" else "重新连接 ChatGPT")
            }
            if (account.busy) TextButton({ account.cancelSignIn() }) { Text("取消授权等待") }
            if (registration.connected) TextButton({
                runtime.loop.cancel(); runtime.journey.invalidateProvider()
                scope.launch { account.disconnect() }
            }, enabled = !account.busy) { Text("断开 ChatGPT") }
            if (registration.planEnabled) {
                TextButton({ scope.launch { account.refreshModels() } }, enabled = !account.busy && !account.catalogLoading) {
                    Text(if (account.catalogLoading) "正在加载账号模型…" else "刷新账号模型")
                }
                var expanded by remember { mutableStateOf(false) }
                val chosen = account.models.find { it.slug == model }
                Box {
                    OutlinedButton({ expanded = true }, enabled = account.models.isNotEmpty()) { Text(chosen?.displayName ?: "等待账号模型列表") }
                    DropdownMenu(expanded, { expanded = false }) {
                        account.models.forEach { item -> DropdownMenuItem(text = { Text(item.displayName) }, onClick = {
                            model = item.slug; effort = ""; expanded = false
                        }) }
                    }
                }
                if (!chosen?.efforts.isNullOrEmpty()) {
                    Text("思考强度（账号模型支持）")
                    Column { (listOf("") + chosen!!.efforts).forEach { value ->
                        FilterChip(effort == value, { effort = value }, { Text(value.ifBlank { "默认" }) })
                    } }
                }
                var testing by remember { mutableStateOf(false) }
                TextButton({
                    testing = true
                    scope.launch {
                        try {
                            val result = ChatGptProvider(account, draft).direct(DirectorRequest(
                                contextCard = "【当前位置】无位置；设置中的连接测试。",
                                userUtterance = "请用一句话确认连接，不描述任何现场。"))
                            message = "Director 请求已完成：${DirectorContract.parse(result).action}（ChatGPT 计划）"
                        } catch (_: Exception) { message = account.message }
                        finally { testing = false }
                    }
                }, enabled = chosen != null && !testing && !account.busy) { Text(if (testing) "正在测试连接…" else "测试 Director 连接") }
            }
            if (account.message.isNotBlank()) Text(account.message)
            TextButton({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/settings/usage"))) }) { Text("ChatGPT 用量与授权设置") }
        } else if (kind != ProviderKind.FAKE) {
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
            val selected = account.models.find { it.slug == model }
            val saved = if (kind == ProviderKind.CHATGPT && (!account.record.planEnabled || selected == null))
                Result.failure(IllegalStateException("请先授权 ChatGPT 计划使用并选择账号模型，或选择其他 Provider。"))
            else runtime.settings.save(if (kind == ProviderKind.CHATGPT) draft.copy(endpoint = ChatGptProtocol.RESOURCE,
                effort = effort.takeIf { it in selected!!.efforts }.orEmpty()) else draft, rate)
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
