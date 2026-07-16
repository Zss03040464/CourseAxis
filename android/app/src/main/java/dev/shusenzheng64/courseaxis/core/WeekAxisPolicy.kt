package dev.shusenzheng64.courseaxis.core

import dev.shusenzheng64.courseaxis.model.TimeRule
import kotlin.math.max

data class WeekAxisMarker(
    val section: Int?,
    val anchorMinute: Int,
    val startText: String,
    val endText: String?,
)

data class WeekAxisSpan(
    val section: Int,
    val startMinute: Int,
    val endMinute: Int,
    val slotIndex: Int,
)

data class WeekAxisScale(
    val spans: List<WeekAxisSpan>,
    val startMinute: Int,
    val endMinute: Int,
    val totalSlots: Float,
) {
    fun positionSlots(minute: Int): Float {
        if (spans.isEmpty()) {
            val duration = (endMinute - startMinute).coerceAtLeast(1)
            return ((minute.coerceIn(startMinute, endMinute) - startMinute).toFloat() / duration) * totalSlots
        }
        if (minute <= spans.first().startMinute) return 0f
        spans.forEachIndexed { index, span ->
            if (minute <= span.endMinute) {
                val fraction = (minute - span.startMinute).toFloat() /
                    (span.endMinute - span.startMinute).coerceAtLeast(1)
                return span.slotIndex + fraction.coerceIn(0f, 1f)
            }
            val next = spans.getOrNull(index + 1)
            if (next != null && minute < next.startMinute) return (span.slotIndex + 1).toFloat()
        }
        return totalSlots
    }
}

object WeekAxisPolicy {
    fun markerTopDp(
        positionSlots: Float,
        slotHeightDp: Float,
        visualNudgeDp: Float = 3f,
    ): Float = (positionSlots * slotHeightDp - visualNudgeDp).coerceAtLeast(0f)

    fun scale(rule: TimeRule?, startMinute: Int, endMinute: Int): WeekAxisScale {
        val spans = rule?.sections.orEmpty().mapNotNull { section ->
            val start = parseMinute(section.start) ?: return@mapNotNull null
            val end = parseMinute(section.end) ?: return@mapNotNull null
            if (end <= start) return@mapNotNull null
            Triple(section.section, start, end)
        }.sortedBy { it.second }.mapIndexed { index, (section, start, end) ->
            WeekAxisSpan(section, start, end, index)
        }
        if (spans.isNotEmpty()) {
            return WeekAxisScale(
                spans = spans,
                startMinute = spans.first().startMinute,
                endMinute = spans.last().endMinute,
                totalSlots = spans.size.toFloat(),
            )
        }
        val safeEnd = max(endMinute, startMinute + 60)
        return WeekAxisScale(
            spans = emptyList(),
            startMinute = startMinute,
            endMinute = safeEnd,
            totalSlots = (safeEnd - startMinute) / 60f,
        )
    }

    fun markers(rule: TimeRule?, startMinute: Int, endMinute: Int): List<WeekAxisMarker> {
        val sections = rule?.sections.orEmpty().mapNotNull { section ->
            val start = parseMinute(section.start) ?: return@mapNotNull null
            val end = parseMinute(section.end) ?: return@mapNotNull null
            if (end <= start || start !in startMinute..endMinute) return@mapNotNull null
            WeekAxisMarker(
                section = section.section,
                anchorMinute = start,
                startText = section.start,
                endText = section.end,
            )
        }.sortedBy { it.anchorMinute }
        if (sections.isNotEmpty()) return sections

        val firstHour = ((startMinute + 59) / 60) * 60
        return if (firstHour > endMinute) emptyList() else (firstHour..endMinute step 60).map { minute ->
            WeekAxisMarker(
                section = null,
                anchorMinute = minute,
                startText = "%02d:00".format(minute / 60),
                endText = null,
            )
        }
    }

    fun parseMinute(value: String): Int? {
        val match = Regex("""^([01]?\d|2[0-3]):([0-5]\d)$""").matchEntire(value) ?: return null
        return match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()
    }
}
