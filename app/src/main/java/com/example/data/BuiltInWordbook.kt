package com.example.data

/** A deliberately small, explicit wordbook. Never manufacture vocabulary by adding affixes.
 * Example sentences are original teaching examples, not examination quotations.
 * See docs/content-review.md for references and the limits of the mnemonic breakdowns.
 */
object BuiltInWordbook {
    val words = listOf(
        word("attract", "pref_ad", "root_tract", null, "at-", "tract", null,
            "词源提示：at- 是 ad- 的同化形式；tract 表示拉。联想：把注意力拉过来。", "v. 吸引；引起",
            "Bright flowers attract bees.", "鲜艳的花朵吸引蜜蜂。"),
        word("import", "pref_im", "root_port", null, "im-", "port", null,
            "im-（向内）+ port（运送）。联想：把商品运进来。", "v. 进口；引入；n. 进口商品",
            "We import coffee from Brazil.", "我们从巴西进口咖啡。"),
        word("export", "pref_ex", "root_port", null, "ex-", "port", null,
            "ex-（向外）+ port（运送）。联想：把商品运出去。", "v. 出口；输出；n. 出口商品",
            "These farms export fruit to other countries.", "这些农场向其他国家出口水果。"),
        word("reject", "pref_re", "root_ject", null, "re-", "ject", null,
            "re-（向后）+ ject（投、扔）。联想：把不接受的东西退回去。", "v. 拒绝；不接受",
            "The editor may reject the article.", "编辑可能会拒绝这篇文章。"),
        word("project", "pref_pro", "root_ject", null, "pro-", "ject", null,
            "pro-（向前）+ ject（投）。词源有向前投出的含义；项目是其引申义。", "n. 项目；计划；v. 投射；预计",
            "Our next project is a school garden.", "我们的下一个项目是建设校园花园。"),
        word("predict", "pref_pre", "root_dict", null, "pre-", "dict", null,
            "pre-（预先）+ dict（说）。联想：预先说出将发生的事。", "v. 预测；预言",
            "It is hard to predict the weather.", "天气很难预测。"),
        word("describe", "pref_de", "root_scrib", null, "de-", "scribe", null,
            "词源提示：de- 有向下、完全等含义，scribe 与写有关。联想：写下细节。", "v. 描述；形容",
            "Can you describe your hometown?", "你能描述一下你的家乡吗？"),
        word("commit", "pref_co", "root_mit", null, "com-", "mit", null,
            "com- + mit（送、交付）是词源记忆提示；commit 有承诺、投入、犯下等不同用法。", "v. 承诺；投入；犯（罪、错）",
            "We commit to meeting every week.", "我们承诺每周见面。"),
        word("submit", "pref_sub", "root_mit", null, "sub-", "mit", null,
            "sub-（在下）+ mit（送、交付）。联想：递交材料供审核。", "v. 提交；屈服",
            "Please submit your report by Friday.", "请在星期五之前提交报告。"),
        word("extend", "pref_ex", "root_tend", null, "ex-", "tend", null,
            "ex-（向外）+ tend（伸展）。联想：向外伸长。", "v. 延长；扩展；伸出",
            "The library will extend its opening hours.", "图书馆将延长开放时间。"),
        word("visible", null, "root_vis", "suff_able", null, "vis", "-ible",
            "vis（看）+ -ible（可被……的）。联想：能够被看见。", "adj. 可见的；明显的",
            "The moon is visible tonight.", "今晚能看见月亮。"),
        word("inspect", "pref_im", "root_spect", null, "in-", "spect", null,
            "in-（向内）+ spect（看）。联想：仔细查看内部。", "v. 检查；视察",
            "The mechanic will inspect the brakes.", "机械师将检查刹车。"),
        word("express", "pref_ex", "root_press", null, "ex-", "press", null,
            "ex-（向外）+ press（压）。联想：把心里的想法表达出来。", "v. 表达；adj. 快速的；n. 快车",
            "Music helps us express our feelings.", "音乐帮助我们表达感受。"),
        word("structure", null, "root_struct", "suff_ure", null, "struct", "-ure",
            "struct（建造）+ -ure（名词后缀）。联想：各部分搭建成的结构。", "n. 结构；构造；v. 组织；安排",
            "We studied the structure of the bridge.", "我们研究了这座桥的结构。"),
        word("sensitive", null, null, null, null, "sensitive", null,
            "词源与感觉、感知有关。本词采用整体拼写练习，不把 sens + ive 当作完整拼写。", "adj. 敏感的；体贴的；需谨慎处理的",
            "My skin is sensitive to sunlight.", "我的皮肤对阳光敏感。"),
        word("definition", null, null, null, null, "definition", null,
            "可与 define（下定义）联系记忆；名词拼写发生变化，本词采用整体拼写练习。", "n. 定义；释义；清晰度",
            "Please give a clear definition of this term.", "请给这个术语下一个清楚的定义。"),
        word("transfer", "pref_trans", "root_fer", null, "trans-", "fer", null,
            "trans-（跨越）+ fer（携带）。联想：从一处带到另一处。", "v. 转移；调动；转账；n. 转移",
            "You can transfer the money online.", "你可以在网上转账。"),
        word("transport", "pref_trans", "root_port", null, "trans-", "port", null,
            "trans-（跨越）+ port（运送）。联想：把人或货物运到别处。", "v. 运输；n. 交通运输",
            "These trucks transport food to the city.", "这些卡车把食物运到城里。")
    )

