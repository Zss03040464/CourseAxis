package courseaxis

import java.time.LocalDate
import java.time.LocalTime

object WeekParser {
    private val segment = Regex("""(\d{1,2})(?:\s*[-~至到]\s*(\d{1,2}))?\s*(?:周)?\s*(?:[（(]?\s*([单双])\s*(?:周)?\s*[）)]?)?""")
    fun parse(input: String, maxWeek: Int = 40): Set<Int> {
        val text = input.replace('，', ',').replace('；', ',').replace(';', ',').replace('－', '-').replace('—', '-').replace("第", "").replace("周次", "周").trim()
        if (text.isBlank()) return emptySet()
        val out = sortedSetOf<Int>()
        segment.findAll(text).forEach { m ->
            val a = m.groupValues[1].toIntOrNull() ?: return@forEach
            val b = m.groupValues[2].toIntOrNull() ?: a
            val parity = m.groupValues[3].ifBlank {
                val tail = text.substring((m.range.last + 1).coerceAtMost(text.length)).trimStart()
                when { tail.startsWith("单周") || tail.startsWith("(单)") || tail.startsWith("（单）") -> "单"; tail.startsWith("双周") || tail.startsWith("(双)") || tail.startsWith("（双）") -> "双"; else -> "" }
            }
            for (week in minOf(a,b).coerceAtLeast(1)..maxOf(a,b).coerceAtMost(maxWeek)) {
                if (parity == "单" && week % 2 == 0) continue
                if (parity == "双" && week % 2 != 0) continue
                out += week
            }
        }
        return out
    }
    fun format(values: Set<Int>): String {
        if (values.isEmpty()) return ""
        val sorted = values.sorted(); val chunks = mutableListOf<String>(); var start = sorted.first(); var last = start
        for (v in sorted.drop(1)) { if (v == last + 1) last = v else { chunks += if (start == last) "$start" else "$start-$last"; start = v; last = v } }
        chunks += if (start == last) "$start" else "$start-$last"
        return chunks.joinToString(",") + "周"
    }
}
object SectionParser {
    fun parse(input: String): List<Int> {
        val text = input.replace('，', ',').replace('；', ',').replace('－', '-').replace('—', '-').replace("第", "").replace("节次", "").replace("节", "").replace(" ", "")
        val out = sortedSetOf<Int>()
        Regex("""(\d{1,2})(?:[-~至到](\d{1,2}))?""").findAll(text).forEach { m ->
            val a=m.groupValues[1].toInt(); val b=m.groupValues[2].toIntOrNull() ?: a
            for (v in minOf(a,b)..maxOf(a,b)) if (v in 1..40) out += v
        }
        return out.toList()
    }
}
data class ParsedExamTime(val date: LocalDate, val start: LocalTime, val end: LocalTime)
object ExamTimeParser {
    private val date = Regex("""(20\d{2})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})\s*(?:日)?""")
    private val time = Regex("""([01]?\d|2[0-3])\s*[:：]\s*([0-5]\d)\s*[-~—–至到]\s*([01]?\d|2[0-3])\s*[:：]\s*([0-5]\d)""")
    fun parse(input: String): ParsedExamTime? {
        val d=date.find(input) ?: return null; val t=time.find(input) ?: return null
        return runCatching { val value=ParsedExamTime(LocalDate.of(d.groupValues[1].toInt(),d.groupValues[2].toInt(),d.groupValues[3].toInt()),LocalTime.of(t.groupValues[1].toInt(),t.groupValues[2].toInt()),LocalTime.of(t.groupValues[3].toInt(),t.groupValues[4].toInt())); require(value.end > value.start); value }.getOrNull()
    }
}
