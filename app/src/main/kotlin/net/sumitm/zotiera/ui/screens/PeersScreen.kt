package net.sumitm.zotiera.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zerotier.sdk.PeerRole
import net.sumitm.zotiera.R
import net.sumitm.zotiera.ui.NodeViewModel
import net.sumitm.zotiera.ui.component.AppLargeTitlePage
import net.sumitm.zotiera.ui.component.AppListRow
import net.sumitm.zotiera.ui.component.AppRoleChip
import net.sumitm.zotiera.ui.theme.LocalDarkTheme

@Composable
fun PeersScreen(viewModel: NodeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AppLargeTitlePage(title = stringResource(R.string.peer_list)) {
        Column(Modifier.fillMaxSize()) {
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
                            append(version)
                            append(" · ")
                            append(latency)
                            if (path != null) {
                                append("\n")
                                append(formatSocketAddress(path))
                            }
                        },
                        monoSubtitle = true,
                        trailing = {
                            AppRoleChip(
                                label = roleLabel(peer.role),
                                tint = roleTint(peer.role),
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * 角色名。核心报的是 `PEER_ROLE_*`，这里换成给人看的词。
 */
@Composable
private fun roleLabel(role: PeerRole): String = when (role) {
    PeerRole.PEER_ROLE_PLANET -> stringResource(R.string.peer_role_planet)
    PeerRole.PEER_ROLE_MOON -> stringResource(R.string.peer_role_moon)
    PeerRole.PEER_ROLE_LEAF -> stringResource(R.string.peer_role_leaf)
}

/**
 * 角色配色：根服务器最醒目，Moon 次之，普通叶结点用蓝色。
 *
 * 深色下用同一色相提亮的一档，否则小字在胶囊底色上不够清楚。
 */
@Composable
private fun roleTint(role: PeerRole): Color {
    val dark = LocalDarkTheme.current
    return when (role) {
        PeerRole.PEER_ROLE_PLANET ->
            if (dark) Color(0xFFFFA95E) else Color(0xFFB54708)
        PeerRole.PEER_ROLE_MOON ->
            if (dark) Color(0xFFC9A7FF) else Color(0xFF6941C6)
        PeerRole.PEER_ROLE_LEAF ->
            if (dark) Color(0xFF8CC0FF) else Color(0xFF1D5FBF)
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
