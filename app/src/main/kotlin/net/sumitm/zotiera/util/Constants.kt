package net.sumitm.zotiera.util

/** 维护程序中公共的常量。 */
object Constants {
    /** 界面风格：material / miuix */
    const val PREF_UI_STYLE = "ui_style"

    /** 全局：所有流量走 ZeroTier */
    const val PREF_ROUTE_ALL_TRAFFIC = "route_all_traffic"

    /** 配色模式：0 跟随系统 / 1 浅色 / 2 深色 */
    const val PREF_COLOR_MODE = "color_mode"

    const val PREF_NETWORK_USE_CELLULAR_DATA = "network_use_cellular_data"

    const val PREF_NETWORK_DISABLE_IPV6 = "network_disable_ipv6"

    const val PREF_GENERAL_START_ZEROTIER_ON_BOOT = "general_start_zerotier_on_boot"

    /** 简易模式：隐藏行星 / 身份管理，只用默认实例。 */
    const val PREF_SIMPLE_MODE = "simple_mode"

    /** 从最近任务中隐藏：冷启动时把任务重开一次并带上 EXCLUDE_FROM_RECENTS。 */
    const val PREF_HIDE_FROM_RECENTS = "hide_from_recents"

    const val FILE_TEMP = "temp"

    const val FILE_PLANET = "planet"

    const val CHANNEL_ID = "ZerotierFix"

    const val VPN_SESSION_NAME = "ZerotierFix"
}