    val components = listOf(
        component("pref_ad", "ad- / at-", "PREFIX", "朝向；接近", "attract"),
        component("pref_im", "in- / im-", "PREFIX", "向内（在本词书所选词中）", "import"),
        component("pref_ex", "ex-", "PREFIX", "向外", "export"),
        component("pref_re", "re-", "PREFIX", "向后；再次（随词而异）", "reject"),
        component("pref_pro", "pro-", "PREFIX", "向前", "project"),
        component("pref_pre", "pre-", "PREFIX", "预先；在前", "predict"),
        component("pref_de", "de-", "PREFIX", "向下；完全（随词而异）", "describe"),
        component("pref_co", "co- / con- / com-", "PREFIX", "共同；也可起加强作用", "commit"),
        component("pref_sub", "sub-", "PREFIX", "在下", "submit"),
        component("pref_trans", "trans-", "PREFIX", "跨越；转移", "transfer"),
        component("root_tract", "tract", "ROOT", "拉；引", "attract"),
        component("root_port", "port", "ROOT", "携带；运送", "import"),
        component("root_ject", "ject", "ROOT", "投；扔", "reject"),
        component("root_dict", "dict", "ROOT", "说", "predict"),
        component("root_scrib", "scrib / scribe", "ROOT", "写", "describe"),
        component("root_mit", "mit / miss", "ROOT", "送；交付", "submit"),
        component("root_tend", "tend / tens", "ROOT", "伸展", "extend"),
        component("root_vis", "vis / vid", "ROOT", "看", "visible"),
        component("root_spect", "spect", "ROOT", "看", "inspect"),
        component("root_press", "press", "ROOT", "压", "express"),
        component("root_struct", "struct", "ROOT", "建造", "structure"),
        component("root_fer", "fer", "ROOT", "带；运送", "transfer"),
        component("suff_able", "-able / -ible", "SUFFIX", "可被……的；能……的", "visible"),
        component("suff_ure", "-ure", "SUFFIX", "名词后缀，可表结果、状态等", "structure")
    )

    private fun word(spell: String, prefix: String?, root: String?, suffix: String?,
        prefixPart: String?, rootPart: String, suffixPart: String?, note: String,
        meaning: String, sentence: String, translation: String) = WordFormula(
        id = spell, spell = spell, prefixId = prefix, rootId = root, suffixId = suffix,
        prefixPart = prefixPart, rootPart = rootPart, suffixPart = suffixPart,
        literalFormula = note, chineseMeaning = meaning, sentenceEn = sentence,
        sentenceZh = translation, frequencyGroup = "未分级"
    )

    private fun component(id: String, name: String, type: String, meaning: String, anchor: String): AffixComponent {
        val word = words.first { it.id == anchor }
        return AffixComponent(id, name, type, when (type) {
            "PREFIX" -> "➡️"; "ROOT" -> "🌱"; else -> "🔗"
        }, meaning, "$anchor (${word.chineseMeaning})", word.literalFormula)
    }
}
