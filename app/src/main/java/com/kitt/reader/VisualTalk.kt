package com.kitt.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*

/** One passenger/stopped-use interaction. No URI, image queue, restored picture or automatic attachment. */
class VisualTalk(private val journey: Journey, private val loop: DirectorLoop,
    private val scope: CoroutineScope, private val provider: () -> DirectorProvider) {
    var open by mutableStateOf(false); private set
    var ready by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var notice by mutableStateOf(""); private set
    private var image: ImageInput? = null
    private var job: Job? = null
    private var token = -1L
    private var generation = 0
    fun begin() {
        clear()
        if (!journey.running) return
        loop.cancel(); journey.beginImageInteraction(); token = journey.epoch; open = true
        if (!provider().acceptsImages) notice = UnsupportedImage().message.orEmpty()
    }
    private fun valid() = open && journey.running && journey.epoch == token
    fun select(load: suspend () -> ImageInput) {
        if (!valid()) { clear(); return }
        job?.cancel(); image = null; ready = false; busy = true; notice = "正在读取图片…"
        val sequence = ++generation
        job = scope.launch {
            try {
                if (!provider().acceptsImages) throw UnsupportedImage()
                val selected = withTimeout(15000) { load() }
                ensureActive()
                if (valid() && generation == sequence) { image = selected; ready = true; notice = "图片就绪" }
            } catch (e: TimeoutCancellationException) { if (valid()) notice = "读取图片超时，请重新选择。" }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (valid()) notice = if (e is UnsupportedImage) e.message.orEmpty() else "图片无法读取，请重新选择较小的照片或截图。" }
            finally { if (generation == sequence) busy = false }
        }
    }
    fun send(question: String = "") {
        if (!valid() || !ready) { if (!valid()) clear(); return }
        val attachment = image ?: return
        clear()
        loop.user(question.trim().take(800).ifBlank { "帮我看看这个" }, attachment)
    }
    fun sync() { if (open && !valid()) clear() }
    fun clear() {
        generation++; job?.cancel(); job = null; image = null; ready = false; busy = false; open = false; notice = ""
        if (journey.epoch == token) journey.finishImageInteraction()
        token = -1L
    }
}
