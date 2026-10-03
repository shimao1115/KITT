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

/** OTHER keeps the editorial field open: a worthwhile subject is never dropped for lacking a neat category. */
enum class TopicFamily { GEOGRAPHY, TRANSPORT, EVERYDAY_LIFE, HISTORY, HISTORIC_SETTLEMENT,
    HERITAGE, CULTURAL_SITE, CULTURAL_GEOGRAPHY, ECONOMY, PEOPLE, OTHER }

/** A subject worth knowing about this place — never a thesis, an angle, or a sentence outline. */
data class NarrativeCandidate(val title: String, val family: TopicFamily, val grounding: String = "",
    val salience: Int = 1)

data class AreaCard(val area: AreaIdentity, val orientation: String, val candidates: List<NarrativeCandidate>) {
    fun text() = buildString {
        appendLine("【本章素材架】区县背景：${area.district.ifBlank { area.city }}；基本章节：${area.chapter.ifBlank { "平台未提供镇乡街道" }}")
        appendLine(orientation)
        candidates.forEach {
            append("素材：${it.title}（${it.family}；价值${it.salience}")
            if (it.grounding.isNotBlank()) append("；依据：${it.grounding}")
            appendLine("）")
        }
        appendLine("这是素材架，不是播放清单：顺序、类别和条数都不构成要求，可以任选一条、把几条真正相关的串起来，也可以在确实没有值得讲的东西时保持安静。")
        appendLine("标题只标明可讲的对象，不规定切入方式、结构、深度或结论；形式按素材本身决定。")
        appendLine("未标依据的素材只是方向提示，具体当地事实必须可靠；无依据的数字、日期和现状删去。")
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

/**
 * Material shelf for one chapter: neutral subject labels, open-ended and unordered.
 * Grounded local entities carry source notes; generic slots only name a category to look into.
 */
fun chapterCandidates(area: AreaIdentity): AreaCard {
    val candidates = mutableListOf<NarrativeCandidate>()
    if (area.district == "广汉市") candidates += listOf(
        NarrativeCandidate("三星堆 / 古蜀文明", TopicFamily.HERITAGE,
            "稳定广为人知的广汉关联；三星堆博物馆 https://www.sxd.cn/", 5),
        NarrativeCandidate("三星堆代表性器物与考古发现", TopicFamily.HERITAGE,
            "三星堆博物馆 https://www.sxd.cn/；具体年代、尺寸与出土细节需可靠依据", 4),
        NarrativeCandidate("广汉与成都平原的古代文明背景", TopicFamily.HISTORY, "", 3),
    )
    if (area.district == "绵竹市") candidates += listOf(
        NarrativeCandidate("绵竹木版年画 / 年画村", TopicFamily.CULTURAL_GEOGRAPHY,
            "稳定地方关联；绵竹市政府孝德镇年画村报道 https://www.mz.gov.cn/gk/zfxxgk/fdzdnr/cdgz/1594150.htm", 4),
        NarrativeCandidate("年画印版与手工上色工艺", TopicFamily.CULTURAL_GEOGRAPHY,
            "绵竹市政府 https://www.mz.gov.cn/gk/zfxxgk/fdzdnr/cdgz/1594150.htm；不推断当前活动", 3),
    )
    if (area.district == "安州区" && area.chapter == "雎水镇") candidates += listOf(
        NarrativeCandidate("雎水太平桥与踩桥民俗", TopicFamily.CULTURAL_GEOGRAPHY,
            "稳定地方关联；四川统一战线 https://www.sctyzx.gov.cn/my/202409/54308962.html；不推断正在举办活动", 4),
        NarrativeCandidate("沙汀故居与地方文学", TopicFamily.PEOPLE,
            "四川统一战线 https://www.sctyzx.gov.cn/my/202409/54308962.html 明确故居关联；不猜生平年份与细节", 3),
    )
    candidates += listOf(
        NarrativeCandidate("地方历史与建置变迁", TopicFamily.HISTORY),
        NarrativeCandidate("古镇、老街与传统聚落", TopicFamily.HISTORIC_SETTLEMENT),
        NarrativeCandidate("各级文物保护单位与历史建筑", TopicFamily.HERITAGE),
        NarrativeCandidate("遗址、考古发现与博物馆", TopicFamily.CULTURAL_SITE),
        NarrativeCandidate("寺庙、宗教场所与地方信仰", TopicFamily.CULTURAL_SITE),
        NarrativeCandidate("风景名胜、观景点与自然遗产", TopicFamily.CULTURAL_SITE),
        NarrativeCandidate("文学、艺术与地方文化人物", TopicFamily.PEOPLE),
        NarrativeCandidate("传说、轶闻与有依据的趣闻", TopicFamily.CULTURAL_GEOGRAPHY),
        NarrativeCandidate("非遗项目与民间工艺", TopicFamily.CULTURAL_GEOGRAPHY),
        NarrativeCandidate("民俗、节庆与集市", TopicFamily.CULTURAL_GEOGRAPHY),
        NarrativeCandidate("方言与地名由来", TopicFamily.CULTURAL_GEOGRAPHY),
        NarrativeCandidate("本地水系、山川与地貌", TopicFamily.GEOGRAPHY),
        NarrativeCandidate("桥梁、隧道、水利与铁路工程", TopicFamily.TRANSPORT),
        NarrativeCandidate("地方道路与交通", TopicFamily.TRANSPORT),
        NarrativeCandidate("特产、饮食与物产", TopicFamily.ECONOMY),
        NarrativeCandidate("农业、工业与贸易", TopicFamily.ECONOMY),
        NarrativeCandidate("街巷与当代生活", TopicFamily.EVERYDAY_LIFE),
        NarrativeCandidate("本章其他有依据而有趣的线索", TopicFamily.OTHER),
    )
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
