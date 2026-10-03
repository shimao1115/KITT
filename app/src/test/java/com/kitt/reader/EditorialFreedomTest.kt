package com.kitt.reader

import org.junit.Assert.*
import org.junit.Test

/** Guards the editorial reset: the content layer supplies material, never a composition topic. */
val ThesisInTitle = Regex("怎样|如何|为什么|塑造|留下.{0,6}痕迹|改变.{0,8}理解|进入日常生活")

class EditorialFreedomTest {
    private val area = AreaIdentity("测试市", "测试区", "测试镇")

    @Test fun constitutionForbidsTheFixedRhetoricalTemplateAndLandThesis() {
        val text = DirectorContract.constitution
        listOf("眼前切入", "解释一层", "落回眼前", "最值得理解的一件事", "改变对这片土地的理解",
            "道路只是一个视角", "不是整段旅程的主角", "讲清就停", "纪录片导演").forEach {
            assertFalse("constitution still prescribes: $it", it in text)
        }
    }
    @Test fun constitutionStatesFreedomDestinationAndHardBoundaries() {
        val text = DirectorContract.constitution
        listOf("不必每次提问", "不必每次升华", "不必每次总结", "素材架", "形式随素材而定", "OTHER",
            "倾向于开口", "不是黑名单", "用户最新明确意图永远优先", "查不到就删", "不假装用户眼前看到了什么",
            "没有待播队列", "安静是合法选择", "独立机会").forEach { assertTrue("missing: $it", it in text) }
        assertTrue("constitution grew into a manual: ${text.length}", text.length < 4000)
    }
    @Test fun chapterOpensBroadDossierAcrossMaterialTypes() {
        val titles = chapterCandidates(area).candidates.map { it.title }
        listOf("文物保护单位", "非遗", "民俗", "特产", "博物馆", "寺庙", "风景名胜", "工程", "水系",
            "人物", "地名", "当代生活", "古镇").forEach { assertTrue("dossier lacks: $it", titles.any { t -> it in t }) }
        assertTrue(titles.none { ThesisInTitle.containsMatchIn(it) })
        assertTrue(chapterCandidates(area).candidates.any { it.family == TopicFamily.OTHER })
    }
    @Test fun dossierTextPresentsShelfNotPlaylist() {
        val text = chapterCandidates(area).text()
        assertTrue(text.contains("素材架，不是播放清单"))
        assertTrue(text.contains("不规定切入方式"))
        assertFalse(text.contains("最近题材")) // Diversity pressure does not belong on the shelf.
        assertTrue(chapterCandidates(AreaIdentity("德阳市", "广汉市", "")).text().contains("三星堆 / 古蜀文明"))
    }
    @Test fun groundedSubjectsKeepSourceNotesAndNeutralLabels() {
        listOf(AreaIdentity("德阳市", "广汉市", "三星堆镇"), AreaIdentity("德阳市", "绵竹市", "某镇"),
            AreaIdentity("绵阳市", "安州区", "雎水镇")).forEach { place ->
            val grounded = chapterCandidates(place).candidates.filter { it.salience >= 4 }
            assertTrue(grounded.isNotEmpty()); assertTrue(grounded.all { "https://" in it.grounding })
            assertTrue(grounded.none { ThesisInTitle.containsMatchIn(it.title) })
        }
        val guanghan = chapterCandidates(AreaIdentity("德阳市", "广汉市", "三星堆镇")).candidates.filter { "三星堆" in it.title }
        assertTrue(guanghan.size >= 2) // Several valid angles, none mandated.
        assertTrue(guanghan.any { "器物" in it.title })
    }
    @Test fun recentFamilyHistoryIsWeakSignalNotCandidateBlacklist() {
        var time = 1000000L; val voice = TestVoice(); val journey = Journey({ time }, voice); journey.start()
        val pipeline = ContextPipeline()
        journey.location(Fix(30.0, 104.0, time, administrative = area)); pipeline.accept(journey.fix!!)
        journey.deliver(journey.ticket(true), DirectorResult(Action.SPEAK_NOW, "本地水系、山川与地貌", "正文",
            memoryUpdate = "水系", topicFamily = TopicFamily.GEOGRAPHY).json())
        val text = pipeline.card(journey, time)
        assertTrue(text.contains("不是黑名单"))
        assertTrue(text.contains("最近题材】GEOGRAPHY"))
        assertEquals(chapterCandidates(area).candidates.size, pipeline.areas.active!!.candidates.size)
        voice.finish()
    }
    @Test fun unmappedSubjectIsAcceptedByTheStrictContract() {
        val result = DirectorContract.parse(DirectorResult(Action.SPEAK_NOW, "说不清类别的老街", "正文",
            memoryUpdate = "老街", topicFamily = TopicFamily.OTHER).json())
        assertEquals(TopicFamily.OTHER, result.topicFamily)
        assertTrue(DirectorContract.schema.toString().contains("OTHER"))
        assertEquals(8, DirectorContract.keys.size)
    }
    @Test fun strictContractAndFactBoundariesSurviveTheReset() {
        assertTrue(runCatching {
            DirectorContract.parse("""{"action":"SPEAK_NOW","topic":"题","narration":"文","question":"","prepare_hint":"",""" +
                """"memory_update":"","topic_family":"","landmark_id":"","extra":"1"}""")
        }.isFailure)
        assertTrue(runCatching { DirectorContract.parse(DirectorResult(Action.SPEAK_NOW, "题", "").json()) }.isFailure)
        listOf("精确数字、日期、纪录与当前状态必须有依据", "不编造", "一次跳过不是长期偏好",
            "不要在后台问需要即时回答的问题").forEach { assertTrue("lost boundary: $it", it in DirectorContract.constitution) }
    }
}
