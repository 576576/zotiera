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
 * 配色模式。深色只有一种画法：底色纯黑、容器略微抬起。
 */
enum class ColorMode(val value: Int) {
    SYSTEM(0), LIGHT(1), DARK(2);

    companion object {
        /** 旧版本存过 3 = AMOLED，按深色处理。 */
        const val LEGACY_AMOLED = 3

        /** 旧值 3 = AMOLED，按深色读回来，别让老用户掉回「跟随系统」。 */
        fun fromValue(value: Int): ColorMode =
            if (value == LEGACY_AMOLED) DARK else entries.firstOrNull { it.value == value } ?: SYSTEM
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
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Bold,
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
 * 深色配色：底色接近黑但不纯黑，卡片与弹窗抬到 KernelSU 那一档。
 *
 * 早先压到纯黑 + 最深容器，卡片和弹窗看着像糊在一起；这里把容器整体提亮，
 * 层级仍是「底色 < 卡片 < 弹窗」。
 */
fun ColorScheme.blackDark(): ColorScheme = copy(
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
    surfaceContainerLowest = Color(0xFF161616),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF1E1E1E),
    surfaceContainerHigh = Color(0xFF232323),
    surfaceContainerHighest = Color(0xFF282828),
)

@Composable
fun ZotieraTheme(
    uiMode: UiMode,
    colorMode: ColorMode = ColorMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = colorMode.isDark(isSystemInDarkTheme())
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
                }.let { if (dark) it.blackDark() else it }
                MaterialTheme(colorScheme = scheme, content = content)
            }

            UiMode.Miuix -> {
                val controller = remember(dark) {
                    ThemeController(
                        colorSchemeMode = if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light,
                        // Miuix 自带的暗色板里卡片与背景几乎同色，深色统一换成这套
                        darkColors = top.yukonga.miuix.kmp.theme.darkColorScheme().let {
                            it.copy(
                                background = Color(0xFF121212),
                                surface = Color(0xFF121212),
                                surfaceContainer = Color(0xFF1E1E1E),
                                surfaceContainerHigh = Color(0xFF232323),
                                surfaceContainerHighest = Color(0xFF282828),
                            )
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
            background = Color(0xFF121212),
            surface = Color(0xFF121212),
            onSurface = neutral(0.92f, 0.02f),
            surfaceContainerLow = Color(0xFF1A1A1A),
            surfaceContainer = Color(0xFF1E1E1E),
            surfaceVariant = Color(0xFF232323),
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
