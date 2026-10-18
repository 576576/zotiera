package net.sumitm.zotiera.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import net.sumitm.zotiera.R
import net.sumitm.zotiera.ui.screens.NetworksScreen
import net.sumitm.zotiera.ui.screens.PeersScreen
import net.sumitm.zotiera.ui.screens.SettingsScreen
import net.sumitm.zotiera.ui.theme.ZotieraTheme

class MainActivity : ComponentActivity() {

    private val viewModel: NodeViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZotieraTheme {
                ZotieraApp(viewModel)
            }
        }
        requestNotificationPermissionIfNeeded()
        viewModel.refresh()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private enum class Destination(val route: String, val label: Int) {
    NETWORKS("networks", R.string.title_activity_zero_tier_one__main),
    PEERS("peers", R.string.peer_list),
    SETTINGS("settings", R.string.settings),
}

@Composable
private fun ZotieraApp(viewModel: NodeViewModel) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = when (destination) {
                                    Destination.NETWORKS -> Icons.Filled.List
                                    Destination.PEERS -> Icons.Filled.List
                                    Destination.SETTINGS -> Icons.Filled.Settings
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.NETWORKS.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.NETWORKS.route) { NetworksScreen(viewModel) }
            composable(Destination.PEERS.route) { PeersScreen(viewModel) }
            composable(Destination.SETTINGS.route) { SettingsScreen(viewModel) }
        }
    }
}
