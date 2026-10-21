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
}
