package com.example

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.*
import java.util.UUID
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LibraryTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: LexiRepository
    private val a = "3-CET4-顺序"
    private val b = "4-CET6-顺序"
    private val now = 1_800_000_000_000L
    private val settings = BackupPreferences("SYSTEM", true, false, false, "system")
    private fun word(spell: String = "plain", meaning: String = "adj. 明白的") =
        WordFormula(spell, spell, null, null, null, null, null, null, OfflineMorphology.WHOLE_WORD_NOTE, meaning, "", "")

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        DataSafety.recoveryRequired = false
        context.getSharedPreferences("lexiforge_prefs", 0).edit().clear().commit()
        File(context.noBackupFilesDir, "recovery").deleteRecursively()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = LexiRepository(db.lexiDao(), context)
    }
    @After fun close() { db.close(); DataSafety.recoveryRequired = false }

    private suspend fun seed() {
        db.lexiDao().ensureLibrary(a, now)
        db.lexiDao().installBook(a, emptyList(), listOf(word()))
    }
    private fun answer(correct: Boolean, at: Long = now, attempt: Int = 1, hints: String = "", session: String = "session") =
        AnswerRequest(UUID.randomUUID().toString(), session, a, "plain", "WORD", "SPELLING",
            if (correct) "plain" else "plian", correct, hints, attempt, at, 1200)

    @Test fun `books with identical word ids retain separate meanings progress and positions`() = runBlocking {
        seed()
        assertTrue(repo.recordAnswer(answer(true), DataSafety.generation))
        repo.rememberWord(a, "plain")
        val first = db.lexiDao().wordSnapshot().single()
        repo.replaceWordbook(emptyList(), listOf(word(meaning = "n. 平原")), b)
        assertEquals(2, db.lexiDao().bookSnapshot().size)
        assertEquals("n. 平原", db.lexiDao().wordSnapshot().single().chineseMeaning)
        assertEquals(0L, db.lexiDao().wordSnapshot().single().lastReviewedAt)
        repo.selectBook(a)
        assertEquals(first, db.lexiDao().wordSnapshot().single())
        assertEquals("plain", db.lexiDao().bookSnapshot().first { it.id == a }.lastWordId)
        // Reimport is not a destructive update, even if the supplied contents changed.
        repo.replaceWordbook(emptyList(), listOf(word("replacement")), a)
        assertEquals(first, db.lexiDao().wordSnapshot().single())
        assertEquals(2, db.lexiDao().bookSnapshot().size)
    }

    @Test fun `all submitted answers including correction and early practice are recorded atomically`() = runBlocking {
        seed()
        val wrong = answer(false)
        assertTrue(repo.recordAnswer(wrong, DataSafety.generation))
        assertFalse(repo.recordAnswer(wrong, DataSafety.generation))
        assertTrue(repo.recordAnswer(answer(true, now + 1000, 2, "ANALYSIS"), DataSafety.generation))
        val events = db.lexiDao().eventSnapshot()
        assertEquals(2, events.size)
        assertEquals(wrong.id, events.last().retryOf)
        assertEquals("ANALYSIS", events.last().hints)
        assertFalse(events.last().scheduleChanged)
        assertEquals(0, db.lexiDao().wordSnapshot().single().ebbinghausStage)
        assertTrue(repo.recordAnswer(answer(true, now + 300000, session = "next"), DataSafety.generation))
        assertEquals(1, db.lexiDao().wordSnapshot().single().ebbinghausStage)
        assertTrue(repo.recordAnswer(answer(true, now + 300001, session = "early"), DataSafety.generation))
        assertEquals(4, db.lexiDao().eventSnapshot().size)
        assertFalse(db.lexiDao().eventSnapshot().last().scheduleChanged)
    }

    @Test fun `same millisecond events keep commit order across backup restore`() = runBlocking {
        seed()
        val wrong = answer(false)
        repo.recordAnswer(wrong, DataSafety.generation)
        repo.recordAnswer(answer(true, attempt = 2), DataSafety.generation)
        val before = BackupRepository(context, db.lexiDao()).snapshot()
        assertEquals(listOf(1L, 2L), before.events.map { it.sequence })
        val out = ByteArrayOutputStream()
        BackupFormat.write(before, out)
        db.lexiDao().restoreLibrary(BackupFormat.read(out.toByteArray().inputStream()).data)
        assertTrue(db.lexiDao().lastEvent(a, "plain", "WORD")!!.correct)
    }

    @Test fun `an event insert failure or progress failure cannot leave half a saved answer`() = runBlocking {
        seed()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_progress BEFORE UPDATE ON word_formulas BEGIN SELECT RAISE(ABORT, 'stop'); END")
        assertThrows(Exception::class.java) { runBlocking { repo.recordAnswer(answer(true), DataSafety.generation) } }
        assertTrue(db.lexiDao().eventSnapshot().isEmpty())
        assertEquals(0L, db.lexiDao().wordSnapshot().single().lastReviewedAt)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_progress")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_event BEFORE INSERT ON review_events BEGIN SELECT RAISE(ABORT, 'stop'); END")
        assertThrows(Exception::class.java) { runBlocking { repo.recordAnswer(answer(true), DataSafety.generation) } }
        assertEquals(0L, db.lexiDao().wordSnapshot().single().lastReviewedAt)
    }

    @Test fun `switching and restoring reject queued answers from the old library`() = runBlocking {
        seed()
        val generation = DataSafety.generation
        repo.replaceWordbook(emptyList(), listOf(word()), b)
        assertFalse(repo.recordAnswer(answer(true), generation))
        assertFalse(repo.recordAnswer(answer(true), DataSafety.generation))
        assertTrue(db.lexiDao().eventSnapshot().isEmpty())
        assertEquals(0, db.lexiDao().wordSnapshot().single().ebbinghausStage)
    }

    @Test fun `reset affects current progress only and retains all historical events`() = runBlocking {
        seed()
        repo.recordAnswer(answer(true), DataSafety.generation)
        val learned = db.lexiDao().wordSnapshot().single()
        repo.replaceWordbook(emptyList(), listOf(word().copy(ebbinghausStage = 4, lastReviewedAt = now, nextReviewAt = now + 100000)), b)
        repo.resetAllLearningData()
        assertEquals(0, db.lexiDao().wordSnapshot().single().ebbinghausStage)
        repo.selectBook(a)
        assertEquals(learned, db.lexiDao().wordSnapshot().single())
        assertEquals(1, db.lexiDao().eventSnapshot().size)
        repo.resetAllLearningData()
        assertEquals(1, db.lexiDao().eventSnapshot().size)
    }

    @Test fun `multibook backup resumes after interruption and retains all records and position`() = runBlocking {
        seed()
        repo.recordAnswer(answer(true, hints = "AUDIO"), DataSafety.generation)
        repo.rememberWord(a, "plain")
        repo.replaceWordbook(emptyList(), listOf(word("book")), b)
        val service = BackupRepository(context, db.lexiDao())
        val before = service.snapshot()
        val out = ByteArrayOutputStream()
        BackupFormat.write(before, out)
        val decoded = BackupFormat.read(out.toByteArray().inputStream())
        assertEquals(5, decoded.manifest.minReaderVersion)
        assertEquals(before, decoded.data)
        val broken = BackupRepository(context, db.lexiDao()) { throw IOException("interrupted") }
        assertThrows(IOException::class.java) { runBlocking { broken.restore(broken.preview(decoded)) } }
        assertTrue(DataSafety.recoveryRequired)
        assertTrue(service.resumePending())
        assertEquals(before, service.snapshot())
        assertFalse(service.resumePending())
    }

    @Test fun `invalid cross book event duplicate book and mismatched position reject import before writes`() = runBlocking {
        seed()
        repo.recordAnswer(answer(true), DataSafety.generation)
        val original = BackupRepository(context, db.lexiDao()).snapshot()
        listOf(original.copy(otherBooks = listOf(BackupBook(a, original.words, original.components))),
            original.copy(events = original.events.map { it.copy(bookId = "missing") }),
            original.copy(positions = mapOf(a to "missing")),
            original.copy(events = original.events + original.events.single())).forEach {
            assertThrows(IllegalArgumentException::class.java) { BackupFormat.validate(it) }
        }
        assertEquals(original, BackupRepository(context, db.lexiDao()).snapshot())
    }

    @Test fun `v1 backup remains readable and contains no invented review history`() {
        val data = BackupData(a, listOf(word()), emptyList(), settings)
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val json = JSONObject(moshi.adapter(BackupData::class.java).toJson(data))
        listOf("otherBooks", "events", "positions", "historyStartedAt").forEach(json::remove)
        json.getJSONArray("words").getJSONObject(0).remove("bookId")
        val bytes = json.toString().toByteArray()
        val manifest = BackupManifest(1, 1, 3, UUID.randomUUID().toString(), now, "1.0.7", 1, 0, BackupFormat.digest(bytes))
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { z ->
            z.putNextEntry(ZipEntry("manifest.json")); z.write(moshi.adapter(BackupManifest::class.java).toJson(manifest).toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("data.json")); z.write(bytes); z.closeEntry()
        }
        val decoded = BackupFormat.read(output.toByteArray().inputStream())
        assertEquals(data, decoded.data)
        assertTrue(decoded.data.events.isEmpty())
    }

    @Test fun `v3 backup retains daily targets and review markers`() = runBlocking {
        seed()
        repo.setDailyTarget(a, 35)
        repo.recordAnswer(answer(true), DataSafety.generation)
        val data = BackupRepository(context, db.lexiDao()).snapshot()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val json = JSONObject(moshi.adapter(BackupData::class.java).toJson(data))
        json.remove("corrections")
        val bytes = json.toString().toByteArray()
        val manifest = BackupManifest(3, 3, 5, UUID.randomUUID().toString(), now, "1.0.9",
            data.words.size, data.components.size, BackupFormat.digest(bytes))
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { z ->
            z.putNextEntry(ZipEntry("manifest.json")); z.write(moshi.adapter(BackupManifest::class.java).toJson(manifest).toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("data.json")); z.write(bytes); z.closeEntry()
        }
        val decoded = BackupFormat.read(output.toByteArray().inputStream())
        assertEquals(data, decoded.data)
        assertEquals(35, decoded.data.dailyTargets[a])
        assertEquals(true, decoded.data.events.single().firstLearning)
        assertEquals(false, decoded.data.events.single().dueReview)
        assertTrue(decoded.data.corrections.isEmpty())
    }

    private fun createV3(name: String, data: BackupData) {
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        fun values(json: String): ContentValues {
            val result = ContentValues()
            val obj = JSONObject(json)
            obj.remove("bookId")
            obj.keys().forEach { key -> when (val v = obj.get(key)) {
                JSONObject.NULL -> result.putNull(key)
                is Boolean -> result.put(key, if (v) 1 else 0)
                is Number -> result.put(key, v.toLong())
                else -> result.put(key, v.toString())
            } }
            return result
        }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            // SQL from the actual checked-in v3 schema, not the new migration implementation.
            val schema = JSONObject(File("schemas/com.example.data.AppDatabase/3.json").readText()).getJSONObject("database")
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("$" + "{TABLE_NAME}", entity.getString("tableName")))
            }
            data.words.forEach { old.insertOrThrow("word_formulas", null, values(moshi.adapter(WordFormula::class.java).toJson(it))) }
            data.components.forEach { old.insertOrThrow("affix_components", null, values(moshi.adapter(AffixComponent::class.java).toJson(it))) }
            old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            old.execSQL("INSERT INTO room_master_table VALUES(42, ?)", arrayOf(schema.getString("identityHash")))
            old.version = 3
        }
    }

    @Test fun `v3 migration preserves all data`() = runBlocking {
        val name = "v3.db"
        val old = BackupData(a, BuiltInWordbook.words.map { it.copy(ebbinghausStage = 4, lastReviewedAt = now, nextReviewAt = now + 9999, isAssembled = true) },
            BuiltInWordbook.components.map { it.copy(reviewCount = 7, lastReviewedAt = now, isMastered = true) }, settings)
        createV3(name, old)
        context.getSharedPreferences("lexiforge_prefs", 0).edit().putString("current_wordbook_id", a).commit()
        var migrated = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(LibraryMigration(context), DailyPlanMigration(context), DailyPlanMigration(context, 5)).allowMainThreadQueries().build()
        try {
            migrated.openHelper.writableDatabase
            val dao = migrated.lexiDao()
            assertEquals(old.words.sortedBy { it.id }, dao.wordSnapshot().map { it.copy(bookId = "built-in") }.sortedBy { it.id })
            assertEquals(old.components.sortedBy { it.id }, dao.componentSnapshot().map { it.copy(bookId = "built-in") }.sortedBy { it.id })
            assertEquals(a, dao.libraryState()!!.activeBookId)
            assertTrue(dao.libraryState()!!.historyStartedAt > 0)
            assertTrue(dao.eventSnapshot().isEmpty())
            val service = BackupRepository(context, dao)
            assertEquals(old.words, service.points().first().inputStream().use(BackupFormat::read).data.words.sortedBy { it.id }.let { sorted -> old.words.map { w -> sorted.single { it.id == w.id } } })
            val before = service.snapshot()
            migrated.close()
            migrated = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(LibraryMigration(context), DailyPlanMigration(context), DailyPlanMigration(context, 5)).allowMainThreadQueries().build()
            assertEquals(before, BackupRepository(context, migrated.lexiDao()).snapshot())
            assertEquals(3, service.points().size)
        } finally { migrated.close(); context.deleteDatabase(name) }
    }

    @Test fun `failure to create upgrade backup leaves original v3 database untouched`() {
        val name = "blocked-v3.db"
        val old = BackupData(a, listOf(word().copy(ebbinghausStage = 5)), emptyList(), settings)
        createV3(name, old)
        File(context.noBackupFilesDir, "recovery").writeText("blocked")
        val migrating = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(LibraryMigration(context), DailyPlanMigration(context), DailyPlanMigration(context, 5)).allowMainThreadQueries().build()
        try { assertThrows(Exception::class.java) { migrating.openHelper.writableDatabase } }
        finally { migrating.close() }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use {
            assertEquals(3, it.version)
            it.rawQuery("SELECT ebbinghausStage FROM word_formulas", null).use { c -> assertTrue(c.moveToFirst()); assertEquals(5, c.getInt(0)) }
        }
        context.deleteDatabase(name)
    }

    @Test fun `full CET4 and CET6 coexist and complete backup roundtrip preserves both`() = runBlocking {
        val root = File("../../content-samples")
        Assume.assumeTrue(File(root, "cet4-legacy.json").exists())
        val first = WordbookImporter.parse(File(root, "cet4-legacy.json").readText(), a)
        val second = WordbookImporter.parse(File(root, "cet6-legacy.json").readText(), b)
        db.lexiDao().ensureLibrary(a, now)
        db.lexiDao().installBook(a, first.components, first.words)
        db.lexiDao().updateWordEbbinghaus(first.words.first().id, true, now, 3, now + 10000)
        db.lexiDao().installBook(b, second.components, second.words)
        val service = BackupRepository(context, db.lexiDao())
        val original = service.snapshot()
        assertEquals(3992, original.words.size)
        assertEquals(4544, original.otherBooks.single().words.size)
        val encoded = ByteArrayOutputStream()
        BackupFormat.write(original, encoded)
        service.restore(service.preview(BackupFormat.read(encoded.toByteArray().inputStream())))
        assertEquals(original, service.snapshot())
        repo.selectBook(a)
        assertEquals(3, db.lexiDao().wordSnapshot().single { it.id == first.words.first().id }.ebbinghausStage)
    }
}
