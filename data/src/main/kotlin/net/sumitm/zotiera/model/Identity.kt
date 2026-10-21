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
    /** 序号，从 1 起按加入顺序分配。 */
    @ColumnInfo(name = "ordinal")
    var ordinal: Int = 0,
    /** 默认身份：新建网络与简易模式都用它，全局只有一个。 */
    @ColumnInfo(name = "is_default")
    var isDefault: Boolean = false,
    /** 停用后不出现在选择列表里；默认身份不能停用。 */
    @ColumnInfo(name = "enabled")
    var enabled: Boolean = true,
) {
    /** 10 位小写节点 ID；还没生成时为空串。 */
    val nodeIdString: String
        get() = nodeId?.let { String.format("%10x", it) } ?: ""
}
