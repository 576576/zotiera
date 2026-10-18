package net.sumitm.zotiera.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 本机节点。正常情况下只有一行。
 */
@Entity(tableName = "app_node")
class AppNode @JvmOverloads constructor(
    @PrimaryKey
    @ColumnInfo(name = "node_id")
    var nodeId: Long = 0L,
    @ColumnInfo(name = "node_id_str")
    var nodeIdStr: String? = null,
)
