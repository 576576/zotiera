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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.preference.PreferenceManager
import net.sumitm.zotiera.R
import net.sumitm.zotiera.ui.component.AppBottomBar
import net.sumitm.zotiera.ui.component.AppNavigationRail
import net.sumitm.zotiera.ui.component.AppScaffold
import net.sumitm.zotiera.ui.component.NavDestination
import net.sumitm.zotiera.ui.component.ScreenSurface
import net.sumitm.zotiera.ui.component.isWideLayout
import net.sumitm.zotiera.ui.screens.DeviceScreen
import net.sumitm.zotiera.ui.screens.AboutScreen
import net.sumitm.zotiera.ui.screens.IdentityScreen
import net.sumitm.zotiera.ui.screens.LicensesScreen
import net.sumitm.zotiera.ui.screens.MoonsScreen
import net.sumitm.zotiera.ui.screens.PlanetScreen
import net.sumitm.zotiera.ui.screens.NetworkDetailScreen
import net.sumitm.zotiera.ui.screens.OverviewScreen
import net.sumitm.zotiera.ui.screens.PeersScreen
import net.sumitm.zotiera.ui.screens.SettingsScreen
import net.sumitm.zotiera.ui.theme.ColorMode
import net.sumitm.zotiera.ui.theme.ZotieraTheme
import net.sumitm.zotiera.util.Constants

/** 页面切换时长：够短，返回手势跟手。 */
private const val NAV_DURATION = 220

class MainActivity : ComponentActivity() {

    private val viewModel: NodeViewModel by viewModels()
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        // 旧版本把纯黑存成第四种配色（3），现在深色本身就是纯黑
        val storedMode = prefs.getInt(Constants.PREF_COLOR_MODE, ColorMode.SYSTEM.value)
        setContent {
            var uiMode by remember {
                mutableStateOf(UiMode.fromValue(prefs.getString(Constants.PREF_UI_STYLE, null)))
            }
            var colorMode by remember { mutableStateOf(ColorMode.fromValue(storedMode)) }
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
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 隧道状态是全局的，提示放在这里，切到别的页面也能看到
    var wasRunning by remember { mutableStateOf<Boolean?>(null) }
    var wasConnected by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(state.tunnelRunning) {
        if (wasRunning == true && !state.tunnelRunning) toast(context, R.string.disconnected_toast)
        wasRunning = state.tunnelRunning
    }
    // 开关打开只代表「开始连」，要等节点真的上线才提示已连接
    LaunchedEffect(state.tunnelRunning, state.online) {
        val connected = state.tunnelRunning && state.online
        if (connected && wasConnected == false) toast(context, R.string.connected_toast)
        wasConnected = connected
    }

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
                    // 入栈：新页从右侧滑入；出栈：上面的页整页右移退出，下面的页不动。
                    // 只做位移不做淡入淡出——每页是不透明的，一旦半透明就会和下面那页叠字。
                    enterTransition = {
                        slideInHorizontally(tween(NAV_DURATION)) { it / 3 }
                    },
                    exitTransition = {
                        slideOutHorizontally(tween(NAV_DURATION)) { -it / 6 }
                    },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = {
                        slideOutHorizontally(tween(NAV_DURATION)) { it }
                    },
                ) {
                    // 三个顶层页平级：切换不做位移动画，只是换内容
                    tab("overview") {
                        OverviewScreen(
                            viewModel = viewModel,
                            onOpenDevice = { navController.navigate("device") },
                            onOpenNetwork = { id -> navController.navigate("network/$id") },
                        )
                    }
                    tab("peers") { PeersScreen(viewModel) }
                    tab("settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            uiMode = uiMode,
                            onUiModeChange = onUiModeChange,
                            colorMode = colorMode,
                            onColorModeChange = onColorModeChange,
                            onOpenIdentities = { navController.navigate("identities") },
                            onOpenPlanet = { navController.navigate("planet") },
                            onOpenMoons = { navController.navigate("moons") },
                            onOpenAbout = { navController.navigate("about") },
                        )
                    }

                    // 以下是从顶层页推入的详情页，走滑入/滑出
                    composable("device") {
                        ScreenSurface {
                            DeviceScreen(viewModel, onBack = { navController.popBackStack() })
                        }
                    }
                    composable(
                        route = "network/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    ) { entry ->
                        val id = entry.arguments?.getLong("id") ?: 0L
                        val network = state.networks.firstOrNull { it.networkId == id }
                        if (network == null) {
                            // 网络被删掉了，别停在一个空详情页上
                            LaunchedEffect(Unit) { navController.popBackStack() }
                        } else {
                            ScreenSurface {
                                NetworkDetailScreen(
                                    viewModel = viewModel,
                                    network = network,
                                    config = state.configs[id],
                                    onBack = { navController.popBackStack() },
                                )
                            }
                        }
                    }
                    composable("moons") {
                        ScreenSurface {
                            MoonsScreen(viewModel, onBack = { navController.popBackStack() })
                        }
                    }
                    composable("identities") {
                        ScreenSurface { IdentityScreen(onBack = { navController.popBackStack() }) }
                    }
                    composable("planet") {
                        ScreenSurface { PlanetScreen(onBack = { navController.popBackStack() }) }
                    }
                    composable("about") {
                        ScreenSurface {
                            AboutScreen(
                                onBack = { navController.popBackStack() },
                                onOpenLicenses = { navController.navigate("licenses") },
                            )
                        }
                    }
                    composable("licenses") {
                        ScreenSurface { LicensesScreen(onBack = { navController.popBackStack() }) }
                    }
                }
            }
        }
    }
}

/** 顶层页：进出都不做位移动画。 */
private fun NavGraphBuilder.tab(route: String, content: @Composable () -> Unit) {
    composable(
        route = route,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        ScreenSurface { content() }
    }
}
