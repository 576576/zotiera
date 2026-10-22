package net.sumitm.zotiera.identity

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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

    // 按需解析：构造发生在界面组合期，而 getFilesDir() 会做一次文件存在性检查
    private val root by lazy { File(context.filesDir, InstanceStore.IDENTITIES_DIR) }

    fun dir(uid: Long): File = File(root, uid.toString())

    fun hasKeys(uid: Long): Boolean = File(dir(uid), SECRET).exists() && File(dir(uid), PUBLIC).exists()

    fun writeKeys(uid: Long, secret: ByteArray, public: ByteArray) {
        val dir = dir(uid)
        dir.mkdirs()
        File(dir, SECRET).writeBytes(secret)
        File(dir, PUBLIC).writeBytes(public)
    }

    /**
     * 把身份打包成 zip：两个密钥文件 + 一份说明。
     *
     * 密钥还没生成时返回 null。
     */
    fun buildZip(uid: Long): ByteArray? {
        val dir = dir(uid)
        val secret = File(dir, SECRET).takeIf { it.exists() }?.readBytes() ?: return null
        val public = File(dir, PUBLIC).takeIf { it.exists() }?.readBytes() ?: return null
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in listOf(
                SECRET to secret,
                PUBLIC to public,
                "README.txt" to README.toByteArray(Charsets.UTF_8),
            )) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
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

        private val README = """
            Zotiera identity export
            =======================

            identity.public  node id + public key (141 bytes, safe to share)
            identity.secret  private key record (270 bytes)

            Put both files into the data directory of a ZeroTier client to use the same
            node id there. On Android, import them in Zotiera: Manage identity -> Add
            identity -> Import from file. On desktop, copy them into the zerotier-one
            data directory.

            identity.secret is a private key: anyone who has it can act as this node.
            Do not upload it or share it. 这个文件等同私钥，泄露即可冒充该节点，不要上传。
        """.trimIndent()
    }
}
