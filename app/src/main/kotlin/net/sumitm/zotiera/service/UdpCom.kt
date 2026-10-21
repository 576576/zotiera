package net.sumitm.zotiera.service

import android.util.Log
import com.zerotier.sdk.Node
import com.zerotier.sdk.PacketSender
import com.zerotier.sdk.ResultCode
import net.sumitm.zotiera.util.DebugLog
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

class UdpCom(
    private val ztService: ZeroTierOneService,
    private val svrSocket: DatagramSocket?,
) : PacketSender, Runnable {

    private lateinit var node: Node

    fun setNode(node: Node) {
        this.node = node
    }

    override fun onSendPacketRequested(
        networkId: Long,
        address: InetSocketAddress,
        data: ByteArray,
        ttl: Int,
    ): Int {
        val socket = svrSocket ?: run {
            Log.e(TAG, "Attempted to send packet on a null socket")
            return -1
        }
        return try {
            val packet = DatagramPacket(data, data.size, address)
            DebugLog.d(TAG, "onSendPacketRequested: Sent ${packet.length} bytes to $address")
            socket.send(packet)
            0
        } catch (e: Exception) {
            -1
        }
    }

    override fun run() {
        Log.d(TAG, "UDP Listen Thread Started.")
        val socket = svrSocket
        try {
            val deadline = LongArray(1)
            val buffer = ByteArray(16384)
            while (!Thread.interrupted()) {
                deadline[0] = 0
                val packet = DatagramPacket(buffer, 16384)
                try {
                    socket!!.receive(packet)
                    if (packet.length > 0) {
                        val frame = ByteArray(packet.length)
                        System.arraycopy(packet.data, 0, frame, 0, packet.length)
                        DebugLog.d(TAG, "Got ${packet.length} Bytes From: ${packet.address}:${packet.port}")
                        val result = node.processWirePacket(
                            System.currentTimeMillis(),
                            -1,
                            InetSocketAddress(packet.address, packet.port),
                            frame,
                            deadline,
                        )
                        if (result != ResultCode.RESULT_OK) {
                            Log.e(TAG, "processWirePacket returned: $result")
                            ztService.shutdown()
                        }
                        ztService.setNextBackgroundTaskDeadline(deadline[0])
                    }
                } catch (e: SocketTimeoutException) {
                    // 收包超时是正常的，继续等
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Log.d(TAG, "UDP Listen Thread Ended.")
    }

    private companion object {
        const val TAG = "UdpCom"
    }
}
