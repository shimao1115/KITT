package com.kitt.reader

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun VisualTalkDialog(runtime: KittRuntime) {
    val context = LocalContext.current
    val visual = runtime.visualTalk
    var question by remember { mutableStateOf("") }
    var captureUri by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf("") }
    val capture = File(context.cacheDir, "visual-talk/capture.jpg")
    DisposableEffect(Unit) { onDispose { capture.delete(); runtime.visualTalk.clear() } }
    fun read(uri: Uri, camera: Boolean) {
        visual.select {
            withContext(Dispatchers.IO) {
                try { AndroidImageReader.read(context, uri) } finally { if (camera) capture.delete() }
            }
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) read(uri, false)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && captureUri != null) read(captureUri!!, true) else capture.delete()
        captureUri = null
    }
    AlertDialog(onDismissRequest = visual::clear,
        title = { Text("旅途看图") }, text = {
            Column {
                Text("由乘客操作，或停车后使用。图片仅用于这次问答。")
                TextButton({
                    try {
                        capture.parentFile?.mkdirs()
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.images", capture)
                        captureUri = uri; camera.launch(uri)
                    } catch (_: ActivityNotFoundException) { capture.delete(); error = "系统相机不可用，请从相册选择。" }
                    catch (_: Exception) { capture.delete(); error = "相机暂时无法打开，请从相册选择。" }
                }, enabled = !visual.busy && runtime.provider().acceptsImages) { Text("拍照") }
                TextButton({ gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !visual.busy && runtime.provider().acceptsImages) { Text("从相册选择") }
                if (visual.notice.isNotBlank()) Text(visual.notice)
                if (error.isNotBlank()) Text(error)
                OutlinedTextField(question, { question = it.take(800) }, label = { Text("想问什么（可选）") }, maxLines = 3)
            }
        }, confirmButton = { TextButton({ visual.send(question) }, enabled = visual.ready && !visual.busy) { Text("帮我看看这个") } },
        dismissButton = { TextButton(visual::clear) { Text("取消") } })
}
