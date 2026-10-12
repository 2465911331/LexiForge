package com.example.offline

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Verified extraction into noBackupFilesDir; partial files never become usable models. */
class ModelFiles(
    private val name: String = NAME,
    private val size: Long = SIZE,
    private val sha256: String = SHA256
) {
    fun prepare(directory: File, openAsset: () -> InputStream, checkActive: () -> Unit,
                progress: (String) -> Unit): File {
        check(directory.isDirectory || directory.mkdirs()) { "无法创建离线模型目录" }
        directory.listFiles()?.filter { it.name.startsWith("extract-") && it.extension == "part" }?.forEach { it.delete() }
        val destination = File(directory, name)
        fun digest(file: File): String {
            val hash = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer = ByteArray(512 * 1024)
                while (true) {
                    checkActive()
                    val read = stream.read(buffer)
                    if (read < 0) break
                    hash.update(buffer, 0, read)
                }
            }
            return hash.digest().joinToString("") { "%02x".format(it) }
        }
        progress("校验本地模型…")
        if (destination.length() == size && digest(destination) == sha256) return destination
        check(directory.usableSpace >= size + 64L * 1024 * 1024) { "存储空间不足，请至少腾出 800 MB 后重试" }
        val temporary = File.createTempFile("extract-", ".part", directory)
        try {
            val hash = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            var lastPercent = -1
            openAsset().use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(512 * 1024)
                    while (true) {
                        checkActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        copied += read
                        check(copied <= size) { "模型文件大小异常" }
                        output.write(buffer, 0, read)
                        hash.update(buffer, 0, read)
                        val percent = (copied * 100 / size).toInt()
                        if (percent != lastPercent) { progress("首次准备模型 $percent%…"); lastPercent = percent }
                    }
                    output.fd.sync()
                }
            }
            check(copied == size && hash.digest().joinToString("") { "%02x".format(it) } == sha256) {
                "模型完整性校验失败，请重新安装测试版"
            }
            checkActive()
            // Existing destination is known to be invalid; remove it only after a verified replacement exists.
            check(!destination.exists() || destination.delete()) { "无法替换损坏的模型文件" }
            check(temporary.renameTo(destination)) { "无法保存离线模型，请检查存储空间" }
            return destination
        } finally { temporary.delete() }
    }

    companion object {
        const val NAME = "MiniCPM5-1B-Q4_K_M.gguf"
        const val SIZE = 688065920L
        const val SHA256 = "81b64d05a23b17b34c475f42b3e72fbde62d4b92cc34541f7a8031d0752deafa"
    }
}
