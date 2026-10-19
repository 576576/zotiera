package net.sumitm.zotiera.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.sumitm.zotiera.BuildConfig
import net.sumitm.zotiera.R
import net.sumitm.zotiera.ui.NodeViewModel
import net.sumitm.zotiera.ui.component.AppCard
import net.sumitm.zotiera.ui.component.AppKeyValueRow
import net.sumitm.zotiera.ui.component.AppTopBar
import net.sumitm.zotiera.ui.component.appColors
import net.sumitm.zotiera.ui.component.preference.PreferenceSwitch
import net.sumitm.zotiera.ui.theme.LocalZotieraType

/**
 * 设备详情页：版本、隧道实况，以及每个已加入网络的 MAC / 状态 / 地址。
 *
 * 从概览页的设备卡进入；与单个网络的详情页是两回事。
 */
@Composable
fun DeviceScreen(viewModel: NodeViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val type = LocalZotieraType.current
    val joined = state.networks.filter { it.networkId in state.joinedNetworkIds }

    // 隧道实际 MTU：各网络取最小值，与服务的算法一致；都没有时显示 -
    val tunnelMtu = state.configs.values
        .mapNotNull { it.mtu.takeIf { mtu -> mtu > 0 } }
        .minOrNull()

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = stringResource(R.string.device_title), onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 1) 版本
            item {
                AppCard {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        AppKeyValueRow(stringResource(R.string.client_version), BuildConfig.VERSION_NAME, mono = true)
                        AppKeyValueRow(
                            stringResource(R.string.core_version),
                            state.coreVersion.ifEmpty { "-" },
                            mono = true,
                        )
                    }
                }
            }

            // 2) 隧道设置
            item {
                AppCard {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        AppKeyValueRow(
                            label = stringResource(R.string.tunnel_mtu),
                            value = tunnelMtu?.toString() ?: "-",
                            mono = true,
                        )
                        PreferenceSwitch(
                            title = stringResource(R.string.route_all_traffic),
                            summary = stringResource(R.string.route_all_traffic_summary),
                            checked = state.routeAllTraffic,
                            onCheckedChange = { viewModel.setRouteAllTraffic(it) },
                        )
                    }
                }
            }

            // 3) 每个已加入网络：信息 + 地址
            if (joined.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.no_data),
                        style = type.rowTitle,
                        color = appColors().onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {
                joined.forEach { network ->
                    item(key = "info-${network.networkId}") {
                        NetworkInfoCard(
                            network = network,
                            config = state.configs[network.networkId],
                            withMtuAndDns = false,
                        )
                    }
                    item(key = "addr-${network.networkId}") {
                        NetworkAddressCard(config = state.configs[network.networkId])
                    }
                }
            }
        }
    }
}
