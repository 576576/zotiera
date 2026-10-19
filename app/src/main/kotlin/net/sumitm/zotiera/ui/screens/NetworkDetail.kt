package net.sumitm.zotiera.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zerotier.sdk.VirtualNetworkConfig
import com.zerotier.sdk.VirtualNetworkStatus
import net.sumitm.zotiera.R
import net.sumitm.zotiera.model.Network
import net.sumitm.zotiera.ui.NodeViewModel
import net.sumitm.zotiera.ui.component.AppCard
import net.sumitm.zotiera.ui.component.AppKeyValueRow
import net.sumitm.zotiera.ui.component.AppTopBar
import androidx.compose.foundation.layout.fillMaxWidth

/** 标题用：没有名字时退回网络 ID，避免顶栏只剩一个「-」。 */
@Composable
internal fun networkTitle(network: Network, config: VirtualNetworkConfig?): String =
    config?.name?.takeIf { it.isNotBlank() }
        ?: network.networkName?.takeIf { it.isNotBlank() }
        ?: java.lang.Long.toHexString(network.networkId)

/** 网络名称：控制器下发的优先，其次是数据库里缓存的。 */
@Composable
internal fun networkDisplayName(network: Network, config: VirtualNetworkConfig?): String =
    config?.name?.takeIf { it.isNotBlank() }
        ?: network.networkName?.takeIf { it.isNotBlank() }
        ?: "-"

/** MAC：每个网络各分配一个。 */
internal fun networkMac(config: VirtualNetworkConfig?): String =
    config?.let { com.zerotier.sdk.util.StringUtils.macAddressToString(it.mac) } ?: "-"

@Composable
internal fun statusLabel(status: VirtualNetworkStatus?): String = when (status) {
    VirtualNetworkStatus.NETWORK_STATUS_REQUESTING_CONFIGURATION ->
        stringResource(R.string.network_status_requesting)
    VirtualNetworkStatus.NETWORK_STATUS_OK -> stringResource(R.string.network_status_ok)
    VirtualNetworkStatus.NETWORK_STATUS_ACCESS_DENIED -> stringResource(R.string.network_status_access_denied)
    VirtualNetworkStatus.NETWORK_STATUS_NOT_FOUND -> stringResource(R.string.network_status_not_found)
    VirtualNetworkStatus.NETWORK_STATUS_PORT_ERROR -> stringResource(R.string.network_status_port_error)
    VirtualNetworkStatus.NETWORK_STATUS_AUTHENTICATION_REQUIRED ->
        stringResource(R.string.network_status_auth_required)
    VirtualNetworkStatus.NETWORK_STATUS_CLIENT_TOO_OLD -> stringResource(R.string.network_status_client_too_old)
    null -> "-"
    else -> status.name
}

/** 基本信息卡：名称 / 网络 ID / MAC / 状态（可选 MTU、DNS）。 */
@Composable
internal fun NetworkInfoCard(
    network: Network,
    config: VirtualNetworkConfig?,
    withMtuAndDns: Boolean = true,
) {
    AppCard {
        Column(Modifier.padding(vertical = 8.dp)) {
            AppKeyValueRow(
                stringResource(R.string.network_name_label),
                networkDisplayName(network, config),
            )
            AppKeyValueRow(
                stringResource(R.string.network_id),
                java.lang.Long.toHexString(network.networkId),
                mono = true,
            )
            AppKeyValueRow(stringResource(R.string.mac_address_label), networkMac(config), mono = true)
            AppKeyValueRow(stringResource(R.string.network_status_label), statusLabel(config?.status))
            if (withMtuAndDns) {
                AppKeyValueRow("MTU", config?.mtu?.toString() ?: "-", mono = true)
                val dns = network.networkConfig?.dnsServers.orEmpty().mapNotNull { it.nameserver }
                AppKeyValueRow(
                    label = stringResource(R.string.dns_label_string),
                    value = if (dns.isEmpty()) "-" else dns.joinToString(", "),
                    mono = dns.isNotEmpty(),
                )
            }
        }
    }
}

/** 分配地址卡：可能有多个（IPv4 / IPv6 / 多个网段）。 */
@Composable
internal fun NetworkAddressCard(config: VirtualNetworkConfig?) {
    AppCard {
        Column(Modifier.padding(vertical = 8.dp)) {
            val addresses = config?.assignedAddresses
            if (addresses.isNullOrEmpty()) {
                AppKeyValueRow(stringResource(R.string.ip_addresses_label), "-")
            } else {
                addresses.forEach { address ->
                    AppKeyValueRow(
                        label = stringResource(R.string.ip_addresses_label),
                        value = "${address.address.hostAddress}/${address.port}",
                        mono = true,
                    )
                }
            }
        }
    }
}

/** 详情主体：基本信息 + 分配地址 + 删除。宽屏右栏与窄屏页面共用。 */
@Composable
fun NetworkDetailPane(
    network: Network,
    config: VirtualNetworkConfig?,
    onDelete: (() -> Unit)? = null,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { NetworkInfoCard(network, config) }
        item { NetworkAddressCard(config) }
        if (onDelete != null) {
            item {
                androidx.compose.material3.TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.delete_network),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_network)) },
            text = { Text(networkDisplayName(network, config)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.delete_network)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

/** 窄屏的网络详情页（带返回与删除）。 */
@Composable
fun NetworkDetailScreen(
    viewModel: NodeViewModel,
    network: Network,
    config: VirtualNetworkConfig?,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = networkTitle(network, config), onBack = onBack)
        NetworkDetailPane(
            network = network,
            config = config,
            onDelete = {
                viewModel.deleteNetwork(network)
                onBack()
            },
        )
    }
}
