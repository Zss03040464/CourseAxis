package dev.shusenzheng64.courseaxis.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.shusenzheng64.courseaxis.core.SettingsJellyVisualPolicy

enum class ImportConnectionStatus(
    val label: String,
    val icon: ImageVector,
    val color: Color,
) {
    LOADING("正在连接", Icons.Default.HourglassTop, Color(0xFFB7C7FF)),
    CONNECTED("教务已连接", Icons.Default.CloudDone, Color(0xFF79F2C7)),
    SIGNING_IN("正在自动登录", Icons.Default.Key, Color(0xFF78E9FF)),
    NEED_CREDENTIALS("请保存账号", Icons.Default.Password, Color(0xFFFFD47B)),
    CAPTCHA("请完成验证码", Icons.Default.WarningAmber, Color(0xFFFFC46B)),
    STOPPED("自动登录已暂停", Icons.Default.CloudOff, Color(0xFFFFA39B)),
    FAILED("页面连接失败", Icons.Default.CloudOff, Color(0xFFFF8E9D)),
}

@Composable
fun ImportGlassBackdrop(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(SettingsJellyVisualPolicy.backdropTopArgb),
                        Color(SettingsJellyVisualPolicy.backdropBottomArgb),
                    ),
                ),
            ),
        content = content,
    )
}

@Composable
fun ImportConnectionPanel(
    status: ImportConnectionStatus,
    termSummary: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
        color = Color(SettingsJellyVisualPolicy.cardStrongSurfaceArgb),
        border = BorderStroke(.8.dp, Color(SettingsJellyVisualPolicy.highlightArgb)),
        shadowElevation = 2.dp,
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "教务工作台",
                        color = Color(SettingsJellyVisualPolicy.primaryTextArgb),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        termSummary,
                        color = Color(SettingsJellyVisualPolicy.secondaryTextArgb),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ImportStatusBadge(status)
            }
        }
    }
}

@Composable
private fun ImportStatusBadge(status: ImportConnectionStatus) {
    Row(
        modifier = Modifier
            .background(status.color.copy(alpha = .14f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(status.icon, null, tint = status.color)
        Text(
            status.label,
            color = status.color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun ImportSettingsDisclosure(
    expanded: Boolean,
    accountSummary: String,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(SettingsJellyVisualPolicy.cardSurfaceArgb),
        border = BorderStroke(.7.dp, Color(SettingsJellyVisualPolicy.highlightArgb)),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "账号与导入设置",
                        color = Color(SettingsJellyVisualPolicy.primaryTextArgb),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        accountSummary,
                        color = Color(SettingsJellyVisualPolicy.secondaryTextArgb),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起账号与导入设置" else "展开账号与导入设置",
                    tint = Color(SettingsJellyVisualPolicy.primaryTextArgb),
                )
            }
            if (expanded) {
                Box(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
                }
            }
        }
    }
}
