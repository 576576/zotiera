package net.sumitm.zotiera.service;

import android.os.ParcelFileDescriptor;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import com.zerotier.sdk.Node;
import com.zerotier.sdk.ResultCode;
import com.zerotier.sdk.VirtualNetworkConfig;
import com.zerotier.sdk.VirtualNetworkFrameListener;
import com.zerotier.sdk.util.StringUtils;

import net.sumitm.zotiera.util.DebugLog;
import net.sumitm.zotiera.util.IPPacketUtils;
import net.sumitm.zotiera.util.InetAddressUtils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Objects;

// TODO: clear up
public class TunTapAdapter implements VirtualNetworkFrameListener {
    public static final String TAG = "TunTapAdapter";
    private static final int ARP_PACKET = 2054;
    private static final int IPV4_PACKET = 2048;
    private static final int IPV6_PACKET = 34525;
    private final HashMap<Route, Long> routeMap = new HashMap<>();
    private final ZeroTierOneService ztService;
    private FileInputStream in;
    // ARP / NDP 按网络隔离：不同网络的 MAC 空间互不相干
    private final HashMap<Long, ARPTable> arpTables = new HashMap<>();
    private final HashMap<Long, NDPTable> ndpTables = new HashMap<>();
    private Node node;
    private FileOutputStream out;
    private Thread receiveThread;
    private ParcelFileDescriptor vpnSocket;

    public TunTapAdapter(ZeroTierOneService zeroTierOneService) {
        this.ztService = zeroTierOneService;
    }

    private synchronized ARPTable arpTableFor(long nwid) {
        return arpTables.computeIfAbsent(nwid, k -> new ARPTable());
    }

    private synchronized NDPTable ndpTableFor(long nwid) {
        return ndpTables.computeIfAbsent(nwid, k -> new NDPTable());
    }

    private void clearTables() {
        synchronized (this) {
            arpTables.clear();
            ndpTables.clear();
        }
    }

    private void stopTables() {
        synchronized (this) {
            for (var table : arpTables.values()) {
                table.stop();
            }
            for (var table : ndpTables.values()) {
                table.stop();
            }
            arpTables.clear();
            ndpTables.clear();
        }
    }

    /**
     * 出站包属于哪个网络：先按目的地址找路由，找不到再用源地址反查（回包场景）。
     */
    private long resolveNetworkId(InetAddress destAddress, InetAddress sourceAddress) {
        long nwid = networkIdForDestination(destAddress);
        if (nwid != 0) {
            return nwid;
        }
        for (long id : this.ztService.getJoinedNetworkIds()) {
            var config = this.ztService.getVirtualNetworkConfig(id);
            if (config == null) {
                continue;
            }
            for (var address : config.getAssignedAddresses()) {
                if (address.getAddress().equals(sourceAddress)) {
                    return id;
                }
            }
        }
        return 0;
    }

    public static long multicastAddressToMAC(InetAddress inetAddress) {
        if (inetAddress instanceof Inet4Address) {
            byte[] address = inetAddress.getAddress();
            return ByteBuffer.wrap(new byte[]{0, 0, 1, 0, 94, (byte) (address[1] & Byte.MAX_VALUE), address[2], address[3]}).getLong();
        } else if (!(inetAddress instanceof Inet6Address)) {
            return 0;
        } else {
            byte[] address2 = inetAddress.getAddress();
            return ByteBuffer.wrap(new byte[]{0, 0, 51, 51, address2[12], address2[13], address2[14], address2[15]}).getLong();
        }
    }

    private void addMulticastRoutes() {
    }

    public void setNode(Node node) {
        this.node = node;
        // 组播订阅在 rebuildTunnel 里按地址做；此处节点尚未 join 网络，订阅必然失败
    }

    public void setVpnSocket(ParcelFileDescriptor vpnSocket) {
        this.vpnSocket = vpnSocket;
    }

    public void setFileStreams(FileInputStream fileInputStream, FileOutputStream fileOutputStream) {
        this.in = fileInputStream;
        this.out = fileOutputStream;
    }

    public void addRouteAndNetwork(Route route, long networkId) {
        synchronized (this.routeMap) {
            this.routeMap.put(route, networkId);
        }
    }

