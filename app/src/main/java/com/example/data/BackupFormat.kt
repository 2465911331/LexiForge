package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupPreferences(
    val theme: String, val dynamicColor: Boolean, val dueOnly: Boolean,
    val localSpeechOnly: Boolean, val ttsEngine: String
)

data class BackupBook(val id: String, val words: List<WordFormula>, val components: List<AffixComponent>)

data class BackupData(
    val bookId: String, val words: List<WordFormula>, val components: List<AffixComponent>,
    val preferences: BackupPreferences,
    val otherBooks: List<BackupBook> = emptyList(),
    val events: List<ReviewEvent> = emptyList(),
    val positions: Map<String, String> = emptyMap(),
    val historyStartedAt: Long = 0,
    val dailyTargets: Map<String, Int> = emptyMap(),
    val corrections: List<ContentCorrection> = emptyList()
)

data class BackupManifest(
    val formatVersion: Int, val minReaderVersion: Int, val sourceSchema: Int,
    val backupId: String, val createdAt: Long, val appVersion: String,
    val wordCount: Int, val componentCount: Int, val sha256: String
)

data class VerifiedBackup(val manifest: BackupManifest, val data: BackupData)

object BackupFormat {
    const val MAX_FILE_BYTES = 32 * 1024 * 1024
    const val MAX_DATA_BYTES = 64 * 1024 * 1024
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val dataAdapter = moshi.adapter(BackupData::class.java).failOnUnknown()
    private val manifestAdapter = moshi.adapter(BackupManifest::class.java).failOnUnknown()

    fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    fun fingerprint(data: BackupData): String = digest(dataAdapter.toJson(data).toByteArray(Charsets.UTF_8))

