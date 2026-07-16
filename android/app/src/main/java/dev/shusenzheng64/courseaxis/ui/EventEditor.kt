package dev.shusenzheng64.courseaxis.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.shusenzheng64.courseaxis.core.*
import dev.shusenzheng64.courseaxis.data.ScheduleRepository
import dev.shusenzheng64.courseaxis.model.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable fun EventEditor(initial:ScheduleEvent?,defaultDate:LocalDate,document:ScheduleDocument,repository:ScheduleRepository,snackbar:SnackbarHostState,onDismiss:()->Unit){
    val term=initial?.termId?.let{id->document.terms.firstOrNull{it.id==id}}?:document.activeTerm()
    var recurrence by remember(initial?.id){mutableStateOf(initial?.recurrence?:Recurrence.SINGLE)}
    var kind by remember(initial?.id){mutableStateOf(initial?.kind?:EventKind.CUSTOM)}
    var title by remember(initial?.id){mutableStateOf(initial?.title.orEmpty())};var teacher by remember(initial?.id){mutableStateOf(initial?.teacher.orEmpty())};var location by remember(initial?.id){mutableStateOf(initial?.location.orEmpty())};var notes by remember(initial?.id){mutableStateOf(initial?.notes.orEmpty())}
    var courseNature by remember(initial?.id){mutableStateOf(initial?.courseNature.orEmpty())};var totalHours by remember(initial?.id){mutableStateOf(initial?.totalHours.orEmpty())};var credits by remember(initial?.id){mutableStateOf(initial?.credits.orEmpty())}
    var dateText by remember(initial?.id){mutableStateOf(initial?.date?:defaultDate.toString())};var weekday by remember(initial?.id){mutableIntStateOf(initial?.weekday?:defaultDate.dayOfWeek.value)}
    var weeksText by remember(initial?.id){mutableStateOf(initial?.weeks?.takeIf{it.isNotEmpty()}?.let(WeekParser::format)?:term?.let{"1-${it.totalWeeks}周"}.orEmpty())}
    var startText by remember(initial?.id){mutableStateOf(initial?.startTime?:if(initial==null)"16:00" else "")};var endText by remember(initial?.id){mutableStateOf(initial?.endTime?:if(initial==null)"18:00" else "")}
    var error by remember{mutableStateOf<String?>(null)};var pending by remember{mutableStateOf<ScheduleEvent?>(null)};var conflictText by remember{mutableStateOf<List<String>>(emptyList())};var deleteConfirm by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope();val rule=document.timeRuleFor(term?.id);val parsedStart=parseTime(startText);val parsedEnd=parseTime(endText);val match=remember(parsedStart,parsedEnd,rule?.updatedAt){if(parsedStart!=null&&parsedEnd!=null&&rule!=null)TimeResolver.inferSections(parsedStart,parsedEnd,rule)else null}

    fun build():ScheduleEvent?{
        error=null;if(title.isBlank()){error="请填写名称";return null};val start=parseTime(startText);val end=parseTime(endText);if(start==null||end==null||end<=start){error="请输入有效的 HH:mm 起止时间，且结束晚于开始";return null}
        val termId=if(recurrence==Recurrence.WEEKLY)term?.id.also{if(it==null)error="周期事件需要先建立学期"}?:return null else initial?.termId?:term?.id
        val weeks=if(recurrence==Recurrence.WEEKLY)WeekParser.parse(weeksText,term?.totalWeeks?:40).takeIf{it.isNotEmpty()}.also{if(it==null)error="周次无法识别，例如 1-18周 或 1-17周(单)"}?:return null else emptySet()
        val date=if(recurrence==Recurrence.SINGLE)runCatching{LocalDate.parse(dateText.trim())}.getOrNull().also{if(it==null)error="日期格式应为 YYYY-MM-DD"}?:return null else null
        val inferred=rule?.let{TimeResolver.inferSections(start,end,it)};val base=initial?:ScheduleEvent(UUID.randomUUID().toString(),kind=kind,recurrence=recurrence,title=title)
        return base.copy(kind=kind,recurrence=recurrence,title=title.trim(),teacher=teacher.trim(),location=location.trim(),notes=notes.trim(),termId=termId,date=date?.toString(),weekday=if(recurrence==Recurrence.WEEKLY)weekday else null,weeks=weeks,startTime=start.toString(),endTime=end.toString(),startSection=inferred?.startSection,endSection=inferred?.endSection,timeRuleId=inferred?.let{rule?.id},timeSource=TimeSource.USER,resolutionStatus=ResolutionStatus.RESOLVED,colorKey=initial?.colorKey?:StableId.colorKey(title),updatedAt=Instant.now().toString(),courseNature=if(kind==EventKind.COURSE)courseNature.trim()else"",totalHours=if(kind==EventKind.COURSE)totalHours.trim()else"",credits=if(kind==EventKind.COURSE)credits.trim()else"")
    }
    fun save(event:ScheduleEvent){scope.launch{repository.upsertEvent(event);snackbar.showSnackbar("已按真实时间保存：${event.startTime}–${event.endTime}");onDismiss()}}

    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){Scaffold(
            topBar={TopAppBar(title={Text(if(initial==null)"添加事件" else "编辑事件")},navigationIcon={IconButton(onClick=onDismiss){Icon(Icons.Default.Close,"关闭")}},actions={if(initial!=null)IconButton(onClick={deleteConfirm=true}){Icon(Icons.Default.Delete,"删除")}})},
            bottomBar={Surface(shadowElevation=6.dp){Button(onClick={val candidate=build()?:return@Button;val conflicts=ScheduleEngine.conflicts(candidate,document);if(conflicts.isEmpty())save(candidate)else{pending=candidate;conflictText=conflicts.take(10).map{(a,b)->"${a.date} ${a.start}–${a.end} 与 ${b.event.title} ${b.start}–${b.end} 重叠 ${ScheduleEngine.overlapMinutes(a,b)} 分钟"}}},Modifier.fillMaxWidth().padding(16.dp)){Icon(Icons.Default.Save,null);Spacer(Modifier.width(8.dp));Text("保存")}}}
        ){padding->Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            Text("真实日期和真实时间决定位置；节次仅供参考。",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold)
            Text("事件类型",style=MaterialTheme.typography.titleSmall);FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(EventKind.CUSTOM to "自定义",EventKind.EXAM to "考试",EventKind.COURSE to "课程",EventKind.ADJUSTMENT to "调课").forEach{(v,label)->FilterChip(kind==v,{kind=v},{Text(label)})}}
            Text("重复方式",style=MaterialTheme.typography.titleSmall);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(recurrence==Recurrence.SINGLE,{recurrence=Recurrence.SINGLE},{Text("单次日期")});FilterChip(recurrence==Recurrence.WEEKLY,{recurrence=Recurrence.WEEKLY},{Text("按周重复")})}
            OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),label={Text("名称")},singleLine=true)
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){OutlinedTextField(startText,{startText=it},Modifier.weight(1f),label={Text("开始 HH:mm")},singleLine=true);OutlinedTextField(endText,{endText=it},Modifier.weight(1f),label={Text("结束 HH:mm")},singleLine=true)}
            Text(when{parsedStart==null||parsedEnd==null->"请输入真实起止时间。";match==null->"${parsedStart}–${parsedEnd} 不强行归入固定节次。";match.exact->"精确对应第 ${match.startSection}–${match.endSection} 节，但仍按时间定位。";else->"约覆盖第 ${match.startSection}–${match.endSection} 节，仍按时间定位。"},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(recurrence==Recurrence.SINGLE)OutlinedTextField(dateText,{dateText=it},Modifier.fillMaxWidth(),label={Text("日期 YYYY-MM-DD")},singleLine=true)else{
                Text("星期",style=MaterialTheme.typography.titleSmall);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){(1..7).forEach{v->FilterChip(weekday==v,{weekday=v},{Text("周${"一二三四五六日"[v-1]}")})}}
                OutlinedTextField(weeksText,{weeksText=it},Modifier.fillMaxWidth(),label={Text("周次，如 1-18周、1-17周(单)")},singleLine=true);Text("所属学期：${term?.name?:"未建立"}",style=MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){OutlinedTextField(location,{location=it},Modifier.weight(1f),label={Text("地点")},singleLine=true);OutlinedTextField(teacher,{teacher=it},Modifier.weight(1f),label={Text("教师")},singleLine=true)}
            if(kind==EventKind.COURSE){OutlinedTextField(courseNature,{courseNature=it},Modifier.fillMaxWidth(),label={Text("课程性质，如专业必修课")},singleLine=true);Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){OutlinedTextField(totalHours,{totalHours=it},Modifier.weight(1f),label={Text("总学时")},singleLine=true);OutlinedTextField(credits,{credits=it},Modifier.weight(1f),label={Text("学分")},singleLine=true)}}
            OutlinedTextField(notes,{notes=it},Modifier.fillMaxWidth(),label={Text("备注")},minLines=3);error?.let{Text(it,color=MaterialTheme.colorScheme.error,fontWeight=FontWeight.Medium)};Spacer(Modifier.height(96.dp))
        }} }
    }
    if(pending!=null)AlertDialog(onDismissRequest={pending=null},title={Text("发现真实时间重叠")},text={Column(verticalArrangement=Arrangement.spacedBy(5.dp)){Text("冲突允许保存，周视图会分栏显示。");conflictText.forEach{Text("• $it",style=MaterialTheme.typography.bodySmall)}}},confirmButton={TextButton(onClick={pending?.let(::save);pending=null}){Text("仍然保存")}},dismissButton={TextButton(onClick={pending=null}){Text("返回修改")}})
    if(deleteConfirm&&initial!=null)AlertDialog(onDismissRequest={deleteConfirm=false},title={Text("删除“${initial.title}”？")},text={Text("删除会写入 tombstone 并参与跨设备同步。")},confirmButton={TextButton(onClick={scope.launch{repository.deleteEvent(initial.id);snackbar.showSnackbar("已删除");onDismiss()}}){Text("删除",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={deleteConfirm=false}){Text("取消")}})
}
private fun parseTime(value:String):LocalTime?{val m=Regex("""^\s*([01]?\d|2[0-3])\s*[:：]\s*([0-5]\d)\s*$""").find(value)?:return null;return runCatching{LocalTime.of(m.groupValues[1].toInt(),m.groupValues[2].toInt())}.getOrNull()}
