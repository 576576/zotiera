package net.sumitm.zotiera

import com.zerotier.sdk.VirtualNetworkStatus
import net.sumitm.zotiera.ui.component.AppStatusTone
import net.sumitm.zotiera.ui.screens.statusToneOf
import org.junit.Assert.assertEquals
import org.junit.Test

/** 设备卡语气：隧道、节点、每个网络的配置状态三者怎么合成一个状态。 */
class StatusToneTest {

    private val ok = VirtualNetworkStatus.NETWORK_STATUS_OK
    private val denied = VirtualNetworkStatus.NETWORK_STATUS_ACCESS_DENIED
    private val notFound = VirtualNetworkStatus.NETWORK_STATUS_NOT_FOUND
    private val requesting = VirtualNetworkStatus.NETWORK_STATUS_REQUESTING_CONFIGURATION
    private val authRequired = VirtualNetworkStatus.NETWORK_STATUS_AUTHENTICATION_REQUIRED

    private fun tone(
        tunnelRunning: Boolean = true,
        nodeOnline: Boolean = true,
        statuses: List<VirtualNetworkStatus?>,
    ) = statusToneOf(tunnelRunning, nodeOnline, statuses)

    @Test
    fun noNetworkIsEmpty() {
        assertEquals(AppStatusTone.EMPTY, tone(statuses = emptyList()))
    }

    @Test
    fun tunnelOffBeatsEverything() {
        assertEquals(AppStatusTone.STOPPED, tone(tunnelRunning = false, statuses = listOf(ok)))
    }

    @Test
    fun offlineNodeIsConnecting() {
        assertEquals(AppStatusTone.CONNECTING, tone(nodeOnline = false, statuses = listOf(ok)))
    }

    @Test
    fun allNetworksOkIsWorking() {
        assertEquals(AppStatusTone.WORKING, tone(statuses = listOf(ok, ok)))
    }

    @Test
    fun oneOkOneDeniedIsPartial() {
        assertEquals(AppStatusTone.WORKING_PARTIAL, tone(statuses = listOf(ok, denied)))
    }

    @Test
    fun oneOkOneWithoutConfigIsPartial() {
        assertEquals(AppStatusTone.WORKING_PARTIAL, tone(statuses = listOf(ok, null)))
    }

    @Test
    fun allDeniedIsEmpty() {
        assertEquals(AppStatusTone.EMPTY, tone(statuses = listOf(denied, denied)))
    }

    @Test
    fun allNotFoundIsEmpty() {
        assertEquals(AppStatusTone.EMPTY, tone(statuses = listOf(notFound)))
    }

    @Test
    fun nothingConnectedButStillPossibleIsConnecting() {
        assertEquals(AppStatusTone.CONNECTING, tone(statuses = listOf(null, null)))
        assertEquals(AppStatusTone.CONNECTING, tone(statuses = listOf(requesting, requesting)))
        assertEquals(AppStatusTone.CONNECTING, tone(statuses = listOf(authRequired)))
        // 一条被拒、另一条还没结论：不是「全被拒」，仍在连接中
        assertEquals(AppStatusTone.CONNECTING, tone(statuses = listOf(denied, null)))
    }
}
