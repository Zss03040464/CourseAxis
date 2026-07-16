package dev.shusenzheng64.courseaxis.core
import java.time.LocalDate
import java.time.LocalTime
data class ParsedExamTime(val date: LocalDate,val start: LocalTime,val end: LocalTime)
object ExamTimeParser{
    private val dateRegex=Regex("""(20\d{2})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})\s*(?:日)?""")
    private val timeRegex=Regex("""([01]?\d|2[0-3])\s*[:：]\s*([0-5]\d)\s*[-~—–至到]\s*([01]?\d|2[0-3])\s*[:：]\s*([0-5]\d)""")
    fun parse(input:String):ParsedExamTime?{val d=dateRegex.find(input)?:return null;val t=timeRegex.find(input)?:return null;return runCatching{val p=ParsedExamTime(LocalDate.of(d.groupValues[1].toInt(),d.groupValues[2].toInt(),d.groupValues[3].toInt()),LocalTime.of(t.groupValues[1].toInt(),t.groupValues[2].toInt()),LocalTime.of(t.groupValues[3].toInt(),t.groupValues[4].toInt()));require(p.end>p.start);p}.getOrNull()}
}
