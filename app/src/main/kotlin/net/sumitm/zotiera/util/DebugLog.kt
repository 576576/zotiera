package net.sumitm.zotiera.util

import android.util.Log
import net.sumitm.zotiera.BuildConfig

/** 调试日志相关的工具函数，用于控制部分日志仅在调试模式下输出。 */
object DebugLog {
    @JvmStatic
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    @JvmStatic
    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message)
        }
    }
}
