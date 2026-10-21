package net.sumitm.zotiera.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.sumitm.zotiera.ui.LocalUiMode
import net.sumitm.zotiera.ui.UiMode
import net.sumitm.zotiera.ui.theme.LocalZotieraType
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.overlay.OverlayDialog

@Composable
fun AppButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            MiuixText(label)
        }

        UiMode.Material -> Button(onClick = onClick, modifier = modifier, enabled = enabled) {
            Text(label)
        }
    }
}

/**
 * 对话框。Miuix 用 OverlayDialog，Material 用 AlertDialog——各自用自己那套，
 * 不混用，否则 Miuix 下会没有 MaterialTheme 而退化成黑块。
 *
 * [destructive] 把确认按钮的字染成错误色，用于删除这类不可撤销的操作。
 */
@Composable
fun AppDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = null,
    destructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = appColors()
    when (LocalUiMode.current) {
        UiMode.Miuix -> OverlayDialog(show = true, title = title, onDismissRequest = onDismiss) {
            Column {
                content()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (dismissLabel != null) {
                        MiuixButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            MiuixText(dismissLabel)
                        }
                    }
                    MiuixButton(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                        if (destructive) {
                            MiuixText(confirmLabel, color = colors.error)
                        } else {
                            MiuixText(confirmLabel)
                        }
                    }
                }
            }
        }

        UiMode.Material -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Column { content() } },
            confirmButton = {
                TextButton(
                    onClick = onConfirm,
                    colors = if (destructive) {
                        androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = colors.error)
                    } else {
                        androidx.compose.material3.ButtonDefaults.textButtonColors()
                    },
                ) { Text(confirmLabel) }
            },
            dismissButton = dismissLabel?.let { { TextButton(onClick = onDismiss) { Text(it) } } },
        )
    }
}

/** 列表行：图标 + 标题 + 副标题 + 尾部控件。 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppListRow(
    title: String,
    subtitle: String? = null,
    monoSubtitle: Boolean = false,
    leading: ImageVector? = null,
    leadingTint: Color = Color.Unspecified,
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val type = LocalZotieraType.current
    when (LocalUiMode.current) {
        UiMode.Miuix -> {
            val scheme = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
            MiuixCard(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leading != null) {
                        MiuixIcon(
                            imageVector = leading,
                            contentDescription = null,
                            tint = if (leadingTint == Color.Unspecified) scheme.onSurfaceVariantSummary else leadingTint,
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        MiuixText(title, style = type.rowTitle, color = scheme.onSurface)
                        if (!subtitle.isNullOrEmpty()) {
                            MiuixText(
                                text = subtitle,
                                style = if (monoSubtitle) type.monoSmall else type.rowSummary,
                                color = scheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    trailing()
                }
            }
        }

        UiMode.Material -> androidx.compose.material3.Surface(
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
        ListItem(
            colors = androidx.compose.material3.ListItemDefaults.colors(
                containerColor = Color.Transparent,
            ),
            headlineContent = {
                Text(title, style = type.rowTitle)
            },
            supportingContent = subtitle?.let { text ->
                {
                    Text(
                        text = text,
                        style = if (monoSubtitle) type.monoSmall else type.rowSummary,
                    )
                }
            },
            leadingContent = leading?.let { icon ->
                {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (leadingTint == Color.Unspecified) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            leadingTint
                        },
                    )
                }
            },
            trailingContent = { trailing() },
            modifier = Modifier.combinedClickable(
                onClick = { onClick?.invoke() },
                onLongClick = onLongClick,
            ),
        )
        }
    }
}

/** 卡片容器（详情页的分组）。 */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixCard(modifier.fillMaxWidth()) { Column(content = content) }
        UiMode.Material -> androidx.compose.material3.Surface(
            modifier = modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) { Column(content = content) }
    }
}

/** 语义色。屏内取色只经过这里，不直接摸 Material/Miuix 主题。 */
data class AppColors(
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val error: Color,
    /** 压在 [error] 实心块上的字色 */
    val onError: Color,
    val surfaceContainer: Color,
)

