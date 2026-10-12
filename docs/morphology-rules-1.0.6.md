# 1.0.6：扩展离线拆词规则

规则包版本 `2026.10.02.1`。在 1.0.5 的 36 条词缀规则上增加 15 条，共 51 条；完全离线计算，不接入 AI API 或模型。已有单词的学习状态、阶段及复习时间保留，首次启动会自动重新分析当前词书；不需要重新下载或重置进度。

## 本批增加什么

| 类别 | 新增规则 | 条件与示例 |
| --- | --- | --- |
| 否定前缀 | `in-/im-/il-/ir-` | 形容词词基、对应形容词义项的否定线索，并检查词基首字母；inactive、impossible、illegal、irregular |
| 行为/状态名词 | `-ation/-ance/-ence` | 动词词基；information、performance、dependence |
| 人或工具 | `-or/-ist` | 限定词性及人员/工具释义线索；actor、artist、pianist |
| 思想/制度等 | `-ism` | 限定词性与释义线索；socialism |
| 性质形容词 | `-ive` | 动词词基；active、creative |
| 动词派生 | `-ize/-ise/-ify/-en` | 名词或形容词词基；modernize、modernise、simplify、widen |

拼写变化新增：

- reliance：`rely → reli + -ance`。
- organization：`organize → organiz + -ation`。
- qualification：`qualify → qualific + -ation`，限定 -ify/-ific 变化。
- memorize：`memory → memor + -ize`；beautify：`beauty → beaut + -ify`。
- pianist：`piano → pian + -ist`，去 o 只适用于这个明确词基。
- decision、division、conclusion：在 -cis/-vis/-clus 词族中识别对应 -cide/-vide/-clude 词基。
- admission、permission：在 -miss 形式中识别 -mit 词基。
- revision、confusion：识别 -vise/-fuse 去 e 的形式。

以上分块用于拼写训练，不把字母变形宣称为通用历史音变；仍要求当前词书存在原形。不是任意 d/t/s 替换，也不把未出现的中间词补造出来。

## 降低误拆

词性与释义现在按段对应：否定形容词的条件只查看形容词释义，不能借用同词名词义项中的“不”字。支持 `vi/vt.` 等斜线组合及原有 `n & vt.` 格式，大小写转换不受手机语言地区影响。

in- 家族只处理否定形容词：im- 限 b/m/p 开头词基，il- 限 l，ir- 限 r；其余允许的首字母使用 in-。inflammable、invaluable、infamous 等不按普通否定处理；impose/inform 等也不会被当作否定形容词。

原有例外表继续生效，并排除 objective/subjective/executive 等不适合本批简单动词派生解释的词。真实词书检查后还排除了 finance/fine、importance/import、passive/pass、indifferent/different、competence/compete、authorize/author 等易误导配对。排除只针对目标词，不禁止这些词充当其他派生词的词基，例如组织机构 organization 仍可匹配 organize。

存在多个同级词基候选时仍退回整词。先匹配示范解析，再匹配一个最外层后缀，最后考虑前缀；不新增无限递归拆解。

界面继续标为“规则推导”，尚未经逐词人工审核。51 条是规则数量，命中数量不是准确率。词基不在当前词书、词性缺失、语义线索不足或转换超出范围时，仍使用整词训练。词频仍为“未分级”。

## 内容维护与参考

规则位于 `app/src/main/java/com/example/data/MorphologyRules.kt`。新增或放宽规则必须同时增加正确示例、误拆反例、候选冲突和拼写还原测试。相同单词在两本词书中的结果可能不同，因为词基和词性可用性不同；不承诺全词库覆盖。

## 验证与安装

使用 1.0.5 留存的同一批上游样本，于 2026-10-02 完成验证；未把上游内容变化算作规则提升。

| 词书 | 总词条 | 1.0.5 可分块 | 1.0.6 可分块 | 其中规则推导 |
| --- | ---: | ---: | ---: | ---: |
| 四级 | 4,544 | 436 | 581 | 565 |
| 六级 | 3,992 | 281 | 395 | 386 |

净增四级 145 个、六级 114 个。对应词性释义检查也撤回了旧规则的四级 overhead、六级 discount 命中；这体现精度约束，不是为了增加数量保留旧结果。当前仍有大部分词使用整词拼写。

84 项测试全部通过，失败、错误、跳过均为 0。覆盖新增词缀与拼写变化、否定前缀首字母限制、跨词性义项误匹配、斜线词性与土耳其语地区设置、假朋友反例、旧进度保留、备份往返、实际词书导入幂等，以及已有练习界面与发音逻辑回归。APK 构建和签名验证通过；未在用户的一加 ACE2 Pro 真机验证。

安装包为 1.0.6 / versionCode 7，包名与此前相同；签名 SHA-256 为 `77dcae457eff638df59d400554939c99423039435b7cc2b3789d6640f6d64a05`。可覆盖此前同签名开发版，保留进度时不要先卸载。升级首次启动自动修正词书并保存恢复点；无需重新下载词书。

参考：[Cambridge 否定前缀](https://dictionary.cambridge.org/uk/grammar/british-grammar/prefixes-and-suffixes)、[动词构词](https://dictionary.cambridge.org/grammar/british-grammar/verbs-formation)、[拼写变化](https://dictionary.cambridge.org/uk/grammar/british-grammar/spelling-and-verb-forms)、[Oxford 拼写教学材料](https://cdn.oxfordowl.co.uk/2016/09/02/14/11/17/346/bp_A_Week_of_Teaching_Spelling.pdf)。参考资料支持一般构词现象，不为每个规则生成的词条背书。未将参考网页或词典正文打包入应用。
