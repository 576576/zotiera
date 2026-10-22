package net.sumitm.zotiera.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import net.sumitm.zotiera.R
import net.sumitm.zotiera.ui.component.AppBottomBar
import net.sumitm.zotiera.ui.component.AppNavigationRail
import net.sumitm.zotiera.ui.component.AppScaffold
import net.sumitm.zotiera.ui.component.NavDestination
import net.sumitm.zotiera.ui.component.ScreenSurface
import net.sumitm.zotiera.ui.component.appColors
import net.sumitm.zotiera.ui.component.isWideLayout
import net.sumitm.zotiera.ui.screens.AboutScreen
import net.sumitm.zotiera.ui.screens.DeviceScreen
import net.sumitm.zotiera.ui.screens.IdentityScreen
import net.sumitm.zotiera.ui.screens.LicensesScreen
import net.sumitm.zotiera.ui.screens.MoonsScreen
import net.sumitm.zotiera.ui.screens.NetworkDetailPane
import net.sumitm.zotiera.ui.screens.NetworkDetailScreen
import net.sumitm.zotiera.ui.screens.OverviewScreen
import net.sumitm.zotiera.ui.screens.PeersScreen
import net.sumitm.zotiera.ui.screens.PlanetScreen
import net.sumitm.zotiera.ui.screens.SettingsScreen
import net.sumitm.zotiera.ui.theme.ColorMode
import net.sumitm.zotiera.ui.theme.ZotieraTheme
import net.sumitm.zotiera.util.AppLanguage
import net.sumitm.zotiera.util.AppPrefs
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
        val prefs = AppPrefs.get(this)
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

    /**
     * 选窗口主题。
     *
     * Compose 画出第一帧之前屏幕上是 XML 主题给的窗口底色，它不跟应用内的深浅色设置走，
     * 所以这里按设置先挑一套，免得系统浅色而应用内选深色时先闪一下白。
     */
    private fun applyWindowTheme() {
        val prefs = AppPrefs.get(this)
        val mode = ColorMode.fromValue(prefs.getInt(Constants.PREF_COLOR_MODE, ColorMode.SYSTEM.value))
        val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        setTheme(if (mode.isDark(systemDark)) R.style.Theme_Zotiera_Dark else R.style.Theme_Zotiera_Light)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyWindowTheme()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = AppPrefs.get(this)
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
    // 二级页只有这一份状态：宽屏铺在右栏，窄屏盖在顶层页上，旋转前后不会丢。
    // 用列表是因为关于页还能再开一层许可页。
    var details by rememberSaveable(stateSaver = Detail.SAVER) { mutableStateOf(emptyList<Detail>()) }
    val detail = details.lastOrNull()

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
        // 换顶层页就不再保留二级页，否则设置页会拖着概览页的设备页
        details = emptyList()
        navController.navigate(route) {
            // 导航栈里只留一个顶层页：二级页不在栈里，返回键才不会把顶层页当成「上一层」弹掉
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    // 概览页在宽屏下始终分栏（右栏空着时提示选一个网络），其它顶层页有二级页时才分
    val splitView = wide && (detail != null || currentRoute == "overview")

    val items = listOf(
        NavDestination("overview", stringResource(R.string.network_list), R.drawable.ic_dns),
        NavDestination("peers", stringResource(R.string.peer_list), R.drawable.ic_devices),
        NavDestination("settings", stringResource(R.string.settings), R.drawable.ic_settings),
    )

    AppScaffold(
        bottomBar = {
            if (!wide) {
                AppBottomBar(items, currentRoute, ::navigate)
            }
        },
    ) { padding ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (wide) {
                AppNavigationRail(items, currentRoute, ::navigate, Modifier.fillMaxHeight())
            }
            // 窄屏铺满会把行尾控件推到对侧，限制可读宽度并居中；分栏时左栏只占 0.42
            Box(
                Modifier.weight(if (splitView) 0.42f else 1f).fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                NavHost(
                    navController = navController,
                    startDestination = "overview",
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None },
                ) {
                    // 三个顶层页平级：切换不做位移动画，只是换内容
                    tab("overview") {
                        OverviewScreen(
                            viewModel = viewModel,
                            onOpenDevice = { details = details + Detail.Device },
                            onOpenNetwork = { id -> details = details + Detail.Network(id) },
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
                            onOpenIdentities = { details = details + Detail.Identities },
                            onOpenPlanet = { details = details + Detail.Planet },
                            onOpenMoons = { details = details + Detail.Moons },
                            onOpenAbout = { details = details + Detail.About },
                        )
                    }
                }

                // 窄屏：二级页盖在顶层页上，顶层页的滚动位置留着
                AnimatedContent(
                    targetState = if (wide) null else detail,
                    transitionSpec = {
                        if (targetState != null) {
                            slideInHorizontally(tween(NAV_DURATION)) { it } togetherWith
                                ExitTransition.None
                        } else {
                            EnterTransition.None togetherWith
                                slideOutHorizontally(tween(NAV_DURATION)) { it }
                        }
                    },
                    label = "detail",
                ) { target ->
                    if (target != null) {
                        DetailPage(
                            detail = target,
                            viewModel = viewModel,
                            onOpen = { details = details + it },
                            onClose = { details = details.dropLast(1) },
                        )
                    }
                }
            }
            if (splitView) {
                Box(Modifier.weight(0.58f).fillMaxSize()) {
                    if (detail != null) {
                        DetailPage(
                            detail = detail,
                            viewModel = viewModel,
                            onOpen = { details = details + it },
                            onClose = { details = details.dropLast(1) },
                        )
                    } else {
                        // 只有概览页会把空右栏露出来，给个提示；别的顶层页没有二级页时不分栏
                        Text(
                            text = stringResource(R.string.select_a_network),
                            color = appColors().onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        }
    }
    // 放在最后：返回处理是后注册者优先，写在 NavHost 前面会被导航栈先吃掉
    BackHandler(enabled = details.isNotEmpty()) { details = details.dropLast(1) }
}

/**
 * 宽屏右栏里的二级页，按路由名与网络 ID 存盘。
 *
 * 窄屏仍走导航栈，两边用的是同一批页面。
 */
private sealed interface Detail {
    val route: String

    data object Device : Detail {
        override val route = "device"
    }

    data class Network(val id: Long) : Detail {
        override val route = "network"
    }

    data object Moons : Detail {
        override val route = "moons"
    }

    data object Identities : Detail {
        override val route = "identities"
    }

    data object Planet : Detail {
        override val route = "planet"
    }

    data object About : Detail {
        override val route = "about"
    }

    data object Licenses : Detail {
        override val route = "licenses"
    }

    companion object {
        fun of(route: String, networkId: Long): Detail? = when (route) {
            "device" -> Device
            "network" -> Network(networkId)
            "moons" -> Moons
            "identities" -> Identities
            "planet" -> Planet
            "about" -> About
            "licenses" -> Licenses
            else -> null
        }

        /** 一摞页面按「路由名、网络 ID」交替铺平存盘。 */
        val SAVER = listSaver<List<Detail>, Any>(
            save = { list -> list.flatMap { listOf(it.route, (it as? Network)?.id ?: 0L) } },
            restore = { flat ->
                flat.chunked(2).mapNotNull { Detail.of(it[0] as String, it[1] as Long) }
            },
        )
    }
}

/**
 * 二级页。宽屏铺在右栏、不画顶栏（退出靠返回手势或换选），窄屏铺成整页、带头返回。
 */
@Composable
private fun DetailPage(
    detail: Detail,
    viewModel: NodeViewModel,
    onOpen: (Detail) -> Unit,
    onClose: () -> Unit,
) {
    val embedded = isWideLayout()
    when (detail) {
        Detail.Device -> ScreenSurface {
            DeviceScreen(viewModel, onBack = onClose, embedded = embedded)
        }

        is Detail.Network -> {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val network = state.networks.firstOrNull { it.networkId == detail.id }
            if (network == null) {
                // 网络刚被删掉：自己收起来，别停在空页上
                LaunchedEffect(detail.id) { onClose() }
            } else if (embedded) {
                ScreenSurface {
                    NetworkDetailPane(
                        network = network,
                        config = state.configs[network.networkId],
                        onDelete = {
                            viewModel.deleteNetwork(network)
                            onClose()
                        },
                    )
                }
            } else {
                ScreenSurface {
                    NetworkDetailScreen(
                        viewModel = viewModel,
                        network = network,
                        config = state.configs[network.networkId],
                        onBack = onClose,
                    )
                }
            }
        }

        Detail.Moons -> ScreenSurface {
            MoonsScreen(viewModel, onBack = onClose, embedded = embedded)
        }

        Detail.Identities -> ScreenSurface {
            IdentityScreen(onBack = onClose, embedded = embedded)
        }

        Detail.Planet -> ScreenSurface {
            PlanetScreen(onBack = onClose, embedded = embedded)
        }

        Detail.About -> ScreenSurface {
            AboutScreen(
                onBack = onClose,
                onOpenLicenses = { onOpen(Detail.Licenses) },
                embedded = embedded,
            )
        }

        Detail.Licenses -> ScreenSurface {
            LicensesScreen(onBack = onClose, embedded = embedded)
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