    public void clearRouteMap() {
        synchronized (this.routeMap) {
            this.routeMap.clear();
        }
        clearTables();
        addMulticastRoutes();
    }

    private boolean isIPv4Multicast(InetAddress inetAddress) {
        return (inetAddress.getAddress()[0] & 0xF0) == 224;
    }

    private boolean isIPv6Multicast(InetAddress inetAddress) {
        return (inetAddress.getAddress()[0] & 0xFF) == 0xFF;
    }

    public void startThreads() {
        this.receiveThread = new Thread("Tunnel Receive Thread") {

            @Override
            public void run() {
                // 转发 TUN 消息至 Zerotier
                try {
                    Log.d(TunTapAdapter.TAG, "TUN Receive Thread Started");
                    var buffer = ByteBuffer.allocate(32767);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    while (!isInterrupted()) {
                        try {
                            boolean noDataBeenRead = true;
                            int readCount = TunTapAdapter.this.in.read(buffer.array());
                            if (readCount > 0) {
                                DebugLog.d(TunTapAdapter.TAG, "Sending packet to ZeroTier. " + readCount + " bytes.");
                                var readData = new byte[readCount];
                                System.arraycopy(buffer.array(), 0, readData, 0, readCount);
                                byte iPVersion = IPPacketUtils.getIPVersion(readData);
                                if (iPVersion == 4) {
                                    TunTapAdapter.this.handleIPv4Packet(readData);
                                } else if (iPVersion == 6) {
                                    TunTapAdapter.this.handleIPv6Packet(readData);
                                } else {
                                    Log.e(TunTapAdapter.TAG, "Unknown IP version");
                                }
                                buffer.clear();
                                noDataBeenRead = false;
                            }
                            if (noDataBeenRead) {
                                Thread.sleep(10);
                            }
                        } catch (IOException e) {
                            Log.e(TunTapAdapter.TAG, "Error in TUN Receive: " + e.getMessage(), e);
                        }
                    }
                } catch (InterruptedException ignored) {
                }
                Log.d(TunTapAdapter.TAG, "TUN Receive Thread ended");
                // 关闭所有网络的 ARP、NDP 表
                TunTapAdapter.this.stopTables();
            }
        };
        this.receiveThread.start();
    }

    private void handleIPv4Packet(byte[] packetData) {
        boolean isMulticast;
        long destMac;
        var destIP = IPPacketUtils.getDestIP(packetData);
        var sourceIP = IPPacketUtils.getSourceIP(packetData);

        if (destIP == null || sourceIP == null) {
            Log.e(TAG, "destAddress or sourceAddress is null");
            return;
        }
        long nwid = resolveNetworkId(destIP, sourceIP);
        if (nwid == 0) {
            Log.w(TAG, "No joined network covers destination " + destIP);
            return;
        }
        var virtualNetworkConfig = this.ztService.getVirtualNetworkConfig(nwid);
        if (virtualNetworkConfig == null) {
            Log.e(TAG, "TunTapAdapter has no network config yet");
            return;
        }

        var arpTable = arpTableFor(nwid);
        if (isIPv4Multicast(destIP)) {
            var result = this.node.multicastSubscribe(nwid, multicastAddressToMAC(destIP));
            if (result != ResultCode.RESULT_OK) {
                Log.e(TAG, "Error when calling multicastSubscribe: " + result);
            }
            isMulticast = true;
        } else {
            isMulticast = false;
        }
        var route = routeForDestination(destIP);
        var gateway = route != null ? route.getGateway() : null;

        // 查找当前节点在该网络的 v4 地址
        var ztAddresses = virtualNetworkConfig.getAssignedAddresses();
        InetAddress localV4Address = null;
        int cidr = 0;

        for (var address : ztAddresses) {
            if (address.getAddress() instanceof Inet4Address) {
                localV4Address = address.getAddress();
                cidr = address.getPort();
                break;
            }
        }

        var destRoute = InetAddressUtils.addressToRouteNo0Route(destIP, cidr);
        var sourceRoute = InetAddressUtils.addressToRouteNo0Route(sourceIP, cidr);
        if (gateway != null && !Objects.equals(destRoute, sourceRoute)) {
            destIP = gateway;
        }
        if (localV4Address == null) {
            Log.e(TAG, "Couldn't determine local address");
            return;
        }

        long localMac = virtualNetworkConfig.getMac();
        long[] nextDeadline = new long[1];
        if (isMulticast || arpTable.hasMacForAddress(destIP)) {
            // 已确定目标 MAC，直接发送
            if (isIPv4Multicast(destIP)) {
                destMac = multicastAddressToMAC(destIP);
            } else {
                destMac = arpTable.getMacForAddress(destIP);
            }
            var result = this.node.processVirtualNetworkFrame(System.currentTimeMillis(), nwid, localMac, destMac, IPV4_PACKET, 0, packetData, nextDeadline);
            if (result != ResultCode.RESULT_OK) {
                Log.e(TAG, "Error calling processVirtualNetworkFrame: " + result.toString());
                return;
            }
            Log.d(TAG, "Packet sent to ZT");
            this.ztService.setNextBackgroundTaskDeadline(nextDeadline[0]);
        } else {
            // 目标 MAC 未知，进行 ARP 查询
            Log.d(TAG, "Unknown dest MAC address.  Need to look it up. " + destIP);
            destMac = InetAddressUtils.BROADCAST_MAC_ADDRESS;
            packetData = arpTable.getRequestPacket(localMac, localV4Address, destIP);
            var result = this.node.processVirtualNetworkFrame(System.currentTimeMillis(), nwid, localMac, destMac, ARP_PACKET, 0, packetData, nextDeadline);
            if (result != ResultCode.RESULT_OK) {
                Log.e(TAG, "Error sending ARP packet: " + result.toString());
                return;
            }
            Log.d(TAG, "ARP Request Sent!");
            this.ztService.setNextBackgroundTaskDeadline(nextDeadline[0]);
        }
    }

