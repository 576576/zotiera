package net.sumitm.zotiera.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zerotier.sdk.Peer
import net.sumitm.zotiera.R
import net.sumitm.zotiera.ui.NodeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeersScreen(viewModel: NodeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.requestPeers() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.peer_list)) },
                actions = {
                    TextButton(onClick = { viewModel.requestPeers() }) {
                        Text(stringResource(R.string.refresh))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            if (state.peers.isEmpty()) {
                Text(
                    text = stringResource(
                        if (state.peersLoaded) R.string.peer_no_connection else R.string.wait_to_fetch
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 32.dp),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.peers, key = { it.address }) { peer ->
                        PeerRow(peer)
                    }
                }
            }
        }
    }
}

@Composable
private fun PeerRow(peer: Peer) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(
                    text = String.format("%10x", peer.address),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "  ${peer.role}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "v${peer.versionMajor}.${peer.versionMinor}.${peer.versionRev} · ${peer.latency} ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val path = peer.paths?.firstOrNull()?.address
            if (path != null) {
                Text(
                    text = path.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
