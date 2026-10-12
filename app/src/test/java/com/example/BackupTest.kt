package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.*
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var service: BackupRepository
    private val preferences = BackupPreferences("DARK", false, true, true, "example.tts")
    private fun sample() = BackupData("3-CET4-顺序", BuiltInWordbook.words.mapIndexed { index, word ->
        word.copy(isAssembled = index % 2 == 0, lastReviewedAt = 1800000000000L + index,
            ebbinghausStage = index % 9, nextReviewAt = 1800000100000L + index)
    }, BuiltInWordbook.components.map { it.copy(reviewCount = 3, isMastered = true, lastReviewedAt = 1800000000000L) }, preferences)

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        DataSafety.recoveryRequired = false
        context.getSharedPreferences("lexiforge_prefs", 0).edit().clear().commit()
        // Test-owned directory under Robolectric's application sandbox.
        File(context.noBackupFilesDir, "recovery").deleteRecursively()
        db = Room.databaseBuilder(context, AppDatabase::class.java, "backup-test.db").allowMainThreadQueries().build()
        service = BackupRepository(context, db.lexiDao())
    }
    @After fun teardown() { db.close(); context.deleteDatabase("backup-test.db"); DataSafety.recoveryRequired = false }

    private fun encoded(data: BackupData = sample()): ByteArray = ByteArrayOutputStream().also { BackupFormat.write(data, it) }.toByteArray()
    private fun decoded(data: BackupData = sample()) = BackupFormat.read(encoded(data).inputStream())
    private suspend fun seed() { db.lexiDao().replaceAll(BuiltInWordbook.components, BuiltInWordbook.words) }
    private fun rewritten(bytes: ByteArray, change: (String, ByteArray) -> Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip -> ZipInputStream(bytes.inputStream()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val (name, data) = change(entry.name, input.readBytes())
                zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry()
            }
        } }
        return output.toByteArray()
    }

    @Test fun `format roundtrip preserves every state and text field`() {
        assertEquals(sample(), decoded().data)
    }

    @Test fun `corruption future format and unexpected zip paths are rejected`() {
        val original = encoded()
        val broken = rewritten(original) { name, data -> name to if (name == "data.json") data + ' '.code.toByte() else data }
        val future = rewritten(original) { name, data -> name to if (name == "manifest.json") data.toString(Charsets.UTF_8).replace("\"formatVersion\":5", "\"formatVersion\":99").toByteArray() else data }
        val path = rewritten(original) { name, data -> (if (name == "data.json") "../data.json" else name) to data }
        for (bytes in listOf(broken, future, path, original.copyOf(40))) {
            assertThrows(Exception::class.java) { BackupFormat.read(bytes.inputStream()) }
        }
    }

    @Test fun `invalid progress duplicate identities missing references and oversized text are rejected`() {
        val data = sample()
        val first = data.words.first()
        val invalid = listOf(data.copy(words = data.words + first),
            data.copy(words = listOf(first.copy(ebbinghausStage = 9))),
            data.copy(words = listOf(first.copy(rootId = "missing"))),
            data.copy(words = listOf(first.copy(sentenceEn = "a".repeat(65537)))))
        invalid.forEach { assertThrows(IllegalArgumentException::class.java) { encoded(it) } }
    }

    @Test fun `zip bomb expansion is bounded`() {
        val bytes = ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            repeat(BackupFormat.MAX_DATA_BYTES / 8192 + 1) { zip.write(ByteArray(8192)) }
            zip.closeEntry()
        } }.toByteArray()
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.read(bytes.inputStream()) }
    }

    @Test fun `restore preserves snapshot and settings after reopen and creates usable rollback point`() = runBlocking {
        seed()
        val before = service.snapshot()
        service.restore(service.preview(decoded()))
        assertEquals(sample().copy(words = sample().words.sortedBy { it.id }, components = sample().components.sortedBy { it.id }), service.snapshot())
        assertEquals(before, service.points().single().inputStream().use(BackupFormat::read).data)
        db.close()
        db = Room.databaseBuilder(context, AppDatabase::class.java, "backup-test.db").allowMainThreadQueries().build()
        service = BackupRepository(context, db.lexiDao())
        assertFalse(service.resumePending())
        LexiRepository(db.lexiDao(), context).checkAndPrepopulate(true)
        assertEquals("3-CET4-顺序", service.snapshot().bookId)
        assertEquals(preferences, service.snapshot().preferences)
        assertTrue(service.snapshot().words.all { it.lastReviewedAt > 0 })
    }

    @Test fun `changed data invalidates preview without overwriting newer answer`() = runBlocking {
        seed()
        val preview = service.preview(decoded())
        db.lexiDao().updateWordAssembly("attract", true)
        val before = service.snapshot()
        assertThrows(IllegalStateException::class.java) { runBlocking { service.restore(preview) } }
        assertEquals(before, service.snapshot())
        assertTrue(service.points().isEmpty())
    }

    @Test fun `interruption after database commit resumes preferences before allowing writes`() = runBlocking {
        seed()
        val interrupted = BackupRepository(context, db.lexiDao()) { throw IOException("simulated process stop") }
        assertThrows(IOException::class.java) { runBlocking { interrupted.restore(interrupted.preview(decoded())) } }
        assertTrue(DataSafety.recoveryRequired)
        assertFalse(LexiRepository(db.lexiDao()).updateWordAssembly("attract", false))
        assertTrue(service.resumePending())
        assertFalse(DataSafety.recoveryRequired)
        assertEquals(preferences, service.snapshot().preferences)
        assertEquals("3-CET4-顺序", service.snapshot().bookId)
        assertFalse(service.resumePending())
        assertEquals(1, service.points().size)
    }

    @Test fun `transaction insert failure retains old database and confirmed operation can retry`() = runBlocking {
        seed()
        val before = service.snapshot()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_backup BEFORE INSERT ON word_formulas BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        assertThrows(Exception::class.java) { runBlocking { service.restore(service.preview(decoded())) } }
        assertEquals(before, service.snapshot())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_backup")
        assertTrue(service.resumePending())
        assertEquals(preferences, service.snapshot().preferences)
    }

    @Test fun `replacement and reset create at most three verified points`() = runBlocking {
        seed()
        val repository = LexiRepository(db.lexiDao(), context)
        repository.replaceWordbook(sample().components, sample().words, "3-CET4-顺序")
        assertEquals("3-CET4-顺序", service.snapshot().bookId)
        repeat(4) { repository.resetAllLearningData() }
        assertEquals(3, service.points().size)
        service.points().forEach { it.inputStream().use(BackupFormat::read) }
        assertTrue(service.snapshot().words.all { it.lastReviewedAt == 0L })
    }

    @Test fun `failed recovery point creation prevents destructive reset`() = runBlocking {
        seed()
        db.lexiDao().updateWordAssembly("attract", true)
        val before = service.snapshot()
        File(context.noBackupFilesDir, "recovery").writeText("cannot be a directory")
        assertThrows(Exception::class.java) { runBlocking { LexiRepository(db.lexiDao(), context).resetAllLearningData() } }
        assertEquals(before, service.snapshot())
    }

    @Test fun `verified export can be previewed and restored to empty database`() = runBlocking {
        seed()
        val before = service.snapshot()
        val file = File(context.cacheDir, "roundtrip.lexiforge-backup")
        service.export(Uri.fromFile(file))
        assertTrue(context.getSharedPreferences("lexiforge_prefs", 0).getLong("last_verified_backup", 0) > 0)
        db.lexiDao().replaceAll(emptyList(), emptyList())
        service.restore(service.preview(Uri.fromFile(file)))
        assertEquals(before, service.snapshot())
    }

    @Test fun `unsupported schema is not silently destroyed`() {
        val file = context.getDatabasePath("unsupported.db")
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use {
            it.execSQL("CREATE TABLE precious (value TEXT)")
            it.execSQL("INSERT INTO precious VALUES ('retain me')")
            it.version = 2
        }
        val unsupported = Room.databaseBuilder(context, AppDatabase::class.java, "unsupported.db").allowMainThreadQueries().build()
        try { assertThrows(IllegalStateException::class.java) { unsupported.openHelper.writableDatabase } }
        finally { unsupported.close() }
        android.database.sqlite.SQLiteDatabase.openDatabase(file.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use {
            it.rawQuery("SELECT value FROM precious", null).use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals("retain me", cursor.getString(0)) }
        }
        context.deleteDatabase("unsupported.db")
    }

    @Test fun `queued answer from earlier data cannot change a replacement`() = runBlocking {
        seed()
        DataSafety.mutex.lock()
        val write = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            LexiRepository(db.lexiDao()).updateWordAssembly("attract", true)
        }
        DataSafety.generation++
        DataSafety.mutex.unlock()
        assertFalse(write.await())
        assertFalse(db.lexiDao().wordSnapshot().first { it.id == "attract" }.isAssembled)
    }
}
