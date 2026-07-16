package dev.shusenzheng64.courseaxis.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.shusenzheng64.courseaxis.core.SettingsJellyVisualPolicy
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun SettingsJellyBackdrop(content: @Composable ColumnScope.() -> Unit) {
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
fun SettingsJellyHero() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SettingsJellyVisualPolicy.cardCornerDp.dp),
        color = Color(SettingsJellyVisualPolicy.cardStrongSurfaceArgb),
        border = BorderStroke(
            SettingsJellyVisualPolicy.cardBorderDp.dp,
            Color(SettingsJellyVisualPolicy.highlightArgb),
        ),
        shadowElevation = SettingsJellyVisualPolicy.cardElevationDp.dp,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                text = "设置",
                color = Color(SettingsJellyVisualPolicy.primaryTextArgb),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "调整学期、作息、显示与同步",
                color = Color(SettingsJellyVisualPolicy.secondaryTextArgb),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun SettingsJellyCard(
    icon: ImageVector,
    title: String,
    supporting: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SettingsJellyVisualPolicy.cardCornerDp.dp),
        color = Color(SettingsJellyVisualPolicy.cardSurfaceArgb),
        border = BorderStroke(
            SettingsJellyVisualPolicy.cardBorderDp.dp,
            Color(SettingsJellyVisualPolicy.highlightArgb),
        ),
        shadowElevation = SettingsJellyVisualPolicy.cardElevationDp.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(SettingsJellyVisualPolicy.accentSoftArgb),
                    border = BorderStroke(.6.dp, Color(SettingsJellyVisualPolicy.highlightArgb)),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp).size(22.dp),
                        tint = Color(SettingsJellyVisualPolicy.accentArgb),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color(SettingsJellyVisualPolicy.primaryTextArgb),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = supporting,
                        color = Color(SettingsJellyVisualPolicy.secondaryTextArgb),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            content()
        }
    }
}

@Composable
fun JellyOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    monospace: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(SettingsJellyVisualPolicy.fieldCornerDp.dp),
        textStyle = if (monospace) {
            LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
        } else {
            LocalTextStyle.current
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color(SettingsJellyVisualPolicy.primaryTextArgb),
            unfocusedTextColor = Color(SettingsJellyVisualPolicy.primaryTextArgb),
            focusedContainerColor = Color(SettingsJellyVisualPolicy.fieldSurfaceArgb),
            unfocusedContainerColor = Color(SettingsJellyVisualPolicy.fieldSurfaceArgb),
            disabledContainerColor = Color(SettingsJellyVisualPolicy.fieldSurfaceArgb).copy(alpha = .55f),
            cursorColor = Color(SettingsJellyVisualPolicy.accentArgb),
            focusedBorderColor = Color(SettingsJellyVisualPolicy.accentArgb),
            unfocusedBorderColor = Color(SettingsJellyVisualPolicy.highlightArgb),
            focusedLabelColor = Color(SettingsJellyVisualPolicy.accentArgb),
            unfocusedLabelColor = Color(SettingsJellyVisualPolicy.secondaryTextArgb),
        ),
    )
}

@Composable
fun JellyDropdownField(
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        JellyPickerSurface(
            label = label,
            value = value,
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(SettingsJellyVisualPolicy.cardStrongSurfaceArgb)),
        ) {
            options.distinct().forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option,
                            color = Color(SettingsJellyVisualPolicy.primaryTextArgb),
                            fontWeight = if (option == value) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        onValueChange(option)
                    },
                )
            }
        }
    }
}

@Composable
fun JellyDatePickerField(
    value: LocalDate,
    onValueChange: (LocalDate) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    JellyPickerSurface(
        label = label,
        value = value.toString(),
        modifier = modifier,
        onClick = {
            DatePickerDialog(
                context,
                { _, year, month, day -> onValueChange(LocalDate.of(year, month + 1, day)) },
                value.year,
                value.monthValue - 1,
                value.dayOfMonth,
            ).show()
        },
    )
}

@Composable
fun JellyTimePickerField(
    value: LocalTime,
    onValueChange: (LocalTime) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    JellyPickerSurface(
        label = label,
        value = "%02d:%02d".format(value.hour, value.minute),
        modifier = modifier,
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute -> onValueChange(LocalTime.of(hour, minute)) },
                value.hour,
                value.minute,
                true,
            ).show()
        },
    )
}

@Composable
private fun JellyPickerSurface(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 62.dp),
        shape = RoundedCornerShape(SettingsJellyVisualPolicy.fieldCornerDp.dp),
        color = Color(SettingsJellyVisualPolicy.fieldSurfaceArgb),
        border = BorderStroke(.8.dp, Color(SettingsJellyVisualPolicy.highlightArgb)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    color = Color(SettingsJellyVisualPolicy.secondaryTextArgb),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    value,
                    color = Color(SettingsJellyVisualPolicy.primaryTextArgb),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = Color(SettingsJellyVisualPolicy.accentArgb),
            )
        }
    }
}

@Composable
fun JellyPrimaryButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SettingsJellyVisualPolicy.minimumTouchDp.dp),
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        border = BorderStroke(.8.dp, Color(SettingsJellyVisualPolicy.highlightArgb)),
    ) {
        Box(
            modifier = Modifier.background(
                Brush.horizontalGradient(
                    listOf(
                        Color(SettingsJellyVisualPolicy.primaryButtonStartArgb).copy(alpha = if (enabled) 1f else .42f),
                        Color(SettingsJellyVisualPolicy.primaryButtonEndArgb).copy(alpha = if (enabled) 1f else .42f),
                    ),
                ),
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(SettingsJellyVisualPolicy.primaryTextArgb),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = text,
                    color = Color(SettingsJellyVisualPolicy.primaryTextArgb),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
fun JellySecondaryButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = SettingsJellyVisualPolicy.minimumTouchDp.dp),
        shape = RoundedCornerShape(50),
        border = BorderStroke(.8.dp, Color(SettingsJellyVisualPolicy.highlightArgb)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(SettingsJellyVisualPolicy.secondaryButtonArgb),
            contentColor = Color(SettingsJellyVisualPolicy.primaryTextArgb),
            disabledContainerColor = Color(SettingsJellyVisualPolicy.secondaryButtonArgb).copy(alpha = .35f),
            disabledContentColor = Color(SettingsJellyVisualPolicy.subtleTextArgb),
        ),
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}
