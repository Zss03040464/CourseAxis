package dev.shusenzheng64.courseaxis.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.shusenzheng64.courseaxis.core.ScheduleEngine
import dev.shusenzheng64.courseaxis.core.ExamFocusPolicy
import dev.shusenzheng64.courseaxis.core.WeekAxisMarker
import dev.shusenzheng64.courseaxis.core.WeekAxisPolicy
import dev.shusenzheng64.courseaxis.core.WeekAxisScale
import dev.shusenzheng64.courseaxis.core.WeekGlassVisualPolicy
import dev.shusenzheng64.courseaxis.core.WeekEventFilter
import dev.shusenzheng64.courseaxis.core.WeekEventMode
import dev.shusenzheng64.courseaxis.core.WeekLayoutPolicy
import dev.shusenzheng64.courseaxis.core.WeekPagerPolicy
import dev.shusenzheng64.courseaxis.core.WeekProjectionEngine
import dev.shusenzheng64.courseaxis.core.WeekTermSwitchPolicy
import dev.shusenzheng64.courseaxis.core.WeekTimelineDensityPolicy
import dev.shusenzheng64.courseaxis.core.ProjectedOccurrence
import dev.shusenzheng64.courseaxis.model.EventKind
import dev.shusenzheng64.courseaxis.model.EventOccurrence
import dev.shusenzheng64.courseaxis.model.ScheduleDocument
import dev.shusenzheng64.courseaxis.model.ScheduleEvent
import dev.shusenzheng64.courseaxis.model.Term
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun WeekScreen(
    document: ScheduleDocument,
    contentPadding: PaddingValues,
    onEdit: (ScheduleEvent) -> Unit,
    onAdd: (LocalDate) -> Unit,
    onEditTimeRule: () -> Unit,
    onActivateTerm: (String) -> Unit,
) {
    val term = document.activeTerm()
    var mode by rememberSaveable { mutableStateOf(WeekEventMode.COURSE) }
    var jumpRequest by remember { mutableStateOf<WeekJumpRequest?>(null) }
    var jumpSequence by remember { mutableStateOf(0) }
    val backdrop = Brush.verticalGradient(
        listOf(
            Color(WeekGlassVisualPolicy.backdropTopArgb),
            Color(WeekGlassVisualPolicy.backdropBottomArgb),
        ),
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(backdrop)
            .padding(contentPadding),
    ) {
        if (term == null) {
            Text(
                "请先导入教务或在设置中建立学期",
                color = Color(WeekGlassVisualPolicy.primaryTextArgb),
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }

        key(term.id) {
            TermWeekPager(
                document = document,
                term = term,
                terms = document.terms,
                onEdit = onEdit,
                onAdd = onAdd,
                onEditTimeRule = onEditTimeRule,
                mode = mode,
                jumpRequest = jumpRequest?.takeIf { it.termId == term.id },
                onTermSelected = { selectedTerm ->
                    jumpRequest = null
                    if (selectedTerm.id != term.id) onActivateTerm(selectedTerm.id)
                },
                onModeChange = { selectedMode ->
                    mode = selectedMode
                    if (selectedMode == WeekEventMode.EXAM) {
                        ExamFocusPolicy.resolve(document, term.id)?.let { focus ->
                            jumpSequence += 1
                            jumpRequest = WeekJumpRequest(focus.termId, focus.weekNumber, jumpSequence)
                        }
                    }
                },
            )
        }
    }
}

private data class WeekJumpRequest(
    val termId: String,
    val weekNumber: Int,
    val sequence: Int,
)

@Composable
private fun TermWeekPager(
    document: ScheduleDocument,
    term: Term,
    terms: List<Term>,
    onEdit: (ScheduleEvent) -> Unit,
    onAdd: (LocalDate) -> Unit,
    onEditTimeRule: () -> Unit,
    mode: WeekEventMode,
    jumpRequest: WeekJumpRequest?,
    onTermSelected: (Term) -> Unit,
    onModeChange: (WeekEventMode) -> Unit,
) {
    val currentWeek = WeekTermSwitchPolicy.initialWeek(term, LocalDate.now())
    val initialPage = WeekPagerPolicy.pageForWeek(
        jumpRequest?.weekNumber ?: currentWeek,
        term.totalWeeks,
    )
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { term.totalWeeks.coerceAtLeast(1) },
    )
    val scope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(jumpRequest?.sequence) {
        jumpRequest?.let { request ->
            pagerState.scrollToPage(
                WeekPagerPolicy.pageForWeek(request.weekNumber, term.totalWeeks),
            )
        }
    }
    val week = WeekPagerPolicy.weekForPage(pagerState.currentPage, term.totalWeeks)
    val projected = remember(document.updatedAt, term.id, week, mode) {
        WeekEventFilter.apply(WeekProjectionEngine.itemsInWeek(week, term, document), mode)
            .groupBy { it.date }
    }
    val monday = requireNotNull(ScheduleEngine.dateFor(term, week, 1))

    Column(Modifier.fillMaxSize()) {
        WeekGlassHeader(
            term = term,
            terms = terms,
            week = week,
            monday = monday,
            itemCount = projected.values.sumOf { it.size },
            mode = mode,
            canGoBack = pagerState.currentPage > 0,
            canGoForward = pagerState.currentPage < term.totalWeeks - 1,
            onBack = {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
            },
            onForward = {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            onAdd = { onAdd(if (week == currentWeek) LocalDate.now() else monday) },
            onTermSelected = onTermSelected,
            onModeChange = onModeChange,
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            beyondViewportPageCount = 0,
            userScrollEnabled = term.totalWeeks > 1,
        ) { page ->
            val pageWeek = WeekPagerPolicy.weekForPage(page, term.totalWeeks)
            val pageProjected = remember(document.updatedAt, term.id, pageWeek, mode) {
                WeekEventFilter.apply(WeekProjectionEngine.itemsInWeek(pageWeek, term, document), mode)
                    .groupBy { it.date }
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                WeekTimeline(
                    document = document,
                    term = term,
                    week = pageWeek,
                    projected = pageProjected,
                    onEdit = onEdit,
                    onAdd = onAdd,
                    onEditTimeRule = onEditTimeRule,
                    totalWidth = maxWidth,
                )
            }
        }
    }
}

@Composable
private fun WeekGlassHeader(
    term: Term,
    terms: List<Term>,
    week: Int,
    monday: LocalDate,
    itemCount: Int,
    mode: WeekEventMode,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onAdd: () -> Unit,
    onTermSelected: (Term) -> Unit,
    onModeChange: (WeekEventMode) -> Unit,
) {
    Surface(
        color = Color(WeekGlassVisualPolicy.headerSurfaceArgb),
        border = BorderStroke(0.7.dp, Color(WeekGlassVisualPolicy.highlightArgb)),
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${monday.year}/${monday.monthValue}/${monday.dayOfMonth}",
                        color = Color(WeekGlassVisualPolicy.primaryTextArgb),
                        fontSize = 22.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "第 $week 周 · $itemCount 项",
                        color = Color(WeekGlassVisualPolicy.secondaryTextArgb),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                GlassIconButton(
                    enabled = canGoBack,
                    contentDescription = "上一周",
                    onClick = onBack,
                ) { Icon(Icons.Default.ChevronLeft, null) }
                GlassIconButton(
                    enabled = canGoForward,
                    contentDescription = "下一周",
                    onClick = onForward,
                ) { Icon(Icons.Default.ChevronRight, null) }
                GlassIconButton(contentDescription = "添加真实时间事件", onClick = onAdd) {
                    Icon(Icons.Default.Add, null)
                }
            }
            WeekCompactControls(
                activeTerm = term,
                terms = terms,
                mode = mode,
                onTermSelected = onTermSelected,
                onModeChange = onModeChange,
            )
        }
    }
}

