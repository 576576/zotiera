package net.sumitm.zotiera.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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
 */
@Composable
fun AppDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
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
                        MiuixText(confirmLabel)
                    }
                }
            }
        }

        UiMode.Material -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Column { content() } },
            confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
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
    val surfaceContainer: Color,
)

@Composable
fun appColors(): AppColors = when (LocalUiMode.current) {
    UiMode.Miuix -> {
        val c = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
        AppColors(c.onSurface, c.onSurfaceVariantSummary, c.primary, c.error, c.surfaceContainer)
    }

    UiMode.Material -> {
        val c = MaterialTheme.colorScheme
        AppColors(c.onSurface, c.onSurfaceVariant, c.primary, c.error, c.surfaceContainerLow)
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
fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> top.yukonga.miuix.kmp.basic.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )

        UiMode.Material -> androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
