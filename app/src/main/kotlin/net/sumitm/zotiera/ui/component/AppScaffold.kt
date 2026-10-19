package net.sumitm.zotiera.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import net.sumitm.zotiera.ui.LocalUiMode
import net.sumitm.zotiera.ui.UiMode
import net.sumitm.zotiera.ui.theme.LocalZotieraType
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail as MiuixNavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem as MiuixNavigationRailItem
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar

data class NavDestination(val route: String, val label: String, val icon: ImageVector)

@Composable
fun AppScaffold(
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixScaffold(topBar = topBar, bottomBar = bottomBar, content = content)
        UiMode.Material -> Scaffold(topBar = topBar, bottomBar = bottomBar, content = content)
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
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = tint)
            }
        }
    }
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

/**
 * 页面大标题：标题 + 一行说明。
 *
 * 这是 Material 与 Miuix 共用的排版，字号来自 [LocalZotieraType]，切换风格不跳。
 */
@Composable
fun AppHeader(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)) {
        val type = LocalZotieraType.current
        val color = when (LocalUiMode.current) {
            UiMode.Miuix -> top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface
            UiMode.Material -> MaterialTheme.colorScheme.onSurface
        }
        val subColor = when (LocalUiMode.current) {
            UiMode.Miuix -> top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary
            UiMode.Material -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(text = title, style = type.titleLarge, color = color)
        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                style = type.rowSummary,
                color = subColor,
                modifier = Modifier.padding(top = 2.dp),
            )
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
                    icon = item.icon,
                    label = item.label,
                )
            }
        }

        UiMode.Material -> NavigationBar {
            items.forEach { item ->
                NavigationBarItem(
                    selected = item.route == current,
                    onClick = { onSelect(item.route) },
                    icon = { Icon(item.icon, contentDescription = null) },
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
                    icon = item.icon,
                    label = item.label,
                )
            }
        }

        UiMode.Material -> NavigationRail {
            items.forEach { item ->
                NavigationRailItem(
                    selected = item.route == current,
                    onClick = { onSelect(item.route) },
                    icon = { Icon(item.icon, contentDescription = null) },
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
