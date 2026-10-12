# 1.0.7：共享离线构词与拼写资料

当前版本解决“当前词书没有词基，就只能整词拼写”的限制。全部分析、分块训练与许可阅读均在本地完成，不使用 AI API、模型或在线解析服务。外部词书下载、来源链接、在线发音仍沿用原有网络行为。

## 内容层级

1. 保留 18 个示范词的人工整理结果（含 2 个整词示范），并优先使用原有 51 条受约束规则。
2. 规则无法匹配时，查询独立 MorphyNet 构词资料。目标词性必须匹配；词基需在共享读音词典中有记录，但不要求出现在当前词书。生成阶段限定可用词缀、排除已知不适合教学的关系，最多展开三层词族路径；字母块和最内层词基均无歧义才接受。保留拼写变化，最终字母块必须原样拼回目标词。
3. 60 个明确列出的现代复合词使用项目维护的组成词表，不做任意子串试拆。
4. 无构词资料时，使用 Moby 排版断词位置提供**拼写分块**，同时附上 CMUdict 首个美式读音的音节数和主重音位置。两份数据独立，**字母块不是词根，也不是音节边界；块数不保证等于音节数**。TTS 仍使用用户选择的语音引擎，不冒充 CMUdict 音频。
5. 短词、未收录词、不合适的资料继续整词拼写。未支持的词书来源、大写专名和未知示范词义项不自动套用构词解释。

公开资料存在错误与义项差异。例如剔除了 `remember = re + member`；不能因覆盖率提高就声称逐词审核完毕。规则/资料结果明确标为推导，释义仍来自用户的原始词书。词频仍未分级。

## 数据、来源与许可

| 资源 | 固定版本 / 来源 | 使用方式 | 许可 |
|---|---|---|---|
| MorphyNet 英语派生表 | [项目](https://github.com/kbatsuren/MorphyNet)，commit `378144f64df58c78db5245af19d16a511ccecf3a` | 过滤、有限拼写变换、最多三层展开、去除有歧义分块，输出 80,464 条词形/词性记录 | CC BY-SA 3.0；派生文件 `morphynet.tsv.gz` 与其来源派生的词族路径继续按该许可提供 |
| CMUdict | [项目](https://github.com/cmusphinx/cmudict)，commit `74790861f652b15e4ac49015a90074ad62a27690` | 首个美式读音的音节数和主重音位置 | 保留原版权、再分发条件与免责声明 |
| Moby Hyphenator II | [Grady Ward / Project Gutenberg #3204](https://www.gutenberg.org/ebooks/3204)，[断词文件](https://www.gutenberg.org/files/3204/files/mhyph.txt) | 过滤纯小写词、唯一断词位置，与 CMUdict 至少两音节词交集，共 28,300 条拼写辅助记录 | 作者 2001 年 1 月公共领域授权；保留完整原始说明及 Project Gutenberg 条款 |
| 复合词表 | `SharedLearningPack.compounds` | 明示组成词，不宣称历史词源 | 项目整理；遵循项目代码许可情况 |

MorphyNet 作者：Khuyagbaatar Batsuren、Gábor Bella、Fausto Giunchiglia；上游使用 Wiktionary 资料。论文：[MorphyNet: a Large Multilingual Database of Derivational and Inflectional Morphology](https://aclanthology.org/2021.sigmorphon-1.5/)。本项目修改包括词性/大小写/例外/字形筛选、三层路径展开、中文提示及压缩格式转换。**应用代码与各份数据的许可分别管理，不将数据许可冒充整个项目已完成开源许可审查。**

完整许可随 APK 和源码存放于 `app/src/main/resources/learning/licenses/`，应用“设置 → 离线分块学习 → 资料来源与开源许可”可离线阅读。生成输入/输出 SHA-256、记录数及上游版本位于 `content/learning-pack-manifest.json`；APK 内有同一份清单。

## 重建

常规 Android 构建直接使用已提交的压缩资料，不需要下载数据。重新生成时使用 Python 3 标准库：

```text
python tools/build_learning_pack.py --sources ../content-sources
```

输入目录需包含 `morphynet-eng.tsv`（上述固定版本的 `eng/eng.derivational.v1.tsv`）、`cmudict.dict`、`moby-hyphenation.txt`、`morphynet-commit.txt`、`cmudict-commit.txt`、`cmudict-LICENSE.txt`、`moby-NOTICE.txt`、`CC-BY-SA-3.0.txt`。原文许可来源分别为 CMUdict 仓库 `LICENSE`、Gutenberg 完整 [eBook 原文](https://www.gutenberg.org/cache/epub/3204/pg3204.txt)、[CC BY-SA 3.0 法律文本](https://creativecommons.org/licenses/by-sa/3.0/legalcode.txt)。commit 文件仅存对应版本号。

已有清单时，输入数据校验值不一致会拒绝生成，防止上游悄悄更新。压缩时间戳固定为零；相同数据和代码生成相同文件。可维护的资料例外在 `content/morphology-exclusions.txt`，原有规则例外也会共同参与过滤；词缀白名单在生成脚本内。修改例外/过滤逻辑时应重新生成、审查差异、更新数据版本并重新运行覆盖审计。

## 训练与升级

新资料采用 2–6 个字母块的排序练习，支持撤回、清空、错误重试，重复字母块使用独立索引。构词资料和普通拼写块分开显示、分开计数，不占用旧的“词根”组件槽，也不会给不存在的词根增加掌握度。

版本为 1.0.7 / versionCode 8。数据库结构和备份格式保持兼容。首次启动通过现有恢复点/恢复日志流程重新匹配当前词书；保存恢复点失败时不会继续替换内容。保留单词 ID、释义、例句、学习状态、复习阶段和时间，旧组件进度保留存档；重新启动不会重复更改内容。恢复旧备份后也按当前离线资料重新匹配。无需重新下载词书。

应覆盖安装同签名版本，不要卸载。此版本仍是单词书应用；多词书并存不在本次修改内。

## 审计与验证

`SharedLearningPackTest` 检查全部压缩行可原样拼回单词、无词基时的多层拆分、错误关系拦截、来源类别、许可资源、升级/备份进度。现有规则测试独立于新资料回退，继续检查规则本身的例外、词性和歧义限制。界面测试检查分块提交、重排、重复块和复习记录。

真实四/六级词书审计使用 `../content-samples/cet4-legacy.json` 与 `cet6-legacy.json`（KyleBing/english-vocabulary）。样本不随源码打包；缺少时该项测试会明确跳过。审计生成 `app/build/ui-preview/learning-pack-coverage.tsv` 和逐词分类报告；类别 `spelling` 不计入构词覆盖。实际结果见发布说明和随包审计文件。