@Composable
fun appColors(): AppColors = when (LocalUiMode.current) {
    UiMode.Miuix -> {
        val c = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
        AppColors(c.onSurface, c.onSurfaceVariantSummary, c.primary, c.error, c.onError, c.surfaceContainer)
    }

    UiMode.Material -> {
        val c = MaterialTheme.colorScheme
        AppColors(c.onSurface, c.onSurfaceVariant, c.primary, c.error, c.onError, c.surfaceContainerLow)
    }
}

/**
 * 胶囊标签：小号圆角铭牌，放列表行尾部。
 *
 * 底色由 [tint] 按 16% 透明算出来，两种风格长得一样，也不用去凑各自的主题色板。
 */
@Composable
fun AppRoleChip(label: String, tint: Color, modifier: Modifier = Modifier) {
    val type = LocalZotieraType.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(tint.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = type.rowSummary.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            color = tint,
        )
    }
}

/**
 * 危险操作块：和上面那些卡片同宽同圆角，整块实心红底。
 *
 * 圆角取各自风格卡片的值，避免和相邻卡片对不齐。
 */
@Composable
fun AppDangerCard(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    val type = LocalZotieraType.current
    val shape = when (LocalUiMode.current) {
        // Miuix 卡片默认 16dp，Material 那套 AppCard 用 20dp
        UiMode.Miuix -> RoundedCornerShape(16.dp)
        UiMode.Material -> RoundedCornerShape(20.dp)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.error)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.onError, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = type.rowTitle, color = colors.onError, fontWeight = FontWeight.Medium)
    }
}

/** 键值行：详情页用。 */
@Composable
fun AppKeyValueRow(label: String, value: String, mono: Boolean = false) {
    val colors = appColors()
    val type = LocalZotieraType.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = type.rowSummary, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = if (mono) type.monoValue else type.rowTitle,
            color = colors.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.5f),
        )
    }
}

/** 浮动按钮。 */
@Composable
fun AppFab(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixButton(onClick = onClick, modifier = modifier) {
            MiuixIcon(imageVector = icon, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            MiuixText(label)
        }

        UiMode.Material -> androidx.compose.material3.ExtendedFloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            icon = { Icon(icon, contentDescription = null) },
            text = { Text(label) },
        )
    }
}

/** 开关。 */
@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> top.yukonga.miuix.kmp.basic.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )

        UiMode.Material -> androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}


/** 隧道开关的绿色：与 Miuix 默认主色区分开，只用于「已连接」。 */
private val TunnelGreen = Color(0xFF34C759)

/**
 * 设备卡上的隧道开关：整条轨道变绿，轨道左侧留一个对勾。
 *
 * 框架自带的 Switch 只有滑块、没有轨道上的对勾（Miuix 的 Switch 同样没有），
 * 这里按同一套尺寸自己画，点击与无障碍语义仍走 toggleable。
 */
@Composable
fun AppTunnelSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    val trackWidth = 58.dp
    val trackHeight = 34.dp
    val knobSize = 28.dp
    val knobOffset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (checked) trackWidth - knobSize - 3.dp else 3.dp,
        label = "tunnelKnob",
    )
    Box(
        modifier = modifier
            .size(trackWidth, trackHeight)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(percent = 50))
            .background(if (checked) TunnelGreen else colors.onSurfaceVariant.copy(alpha = 0.28f))
            .toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onCheckedChange),
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 7.dp)
                    .size(17.dp),
            )
        }
        Box(
            Modifier
                .offset(x = knobOffset, y = 3.dp)
                .size(knobSize)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (checked) Color.White else Color.White),
        )
    }
}

/** 对话框里的一行可点操作，三个管理页共用。 */
@Composable
fun AppDialogAction(label: String, error: Boolean = false, onClick: () -> Unit) {
    Text(
        text = label,
        style = LocalZotieraType.current.rowTitle,
        color = if (error) appColors().error else appColors().onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

/** 行星与身份各用一谱系，两边颜色不会撞；同一序号永远同一个颜色。 */
private val PlanetTints = listOf(
    Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFFEF4444),
)
private val IdentityTints = listOf(
    Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF0D9488), Color(0xFF84CC16),
)

fun planetChipTint(ordinal: Int): Color = PlanetTints[Math.floorMod(ordinal, PlanetTints.size)]

fun identityChipTint(ordinal: Int): Color = IdentityTints[Math.floorMod(ordinal, IdentityTints.size)]
