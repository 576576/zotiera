package net.sumitm.zotiera.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.io.FileNotFoundException
import java.io.IOException
import java.util.regex.Pattern

/** 网络连接信息处理工具类。 */
object NetworkInfoUtils {
    const val TAG = "NetworkInfoUtils"

    private val WHITESPACE = Pattern.compile("\\s+")

    enum class CurrentConnection {
        CONNECTION_NONE,
        CONNECTION_MOBILE,
        CONNECTION_OTHER,
    }

    @JvmStatic
    fun getNetworkInfoCurrentConnection(context: Context): CurrentConnection {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return CurrentConnection.CONNECTION_NONE
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            ?: return CurrentConnection.CONNECTION_NONE
        return if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            CurrentConnection.CONNECTION_MOBILE
        } else {
            CurrentConnection.CONNECTION_OTHER
        }
    }

    /**
     * 从 /proc/net/igmp 或 /proc/net/igmp6 读指定网卡加入的组播组。
     *
     * 文件格式大致为：
     *
     *     Idx     Device    : Count Querier       Group    Users Timer    Reporter
     *     1       tun0      :     2      V3
     *                                     010000E0     1 0:00000000               0
     *     2       wlan0     :     1      V3
     *                                     010000E0     1 0:00000000               0
     *
     * 所以要先定位目标网卡那一行，再往下读若干行组播组，直到遇到下一个网卡行。
     */
    @JvmStatic
    fun listMulticastGroupOnInterface(interfaceName: String, isIpv6: Boolean): List<String> {
        val groups = ArrayList<String>()
        val path = if (isIpv6) "/proc/net/igmp6" else "/proc/net/igmp"
        try {
            java.io.BufferedReader(java.io.FileReader(path)).use { reader ->
                var foundTarget = false
                while (true) {
                    val line = reader.readLine() ?: break
                    val row = WHITESPACE.split(line, -1)
                    if (!foundTarget) {
                        if (row.size > 1 && row[1] == interfaceName) {
                            foundTarget = true
                        }
                    } else if (row[0].isEmpty()) {
                        groups.add(row[1])
                    } else {
                        // 目标网卡的段落结束
                        foundTarget = false
                    }
                }
            }
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "IGMP info file not found", e)
        } catch (e: IOException) {
            Log.e(TAG, "Error reading IGMP info", e)
        }
        return groups
    }
}
