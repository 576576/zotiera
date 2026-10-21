package net.sumitm.zotiera.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import net.sumitm.zotiera.util.AppPrefs
import net.sumitm.zotiera.util.Constants

/** 开机自启：设置里打开后，收到启动完成的广播就把服务拉起来。 */
class StartupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Received: ${intent.action}")
        val prefs = AppPrefs.get(context)
        if (!prefs.getBoolean(Constants.PREF_GENERAL_START_ZEROTIER_ON_BOOT, false)) {
            Log.i(TAG, "Preferences set to not start ZeroTier on boot")
            return
        }
        Log.i(TAG, "Preferences set to start ZeroTier on boot")
        ServiceCommands.start(context)
    }

    private companion object {
        const val TAG = "StartupReceiver"
    }
}