@Composable
private fun WeekCompactControls(
    activeTerm: Term,
    terms: List<Term>,
    mode: WeekEventMode,
    onTermSelected: (Term) -> Unit,
    onModeChange: (WeekEventMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp, top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeekTermSelector(
            activeTerm = activeTerm,
            terms = terms,
            onTermSelected = onTermSelected,
            modifier = Modifier.weight(1f),
        )
        WeekModeSelector(
            mode = mode,
            onModeChange = onModeChange,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun WeekTermSelector(
    activeTerm: Term,
    terms: List<Term>,
    onTermSelected: (Term) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(activeTerm.id) { mutableStateOf(false) }
    val enabled = terms.size > 1

    Box(
        modifier = modifier.height(WeekGlassVisualPolicy.filterMinimumTouchDp.dp),
    ) {
        Surface(
            onClick = { if (enabled) expanded = true },
            enabled = enabled,
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "切换学期" },
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = .07f),
            border = BorderStroke(.7.dp, Color(WeekGlassVisualPolicy.highlightArgb)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(WeekGlassVisualPolicy.accentArgb),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp),
                ) {
                    Text(
                        "当前学期",
                        color = Color(WeekGlassVisualPolicy.secondaryTextArgb),
                        fontSize = 7.5.sp,
                        lineHeight = 8.sp,
                    )
                    Text(
                        activeTerm.name,
                        color = Color(WeekGlassVisualPolicy.primaryTextArgb),
                        fontSize = 9.5.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (enabled) {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(WeekGlassVisualPolicy.secondaryTextArgb),
                    )
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            terms.forEach { option ->
                val selected = option.id == activeTerm.id
                DropdownMenuItem(
                    modifier = Modifier.heightIn(min = 48.dp),
                    text = {
                        Text(
                            option.name,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (selected) Icons.Default.Check else Icons.Default.CalendarMonth,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        expanded = false
                        onTermSelected(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun WeekModeSelector(
    mode: WeekEventMode,
    onModeChange: (WeekEventMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(WeekGlassVisualPolicy.filterMinimumTouchDp.dp)
            .background(Color.White.copy(alpha = .055f), RoundedCornerShape(14.dp)),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf(
            WeekEventMode.COURSE to "全部课程",
            WeekEventMode.EXAM to "考试",
        ).forEach { (item, label) ->
            val selected = item == mode
            Surface(
                onClick = { onModeChange(item) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = WeekGlassVisualPolicy.filterMinimumTouchDp.dp)
                    .semantics { contentDescription = "显示$label" },
                shape = RoundedCornerShape(12.dp),
                color = if (selected) Color.White.copy(alpha = .16f) else Color.Transparent,
                border = if (selected) BorderStroke(.7.dp, Color(WeekGlassVisualPolicy.highlightArgb)) else null,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        color = if (selected) Color(WeekGlassVisualPolicy.primaryTextArgb)
                        else Color(WeekGlassVisualPolicy.subtleTextArgb),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassIconButton(
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.size(38.dp).semantics { this.contentDescription = contentDescription },
    ) {
        Box(
            Modifier
                .size(31.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color.White.copy(alpha = if (enabled) 0.09f else 0.03f)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides Color.White.copy(
                    alpha = if (enabled) 0.94f else 0.30f,
                ),
                content = content,
            )
        }
    }
}

@Composable
private fun WeekTimeline(
    document: ScheduleDocument,
    term: Term,
    week: Int,
    projected: Map<LocalDate, List<ProjectedOccurrence>>,
    onEdit: (ScheduleEvent) -> Unit,
    onAdd: (LocalDate) -> Unit,
    onEditTimeRule: () -> Unit,
    totalWidth: Dp,
) {
    val metrics = remember(totalWidth) { WeekLayoutPolicy.timelineMetrics(totalWidth.value.toInt()) }
    val gutter = metrics.gutterWidthDp.dp
    val dayWidth = ((totalWidth.value - metrics.gutterWidthDp) / metrics.dayCount).coerceAtLeast(1f).dp
    val timeRule = document.timeRuleFor(term.id)
    val fallbackStart = parseMinutes(document.settings.dayStart) ?: 480
    val fallbackEnd = (parseMinutes(document.settings.dayEnd) ?: 1305).coerceAtLeast(fallbackStart + 60)
    val scale = remember(document.updatedAt, term.id, fallbackStart, fallbackEnd) {
        WeekAxisPolicy.scale(timeRule, fallbackStart, fallbackEnd)
    }
    val start = scale.startMinute
    val end = scale.endMinute
    val axisMarkers = remember(document.updatedAt, term.id, start, end) {
        WeekAxisPolicy.markers(timeRule, start, end)
    }
    val vertical = rememberScrollState()

    Column(Modifier.fillMaxSize()) {
        WeekdayHeader(term, week, gutter, dayWidth)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val slotHeight = WeekLayoutPolicy.sectionSlotHeightDp(
                availableHeightDp = maxHeight.value.toInt(),
                slotCount = scale.totalSlots,
            ).dp
            val timelineHeight = (scale.totalSlots * slotHeight.value).dp
            Row(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(vertical),
            ) {
                TimeGutter(
                    scale = scale,
                    slotHeight = slotHeight,
                    width = gutter,
                    height = timelineHeight,
                    markers = axisMarkers,
                    onEditTimeRule = onEditTimeRule,
                )
                WeekLayoutPolicy.visibleWeekdays().forEach { weekday ->
                    val date = requireNotNull(ScheduleEngine.dateFor(term, week, weekday))
                    DayTimeline(
                        date = date,
                        items = projected[date].orEmpty(),
                        scale = scale,
                        slotHeight = slotHeight,
                        width = dayWidth,
                        height = timelineHeight,
                        markers = axisMarkers,
                        onEdit = onEdit,
                        onAdd = onAdd,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader(term: Term, week: Int, gutter: Dp, dayWidth: Dp) {
    val monday = requireNotNull(ScheduleEngine.dateFor(term, week, 1))
    Surface(
        color = Color(WeekGlassVisualPolicy.headerSurfaceArgb).copy(alpha = 0.72f),
        border = BorderStroke(0.5.dp, Color(WeekGlassVisualPolicy.highlightArgb)),
        tonalElevation = 0.dp,
    ) {
        Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.width(gutter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "${monday.monthValue}",
                    color = Color(WeekGlassVisualPolicy.primaryTextArgb),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "月",
                    color = Color(WeekGlassVisualPolicy.subtleTextArgb),
                    fontSize = 9.sp,
                )
            }
            WeekLayoutPolicy.visibleWeekdays().forEach { weekday ->
                val date = requireNotNull(ScheduleEngine.dateFor(term, week, weekday))
                val isToday = date == LocalDate.now()
                Column(
                    Modifier.width(dayWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "一二三四五六日"[weekday - 1].toString(),
                        color = if (isToday) Color(WeekGlassVisualPolicy.accentArgb)
                        else Color(WeekGlassVisualPolicy.secondaryTextArgb),
                        fontSize = 11.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    )
                    Text(
                        date.format(DateTimeFormatter.ofPattern("M/d")),
                        color = if (isToday) Color(WeekGlassVisualPolicy.accentArgb)
                        else Color(WeekGlassVisualPolicy.subtleTextArgb),
                        fontSize = 8.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeGutter(
    scale: WeekAxisScale,
    slotHeight: Dp,
    width: Dp,
    height: Dp,
    markers: List<WeekAxisMarker>,
    onEditTimeRule: () -> Unit,
) {
    Box(
        Modifier
            .width(width)
            .height(height)
            .background(Color(WeekGlassVisualPolicy.headerSurfaceArgb).copy(alpha = 0.36f))
            .semantics { contentDescription = "调整当前学期真实作息" }
            .combinedClickable(onClick = onEditTimeRule, onLongClick = onEditTimeRule),
    ) {
        markers.forEach { marker ->
            val y = WeekAxisPolicy.markerTopDp(
                positionSlots = scale.positionSlots(marker.anchorMinute),
                slotHeightDp = slotHeight.value,
            ).dp
            Column(
                Modifier
                    .absoluteOffset(y = y)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                marker.section?.let { section ->
                    Text(
                        section.toString(),
                        color = Color(WeekGlassVisualPolicy.primaryTextArgb),
                        fontSize = 11.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    marker.startText,
                    color = Color(WeekGlassVisualPolicy.secondaryTextArgb),
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    textAlign = TextAlign.Center,
                )
                marker.endText?.takeIf {
                    WeekTimelineDensityPolicy.showEndTime(slotHeight.value)
                }?.let { endText ->
                    Text(
                        endText,
                        color = Color(WeekGlassVisualPolicy.subtleTextArgb),
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayTimeline(
    date: LocalDate,
    items: List<ProjectedOccurrence>,
    scale: WeekAxisScale,
    slotHeight: Dp,
    width: Dp,
    height: Dp,
    markers: List<WeekAxisMarker>,
    onEdit: (ScheduleEvent) -> Unit,
    onAdd: (LocalDate) -> Unit,
) {
    val placements = remember(items) {
        ScheduleEngine.assignLanes(items.map { EventOccurrence(it.event, it.date, it.start, it.end) })
    }
    Box(
        Modifier
            .width(width)
            .height(height)
            .border(0.35.dp, Color(WeekGlassVisualPolicy.gridArgb))
            .combinedClickable(onClick = { onAdd(date) }, onLongClick = { onAdd(date) }),
    ) {
        markers.forEach { marker ->
            HorizontalDivider(
                Modifier.absoluteOffset(
                    y = (scale.positionSlots(marker.anchorMinute) * slotHeight.value).dp,
                ),
                thickness = 0.45.dp,
                color = Color(WeekGlassVisualPolicy.gridArgb),
            )
        }
        val now = LocalTime.now()
        val nowMinute = now.hour * 60 + now.minute
        if (date == LocalDate.now() && nowMinute in scale.startMinute..scale.endMinute) {
            HorizontalDivider(
                Modifier.absoluteOffset(
                    y = (scale.positionSlots(nowMinute) * slotHeight.value).dp,
                ),
                thickness = 1.5.dp,
                color = Color(WeekGlassVisualPolicy.accentArgb),
            )
        }
        placements.forEach { placement ->
            val occurrence = placement.occurrence
            val projected = items.first { it.event.id == occurrence.event.id }
            val rawStart = occurrence.start.hour * 60 + occurrence.start.minute
            val rawEnd = occurrence.end.hour * 60 + occurrence.end.minute
            val clippedStart = rawStart.coerceAtLeast(scale.startMinute)
            val clippedEnd = rawEnd.coerceAtMost(scale.endMinute)
            if (clippedEnd > clippedStart) {
                val laneWidth = (width - 3.dp) / placement.laneCount
                val x = 1.5.dp + laneWidth * placement.laneIndex
                val mappedStart = scale.positionSlots(clippedStart)
                val mappedEnd = scale.positionSlots(clippedEnd)
                val blockHeight = ((mappedEnd - mappedStart) * slotHeight.value).dp.coerceAtLeast(28.dp)
                CourseBlock(
                    event = occurrence.event,
                    startTime = occurrence.start,
                    endTime = occurrence.end,
                    occursThisWeek = projected.occursThisWeek,
                    blockHeightDp = blockHeight.value,
                    modifier = Modifier
                        .absoluteOffset(
                            x = x,
                            y = (mappedStart * slotHeight.value).dp,
                        )
                        .width(laneWidth - 1.5.dp)
                        .height(blockHeight),
                    onClick = { onEdit(occurrence.event) },
                )
            }
        }
    }
}

@Composable
private fun CourseBlock(
    event: ScheduleEvent,
    startTime: LocalTime,
    endTime: LocalTime,
    occursThisWeek: Boolean,
    blockHeightDp: Float,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val isExam = event.kind == EventKind.EXAM
    val blockColor = if (isExam) {
        Color(WeekGlassVisualPolicy.examSurfaceArgb)
    } else {
        eventColor(event.colorKey).copy(alpha = 0.77f)
    }
    val title = if (occursThisWeek) event.title else "[非本周] ${event.title}"
    val density = WeekTimelineDensityPolicy.courseCard(blockHeightDp, isExam)
    val compactWeeks = if (event.weeks.isEmpty()) "" else {
        val min = event.weeks.minOrNull()
        val max = event.weeks.maxOrNull()
        if (min == max) "${min}周" else "${min}-${max}周"
    }
    Box(
        modifier
            .alpha(if (occursThisWeek) 1f else 0.43f)
            .clip(RoundedCornerShape(6.dp))
            .background(blockColor)
            .border(0.65.dp, Color.White.copy(alpha = 0.36f), RoundedCornerShape(6.dp))
            .semantics {
                contentDescription = buildString {
                    if (isExam) append("考试，")
                    append(event.title)
                    append("，$startTime 至 $endTime")
                    if (event.location.isNotBlank()) append("，${event.location}")
                    append("，点击编辑")
                }
            }
            .combinedClickable(onClick = onClick, onLongClick = onClick)
            .padding(horizontal = 1.5.dp, vertical = 2.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (isExam) {
                Text(
                    "考试",
                    color = Color.White.copy(alpha = .92f),
                    fontSize = 6.8.sp,
                    lineHeight = 7.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                title,
                maxLines = density.titleMaxLines,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
                fontSize = 8.2.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.Bold,
            )
            if (density.showTime) {
                Text(
                    "$startTime–$endTime",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    color = Color.White.copy(alpha = 0.96f),
                    fontSize = 6.sp,
                    lineHeight = 7.sp,
                )
            }
            if (density.showLocation && event.location.isNotBlank()) {
                Text(
                    event.location,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 6.8.sp,
                    lineHeight = 7.5.sp,
                )
            }
            if (density.showTeacher && event.teacher.isNotBlank()) {
                Text(
                    event.teacher,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 6.8.sp,
                    lineHeight = 7.5.sp,
                )
            }
            if (density.showWeeks && compactWeeks.isNotBlank()) {
                Text(
                    compactWeeks,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 6.8.sp,
                    lineHeight = 7.5.sp,
                )
            }
        }
    }
}

@Composable
private fun eventColor(key: Int): Color {
    val colors = listOf(
        Color(0xFFED7D9B),
        Color(0xFF55B8E8),
        Color(0xFF6B9CF0),
        Color(0xFF66D7C0),
        Color(0xFFEBAF55),
        Color(0xFFF08369),
        Color(0xFF8F7DE8),
        Color(0xFFD67AB1),
        Color(0xFF4EA3D7),
        Color(0xFF58B89B),
        Color(0xFFD99E58),
        Color(0xFFB57CDA),
    )
    return colors[(key and Int.MAX_VALUE) % colors.size]
}

private fun parseMinutes(value: String): Int? {
    val match = Regex("""^([01]?\d|2[0-3]):([0-5]\d)$""").find(value) ?: return null
    return match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()
}
