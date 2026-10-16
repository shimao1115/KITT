package com.kitt.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun ExplorePlaces(runtime: KittRuntime, onReturn: () -> Unit) {
    val context = LocalContext.current
    val catalog = remember { ExploreCatalog.load(context) }
    val voice = remember { AndroidVoice(context).apply {
        offlineRequired = { true }
        speechRate = runtime.settings.speechRate; voiceName = runtime.settings.voiceName
    } }
    val playback = remember { ExplorePlayback(voice) { voice.offlineReady } }
    DisposableEffect(voice) { onDispose { voice.close() } }
    ExploreScreen(catalog, playback, onReturn)
}

@Composable
fun ExploreScreen(catalog: ExploreCatalog, playback: ExplorePlayback, onReturn: () -> Unit) {
    var regionKey by rememberSaveable { mutableStateOf("") }
    var objectTitle by rememberSaveable { mutableStateOf("") }
    var storyId by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    val region = catalog.regions.find { it.key == regionKey }
    val obj = region?.pack?.objects?.find { it.title == objectTitle }
    val story = region?.storiesFor(objectTitle)?.find { it.id == storyId }
    val focus = LocalFocusManager.current
    val back: () -> Unit = {
        playback.stop(); focus.clearFocus()
        when {
            storyId.isNotEmpty() -> storyId = ""
            objectTitle.isNotEmpty() -> objectTitle = ""
            regionKey.isNotEmpty() -> { regionKey = ""; query = "" }
            else -> onReturn()
        }
    }
    BackHandler(onBack = back)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, playback) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) playback.stop()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); playback.stop() }
    }
    LaunchedEffect(regionKey, objectTitle, storyId) { playback.stop() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 900.dp).fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 16.dp)) {
        TextButton(back, Modifier.heightIn(min = 48.dp).testTag("explore-back")) {
            Text(if (regionKey.isEmpty()) "返回首页" else "返回")
        }
        Text(story?.title ?: obj?.title ?: region?.pack?.area?.district ?: "探索地点",
            style = MaterialTheme.typography.headlineSmall)
        if (region != null && obj == null) {
            OutlinedTextField(query, { query = it.take(160) }, Modifier.fillMaxWidth().testTag("explore-search"),
                label = { Text("搜索地方、对象或正文") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                trailingIcon = { if (query.isNotEmpty()) TextButton({ query = "" }) { Text("清除") } })
        }
        key(regionKey, objectTitle, storyId) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("explore-content"),
                contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when {
                    story != null -> {
                        item { Text("完整故事 · ${if (story.type == StoryType.main) "主篇" else "延伸篇"} · ${story.category.label()}",
                            style = MaterialTheme.typography.labelLarge) }
                        item { SelectionContainer { Text(story.text, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag("explore-story-text")) } }
                        item { Text("故事来源 · 本次未联网查询", style = MaterialTheme.typography.titleMedium) }
                        items(story.sourceLinks) { (title, url) -> SourceText(title, url) }
                    }
                    obj != null -> {
                        item { Text("基础事实 · 未归类 · ${obj.facts.size} 条\n来源检索日期 ${region.pack.retrievedAt}；本次未联网查询。") }
                        item { Text("仅关联本区，不代表当前位置；暂无可信镇街目录与对象精确位置。", style = MaterialTheme.typography.bodyMedium) }
                        items(obj.facts, key = { it.id }) { fact ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SelectionContainer { Text(fact.summary, style = MaterialTheme.typography.bodyLarge) }
                                fact.sourceIds.forEach { id ->
                                    region.pack.sources.find { it.id == id }?.let { SourceText(it.title, it.url) }
                                }
                            }
                        }
                        item { Text("完整故事", style = MaterialTheme.typography.titleLarge) }
                        val available = region.storiesFor(obj.title)
                        if (available.isEmpty()) item { Text("此对象暂无审核通过的完整故事；以上为独立基础事实。") }
                        items(available, key = { it.id }) { article ->
                            OutlinedButton({ playback.stop(); storyId = article.id }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                Text("${if (article.type == StoryType.main) "主篇" else "延伸篇"} · ${article.title}")
                            }
                        }
                    }
                    region != null -> {
                        item { Text("${region.pack.area.fullName}\n${region.pack.objects.size} 个对象 · ${region.factCount} 条基础事实 · ${region.stories.size} 篇完整故事") }
                        item { Text(region.storyStatus + "\n暂无可信镇街目录；按区县查看对象。") }
                        region.stories.groupingBy { it.category }.eachCount().forEach { (category, count) ->
                            item { Text("${category.label()} · $count 篇完整故事", style = MaterialTheme.typography.labelLarge) }
                        }
                        val found = region.search(query)
                        if (found.isEmpty()) item { Text("没有匹配内容。可换个关键词；未安装地区暂不可浏览。") }
                        items(found, key = { it.title }) { entry ->
                            OutlinedButton({ focus.clearFocus(); playback.stop(); objectTitle = entry.title },
                                Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(entry.title, style = MaterialTheme.typography.titleMedium)
                                    Text("基础事实 · 未归类 · ${entry.facts.size} 条", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    else -> {
                        item { Text("离线阅读 · 无需定位、登录或开始旅程", style = MaterialTheme.typography.bodyLarge) }
                        item { Text("已安装地区", style = MaterialTheme.typography.titleMedium) }
                        if (catalog.regions.isEmpty()) item { Text("没有可读取的已安装资料，文件可能缺失或校验失败。请检查应用资料。") }
                        items(catalog.regions, key = { it.key }) { entry ->
                            OutlinedButton({ playback.stop(); regionKey = entry.key }, Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(entry.pack.area.district, style = MaterialTheme.typography.titleLarge)
                                    Text(entry.pack.area.fullName)
                                    Text("${entry.pack.objects.size} 个对象 · ${entry.factCount} 条基础事实 · ${entry.stories.size} 篇完整故事")
                                }
                            }
                        }
                        item { Text("仅显示已安装且校验通过的地区。其它地区尚不可用。") }
                    }
                }
            }
        }
        if (story != null) {
            if (playback.message.isNotBlank()) Text(playback.message, Modifier.testTag("explore-voice-message"))
            val active = playback.state == ExploreSpeech.PREPARING || playback.state == ExploreSpeech.PLAYING
            Button({ if (active) playback.stop() else playback.play(story) },
                Modifier.fillMaxWidth().padding(vertical = 8.dp).heightIn(min = 56.dp).testTag("explore-play")) {
                Text(if (active) "停止朗读" else "播放完整故事")
            }
        }
    }
    }
}

@Composable
private fun SourceText(title: String, url: String) {
    SelectionContainer { Text("来源：$title\n$url", style = MaterialTheme.typography.bodySmall) }
}

private fun StoryCategory.label() = when (this) {
    StoryCategory.NATURE -> "山河地理生态"
    StoryCategory.ENGINEERING -> "交通与工程"
    StoryCategory.CULTURE -> "人文文化社会"
    StoryCategory.LOCAL_LIFE -> "物产产业生活"
    StoryCategory.HISTORY -> "历史与人物"
    StoryCategory.TRAVEL -> "旅行现场与常识"
}
