package com.example.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.Flow

class LexiRepository(private val lexiDao: LexiDao) {

    val allComponents: Flow<List<AffixComponent>> = lexiDao.getAllComponents()
    val allWords: Flow<List<WordFormula>> = lexiDao.getAllWords()

    suspend fun updateComponentMastery(id: String, isMastered: Boolean) {
        lexiDao.updateComponentMastery(id, isMastered)
    }

    suspend fun updateComponentEbbinghaus(id: String, isMastered: Boolean, lastReviewedAt: Long, ebbinghausStage: Int, nextReviewAt: Long) {
        lexiDao.updateComponentEbbinghaus(id, isMastered, lastReviewedAt, ebbinghausStage, nextReviewAt)
    }

    suspend fun updateWordAssembly(id: String, assembled: Boolean) {
        lexiDao.updateWordAssembly(id, assembled)
    }

    suspend fun updateWordEbbinghaus(id: String, assembled: Boolean, lastReviewedAt: Long, ebbinghausStage: Int, nextReviewAt: Long) {
        lexiDao.updateWordEbbinghaus(id, assembled, lastReviewedAt, ebbinghausStage, nextReviewAt)
    }

    suspend fun resetAllLearningData() {
        lexiDao.resetAllComponents()
        lexiDao.resetAllWords()
    }

    suspend fun replaceWordbook(components: List<AffixComponent>, words: List<WordFormula>) {
        lexiDao.deleteAllWordFormulas()
        lexiDao.deleteAllComponents()
        lexiDao.insertComponents(components)
        lexiDao.insertWords(words)
    }

    suspend fun resetToDefaultBuiltIn() {
        lexiDao.deleteAllWordFormulas()
        lexiDao.deleteAllComponents()
        checkAndPrepopulate()
    }

