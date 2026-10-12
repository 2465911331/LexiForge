package com.example

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
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
class DailyPlanDataTest {
    private lateinit var context: Context
    private val now = 1_800_000_000_000L
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val prefs = BackupPreferences("SYSTEM", true, false, false, "system")
    private fun word() = BuiltInWordbook.words.single { it.id == "sensitive" }
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("lexiforge_prefs", 0).edit().clear().commit()
        DataSafety.recoveryRequired = false
    }

    @Test fun planRoundtrip() = runBlocking {
        val name = "plan.db"
        var db = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            var dao = db.lexiDao()
            dao.ensureLibrary("A", now)
            dao.installBook("A", emptyList(), listOf(word()))
            var repo = LexiRepository(dao, context)
            repo.setDailyTarget("A", 30)
            val request = AnswerRequest(UUID.randomUUID().toString(), "session", "A", word().id, "WORD", "SPELLING", "wrong", false, "ANALYSIS", 1, now, 1000)
            assertTrue(repo.recordAnswer(request, DataSafety.generation))
            assertEquals(true, dao.eventSnapshot().single().firstLearning)
            assertTrue(repo.recordAnswer(request.copy(id = "retry", correct = true, answer = word().spell, attempt = 2), DataSafety.generation))
            var plan = DailyPlanner.calculate(dao.dailyInputs(now - 100, now + 100), now, DayWindow(now - 100, now + 100))
            assertEquals(1, plan.newDone)
            dao.installBook("B", emptyList(), listOf(word()))
            repo.setDailyTarget("B", 0)
            assertEquals(0, DailyPlanner.calculate(dao.dailyInputs(now - 100, now + 100), now).newDone)
            val service = BackupRepository(context, dao)
            val data = service.snapshot()
            assertEquals(mapOf("A" to 30, "B" to 0), data.dailyTargets)
            val bytes = ByteArrayOutputStream().also { BackupFormat.write(data, it) }.toByteArray()
            service.restore(service.preview(BackupFormat.read(bytes.inputStream())))
            assertEquals(OfflineMorphology.prepare(data), service.snapshot())
            db.close()
            db = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
            dao = db.lexiDao(); repo = LexiRepository(dao, context)
            repo.selectBook("A")
            plan = DailyPlanner.calculate(dao.dailyInputs(now - 100, now + 100), now, DayWindow(now - 100, now + 100))
            assertEquals(30, plan.target); assertEquals(1, plan.newDone)
            repo.resetAllLearningData()
            assertEquals(30, dao.bookSnapshot().single { it.id == "A" }.dailyNewTarget)
            assertEquals(2, dao.eventSnapshot().size)
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.setDailyTarget("A", 201) } }
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.setDailyTarget("B", 10) } }
            assertThrows(IllegalArgumentException::class.java) { BackupFormat.validate(data.copy(dailyTargets = mapOf("missing" to 20))) }
            val defaultTarget = data.copy(dailyTargets = mapOf("A" to 20, "B" to 0))
            val normalized = OfflineMorphology.prepare(defaultTarget)
            assertEquals(mapOf("B" to 0), normalized.dailyTargets)
            BackupRepository(context, dao).replaceProtectedLocked(normalized)
            assertEquals(mapOf("B" to 0), BackupRepository(context, dao).snapshot().dailyTargets)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    private fun createV4(name: String, version: Int = 4): ReviewEvent {
        val event = ReviewEvent("e1", "s", "A", word().id, word().spell, "WORD", "SPELLING", "释义", word().spell,
            true, "ANALYSIS", 1, now, 100, 2, 3, true, sequence = 1,
            firstLearning = if (version == 5) false else null, dueReview = if (version == 5) true else null)
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            val schema = JSONObject(File("schemas/com.example.data.AppDatabase/$version.json").readText()).getJSONObject("database")
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                db.execSQL(entity.getString("createSql").replace("$" + "{TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("$" + "{TABLE_NAME}", entity.getString("tableName")))
            }
            fun <T> insert(table: String, value: T, type: Class<T>) {
                val obj = JSONObject(moshi.adapter(type).toJson(value))
                if (version == 4) { obj.remove("dailyNewTarget"); obj.remove("firstLearning"); obj.remove("dueReview") }
                val values = ContentValues()
                obj.keys().forEach { key -> when (val v = obj.get(key)) {
                    JSONObject.NULL -> values.putNull(key)
                    is Boolean -> values.put(key, if (v) 1 else 0)
                    is Number -> values.put(key, v.toLong())
                    else -> values.put(key, v.toString())
                } }
                db.insertOrThrow(table, null, values)
            }
            for (id in listOf("A", "B")) {
                insert("wordbooks", Wordbook(id, word().id, if (version == 5) 30 else 20), Wordbook::class.java)
                insert("word_formulas", word().copy(bookId = id, lastReviewedAt = now, ebbinghausStage = 3, nextReviewAt = now + 1000), WordFormula::class.java)
            }
            insert("library_state", LibraryState(activeBookId = "A", historyStartedAt = now), LibraryState::class.java)
            insert("review_events", event, ReviewEvent::class.java)
            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table VALUES(42, ?)", arrayOf(schema.getString("identityHash")))
            db.version = version
        }
        return event
    }

    @Test fun v4migration() = runBlocking {
        val event = createV4("v4.db")
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "v4.db").addMigrations(DailyPlanMigration(context), DailyPlanMigration(context, 5)).allowMainThreadQueries().build()
        try {
            val dao = db.lexiDao()
            assertEquals(2, dao.bookSnapshot().size)
            assertTrue(dao.bookSnapshot().all { it.dailyNewTarget == 20 && it.lastWordId == word().id })
            assertEquals(listOf(event), dao.eventSnapshot())
            assertEquals(3, dao.wordSnapshot().single().ebbinghausStage)
            val point = BackupRepository(context, dao).points().first().inputStream().use(BackupFormat::read).data
            assertEquals(listOf(event), point.events)
            assertEquals(1, point.otherBooks.size)
            assertEquals(6, db.openHelper.writableDatabase.version)
        } finally { db.close(); context.deleteDatabase("v4.db") }
    }

    @Test fun v5migration() = runBlocking {
        val event = createV4("v5.db", 5)
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "v5.db").addMigrations(DailyPlanMigration(context, 5)).allowMainThreadQueries().build()
        try {
            val dao = db.lexiDao()
            assertEquals(listOf(event), dao.eventSnapshot())
            assertTrue(dao.bookSnapshot().all { it.dailyNewTarget == 30 })
            assertTrue(dao.correctionSnapshot().isEmpty())
            assertEquals(6, db.openHelper.writableDatabase.version)
            val point = BackupRepository(context, dao).points().single().inputStream().use(BackupFormat::read).data
            assertEquals(mapOf("A" to 30, "B" to 30), point.dailyTargets)
            assertEquals(listOf(event), point.events)
        } finally { db.close(); context.deleteDatabase("v5.db") }
    }

    @Test fun blockedV4() {
        createV4("v4.db")
        File(context.noBackupFilesDir, "recovery").writeText("blocked")
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "v4.db").addMigrations(DailyPlanMigration(context), DailyPlanMigration(context, 5)).allowMainThreadQueries().build()
        try { assertThrows(Exception::class.java) { db.openHelper.writableDatabase } } finally { db.close() }
        SQLiteDatabase.openDatabase(context.getDatabasePath("v4.db").path, null, SQLiteDatabase.OPEN_READONLY).use {
            assertEquals(4, it.version)
            it.rawQuery("SELECT COUNT(*) FROM review_events", null).use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        }
        context.deleteDatabase("v4.db")
    }

    @Test fun emptyV4() = runBlocking {
        createV4("v4.db")
        SQLiteDatabase.openDatabase(context.getDatabasePath("v4.db").path, null, SQLiteDatabase.OPEN_READWRITE).use { old ->
            listOf("review_events", "word_formulas", "affix_components", "wordbooks", "library_state").forEach { old.execSQL("DELETE FROM $it") }
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "v4.db").addMigrations(DailyPlanMigration(context), DailyPlanMigration(context, 5)).allowMainThreadQueries().build()
        try {
            val repo = LexiRepository(db.lexiDao(), context)
            repo.initializeLibrary()
            repo.checkAndPrepopulate()
            assertEquals(20, db.lexiDao().bookSnapshot().single().dailyNewTarget)
            assertEquals(18, db.lexiDao().wordSnapshot().size)
        } finally { db.close(); context.deleteDatabase("v4.db") }
    }

    @Test fun `actual v2 backup reads with default target and unknown historical newness`() {
        val data = BackupData("A", listOf(word()), emptyList(), prefs)
        val json = JSONObject(moshi.adapter(BackupData::class.java).toJson(data)).apply { remove("dailyTargets") }
        val bytes = json.toString().toByteArray()
        val manifest = BackupManifest(2, 2, 4, UUID.randomUUID().toString(), now, "1.0.8", 1, 0, BackupFormat.digest(bytes))
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { z ->
            z.putNextEntry(ZipEntry("manifest.json")); z.write(moshi.adapter(BackupManifest::class.java).toJson(manifest).toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("data.json")); z.write(bytes); z.closeEntry()
        }
        assertEquals(data, BackupFormat.read(output.toByteArray().inputStream()).data)
    }
}
