import courseaxis.*
import java.time.LocalDate
import java.time.LocalTime

fun checkThat(value:Boolean,message:String){if(!value) error(message)}
fun main(){
    checkThat(AppSettings().widgetUpcomingCount==5,"widget upcoming count default")
    val enriched=ScheduleEvent("meta",kind=EventKind.COURSE,recurrence=Recurrence.WEEKLY,title="人工智能原理",courseNature="专业必修课",totalHours="34",credits="2.0")
    checkThat(enriched.courseNature=="专业必修课"&&enriched.totalHours=="34"&&enriched.credits=="2.0","course metadata fields")
    checkThat(WeekParser.parse("1-15周,17-18周")==((1..15)+(17..18)).toSet(),"week range")
    checkThat(WeekParser.parse("1-17周(单)")==setOf(1,3,5,7,9,11,13,15,17),"odd weeks")
    checkThat(SectionParser.parse("(1-3节)")==listOf(1,2,3),"sections")
    val rule=TimeRule("r","t",sections=listOf(SectionTime(1,"08:00","08:40"),SectionTime(2,"08:45","09:25"),SectionTime(9,"16:20","17:00"),SectionTime(10,"17:05","17:45")))
    checkThat(TimeResolver.resolve(listOf(1,2),rule)==LocalTime.of(8,0) to LocalTime.of(9,25),"resolve true times")
    checkThat(TimeResolver.infer(LocalTime.of(16,0),LocalTime.of(18,0),rule)?.exact==false,"reverse approximate")
    val term=Term("t","term",firstWeekMonday="2026-02-23",totalWeeks=20)
    val existing=ScheduleEvent("a",kind=EventKind.COURSE,recurrence=Recurrence.SINGLE,title="A",date="2026-07-10",startTime="17:30",endTime="19:00",timeSource=TimeSource.USER,resolutionStatus=ResolutionStatus.RESOLVED)
    val manual=ScheduleEvent("b",kind=EventKind.EXAM,recurrence=Recurrence.SINGLE,title="B",date="2026-07-10",startTime="16:00",endTime="18:00",timeSource=TimeSource.USER,resolutionStatus=ResolutionStatus.RESOLVED)
    val doc=ScheduleDocument(terms=listOf(term),events=listOf(existing))
    checkThat(ScheduleEngine.conflicts(manual,doc).single().let{ScheduleEngine.overlapMinutes(it.first,it.second)}==30,"actual time conflict")
    val exam=ExamTimeParser.parse("2026年7月10日 16:00—18:00")!!
    checkThat(exam.date==LocalDate.of(2026,7,10)&&exam.start==LocalTime.of(16,0),"exam parser")
    checkThat(StableId.of("term","广州南方学院","2025","12","2026-02-23")=="term-d248459c24acef18","stable id")
    val deleted=doc.copy(events=emptyList(),tombstones=listOf(Tombstone("a",EntityType.EVENT,"2026-07-12T00:00:00Z")),updatedAt="2026-07-12T00:00:00Z")
    checkThat(MergeEngine.merge(doc.copy(updatedAt="2026-07-11T00:00:00Z"),deleted).events.isEmpty(),"tombstone")
    println("reference-core: all tests passed")
}
