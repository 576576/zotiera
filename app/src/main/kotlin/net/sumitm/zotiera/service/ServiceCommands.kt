package net.sumitm.zotiera.service

import android.content.Context
import android.content.Intent

/**
 * 服务的命令入口。
 *
 * 命令必须能在服务没起来时生效（磁贴、页面里加网络），所以走 `startService` + action 而不是 Flow。
 */
object ServiceCommands {
    const val ACTION_START = "net.sumitm.zotiera.action.START"
    const val ACTION_STOP = "net.sumitm.zotiera.action.STOP"
    const val ACTION_JOIN = "net.sumitm.zotiera.action.JOIN"
    const val ACTION_LEAVE = "net.sumitm.zotiera.action.LEAVE"
    const val ACTION_RECONFIGURE = "net.sumitm.zotiera.action.RECONFIGURE"
    const val ACTION_ORBIT = "net.sumitm.zotiera.action.ORBIT"
    const val ACTION_DEORBIT = "net.sumitm.zotiera.action.DEORBIT"

    const val EXTRA_NETWORK_ID = "network_id"
    const val EXTRA_WORLD_ID = "world_id"
    const val EXTRA_SEED = "seed"

    @JvmStatic
    fun start(context: Context) = send(context, ACTION_START)

    @JvmStatic
    fun stop(context: Context) = send(context, ACTION_STOP)

    @JvmStatic
    fun join(context: Context, networkId: Long) =
        send(context, ACTION_JOIN) { it.putExtra(EXTRA_NETWORK_ID, networkId) }

    @JvmStatic
    fun leave(context: Context, networkId: Long) =
        send(context, ACTION_LEAVE) { it.putExtra(EXTRA_NETWORK_ID, networkId) }

    /** 隧道配置变了（全隧道开关、DNS 模式），按当前网络集合重建。 */
    @JvmStatic
    fun reconfigure(context: Context) = send(context, ACTION_RECONFIGURE)

    @JvmStatic
    fun orbit(context: Context, worldId: Long, seed: Long) = send(context, ACTION_ORBIT) {
        it.putExtra(EXTRA_WORLD_ID, worldId).putExtra(EXTRA_SEED, seed)
    }

    @JvmStatic
    fun deorbit(context: Context, worldId: Long, seed: Long) = send(context, ACTION_DEORBIT) {
        it.putExtra(EXTRA_WORLD_ID, worldId).putExtra(EXTRA_SEED, seed)
    }

    private fun send(context: Context, action: String, fill: (Intent) -> Unit = {}) {
        val intent = Intent(context, ZeroTierOneService::class.java).setAction(action)
        fill(intent)
        context.startService(intent)
    }
}