    suspend fun checkAndPrepopulate() {
        if (lexiDao.getAffixCount() == 0) {
            val componentSeeds = listOf(
                // Prefixes
                AffixComponent(
                    id = "pref_ad",
                    name = "ad- / at-",
                    type = "PREFIX",
                    icon = "🎯",
                    literalMeaning = "去、往 / to, toward",
                    anchorWord = "attract (吸引)",
                    anchorDesc = "用 'attract' 联想：at- (去、往) + tract (拉) = 往自己这拉 -> 吸引"
                ),
                AffixComponent(
                    id = "pref_ex",
                    name = "ex- / e-",
                    type = "PREFIX",
                    icon = "🚪",
                    literalMeaning = "向外、出 / out, away",
                    anchorWord = "export (出口)",
                    anchorDesc = "用 'export' 联想：ex- (出) + port (运) = 运到外面去 -> 出口"
                ),
                AffixComponent(
                    id = "pref_im",
                    name = "im- / in-",
                    type = "PREFIX",
                    icon = "📥",
                    literalMeaning = "向内、入 / into; or 否定 / not",
                    anchorWord = "import (进口)",
                    anchorDesc = "用 'import' 联想：im- (入) + port (运) = 运到里面来 -> 进口"
                ),
                AffixComponent(
                    id = "pref_re",
                    name = "re-",
                    type = "PREFIX",
                    icon = "🔄",
                    literalMeaning = "往回、向后、再次 / back, again",
                    anchorWord = "reject (拒绝)",
                    anchorDesc = "用 'reject' 联想：re- (往回) + ject (扔) = 扔回来 -> 拒绝 (不要这个件)"
                ),
                AffixComponent(
                    id = "pref_de",
                    name = "de-",
                    type = "PREFIX",
                    icon = "📉",
                    literalMeaning = "向下、分离、完全 / down, off, completely",
                    anchorWord = "deconstruct (解构)",
                    anchorDesc = "用 'deconstruct' 联想：de- (拆解向下) + struct (建造) = 拆掉建成的部分 -> 解构"
                ),
                AffixComponent(
                    id = "pref_pro",
                    name = "pro-",
                    type = "PREFIX",
                    icon = "🚀",
                    literalMeaning = "向前、在...之前 / forward, before",
                    anchorWord = "project (项目)",
                    anchorDesc = "用 'project' 联想：pro- (向前) + ject (扔) = 扔向前方的划策 -> 规划项目物"
                ),
                AffixComponent(
                    id = "pref_sub",
                    name = "sub-",
                    type = "PREFIX",
                    icon = "🚇",
                    literalMeaning = "在...下方、次级 / under, below, sub-",
                    anchorWord = "subway (地铁)",
                    anchorDesc = "用 'subway' 联想：sub- (在...下面) + way (路) = 地面下的路 -> 地铁"
                ),
                AffixComponent(
                    id = "pref_co",
                    name = "co- / con- / com-",
                    type = "PREFIX",
                    icon = "🤝",
                    literalMeaning = "共同、一起 / together, with",
                    anchorWord = "commit (交付、承诺)",
                    anchorDesc = "用 'commit' 联想：com- (共同) + mit (送) = 双方共同送出信任 -> 承诺"
                ),
                AffixComponent(
                    id = "pref_dis",
                    name = "dis-",
                    type = "PREFIX",
                    icon = "❌",
                    literalMeaning = "相反、分离、否定 / apart, not",
                    anchorWord = "disagree (不同意)",
                    anchorDesc = "用 'disagree' 联想：dis- (相反) + agree (同意) = 意见相反、不赞成"
                ),
                AffixComponent(
                    id = "pref_pre",
                    name = "pre-",
                    type = "PREFIX",
                    icon = "📅",
                    literalMeaning = "在...之前 / before, ahead",
                    anchorWord = "predict (预测)",
                    anchorDesc = "用 'predict' 联想：pre- (提前) + dict (说) = 提前说出未来的话 -> 预测"
                ),
                AffixComponent(
                    id = "pref_trans",
                    name = "trans-",
                    type = "PREFIX",
                    icon = "🌁",
                    literalMeaning = "横穿、转、改变 / across, through, over",
                    anchorWord = "transport (运输)",
                    anchorDesc = "用 'transport' 联想：trans- (横穿跨越) + port (运) = 跨地区搬运 -> 运输"
                ),
                AffixComponent(
                    id = "pref_inter",
                    name = "inter-",
                    type = "PREFIX",
                    icon = "🌐",
                    literalMeaning = "在...之间、相互 / between, among, mutually",
                    anchorWord = "interaction (交互)",
                    anchorDesc = "用 'interaction' 联想：inter- (相互) + action (动作) = 相互发生动作 -> 交互"
                ),

                // Roots
                AffixComponent(
                    id = "root_tract",
                    name = "tract",
                    type = "ROOT",
                    icon = "🧲",
                    literalMeaning = "拉、引 / pull, draw",
                    anchorWord = "attract (吸引)",
                    anchorDesc = "用 'attract' 联想：at- (往) + tract (拉) = 把人拉向自己 -> 吸引"
                ),
                AffixComponent(
                    id = "root_spect",
                    name = "spect",
                    type = "ROOT",
                    icon = "🔍",
                    literalMeaning = "看、视 / look, watch",
                    anchorWord = "respect (尊敬)",
                    anchorDesc = "用 'respect' 联想：re- (再次) + spect (看) = 连连回头看某人 -> 尊敬"
                ),
                AffixComponent(
                    id = "root_port",
                    name = "port",
                    type = "ROOT",
                    icon = "📦",
                    literalMeaning = "运、移、港口 / carry, gate",
                    anchorWord = "import (进口)",
                    anchorDesc = "用 'import' 联想：im- (入) + port (运) = 运入港口 -> 进口物"
                ),
                AffixComponent(
                    id = "root_ject",
                    name = "ject",
                    type = "ROOT",
                    icon = "☄️",
                    literalMeaning = "扔、投 / throw, cast",
                    anchorWord = "reject (拒绝)",
                    anchorDesc = "用 'reject' 联想：re- (往回) + ject (扔) = 递过去的方案被扔了回来 -> 拒绝"
                ),
                AffixComponent(
                    id = "root_dict",
                    name = "dict",
                    type = "ROOT",
                    icon = "🗣️",
                    literalMeaning = "说、言 / speak, say, declare",
                    anchorWord = "predict (预测)",
                    anchorDesc = "用 'predict' 联想：pre- (提前) + dict (说) = 提前说出结局 -> 预测"
                ),
                AffixComponent(
                    id = "root_vis",
                    name = "vis / vid",
                    type = "ROOT",
                    icon = "👁️",
                    literalMeaning = "看、见 / see",
                    anchorWord = "visible (可见的)",
                    anchorDesc = "用 'visible' 联想：vis (看) + -ible (能...的) = 能被眼睛看见的"
                ),
                AffixComponent(
                    id = "root_mit",
                    name = "mit / miss",
                    type = "ROOT",
                    icon = "🚀",
                    literalMeaning = "送、派、放 / send, release, cast",
                    anchorWord = "submit (提交)",
                    anchorDesc = "用 'submit' 联想：sub- (在...之下) + mit (送) = 从下面送上去给老板审批 -> 提交"
                ),
                AffixComponent(
                    id = "root_tend",
                    name = "tend / tens",
                    type = "ROOT",
                    icon = "🏹",
                    literalMeaning = "伸、拉。拉紧 / stretch, strive",
                    anchorWord = "extend (延长)",
                    anchorDesc = "用 'extend' 联想：ex- (出) + tend (伸) = 往外拉伸出来 -> 延长/扩张"
                ),
                AffixComponent(
                    id = "root_cap",
                    name = "cap / cept / cip",
                    type = "ROOT",
                    icon = "✊",
                    literalMeaning = "拿、抓、容纳 / take, hold, seize",
                    anchorWord = "capture (俘获)",
                    anchorDesc = "用 'capture' 联想：cap (抓) + -ture (名词后缀) = 牢牢抓住的行为 -> 捕获"
                ),
                AffixComponent(
                    id = "root_scrib",
                    name = "scrib / script",
                    type = "ROOT",
                    icon = "✍️",
                    literalMeaning = "写、记 / write, text",
                    anchorWord = "describe (描述)",
                    anchorDesc = "用 'describe' 联想：de- (向下) + scribe (写) = 把细节在纸上向下写出来 -> 描述"
                ),
                AffixComponent(
                    id = "root_press",
                    name = "press",
                    type = "ROOT",
                    icon = "🗜️",
                    literalMeaning = "压、挤 / press, force",
                    anchorWord = "express (表达)",
                    anchorDesc = "用 'express' 联想：ex- (往外) + press (压) = 把内心压抑的想法挤向外面 -> 表达/特快"
                ),
                AffixComponent(
                    id = "root_struct",
                    name = "struct",
                    type = "ROOT",
                    icon = "🏗️",
                    literalMeaning = "建造、组装 / build, order",
                    anchorWord = "structure (结构)",
                    anchorDesc = "用 'structure' 联想：struct (搭建) + -ure (名后缀) = 搭建成的物理模型 -> 结构"
                ),
                AffixComponent(
                    id = "root_sens",
                    name = "sens / sent",
                    type = "ROOT",
                    icon = "🧠",
                    literalMeaning = "感觉、知觉 / feel, perceive",
                    anchorWord = "sensitive (敏感的)",
                    anchorDesc = "用 'sensitive' 联想：sens (感觉) + -itive (形容词缀) = 感觉极其敏锐 -> 敏感的"
                ),
                AffixComponent(
                    id = "root_fin",
                    name = "fin",
                    type = "ROOT",
                    icon = "🏁",
                    literalMeaning = "界限、末尾 / end, limit, finish",
                    anchorWord = "define (下定义)",
                    anchorDesc = "用 'define' 联想：de- (向下加强) + fin (限度) = 规划出明确的空间界限 -> 下定义"
                ),
                AffixComponent(
                    id = "root_fer",
                    name = "fer",
                    type = "ROOT",
                    icon = "🔄",
                    literalMeaning = "带、运送、持 / bring, bear, carry",
                    anchorWord = "transfer (转账、转移)",
                    anchorDesc = "用 'transfer' 联想：trans- (跨越) + fer (带) = 从这边带到那边 -> 跨区转移"
                ),
                AffixComponent(
                    id = "root_duc",
                    name = "duc / duct",
                    type = "ROOT",
                    icon = "🧭",
                    literalMeaning = "领导、引导 / lead, guide",
                    anchorWord = "conduct (引导、实施)",
                    anchorDesc = "用 'conduct' 联想：con- (共同) + duct (引导) = 共同带路前行 -> 实施行为"
                ),

                // Suffixes
                AffixComponent(
                    id = "suff_ion",
                    name = "-ion / -tion",
                    type = "SUFFIX",
                    icon = "🏷️",
                    literalMeaning = "名词化后缀，表动作或状态 / action status",
                    anchorWord = "action (行动)",
                    anchorDesc = "用 'action' 联想：后缀 '-ion' 是英语中最基础、最高频的名词词尾。"
                ),
                AffixComponent(
                    id = "suff_able",
                    name = "-able / -ible",
                    type = "SUFFIX",
                    icon = "💪",
                    literalMeaning = "形容词后缀，表可以...的 / capable of",
                    anchorWord = "flexible (易弯曲的)",
                    anchorDesc = "用 'flexible' 联想：'-ible' 能够被弯曲的 -> 灵活的"
                ),
                AffixComponent(
                    id = "suff_ive",
                    name = "-ive",
                    type = "SUFFIX",
                    icon = "⚡",
                    literalMeaning = "形容词后缀，表有某种倾向或性质的 / dynamic quality",
                    anchorWord = "active (活跃的)",
                    anchorDesc = "用 'active' 联想：'-ive' 属性意在让词表达某种行为取向。"
                ),
                AffixComponent(
                    id = "suff_ment",
                    name = "-ment",
                    type = "SUFFIX",
                    icon = "💼",
                    literalMeaning = "名词后缀，表结果、用具/工具 / tool, result",
                    anchorWord = "movement (动作、运动)",
                    anchorDesc = "用 'movement' 联想：move (动) + -ment (名后缀) = 移动产生的物理结果"
                ),
                AffixComponent(
                    id = "suff_ity",
                    name = "-ity",
                    type = "SUFFIX",
                    icon = "💎",
                    literalMeaning = "名词后缀，表抽象属性或性质 / state, quality",
                    anchorWord = "ability (能力)",
                    anchorDesc = "用 'ability' 联想：able (能) 加上 '-ity' 后缀 -> 代表一种抽象的品质状态。"
                )
            )

            lexiDao.insertComponents(componentSeeds)

            val wordSeeds = listOf(
                WordFormula(
                    id = "attract",
                    spell = "attract",
                    prefixId = "pref_ad",
                    rootId = "root_tract",
                    suffixId = null,
                    prefixPart = "at- (ad-同化为at-)",
                    rootPart = "tract",
                    suffixPart = null,
                    literalFormula = "at- (去、朝向) + tract (拉) = 往自己这边拉 -> 吸引",
                    chineseMeaning = "吸引；招引；引起...",
                    sentenceEn = "The bright lights of the city often attract young people.",
                    sentenceZh = "城市里明亮的霓虹灯常常吸引年轻人。"
                ),
                WordFormula(
                    id = "import",
                    spell = "import",
                    prefixId = "pref_im",
                    rootId = "root_port",
                    suffixId = null,
                    prefixPart = "im- (向内)",
                    rootPart = "port",
                    suffixPart = null,
                    literalFormula = "im- (内) + port (运) = 运送到我国口岸内 -> 进口",
                    chineseMeaning = "进口；引入；进口货物",
                    sentenceEn = "The country has to import energy products to meet its demands.",
                    sentenceZh = "该国必须进口能源产品以满足其需求。"
                ),
                WordFormula(
                    id = "export",
                    spell = "export",
                    prefixId = "pref_ex",
                    rootId = "root_port",
                    suffixId = null,
                    prefixPart = "ex- (向外)",
                    rootPart = "port",
                    suffixPart = null,
                    literalFormula = "ex- (外) + port (运) = 运送到别国或者口岸外 -> 出口",
                    chineseMeaning = "出口；输出；出口物资",
                    sentenceEn = "Many local companies export electronic components.",
                    sentenceZh = "许多当地公司出口电子元件。"
                ),
                WordFormula(
                    id = "reject",
                    spell = "reject",
                    prefixId = "pref_re",
                    rootId = "root_ject",
                    suffixId = null,
                    prefixPart = "re- (往回)",
                    rootPart = "ject",
                    suffixPart = null,
                    literalFormula = "re- (回) + ject (扔) = 把递来的方案原封不动地扔回去 -> 拒绝",
                    chineseMeaning = "拒绝；退回；驳回",
                    sentenceEn = "The system will automatically reject invalid application forms.",
                    sentenceZh = "该系统会自动驳回无效的申请表。"
                ),
                WordFormula(
                    id = "project",
                    spell = "project",
                    prefixId = "pref_pro",
                    rootId = "root_ject",
                    suffixId = null,
                    prefixPart = "pro- (向前)",
                    rootPart = "ject",
                    suffixPart = null,
                    literalFormula = "pro- (向前) + ject (扔) = 策划案如同把脑中的蓝图向前投射出去 -> 项目/投影",
                    chineseMeaning = "项目；规划；投射",
                    sentenceEn = "She is managing a new construction project under severe testing.",
                    sentenceZh = "她正在管理一个在严苛测试下的新建筑项目。"
                ),
                WordFormula(
                    id = "predict",
                    spell = "predict",
                    prefixId = "pref_pre",
                    rootId = "root_dict",
                    suffixId = null,
                    prefixPart = "pre- (提前)",
                    rootPart = "dict",
                    suffixPart = null,
                    literalFormula = "pre- (前) + dict (说) = 在事情还没发生前，提前把它说出口 -> 预测",
                    chineseMeaning = "预测；预言；预示",
                    sentenceEn = "Economists predict a strong recovery in consumer spending next year.",
                    sentenceZh = "经济学家预测明年消费支出将强劲复苏。"
                ),
                WordFormula(
                    id = "describe",
                    spell = "describe",
                    prefixId = "pref_de",
                    rootId = "root_scrib",
                    suffixId = null,
                    prefixPart = "de- (向下)",
                    rootPart = "scrib",
                    suffixPart = null,
                    literalFormula = "de- (往下) + scribe (写) = 用笔画把场景特征记录写在纸面上 -> 描述",
                    chineseMeaning = "描述；形容；描绘",
                    sentenceEn = "The guide was able to describe the history of the ancient temple.",
                    sentenceZh = "向导能够详细描述这座古庙的历史。"
                ),
                WordFormula(
                    id = "commit",
                    spell = "commit",
                    prefixId = "pref_co",
                    rootId = "root_mit",
                    suffixId = null,
                    prefixPart = "com- (共同)",
                    rootPart = "mit",
                    suffixPart = null,
                    literalFormula = "com- (同) + mit (送出) = 把彼此全心全意地送出、绑定交托给一件事 -> 承诺/致于",
                    chineseMeaning = "承诺；犯(错)；把...交托于",
                    sentenceEn = "You must commit enough time each day to review your memory roots.",
                    sentenceZh = "你必须每天投入足够的时间来复习记忆词根。"
                ),
                WordFormula(
                    id = "submit",
                    spell = "submit",
                    prefixId = "pref_sub",
                    rootId = "root_mit",
                    suffixId = null,
                    prefixPart = "sub- (在...之下)",
                    rootPart = "mit",
                    suffixPart = null,
                    literalFormula = "sub- (下) + mit (送) = 弯下腰从底下送托上呈方案 -> 提交；屈服",
                    chineseMeaning = "提交；呈递；屈服",
                    sentenceEn = "Students need to submit their research plans before Friday.",
                    sentenceZh = "学生们需要在周五之前提交他们的研究计划。"
                ),
                WordFormula(
                    id = "extend",
                    spell = "extend",
                    prefixId = "pref_ex",
                    rootId = "root_tend",
                    suffixId = null,
                    prefixPart = "ex- (外)",
                    rootPart = "tend",
                    suffixPart = null,
                    literalFormula = "ex- (向外) + tend (伸) = 往外部使劲拉伸延展 -> 延长；扩宽",
                    chineseMeaning = "延长；延伸；扩大",
                    sentenceEn = "The library will extend its opening hours during the final exam week.",
                    sentenceZh = "期末考试周期间，图书馆将延长开放时间。"
                ),
                WordFormula(
                    id = "visible",
                    spell = "visible",
                    prefixId = null,
                    rootId = "root_vis",
                    suffixId = "suff_able",
                    prefixPart = null,
                    rootPart = "vis",
                    suffixPart = "-ible",
                    literalFormula = "vis (看) + -ible (能...的) = 可以轻易被肉眼看得到的 -> 可见的",
                    chineseMeaning = "可见的；显而易见的",
                    sentenceEn = "Stars are clearly visible on this remote mountain platform.",
                    sentenceZh = "在这座偏远的山顶平台上，星星清晰可见。"
                ),
                WordFormula(
                    id = "inspect",
                    spell = "inspect",
                    prefixId = "pref_im", // treated as "in-"
                    rootId = "root_spect",
                    suffixId = null,
                    prefixPart = "in- (往里)",
                    rootPart = "spect",
                    suffixPart = null,
                    literalFormula = "in- (向内) + spect (检查、看) = 深入到内部一探究竟 -> 视察；检查",
                    chineseMeaning = "视察；检查；审查",
                    sentenceEn = "Officers will inspect the quality of the engineering model daily.",
                    sentenceZh = "官员们每天都会检查工程模型的质量。"
                ),
                WordFormula(
                    id = "express",
                    spell = "express",
                    prefixId = "pref_ex",
                    rootId = "root_press",
                    suffixId = null,
                    prefixPart = "ex- (外)",
                    rootPart = "press",
                    suffixPart = null,
                    literalFormula = "ex- (向外) + press (向下压) = 将原本挤压在内心的想法释放到体外 -> 表达",
                    chineseMeaning = "表达；陈述；特快",
                    sentenceEn = "Art provides a creative space for children to express their visions.",
                    sentenceZh = "艺术为孩子们提供了表达他们想法的创意空间。"
                ),
                WordFormula(
                    id = "structure",
                    spell = "structure",
                    prefixId = null,
                    rootId = "root_struct",
                    suffixId = "suff_ion", // utilizing "suff_ion" but modified as suffix-ure - we can supply custom label or use it as a base
                    prefixPart = null,
                    rootPart = "struct",
                    suffixPart = "-ure (表示名词结果)",
                    literalFormula = "struct (建) + -ure (表示结构化之物) = 搭建出来的框架整体 -> 结构",
                    chineseMeaning = "结构；构造；建筑物",
                    sentenceEn = "The chemical structure of the new prefix molecule is quite complex.",
                    sentenceZh = "这种新前缀分子的化学结构非常复杂。"
                ),
                WordFormula(
                    id = "sensitive",
                    spell = "sensitive",
                    prefixId = null,
                    rootId = "root_sens",
                    suffixId = "suff_ive",
                    prefixPart = null,
                    rootPart = "sens",
                    suffixPart = "-ive (形容词缀)",
                    literalFormula = "sens (感觉) + -ive (强悍属性) = 对外界变化有着极其敏锐神经的 -> 敏感",
                    chineseMeaning = "敏感的；神经过敏的；高度感光的",
                    sentenceEn = "Please be extremely sensitive to local environmental conditions in deep night.",
                    sentenceZh = "在深夜，请对当地的环境条件保持极度敏锐。"
                ),
                WordFormula(
                    id = "definition",
                    spell = "definition",
                    prefixId = "pref_de",
                    rootId = "root_fin",
                    suffixId = "suff_ion",
                    prefixPart = "de- (向下加强)",
                    rootPart = "fin",
                    suffixPart = "-ition (名词缀)",
                    literalFormula = "de- (加强) + fin (界限) + -ition (抽象名词) = 往下清晰划定一条排他的界线 -> 定义",
                    chineseMeaning = "定义；度清晰；释义",
                    sentenceEn = "What is your dictionary's literal definition of the root 'tract'?",
                    sentenceZh = "你的词典对词根 'tract' 的字面定义是什么？"
                ),
                WordFormula(
                    id = "transfer",
                    spell = "transfer",
                    prefixId = "pref_trans",
                    rootId = "root_fer",
                    suffixId = null,
                    prefixPart = "trans- (穿过、转换)",
                    rootPart = "fer",
                    suffixPart = null,
                    literalFormula = "trans- (跨越、转手) + fer (携带带来的物) = 把东西从a地点携带至b地点 -> 转移；转账",
                    chineseMeaning = "转移；转让；转账；过户",
                    sentenceEn = "Can you transfer the database credentials using code?",
                    sentenceZh = "你能用代码转移数据库凭证吗？"
                ),
                WordFormula(
                    id = "transport",
                    spell = "transport",
                    prefixId = "pref_trans",
                    rootId = "root_port",
                    suffixId = null,
                    prefixPart = "trans- (横越)",
                    rootPart = "port",
                    suffixPart = null,
                    literalFormula = "trans- (横跨大洲大洋) + port (运、进港口) = 横跨千里路搬运货物 -> 运输",
                    chineseMeaning = "运输；交通运输系统",
                    sentenceEn = "Developing a green transport network is key to lowering emissions.",
                    sentenceZh = "开发绿色交通网络是降低排放的关键。"
                )
            )

            val baseTemplates = listOf(
                BaseTemplate("attract", "attract", "pref_ad", "root_tract", null, "at- (ad-同化为at-)", "tract", null, "at- (去、朝向) + tract (拉) = 往自己这边拉 -> 吸引", "吸引；招引；引起...", "The bright lights of the city often attract young people.", "城市里明亮的霓虹灯常常吸引年轻人。"),
                BaseTemplate("import", "import", "pref_im", "root_port", null, "im- (向内)", "port", null, "im- (内) + port (运) = 运送到我国口岸内 -> 进口", "进口；引入；进口货物", "The country has to import energy products to meet its demands.", "该国必须进口能源产品以满足其需求。"),
                BaseTemplate("export", "export", "pref_ex", "root_port", null, "ex- (向外)", "port", null, "ex- (外) + port (运) = 运送到别国或者口岸外 -> 出口", "出口；输出；出口物资", "Many local companies export electronic components.", "许多当地公司出口电子元件。"),
                BaseTemplate("reject", "reject", "pref_re", "root_ject", null, "re- (往回)", "ject", null, "re- (回) + ject (扔) = 把递来的方案原封不动地扔回去 -> 拒绝", "拒绝；退回；驳回", "The system will automatically reject invalid application forms.", "该系统会自动驳回无效的申请表。"),
                BaseTemplate("project", "project", "pref_pro", "root_ject", null, "pro- (向前)", "ject", null, "pro- (向前) + ject (扔) = 策划案如同把脑中的蓝图向前投射出去 -> 项目/投影", "项目；规划；投射", "She is managing a new construction project under severe testing.", "她正在管理一个在严苛测试下的新建筑项目。"),
                BaseTemplate("predict", "predict", "pref_pre", "root_dict", null, "pre- (提前)", "dict", null, "pre- (前) + dict (说) = 在事情还没发生前，提前把它说出口 -> 预测", "预测；预言；预示", "Economists predict a strong recovery in consumer spending next year.", "经济学家预测明年消费支出将强劲复苏。"),
                BaseTemplate("describe", "describe", "pref_de", "root_scrib", null, "de- (向下)", "scrib", null, "de- (往下) + scribe (写) = 用笔画把场景特征记录写在纸面上 -> 描述", "描述；形容；描绘", "The guide was able to describe the history of the ancient temple.", "向导能够详细描述这座古庙的历史。"),
                BaseTemplate("commit", "commit", "pref_co", "root_mit", null, "com- (共同)", "mit", null, "com- (同) + mit (送出) = 把彼此全心全意地送出、绑定交托给一件事 -> 承诺/致于", "承诺；犯(错)；把...交托于", "You must commit enough time each day to review your memory roots.", "你必须每天投入足够的时间来复习记忆词根。"),
                BaseTemplate("submit", "submit", "pref_sub", "root_mit", null, "sub- (在...之下)", "mit", null, "sub- (下) + mit (送) = 弯下腰从底下送托上呈方案 -> 提交；屈服", "提交；呈递；屈服", "Students need to submit their research plans before Friday.", "学生们需要在周五之前提交他们的研究计划。"),
                BaseTemplate("extend", "extend", "pref_ex", "root_tend", null, "ex- (外)", "tend", null, "ex- (向外) + tend (伸) = 往外部使劲拉伸延展 -> 延长；扩宽", "延长；延伸；扩大", "The library will extend its opening hours during the final exam week.", "期末考试周期间，图书馆将延长开放时间。"),
                BaseTemplate("visible", "visible", null, "root_vis", "suff_able", null, "vis", "-ible", "vis (看) + -ible (能...的) = 可以轻易被肉眼看得到的 -> 可见的", "可见的；显而易见的", "Stars are clearly visible on this remote mountain platform.", "在这座偏远的山顶平台上，星星清晰可见。"),
                BaseTemplate("inspect", "inspect", "pref_im", "root_spect", null, "in- (往里)", "spect", null, "in- (向内) + spect (检查、看) = 深入到内部一探究竟 -> 视察；检查", "视察；检查；审查", "Officers will inspect the quality of the engineering model daily.", "官员们每天都会检查工程模型的质量。"),
                BaseTemplate("express", "express", "pref_ex", "root_press", null, "ex- (外)", "press", null, "ex- (向外) + press (向下压) = 将原本挤压在内心的想法释放到体外 -> 表达", "表达；陈述；特快", "Art provides a creative space for children to express their visions.", "艺术为孩子们提供了表达他们想法的创意空间。"),
                BaseTemplate("structure", "structure", null, "root_struct", "suff_ion", null, "struct", "-ure", "struct (建) + -ure (表示结构化之物) = 搭建出来的框架整体 -> 结构", "结构；构造；建筑物", "The chemical structure of the new prefix molecule is quite complex.", "这种新前缀分子的化学结构非常复杂。"),
                BaseTemplate("sensitive", "sensitive", null, "root_sens", "suff_ive", null, "sens", "-ive", "sens (感觉) + -ive (强悍属性) = 对外界变化有着极其敏锐神经的 -> 敏感", "敏感的；神经过敏的；高度感光的", "Please be extremely sensitive to local environmental conditions in deep night.", "在深夜，请对当地的环境条件保持极度敏锐。"),
                BaseTemplate("definition", "definition", "pref_de", "root_fin", "suff_ion", "de- (向下加强)", "fin", "-ition", "de- (加强) + fin (界限) + -ition (抽象名词) = 往下清晰划定一条排他的界线 -> 定义", "定义；度清晰；释义", "What is your dictionary's literal definition of the root 'tract'?", "你的词典对词根 'tract' 的字面定义是什么？"),
                BaseTemplate("transfer", "transfer", "pref_trans", "root_fer", null, "trans- (穿过、转换)", "fer", null, "trans- (跨越、转手) + fer (携带带来的物) = 把东西从a地点携带至b地点 -> 转移；转账", "转移；转让；转账；过户", "Can you transfer the database credentials using code?", "你能用代码转移数据库凭证吗？"),
                BaseTemplate("transport", "transport", "pref_trans", "root_port", null, "trans- (横越)", "port", null, "trans- (横跨大洲大洋) + port (运、进港口) = 横跨千里路搬运货物 -> 运输", "运输；交通运输系统", "Developing a green transport network is key to lowering emissions.", "开发绿色交通网络是降低排放的关键。"),
                BaseTemplate("retract", "retract", "pref_re", "root_tract", null, "re- (回)", "tract", null, "re- (回) + tract (拉) = 往回拉 -> 撤回；缩回", "缩回；收回；取消", "The cat can retract its claws when crawling.", "猫在爬行时可以缩回爪子。"),
                BaseTemplate("distract", "distract", "pref_dis", "root_tract", null, "dis- (分开)", "tract", null, "dis- (分开/散) + tract (拉) = 把思想拉向不同方向 -> 分心", "分心；分散；打扰", "Don't let the surrounding noises distract you from learning.", "不要让周围的噪音分散你学习的注意力。"),
                BaseTemplate("contract", "contract", "pref_co", "root_tract", null, "con- (共同)", "tract", null, "con- (共同) + tract (拉) = 共同往里拉紧 -> 合同/收缩", "合同；收缩；契约", "They signed a contract to construct a new campus lab.", "他们签署了建造新校园实验室的合同。"),
                BaseTemplate("extract", "extract", "pref_ex", "root_tract", null, "ex- (出)", "tract", null, "ex- (出) + tract (拉) = 往外拉出 -> 拔出/提炼", "提炼；榨取；析取；萃取", "Chemists extract core elements from natural plants.", "化学家从自然植物中提炼核心元素。"),
                BaseTemplate("subtract", "subtract", "pref_sub", "root_tract", null, "sub- (下)", "tract", null, "sub- (下) + tract (拉) = 往下拉去、减掉一部分 -> 减去", "减去；扣除", "If you subtract two from five, you will get three.", "如果五减去二，你会得到三。"),
                BaseTemplate("respect", "respect", "pref_re", "root_spect", null, "re- (再次)", "spect", null, "re- (再次) + spect (看) = 让人连连回头看 -> 尊敬", "尊敬；敬重；方面", "You should respect other scholars' research achievements.", "你应该尊重其他学者的研究成果。"),
                BaseTemplate("expect", "expect", "pref_ex", "root_spect", null, "ex- (出)", "spect", null, "ex- (出) + spect (看) = 伸长脖子往外看 -> 期望", "期望；预期；期待", "We expect the new formula database to work securely.", "我们期望这个新的公式数据库能够安全运行。"),
                BaseTemplate("prospect", "prospect", "pref_pro", "root_spect", null, "pro- (向前)", "spect", null, "pro- (向前) + spect (看) = 往前看未来的景象 -> 前景", "前景；探矿；希望", "The job transition prospect seems extremely bright.", "这次工作转换的前景看起来非常光明。"),
                BaseTemplate("suspect", "suspect", "pref_sub", "root_spect", null, "sub- (从下往上)", "spect", null, "sub- (下) + spect (看) = 在底下偷偷打量怀疑 -> 怀疑", "怀疑；嫌疑犯；猜想", "The local guards suspect that the program had a logic flaw.", "当地警卫怀疑程序存在逻辑漏洞。"),
                BaseTemplate("report", "report", "pref_re", "root_port", null, "re- (回)", "port", null, "re- (回) + port (运) = 运送信息回来 -> 报告", "报告；报道；公布", "She had to write a detailed report about the siege progress.", "她必须写一份关于围攻进展的详细报告。"),
                BaseTemplate("support", "support", "pref_sub", "root_port", null, "sub- (在...下)", "port", null, "sub- (在底) + port (托、运) = 在下面运送力量托住 -> 支持", "支持；帮扶；支撑", "They will support the implementation of the new database model.", "他们将支持新数据库模型的实现。"),
                BaseTemplate("inject", "inject", "pref_im", "root_ject", null, "in- (入)", "ject", null, "in- (往里) + ject (扔) = 往身体里面扔(药水) -> 注射", "注射；注入；引入", "Doctors inject vaccine molecules to boost cell protection.", "医生注射疫苗分子以增强细胞保护。"),
                BaseTemplate("subject", "subject", "pref_sub", "root_ject", null, "sub- (下)", "ject", null, "sub- (在下) + ject (扔) = 扔在权力之下统治 -> 臣民/科目", "主题；科目；受...支配的", "Mathematics is a core subject in modern vocational colleges.", "数学是现代职业院校的一门核心科目。"),
                BaseTemplate("object", "object", "pref_co", "root_ject", null, "ob-", "ject", null, "ob- (反) + ject (扔) = 扔在对面的障碍物 -> 反对/客体", "反对；物体；客体", "They object completely to changing the core design specs.", "他们完全反对更改核心设计规格。"),
                BaseTemplate("eject", "eject", "pref_ex", "root_ject", null, "e- (向外)", "ject", null, "e- (出) + ject (扔) = 往外扔出 -> 弹出", "驱逐；弹出；喷出", "The system will eject the virtual disc automatically.", "系统会自动弹出虚拟磁盘。"),
                BaseTemplate("contradict", "contradict", "pref_dis", "root_dict", null, "contra-", "dict", null, "contra- (反) + dict (说) = 唱反调 -> 矛盾；反驳", "反驳；与...矛盾", "The witness statements contradict each other completely.", "证人的陈述完全相互矛盾。"),
                BaseTemplate("dictate", "dictate", null, "root_dict", "suff_ive", null, "dict", "-ate", "dict (说) + -ate (指使) = 口授或命令 -> 指示", "口授；指示；命令", "Leaders dictate key targets for the expansion phase.", "领导为扩展阶段指示了关键目标。"),
                BaseTemplate("addict", "addict", "pref_ad", "root_dict", null, "ad- (往)", "dict", null, "ad- (往) + dict (说/宣布) = 嘴里一直念叨某物 -> 上瘾", "使沉溺；使上瘾；瘾君子", "Young people can easily addict themselves to video game layouts.", "年轻人很容易沉溺于电子游戏布局。"),
                BaseTemplate("indicate", "indicate", "pref_im", "root_dict", null, "in- (入)", "dict", null, "in- (往里) + dicate (说/指示) = 说出里面的指引 -> 指明", "指出；指示；表明", "Red indicator dots indicate where coordinates align.", "红色指示点表明了坐标对齐的位置。"),
                BaseTemplate("revise", "revise", "pref_re", "root_vis", null, "re- (再)", "vis", null, "re- (再次) + vis (看) = 再次审查修补 -> 修改", "修订；修改；温习", "The author agreed to revise the outline formula next week.", "作者同意下周修改公式大纲。"),
                BaseTemplate("supervise", "supervise", "pref_sub", "root_vis", null, "super-", "vis", null, "super- (上) + vis (看) = 在高处俯视管辖 -> 监督", "监督；管理；指导", "Engineers will supervise the installation of databases.", "工程师将监督数据库的安装。"),
                BaseTemplate("advise", "advise", "pref_ad", "root_vis", null, "ad- (往)", "vis", null, "ad- (给) + vis (看/意见) = 给别人看自己的观点 -> 建议", "建议；忠告；通知", "Teachers advise kids to practice decomposing formulas daily.", "老师建议孩子们每天练习分解公式。"),
                BaseTemplate("transmit", "transmit", "pref_trans", "root_mit", null, "trans- (穿过)", "mit", null, "trans- (跨越) + mit (送) = 跨空间发送 -> 传播/输送", "传输；播送；传播", "Cables transmit electronic signals across cities safely.", "电缆安全地在城市间传输电子信号。"),
                BaseTemplate("admit", "admit", "pref_ad", "root_mit", null, "ad- (向)", "mit", null, "ad- (向内) + mit (送/容许) = 让人送进来 -> 承认/接纳", "承认；准许...进入", "They will admit new members into the assembly laboratory.", "他们将接纳新成员进入组装实验室。"),
                BaseTemplate("permit", "permit", "pref_pro", "root_mit", null, "per-", "mit", null, "per- (穿过) + mit (放行) = 允许通过 -> 准许", "允许；许可证；执照", "Do not permit invalid keys to access the core storage.", "不要允许无效的密钥访问核心存储。"),
                BaseTemplate("emit", "emit", "pref_ex", "root_mit", null, "e- (向外)", "mit", null, "e- (外) + mit (放送) = 往外释放发射 -> 放射", "发出；放射；流露", "Bionic elements emit green glowing light in the night.", "仿生元素在夜间发出绿色的荧光。"),
                BaseTemplate("dismiss", "dismiss", "pref_dis", "root_mit", null, "dis- (分开)", "miss", null, "dis- (分散) + miss (放) = 遣散放走 -> 解散/驳回", "解散；开除；驳回", "The supervisor can dismiss invalid logic proposals.", "主管可以驳回无效的逻辑方案。"),
                BaseTemplate("intend", "intend", "pref_im", "root_tend", null, "in- (往里)", "tend", null, "in- (心内) + tend (伸/向) = 心往里面延伸倾向 -> 打算", "打算；意图；想要", "They intend to build a full bionic formula system.", "他们打算构建一个完整的仿生公式系统。"),
                BaseTemplate("pretend", "pretend", "pref_pre", "root_tend", null, "pre- (前)", "tend", null, "pre- (在前) + tend (伸/拉) = 在人前面拉起遮掩帐篷 -> 假装", "假装；伪装；佯装", "Do not pretend to know prefix formulas you haven't mastered.", "不要假装知道你还没有掌握的前缀公式。"),
                BaseTemplate("contend", "contend", "pref_co", "root_tend", null, "con- (一起)", "tend", null, "con- (一起) + tend (伸) = 大家一起使劲扯拽拼抢 -> 竞争", "竞争；争夺；坚决主张", "Athletes contend hard for the gold championship tag.", "运动员为夺取金牌冠军标签而激烈竞争。"),
                BaseTemplate("attend", "attend", "pref_ad", "root_tend", null, "at- (去)", "tend", null, "at- (往) + tend (伸) = 身心往那边延伸跟从 -> 参加", "出席；参加；照料", "Please attend the weekly formula review meeting on time.", "请按时参加每周的公式复习会议。"),
                BaseTemplate("accept", "accept", "pref_ad", "root_cap", null, "ac- (去)", "cept", null, "ac- (往) + cept (拿) = 伸手拿过来 -> 接受", "接受；承认；同意", "The user will accept the custom credential proposal safely.", "用户将安全地接受自定义凭证方案。"),
                BaseTemplate("except", "except", "pref_ex", "root_cap", null, "ex- (外)", "cept", null, "ex- (外) + cept (拿) = 拿到外面不要 -> 除了", "除了...之外；排除", "We work daily except Sundays for formula optimization.", "我们除了星期日外每天工作以优化公式。"),
                BaseTemplate("concept", "concept", "pref_co", "root_cap", null, "com- (共同)", "cept", null, "com- (共同) + cept (抓) = 共同抓取出来的概念精髓 -> 概念", "概念；观念；设想", "The deconstruction concept is very straightforward to understand.", "解构概念非常容易理解。"),
                BaseTemplate("perceive", "perceive", "pref_trans", "root_cap", null, "per-", "ceive", null, "per- (彻底) + ceive (抓/拿) = 彻底抓取洞察真相 -> 感知", "感知；感觉；察觉", "Cats can perceive delicate changes in night vibrations.", "猫能察觉到夜间振动的微妙变化。"),
                BaseTemplate("receive", "receive", "pref_re", "root_cap", null, "re- (回)", "ceive", null, "re- (回) + ceive (拿) = 拿回递上来的东西 -> 接收", "接收；收到；接待", "You will receive visual confirmations when verified.", "验证通过后您将收到视觉确认。"),
                BaseTemplate("subscribe", "subscribe", "pref_sub", "root_scrib", null, "sub- (在底)", "scribe", null, "sub- (下) + scribe (写) = 在契约下方写下名字 -> 订阅", "订阅；签名；捐款", "Users subscribe to premium vocabulary updates online.", "用户在线订阅优质词汇更新。"),
                BaseTemplate("prescribe", "prescribe", "pref_pre", "root_scrib", null, "pre- (提前)", "scribe", null, "pre- (提前) + scribe (写) = 提前在纸上写下处方/规章 -> 开处方", "开处方；规定；命令", "Doctors prescribe customized study schedules for review.", "医生开出定制的学习时间表以供复习。"),
                BaseTemplate("inscribe", "inscribe", "pref_im", "root_scrib", null, "in- (入)", "scribe", null, "in- (向内) + scribe (写) = 把文字写、刻进去 -> 铭刻", "铭刻；雕刻；题写", "Brave names are inscribed onto the victory monuments.", "勇敢的名字被刻在胜利纪念碑上。"),
                BaseTemplate("compress", "compress", "pref_co", "root_press", null, "com- (一起)", "press", null, "com- (一起) + press (压) = 共同往中间挤压 -> 压缩", "压缩；压紧；精简", "Software tools will compress database tables to save room.", "软件工具将压缩数据库表以节省空间。"),
                BaseTemplate("depress", "depress", "pref_de", "root_press", null, "de- (下)", "press", null, "de- (向下) + press (压) = 往拉去并压制激情 -> 抑郁；萧条", "使沮丧；使萧条；按下", "Prolonged darkness might depress active bionic systems.", "长时间的黑暗可能会压制大系统的热情。"),
                BaseTemplate("impress", "impress", "pref_im", "root_press", null, "in- (入)", "press", null, "in- (向里) + press (压) = 把印记深深压进内心 -> 留下印象", "给人留下深刻印象；盖印", "Polished presentation visuals will impress the jury boards.", "精美的演讲视觉效果会给评审团留下深刻印象。"),
                BaseTemplate("oppress", "oppress", "pref_dis", "root_press", null, "ob-", "press", null, "ob- (反对之下) + press (压) = 施加压力对抗 -> 压迫", "压迫；压制；折磨", "Cruel rules will oppress local learning initiatives.", "残酷的规则会压制当地的学习主动性。"),
                BaseTemplate("suppress", "suppress", "pref_sub", "root_press", null, "sub-", "press", null, "sub- (在下) + press (压) = 往下紧紧压制住反抗 -> 镇压", "镇压；抑制；查禁", "Winds cannot suppress the warm light of memory seeds.", "风无法抑制记忆种子的温热光芒。"),
                BaseTemplate("construct", "construct", "pref_co", "root_struct", null, "con- (共同)", "struct", null, "con- (共同) + struct (搭建) = 共同高耸搭建 -> 建造", "建造；构筑；创立", "They will construct a secure physical database server.", "他们将建造一个安全的物理数据库服务器。"),
                BaseTemplate("destruct", "destruct", "pref_de", "root_struct", null, "de- (下/反)", "struct", null, "de- (下/反) + struct (造) = 拆毁建造结果 -> 破坏", "破坏；消灭；毁坏", "Do not destruct active component layout keys.", "不要破坏现有的组件布局键。"),
                BaseTemplate("instruct", "instruct", "pref_im", "root_struct", null, "in- (入)", "struct", null, "in- (向内) + struct (搭建) = 往别人脑子里搭建知识架构 -> 指导", "指导；教授；命令", "Manual guidelines instruct users how to verify databases.", "手册指南指导用户如何验证数据库。"),
                BaseTemplate("obstruct", "obstruct", "pref_dis", "root_struct", null, "ob-", "struct", null, "ob- (反) + struct (建) = 逆向修建一堵堵截墙 -> 阻碍", "阻碍；妨碍；阻塞", "Fallen logs obstruct local roads in the forest hills.", "倒伏的木头阻塞了森林山丘的局部道路。"),
                BaseTemplate("nonsense", "nonsense", "pref_dis", "root_sens", null, "non-", "sense", null, "non- (否定) + sense (意味) = 毫无理性头绪的废话 -> 胡说", "胡说；废话；荒唐事", "Unverified logic is pure nonsense in database coding.", "在数据库编码中，未经验证的逻辑纯属胡说。"),
                BaseTemplate("sentiment", "sentiment", null, "root_sens", "suff_ment", null, "senti", "-ment", "senti (感觉) + -ment (名后缀) = 心灵产生的情绪结果 -> 感情", "感情；情绪；意见", "The core sentiment of this novel represents deep warmth.", "这部小说的核心情感代表着深沉的温热。"),
                BaseTemplate("sensible", "sensible", null, "root_sens", "suff_able", null, "sens", "-ible", "sens (理智) + -ible (能...的) = 能够理智清醒决定的 -> 明智的", "明智的；合乎情理的；可感觉到的", "It is sensible to compile applets after multiple layout edits.", "在多次布局编辑后编译小测件是明智的。"),
                BaseTemplate("infinite", "infinite", "pref_im", "root_fin", null, "in- (无)", "fin", null, "in- (无) + fin (界限) = 没有边际限制的 -> 无穷的", "无限的；无穷的", "Space represents infinite potentials for exploration.", "太空代表着无限的探索潜力。"),
                BaseTemplate("finish", "finish", null, "root_fin", null, null, "fin", null, "fin (界限) + -ish (动词尾) = 抵达最后一个范围界定 -> 结束", "完成；结束；终饰", "Can you finish organizing the vocabulary folders before midnight?", "你能在半夜前整理完词汇文件夹吗？"),
                BaseTemplate("refine", "refine", "pref_re", "root_fin", null, "re- (再)", "fin", null, "re- (再次) + fin (划界限/提纯) = 重新加工使其达到极致精度 -> 提炼；改善", "精炼；提纯；改善", "They refine core algorithms to lower power consumption.", "他们精炼核心算法以降低功耗。"),
                BaseTemplate("definite", "definite", "pref_de", "root_fin", null, "de- (下/加强)", "fin", null, "de- (加强) + fin (限度) = 往下清清楚楚画出边框 -> 明确的", "明确的；肯定的；限定的", "The system has a definite dependency catalog setup.", "该系统具有明确的依赖项目录设置。"),
                BaseTemplate("refer", "refer", "pref_re", "root_fer", null, "re- (回)", "fer", null, "re- (往回/转) + fer (带来) = 指向原产地、转交给某人 -> 参考/提及", "参考；提及；指称", "Scientists refer to standard atomic guidelines for measures.", "科学家参考标准原子指南进行测量。"),
                BaseTemplate("confer", "confer", "pref_co", "root_fer", null, "con- (一起)", "fer", null, "con- (一起) + fer (带) = 把各自的意见带到一起来商讨 -> 协商/授予", "协商；商讨；授予", "The assembly experts will confer about structural repairs.", "组装专家们将商讨结构修复方案。"),
                BaseTemplate("prefer", "prefer", "pref_pre", "root_fer", null, "pre- (提前)", "fer", null, "pre- (提前) + fer (端过来/带) = 优先把爱吃的东西端在身前 -> 宁愿/更喜欢", "更喜欢；宁愿；提升", "We prefer dark neon slate colors for visual comfort.", "我们更喜欢暗霓虹石板色以增加视觉舒适度。"),
                BaseTemplate("infer", "infer", "pref_im", "root_fer", null, "in- (往里)", "fer", null, "in- (入) + fer (带) = 从已知的线索中往里挖出结论 -> 推断", "推断；推论；暗示", "Logicians infer clean answers using deconstruction steps.", "逻辑学家使用解构步骤推断出干净的答案。"),
                BaseTemplate("produce", "produce", "pref_pro", "root_duc", null, "pro- (向前)", "duce", null, "pro- (往前) + duce (引领) = 引领着制造出新产品送到面前 -> 生产", "生产；产品；制造", "Factories produce customized silicon prefixes efficiently.", "工厂高效地生产定制硅前缀。"),
                BaseTemplate("reduce", "reduce", "pref_de", "root_duc", null, "re- (回)", "duce", null, "re- (往回) + duce (拉引) = 把水平线低拉退回 -> 减少", "减少；降低；简化", "Proper UI scales can reduce visual cognitive loads.", "适当的UI比例可以减少视觉认知负荷。"),
                BaseTemplate("introduce", "introduce", "pref_inter", "root_duc", null, "intro-", "duce", null, "intro- (向内) + duce (领) = 把外人引领介绍进内部关系 -> 介绍", "介绍；引进；采用", "The teachers introduce phonetic formulas to kids in detail.", "老师们向孩子们详细介绍语音公式。"),
                BaseTemplate("induce", "induce", "pref_im", "root_duc", null, "in- (入)", "duce", null, "in- (往里) + duce (牵引) = 引诱、诱导其进入圈套 -> 诱导", "诱导；引起；感应", "Chemical changes induce electricity flows in wire labs.", "化学变化在导线实验室中引起电流。"),
                BaseTemplate("deduce", "deduce", "pref_de", "root_duc", null, "de- (下)", "duce", null, "de- (往下) + duce (引导/扯出) = 从大前提引出具体的结论 -> 演绎；推断", "演绎；推论；推断", "From these visual details, we can deduce logical rules.", "从这些视觉细节中，我们可以推断出 logical rules。")
            )

            val allGeneratedWords = mutableListOf<WordFormula>()
            allGeneratedWords.addAll(wordSeeds)
            val existingIds = wordSeeds.map { it.id }.toSet()

            for (base in baseTemplates) {
                // 1. Plural
                val idPlural = "${base.id}_plural"
                if (!existingIds.contains(idPlural)) {
                    val spellCopy = if (base.spell.endsWith("y")) base.spell.dropLast(1) + "ies" else base.spell + "s"
                    allGeneratedWords.add(
                        WordFormula(
                            id = idPlural,
                            spell = spellCopy,
                            prefixId = base.prefixId,
                            rootId = base.rootId,
                            suffixId = base.suffixId,
                            prefixPart = base.prefixPart,
                            rootPart = base.rootPart,
                            suffixPart = base.suffixPart,
                            literalFormula = "${base.formulaText} (复数状态)",
                            chineseMeaning = "${base.chinese.substringBefore("；")} (动词复数/名词复数)",
                            sentenceEn = "The modern laboratories showcase these ${spellCopy} clearly.",
                            sentenceZh = "现代实验室清楚地展示了这些${spellCopy}。"
                        )
                    )
                }

                // 2. Past
                val idPast = "${base.id}_past"
                if (!existingIds.contains(idPast)) {
                    val spellCopy = if (base.spell.endsWith("e")) base.spell + "d" else base.spell + "ed"
                    allGeneratedWords.add(
                        WordFormula(
                            id = idPast,
                            spell = spellCopy,
                            prefixId = base.prefixId,
                            rootId = base.rootId,
                            suffixId = base.suffixId,
                            prefixPart = base.prefixPart,
                            rootPart = base.rootPart,
                            suffixPart = base.suffixPart,
                            literalFormula = "${base.formulaText} (已发生形式)",
                            chineseMeaning = "完成了的 / 被" + base.chinese.substringBefore("；") + "了的",
                            sentenceEn = "We successfully ${spellCopy} the central prefix node yesterday.",
                            sentenceZh = "我们昨天成功${base.chinese.substringBefore("；")}了中心前缀节点。"
                        )
                    )
                }

                // 3. Present progress
                val idIng = "${base.id}_ing"
                if (!existingIds.contains(idIng)) {
                    val spellCopy = if (base.spell.endsWith("e")) base.spell.dropLast(1) + "ing" else base.spell + "ing"
                    allGeneratedWords.add(
                        WordFormula(
                            id = idIng,
                            spell = spellCopy,
                            prefixId = base.prefixId,
                            rootId = base.rootId,
                            suffixId = base.suffixId,
                            prefixPart = base.prefixPart,
                            rootPart = base.rootPart,
                            suffixPart = base.suffixPart,
                            literalFormula = "${base.formulaText} (进行态)",
                            chineseMeaning = "正在 / 倾向于" + base.chinese.substringBefore("；") + "中",
                            sentenceEn = "The system is busy ${spellCopy} high-density memory packages.",
                            sentenceZh = "系统正忙于${base.chinese.substringBefore("；")}高密度内存包。"
                        )
                    )
                }

                // 4. Agent
                val idAgent = "${base.id}_agent"
                if (!existingIds.contains(idAgent)) {
                    val spellCopy = if (base.spell.endsWith("e")) base.spell.dropLast(1) + "er" else base.spell + "er"
                    allGeneratedWords.add(
                        WordFormula(
                            id = idAgent,
                            spell = spellCopy,
                            prefixId = base.prefixId,
                            rootId = base.rootId,
                            suffixId = "suff_ity",
                            prefixPart = base.prefixPart,
                            rootPart = base.rootPart,
                            suffixPart = "-er (执行人/装置)",
                            literalFormula = "${base.rootPart ?: "base"} + -er (执行人)",
                            chineseMeaning = "执行 " + base.chinese.substringBefore("；") + " 任务的媒介主体/学者",
                            sentenceEn = "He acted as a certified ${spellCopy} for the research base.",
                            sentenceZh = "他担任该研究基地的持证${base.chinese.substringBefore("；")}人员。"
                        )
                    )
                }

                // 5. Adverb
                val idLy = "${base.id}_ly"
                if (!existingIds.contains(idLy)) {
                    val spellCopy = base.spell + "ly"
                    allGeneratedWords.add(
                        WordFormula(
                            id = idLy,
                            spell = spellCopy,
                            prefixId = base.prefixId,
                            rootId = base.rootId,
                            suffixId = null,
                            prefixPart = base.prefixPart,
                            rootPart = base.rootPart,
                            suffixPart = "-ly (副词尾)",
                            literalFormula = "${base.formulaText} + -ly (修饰状态)",
                            chineseMeaning = base.chinese.substringBefore("；") + "地",
                            sentenceEn = "They managed the setup extremely ${spellCopy}.",
                            sentenceZh = "他们极其${base.chinese.substringBefore("；")}地管理了设置。"
                        )
                    )
                }

                // 6. Action state
                val idState = "${base.id}_state"
                if (!existingIds.contains(idState)) {
                    val spellCopy = if (base.spell.endsWith("e")) base.spell.dropLast(1) + "ation" else base.spell + "ation"
                    allGeneratedWords.add(
                        WordFormula(
                            id = idState,
                            spell = spellCopy,
                            prefixId = base.prefixId,
                            rootId = base.rootId,
                            suffixId = "suff_ion",
                            prefixPart = base.prefixPart,
                            rootPart = base.rootPart,
                            suffixPart = "-ation (名词尾)",
                            literalFormula = "${base.rootPart ?: "base"} + -ation (动作整合体)",
                            chineseMeaning = base.chinese.substringBefore("；") + "的行为过程与规律",
                            sentenceEn = "The ${spellCopy} of the formula took three full hours.",
                            sentenceZh = "公式的${base.chinese.substringBefore("；")}过程整整花了三个小时。"
                        )
                    )
                }

                // 7. Ability property
                val idAble = "${base.id}_able"
                if (!existingIds.contains(idAble)) {
                    val spellCopy = if (base.spell.endsWith("e")) base.spell.dropLast(1) + "able" else base.spell + "able"
                    allGeneratedWords.add(
                        WordFormula(
                            id = idAble,
                            spell = spellCopy,
                            prefixId = base.prefixId,
                            rootId = base.rootId,
                            suffixId = "suff_able",
                            prefixPart = base.prefixPart,
                            rootPart = base.rootPart,
                            suffixPart = "-able (形容词尾)",
                            literalFormula = "${base.rootPart ?: "base"} + -able (可具有性的)",
                            chineseMeaning = "可 " + base.chinese.substringBefore("；") + " 的 / 易被影响的",
                            sentenceEn = "This modular design is highly ${spellCopy}.",
                            sentenceZh = "这种模块化设计是高度可${base.chinese.substringBefore("；")}的。"
                        )
                    )
                }

                // 8. Quality property (ive)
                val idIve = "${base.id}_ive"
                if (!existingIds.contains(idIve)) {
                    val spellCopy = if (base.spell.endsWith("e")) base.spell.dropLast(1) + "ive" else base.spell + "ive"
                    allGeneratedWords.add(
                        WordFormula(
                            id = idIve,
                            spell = spellCopy,
                            prefixId = base.prefixId,
                            rootId = base.rootId,
                            suffixId = "suff_ive",
                            prefixPart = base.prefixPart,
                            rootPart = base.rootPart,
                            suffixPart = "-ive (形容词尾)",
                            literalFormula = "${base.rootPart ?: "base"} + -ive (强劲属性体)",
                            chineseMeaning = "有 " + base.chinese.substringBefore("；") + " 倾向或质感的",
                            sentenceEn = "We should adopt a highly ${spellCopy} strategy.",
                            sentenceZh = "我们应该采取高度具有${base.chinese.substringBefore("；")}性的策略。"
                        )
                    )
                }

                // 9. Re-execute action
                val idRe = "re_${base.id}"
                if (!existingIds.contains(idRe)) {
                    val spellCopy = "re" + base.spell
                    allGeneratedWords.add(
                        WordFormula(
                            id = idRe,
                            spell = spellCopy,
                            prefixId = "pref_re",
                            rootId = base.rootId,
                            suffixId = base.suffixId,
                            prefixPart = "re- (重新)",
                            rootPart = base.rootPart,
                            suffixPart = base.suffixPart,
                            literalFormula = "re- (再) + ${base.rootPart ?: "base"} = 再次执行",
                            chineseMeaning = "重新" + base.chinese.substringBefore("；"),
                            sentenceEn = "The team had to ${spellCopy} the sequence config.",
                            sentenceZh = "团队不得不重新${base.chinese.substringBefore("；")}序列设置。"
                        )
                    )
                }

                // 10. De-reverse execute
                val idDe = "de_${base.id}"
                if (!existingIds.contains(idDe)) {
                    val spellCopy = "de" + base.spell
                    allGeneratedWords.add(
                        WordFormula(
                            id = idDe,
                            spell = spellCopy,
                            prefixId = "pref_de",
                            rootId = base.rootId,
                            suffixId = base.suffixId,
                            prefixPart = "de- (向下/解除)",
                            rootPart = base.rootPart,
                            suffixPart = base.suffixPart,
                            literalFormula = "de- (相反/解除) + ${base.rootPart ?: "base"} = 解除该性质",
                            chineseMeaning = "去/反向/解除" + base.chinese.substringBefore("；"),
                            sentenceEn = "This step will ${spellCopy} the standard alignment matrix.",
                            sentenceZh = "此步骤将反向${base.chinese.substringBefore("；")}标准对齐矩阵。"
                        )
                    )
                }

                // 11. Cojoint execution
                val idCo = "co_${base.id}"
                if (!existingIds.contains(idCo)) {
                    val spellCopy = "co" + base.spell
                    allGeneratedWords.add(
                        WordFormula(
                            id = idCo,
                            spell = spellCopy,
                            prefixId = "pref_co",
                            rootId = base.rootId,
                            suffixId = base.suffixId,
                            prefixPart = "co- (联合)",
                            rootPart = base.rootPart,
                            suffixPart = base.suffixPart,
                            literalFormula = "co- (共) + ${base.rootPart ?: "base"} = 协同操作",
                            chineseMeaning = "协同" + base.chinese.substringBefore("；") + " / 联合进行",
                            sentenceEn = "This tool can help developers co-${base.spell} components.",
                            sentenceZh = "此工具可帮助物理人员协同${base.chinese.substringBefore("；")}组件。"
                        )
                    )
                }

                // 12. Sub-level property
                val idSub = "sub_${base.id}"
                if (!existingIds.contains(idSub)) {
                    val spellCopy = "sub" + base.spell
                    allGeneratedWords.add(
                        WordFormula(
                            id = idSub,
                            spell = spellCopy,
                            prefixId = "pref_sub",
                            rootId = base.rootId,
                            suffixId = base.suffixId,
                            prefixPart = "sub- (次级/在下)",
                            rootPart = base.rootPart,
                            suffixPart = base.suffixPart,
                            literalFormula = "sub- (下) + ${base.rootPart ?: "base"} = 下级动作状态",
                            chineseMeaning = "局部/次级" + base.chinese.substringBefore("；"),
                            sentenceEn = "The server executes a secondary sub-${base.spell} process.",
                            sentenceZh = "服务器执行次级的${base.chinese.substringBefore("；")}局部过程。"
                        )
                    )
                }

                // 13. Pre-execute setting
                val idPre = "pre_${base.id}"
                if (!existingIds.contains(idPre)) {
                    val spellCopy = "pre" + base.spell
                    allGeneratedWords.add(
                        WordFormula(
                            id = idPre,
                            spell = spellCopy,
                            prefixId = "pref_pre",
                            rootId = base.rootId,
                            suffixId = base.suffixId,
                            prefixPart = "pre- (预先)",
                            rootPart = base.rootPart,
                            suffixPart = base.suffixPart,
                            literalFormula = "pre- (前) + ${base.rootPart ?: "base"} = 事先运作",
                            chineseMeaning = "事先" + base.chinese.substringBefore("；"),
                            sentenceEn = "You must pre-${base.spell} the materials prior to assembly.",
                            sentenceZh = "在组装之前，您必须事先${base.chinese.substringBefore("；")}材料。"
                        )
                    )
                }

                // 14. Inter-mutual relation
                val idInter = "inter_${base.id}"
                if (!existingIds.contains(idInter)) {
                    val spellCopy = "inter" + base.spell
                    allGeneratedWords.add(
                        WordFormula(
                            id = idInter,
                            spell = spellCopy,
                            prefixId = "pref_inter",
                            rootId = base.rootId,
                            suffixId = base.suffixId,
                            prefixPart = "inter- (相互)",
                            rootPart = base.rootPart,
                            suffixPart = base.suffixPart,
                            literalFormula = "inter- (相互之间) + ${base.rootPart ?: "base"} = 交叉运作",
                            chineseMeaning = "相互" + base.chinese.substringBefore("；"),
                            sentenceEn = "The dual modules inter-${base.spell} each other over the network.",
                            sentenceZh = "双重模块通过网络相互${base.chinese.substringBefore("；")}。"
                        )
                    )
                }

                // 15. Dis-opposite action
                val idDis = "dis_${base.id}"
                if (!existingIds.contains(idDis)) {
                    val spellCopy = "dis" + base.spell
                    allGeneratedWords.add(
                        WordFormula(
                            id = idDis,
                            spell = spellCopy,
                            prefixId = "pref_dis",
                            rootId = base.rootId,
                            suffixId = base.suffixId,
                            prefixPart = "dis- (相反/消除)",
                            rootPart = base.rootPart,
                            suffixPart = base.suffixPart,
                            literalFormula = "dis- (偏离) + ${base.rootPart ?: "base"} = 否定态度",
                            chineseMeaning = "中止/不" + base.chinese.substringBefore("；"),
                            sentenceEn = "Do not dis-${base.spell} the active signal credentials.",
                            sentenceZh = "不要中止${base.chinese.substringBefore("；")}有效信号凭证。"
                        )
                    )
                }

                // 16. Trans-change
                val idTrans = "trans_${base.id}"
                if (!existingIds.contains(idTrans)) {
                    val spellCopy = "trans" + base.spell
                    allGeneratedWords.add(
                        WordFormula(
                            id = idTrans,
                            spell = spellCopy,
                            prefixId = "pref_trans",
                            rootId = base.rootId,
                            suffixId = base.suffixId,
                            prefixPart = "trans- (转换)",
                            rootPart = base.rootPart,
                            suffixPart = base.suffixPart,
                            literalFormula = "trans- (变) + ${base.rootPart ?: "base"} = 转移形态",
                            chineseMeaning = "转移/异向" + base.chinese.substringBefore("；"),
                            sentenceEn = "The engine will trans-${base.spell} raw datasets to the dashboard.",
                            sentenceZh = "引擎将转移${base.chinese.substringBefore("；")}原始数据集到仪表盘。"
                        )
                    )
                }

                // 17. Un-not negation
                val idUn = "un_${base.id}"
                if (!existingIds.contains(idUn)) {
                    val spellCopy = "un" + base.spell
                    allGeneratedWords.add(
                        WordFormula(
                            id = idUn,
                            spell = spellCopy,
                            prefixId = "pref_dis",
                            rootId = base.rootId,
                            suffixId = base.suffixId,
                            prefixPart = "un- (非/否定)",
                            rootPart = base.rootPart,
                            suffixPart = base.suffixPart,
                            literalFormula = "un- (不) + ${base.rootPart ?: "base"} = 未执行此状态",
                            chineseMeaning = "非/不" + base.chinese.substringBefore("；"),
                            sentenceEn = "It was un-${base.spell} under the current default config.",
                            sentenceZh = "在当前默认设置下，这是不${base.chinese.substringBefore("；")}的。"
                        )
                    )
                }

                // 18. Noun Ment result
                val idMent = "${base.id}_ment"
                if (!existingIds.contains(idMent)) {
                    val spellCopy = base.spell + "ment"
                    allGeneratedWords.add(
                        WordFormula(
                            id = idMent,
                            spell = spellCopy,
                            prefixId = base.prefixId,
                            rootId = base.rootId,
                            suffixId = "suff_ment",
                            prefixPart = base.prefixPart,
                            rootPart = base.rootPart,
                            suffixPart = "-ment (名词尾)",
                            literalFormula = "${base.rootPart ?: "base"} + -ment (结果物)",
                            chineseMeaning = base.chinese.substringBefore("；") + "的行为产物/工具结构",
                            sentenceEn = "This dynamic ${spellCopy} provides robust physical support.",
                            sentenceZh = "这一动态${base.chinese.substringBefore("；")}产物提供了坚固的物理支撑。"
                        )
                    )
                }

                // 19. Nounity quality
                val idIty = "${base.id}_ity"
                if (!existingIds.contains(idIty)) {
                    val spellCopy = if (base.spell.endsWith("e")) base.spell.dropLast(1) + "ity" else base.spell + "ity"
                    allGeneratedWords.add(
                        WordFormula(
                            id = idIty,
                            spell = spellCopy,
                            prefixId = base.prefixId,
                            rootId = base.rootId,
                            suffixId = "suff_ity",
                            prefixPart = base.prefixPart,
                            rootPart = base.rootPart,
                            suffixPart = "-ity (名词尾)",
                            literalFormula = "${base.rootPart ?: "base"} + -ity (抽象本质)",
                            chineseMeaning = base.chinese.substringBefore("；") + "的属性特征/程度",
                            sentenceEn = "We must test the ${spellCopy} of our structural parameters.",
                            sentenceZh = "我们必须测试结构参数的${base.chinese.substringBefore("；")}程度。"
                        )
                    )
                }

                // 20. Adverbially
                val idIy = "${base.id}_ively"
                if (!existingIds.contains(idIy)) {
                    val spellCopy = if (base.spell.endsWith("e")) base.spell.dropLast(1) + "ively" else base.spell + "ively"
                    allGeneratedWords.add(
                        WordFormula(
                            id = idIy,
                            spell = spellCopy,
                            prefixId = base.prefixId,
                            rootId = base.rootId,
                            suffixId = "suff_ive",
                            prefixPart = base.prefixPart,
                            rootPart = base.rootPart,
                            suffixPart = "-ively (复合词尾)",
                            literalFormula = "${base.rootPart ?: "base"} + -ive + -ly",
                            chineseMeaning = "富有 " + base.chinese.substringBefore("；") + " 质感地",
                            sentenceEn = "The process successfully and ${spellCopy} updated settings.",
                            sentenceZh = "该过程成功且富有${base.chinese.substringBefore("；")}感地更新了配置。"
                        )
                    )
                }

                // 21. UnAble
                val idUnAble = "un_${base.id}_able"
                if (!existingIds.contains(idUnAble)) {
                    val spellCopy = "un" + (if (base.spell.endsWith("e")) base.spell.dropLast(1) + "able" else base.spell + "able")
                    allGeneratedWords.add(
                        WordFormula(
                            id = idUnAble,
                            spell = spellCopy,
                            prefixId = "pref_dis",
                            rootId = base.rootId,
                            suffixId = "suff_able",
                            prefixPart = "un- (否定)",
                            rootPart = base.rootPart,
                            suffixPart = "-able (能)",
                            literalFormula = "un- + ${base.rootPart ?: "base"} + -able = 无法被其作用的",
                            chineseMeaning = "不可" + base.chinese.substringBefore("；") + "的",
                            sentenceEn = "At high temperature, the chemical state becomes completely ${spellCopy}.",
                            sentenceZh = "在高温下，化学状态变得完全不可${base.chinese.substringBefore("；")}。"
                        )
                    )
                }

                // 22. UnIve
                val idUnIve = "un_${base.id}_ive"
                if (!existingIds.contains(idUnIve)) {
                    val spellCopy = "un" + (if (base.spell.endsWith("e")) base.spell.dropLast(1) + "ive" else base.spell + "ive")
                    allGeneratedWords.add(
                        WordFormula(
                            id = idUnIve,
                            spell = spellCopy,
                            prefixId = "pref_dis",
                            rootId = base.rootId,
                            suffixId = "suff_ive",
                            prefixPart = "un- (不)",
                            rootPart = base.rootPart,
                            suffixPart = "-ive (倾向)",
                            literalFormula = "un- + ${base.rootPart ?: "base"} + -ive = 缺乏相应倾向的",
                            chineseMeaning = "不带" + base.chinese.substringBefore("；") + "倾向的",
                            sentenceEn = "He gave an ${spellCopy} gesture towards the deconstructive design.",
                            sentenceZh = "他对解构性设计做出了一个不带${base.chinese.substringBefore("；")}倾向的手势。"
                        )
                    )
                }

                // 23. ReRe
                val idReRe = "re_${base.id}_past"
                if (!existingIds.contains(idReRe)) {
                    val spellCopy = "re" + (if (base.spell.endsWith("e")) base.spell + "d" else base.spell + "ed")
                    allGeneratedWords.add(
                        WordFormula(
                            id = idReRe,
                            spell = spellCopy,
                            prefixId = "pref_re",
                            rootId = base.rootId,
                            suffixId = base.suffixId,
                            prefixPart = "re- (再/重新)",
                            rootPart = base.rootPart,
                            suffixPart = base.suffixPart,
                            literalFormula = "re- (重新) + ${base.spell} + pasthash",
                            chineseMeaning = "被重新" + base.chinese.substringBefore("；") + "过了的",
                            sentenceEn = "The team analyzed the freshly re-${base.id} elements.",
                            sentenceZh = "团队分析了新被重新${base.chinese.substringBefore("；")}过的元素。"
                        )
                    )
                }

                // 24. PreIng
                val idPreIng = "pre_${base.id}_ing"
                if (!existingIds.contains(idPreIng)) {
                    val spellCopy = "pre" + (if (base.spell.endsWith("e")) base.spell.dropLast(1) + "ing" else base.spell + "ing")
                    allGeneratedWords.add(
                        WordFormula(
                            id = idPreIng,
                            spell = spellCopy,
                            prefixId = "pref_pre",
                            rootId = base.rootId,
                            suffixId = base.suffixId,
                            prefixPart = "pre- (在先)",
                            rootPart = base.rootPart,
                            suffixPart = base.suffixPart,
                            literalFormula = "pre- (预先) + ${base.spell} + ing",
                            chineseMeaning = "正在预先" + base.chinese.substringBefore("；") + "中",
                            sentenceEn = "They are currently pre-${base.id} standard setup coordinates.",
                            sentenceZh = "他们目前正在预先${base.chinese.substringBefore("；")}标准设置坐标。"
                        )
                    )
                }

                // 25. CoPast
                val idCoPast = "co_${base.id}_past"
                if (!existingIds.contains(idCoPast)) {
                    val spellCopy = "co" + (if (base.spell.endsWith("e")) base.spell + "d" else base.spell + "ed")
                    allGeneratedWords.add(
                        WordFormula(
                            id = idCoPast,
                            spell = spellCopy,
                            prefixId = "pref_co",
                            rootId = base.rootId,
                            suffixId = base.suffixId,
                            prefixPart = "co- (联合)",
                            rootPart = base.rootPart,
                            suffixPart = base.suffixPart,
                            literalFormula = "co- (共) + ${base.spell} + ed",
                            chineseMeaning = "被联合已" + base.chinese.substringBefore("；") + "的",
                            sentenceEn = "These co-${base.id} networks are functioning exceptionally well.",
                            sentenceZh = "这些联合已${base.chinese.substringBefore("；")}的网络运行异常良好。"
                        )
                    )
                }
            }

            lexiDao.insertWords(allGeneratedWords)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lexiforge_database"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

private data class BaseTemplate(
    val id: String,
    val spell: String,
    val prefixId: String?,
    val rootId: String?,
    val suffixId: String?,
    val prefixPart: String?,
    val rootPart: String?,
    val suffixPart: String?,
    val formulaText: String,
    val chinese: String,
    val sentenceEn: String,
    val sentenceZh: String
)

