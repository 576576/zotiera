package net.sumitm.zotiera.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import net.sumitm.zotiera.ui.LocalUiMode
import net.sumitm.zotiera.ui.UiMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 配色模式。AMOLED 不是第四种模式，而是深色下的一个开关
 * （见 [ZotieraTheme] 的 amoled 参数）。
 */
enum class ColorMode(val value: Int) {
    SYSTEM(0), LIGHT(1), DARK(2);

    companion object {
        /** 旧版本存过 3 = AMOLED，这里退回深色，由调用方补上 AMOLED 开关。 */
        const val LEGACY_AMOLED = 3

        fun fromValue(value: Int) = entries.firstOrNull { it.value == value } ?: SYSTEM
    }

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

/** 语义字体。两套风格共用，切换时排版不跳。 */
data class ZotieraType(
    val mono: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        letterSpacing = 0.sp,
        textDirection = TextDirection.Ltr,
    ),
    val monoSmall: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        letterSpacing = 0.sp,
        textDirection = TextDirection.Ltr,
    ),
    val titleLarge: TextStyle = TextStyle(
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    /** 键值行的等宽值：与 rowTitle 同一量级，避免一张卡片里字号忽大忽小。 */
    val monoValue: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
        textDirection = TextDirection.Ltr,
    ),
    val rowTitle: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    /** 设备卡上的节点 ID：比正文大一档 */
    val deviceTitle: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
        textDirection = TextDirection.Ltr,
    ),
    val rowSummary: TextStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
)

val LocalZotieraType = staticCompositionLocalOf { ZotieraType() }

/** 当前是否深色。需要按明暗换色的地方（如角色胶囊）读它，别直接读系统的。 */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** 品牌种子色。 */
val SeedColor = Color(0xFFFF6A00)

/**
 * AMOLED：背景与 surface 压到纯黑，容器只留一点点亮度。
 *
 * 容器也压成纯黑的话，分组卡片和背景完全同色，界面退化为一片黑。
 */
fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF101010),
    surfaceContainerHigh = Color(0xFF151515),
    surfaceContainerHighest = Color(0xFF1A1A1A),
)

@Composable
fun ZotieraTheme(
    uiMode: UiMode,
    colorMode: ColorMode = ColorMode.SYSTEM,
    /** 纯黑背景；只在深色下有意义 */
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = colorMode.isDark(isSystemInDarkTheme())
    val amoledDark = amoled && dark
    // enableEdgeToEdge 只按启动时的明暗定一次系统栏图标颜色；运行时切深色后
    // 图标会留在深色上（黑底黑字），所以每次明暗变化都要重新声明
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.LaunchedEffect(dark) {
        var context: android.content.Context? = view.context
        while (context is android.content.ContextWrapper && context !is android.app.Activity) {
            context = context.baseContext
        }
        val window = (context as? android.app.Activity)?.window ?: return@LaunchedEffect
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(
        LocalUiMode provides uiMode,
        LocalZotieraType provides ZotieraType(),
        LocalDarkTheme provides dark,
    ) {
        when (uiMode) {
            UiMode.Material -> {
                val context = LocalContext.current
                val scheme = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    else -> materialScheme(SeedColor, dark)
                }.let { if (amoledDark) it.amoled() else it }
                MaterialTheme(colorScheme = scheme, content = content)
            }

            UiMode.Miuix -> {
                val controller = remember(dark, amoledDark) {
                    ThemeController(
                        colorSchemeMode = if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light,
                        // AMOLED 要把面压到纯黑；Miuix 没有内置档位，改它自己的暗色板
                        darkColors = if (amoledDark) {
                            top.yukonga.miuix.kmp.theme.darkColorScheme().copy(
                                background = Color.Black,
                                surface = Color.Black,
                                surfaceContainer = Color(0xFF101010),
                                surfaceContainerHigh = Color(0xFF151515),
                                surfaceContainerHighest = Color(0xFF1A1A1A),
                            )
                        } else {
                            top.yukonga.miuix.kmp.theme.darkColorScheme()
                        },
                    )
                }
                MiuixTheme(controller = controller, content = content)
            }
        }
    }
}

/** 无动态取色时的方案：种子色的明度/彩度梯度，避免落到默认蓝。 */
private fun materialScheme(seed: Color, dark: Boolean): ColorScheme {
    fun shift(value: Float, saturation: Float = 0f): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(seed.toArgb(), hsv)
        hsv[2] = (hsv[2] + value).coerceIn(0f, 1f)
        hsv[1] = (hsv[1] + saturation).coerceIn(0f, 1f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    fun neutral(value: Float, saturation: Float = 0.04f): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(seed.toArgb(), hsv)
        return Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], saturation, value)))
    }

    return if (dark) {
        androidx.compose.material3.darkColorScheme(
            primary = shift(0.18f, 0.05f),
            background = neutral(0.07f),
            surface = neutral(0.07f),
            onSurface = neutral(0.92f, 0.02f),
            surfaceContainerLow = neutral(0.11f),
            surfaceContainer = neutral(0.13f),
            surfaceVariant = neutral(0.16f),
            onSurfaceVariant = neutral(0.78f, 0.03f),
        )
    } else {
        androidx.compose.material3.lightColorScheme(
            primary = seed,
            background = neutral(0.99f, 0.02f),
            surface = neutral(0.99f, 0.02f),
            onSurface = neutral(0.13f, 0.05f),
            surfaceContainerLow = neutral(0.96f, 0.03f),
            surfaceContainer = neutral(0.94f, 0.04f),
            surfaceVariant = neutral(0.93f, 0.05f),
            onSurfaceVariant = neutral(0.38f, 0.05f),
        )
    }
}
