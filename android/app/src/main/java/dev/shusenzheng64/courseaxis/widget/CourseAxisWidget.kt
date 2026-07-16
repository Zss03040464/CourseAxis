package dev.shusenzheng64.courseaxis.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.background
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.Alignment
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.shusenzheng64.courseaxis.MainActivity
import dev.shusenzheng64.courseaxis.R
import dev.shusenzheng64.courseaxis.core.CoursePresentation
import dev.shusenzheng64.courseaxis.core.ScheduleEngine
import dev.shusenzheng64.courseaxis.data.ScheduleStorage
import dev.shusenzheng64.courseaxis.model.EventOccurrence
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class CourseAxisWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = WidgetModel.load(context)
        val openApp = actionStartActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_INITIAL_TAB, MainActivity.ROUTE_WEEK),
        )
        provideContent { WidgetContent(model, openApp) }
    }
}

class CourseAxisWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CourseAxisWidget()
}

private data class WidgetAgendaItem(
    val day: String,
    val time: String,
    val title: String,
    val details: String,
)

private data class WidgetModel(
    val headlineOverride: String?,
    val requestedCount: Int,
    val items: List<WidgetAgendaItem>,
) {
    companion object {
        fun load(context: Context): WidgetModel {
            val document = ScheduleStorage.read(context)
            val today = LocalDate.now()
            val now = LocalTime.now()
            val requested = document.settings.widgetUpcomingCount.coerceIn(1, 5)
            val current = ScheduleEngine.occurrencesOn(document, today)
                .firstOrNull { it.start <= now && now < it.end }
            val upcoming = ScheduleEngine.nextOccurrences(
                document = document,
                nowDate = today,
                nowTime = now,
                limit = requested,
            )

            val occurrences = buildList {
                if (current != null) add(current)
                addAll(upcoming)
            }.distinctBy { it.event.id to it.date }
                .take(requested)
            val headlineOverride = current?.let {
                "正在上课 · 还剩 ${Duration.between(now, it.end).toMinutes().coerceAtLeast(0)} 分钟"
            } ?: if (occurrences.isEmpty()) {
                "暂无已解析安排"
            } else {
                null
            }
            return WidgetModel(
                headlineOverride = headlineOverride,
                requestedCount = requested,
                items = occurrences.map { it.toWidgetItem(today, current?.event?.id == it.event.id) },
            )
        }

        private fun EventOccurrence.toWidgetItem(today: LocalDate, isCurrent: Boolean): WidgetAgendaItem {
            val day = when {
                isCurrent -> "现在"
                date == today -> "今天"
                date == today.plusDays(1) -> "明天"
                else -> date.format(DateTimeFormatter.ofPattern("M/d E", Locale.CHINA))
            }
            return WidgetAgendaItem(
                day = day,
                time = "$start–$end",
                title = event.title,
                details = listOf(
                    event.location,
                    event.teacher,
                    CoursePresentation.compactWeekRange(event),
                )
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
            )
        }
    }
}

@Composable
private fun WidgetContent(model: WidgetModel, openApp: Action) {
    val size = LocalSize.current
    val capacity = WidgetAgendaPolicy.capacity(
        widthDp = size.width.value.toInt(),
        heightDp = size.height.value.toInt(),
        requested = model.requestedCount,
    )
    val visibleItems = model.items.take(capacity)
    val headline = model.headlineOverride ?: "接下来 ${visibleItems.size} 项"
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_glass_background))
            .cornerRadius(24.dp)
            .appWidgetBackground()
            .padding(14.dp)
            .clickable(openApp),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(GlanceModifier.fillMaxSize()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = GlanceModifier.size(23.dp),
                )
                Spacer(GlanceModifier.width(7.dp))
                Text(
                    text = headline,
                    maxLines = 1,
                    style = TextStyle(
                        color = primaryColor(),
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                    ),
                )
            }
            Spacer(GlanceModifier.height(8.dp))

            if (model.items.isEmpty()) {
                Text(
                    text = "打开课轴导入课程，或检查尚未解析真实时间的项目。",
                    maxLines = 2,
                    style = TextStyle(
                        color = secondaryColor(),
                        fontSize = 12.sp,
                    ),
                )
            } else {
                Column(GlanceModifier.fillMaxWidth()) {
                    visibleItems.forEachIndexed { index, item ->
                        AgendaEntry(
                            item = item,
                            showDivider = index > 0,
                            showBottomSpacing = index < visibleItems.lastIndex,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaEntry(
    item: WidgetAgendaItem,
    showDivider: Boolean,
    showBottomSpacing: Boolean,
) {
    Column(GlanceModifier.fillMaxWidth()) {
        if (showDivider) {
            Spacer(GlanceModifier.height(4.dp))
        }
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ImageProvider(R.drawable.widget_glass_row))
                .cornerRadius(12.dp)
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            AgendaRow(item)
        }
        if (showBottomSpacing) Spacer(GlanceModifier.height(2.dp))
    }
}

@Composable
private fun AgendaRow(item: WidgetAgendaItem) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(GlanceModifier.width(68.dp)) {
            Text(
                text = item.day,
                maxLines = 1,
                style = TextStyle(
                    color = accentColor(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                ),
            )
            Text(
                text = item.time,
                maxLines = 1,
                style = TextStyle(
                    color = secondaryColor(),
                    fontSize = 10.sp,
                ),
            )
        }
        Spacer(GlanceModifier.width(6.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                text = item.title,
                maxLines = 1,
                style = TextStyle(
                    color = primaryColor(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                ),
            )
            if (item.details.isNotBlank()) {
                Text(
                    text = item.details,
                    maxLines = 1,
                    style = TextStyle(
                        color = secondaryColor(),
                        fontSize = 10.sp,
                    ),
                )
            }
        }
    }
}

private fun primaryColor() = ColorProvider(
    Color(WidgetGlassVisualPolicy.primaryTextArgb),
    Color(WidgetGlassVisualPolicy.primaryTextArgb),
)

private fun secondaryColor() = ColorProvider(
    Color(WidgetGlassVisualPolicy.secondaryTextArgb),
    Color(WidgetGlassVisualPolicy.secondaryTextArgb),
)

private fun accentColor() = ColorProvider(
    Color(WidgetGlassVisualPolicy.accentArgb),
    Color(WidgetGlassVisualPolicy.accentArgb),
)
