package net.sumitm.zotiera.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.sumitm.zotiera.BuildConfig
import net.sumitm.zotiera.R
import net.sumitm.zotiera.ui.component.AppCard
import net.sumitm.zotiera.ui.component.AppTopBar
import net.sumitm.zotiera.ui.component.appColors
import net.sumitm.zotiera.ui.component.preference.PreferenceArrow
import net.sumitm.zotiera.ui.component.preference.PreferenceGroup
import net.sumitm.zotiera.ui.component.preference.SectionTitle
import net.sumitm.zotiera.ui.theme.LocalZotieraType

private const val REPO_URL = "https://github.com/576576/zotiera"

/** 组件与许可证标识，按字母序排；本项目自己放最前面。 */
private val COMPONENTS = listOf(
    "Zotiera" to "AGPL-3.0-or-later",
    "AndroidX / Jetpack Compose" to "Apache-2.0",
    "Apache Commons IO / Validator" to "Apache-2.0",
    "EventBus" to "Apache-2.0",
    "Kotlin / kotlinx.coroutines" to "Apache-2.0",
    "Material Components for Android" to "Apache-2.0",
    "Miuix" to "Apache-2.0",
    "Room" to "Apache-2.0",
    "ZeroTier One" to "MPL-2.0",
)

@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLicenses: () -> Unit) {
    val context = LocalContext.current
    val type = LocalZotieraType.current
    val colors = appColors()

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = stringResource(R.string.about), onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_about),
                    contentDescription = null,
                    modifier = Modifier.size(88.dp).clip(CircleShape),
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.app_name), style = type.titleLarge, color = colors.onSurface)
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = type.rowSummary,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
            PreferenceGroup {
                PreferenceArrow(
                    title = stringResource(R.string.licenses),
                    summary = stringResource(R.string.licenses_summary),
                    onClick = onOpenLicenses,
                )
                PreferenceArrow(
                    title = stringResource(R.string.open_source_repo),
                    summary = null,
                    onClick = { openUrl(REPO_URL) },
                )
            }

            SectionTitle(stringResource(R.string.disclaimer))
            Text(
                text = stringResource(R.string.disclaimer_body),
                style = type.rowSummary,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val type = LocalZotieraType.current
    val colors = appColors()

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = stringResource(R.string.licenses), onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            AppCard(Modifier.padding(horizontal = 16.dp)) {
                COMPONENTS.forEach { (name, license) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = name,
                            style = type.rowTitle,
                            color = colors.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(text = license, style = type.rowSummary, color = colors.onSurfaceVariant)
                    }
                }
            }
            Text(
                text = stringResource(R.string.licenses_note),
                style = type.rowSummary,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
