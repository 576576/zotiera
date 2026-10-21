package net.sumitm.zotiera

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 页面层不许自己拼控件。
 *
 * 下拉、开关、对话框这些必须走 `ui/component`，否则两套外观（Miuix / Material）
 * 会各自跑偏：风格分支只能待在组件层，页面里出现底层控件就是漏了一层。
 */
class UiConventionsTest {

    private val forbidden = listOf(
        Regex("(?<![A-Za-z])DropdownMenu\\(") to "下拉菜单",
        Regex("(?<![A-Za-z])ExposedDropdownMenuBox\\(") to "下拉菜单",
        Regex("(?<![A-Za-z])AlertDialog\\(") to "对话框",
        Regex("(?<![A-Za-z])Switch\\(") to "开关",
        Regex("(?<![A-Za-z])IconButton\\(") to "图标按钮",
        Regex("Modifier\\.border\\(") to "手绘边框",
    )

    @Test
    fun screensDoNotHandRollControls() {
        val dir = File("src/main/kotlin/net/sumitm/zotiera/ui/screens")
        assertTrue("找不到页面目录：${dir.absolutePath}", dir.isDirectory)

        val offenders = mutableListOf<String>()
        dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                forbidden.forEach { (pattern, what) ->
                    if (pattern.containsMatchIn(text)) {
                        offenders += "${file.name}：直接用了$what（${pattern.pattern}），应改走 ui/component"
                    }
                }
            }

        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
    }

    /**
     * 图标不走 `material-icons-*` 依赖。
     *
     * 这两个库已被上游停止维护，extended 一个包就带进上万个图标类；图标以 vector drawable
     * 放在 `res/drawable`，由 `Icon(painter = ...)` 使用。重新引入依赖会让包体直接涨回去。
     */
    @Test
    fun iconsAreBundledDrawables() {
        val gradle = File("build.gradle")
        assertTrue("找不到 build.gradle", gradle.isFile)
        assertTrue(
            "build.gradle 里不应再出现 material-icons 依赖",
            !gradle.readText().contains("androidx.compose.material:material-icons"),
        )

        val dir = File("src/main/kotlin")
        assertTrue("找不到源码目录：${dir.absolutePath}", dir.isDirectory)
        val offenders = dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("androidx.compose.material.icons") }
            .map { it.name }
            .toList()
        assertTrue("这些文件仍在使用 material-icons：$offenders", offenders.isEmpty())
    }

    /**
     * 不再引入 XML View 时代的那套 UI 库。
     *
     * 界面全是 Compose，`appcompat` / `material` / `recyclerview` 这些只会在包里占位置；
     * `androidx.preference` 唯一用到的 `getDefaultSharedPreferences` 已经由 `util/AppPrefs` 顶替。
     */
    @Test
    fun legacyUiLibrariesStayOut() {
        val gradle = File("build.gradle")
        assertTrue("找不到 build.gradle", gradle.isFile)
        val text = gradle.readText()
        val banned = listOf(
            "androidx.appcompat",
            "com.google.android.material:material",
            "androidx.constraintlayout",
            "androidx.recyclerview",
            "androidx.swiperefreshlayout",
            "androidx.legacy:legacy-support",
            "androidx.preference",
        )
        val found = banned.filter { text.contains(it) }
        assertTrue("build.gradle 里不应再出现这些依赖：$found", found.isEmpty())
    }
}
