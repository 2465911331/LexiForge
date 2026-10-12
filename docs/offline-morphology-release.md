# 1.0.4：离线解析与内容修正

本批落实“不调用 AI API”的约束。未接入 AI 服务、代理或模型；下载好的词书与解析正文离线可用。原有 GitHub 下载和可选在线发音继续保留，点击参考资料会明确打开浏览器联网。

## 用户能看到的变化

- 不再按词库文件顺序生成高、中、低频；现有词书和新导入词书均标为“未分级”。真实词频统计仍未接入。
- 不再根据开头、结尾或包含某段字母就自动生成构词答案。删除原 `analyzeMorphology` 猜测逻辑及虚拟词根生成逻辑。
- 新增本地解析匹配：18 个示范词条，其中 16 个支持词源相关的拼写分块，sensitive 与 definition 保持整词拼写。已有内置教学内容作为首个小规模解析包，并为条目补充参考地址、匹配边界及包版本。
- 未匹配的词直接显示整词学习和拼写，不把剩余字母标成“词根”，也不把缺少解析误说成“没有词缀”。
- 词条详情和学习页显示本地解析状态、版本与词源参考；词源提示和联想不解释所有义项，也不宣称是完整历史演变。
- 设置页展示当前词书可分块练习与整词训练的数量。修正原练习成功文案中的“CET-4 APPROVED”，不暗示考试机构认可。
- 新导入合并同一来源、相同身份和拼写的重复记录，保留不同释义；大小写不同的词条分开。旧版导入是后记录覆盖前记录，本次不会凭空恢复以前已经丢弃的释义。

## 旧数据与备份

应用启动时，在进入学习页前检查旧构词和词频字段。如需修正，先保存恢复点，再通过已实现的持久化操作记录与数据库事务更新。

单词 ID、拼写、释义、实际例句、学习状态、阶段及复习时间保留。旧模板例句被清空，不再显示成教学例句。以前未经核对的组件仍保存在数据库和备份中，其进度不删除，但排除在词根卡片及统计之外；本地已知组件按现有 ID 保留进度并更正教学内容。

恢复旧备份时同样会应用本地解析规则；预览会提示内容修订，保留备份里的学习阶段和期限。恢复点保留旧数据原文，但不会通过“恢复”重新启用不可靠拆解。更新是幂等的，重复启动不会不断创建恢复点或重置学习记录。

只有单词、没有组件的外部词书也可正常使用，启动时不会因此被内置词书覆盖。数据库结构继续为版本 3，备份格式继续为版本 1；版本号为 1.0.4 / versionCode 5。

## 匹配边界

`OfflineMorphology` 是当前内置解析包，版本 `2026.09.26.1`。匹配限于现有受支持词书来源，并要求单词拼写和预设中文义项线索相符；大写专名、未知来源、未知义项和未收录的词均回退为整词。

这种义项线索匹配是保守的适用范围筛选，不是完整的语义消歧模型。后续更大解析包仍需增加明确的来源版本与义项映射，不能按拼写无限扩用。本批不会把一个词的解析自动推广到所有派生词。

`validatePack()` 检查所有分块能够还原目标拼写、组件引用和类型正确。练习页与提交逻辑还会检查词条是否确实匹配当前解析内容，不能仅凭一个旧 prefixId 开启拼装判题。

解析说明为项目现有中文教学简述；参考资料用于核对词源关系，未打包词典原文。它仍是开发版内容，未经过独立语言学专家审核，不能宣传为覆盖完整四、六级词库。

## 导入器与实际词库验证

导入逻辑从 ViewModel 移到 `WordbookImporter`，解析在后台线程执行，下载上限 24 MiB，输入记录上限 20,000。空词条、缺失释义、冲突 ID、损坏结构及超限数据在替换前拒绝；失败保留原词书。

除现有 JSON 数组外，读取器也支持上游 simple/sentence/full JSONL，保留存在的第一条例句，full 格式保留上游 wordId。当前下载选择器仍使用已有 JSON 地址；音标、多例句、短语、同根词的完整存储与展示未在本批接入，不宣称完整导入所有 full 字段。

