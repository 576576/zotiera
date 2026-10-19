package net.sumitm.zotiera.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.zerotier.sdk.Event;
import com.zerotier.sdk.EventListener;
import com.zerotier.sdk.Node;
import com.zerotier.sdk.Peer;
import com.zerotier.sdk.ResultCode;
import com.zerotier.sdk.VirtualNetworkConfig;
import com.zerotier.sdk.VirtualNetworkConfigListener;
import com.zerotier.sdk.VirtualNetworkConfigOperation;
import com.zerotier.sdk.VirtualNetworkStatus;

import net.sumitm.zotiera.R;
import net.sumitm.zotiera.ZotieraApplication;
import net.sumitm.zotiera.events.AfterJoinNetworkEvent;
import net.sumitm.zotiera.events.ErrorEvent;
import net.sumitm.zotiera.events.IsServiceRunningReplyEvent;
import net.sumitm.zotiera.events.LeaveNetworkEvent;
import net.sumitm.zotiera.events.TunnelConfigChangedEvent;
import net.sumitm.zotiera.events.IsServiceRunningRequestEvent;
import net.sumitm.zotiera.events.ManualDisconnectEvent;
import net.sumitm.zotiera.events.NetworkConfigChangedByUserEvent;
import net.sumitm.zotiera.events.NetworkListReplyEvent;
import net.sumitm.zotiera.events.NetworkListRequestEvent;
import net.sumitm.zotiera.events.NetworkReconfigureEvent;
import net.sumitm.zotiera.events.NodeDestroyedEvent;
import net.sumitm.zotiera.events.NodeIDEvent;
import net.sumitm.zotiera.events.NodeStatusEvent;
import net.sumitm.zotiera.events.NodeStatusRequestEvent;
import net.sumitm.zotiera.events.OrbitMoonEvent;
import net.sumitm.zotiera.events.PeerInfoReplyEvent;
import net.sumitm.zotiera.events.PeerInfoRequestEvent;
import net.sumitm.zotiera.events.StopEvent;
import net.sumitm.zotiera.events.VPNErrorEvent;
import net.sumitm.zotiera.events.VirtualNetworkConfigChangedEvent;
import net.sumitm.zotiera.events.VirtualNetworkConfigReplyEvent;
import net.sumitm.zotiera.events.VirtualNetworkConfigRequestEvent;
import net.sumitm.zotiera.model.AppNode;
import net.sumitm.zotiera.model.MoonOrbit;
import net.sumitm.zotiera.model.Network;
import net.sumitm.zotiera.model.type.DNSMode;
import net.sumitm.zotiera.ui.MainActivity;
import net.sumitm.zotiera.util.Constants;
import net.sumitm.zotiera.util.DatabaseUtils;
import net.sumitm.zotiera.util.InetAddressUtils;
import net.sumitm.zotiera.util.NetworkInfoUtils;
import net.sumitm.zotiera.util.StringUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

