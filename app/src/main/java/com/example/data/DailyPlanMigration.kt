package com.example.data

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/** Preserve and verify the complete library before each additive upgrade. */
class DailyPlanMigration(private val context: Context, private val fromVersion: Int = 4) : Migration(fromVersion, fromVersion + 1) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        fun <T> read(table: String, type: Class<T>): List<T> {
            val adapter = moshi.adapter(type)
            val booleanColumns = setOf("isAssembled", "isMastered", "correct", "scheduleChanged", "firstLearning", "dueReview")
            return db.query("SELECT * FROM $table").use { cursor -> buildList {
                while (cursor.moveToNext()) {
                    val fields = cursor.columnNames.mapIndexed { i, name -> name to when {
                        cursor.isNull(i) -> null
                        name in booleanColumns -> cursor.getInt(i) != 0
                        cursor.getType(i) == android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                        else -> cursor.getString(i)
                    } }.toMap()
                    add(requireNotNull(adapter.fromJsonValue(fields)))
                }
            } }
        }
        val books = read("wordbooks", Wordbook::class.java).sortedBy { it.id }
        val states = read("library_state", LibraryState::class.java)
        // A fresh v4 database may have been closed before startup seeded its metadata.
        val state = states.singleOrNull() ?: run {
            check(states.isEmpty() && books.isEmpty()) { "词书状态不完整，请保留原数据" }
            LibraryState()
        }
        val words = read("word_formulas", WordFormula::class.java)
        val components = read("affix_components", AffixComponent::class.java)
        fun book(id: String) = BackupBook(id,
            words.filter { it.bookId == id }.map { it.copy(bookId = "built-in") }.sortedBy { it.id },
            components.filter { it.bookId == id }.map { it.copy(bookId = "built-in") }.sortedBy { it.id })
        val active = book(state.activeBookId)
        val prefs = context.getSharedPreferences("lexiforge_prefs", 0)
        val data = BackupData(active.id, active.words, active.components,
            BackupPreferences(prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM", prefs.getBoolean("dynamic_color", true),
                prefs.getBoolean("ebbinghaus_pref_default_enabled", false), prefs.getBoolean("local_speech_only", false),
                prefs.getString("selected_tts_engine", "system") ?: "system"),
            otherBooks = books.filter { it.id != active.id }.map { book(it.id) },
            events = read("review_events", ReviewEvent::class.java).sortedBy { it.sequence },
            positions = books.mapNotNull { b -> b.lastWordId?.let { b.id to it } }.toMap(),
            historyStartedAt = state.historyStartedAt,
            dailyTargets = books.filter { it.dailyNewTarget != 20 }.associate { it.id to it.dailyNewTarget })
        LibraryMigration(context).saveSourceBackup(data, "v$fromVersion")
        when (fromVersion) {
            4 -> {
                db.execSQL("ALTER TABLE wordbooks ADD COLUMN dailyNewTarget INTEGER NOT NULL DEFAULT 20")
                db.execSQL("ALTER TABLE review_events ADD COLUMN firstLearning INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE review_events ADD COLUMN dueReview INTEGER DEFAULT NULL")
            }
            5 -> db.execSQL("CREATE TABLE IF NOT EXISTS content_corrections (bookId TEXT NOT NULL, wordId TEXT NOT NULL, meaning TEXT NOT NULL, sentenceEn TEXT NOT NULL, sentenceZh TEXT NOT NULL, note TEXT NOT NULL, hideAnalysis INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(bookId, wordId))")
            else -> error("Unsupported source schema")
        }
    }
}
