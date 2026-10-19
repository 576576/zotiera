package net.sumitm.zotiera.ui

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

/** 跨 Activity/Fragment 传递的参数。 */
object Extras {
    /** 打开网络详情页时携带的网络 ID。 */
    const val NETWORK_ID = "com.zerotier.one.network-id"
}

/** 短提示。 */
fun toast(context: Context, @StringRes message: Int) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