    private void handleIPv6Packet(byte[] packetData) {
        var destIP = IPPacketUtils.getDestIP(packetData);
        var sourceIP = IPPacketUtils.getSourceIP(packetData);

        if (destIP == null || sourceIP == null) {
            Log.e(TAG, "destAddress or sourceAddress is null");
            return;
        }
        long nwid = resolveNetworkId(destIP, sourceIP);
        if (nwid == 0) {
            Log.w(TAG, "No joined network covers destination " + destIP);
            return;
        }
        var virtualNetworkConfig = this.ztService.getVirtualNetworkConfig(nwid);
        if (virtualNetworkConfig == null) {
            Log.e(TAG, "TunTapAdapter has no network config yet");
            return;
        }

        var ndpTable = ndpTableFor(nwid);
        if (this.isIPv6Multicast(destIP)) {
            var result = this.node.multicastSubscribe(nwid, multicastAddressToMAC(destIP));
            if (result != ResultCode.RESULT_OK) {
                Log.e(TAG, "Error when calling multicastSubscribe: " + result);
            }
        }
        var route = routeForDestination(destIP);
        var gateway = route != null ? route.getGateway() : null;

        // 查找当前节点在该网络的 v6 地址
        var ztAddresses = virtualNetworkConfig.getAssignedAddresses();
        InetAddress localAddress = null;
        int cidr = 0;

        for (var address : ztAddresses) {
            if (address.getAddress() instanceof Inet6Address) {
                localAddress = address.getAddress();
                cidr = address.getPort();
                break;
            }
        }

        var destRoute = InetAddressUtils.addressToRouteNo0Route(destIP, cidr);
        var sourceRoute = InetAddressUtils.addressToRouteNo0Route(sourceIP, cidr);
        if (gateway != null && !Objects.equals(destRoute, sourceRoute)) {
            destIP = gateway;
        }
        if (localAddress == null) {
            Log.e(TAG, "Couldn't determine local address");
            return;
        }

        long localMac = virtualNetworkConfig.getMac();
        long[] nextDeadline = new long[1];

        // 确定目标 MAC 地址
        long destMac;
        boolean sendNSPacket = false;
        if (this.isNeighborSolicitation(packetData)) {
            // 收到本地 NS 报文，根据 NDP 表记录确定是否广播查询
            if (ndpTable.hasMacForAddress(destIP)) {
                destMac = ndpTable.getMacForAddress(destIP);
            } else {
                destMac = InetAddressUtils.ipv6ToMulticastAddress(destIP);
            }
        } else if (this.isIPv6Multicast(destIP)) {
            // 多播报文
            destMac = multicastAddressToMAC(destIP);
        } else if (this.isNeighborAdvertisement(packetData)) {
            // 收到本地 NA 报文
            if (ndpTable.hasMacForAddress(destIP)) {
                destMac = ndpTable.getMacForAddress(destIP);
            } else {
                // 目标 MAC 未知，不发送数据包
                destMac = 0L;
            }
            sendNSPacket = true;
        } else {
            // 收到普通数据包，根据 NDP 表记录确定是否发送 NS 请求
            if (ndpTable.hasMacForAddress(destIP)) {
                // 目标地址 MAC 已知
                destMac = ndpTable.getMacForAddress(destIP);
            } else {
                destMac = 0L;
                sendNSPacket = true;
            }
        }
        // 发送数据包
        if (destMac != 0L) {
            var result = this.node.processVirtualNetworkFrame(System.currentTimeMillis(), nwid, localMac, destMac, IPV6_PACKET, 0, packetData, nextDeadline);
            if (result != ResultCode.RESULT_OK) {
                Log.e(TAG, "Error calling processVirtualNetworkFrame: " + result.toString());
            } else {
                DebugLog.d(TAG, "Packet sent to ZT");
                this.ztService.setNextBackgroundTaskDeadline(nextDeadline[0]);
            }
        }
        // 发送 NS 请求
        if (sendNSPacket) {
            if (destMac == 0L) {
                destMac = InetAddressUtils.ipv6ToMulticastAddress(destIP);
            }
            Log.d(TAG, "Sending Neighbor Solicitation");
            packetData = ndpTable.getNeighborSolicitationPacket(sourceIP, destIP, localMac);
            var result = this.node.processVirtualNetworkFrame(System.currentTimeMillis(), nwid, localMac, destMac, IPV6_PACKET, 0, packetData, nextDeadline);
            if (result != ResultCode.RESULT_OK) {
                Log.e(TAG, "Error calling processVirtualNetworkFrame: " + result.toString());
            } else {
                Log.d(TAG, "Neighbor Solicitation sent to ZT");
                this.ztService.setNextBackgroundTaskDeadline(nextDeadline[0]);
            }
        }
    }