    fun write(data: BackupData, output: OutputStream, now: Long = System.currentTimeMillis()) {
        validate(data)
        val bytes = dataAdapter.toJson(data).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_DATA_BYTES) { "数据超过当前版本的备份容量，请保留原数据并联系维护者" }
        val manifest = BackupManifest(5, 5, 6, UUID.randomUUID().toString(), now,
            com.example.BuildConfig.VERSION_NAME, data.words.size + data.otherBooks.sumOf { it.words.size },
            data.components.size + data.otherBooks.sumOf { it.components.size }, digest(bytes))
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifestAdapter.toJson(manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(bytes)
            zip.closeEntry()
        }
    }

    fun read(input: InputStream): VerifiedBackup {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(LimitedInput(input, MAX_FILE_BYTES.toLong())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name in setOf("manifest.json", "data.json") && entry.name !in entries) {
                    "备份包含未知或重复文件"
                }
                entries[entry.name] = readLimited(zip, if (entry.name == "manifest.json") 16 * 1024 else MAX_DATA_BYTES)
                zip.closeEntry()
            }
        }
        require(entries.size == 2) { "备份不完整" }
        val manifest = requireNotNull(manifestAdapter.fromJson(entries.getValue("manifest.json").toString(Charsets.UTF_8)))
        val legacy = manifest.formatVersion == 1 && manifest.minReaderVersion == 1 && manifest.sourceSchema == 3
        val libraryVersion = manifest.formatVersion == 2 && manifest.minReaderVersion == 2 && manifest.sourceSchema == 4
        val planVersion = manifest.formatVersion == 3 && manifest.minReaderVersion == 3 && manifest.sourceSchema == 5
        val toolsVersion = manifest.formatVersion == 4 && manifest.minReaderVersion == 4 && manifest.sourceSchema == 6
        require(legacy || libraryVersion || planVersion || toolsVersion || (manifest.formatVersion == 5 && manifest.minReaderVersion == 5 && manifest.sourceSchema == 6)) {
            "此备份需要其他版本的 LexiForge，请升级应用后重试"
        }
        require(manifest.createdAt > 0 && manifest.appVersion.length in 1..80) { "备份信息无效" }
        UUID.fromString(manifest.backupId)
        val bytes = entries.getValue("data.json")
        require(digest(bytes) == manifest.sha256) { "备份校验失败，文件可能已损坏" }
        val data = requireNotNull(dataAdapter.fromJson(bytes.toString(Charsets.UTF_8)))
        validate(data)
        require(manifest.formatVersion >= 5 || data.events.none { it.mode in PracticePlanner.newModes || "CONTEXT" in it.hints.split(',') }) { "旧格式含有不支持的专项练习数据" }
        require(!(legacy || libraryVersion || planVersion) || (data.corrections.isEmpty() && data.events.none { it.mode in RecallTools.modes })) { "旧格式含有不支持的检验或纠错数据" }
        require(!(legacy || libraryVersion) || (data.dailyTargets.isEmpty() && data.events.all { it.firstLearning == null && it.dueReview == null })) { "旧格式含有不支持的计划数据" }
        require(!legacy || (data.otherBooks.isEmpty() && data.events.isEmpty() && data.positions.isEmpty() && data.historyStartedAt == 0L)) { "旧格式含有不支持的数据" }
        require(data.words.size + data.otherBooks.sumOf { it.words.size } == manifest.wordCount &&
            data.components.size + data.otherBooks.sumOf { it.components.size } == manifest.componentCount) { "备份条数不一致" }
        return VerifiedBackup(manifest, data)
    }

    fun validate(data: BackupData) {
        fun text(s: String) { require(s.toByteArray(Charsets.UTF_8).size <= 65536) { "备份中有过长文本" } }
        fun identity(s: String) { require(s.isNotBlank() && s.length <= 512) { "备份中的标识无效" } }
        fun progress(stage: Int, last: Long, next: Long) {
            require(stage in 0..8 && last in 0..253402300799999L && next in 0..253402300799999L) { "复习阶段或时间无效" }
        }
        identity(data.bookId)
        val books = listOf(BackupBook(data.bookId, data.words, data.components)) + data.otherBooks
        require(books.size <= 20 && books.map { it.id }.distinct().size == books.size) { "词书过多或有重复词书" }
        require(books.sumOf { it.words.size.toLong() } <= 100000 && books.sumOf { it.components.size.toLong() } <= 100000) { "词库超过备份容量" }
        data.otherBooks.forEach { validate(BackupData(it.id, it.words, it.components, data.preferences)) }
        val bookIndex = books.associateBy { it.id }
        require(data.dailyTargets.all { (id, target) -> id in bookIndex && target in 0..200 }) { "每日计划目标或词书无效" }
        require(data.historyStartedAt in 0..253402300799999L) { "记录起始时间无效" }
        data.positions.forEach { (book, word) ->
            require(bookIndex[book]?.words?.any { it.id == word } == true) { "学习位置引用了缺失单词" }
        }
        require(data.events.size <= 100000 && data.events.map { it.id }.distinct().size == data.events.size) { "作答记录过多或重复" }
        require(data.events.map { it.sequence }.distinct().size == data.events.size && data.events.all { it.sequence > 0 && it.sequence < Long.MAX_VALUE }) { "作答记录顺序无效" }
        val eventsById = data.events.associateBy { it.id }
        val wordIds = books.associate { it.id to it.words.map { w -> w.id }.toSet() }
        require(data.corrections.size <= 20000 && data.corrections.map { it.bookId to it.wordId }.distinct().size == data.corrections.size) { "纠错记录过多或重复" }
        data.corrections.forEach { c ->
            require(c.wordId in wordIds[c.bookId].orEmpty()) { "纠错引用了缺失词条" }
            ContentTools.validate(c)
        }
        val componentIds = books.associate { it.id to it.components.map { c -> c.id }.toSet() }
        data.events.forEach { e ->
            require((e.firstLearning == null && e.dueReview == null) || e.targetKind == "WORD") { "计划标记只适用于单词作答" }
            require(e.firstLearning != true || e.dueReview != true) { "首次学习不能同时是旧词到期复习" }
            identity(e.id); identity(e.sessionId); identity(e.targetId)
            require(e.bookId in bookIndex && e.targetKind in setOf("WORD", "COMPONENT") &&
                e.mode in (setOf("SPELLING", "BLOCKS", "CONSTRUCTION", "COMPONENT_CARD", "RECALL", "ERROR_RECALL") + PracticePlanner.newModes)) { "作答记录类型无效" }
            require(!e.mode.startsWith("EXAM_") || PracticePlanner.examBook(e.bookId)) { "考试专项词书无效" }
            require(e.mode != "EXAM_CLOZE" || (e.hints == "CONTEXT" && e.attempt == 1)) { "语境练习记录无效" }
            require(e.mode !in RecallTools.modes || (e.hints.isEmpty() && e.attempt == 1)) { "独立检验不能包含辅助提示或重复尝试" }
            require(if (e.targetKind == "WORD") e.targetId in wordIds.getValue(e.bookId) else e.targetId in componentIds.getValue(e.bookId)) { "作答记录引用了缺失条目" }
            require((e.targetKind == "COMPONENT") == (e.mode == "COMPONENT_CARD")) { "题型与条目不符" }
            listOf(e.targetLabel, e.prompt, e.answer, e.hints).forEach(::text)
            require(e.hints.isEmpty() || e.hints.split(',').all { it in setOf("ANALYSIS", "AUDIO", "CHOICES", "ANCHOR", "CONTEXT") }) { "未知提示类型" }
            require(e.attempt in 1..100000 && e.answeredAt in 1..253402300799999L && e.elapsedMs in 0..86400000L &&
                e.stageBefore in 0..8 && e.stageAfter in 0..8 && e.schedulerVersion == "fixed-v1") { "作答时间或调度信息无效" }
            require(e.scheduleChanged || e.stageBefore == e.stageAfter) { "非调度作答不能改写阶段" }
            e.retryOf?.let { previousId ->
                val previous = eventsById[previousId]
                require(previous != null && previous.id != e.id && !previous.correct &&
                    previous.bookId == e.bookId && previous.targetId == e.targetId && previous.targetKind == e.targetKind &&
                    previous.answeredAt <= e.answeredAt && previous.sequence < e.sequence) { "重试关联无效" }
            }
        }
        require(data.words.size <= 20000 && data.components.size <= 20000) { "备份条目超过当前版本容量" }
        require(data.words.map { it.id }.toSet().size == data.words.size &&
            data.components.map { it.id }.toSet().size == data.components.size) { "备份有重复条目" }
        val components = data.components.associateBy { it.id }
        data.components.forEach {
            require(it.bookId == "built-in") { "备份组件必须位于对应词书容器" }
            identity(it.id)
            require(it.type in setOf("PREFIX", "ROOT", "SUFFIX") && it.reviewCount >= 0) { "词根组件信息无效" }
            listOf(it.name, it.icon, it.literalMeaning, it.anchorWord, it.anchorDesc).forEach(::text)
            progress(it.ebbinghausStage, it.lastReviewedAt, it.nextReviewAt)
        }
        data.words.forEach {
            require(it.bookId == "built-in") { "备份单词必须位于对应词书容器" }
            identity(it.id)
            require(it.spell.isNotBlank()) { "备份存在空单词" }
            listOf(it.spell, it.chineseMeaning, it.literalFormula, it.sentenceEn, it.sentenceZh,
                it.frequencyGroup, it.prefixPart.orEmpty(), it.rootPart.orEmpty(), it.suffixPart.orEmpty()).forEach(::text)
            listOf(it.prefixId to "PREFIX", it.rootId to "ROOT", it.suffixId to "SUFFIX").forEach { (id, type) ->
                require(id == null || components[id]?.type == type) { "单词引用了缺失或类型不符的词根组件" }
            }
            progress(it.ebbinghausStage, it.lastReviewedAt, it.nextReviewAt)
        }
        require(data.preferences.theme in setOf("SYSTEM", "LIGHT", "DARK")) { "主题设置无效" }
        identity(data.preferences.ttsEngine)
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(out.size().toLong() + count <= limit) { "备份解压后过大" }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }

    private class LimitedInput(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0) checkSize(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = `in`.read(b, off, len).also { if (it > 0) checkSize(it) }
        private fun checkSize(n: Int) { count += n; require(count <= limit) { "备份文件过大" } }
    }
}
