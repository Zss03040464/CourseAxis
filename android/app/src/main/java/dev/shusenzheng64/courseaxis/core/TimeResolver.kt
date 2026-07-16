package dev.shusenzheng64.courseaxis.core
import dev.shusenzheng64.courseaxis.model.SectionMatch
import dev.shusenzheng64.courseaxis.model.TimeRule
import java.time.Duration
import java.time.LocalTime
object TimeResolver{
    fun resolve(sections:Collection<Int>,rule:TimeRule):Pair<LocalTime,LocalTime>?{val sorted=sections.distinct().sorted();if(sorted.isEmpty()||sorted.zipWithNext().any{it.second!=it.first+1})return null;val map=rule.sections.associateBy{it.section};if(sorted.any{it !in map})return null;return map.getValue(sorted.first()).startLocalTime() to map.getValue(sorted.last()).endLocalTime()}
    fun inferSections(start:LocalTime,end:LocalTime,rule:TimeRule):SectionMatch?{if(end<=start)return null;val ordered=rule.sections.sortedBy{it.section};for(i in ordered.indices)for(j in i until ordered.size){val slice=ordered.subList(i,j+1);if(slice.zipWithNext().any{it.second.section!=it.first.section+1})break;if(slice.first().startLocalTime()==start&&slice.last().endLocalTime()==end)return SectionMatch(slice.first().section,slice.last().section,true,Duration.between(start,end).toMinutes().toInt())};val hit=ordered.filter{it.startLocalTime()<end&&start<it.endLocalTime()};if(hit.isEmpty())return null;val coverage=hit.sumOf{Duration.between(maxOf(start,it.startLocalTime()),minOf(end,it.endLocalTime())).toMinutes().coerceAtLeast(0).toInt()};return SectionMatch(hit.first().section,hit.last().section,false,coverage)}
}
