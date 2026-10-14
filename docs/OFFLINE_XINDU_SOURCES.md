# 新都离线包来源与审校（V0.4.0）

包 `xindu-2026.10.14-1`：6个对象、22条稳定事实；检索/审校日期2026-10-14。全部事实在 `app/src/main/assets/offline/xindu-facts.json`，每条有独立ID、coverage和source_ids；来源含机构、页面日期/出版期、URL与审校注记。范围只有四川省/成都市/新都区，不证明用户位于对象旁边，不包含对象精确坐标、街道边界、天气/票价/开放/路况。

| 来源ID | 机构 / 原始来源 | 使用范围与审校取舍 |
| --- | --- | --- |
| yang | [中央纪委监察部：杨升庵纪念地桂湖](https://m.ccdi.gov.cn/content/cb/21/7409.html) | 杨慎身份、1511状元、1524流放、著述；桂湖纪念关联、植桂、道光布局、祠/书屋/黄峨馆。页面首段1559与末段1599卒年冲突，因此完全排除卒年；页面未显示完整年份，保留原日期不猜。 |
| donghu | [名山区纪委监委：东湖清风徐徐来](https://www.msjw.gov.cn/news/LZJY/20171221/1712211012474332593D118EG77B600.html)，2017-12-18 | 李德裕开湖的历史文献记载、卫公别称、三贤身份及建炎建堂；不采用“唯一”“最完整”等排他性称号，也不推断现存建筑全部为唐宋原构。 |
| scu | [四川大学建筑与环境学院：宝光寺调研](https://acem.scu.edu.cn/info/1004/12879.htm)，2024-09-25 | 建筑组成、2024测量、舍利塔上部倾斜/错位研究及纠倾讨论。只报道历史研究，不推断现在危险程度、修复完成或是否可入内。 |
| sc2008 | [四川省人民政府公报：第二批省级非遗名录](https://www.sc.gov.cn/sczb/lmfl/szfwj/200908/t20090802_792679.shtml)，网页2009-08-02 | 新繁棕编的省级认定、传统美术分类、新都区文化馆申报单位。不能升级成国家级。 |
| palm | [大渡口区文旅委：棕编](https://www.ddk.gov.cn/bm/qwhlyw/zwxx_56852/dt_56853/202108/t20210827_9631543.html)，2021-08-27 | 只用棕编共有的植物原料、实用/观赏属性；不把重庆当地传承史当成新繁史。 |
| gazette2019 | [国务院公报2019年第30号名单](https://www.gov.cn/gbgl/75e17ff291dd418f8f758d508087cd8b/files/6250670fc770465787b85d705d4b12f9.pdf) | 龙藏寺第八批国保、明清时代和新都区地域；只取正式名单，不附未经验证的壁画、始建故事、开放安排。 |

摘要由KITT自行组织为简短中文事实片段，按CC BY 4.0提供；原网页、照片与文章表达版权仍归原权利人。事实内容与原文表达分开，没有镜像文章、原图、录音或全文。获取网页不代表网页有开放转载许可；本包许可不涵盖原网页表达。上表和JSON支持逐条人工复核，不能把自动Schema测试当事实正确性证明。

## 几何数据

- 原始发布：[geoBoundaries gbOpen CHN ADM3元数据](https://www.geoboundaries.org/api/current/gbOpen/CHN/ADM3/)。本次实际读取 boundaryID `CHN-ADM3-62558664`，年份2017，buildDate 2023-12-12。
- 固定原始文件：[9469f09 / geoBoundaries-CHN-ADM3.geojson](https://github.com/wmgeolab/geoBoundaries/raw/9469f09/releaseData/gbOpen/CHN/ADM3/geoBoundaries-CHN-ADM3.geojson)。仅抽取 `shapeName=Xindu District`、`shapeID=62558664B25738263309049`；保留1017个顶点的Polygon及原坐标双精度值，无简化、平移或人工补画。JSON外层重编码，几何坐标不变。数据为WGS84经纬度，直接对应Android物理定位，未混用GCJ-02。
- 原始全国文件仅存本机忽略的 `artifacts/v040-source`；公开仓库与APK只包含新都单片，约42KB。
- 署名：© OpenStreetMap contributors；Lee Beryman；geoBoundaries。元数据明确该层原始许可为 [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/)，不是擅自把全部geoBoundaries数据当CC BY。独立几何单片按ODbL共享，源文件本身在仓库中可得；APK带完整许可副本，设置中提供署名与许可入口。[OSM许可说明](https://www.openstreetmap.org/copyright)。知识摘要与应用代码不因此冒称来自OSM。
- **精度/时间边界**：这是有来源有许可的2017开源行政区数据，不是官方测绘，也没有独立证明2026边界完全未变。Overpass实时核对尝试收到406/超时，未用它生成或伪造新数据。生产识区要求位置年龄≤15秒、精度≤50米、GPS或NETWORK，误差圆加1000米保守带全部处于多边形内部；环孔也排除。1000米是产品安全退让量，**不是已测得的边界误差上限**，不能保证覆盖所有行政调整。边界带、粗/旧/桥接位置均未知；不产生镇街。当前边界权威性/行政调整复核保留PENDING；不能签官方精度或全国覆盖PASS。

## 准备下一版包

本版只通过同签名APK更新包；没有外部文件导入、下载平台或NAS依赖。修改事实前人工打开原URL，核对对象/地域、稳定性、来源日期和表达版权，保留争议注记；修改几何必须同时审查来源、坐标系、许可、年份、邻接/边界与保守带，不能用最近POI圆圈代替。

审校后运行 `python scripts/seal-offline-pack.py` 更新manifest。它只校验JSON并重新计算两份资产SHA256、字节数、对象/事实数，不联网、不证明内容可信。修改包版本与更新日期后运行离线专测及 `scripts/verify.ps1 -Offline`；哈希不是对恶意包的签名，可信分发依靠原证书APK。坏/空/不兼容/校验失败时整个包不激活，不回退到伪造行政区或云端离线问答。`.gitattributes`保留资产原始字节，跨Windows checkout不会因换行破坏校验。
