package dev.shusenzheng64.courseaxis.data
import android.content.Context
import dev.shusenzheng64.courseaxis.core.TimeResolver
import dev.shusenzheng64.courseaxis.core.TermMetadataUpdatePolicy
import dev.shusenzheng64.courseaxis.core.SchoolImportMerge
import dev.shusenzheng64.courseaxis.importer.AcademicImportBundle
import dev.shusenzheng64.courseaxis.model.*
import dev.shusenzheng64.courseaxis.widget.WidgetUpdateCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
class ScheduleRepository(private val context:Context){
    private val mutex=Mutex();private val state=MutableStateFlow(ScheduleStorage.read(context));val document=state.asStateFlow()
    suspend fun mutate(block:(ScheduleDocument)->ScheduleDocument):ScheduleDocument=mutex.withLock{val next=block(state.value).copy(updatedAt=Instant.now().toString());withContext(Dispatchers.IO){ScheduleStorage.write(context,next)};state.value=next;WidgetUpdateCoordinator.onDocumentChanged(context,next);next}
    suspend fun replace(value:ScheduleDocument)=mutate{value}
    suspend fun upsertEvent(event:ScheduleEvent)=mutate{d->val v=event.copy(updatedAt=Instant.now().toString());d.copy(events=d.events.filterNot{it.id==v.id}+v,tombstones=d.tombstones.filterNot{it.entityType==EntityType.EVENT&&it.id==v.id})}
    suspend fun deleteEvent(id:String)=mutate{d->d.copy(events=d.events.filterNot{it.id==id},tombstones=d.tombstones.filterNot{it.entityType==EntityType.EVENT&&it.id==id}+Tombstone(id,EntityType.EVENT))}
    suspend fun applySchoolImport(bundle:AcademicImportBundle):ScheduleDocument{
        require(bundle.canCommit){"导入未通过完整性验收：请确认学期、课程真实时间和考试源后重试"}
        return mutate{d->
            d.copy(terms=d.terms.filterNot{it.id==bundle.term.id}+bundle.term,timeRules=d.timeRules.filterNot{it.termId==bundle.term.id}+listOfNotNull(bundle.timeRule),events=SchoolImportMerge.mergeTermEvents(d.events,bundle.events,bundle.term.id,preserveExistingExams=!bundle.examSource.ok),settings=d.settings.copy(activeTermId=bundle.term.id))
        }
    }
    suspend fun updateTermAndRule(term:Term,rule:TimeRule?)=mutate{d->val now=Instant.now().toString();val t=term.copy(updatedAt=now);val r=rule?.copy(updatedAt=now);val events=d.events.map{e->if(e.termId!=t.id||e.recurrence!=Recurrence.WEEKLY||e.timeSource in setOf(TimeSource.USER,TimeSource.EXPLICIT)||e.startSection==null||e.endSection==null)e else{val resolved=r?.let{TimeResolver.resolve((e.startSection..e.endSection).toList(),it)};if(resolved==null)e.copy(startTime=null,endTime=null,timeRuleId=r?.id,timeSource=TimeSource.UNRESOLVED,resolutionStatus=ResolutionStatus.UNRESOLVED,updatedAt=now)else e.copy(startTime=resolved.first.toString(),endTime=resolved.second.toString(),timeRuleId=r.id,timeSource=TimeSource.SECTION_RULE,resolutionStatus=ResolutionStatus.RESOLVED,updatedAt=now)}};d.copy(terms=d.terms.filterNot{it.id==t.id}+t,timeRules=d.timeRules.filterNot{it.termId==t.id}+listOfNotNull(r),events=events,settings=d.settings.copy(activeTermId=t.id))}
    suspend fun updateTermMetadata(term:Term)=mutate{d->
        TermMetadataUpdatePolicy.apply(d,term.copy(updatedAt=Instant.now().toString()))
    }
    suspend fun setActiveTerm(id:String)=mutate{d->
        if(d.settings.activeTermId==id||d.terms.none{it.id==id}) d
        else d.copy(settings=d.settings.copy(activeTermId=id))
    }
    suspend fun setSettings(settings:AppSettings)=mutate{it.copy(settings=settings)}
    suspend fun clearAllLocalData()=mutex.withLock{
        withContext(Dispatchers.IO){ScheduleStorage.clear(context)}
        val empty=ScheduleDocument.empty()
        state.value=empty
        WidgetUpdateCoordinator.onDocumentChanged(context,empty)
    }
}