// TODO: clear up
public class ZeroTierOneService extends VpnService implements Runnable, EventListener, VirtualNetworkConfigListener {
    public static final int MSG_JOIN_NETWORK = 1;
    public static final int MSG_LEAVE_NETWORK = 2;
    public static final String ZT1_NETWORK_ID = "com.zerotier.one.network_id";
    public static final String ZT1_USE_DEFAULT_ROUTE = "com.zerotier.one.use_default_route";
    private static final String[] DISALLOWED_APPS = {"com.android.vending"};
    private static final String TAG = "ZT1_Service";
    private static final int ZT_NOTIFICATION_TAG = 5919812;
    /** 服务实例是否在跑。通知栏磁贴读它取开关状态。 */
    public static volatile boolean isRunning = false;
    private final IBinder mBinder = new ZeroTierBinder();
    private final DataStore dataStore = new DataStore(this);
    private final EventBus eventBus = EventBus.getDefault();
    private final Map<Long, VirtualNetworkConfig> virtualNetworkConfigMap = new HashMap();
    FileInputStream in;
    FileOutputStream out;
    DatagramSocket svrSocket;
    ParcelFileDescriptor vpnSocket;
    private int bindCount = 0;
    private boolean disableIPv6 = false;
    private int mStartID = -1;
    private final java.util.Set<Long> joinedNetworkIds =
            java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());
    private String tunnelFingerprint = "";
    private boolean routeAllTraffic = false;
    private long nextBackgroundTaskDeadline = 0;
    private Node node;
    private NotificationManager notificationManager;
    private TunTapAdapter tunTapAdapter;
    private UdpCom udpCom;
    private Thread udpThread;
    private Thread v4MulticastScanner = new Thread() {
        /* class com.zerotier.one.service.ZeroTierOneService.AnonymousClass1 */
        List<String> subscriptions = new ArrayList<>();

        @Override
        public void run() {
            Log.d(ZeroTierOneService.TAG, "IPv4 Multicast Scanner Thread Started.");
            while (!isInterrupted()) {
                try {
                    List<String> groups = NetworkInfoUtils.listMulticastGroupOnInterface("tun0", false);

                    ArrayList<String> arrayList2 = new ArrayList<>(this.subscriptions);
                    ArrayList<String> arrayList3 = new ArrayList<>(groups);
                    arrayList3.removeAll(arrayList2);
                    for (String str : arrayList3) {
                        try {
                            byte[] hexStringToByteArray = StringUtils.hexStringToBytes(str);
                            for (int i = 0; i < hexStringToByteArray.length / 2; i++) {
                                byte b = hexStringToByteArray[i];
                                hexStringToByteArray[i] = hexStringToByteArray[(hexStringToByteArray.length - i) - 1];
                                hexStringToByteArray[(hexStringToByteArray.length - i) - 1] = b;
                            }
                            ResultCode multicastSubscribe = ResultCode.RESULT_OK;
                            for (long nwid : ZeroTierOneService.this.getJoinedNetworkIds()) {
                                multicastSubscribe = ZeroTierOneService.this.node.multicastSubscribe(nwid, TunTapAdapter.multicastAddressToMAC(InetAddress.getByAddress(hexStringToByteArray)));
                            }
                            if (multicastSubscribe != ResultCode.RESULT_OK) {
                                Log.e(ZeroTierOneService.TAG, "Error when calling multicastSubscribe: " + multicastSubscribe);
                            }
                        } catch (Exception e) {
                            Log.e(ZeroTierOneService.TAG, e.toString(), e);
                        }
                    }
                    arrayList2.removeAll(new ArrayList<>(groups));
                    for (String str2 : arrayList2) {
                        try {
                            byte[] hexStringToByteArray2 = StringUtils.hexStringToBytes(str2);
                            for (int i2 = 0; i2 < hexStringToByteArray2.length / 2; i2++) {
                                byte b2 = hexStringToByteArray2[i2];
                                hexStringToByteArray2[i2] = hexStringToByteArray2[(hexStringToByteArray2.length - i2) - 1];
                                hexStringToByteArray2[(hexStringToByteArray2.length - i2) - 1] = b2;
                            }
                            ResultCode multicastUnsubscribe = ResultCode.RESULT_OK;
                            for (long nwid : ZeroTierOneService.this.getJoinedNetworkIds()) {
                                multicastUnsubscribe = ZeroTierOneService.this.node.multicastUnsubscribe(nwid, TunTapAdapter.multicastAddressToMAC(InetAddress.getByAddress(hexStringToByteArray2)));
                            }
                            if (multicastUnsubscribe != ResultCode.RESULT_OK) {
                                Log.e(ZeroTierOneService.TAG, "Error when calling multicastUnsubscribe: " + multicastUnsubscribe);
                            }
                        } catch (Exception e) {
                            Log.e(ZeroTierOneService.TAG, e.toString(), e);
                        }
                    }
                    this.subscriptions = groups;
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.d(ZeroTierOneService.TAG, "V4 Multicast Scanner Thread Interrupted", e);
                    break;
                }
            }
            Log.d(ZeroTierOneService.TAG, "IPv4 Multicast Scanner Thread Ended.");
        }
    };
    private Thread v6MulticastScanner = new Thread() {
        /* class com.zerotier.one.service.ZeroTierOneService.AnonymousClass2 */
        List<String> subscriptions = new ArrayList<>();

        @Override
        public void run() {
            Log.d(ZeroTierOneService.TAG, "IPv6 Multicast Scanner Thread Started.");
            while (!isInterrupted()) {
                try {
                    List<String> groups = NetworkInfoUtils.listMulticastGroupOnInterface("tun0", true);

                    ArrayList<String> arrayList2 = new ArrayList<>(this.subscriptions);
                    ArrayList<String> arrayList3 = new ArrayList<>(groups);
                    arrayList3.removeAll(arrayList2);
                    for (String str : arrayList3) {
                        try {
                            ResultCode multicastSubscribe = ResultCode.RESULT_OK;
                            for (long nwid : ZeroTierOneService.this.getJoinedNetworkIds()) {
                                multicastSubscribe = ZeroTierOneService.this.node.multicastSubscribe(nwid, TunTapAdapter.multicastAddressToMAC(InetAddress.getByAddress(StringUtils.hexStringToBytes(str))));
                            }
                            if (multicastSubscribe != ResultCode.RESULT_OK) {
                                Log.e(ZeroTierOneService.TAG, "Error when calling multicastSubscribe: " + multicastSubscribe);
                            }
                        } catch (Exception e) {
                            Log.e(ZeroTierOneService.TAG, e.toString(), e);
                        }
                    }
                    arrayList2.removeAll(new ArrayList<>(groups));
                    for (String str2 : arrayList2) {
                        try {
                            ResultCode multicastUnsubscribe = ResultCode.RESULT_OK;
                            for (long nwid : ZeroTierOneService.this.getJoinedNetworkIds()) {
                                multicastUnsubscribe = ZeroTierOneService.this.node.multicastUnsubscribe(nwid, TunTapAdapter.multicastAddressToMAC(InetAddress.getByAddress(StringUtils.hexStringToBytes(str2))));
                            }
                            if (multicastUnsubscribe != ResultCode.RESULT_OK) {
                                Log.e(ZeroTierOneService.TAG, "Error when calling multicastUnsubscribe: " + multicastUnsubscribe);
                            }
                        } catch (Exception e) {
                            Log.e(ZeroTierOneService.TAG, e.toString(), e);
                        }
                    }
                    this.subscriptions = groups;
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.d(ZeroTierOneService.TAG, "V6 Multicast Scanner Thread Interrupted", e);
                    break;
                }
            }
            Log.d(ZeroTierOneService.TAG, "IPv6 Multicast Scanner Thread Ended.");
        }
    };
    private Thread vpnThread;

    public VirtualNetworkConfig getVirtualNetworkConfig(long j) {
        VirtualNetworkConfig virtualNetworkConfig;
        synchronized (this.virtualNetworkConfigMap) {
            virtualNetworkConfig = this.virtualNetworkConfigMap.get(Long.valueOf(j));
        }
        return virtualNetworkConfig;
    }

    public VirtualNetworkConfig setVirtualNetworkConfig(long j, VirtualNetworkConfig virtualNetworkConfig) {
        VirtualNetworkConfig put;
        synchronized (this.virtualNetworkConfigMap) {
            put = this.virtualNetworkConfigMap.put(Long.valueOf(j), virtualNetworkConfig);
        }
        return put;
    }

    public VirtualNetworkConfig clearVirtualNetworkConfig(long j) {
        VirtualNetworkConfig remove;
        synchronized (this.virtualNetworkConfigMap) {
            remove = this.virtualNetworkConfigMap.remove(Long.valueOf(j));
        }
        return remove;
    }

    private void logBindCount() {
        Log.i(TAG, "Bind Count: " + this.bindCount);
    }

    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Bound by: " + getPackageManager().getNameForUid(Binder.getCallingUid()));
        this.bindCount++;
        logBindCount();
        return this.mBinder;
    }

    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Unbound by: " + getPackageManager().getNameForUid(Binder.getCallingUid()));
        this.bindCount--;
        logBindCount();
        return false;
    }

    /* access modifiers changed from: protected */
    public void setNextBackgroundTaskDeadline(long j) {
        synchronized (this) {
            this.nextBackgroundTaskDeadline = j;
        }
    }

    /**
     * 启动 ZT 服务，连接至给定网络或最近连接的网络
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        if (startId == 3) {
            Log.i(TAG, "Authorizing VPN");
            return START_NOT_STICKY;
        } else if (intent == null) {
            Log.e(TAG, "NULL intent.  Cannot start");
            return START_NOT_STICKY;
        }
        this.mStartID = startId;
        isRunning = true;
        ZeroTierTileService.refreshTile(this);

        // 注册事件总线监听器
        if (!this.eventBus.isRegistered(this)) {
            this.eventBus.register(this);
        }

        // 本次要加入的网络 = intent 指定的那个 + 数据库里所有「已加入」的
        var requested = new java.util.LinkedHashSet<Long>();
        if (intent.hasExtra(ZT1_NETWORK_ID)) {
            long requestedId = intent.getLongExtra(ZT1_NETWORK_ID, 0);
            if (requestedId != 0) {
                requested.add(requestedId);
            }
        }
        DatabaseUtils.readLock.lock();
        try {
            var repository = ((ZotieraApplication) getApplication()).getRepository();
            for (Network network : repository.lastActivatedNetworks()) {
                requested.add(network.getNetworkId());
            }
        } finally {
            DatabaseUtils.readLock.unlock();
        }
        if (requested.isEmpty()) {
            Log.e(TAG, "No network to join");
            abortStart();
            return START_NOT_STICKY;
        }
        this.joinedNetworkIds.addAll(requested);

        // 检查当前的网络环境
        var preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean useCellularData = preferences.getBoolean(Constants.PREF_NETWORK_USE_CELLULAR_DATA, false);
        this.disableIPv6 = preferences.getBoolean(Constants.PREF_NETWORK_DISABLE_IPV6, false);
        this.routeAllTraffic = preferences.getBoolean(Constants.PREF_ROUTE_ALL_TRAFFIC, false);
        var currentNetworkInfo = NetworkInfoUtils.getNetworkInfoCurrentConnection(this);

        if (currentNetworkInfo == NetworkInfoUtils.CurrentConnection.CONNECTION_NONE) {
            // 未连接网络
            Toast.makeText(this, R.string.toast_no_network, Toast.LENGTH_SHORT).show();
            abortStart();
            return START_NOT_STICKY;
        } else if (currentNetworkInfo == NetworkInfoUtils.CurrentConnection.CONNECTION_MOBILE &&
                !useCellularData) {
            // 使用移动网络，但未在设置中允许移动网络访问
            Toast.makeText(this, R.string.toast_mobile_data, Toast.LENGTH_LONG).show();
            abortStart();
            return START_NOT_STICKY;
        }

        // 启动 ZT 服务
        synchronized (this) {
            try {
                // 创建本地 ZT 服务 Socket，监听本地端口
                if (this.svrSocket == null) {
                    this.svrSocket = new DatagramSocket(null);
                    this.svrSocket.setReuseAddress(true);
                    this.svrSocket.setSoTimeout(1000);
                    this.svrSocket.bind(new InetSocketAddress(9994));
                }
                if (!protect(this.svrSocket)) {
                    Log.e(TAG, "Error protecting UDP socket from feedback loop.");
                }

                // 创建本地节点
                if (this.node == null) {
                    this.udpCom = new UdpCom(this, this.svrSocket);
                    this.tunTapAdapter = new TunTapAdapter(this);

                    // 创建节点对象并初始化
                    var dataStore = this.dataStore;
                    this.node = new Node(System.currentTimeMillis());
                    var result = this.node.init(dataStore, dataStore, this.udpCom, this, this.tunTapAdapter, this, null);

                    if (result == ResultCode.RESULT_OK) {
                        Log.d(TAG, "ZeroTierOne Node Initialized");
                    } else {
                        Log.e(TAG, "Error starting ZT1 Node: " + result);
                        abortStart();
                        return START_NOT_STICKY;
                    }
                    this.onNodeStatusRequest(null);

                    // 持久化当前节点信息
                    long address = this.node.address();
                    DatabaseUtils.writeLock.lock();
                    try {
                        var repository = ((ZotieraApplication) getApplication()).getRepository();
                        var nodesList = repository.appNodes();
                        if (nodesList.isEmpty()) {
                            var appNode = new AppNode();
                            appNode.setNodeId(address);
                            appNode.setNodeIdStr(String.format("%10x", address));
                            repository.insertAppNode(appNode);
                        } else {
                            var appNode = nodesList.get(0);
                            appNode.setNodeId(address);
                            appNode.setNodeIdStr(String.format("%10x", address));
                            repository.updateAppNode(appNode);
                        }
                    } finally {
                        DatabaseUtils.writeLock.unlock();
                    }

                    this.eventBus.post(new NodeIDEvent(address));
                    this.udpCom.setNode(this.node);
                    this.tunTapAdapter.setNode(this.node);

                    // 启动 UDP 消息处理线程
                    var thread = new Thread(this.udpCom, "UDP Communication Thread");
                    this.udpThread = thread;
                    thread.start();
                }

                // 创建并启动 VPN 服务线程
                if (this.vpnThread == null) {
                    var thread = new Thread(this, "ZeroTier Service Thread");
                    this.vpnThread = thread;
                    thread.start();
                }

                // 启动 UDP 消息处理线程
                if (!this.udpThread.isAlive()) {
                    this.udpThread.start();
                }
            } catch (Exception e) {
                Log.e(TAG, e.toString(), e);
                abortStart();
                return START_NOT_STICKY;
            }
        }
        for (long id : requested) {
            joinNetwork(id);
        }
        return START_STICKY;
    }

    /**
     * 启动条件不满足：把开关与磁贴退回未连接，再停掉自己。
     *
     * 界面绑着服务时 stopSelf 不会走 onDestroy，不主动通知的话开关会一直停在「正在连接」。
     */
    private void abortStart() {
        isRunning = false;
        ZeroTierTileService.refreshTile(this);
        this.eventBus.post(new IsServiceRunningReplyEvent(false));
        stopSelf(this.mStartID);
    }

    public void stopZeroTier() {
        isRunning = false;
        ZeroTierTileService.refreshTile(this);
        this.joinedNetworkIds.clear();
        this.tunnelFingerprint = "";
        if (this.svrSocket != null) {
            this.svrSocket.close();
            this.svrSocket = null;
        }
        if (this.udpThread != null && this.udpThread.isAlive()) {
            this.udpThread.interrupt();
            try {
                this.udpThread.join();
            } catch (InterruptedException ignored) {
            }
            this.udpThread = null;
        }
        if (this.tunTapAdapter != null && this.tunTapAdapter.isRunning()) {
            this.tunTapAdapter.interrupt();
            try {
                this.tunTapAdapter.join();
            } catch (InterruptedException ignored) {
            }
            this.tunTapAdapter = null;
        }
        if (this.vpnThread != null && this.vpnThread.isAlive()) {
            this.vpnThread.interrupt();
            try {
                this.vpnThread.join();
            } catch (InterruptedException ignored) {
            }
            this.vpnThread = null;
        }
        if (this.v4MulticastScanner != null) {
            this.v4MulticastScanner.interrupt();
            try {
                this.v4MulticastScanner.join();
            } catch (InterruptedException ignored) {
            }
            this.v4MulticastScanner = null;
        }
        if (this.v6MulticastScanner != null) {
            this.v6MulticastScanner.interrupt();
            try {
                this.v6MulticastScanner.join();
            } catch (InterruptedException ignored) {
            }
            this.v6MulticastScanner = null;
        }
        if (this.vpnSocket != null) {
            try {
                this.vpnSocket.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing VPN socket: " + e, e);
            }
            this.vpnSocket = null;
        }
        if (this.node != null) {
            this.eventBus.post(new NodeDestroyedEvent());
            this.node.close();
            this.node = null;
        }
        if (this.eventBus.isRegistered(this)) {
            this.eventBus.unregister(this);
        }
        if (this.notificationManager != null) {
            this.notificationManager.cancel(ZT_NOTIFICATION_TAG);
        }
        if (!stopSelfResult(this.mStartID)) {
            Log.e(TAG, "stopSelfResult() failed!");
        }
    }

    public void onDestroy() {
        try {
            isRunning = false;
            // 服务提前退出（无网络、未允许移动数据、初始化失败）时没有 NodeDestroyedEvent，
            // 界面会一直停在「正在连接」；这里补一条状态
            this.eventBus.post(new IsServiceRunningReplyEvent(false));
            stopZeroTier();
            if (this.vpnSocket != null) {
                try {
                    this.vpnSocket.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing VPN socket: " + e, e);
                }
                this.vpnSocket = null;
            }
            stopSelf(this.mStartID);
            if (this.eventBus.isRegistered(this)) {
                this.eventBus.unregister(this);
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString(), e);
        } finally {
            super.onDestroy();
        }
    }

    public void onRevoke() {
        stopZeroTier();
        if (this.vpnSocket != null) {
            try {
                this.vpnSocket.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing VPN socket: " + e, e);
            }
            this.vpnSocket = null;
        }
        stopSelf(this.mStartID);
        if (this.eventBus.isRegistered(this)) {
            this.eventBus.unregister(this);
        }
        super.onRevoke();
    }

    public void run() {
        Log.d(TAG, "ZeroTierOne Service Started");
        Log.d(TAG, "This Node Address: " + com.zerotier.sdk.util.StringUtils.addressToString(this.node.address()));
        while (!Thread.interrupted()) {
            try {
                // 在后台任务截止期前循环进行后台任务
                var taskDeadline = this.nextBackgroundTaskDeadline;
                long currentTime = System.currentTimeMillis();
                int cmp = Long.compare(taskDeadline, currentTime);
                if (cmp <= 0) {
                    long[] newDeadline = {0};
                    var taskResult = this.node.processBackgroundTasks(currentTime, newDeadline);
                    synchronized (this) {
                        this.nextBackgroundTaskDeadline = newDeadline[0];
                    }
                    if (taskResult != ResultCode.RESULT_OK) {
                        Log.e(TAG, "Error on processBackgroundTasks: " + taskResult.toString());
                        shutdown();
                    }
                }
                Thread.sleep(cmp > 0 ? taskDeadline - currentTime : 100);
            } catch (InterruptedException ignored) {
                break;
            } catch (Exception e) {
                Log.e(TAG, e.toString(), e);
            }
        }
        Log.d(TAG, "ZeroTierOne Service Ended");
    }

    @Subscribe(threadMode = ThreadMode.POSTING)
    public void onStopEvent(StopEvent stopEvent) {
        stopZeroTier();
    }

    @Subscribe(threadMode = ThreadMode.POSTING)
    public void onManualDisconnect(ManualDisconnectEvent manualDisconnectEvent) {
        stopZeroTier();
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onLeaveNetwork(LeaveNetworkEvent event) {
        leaveNetwork(event.getNetworkId());
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onTunnelConfigChanged(TunnelConfigChangedEvent event) {
        this.routeAllTraffic = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(Constants.PREF_ROUTE_ALL_TRAFFIC, false);
        rebuildTunnel();
    }

    @Subscribe(threadMode = ThreadMode.POSTING)
    public void onIsServiceRunningRequest(IsServiceRunningRequestEvent event) {
        // 服务实例还在（被界面绑着）不代表隧道在跑，按实际状态回
        this.eventBus.post(new IsServiceRunningReplyEvent(isRunning));
    }

    /**
     * 加入 ZT 网络
     */
    public void joinNetwork(long networkId) {
        if (this.node == null) {
            Log.e(TAG, "Can't join network if ZeroTier isn't running");
            return;
        }
        // 连接到新网络
        var result = this.node.join(networkId);
        if (result != ResultCode.RESULT_OK) {
            this.eventBus.post(new ErrorEvent(result));
            return;
        }
        this.joinedNetworkIds.add(networkId);
        Log.i(TAG, "Joined network " + Long.toHexString(networkId)
                + " (total " + this.joinedNetworkIds.size() + ")");
        // 连接后事件
        this.eventBus.post(new AfterJoinNetworkEvent());
    }

    /**
     * 离开 ZT 网络
     */
    public void leaveNetwork(long networkId) {
        if (this.node == null) {
            Log.e(TAG, "Can't leave network if ZeroTier isn't running");
            return;
        }
        var result = this.node.leave(networkId);
        if (result != ResultCode.RESULT_OK) {
            this.eventBus.post(new ErrorEvent(result));
            return;
        }
        this.joinedNetworkIds.remove(networkId);
        clearVirtualNetworkConfig(networkId);
        // 只剩这一个网络时把隧道也拆掉；否则按剩余网络重建
        if (this.joinedNetworkIds.isEmpty()) {
            stopZeroTier();
        } else {
            rebuildTunnel();
            return;
        }
        if (this.vpnSocket != null) {
            try {
                this.vpnSocket.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing VPN socket", e);
            }
            this.vpnSocket = null;
        }
        stopSelf(this.mStartID);
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onNetworkListRequest(NetworkListRequestEvent requestNetworkListEvent) {
        VirtualNetworkConfig[] networks;
        Node node2 = this.node;
        if (node2 != null && (networks = node2.networkConfigs()) != null && networks.length > 0) {
            this.eventBus.post(new NetworkListReplyEvent(networks));
        }
    }

    /**
     * 请求节点状态事件回调
     *
     * @param event 事件
     */
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onNodeStatusRequest(NodeStatusRequestEvent event) {
        // 返回节点状态
        if (this.node != null) {
            this.eventBus.post(new NodeStatusEvent(this.node.status(), this.node.getVersion()));
        }
    }

    /**
     * 请求 Peer 信息事件回调
     */
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onRequestPeerInfo(PeerInfoRequestEvent event) {
        if (this.node == null) {
            // PeerInfoReplyEvent 的 peers 是非空数组，传 null 会在构造器里抛 NPE
            this.eventBus.post(new PeerInfoReplyEvent(new Peer[0]));
            return;
        }
        this.eventBus.post(new PeerInfoReplyEvent(this.node.peers()));
    }

    /**
     * 请求网络配置事件回调
     */
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onVirtualNetworkConfigRequest(VirtualNetworkConfigRequestEvent event) {
        if (this.node == null) {
            this.eventBus.post(new VirtualNetworkConfigReplyEvent(null));
            return;
        }
        var config = this.node.networkConfig(event.getNetworkId());
        this.eventBus.post(new VirtualNetworkConfigReplyEvent(config));
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onNetworkReconfigure(NetworkReconfigureEvent event) {
        boolean isChanged = event.isChanged();
        var network = event.getNetwork();
        var networkConfig = event.getVirtualNetworkConfig();
        boolean configUpdated = isChanged && rebuildTunnel();
        boolean networkIsOk = networkConfig.getStatus() == VirtualNetworkStatus.NETWORK_STATUS_OK;

        if (configUpdated || !networkIsOk) {
            this.eventBus.post(new VirtualNetworkConfigChangedEvent(networkConfig));
        }
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onNetworkConfigChangedByUser(NetworkConfigChangedByUserEvent event) {
        // 全局全隧道开关或某个网络的 DNS 模式变化：按当前网络集合重建
        this.routeAllTraffic = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(Constants.PREF_ROUTE_ALL_TRAFFIC, false);
        rebuildTunnel();
    }

    /**
     * Zerotier 事件回调
     *
     * @param event {@link Event} enum
     */
    @Override
    public void onEvent(Event event) {
        Log.d(TAG, "Event: " + event.toString());
        // 更新节点状态
        if (this.node.isInited()) {
            this.eventBus.post(new NodeStatusEvent(this.node.status(), this.node.getVersion()));
        }
    }

    @Override // com.zerotier.sdk.EventListener
    public void onTrace(String str) {
        Log.d(TAG, "Trace: " + str);
    }

    /**
     * 当 ZT 网络配置发生更新
     */
    @Override
    public int onNetworkConfigurationUpdated(long networkId, VirtualNetworkConfigOperation op, VirtualNetworkConfig config) {
        Log.i(TAG, "Virtual Network Config Operation: " + op);
        DatabaseUtils.writeLock.lock();
        try {
            // 查找网络 ID 对应的配置
            var repository = ((ZotieraApplication) getApplication()).getRepository();
            var network = repository.network(networkId);
            if (network == null) {
                throw new IllegalStateException("Database is inconsistent");
            }
            // 根据当前网络状态确定更改配置的行为
            switch (op) {
                case VIRTUAL_NETWORK_CONFIG_OPERATION_UP:
                    Log.d(TAG, "Network Type: " + config.getType() + " Network Status: " + config.getStatus() + " Network Name: " + config.getName() + " ");
                    // 将网络配置的更新交给第一次 Update
                    break;
                case VIRTUAL_NETWORK_CONFIG_OPERATION_CONFIG_UPDATE:
                    Log.i(TAG, "Network Config Update!");
                    boolean isChanged = setVirtualNetworkConfigAndUpdateDatabase(network, config);
                    this.eventBus.post(new NetworkReconfigureEvent(isChanged, network, config));
                    break;
                case VIRTUAL_NETWORK_CONFIG_OPERATION_DOWN:
                case VIRTUAL_NETWORK_CONFIG_OPERATION_DESTROY:
                    Log.d(TAG, "Network Down!");
                    clearVirtualNetworkConfig(networkId);
                    rebuildTunnel();
                    break;
            }
            return 0;
        } finally {
            DatabaseUtils.writeLock.unlock();
        }
    }

    private boolean setVirtualNetworkConfigAndUpdateDatabase(Network network, VirtualNetworkConfig virtualNetworkConfig) {
        if ((DatabaseUtils.writeLock instanceof ReentrantReadWriteLock.WriteLock) && !((ReentrantReadWriteLock.WriteLock) DatabaseUtils.writeLock).isHeldByCurrentThread()) {
            throw new IllegalStateException("DatabaseUtils.writeLock not held");
        }
        VirtualNetworkConfig virtualNetworkConfig2 = getVirtualNetworkConfig(network.getNetworkId());
        setVirtualNetworkConfig(network.getNetworkId(), virtualNetworkConfig);
        var networkName = virtualNetworkConfig.getName();
        if (networkName != null && !networkName.isEmpty()) {
            network.setNetworkName(networkName);
        }
        ((ZotieraApplication) getApplication()).getRepository().updateNetwork(network);
        return !virtualNetworkConfig.equals(virtualNetworkConfig2);
    }

    protected void shutdown() {
        stopZeroTier();
        if (this.vpnSocket != null) {
            try {
                this.vpnSocket.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing VPN socket", e);
            }
            this.vpnSocket = null;
        }
        stopSelf(this.mStartID);
    }

    /**
     * 已加入的网络 id 快照。适配器用它决定组播订阅与地址归属。
     */
    public long[] getJoinedNetworkIds() {
        synchronized (this.joinedNetworkIds) {
            long[] ids = new long[this.joinedNetworkIds.size()];
            int i = 0;
            for (long id : this.joinedNetworkIds) {
                ids[i++] = id;
            }
            return ids;
        }
    }

    /** 拆掉当前隧道：TUN 线程、VPN socket 与路由表。 */
    private void teardownTunnel() {
        if (this.tunTapAdapter != null && this.tunTapAdapter.isRunning()) {
            this.tunTapAdapter.interrupt();
            try {
                this.tunTapAdapter.join();
            } catch (InterruptedException ignored) {
            }
        }
        if (this.tunTapAdapter != null) {
            this.tunTapAdapter.clearRouteMap();
        }
        if (this.vpnSocket != null) {
            try {
                this.vpnSocket.close();
                if (this.in != null) {
                    this.in.close();
                }
                if (this.out != null) {
                    this.out.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error closing VPN socket: " + e, e);
            }
            this.vpnSocket = null;
            this.in = null;
            this.out = null;
        }
    }

    /**
     * 配置指纹：地址、路由、DNS、MTU 任何一个变化才重建隧道。
     *
     * 不做这层判断的话，每次 CONFIG_UPDATE 都会拆一次隧道，连接会周期抖动。
     */
    private String tunnelFingerprint(java.util.Map<Long, VirtualNetworkConfig> configs) {
        var builder = new StringBuilder();
        for (long id : getJoinedNetworkIds()) {
            builder.append(id).append(';');
        }
        builder.append("routeAll=").append(this.routeAllTraffic)
                .append(" disableV6=").append(this.disableIPv6);
        try {
            var repository = ((ZotieraApplication) getApplication()).getRepository();
            for (var entry : configs.entrySet()) {
                var dbNetwork = repository.network(entry.getKey());
                builder.append(entry.getKey()).append('#')
                        .append(dbNetwork == null ? "?" : dbNetwork.getNetworkConfig().getDnsMode())
                        .append('=')
                        .append(entry.getValue().toString())
                        .append('|');
            }
        } catch (Exception e) {
            Log.e(TAG, "Cannot compute tunnel fingerprint", e);
        }
        return Integer.toHexString(builder.toString().hashCode()) + ":" + builder.length();
    }

    /**
     * 按所有已加入网络重建隧道。
     *
     * 之前的实现每次只处理一个网络，加第二个网络会把第一个的地址和路由冲掉。
     */
    private boolean rebuildTunnel() {
        if (this.node == null || this.tunTapAdapter == null) {
            return false;
        }
        var repository = ((ZotieraApplication) getApplication()).getRepository();

        // 已加入且已经拿到配置的网络
        var configs = new java.util.LinkedHashMap<Long, VirtualNetworkConfig>();
        for (long id : getJoinedNetworkIds()) {
            var config = getVirtualNetworkConfig(id);
            if (config != null) {
                configs.put(id, config);
            }
        }

        boolean hasAddress = false;
        for (var config : configs.values()) {
            if (config.getAssignedAddresses().length > 0) {
                hasAddress = true;
                break;
            }
        }
        if (!hasAddress) {
            // 全部网络都还没分配地址（未授权或还在配置中），先不建隧道
            if (this.vpnSocket != null) {
                teardownTunnel();
                this.tunnelFingerprint = "";
            }
            Log.w(TAG, "No assigned address yet, skip VPN setup");
            return false;
        }

        String fingerprint = tunnelFingerprint(configs);
        if (fingerprint.equals(this.tunnelFingerprint) && this.vpnSocket != null) {
            return false;
        }

        teardownTunnel();

        Log.i(TAG, "Configuring VpnService.Builder for " + configs.size() + " network(s)");
        var builder = new VpnService.Builder();
        int mtu = Integer.MAX_VALUE;

        try {
            var v4Loopback = InetAddress.getByName("0.0.0.0");
            var v6Loopback = InetAddress.getByName("::");

            for (var entry : configs.entrySet()) {
                long nwid = entry.getKey();
                var virtualNetworkConfig = entry.getValue();
                var dbNetwork = repository.network(nwid);

                // 1) 该网络分配给本机的地址
                for (var vpnAddress : virtualNetworkConfig.getAssignedAddresses()) {
                    var address = vpnAddress.getAddress();
                    int prefix = vpnAddress.getPort();
                    if (this.disableIPv6 && address instanceof Inet6Address) {
                        continue;
                    }
                    var route = InetAddressUtils.addressToRoute(address, prefix);
                    if (route == null) {
                        Log.e(TAG, "NULL route calculated for " + address);
                        continue;
                    }
                    byte[] rawAddress = address.getAddress();
                    long multicastGroup;
                    long multicastAdi;
                    if (rawAddress.length == 4) {
                        multicastGroup = InetAddressUtils.BROADCAST_MAC_ADDRESS;
                        multicastAdi = ByteBuffer.wrap(rawAddress).getInt();
                    } else {
                        multicastGroup = ByteBuffer.wrap(new byte[]{
                                0, 0, 0x33, 0x33, (byte) 0xFF, rawAddress[13], rawAddress[14], rawAddress[15]})
                                .getLong();
                        multicastAdi = 0;
                    }
                    var result = this.node.multicastSubscribe(nwid, multicastGroup, multicastAdi);
                    if (result != ResultCode.RESULT_OK) {
                        Log.e(TAG, "Error joining multicast group on " + Long.toHexString(nwid));
                    }
                    builder.addAddress(address, prefix);
                    builder.addRoute(route, prefix);
                    this.tunTapAdapter.addRouteAndNetwork(new Route(route, prefix), nwid);
                }

                // 2) 该网络下发的路由
                for (var routeConfig : virtualNetworkConfig.getRoutes()) {
                    var target = routeConfig.getTarget();
                    var via = routeConfig.getVia();
                    var targetAddress = target.getAddress();
                    int targetPort = target.getPort();
                    var viaAddress = InetAddressUtils.addressToRoute(targetAddress, targetPort);

                    boolean isIPv6Route = (targetAddress instanceof Inet6Address) || (viaAddress instanceof Inet6Address);
                    if (this.disableIPv6 && isIPv6Route) {
                        continue;
                    }
                    boolean shouldRouteToZerotier = viaAddress != null && (
                            this.routeAllTraffic
                                    || (!viaAddress.equals(v4Loopback) && !viaAddress.equals(v6Loopback))
                    );
                    if (!shouldRouteToZerotier) {
                        continue;
                    }
                    builder.addRoute(viaAddress, targetPort);
                    var route = new Route(viaAddress, targetPort);
                    if (via != null) {
                        route.setGateway(via.getAddress());
                    }
                    this.tunTapAdapter.addRouteAndNetwork(route, nwid);
                }

                // 3) 该网络的 DNS
                if (dbNetwork != null) {
                    addDNSServers(builder, dbNetwork);
                }

                // 4) MTU 取所有网络的最小值
                int networkMtu = virtualNetworkConfig.getMtu();
                if (networkMtu > 0) {
                    mtu = Math.min(mtu, networkMtu);
                }
            }
            builder.addRoute(InetAddress.getByName("224.0.0.0"), 4);
        } catch (Exception e) {
            this.eventBus.post(new VPNErrorEvent(e.getLocalizedMessage()));
            Log.e(TAG, "Cannot configure VPN", e);
            return false;
        }

        if (mtu == Integer.MAX_VALUE) {
            mtu = 2800;
        }
        Log.i(TAG, "MTU Set: " + mtu);
        builder.setMtu(mtu);

        if (Build.VERSION.SDK_INT >= 29) {
            builder.setMetered(false);
        }
        builder.setSession(Constants.VPN_SESSION_NAME);

        // 只有没开全局全隧道时才让部分应用绕开 VPN
        if (!this.routeAllTraffic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            for (var app : DISALLOWED_APPS) {
                try {
                    builder.addDisallowedApplication(app);
                } catch (Exception e3) {
                    Log.e(TAG, "Cannot disallow app", e3);
                }
            }
        }

        try {
            this.vpnSocket = builder.establish();
        } catch (IllegalArgumentException e) {
            // 配置在构建过程中失效（例如地址被回收）时不应打断事件分发
            Log.e(TAG, "Cannot establish VPN: " + e.getMessage(), e);
            this.eventBus.post(new VPNErrorEvent(getString(R.string.toast_vpn_application_not_prepared)));
            return false;
        }
        if (this.vpnSocket == null) {
            this.eventBus.post(new VPNErrorEvent(getString(R.string.toast_vpn_application_not_prepared)));
            return false;
        }
        this.in = new FileInputStream(this.vpnSocket.getFileDescriptor());
        this.out = new FileOutputStream(this.vpnSocket.getFileDescriptor());
        this.tunTapAdapter.setVpnSocket(this.vpnSocket);
        this.tunTapAdapter.setFileStreams(this.in, this.out);
        this.tunTapAdapter.startThreads();
        this.tunnelFingerprint = fingerprint;

        // 状态栏提示
        if (this.notificationManager == null) {
            this.notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            String channelName = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            var channel = new NotificationChannel(
                    Constants.CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(description);
            this.notificationManager.createNotificationChannel(channel);
        }
        int pendingIntentFlag = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) {
            pendingIntentFlag |= PendingIntent.FLAG_IMMUTABLE;
        }
        var pendingIntent =
                PendingIntent.getActivity(this, 0,
                        new Intent(this, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        , pendingIntentFlag);
        String notificationText = configs.size() == 1
                ? getString(R.string.notification_text_connected, Long.toHexString(configs.keySet().iterator().next()))
                : getString(R.string.notification_text_connected_multi, configs.size());
        var notification = new NotificationCompat.Builder(this, Constants.CHANNEL_ID)
                .setPriority(1)
                .setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.notification_title_connected))
                .setContentText(notificationText)
                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.zerotier_orange))
                .setContentIntent(pendingIntent).build();
        this.notificationManager.notify(ZT_NOTIFICATION_TAG, notification);
        Log.i(TAG, "ZeroTier One Connected");

        // 旧版本 Android 多播处理
        if (Build.VERSION.SDK_INT < 29) {
            if (this.v4MulticastScanner != null && !this.v4MulticastScanner.isAlive()) {
                this.v4MulticastScanner.start();
            }
            if (!this.disableIPv6 && this.v6MulticastScanner != null && !this.v6MulticastScanner.isAlive()) {
                this.v6MulticastScanner.start();
            }
        }
        return true;
    }

    private void addDNSServers(VpnService.Builder builder, Network network) {
        var networkConfig = network.getNetworkConfig();
        var virtualNetworkConfig = getVirtualNetworkConfig(network.getNetworkId());
        var dnsMode = DNSMode.fromInt(networkConfig.getDnsMode());

        switch (dnsMode) {
            case NETWORK_DNS:
                if (virtualNetworkConfig.getDns() == null) {
                    return;
                }
                builder.addSearchDomain(virtualNetworkConfig.getDns().getDomain());
                for (var inetSocketAddress : virtualNetworkConfig.getDns().getServers()) {
                    InetAddress address = inetSocketAddress.getAddress();
                    if (address instanceof Inet4Address) {
                        builder.addDnsServer(address);
                    } else if ((address instanceof Inet6Address) && !this.disableIPv6) {
                        builder.addDnsServer(address);
                    }
                }
                break;
            case CUSTOM_DNS:
                for (var dnsServer : networkConfig.getDnsServers()) {
                    try {
                        InetAddress byName = InetAddress.getByName(dnsServer.getNameserver());
                        if (byName instanceof Inet4Address) {
                            builder.addDnsServer(byName);
                        } else if ((byName instanceof Inet6Address) && !this.disableIPv6) {
                            builder.addDnsServer(byName);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Exception parsing DNS server: " + e, e);
                    }
                }
                break;
            default:
                break;
        }
    }

    /**
     * 入轨事件
     */
    @Subscribe
    public void onOrbitMoonEvent(OrbitMoonEvent event) {
        if (this.node == null) {
            Log.e(TAG, "Can't orbit network if ZeroTier isn't running");
            return;
        }
        // 入轨
        for (MoonOrbit moonOrbit : event.getMoonOrbits()) {
            Log.i(TAG, "Orbiting moon: " + Long.toHexString(moonOrbit.getMoonWorldId()));
            this.orbitNetwork(moonOrbit.getMoonWorldId(), moonOrbit.getMoonSeed());
        }
    }

    /**
     * 当前网络入轨 Moon
     *
     * @param moonWorldId Moon 节点地址
     * @param moonSeed    Moon 种子节点地址
     */
    public void orbitNetwork(Long moonWorldId, Long moonSeed) {
        if (this.node == null) {
            Log.e(TAG, "Can't orbit network if ZeroTier isn't running");
            return;
        }
        // 入轨
        ResultCode result = this.node.orbit(moonWorldId, moonSeed);
        if (result != ResultCode.RESULT_OK) {
            Log.e(TAG, "Failed to orbit " + Long.toHexString(moonWorldId));
            this.eventBus.post(new ErrorEvent(result));
        }
    }

    public class ZeroTierBinder extends Binder {
        public ZeroTierBinder() {
        }

        public ZeroTierOneService getService() {
            return ZeroTierOneService.this;
        }
    }
}
