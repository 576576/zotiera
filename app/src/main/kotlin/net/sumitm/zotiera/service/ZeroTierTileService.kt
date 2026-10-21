package net.sumitm.zotiera.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import net.sumitm.zotiera.R
import net.sumitm.zotiera.state.NodeState
import net.sumitm.zotiera.ZotieraApplication
import net.sumitm.zotiera.ui.MainActivity
import net.sumitm.zotiera.ui.toast

/**
 * 快捷设置里的隧道开关。
 *
 * 状态只看服务实例在不在跑；连哪些网络仍由应用内的各个开关决定。
 */
class ZeroTierTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        listening = this
        updateTile()
    }

    override fun onStopListening() {
        listening = null
        super.onStopListening()
    }

    override fun onDestroy() {
        listening = null
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        if (NodeState.running.value) stopTunnel() else startTunnel()
        updateTile()
    }

    private fun startTunnel() {
        val hasNetwork = runCatching {
            (application as ZotieraApplication).repository.lastActivatedNetworks().isNotEmpty()
        }.getOrDefault(false)
        if (!hasNetwork) {
            toast(this, R.string.tunnel_needs_network)
            return
        }
        // 没授权时无法在此启动 VPN：回到应用里走授权流程
        if (VpnService.prepare(this) != null) {
            toast(this, R.string.tile_needs_permission)
            openApp()
            return
        }
        ServiceCommands.start(this)
    }

    private fun stopTunnel() {
        ServiceCommands.stop(this)
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val running = NodeState.running.value
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        tile.subtitle = getString(if (running) R.string.tile_on else R.string.tile_off)
        tile.updateTile()
    }

    companion object {
        /** 正在面板里显示的那个实例；面板关着时为空。 */
        private var listening: ZeroTierTileService? = null

        /**
         * 隧道状态变了：让磁贴重新读一次。
         *
         * 面板开着时 requestListeningState 不会再回调 onStartListening，只能直接改。
         */
        @JvmStatic
        fun refreshTile(context: Context) {
            listening?.updateTile()
            runCatching {
                requestListeningState(
                    context,
                    ComponentName(context, ZeroTierTileService::class.java),
                )
            }
        }
    }
}