    public void interrupt() {
        if (this.receiveThread != null) {
            try {
                this.in.close();
                this.out.close();
            } catch (IOException e) {
                Log.e(TAG, "Error stopping in/out: " + e.getMessage(), e);
            }
            this.receiveThread.interrupt();
            try {
                this.receiveThread.join();
            } catch (InterruptedException ignored) {
            }
        }
    }

    public void join() throws InterruptedException {
        this.receiveThread.join();
    }

    private boolean isNeighborSolicitation(byte[] packetData) {
        return packetData[6] == 58 && packetData[40] == -121;
    }

    private boolean isNeighborAdvertisement(byte[] packetData) {
        return packetData[6] == 58 && packetData[40] == -120;
    }

    public boolean isRunning() {
        var thread = this.receiveThread;
        if (thread == null) {
            return false;
        }
        return thread.isAlive();
    }

    /**
     * 响应并处理 ZT 网络发送至本节点的以太网帧
     */
    @Override
    public void onVirtualNetworkFrame(long networkId, long srcMac, long destMac, long etherType,
                                      long vlanId, byte[] frameData) {
        DebugLog.d(TAG, "Got Virtual Network Frame. " +
                " Network ID: " + StringUtils.networkIdToString(networkId) +
                " Source MAC: " + StringUtils.macAddressToString(srcMac) +
                " Dest MAC: " + StringUtils.macAddressToString(destMac) +
                " Ether type: " + StringUtils.etherTypeToString(etherType) +
                " VLAN ID: " + vlanId + " Frame Length: " + frameData.length);
        if (this.vpnSocket == null) {
            Log.e(TAG, "vpnSocket is null!");
        } else if (this.in == null || this.out == null) {
            Log.e(TAG, "no in/out streams");
        } else if (etherType == ARP_PACKET) {
            // 收到 ARP 包。更新 ARP 表，若需要则进行应答
            Log.d(TAG, "Got ARP Packet");
            var arpTable = arpTableFor(networkId);
            var arpReply = arpTable.processARPPacket(frameData);
            if (arpReply != null && arpReply.getDestMac() != 0 && arpReply.getDestAddress() != null) {
                // 获取本地 V4 地址
                var networkConfig = this.node.networkConfig(networkId);
                InetAddress localV4Address = null;
                for (var address : networkConfig.getAssignedAddresses()) {
                    if (address.getAddress() instanceof Inet4Address) {
                        localV4Address = address.getAddress();
                        break;
                    }
                }
                // 构造并返回 ARP 应答
                if (localV4Address != null) {
                    var nextDeadline = new long[1];
                    var packetData = arpTable.getReplyPacket(networkConfig.getMac(),
                            localV4Address, arpReply.getDestMac(), arpReply.getDestAddress());
                    var result = this.node
                            .processVirtualNetworkFrame(System.currentTimeMillis(), networkId,
                                    networkConfig.getMac(), srcMac, ARP_PACKET, 0,
                                    packetData, nextDeadline);
                    if (result != ResultCode.RESULT_OK) {
                        Log.e(TAG, "Error sending ARP packet: " + result.toString());
                        return;
                    }
                    Log.d(TAG, "ARP Reply Sent!");
                    this.ztService.setNextBackgroundTaskDeadline(nextDeadline[0]);
                }
            }
        } else if (etherType == IPV4_PACKET) {
            // 收到 IPv4 包。根据需要发送至 TUN
            DebugLog.d(TAG, "Got IPv4 packet. Length: " + frameData.length + " Bytes");
            try {
                var sourceIP = IPPacketUtils.getSourceIP(frameData);
                if (sourceIP != null) {
                    if (isIPv4Multicast(sourceIP)) {
                        var result = this.node.multicastSubscribe(networkId, multicastAddressToMAC(sourceIP));
                        if (result != ResultCode.RESULT_OK) {
                            Log.e(TAG, "Error when calling multicastSubscribe: " + result);
                        }
                    } else {
                        arpTableFor(networkId).setAddress(sourceIP, srcMac);
                    }
                }
                this.out.write(frameData);
            } catch (Exception e) {
                Log.e(TAG, "Error writing data to vpn socket: " + e.getMessage(), e);
            }
        } else if (etherType == IPV6_PACKET) {
            // 收到 IPv6 包。根据需要发送至 TUN，并更新 NDP 表
            DebugLog.d(TAG, "Got IPv6 packet. Length: " + frameData.length + " Bytes");
            try {
                var sourceIP = IPPacketUtils.getSourceIP(frameData);
                if (sourceIP != null) {
                    if (isIPv6Multicast(sourceIP)) {
                        var result = this.node.multicastSubscribe(networkId, multicastAddressToMAC(sourceIP));
                        if (result != ResultCode.RESULT_OK) {
                            Log.e(TAG, "Error when calling multicastSubscribe: " + result);
                        }
                    } else {
                        ndpTableFor(networkId).setAddress(sourceIP, srcMac);
                    }
                }
                this.out.write(frameData);
            } catch (Exception e) {
                Log.e(TAG, "Error writing data to vpn socket: " + e.getMessage(), e);
            }
        } else if (frameData.length >= 14) {
            Log.d(TAG, "Unknown Packet Type Received: 0x" + String.format("%02X%02X", frameData[12], frameData[13]));
        } else {
            Log.d(TAG, "Unknown Packet Received.  Packet Length: " + frameData.length);
        }
    }

    private Route routeForDestination(InetAddress destAddress) {
        synchronized (this.routeMap) {
            for (var route : this.routeMap.keySet()) {
                if (route.belongsToRoute(destAddress)) {
                    return route;
                }
            }
            return null;
        }
    }

    private long networkIdForDestination(InetAddress destAddress) {
        synchronized (this.routeMap) {
            for (Route route : this.routeMap.keySet()) {
                if (route.belongsToRoute(destAddress)) {
                    return this.routeMap.get(route);
                }
            }
            return 0;
        }
    }
}
