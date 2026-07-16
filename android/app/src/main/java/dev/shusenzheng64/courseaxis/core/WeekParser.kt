package dev.shusenzheng64.courseaxis.core

object WeekParser {
    private val segmentRegex = Regex("""(\d{1,2})(?:\s*[-~至到]\s*(\d{1,2}))?\s*(?:周)?\s*(?:[（(]?\s*([单双])\s*(?:周)?\s*[）)]?)?""")
    fun parse(input: String, maxWeek: Int = 40): Set<Int> {
        val text = input.replace('，', ',').replace('；', ',').replace(';', ',').replace('－', '-').replace('—', '-').replace("第", "").replace("周次", "周").trim()
        if (text.isBlank()) return emptySet()
        val result = sortedSetOf<Int>()
        segmentRegex.findAll(text).forEach { match ->
            val start = match.groupValues[1].toIntOrNull() ?: return@forEach
            val end = match.groupValues[2].toIntOrNull() ?: start
            val parity = match.groupValues[3].ifBlank {
                val tail = text.substring((match.range.last + 1).coerceAtMost(text.length)).trimStart()
                when { tail.startsWith("单周") || tail.startsWith("(单)") || tail.startsWith("（单）") -> "单"; tail.startsWith("双周") || tail.startsWith("(双)") || tail.startsWith("（双）") -> "双"; else -> "" }
            }
            for (week in minOf(start,end).coerceAtLeast(1)..maxOf(start,end).coerceAtMost(maxWeek)) {
                if (parity == "单" && week % 2 == 0) continue
                if (parity == "双" && week % 2 != 0) continue
                result += week
            }
        }
        return result
    }
    fun format(weeks: Set<Int>): String {
        if (weeks.isEmpty()) return ""
        val sorted=weeks.sorted(); val parts=mutableListOf<String>(); var start=sorted.first(); var last=start
        for (value in sorted.drop(1)) { if (value==last+1) last=value else { parts += if(start==last) "$start" else "$start-$last"; start=value; last=value } }
        parts += if(start==last) "$start" else "$start-$last"
        return parts.joinToString(",")+"周"
    }
}
object SectionParser {
    fun parse(input:String,maxSection:Int=40):List<Int>{
        val text=input.replace('，',',').replace('；',',').replace(';',',').replace('－','-').replace('—','-').replace("第","").replace("节次","").replace("节","").replace(" ","")
        val result=sortedSetOf<Int>()
        Regex("""(\d{1,2})(?:[-~至到](\d{1,2}))?""").findAll(text).forEach { m -> val a=m.groupValues[1].toInt();val b=m.groupValues[2].toIntOrNull()?:a;for(v in minOf(a,b)..maxOf(a,b))if(v in 1..maxSection)result+=v }
        return result.toList()
    }
}
