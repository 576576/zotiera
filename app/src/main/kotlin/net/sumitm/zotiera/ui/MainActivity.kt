package net.sumitm.zotiera.ui

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
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
import net.sumitm.zotiera.ui.screens.AboutScreen
import net.sumitm.zotiera.ui.screens.DeviceScreen
import net.sumitm.zotiera.ui.screens.IdentityScreen
import net.sumitm.zotiera.ui.screens.LicensesScreen
import net.sumitm.zotiera.ui.screens.MoonsScreen
import net.sumitm.zotiera.ui.screens.NetworkDetailScreen
import net.sumitm.zotiera.ui.screens.OverviewScreen
import net.sumitm.zotiera.ui.screens.PeersScreen
import net.sumitm.zotiera.ui.screens.PlanetScreen
import net.sumitm.zotiera.ui.screens.SettingsScreen
import net.sumitm.zotiera.ui.theme.ColorMode
import net.sumitm.zotiera.ui.theme.ZotieraTheme
import net.sumitm.zotiera.util.AppLanguage
import net.sumitm.zotiera.util.Constants

/** 页面切换时长：够短，返回手势跟手。 */
private const val NAV_DURATION = 220

/** 顶层页切换：略慢一点，淡入淡出才看得出来。 */
private const val TAB_DURATION = 240

class MainActivity : ComponentActivity() {

    private val viewModel: NodeViewModel by viewModels()
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** 本进程内只换一次任务，避免任务标志没带上时反复重开。 */
    private var recentsChecked = false

    override fun attachBaseContext(newBase: Context) {
        // 应用内语言在资源加载前就要生效
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onResume() {
        super.onResume()
        applyRecentsExclusion()
    }

    /**
     * 「从最近任务中隐藏」只能靠 [Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS]，而它只在任务创建时生效，
     * 启动器的 Intent 又不带这个标志，所以发现当前任务没带标志时换一个新任务。
     *
     * 必须带 [Intent.FLAG_ACTIVITY_MULTIPLE_TASK]，否则系统会把新 Activity 塞回已有任务；
     * 旧任务用 finishAndRemoveTask 清掉，不然它会一直留在最近任务里。
     */
    private fun applyRecentsExclusion() {
        if (recentsChecked) return
        recentsChecked = true
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean(Constants.PREF_HIDE_FROM_RECENTS, false)) return
        if (intent.flags and Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS != 0) return
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
            )
        )
        finishAndRemoveTask()
    }

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
                    onLanguageChange = { tag ->
                        // API 33+ 由系统保存 per-app 语言并重建界面；30–32 只有偏好，得自己重建
                        if (!AppLanguage.apply(this@MainActivity, tag)) recreate()
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
    onLanguageChange: (String) -> Unit,
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
        NavDestination("overview", stringResource(R.string.network_list), R.drawable.ic_dns),
        NavDestination("peers", stringResource(R.string.peer_list), R.drawable.ic_devices),
        NavDestination("settings", stringResource(R.string.settings), R.drawable.ic_settings),
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
                    // 入栈：新页从右侧整屏滑入；出栈：上面的页整屏右移退出，下面的页不动。
                    // 两个方向幅度一致，系统预测性返回手势走的就是 popExitTransition，手感才对得上。
                    enterTransition = {
                        slideInHorizontally(tween(NAV_DURATION)) { it }
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
                            onLanguageChange = onLanguageChange,
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

/**
 * 顶层页：切换时淡入淡出，新页从下方轻微上浮。
 *
 * 详情页之间仍走横向位移；从详情页返回顶层页不做动画，否则会看到两套位移叠在一起。
 */
private fun NavGraphBuilder.tab(route: String, content: @Composable () -> Unit) {
    composable(
        route = route,
        enterTransition = {
            fadeIn(tween(TAB_DURATION)) + slideInVertically(tween(TAB_DURATION)) { it / 32 }
        },
        exitTransition = { fadeOut(tween(TAB_DURATION / 2)) },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        ScreenSurface { content() }
    }
}
