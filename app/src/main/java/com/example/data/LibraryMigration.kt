package com.example.data

import android.content.Context
import android.database.Cursor
import android.util.AtomicFile
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.util.UUID

/** The v3 source is read and durably backed up before any table is replaced. */
class LibraryMigration(private val context: Context) : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val prefs = context.getSharedPreferences("lexiforge_prefs", 0)
        val oldId = prefs.getString("current_wordbook_id", "built-in").orEmpty()
            .takeIf { it.isNotBlank() && it.length <= 512 } ?: "legacy-upgrade"
        val words = mutableListOf<WordFormula>()
        db.query("SELECT * FROM word_formulas").use { c ->
            while (c.moveToNext()) words += WordFormula(
                c.s("id")!!, c.s("spell")!!, c.s("prefixId"), c.s("rootId"), c.s("suffixId"),
                c.s("prefixPart"), c.s("rootPart"), c.s("suffixPart"), c.s("literalFormula")!!,
                c.s("chineseMeaning")!!, c.s("sentenceEn")!!, c.s("sentenceZh")!!,
                c.n("isAssembled") != 0L, c.n("lastReviewedAt"), c.n("ebbinghausStage").toInt(),
                c.n("nextReviewAt"), c.s("frequencyGroup")!!)
        }
        val components = mutableListOf<AffixComponent>()
        db.query("SELECT * FROM affix_components").use { c ->
            while (c.moveToNext()) components += AffixComponent(c.s("id")!!, c.s("name")!!, c.s("type")!!,
                c.s("icon")!!, c.s("literalMeaning")!!, c.s("anchorWord")!!, c.s("anchorDesc")!!,
                c.n("isMastered") != 0L, c.n("reviewCount").toInt(), c.n("lastReviewedAt"),
                c.n("ebbinghausStage").toInt(), c.n("nextReviewAt"))
        }
        val data = BackupData(oldId, words.sortedBy { it.id }, components.sortedBy { it.id },
            BackupPreferences(prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM",
                prefs.getBoolean("dynamic_color", true), prefs.getBoolean("ebbinghaus_pref_default_enabled", false),
                prefs.getBoolean("local_speech_only", false), prefs.getString("selected_tts_engine", "system") ?: "system"))
        saveSourceBackup(data)
        copyScopedTable(db, "word_formulas", WORD_COLUMNS, oldId)
        copyScopedTable(db, "affix_components", COMPONENT_COLUMNS, oldId)
        db.execSQL("CREATE TABLE IF NOT EXISTS wordbooks (id TEXT NOT NULL, lastWordId TEXT, PRIMARY KEY(id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS library_state (id INTEGER NOT NULL, activeBookId TEXT NOT NULL, historyStartedAt INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS review_events ($EVENT_COLUMNS, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_review_events_bookId_sequence ON review_events (bookId, sequence)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_review_events_sequence ON review_events (sequence)")
        db.execSQL("INSERT INTO wordbooks (id, lastWordId) VALUES (?, NULL)", arrayOf(oldId))
        db.execSQL("INSERT INTO library_state (id, activeBookId, historyStartedAt) VALUES (0, ?, ?)", arrayOf<Any>(oldId, System.currentTimeMillis()))
        db.query("SELECT COUNT(*) FROM word_formulas").use { check(it.moveToFirst() && it.getInt(0) == words.size) }
        db.query("SELECT COUNT(*) FROM affix_components").use { check(it.moveToFirst() && it.getInt(0) == components.size) }
    }

    internal fun saveSourceBackup(data: BackupData, source: String = "v3") {
        val directory = File(context.noBackupFilesDir, "recovery")
        check(directory.exists() || directory.mkdirs()) { "无法保存升级前恢复点" }
        val file = AtomicFile(File(directory, "point-${System.currentTimeMillis()}-upgrade-$source-${UUID.randomUUID()}.lexiforge-backup"))
        val stream = file.startWrite()
        try {
            BackupFormat.write(data, object : java.io.FilterOutputStream(stream) {
                override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
                override fun close() { flush() }
            })
            file.finishWrite(stream)
            check(file.openRead().use(BackupFormat::read).data == data) { "升级恢复点校验失败" }
        } catch (e: Exception) { file.failWrite(stream); throw e }
    }

    private fun copyScopedTable(db: SupportSQLiteDatabase, table: String, columns: String, bookId: String) {
        val names = columns.split(", ").map { it.substringBefore(' ') }.joinToString(", ")
        db.execSQL("CREATE TABLE ${table}_v4 ($columns, bookId TEXT NOT NULL, PRIMARY KEY(bookId, id))")
        db.execSQL("INSERT INTO ${table}_v4 ($names, bookId) SELECT $names, ? FROM $table", arrayOf(bookId))
        db.execSQL("DROP TABLE $table")
        db.execSQL("ALTER TABLE ${table}_v4 RENAME TO $table")
    }

    private fun Cursor.s(name: String): String? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getString(it) }
    private fun Cursor.n(name: String): Long = getLong(getColumnIndexOrThrow(name))

    companion object {
        const val WORD_COLUMNS = "id TEXT NOT NULL, spell TEXT NOT NULL, prefixId TEXT, rootId TEXT, suffixId TEXT, prefixPart TEXT, rootPart TEXT, suffixPart TEXT, literalFormula TEXT NOT NULL, chineseMeaning TEXT NOT NULL, sentenceEn TEXT NOT NULL, sentenceZh TEXT NOT NULL, isAssembled INTEGER NOT NULL, lastReviewedAt INTEGER NOT NULL, ebbinghausStage INTEGER NOT NULL, nextReviewAt INTEGER NOT NULL, frequencyGroup TEXT NOT NULL"
        const val COMPONENT_COLUMNS = "id TEXT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, icon TEXT NOT NULL, literalMeaning TEXT NOT NULL, anchorWord TEXT NOT NULL, anchorDesc TEXT NOT NULL, isMastered INTEGER NOT NULL, reviewCount INTEGER NOT NULL, lastReviewedAt INTEGER NOT NULL, ebbinghausStage INTEGER NOT NULL, nextReviewAt INTEGER NOT NULL"
        const val EVENT_COLUMNS = "id TEXT NOT NULL, sessionId TEXT NOT NULL, bookId TEXT NOT NULL, targetId TEXT NOT NULL, targetLabel TEXT NOT NULL, targetKind TEXT NOT NULL, mode TEXT NOT NULL, prompt TEXT NOT NULL, answer TEXT NOT NULL, correct INTEGER NOT NULL, hints TEXT NOT NULL, attempt INTEGER NOT NULL, answeredAt INTEGER NOT NULL, elapsedMs INTEGER NOT NULL, stageBefore INTEGER NOT NULL, stageAfter INTEGER NOT NULL, scheduleChanged INTEGER NOT NULL, retryOf TEXT, schedulerVersion TEXT NOT NULL, sequence INTEGER NOT NULL"
    }
}