2026-09-26 下载当前应用使用的上游 JSON 做集成验证，输入与解析结果如下。数量是这次样本结果，不能解释为考试大纲覆盖率或词频。

| 文件 | 原记录数 | 重复拼写组数 | 合并后词条 | 匹配分块练习 |
| --- | ---: | ---: | ---: | ---: |
| `json/3-CET4-顺序.json` | 7,508 | 2,354 | 4,544 | 16 |
| `json/4-CET6-顺序.json` | 5,651 | 1,424 | 3,992 | 9 |

样本 SHA-256：

- 四级：`067354afaa976a3fe5f9717b05465e9982f72e9a0107f228f880a015259a0d37`
- 六级：`30215ac897af7041cbd103c74d57823d543484856f64f034e40c0e85457cc0ae`

可选上游集成测试读取工程旁 `../content-samples/cet4-legacy.json` 和 `cet6-legacy.json`。没有下载样本时该项明确跳过；自包含的导入、重复记录、JSONL、进度保留和备份测试仍执行。上游原文件未打包进源码或 APK。

## 安装与验证

开发版 APK 为 1.0.4，包名与此前提供的版本相同，安装签名 SHA-256 为 `77dcae457eff638df59d400554939c99423039435b7cc2b3789d6640f6d64a05`。可覆盖此前同签名安装包；保留学习记录时不要先卸载。建议先通过设置页导出备份。

本次完整测试运行 68 项，失败、错误与跳过均为 0，包含实际四、六级样本导入、内容修正、进度保留、备份恢复、练习界面及发音逻辑测试。APK 构建与签名验证通过。界面截图来自模拟测试环境，尚未在用户的一加 ACE2 Pro / ColorOS 16 真机验证此版本。

本批不包含多词书数据库迁移、完整词频数据或全词库解析；这些仍是后续工作。

## 参考资料与内容维护

上游结构：[KyleBing/english-vocabulary](https://github.com/KyleBing/english-vocabulary)、[完整格式说明](https://github.com/KyleBing/english-vocabulary/blob/master/full_line_jsonl/README.md)。

2026-09-26 对照的词源资料如下。APP 中的分块用于记忆现代拼写，不等同于直接拼接历史原语言中的词形。

| 词条 | 本批训练 | 参考 |
| --- | --- | --- |
| attract | at- + tract | [attract](https://www.etymonline.com/word/attract) |
| import | im- + port | [import](https://www.etymonline.com/word/import) |
| export | ex- + port | [export](https://www.etymonline.com/word/export) |
| reject | re- + ject | [reject](https://www.etymonline.com/word/reject) |
| project | pro- + ject | [project](https://www.etymonline.com/word/project) |
| predict | pre- + dict | [predict](https://www.etymonline.com/word/predict) |
| describe | de- + scribe | [describe](https://www.etymonline.com/word/describe) |
| commit | com- + mit | [commit](https://www.etymonline.com/word/commit) |
| submit | sub- + mit | [submit](https://www.etymonline.com/word/submit) |
| extend | ex- + tend | [extend](https://www.etymonline.com/word/extend) |
| visible | vis + -ible | [visible](https://www.etymonline.com/word/visible) |
| inspect | in- + spect | [inspect](https://www.etymonline.com/word/inspect) |
| express | ex- + press | [express](https://www.etymonline.com/word/express) |
| structure | struct + -ure | [structure](https://www.etymonline.com/word/structure) |
| sensitive | 整词拼写 | [sensitive](https://www.etymonline.com/word/sensitive) |
| definition | 整词拼写 | [definition](https://www.etymonline.com/word/definition) |
| transfer | trans- + fer | [transfer](https://www.etymonline.com/word/transfer) |
| transport | trans- + port | [transport](https://www.etymonline.com/word/transport) |

扩充条目时应提交适用义项、部件、拼写变化、解释与来源，运行结构校验并核对内容。无需云端 AI API，也不以接入模型为前提。
