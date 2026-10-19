package net.sumitm.zotiera.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import net.sumitm.zotiera.R
import net.sumitm.zotiera.ui.NodeViewModel
import net.sumitm.zotiera.ui.component.AppHeader
import net.sumitm.zotiera.ui.component.AppListRow
import net.sumitm.zotiera.ui.component.appColors

@Composable
fun PeersScreen(viewModel: NodeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 延迟与路径是实时量，停在这个页面时持续刷新
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.requestPeers()
            delay(2000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = stringResource(R.string.peer_list),
            subtitle = stringResource(R.string.peer_subtitle),
        )
        if (state.peers.isEmpty()) {
            Text(
                text = stringResource(
                    if (state.peersLoaded) R.string.peer_no_connection else R.string.wait_to_fetch
                ),
                color = appColors().onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.peers, key = { it.address }) { peer ->
                    val version = if (peer.versionMajor < 0 || peer.versionMinor < 0 || peer.versionRev < 0) {
                        stringResource(R.string.peer_version_unknown)
                    } else {
                        "v${peer.versionMajor}.${peer.versionMinor}.${peer.versionRev}"
                    }
                    val latency = if (peer.latency < 0) "-" else "${peer.latency} ms"
                    val path = peer.paths?.firstOrNull()?.address
                    AppListRow(
                        title = java.lang.Long.toHexString(peer.address),
                        subtitle = buildString {
                            append(peer.role.name)
                            append(" · ")
                            append(version)
                            append(" · ")
                            append(latency)
                            if (path != null) {
                                append("\n")
                                append(formatSocketAddress(path))
                            }
                        },
                        monoSubtitle = true,
                    )
                }
            }
        }
    }
}

/**
 * InetSocketAddress.toString() 会输出 `/1.2.3.4:9993`、`/[::1]:9993`，
 * 前导斜杠和方括号是 Java 的调试格式，不是地址。
 */
private fun formatSocketAddress(address: java.net.InetSocketAddress): String {
    val host = address.address?.hostAddress ?: address.hostString
    val bare = host.removePrefix("[").removeSuffix("]").substringBefore('%')
    return if (bare.contains(':')) "[$bare]:${address.port}" else "$bare:${address.port}"
}
