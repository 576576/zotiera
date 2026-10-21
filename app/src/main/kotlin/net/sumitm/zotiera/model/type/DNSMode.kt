package net.sumitm.zotiera.model.type

import net.sumitm.zotiera.R

enum class DNSMode(private val id: Int) {
    NO_DNS(0),
    NETWORK_DNS(1),
    CUSTOM_DNS(2),
    ;

    fun toInt(): Int = id

    /** 界面显示的文案资源。 */
    fun toStringId(): Int = when (this) {
        NO_DNS -> R.string.network_dns_mode_no_dns
        NETWORK_DNS -> R.string.network_dns_mode_network_dns
        CUSTOM_DNS -> R.string.network_dns_mode_custom_dns
    }

    companion object {
        @JvmStatic
        fun fromInt(i: Int): DNSMode = when (i) {
            0 -> NO_DNS
            1 -> NETWORK_DNS
            2 -> CUSTOM_DNS
            else -> throw RuntimeException("Unhandled value: $i")
        }
    }
}
