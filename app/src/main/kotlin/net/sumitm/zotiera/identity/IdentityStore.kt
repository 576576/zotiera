package net.sumitm.zotiera.identity

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

/** 从导出件里读出的一对密钥。 */
data class ImportedIdentity(
    val nodeId: Long,
    val secret: ByteArray,
    val public: ByteArray,
)

/**
 * 身份密钥的正本：`files/identities/<uid>/identity.{secret,public}`。
 *
 * 实例目录里的密钥由 [InstanceStore] 下发与回收，这里只管正本本身。
 */
class IdentityStore(private val context: Context) {

    private val root = File(context.filesDir, InstanceStore.IDENTITIES_DIR)

    fun dir(uid: Long): File = File(root, uid.toString())

    fun hasKeys(uid: Long): Boolean = File(dir(uid), SECRET).exists() && File(dir(uid), PUBLIC).exists()

    fun writeKeys(uid: Long, secret: ByteArray, public: ByteArray) {
        val dir = dir(uid)
        dir.mkdirs()
        File(dir, SECRET).writeBytes(secret)
        File(dir, PUBLIC).writeBytes(public)
    }

    /**
     * 读导出的 zip（两个文件在根或任意一层目录里都认）。
     *
     * 只校验「公钥能解出节点 ID」与「私钥首段与公钥一致」；签名与可用性交给引擎判断。
     */
    fun parseZip(bytes: ByteArray): ImportedIdentity? {
        var secret: ByteArray? = null
        var public: ByteArray? = null
        runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.substringAfterLast('/')
                    val data = zip.readBytes()
                    when (name) {
                        SECRET -> secret = data
                        PUBLIC -> public = data
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }.getOrNull()

        val secretBytes = secret ?: return null
        val publicBytes = public ?: return null
        val publicId = publicBytes.nodeIdPrefix() ?: return null
        val secretId = secretBytes.nodeIdPrefix() ?: return null
        if (publicId != secretId) return null
        val nodeId = publicId.toLongOrNull(16) ?: return null
        return ImportedIdentity(nodeId, secretBytes, publicBytes)
    }

    private fun ByteArray.nodeIdPrefix(): String? {
        val text = String(this, Charsets.US_ASCII)
        val head = text.substringBefore(':').trim()
        return head.takeIf { it.length == 10 && it.all { c -> c in "0123456789abcdef" } }
    }

    companion object {
        private const val SECRET = "identity.secret"
        private const val PUBLIC = "identity.public"
    }
}
