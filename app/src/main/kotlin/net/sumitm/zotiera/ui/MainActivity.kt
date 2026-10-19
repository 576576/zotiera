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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.preference.PreferenceManager
import net.sumitm.zotiera.R
import net.sumitm.zotiera.ui.component.AppBottomBar
import net.sumitm.zotiera.ui.component.AppNavigationRail
import net.sumitm.zotiera.ui.component.AppScaffold
import net.sumitm.zotiera.ui.component.NavDestination
import net.sumitm.zotiera.ui.component.isWideLayout
import net.sumitm.zotiera.ui.screens.DeviceScreen
import net.sumitm.zotiera.ui.screens.MoonsScreen
import net.sumitm.zotiera.ui.screens.OverviewScreen
import net.sumitm.zotiera.ui.screens.PeersScreen
import net.sumitm.zotiera.ui.screens.SettingsScreen
import net.sumitm.zotiera.ui.theme.ColorMode
import net.sumitm.zotiera.ui.theme.ZotieraTheme
import net.sumitm.zotiera.util.Constants

class MainActivity : ComponentActivity() {

    private val viewModel: NodeViewModel by viewModels()
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        setContent {
            var uiMode by remember {
                mutableStateOf(UiMode.fromValue(prefs.getString(Constants.PREF_UI_STYLE, null)))
            }
            var colorMode by remember {
                mutableStateOf(
                    ColorMode.fromValue(prefs.getInt(Constants.PREF_COLOR_MODE, ColorMode.SYSTEM.value))
                )
            }
            // NavController 在主题之外：切换风格会重建主题子树
            val navController = rememberNavController()
            ZotieraTheme(uiMode = uiMode, colorMode = colorMode) {
                ZotieraApp(
                    navController = navController,
                    viewModel = viewModel,
                    uiMode = uiMode,
                    onUiModeChange = { mode ->
                        uiMode = mode
                        prefs.edit { putString(Constants.PREF_UI_STYLE, mode.value) }
                    },
                    colorMode = colorMode,
                    onColorModeChange = { mode ->
                        colorMode = mode
                        prefs.edit { putInt(Constants.PREF_COLOR_MODE, mode.value) }
                    },
                )
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
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun ZotieraApp(
    navController: NavHostController,
    viewModel: NodeViewModel,
    uiMode: UiMode,
    onUiModeChange: (UiMode) -> Unit,
    colorMode: ColorMode,
    onColorModeChange: (ColorMode) -> Unit,
) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val wide = isWideLayout()

    fun navigate(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val items = listOf(
        NavDestination("overview", stringResource(R.string.network_list), Icons.Rounded.Dns),
        NavDestination("peers", stringResource(R.string.peer_list), Icons.Rounded.Devices),
        NavDestination("settings", stringResource(R.string.settings), Icons.Rounded.Settings),
    )

    AppScaffold(
        bottomBar = {
            if (!wide && currentRoute != "moons") {
                AppBottomBar(items, currentRoute, ::navigate)
            }
        },
    ) { padding ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(
                    WindowInsets.displayCutout.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    )
                )
        ) {
            if (wide) AppNavigationRail(items, currentRoute, ::navigate)
            // 宽屏铺满会把行尾控件推到对侧，限制可读宽度并居中
            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                NavHost(
                    navController = navController,
                    startDestination = "overview",
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                ) {
                    composable("overview") {
                        OverviewScreen(viewModel, onOpenDevice = { navController.navigate("device") })
                    }
                    composable("device") {
                        DeviceScreen(viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("peers") { PeersScreen(viewModel) }
                    composable("settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            uiMode = uiMode,
                            onUiModeChange = onUiModeChange,
                            colorMode = colorMode,
                            onColorModeChange = onColorModeChange,
                            onOpenMoons = { navController.navigate("moons") },
                        )
                    }
                    composable("moons") {
                        MoonsScreen(viewModel, onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
