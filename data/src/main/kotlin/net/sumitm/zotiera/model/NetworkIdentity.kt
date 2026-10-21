package net.sumitm.zotiera.model

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * 某条网络用过的身份。
 *
 * 换身份等于换一个节点：原节点在这条网络上的成员资格不跟过来，管理员得重新授权。
 * 所以「第一次用某个身份连这条网络」要二次确认，这里就是判断依据。
 */
@Entity(tableName = "network_identities", primaryKeys = ["network_id", "identity_uid"])
class NetworkIdentity @JvmOverloads constructor(
    @ColumnInfo(name = "network_id")
    var networkId: Long = 0L,
    @ColumnInfo(name = "identity_uid")
    var identityUid: Long = 0L,
    @ColumnInfo(name = "used_at")
    var usedAt: Long = 0L,
)
