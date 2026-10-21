package net.sumitm.zotiera.util;

/**
 * 维护程序中公共的常量
 *
 * @author kaaass
 */
public class Constants {
    /** 界面风格：material / miuix */
    public static final String PREF_UI_STYLE = "ui_style";
    /** 全局：所有流量走 ZeroTier */
    public static final String PREF_ROUTE_ALL_TRAFFIC = "route_all_traffic";
    /** 配色模式：0 跟随系统 / 1 浅色 / 2 深色 */
    public static final String PREF_COLOR_MODE = "color_mode";
    public static final String PREF_NETWORK_USE_CELLULAR_DATA = "network_use_cellular_data";



    public static final String PREF_NETWORK_DISABLE_IPV6 = "network_disable_ipv6";

    public static final String PREF_GENERAL_START_ZEROTIER_ON_BOOT = "general_start_zerotier_on_boot";

    /** 简易模式：隐藏行星 / 身份管理，只用默认实例。 */
    public static final String PREF_SIMPLE_MODE = "simple_mode";

    /** 从最近任务中隐藏：冷启动时把任务重开一次并带上 EXCLUDE_FROM_RECENTS。 */
    public static final String PREF_HIDE_FROM_RECENTS = "hide_from_recents";



    public static final String FILE_TEMP = "temp";

    public static final String FILE_PLANET = "planet";

    public static final String CHANNEL_ID = "ZerotierFix";

    public static final String VPN_SESSION_NAME = "ZerotierFix";
}
