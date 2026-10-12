package com.example.data

import android.content.Context
import android.net.Uri
import com.squareup.moshi.Moshi

/** A local contribution draft, never an automatic network submission. */
object CorrectionExport {
    fun report(data: BackupData): String {
        val books = listOf(BackupBook(data.bookId, data.words, data.components)) + data.otherBooks
        val words = books.flatMap { b -> b.words.map { (b.id to it.id) to it } }.toMap()
        val entries = data.corrections.map { c ->
            val word = words.getValue(c.bookId to c.wordId)
            mapOf("bookId" to c.bookId, "wordId" to c.wordId, "spelling" to word.spell,
                "originalMeaning" to word.chineseMeaning, "proposedMeaning" to c.meaning,
                "originalExample" to word.sentenceEn, "proposedExample" to c.sentenceEn,
                "originalTranslation" to word.sentenceZh, "proposedTranslation" to c.sentenceZh,
                "originalAnalysis" to word.literalFormula, "disableAnalysis" to c.hideAnalysis,
                "note" to c.note, "updatedAt" to c.updatedAt)
        }
        return Moshi.Builder().build().adapter(Map::class.java).indent("  ")
            .toJson(mapOf("formatVersion" to 1, "appVersion" to com.example.BuildConfig.VERSION_NAME,
                "status" to "personal-unreviewed", "entries" to entries))
    }
    suspend fun export(context: Context, uri: Uri) {
        val text = DataSafety.write { report(BackupRepository(context, LexiRepository.getDatabase(context).lexiDao()).snapshot()) }
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) } ?: error("无法写入文件")
        val saved = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        check(saved == text) { "导出文件校验失败，请更换位置" }
    }
}
