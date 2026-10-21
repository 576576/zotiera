package net.sumitm.zotiera.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一份节点身份。`node_id` 为空表示密钥还没生成（第一次启用该实例时由引擎补齐）。
 *
 * 密钥文件存在 `files/identities/<uid>/identity.{secret,public}`。
 */
@Entity(tableName = "identities")
class Identity @JvmOverloads constructor(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "uid")
    var uid: Long = 0L,
    @ColumnInfo(name = "alias")
    var alias: String = "",
    @ColumnInfo(name = "node_id")
    var nodeId: Long? = null,
    @ColumnInfo(name = "created_at")
    var createdAt: Long = 0L,
) {
    /** 10 位小写节点 ID；还没生成时为空串。 */
    val nodeIdString: String
        get() = nodeId?.let { String.format("%10x", it) } ?: ""

    companion object {
        /** 迁移建出来的默认身份。 */
        const val DEFAULT_UID = 1L
    }
}
