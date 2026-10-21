package net.sumitm.zotiera.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.sumitm.zotiera.R
import net.sumitm.zotiera.ui.LocalUiMode
import net.sumitm.zotiera.ui.UiMode
import net.sumitm.zotiera.ui.theme.LocalDarkTheme
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** 状态卡的语气：决定底色与右下角那枚大图标。 */
enum class AppStatusTone { WORKING, CONNECTING, STOPPED, EMPTY }

/** 卡片高度：三行文字上下排开，右下角图标超出边界被裁掉一角。 */
private val CardHeight = 104.dp

private data class StatusStyle(
    val container: Color,
    val icon: Color,
    @param:DrawableRes val glyph: Int,
)

/**
 * 状态卡：左上「状态 + 版本」，左下「实例 · 网络数」，右下角一枚大图标透出卡片边缘。
 *
 * 单击 [onClick] 开关隧道，长按 [onLongClick] 进入设备页。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppStatusCard(
    title: String,
    subtitle: String,
    footer: String?,
    tone: AppStatusTone,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = statusStyle(tone, LocalDarkTheme.current)
    val body: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().height(CardHeight)) {
            Icon(
                painter = painterResource(style.glyph),
                contentDescription = null,
                tint = style.icon,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 27.dp, y = 31.dp)
                    .size(110.dp),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 14.dp, end = 16.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = appColors().onSurface,
                )
                Spacer(Modifier.height(1.dp))
                Text(text = subtitle, fontSize = 15.sp, color = appColors().onSurface)
            }
            if (footer != null) {
                Text(
                    text = footer,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 10.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = appColors().onSurface,
                )
            }
        }
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixCard(
            modifier = modifier,
            insideMargin = PaddingValues(0.dp),
            colors = MiuixCardDefaults.defaultColors(color = style.container),
            onClick = onClick,
            onLongPress = onLongClick,
            pressFeedbackType = PressFeedbackType.Tilt,
            showIndication = true,
        ) { body() }

        UiMode.Material -> Card(
            modifier = modifier,
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = style.container),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            ) { body() }
        }
    }
}

/**
 * 三种开关状态各一套配色：工作中绿、未连接黄、没有网络红。
 *
 * 深色下底色压到同色相的低明度，图标提亮，保证文字仍是浅色压深底。
 */
@Composable
private fun statusStyle(tone: AppStatusTone, dark: Boolean): StatusStyle = when (tone) {
    AppStatusTone.WORKING -> StatusStyle(
        container = if (dark) Color(0xFF1A3825) else Color(0xFFDFFAE4),
        icon = if (dark) Color(0xFF4BE07C) else Color(0xFF36D167),
        glyph = R.drawable.ic_check_circle_outline,
    )

    AppStatusTone.CONNECTING -> StatusStyle(
        container = if (dark) Color(0xFF3A2E12) else Color(0xFFFFF4D1),
        icon = if (dark) Color(0xFFFFC940) else Color(0xFFE0A100),
        glyph = R.drawable.ic_check_circle_outline,
    )

    AppStatusTone.STOPPED -> StatusStyle(
        container = if (dark) Color(0xFF3A2E12) else Color(0xFFFFF4D1),
        icon = if (dark) Color(0xFFFFC940) else Color(0xFFE0A100),
        glyph = R.drawable.ic_remove_circle_outline,
    )

    AppStatusTone.EMPTY -> StatusStyle(
        container = if (dark) Color(0xFF3B1D1D) else Color(0xFFFFE3E3),
        icon = if (dark) Color(0xFFFF6B6B) else Color(0xFFD93A3A),
        glyph = R.drawable.ic_highlight_off,
    )
}
