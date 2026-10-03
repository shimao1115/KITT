package com.kitt.reader

/** Administrative labels are context, never a route or a claim of exact boundaries. */
data class AreaIdentity(val city: String = "", val district: String = "", val chapter: String = "") {
    val key get() = listOf(city, district, chapter).joinToString("/")
    val label get() = listOf(district, chapter).filter(String::isNotBlank).distinct().joinToString(" · ").ifBlank { city }
    companion object {
        fun normalize(city: String?, district: String?, chapter: String?): AreaIdentity? {
            fun clean(value: String?) = value.orEmpty().replace(Regex("\\s+"), "").take(60)
            val c = clean(city); val d = clean(district); val t = clean(chapter).takeUnless { it == d }.orEmpty()
            return AreaIdentity(c, d, t).takeIf { it.label.isNotBlank() }
        }
        fun geocoderFields(locality: String?, subAdmin: String?, subLocality: String?): AreaIdentity? {
            val fields = listOf(locality, subAdmin, subLocality).map { it.orEmpty().trim() }
            val chapter = fields.firstOrNull { it.endsWith("镇") || it.endsWith("乡") || it.endsWith("街道") }.orEmpty()
            val district = fields.firstOrNull { it.endsWith("区") || it.endsWith("县") || it.endsWith("旗") }
                ?: if (fields[0].endsWith("市") && fields[1].endsWith("市") && fields[0] != fields[1]) fields[0]
                else fields[1].takeUnless { it == chapter }.orEmpty()
            val city = fields.take(2).firstOrNull { it.isNotBlank() && it != district && it != chapter }.orEmpty()
            return normalize(city, district, chapter.ifBlank { fields[2].takeUnless { it == district }.orEmpty() })
        }
    }
}

enum class TopicFamily { GEOGRAPHY, TRANSPORT, EVERYDAY_LIFE, HISTORY, HISTORIC_SETTLEMENT,
    HERITAGE, CULTURAL_SITE, CULTURAL_GEOGRAPHY, ECONOMY, PEOPLE }
data class NarrativeCandidate(val title: String, val family: TopicFamily, val grounding: String,
    val salience: Int = 1)
data class AreaCard(val area: AreaIdentity, val orientation: String, val candidates: List<NarrativeCandidate>) {
    fun ranked(recent: List<TopicFamily>) = candidates.sortedByDescending {
        it.salience * 3 - recent.takeLast(3).count { family -> family == it.family } * 2
    }
    fun text(recent: List<TopicFamily>) = buildString {
        appendLine("【区域章节】区县背景：${area.district.ifBlank { area.city }}；基本章节：${area.chapter.ifBlank { "平台未提供镇乡街道" }}")
        appendLine(orientation)
        ranked(recent).forEach { appendLine("候选 ${it.family}：${it.title}；依据：${it.grounding}；价值：${it.salience}") }
        appendLine("最近题材：${recent.joinToString().ifBlank { "无" }}。这是候选池，不是播放清单；边界变化不要求开口。")
    }
}

/** Small session cache of editorial lenses, not a POI database. Unknown local specifics stay questions. */
class AreaCards(private val generate: (AreaIdentity) -> AreaCard = ::chapterCandidates) {
    private val cache = linkedMapOf<String, AreaCard>()
    var active: AreaCard? = null; private set
    var transitions = 0; private set
    val size get() = cache.size
    fun accept(area: AreaIdentity?) {
        if (area?.key == active?.area?.key) return
        if (area == null) { active = null; return }
        active = cache.getOrPut(area.key) { generate(area) }; transitions++
    }
    fun clear() { cache.clear(); active = null; transitions = 0 }
}

fun chapterCandidates(area: AreaIdentity): AreaCard {
    val candidates = mutableListOf<NarrativeCandidate>()
    if (area.district == "广汉市") candidates += NarrativeCandidate("三星堆与古蜀文化：遗物怎样改变对这片土地的理解",
        TopicFamily.HERITAGE, "稳定广为人知的广汉关联；三星堆博物馆 https://www.sxd.cn/；不推断近在眼前，不含日期数字开放状态", 5)
    if (area.district == "绵竹市") candidates += NarrativeCandidate("绵竹木版年画：印版与手工上色怎样进入日常生活",
        TopicFamily.CULTURAL_GEOGRAPHY, "稳定地方关联；绵竹市政府孝德镇年画村报道 https://www.mz.gov.cn/gk/zfxxgk/fdzdnr/cdgz/1594150.htm；不推断眼前可见或当前活动", 4)
    if (area.district == "安州区" && area.chapter == "雎水镇") {
        candidates += NarrativeCandidate("雎水太平桥与踩桥民俗：一座桥怎样成为地方生活的节点",
            TopicFamily.CULTURAL_GEOGRAPHY, "稳定地方关联；四川统一战线 https://www.sctyzx.gov.cn/my/202409/54308962.html；不推断正在举办民俗活动", 4)
        candidates += NarrativeCandidate("沙汀故居与地方文学：作家怎样留下乡土生活的视角",
            TopicFamily.PEOPLE, "同一官方地方报道明确故居关联；不猜生平年份、故事细节或眼前位置", 3)
    }
    // Lenses invite the same Director to discover stable associations; they do not assert unverified local facts.
    candidates += NarrativeCandidate("${area.label}的地方史与时代变化", TopicFamily.HISTORY, "选题方向；只采用高置信稳定关联，不确定具体事实删除", 2)
    candidates += NarrativeCandidate("老街古镇与聚落留下的空间痕迹", TopicFamily.HISTORIC_SETTLEMENT, "选题方向；无已核验古镇名")
    candidates += NarrativeCandidate("地名、习俗与文化地理", TopicFamily.CULTURAL_GEOGRAPHY, "选题方向；地名来源不可猜测")
    candidates += NarrativeCandidate("博物馆、寺庙与文化名胜", TopicFamily.CULTURAL_SITE, "选题方向；采用高置信稳定关联，不推断可见或开放")
    candidates += NarrativeCandidate("人物与地方故事", TopicFamily.PEOPLE, "选题方向；只有人物和本地关联可靠时采用")
    candidates += NarrativeCandidate("农业、产业与日常饮食生活", TopicFamily.ECONOMY, "选题方向；不得虚构当地特产或产业纪录")
    candidates += NarrativeCandidate("街巷与当代生活", TopicFamily.EVERYDAY_LIFE, "一般生活视角；不把当代居民当作历史样本")
    candidates += NarrativeCandidate("地形水系怎样塑造土地使用", TopicFamily.GEOGRAPHY, "一般机制；现场具体水系未知")
    candidates += NarrativeCandidate("道路与人们的日常联系", TopicFamily.TRANSPORT, "一般机制；不猜具体道路工程")
    return AreaCard(area, "${area.city} / ${area.label}。区县提供背景，镇乡街道组织章节；GPS 坐标和方向见当前位置。", candidates)
}

/** One pending lookup, >=60s between attempts even after failure; movement or 5min staleness refreshes. */
class GeocodeThrottle {
    private var attempted: Fix? = null
    private var at: Long? = null
    var pending = false; private set
    fun begin(fix: Fix, time: Long): Boolean {
        if (pending || !fix.valid()) return false
        val elapsed = at?.let { time - it }
        if (elapsed != null && (elapsed < 60000 || (elapsed < 300000 && attempted!!.distanceTo(fix) < 1000))) return false
        at = time; attempted = fix; pending = true; return true
    }
    fun complete() { pending = false }
}
