package net.sumitm.zotiera.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import net.sumitm.zotiera.R
import net.sumitm.zotiera.ui.LocalUiMode
import net.sumitm.zotiera.ui.UiMode
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail as MiuixNavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem as MiuixNavigationRailItem
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar

data class NavDestination(
    val route: String,
    val label: String,
    @param:DrawableRes val icon: Int,
)

/**
 * 页面骨架。系统栏留白只在这里让一次。
 *
 * 顶部取状态栏与刘海的**最大值**，不是相加：两个 inset 相加会让刘海机多让出一整个状态栏的高度。
 * 不含输入法，键盘弹出时内容不该被整体顶起。
 */
@Composable
fun AppScaffold(
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixScaffold(
            topBar = topBar,
            bottomBar = bottomBar,
            contentWindowInsets = insets,
            content = content,
        )

        UiMode.Material -> Scaffold(
            topBar = topBar,
            bottomBar = bottomBar,
            contentWindowInsets = insets,
            content = content,
        )
    }
}

@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    action: @Composable () -> Unit = {},
) {
    val tint = when (LocalUiMode.current) {
        UiMode.Miuix -> top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface
        UiMode.Material -> MaterialTheme.colorScheme.onSurface
    }
    val back: @Composable () -> Unit = {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null, tint = tint)
            }
        }
    }
    BarWithoutTopInset {
        when (LocalUiMode.current) {
            UiMode.Miuix -> MiuixTopAppBar(
                title = title,
                navigationIcon = back,
                actions = { action() },
            )

            UiMode.Material -> TopAppBar(
                title = { Text(title) },
                navigationIcon = back,
                actions = { action() },
            )
        }
    }
}

/**
 * 状态栏那一段留白由外层 Scaffold 让出。
 *
 * 顶栏自己也会按 systemBars 顶部 inset 让一次，不在这里标成已消费就会多让一整个状态栏高度。
 */
@Composable
private fun BarWithoutTopInset(content: @Composable () -> Unit) {
    Box(
        Modifier.consumeWindowInsets(
            WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top),
        ),
    ) { content() }
}

/**
 * 顶层页：大标题贴在顶部，内容往上滚时标题收缩成一条，始终停在屏幕顶端。
 *
 * 页面内容自己负责滚动；滚动事件冒泡到顶栏，标题才会跟着收缩。
 */
@Composable
fun AppLargeTitlePage(title: String, content: @Composable () -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> {
            val behavior = MiuixScrollBehavior()
            Column(Modifier.fillMaxSize()) {
                BarWithoutTopInset {
                    MiuixTopAppBar(title = title, scrollBehavior = behavior)
                }
                Box(Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection)) { content() }
            }
        }

        UiMode.Material -> {
            val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
            Column(Modifier.fillMaxSize()) {
                BarWithoutTopInset {
                    // 与 Miuix 大标题栏同高（M3 默认展开高度 152dp）
                    LargeTopAppBar(
                        title = { Text(title) },
                        scrollBehavior = behavior,
                        collapsedHeight = 56.dp,
                        expandedHeight = 104.dp,
                    )
                }
                Box(Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection)) { content() }
            }
        }
    }
}

@Composable
fun AppBottomBar(items: List<NavDestination>, current: String?, onSelect: (String) -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixNavigationBar {
            items.forEach { item ->
                MiuixNavigationBarItem(
                    selected = item.route == current,
                    onClick = { onSelect(item.route) },
                    icon = ImageVector.vectorResource(item.icon),
                    label = item.label,
                )
            }
        }

        UiMode.Material -> NavigationBar {
            items.forEach { item ->
                NavigationBarItem(
                    selected = item.route == current,
                    onClick = { onSelect(item.route) },
                    icon = { Icon(painterResource(item.icon), contentDescription = null) },
                    label = { Text(item.label) },
                )
            }
        }
    }
}

@Composable
fun AppNavigationRail(items: List<NavDestination>, current: String?, onSelect: (String) -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixNavigationRail {
            items.forEach { item ->
                MiuixNavigationRailItem(
                    selected = item.route == current,
                    onClick = { onSelect(item.route) },
                    icon = ImageVector.vectorResource(item.icon),
                    label = item.label,
                )
            }
        }

        UiMode.Material -> NavigationRail {
            items.forEach { item ->
                NavigationRailItem(
                    selected = item.route == current,
                    onClick = { onSelect(item.route) },
                    icon = { Icon(painterResource(item.icon), contentDescription = null) },
                    label = { Text(item.label) },
                )
            }
        }
    }
}

/** 宽屏判定：>=840dp，或矮宽比 >=600dp 的横屏也算双栏。 */
@Composable
fun isWideLayout(): Boolean {
    val info = androidx.compose.ui.platform.LocalWindowInfo.current
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val widthDp = info.containerSize.width / density
    val heightDp = info.containerSize.height / density
    return widthDp >= 840f || (widthDp >= 600f && heightDp / widthDp < 1.2f)
}

/**
 * 目的地的底：铺满且不透明。
 *
 * 页面切换时两个目的地会同时在场，缺了这层底色，两页的文字会叠在一起。
 */
@Composable
fun ScreenSurface(content: @Composable BoxScope.() -> Unit) {
    val color = when (LocalUiMode.current) {
        UiMode.Miuix -> top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.background
        UiMode.Material -> MaterialTheme.colorScheme.background
    }
    Box(modifier = Modifier.fillMaxSize().background(color), content = content)
}
