package net.sumitm.zotiera.ui.component.preference

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import net.sumitm.zotiera.R
import net.sumitm.zotiera.ui.LocalUiMode
import net.sumitm.zotiera.ui.UiMode
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * 设置分组。Miuix 用其 Card，Material 用 Surface + ListItem 列表。
 *
 * 屏内不允许出现风格分支，分支只在这一层。
 */
@Composable
fun PreferenceGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixCard(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(content = content)
        }

        UiMode.Material -> Surface(
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun PreferenceSwitch(
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> SwitchPreference(
            title = title,
            summary = summary ?: "",
            checked = checked,
            onCheckedChange = onCheckedChange,
        )

        UiMode.Material -> ListItem(
            headlineContent = { Text(title) },
            supportingContent = summary?.let { { Text(it) } },
            trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { onCheckedChange(!checked) },
        )
    }
}

@Composable
fun PreferenceArrow(
    title: String,
    summary: String?,
    onClick: () -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> ArrowPreference(title = title, summary = summary ?: "", onClick = onClick)

        UiMode.Material -> ListItem(
            headlineContent = { Text(title) },
            supportingContent = summary?.let { { Text(it) } },
            trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}

@Composable
fun PreferenceSelect(
    title: String,
    summary: String?,
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> OverlayDropdownPreference(
            title = title,
            summary = summary ?: "",
            items = items,
            selectedIndex = selectedIndex,
            onSelectedIndexChange = onSelected,
        )

        UiMode.Material -> {
            // 用 ExposedDropdownMenu：锚在行上、与行同宽、可滚动到当前项，
            // 裸露的 DropdownMenu 会飘在卡片左上角，跟 Miuix 那套不是一个东西
            var expanded by remember { mutableStateOf(false) }
            androidx.compose.material3.ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                ListItem(
                    headlineContent = { Text(title) },
                    supportingContent = summary?.let { { Text(it) } },
                    trailingContent = {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = items.getOrElse(selectedIndex) { "" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = expanded,
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.menuAnchor(
                        androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    ),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    items.forEachIndexed { index, item ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = {
                                Text(
                                    text = item,
                                    color = if (index == selectedIndex) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            },
                            trailingIcon = {
                                if (index == selectedIndex) {
                                    Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                                }
                            },
                            onClick = {
                                expanded = false
                                onSelected(index)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val color = when (LocalUiMode.current) {
        UiMode.Miuix -> top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary
        UiMode.Material -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier.fillMaxWidth().padding(start = 32.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
    )
}

/** 分组内带图标的标题行（用于需要额外说明的设置项）。 */
@Composable
fun PreferenceIcon(@DrawableRes icon: Int, tint: androidx.compose.ui.graphics.Color) {
    Box(Modifier, contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = tint)
    }
}

@Composable
fun PreferenceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> {
            val state = androidx.compose.foundation.text.input.rememberTextFieldState(value)
            androidx.compose.runtime.LaunchedEffect(state.text) {
                onValueChange(state.text.toString())
            }
            top.yukonga.miuix.kmp.basic.TextField(
                state = state,
                label = label,
                modifier = modifier.fillMaxWidth(),
            )
        }

        UiMode.Material -> androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = modifier.fillMaxWidth(),
        )
    }
}
