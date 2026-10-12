package com.example.data

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object DataSafety {
    val mutex = Mutex()
    @Volatile var recoveryRequired = false
    @Volatile var generation = 0L
    suspend fun <T> write(block: suspend () -> T): T = mutex.withLock {
        check(!recoveryRequired) { "恢复尚未完成，请重新打开应用" }
        block()
    }
}

data class RestorePreview(val backup: VerifiedBackup, val currentFingerprint: String)

class BackupRepository(
    private val context: Context, private val dao: LexiDao,
    private val afterDatabaseWrite: () -> Unit = {}
) {
    private val prefs get() = context.getSharedPreferences("lexiforge_prefs", 0)
    private val directory get() = File(context.noBackupFilesDir, "recovery").apply { check(exists() || mkdirs()) }
    private val pending get() = AtomicFile(File(directory, "pending-restore"))

    // Called only while holding DataSafety.mutex. DAO snapshot is also a SQLite transaction.
    suspend fun snapshot(): BackupData {
        val p = prefs.all
        return dao.librarySnapshot(BackupPreferences(
                p["theme_mode"] as? String ?: "SYSTEM", p["dynamic_color"] as? Boolean ?: true,
                p["ebbinghaus_pref_default_enabled"] as? Boolean ?: false,
                p["local_speech_only"] as? Boolean ?: false, p["selected_tts_engine"] as? String ?: "system"),
            p["current_wordbook_id"] as? String ?: "built-in")
    }

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "export-${UUID.randomUUID()}.lexiforge-backup")
        try {
            DataSafety.write { writeFile(file, snapshot()) }
            val source = file.inputStream().use(BackupFormat::read)
            context.contentResolver.openOutputStream(uri, "wt")?.use { output -> file.inputStream().use { it.copyTo(output) } }
                ?: error("无法写入所选位置")
            val result = context.contentResolver.openInputStream(uri)?.use(BackupFormat::read)
                ?: error("已写入，但无法重新读取验证；请更换保存位置")
            check(source == result) { "写入后校验不一致，请更换保存位置" }
            check(prefs.edit().putLong("last_verified_backup", System.currentTimeMillis()).commit()) { "文件已验证，但记录备份时间失败" }
        } finally { file.delete() }
    }

    suspend fun preview(uri: Uri): RestorePreview = withContext(Dispatchers.IO) {
        val backup = context.contentResolver.openInputStream(uri)?.use(BackupFormat::read) ?: error("无法读取所选文件")
        preview(backup)
    }

    suspend fun preview(backup: VerifiedBackup): RestorePreview = DataSafety.write {
        BackupFormat.validate(backup.data)
        RestorePreview(backup, BackupFormat.fingerprint(snapshot()))
    }

    suspend fun previewPoint(file: File): RestorePreview = withContext(Dispatchers.IO) {
        require(file.canonicalFile.parentFile == directory.canonicalFile && file.name.startsWith("point-"))
        preview(file.inputStream().use(BackupFormat::read))
    }

    fun points(): List<File> = directory.listFiles()?.filter { it.name.startsWith("point-") && it.extension == "lexiforge-backup" }
        ?.sortedByDescending { it.name } ?: emptyList()

    // Public to the repository layer; caller must hold the write mutex.
    suspend fun createPointLocked(): File = withContext(Dispatchers.IO) {
        val file = File(directory, "point-${System.currentTimeMillis()}-${UUID.randomUUID()}.lexiforge-backup")
        writeFile(file, snapshot())
        file.inputStream().use(BackupFormat::read)
        points().drop(3).forEach { it.delete() }
        file
    }

    suspend fun restore(preview: RestorePreview) = withContext(Dispatchers.IO) {
        DataSafety.write {
            check(BackupFormat.fingerprint(snapshot()) == preview.currentFingerprint) { "当前数据已变化，请重新选择备份并查看预览" }
            replaceProtectedLocked(preview.backup.data)
        }
    }

    suspend fun repairOfflineContent(): Boolean = withContext(Dispatchers.IO) {
        DataSafety.write {
            OfflineMorphology.validatePack()
            val original = snapshot()
            val repaired = OfflineMorphology.prepare(original)
            if (original == repaired) false else {
                replaceProtectedLocked(repaired)
                true
            }
        }
    }

    suspend fun replaceProtectedLocked(data: BackupData) = withContext(Dispatchers.IO) {
        BackupFormat.validate(data)
        createPointLocked()
        // Once the durable intent exists, startup must finish it before allowing any learning.
        withContext(NonCancellable) {
            writeAtomic(pending, data)
            DataSafety.recoveryRequired = true
            finishPending()
        }
    }

    suspend fun resumePending(): Boolean = withContext(Dispatchers.IO) {
        DataSafety.mutex.withLock {
            if (!pending.baseFile.exists() && !File(pending.baseFile.path + ".bak").exists()) {
                DataSafety.recoveryRequired = false
                false
            } else {
                DataSafety.recoveryRequired = true
                withContext(NonCancellable) { finishPending() }
                true
            }
        }
    }

    private suspend fun finishPending() {
        val data = OfflineMorphology.prepare(pending.openRead().use(BackupFormat::read).data)
        BackupFormat.validate(data)
        dao.restoreLibrary(data)
        DataSafety.generation++
        afterDatabaseWrite()
        val p = data.preferences
        check(prefs.edit().putString("current_wordbook_id", data.bookId)
            .putString("theme_mode", p.theme).putBoolean("dynamic_color", p.dynamicColor)
            .putBoolean("ebbinghaus_pref_default_enabled", p.dueOnly)
            .putBoolean("local_speech_only", p.localSpeechOnly).putString("selected_tts_engine", p.ttsEngine)
            .putBoolean("preserve_restored_content", true)
            .putBoolean("reload_learning_state", true)
            .putLong("last_restore_at", System.currentTimeMillis()).commit()) { "恢复设置未完成，请重试；恢复点已保留" }
        check(snapshot() == data.copy(words = data.words.sortedBy { it.id }, components = data.components.sortedBy { it.id },
            events = data.events.sortedBy { it.sequence })) {
            "恢复后的数据校验未通过，请重试"
        }
        pending.delete()
        check(!pending.baseFile.exists()) { "恢复记录尚未完成清理，请重试" }
        DataSafety.recoveryRequired = false
    }

    private fun writeFile(file: File, data: BackupData) = writeAtomic(AtomicFile(file), data)

    private fun writeAtomic(file: AtomicFile, data: BackupData) {
        val stream = file.startWrite()
        try {
            // ZIP closing must not close AtomicFile's descriptor before finishWrite syncs it.
            BackupFormat.write(data, object : java.io.FilterOutputStream(stream) {
                override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
                override fun close() { flush() }
            })
            file.finishWrite(stream)
            file.openRead().use(BackupFormat::read)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
    }
}
