package courseaxis

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

object TimeResolver {
    fun resolve(sections: Collection<Int>, rule: TimeRule): Pair<LocalTime,LocalTime>? {
        val sorted=sections.distinct().sorted(); if (sorted.isEmpty() || sorted.zipWithNext().any { it.second != it.first+1 }) return null
        val map=rule.sections.associateBy { it.section }; if (sorted.any { it !in map }) return null
        return map.getValue(sorted.first()).startTime() to map.getValue(sorted.last()).endTime()
    }
    fun infer(start: LocalTime, end: LocalTime, rule: TimeRule): SectionMatch? {
        if (end <= start) return null; val ordered=rule.sections.sortedBy { it.section }
        for (i in ordered.indices) for (j in i until ordered.size) {
            val slice=ordered.subList(i,j+1); if (slice.zipWithNext().any { it.second.section != it.first.section+1 }) break
            if (slice.first().startTime()==start && slice.last().endTime()==end) return SectionMatch(slice.first().section,slice.last().section,true,Duration.between(start,end).toMinutes().toInt())
        }
        val hit=ordered.filter { it.startTime() < end && start < it.endTime() }; if (hit.isEmpty()) return null
        val coverage=hit.sumOf { Duration.between(maxOf(start,it.startTime()),minOf(end,it.endTime())).toMinutes().coerceAtLeast(0).toInt() }
        return SectionMatch(hit.first().section,hit.last().section,false,coverage)
    }
}
object ScheduleEngine {
    fun dateFor(term: Term, week: Int, weekday: Int): LocalDate? = if (week in 1..term.totalWeeks && weekday in 1..7) term.firstMonday().plusDays((week-1)*7L+(weekday-1)) else null
    fun weekFor(term: Term,date:LocalDate):Int? { val days=date.toEpochDay()-term.firstMonday().toEpochDay(); if(days<0)return null; return ((days/7).toInt()+1).takeIf{it in 1..term.totalWeeks} }
    fun occurrence(event:ScheduleEvent,doc:ScheduleDocument,date:LocalDate):Occurrence? {
        val start=event.start()?:return null; val end=event.end()?:return null; if(end<=start)return null
        val occurs=when(event.recurrence){ Recurrence.SINGLE -> event.localDate()==date; Recurrence.WEEKLY -> { val term=event.termId?.let{ id->doc.terms.firstOrNull{it.id==id}}?:return null; val week=weekFor(term,date)?:return null; event.weekday==date.dayOfWeek.value && week in event.weeks } }
        return if(occurs) Occurrence(event,date,start,end) else null
    }
    fun on(doc:ScheduleDocument,date:LocalDate)=doc.events.mapNotNull{occurrence(it,doc,date)}.sortedWith(compareBy<Occurrence>{it.start}.thenBy{it.end}.thenBy{it.event.title})
    fun overlaps(a:Occurrence,b:Occurrence)=a.date==b.date && a.start < b.end && b.start < a.end
    fun overlapMinutes(a:Occurrence,b:Occurrence)=if(!overlaps(a,b))0 else Duration.between(maxOf(a.start,b.start),minOf(a.end,b.end)).toMinutes().toInt()
    fun conflicts(candidate:ScheduleEvent,doc:ScheduleDocument):List<Pair<Occurrence,Occurrence>> {
        val dates=when(candidate.recurrence){ Recurrence.SINGLE -> listOfNotNull(candidate.localDate()); Recurrence.WEEKLY -> { val term=candidate.termId?.let{ id->doc.terms.firstOrNull{it.id==id}}?:return emptyList(); candidate.weeks.mapNotNull{w->candidate.weekday?.let{dateFor(term,w,it)}} } }
        return dates.flatMap { date -> val c=occurrence(candidate,doc,date)?:return@flatMap emptyList(); on(doc,date).filter{it.event.id!=candidate.id && overlaps(c,it)}.map{c to it} }
    }
}
object MergeEngine {
    fun merge(a:ScheduleDocument,b:ScheduleDocument):ScheduleDocument {
        require(a.schemaVersion==b.schemaVersion)
        fun instant(v:String)=runCatching{Instant.parse(v)}.getOrDefault(Instant.EPOCH)
        val tomb=(a.tombstones+b.tombstones).groupBy{it.entityType to it.id}.values.map{g->g.maxBy{instant(it.deletedAt)}}
        val tombMap=tomb.associateBy{it.entityType to it.id}
        fun <T> mergeRecords(x:List<T>,y:List<T>,id:(T)->String,updated:(T)->String,kind:EntityType):List<T>=(x+y).groupBy(id).values.map{g->g.maxBy{instant(updated(it))}}.filter{r->val t=tombMap[kind to id(r)]; t==null || instant(t.deletedAt)<instant(updated(r))}
        val au=instant(a.updatedAt); val bu=instant(b.updatedAt)
        return ScheduleDocument(a.schemaVersion,a.documentId,maxOf(au,bu).toString(),mergeRecords(a.terms,b.terms,{it.id},{it.updatedAt},EntityType.TERM),mergeRecords(a.timeRules,b.timeRules,{it.id},{it.updatedAt},EntityType.TIME_RULE),mergeRecords(a.events,b.events,{it.id},{it.updatedAt},EntityType.EVENT),tomb,if(au>=bu)a.settings else b.settings)
    }
}
