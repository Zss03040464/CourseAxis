package dev.shusenzheng64.courseaxis.importer

import dev.shusenzheng64.courseaxis.core.*
import dev.shusenzheng64.courseaxis.data.ProviderTermIdentity
import dev.shusenzheng64.courseaxis.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

object ZhengfangImportAdapter : AcademicImportAdapter {
    const val ADAPTER_ID = "zhengfang-v1"
    override val id: String = ADAPTER_ID
    override val scriptResource: String = "adapters/zhengfang-v1/import.js"
    private val timeRange=Regex("""([01]?\d|2[0-3])\s*[:：]\s*([0-5]\d)\s*[-~—–至到]\s*([01]?\d|2[0-3])\s*[:：]\s*([0-5]\d)""")
    private val dateRegex=Regex("""(20\d{2})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})\s*(?:日)?""")

    override fun makeInvocation(
        provider: AcademicProvider,
        academicYear: String,
        semesterCode: String,
        script: String,
    ): String {
        require(provider.adapterId == id) { "教务 Provider 与适配器不匹配：${provider.adapterId}" }
        val options = JSONObject()
            .put("xnm", academicYear)
            .put("xqm", semesterCode)
            .put("allowedOrigin", provider.allowedOrigin)
            .put("pathPrefix", provider.pathPrefix)
        return "window.__COURSEAXIS_OPTIONS__=$options;\n$script\nvoid 0;"
    }

    override fun parse(raw:String,options:AcademicImportOptions):AcademicImportBundle{
        require(options.provider.adapterId == id) { "教务 Provider 与适配器不匹配：${options.provider.adapterId}" }
        val root=JSONObject(raw);require(root.optBoolean("ok",false)){root.optJSONArray("diagnostics").strings().joinToString("\n").ifBlank{"教务读取失败"}};require(options.totalWeeks in 1..40)
        val rawTerm=root.optJSONObject("term")
        val pageYear=rawTerm?.firstString("xnm","academicYear").orEmpty()
        val pageSemester=rawTerm?.firstString("xqm","semesterCode").orEmpty()
        val academicYear=pageYear.ifBlank{options.academicYear}
        val semesterCode=pageSemester.ifBlank{options.semesterCode}
        val diagnostics=root.optJSONArray("diagnostics").strings().toMutableList()
        if(academicYear!=options.academicYear||semesterCode!=options.semesterCode)diagnostics += "已按教务页面当前学期覆盖顶部参数：$academicYear / $semesterCode"
        val schedule=root.opt("schedule")
        val courseItems=collectCourseItems(schedule,diagnostics)
        val maximumCourseWeek=courseItems.flatMap{WeekParser.parse(it.firstString("zcd","zc","weeks","weekDescription"),40)}.maxOrNull()?:1
        val totalWeeks=maxOf(options.totalWeeks,maximumCourseWeek)
        require(totalWeeks in 1..40){"学期日期或周数无效"}
        val school=options.provider.schoolName
        val now=Instant.now().toString();val termId=ProviderTermIdentity.canonicalID(options.provider.id,school,options.campus,academicYear,semesterCode)
        val term=Term(termId,options.termName.ifBlank{"$academicYear-${(academicYear.toIntOrNull()?:0)+1} ${semesterLabel(semesterCode)}"},school,options.campus,academicYear,semesterCode,options.firstWeekMonday.toString(),totalWeeks,updatedAt=now,providerId=options.provider.id)
        val sections=root.optJSONArray("sectionTimes").objects().mapNotNull{j->val n=j.optInt("section",0);val start=normalizeTime(j.optString("start"));val end=normalizeTime(j.optString("end"));runCatching{require(n in 1..40&&LocalTime.parse(end)>LocalTime.parse(start));SectionTime(n,start,end)}.getOrNull()}.distinctBy{it.section}.sortedBy{it.section}
        val rule=sections.takeIf{it.isNotEmpty()}?.let{list->TimeRule(StableId.of("rule",termId,options.campus,list.joinToString(";"){"${it.section}:${it.start}-${it.end}"}),termId,school,options.campus,TimeRuleSource.JIAOWU_DOM,list,now)}
        val examItems=collectExamItems(root.opt("exams"))
        val courses=courseItems.mapNotNull{mapCourse(it,term,rule,now,diagnostics)}
        val exams=examItems.mapNotNull{mapExam(it,term,now,diagnostics)}
        val events=(courses+exams).distinctBy{it.id}
        val sources=root.optJSONObject("sources")
        fun status(key:String,defaultOK:Boolean,count:Int):AcademicSourceStatus{
            val value=sources?.optJSONObject(key)
            return AcademicSourceStatus(value?.optBoolean("ok",defaultOK)?:defaultOK,value?.firstString("endpoint").orEmpty(),value?.optInt("count",count)?:count)
        }
        val courseSource=status("courses",root.has("schedule"),courses.size)
        val examSource=status("exams",root.has("exams"),exams.size)
        val sectionSource=status("sectionTimes",sections.isNotEmpty(),sections.size)
        val courseUnresolved=courses.count{!it.isResolved()}
        val canCommit=courseSource.ok&&courses.isNotEmpty()&&courseUnresolved==0
        if(!examSource.ok)diagnostics += "考试源暂时不可用；本次仍可安全更新课程，已有考试会保留"
        if(!canCommit)diagnostics += "导入未达到写入标准：课程源=${courseSource.ok}，课程待确认=$courseUnresolved"
        return AcademicImportBundle(term,rule,events,courses.size,exams.size,events.count{!it.isResolved()},diagnostics,courseSource,examSource,sectionSource,courseUnresolved,pageYear.isNotBlank()&&pageSemester.isNotBlank(),null,canCommit)
    }

    private fun collectCourseItems(value:Any?,diagnostics:MutableList<String>):List<JSONObject>{
        if(value is JSONObject){
            val normal=value.optJSONArray("kbList").objects()
            val practical=value.optJSONArray("sjkList").objects()
            val structured=practical.filter{it.firstString("xqj","xqjmc","weekday").isNotBlank()&&it.firstString("jc","jcs","sections").isNotBlank()}
            practical.filterNot{it in structured}.forEach{item->item.firstString("qtkcgs","sjkcgs","kcmc").takeIf{it.isNotBlank()}?.let{diagnostics += "实践/其他课程未提供可定位时间，保留诊断：$it"}}
            if(normal.isNotEmpty()||structured.isNotEmpty())return normal+structured
        }
        return findArray(value,listOf("kbList","items","rows","data"))
    }
    private fun collectExamItems(value:Any?)=findArray(value,listOf("items","rows","data","ksList","list"))

    private fun mapCourse(item:JSONObject,term:Term,rule:TimeRule?,now:String,diagnostics:MutableList<String>):ScheduleEvent?{
        val title=item.firstString("kcmc","courseName","title","kcbj").trim();if(title.isBlank()){diagnostics += "跳过一条没有课程名称的课表记录";return null}
        val weekday=parseWeekday(item.firstString("xqj","xqjmc","weekday","weekDay"));val weekText=item.firstString("zcd","zc","weeks","weekDescription");val weeks=WeekParser.parse(weekText,term.totalWeeks);val sectionText=item.firstString("jcs","jc","sections","section");val numbers=SectionParser.parse(sectionText)
        if(weekday==null||weeks.isEmpty()){diagnostics += "课程“$title”缺少可识别的星期或周次：星期=${item.firstString("xqj","xqjmc")}, 周次=$weekText";return null}
        val explicit=parseTimeRange(item.firstString("sksj","kssj","sj","time","qssj"))?:parseSeparateTimes(item)
        val resolved=explicit?:if(rule!=null&&numbers.isNotEmpty())TimeResolver.resolve(numbers,rule)else null
        val source=when{explicit!=null->TimeSource.EXPLICIT;resolved!=null->TimeSource.SECTION_RULE;else->TimeSource.UNRESOLVED}
        if(resolved==null)diagnostics += "课程“$title”无法解析真实时间（节次：$sectionText），未套用默认作息"
        val teacher=item.firstString("xm","jsxm","jsxx","teacher");val location=item.firstString("cdmc","jxdd","location","classroom");val campus=item.firstString("xqmc","campus").ifBlank{term.campus};val external=item.firstString("jxb_id","kch_id","kch","id").ifBlank{null}
        val id=if(external==null)StableId.of("course",term.id,title,weekday,sectionText)else StableId.of("course",term.id,external,weekday,sectionText)
        val nature=item.firstString("kcxz","kcxzmc","kclbmc","courseNature","courseType")
        val totalHours=item.firstString("zxs","zxs1","totalHours","hours")
        val credits=item.firstString("xf","credits","credit")
        if(nature.isBlank())diagnostics += "课程“$title”缺少课程性质"
        if(totalHours.isBlank())diagnostics += "课程“$title”缺少总学时"
        if(credits.isBlank())diagnostics += "课程“$title”缺少学分"
        return ScheduleEvent(id,external,EventKind.COURSE,Recurrence.WEEKLY,title,teacher,location,campus,item.firstString("khfsmc","notes"),term.id,null,weekday,weeks,resolved?.first?.toString(),resolved?.second?.toString(),numbers.firstOrNull(),numbers.lastOrNull(),if(source==TimeSource.SECTION_RULE)rule?.id else null,source,if(resolved!=null)ResolutionStatus.RESOLVED else ResolutionStatus.UNRESOLVED,StableId.colorKey(title),now,nature,totalHours,credits)
    }

    private fun mapExam(item:JSONObject,term:Term,now:String,diagnostics:MutableList<String>):ScheduleEvent?{
        val base=item.firstString("kcmc","courseName","title");val examName=item.firstString("ksmc","examName");val title=when{base.isNotBlank()->"$base · 考试";examName.isNotBlank()->examName;else->"考试"}
        val combined=listOf(item.firstString("kssj","examTime","time"),item.firstString("ksrq","date"),item.firstString("kssjms","timeDescription")).filter{it.isNotBlank()}.joinToString(" ")
        val parsed=ExamTimeParser.parse(combined)?:parseExamSeparate(item);if(parsed==null){diagnostics += "考试“$title”时间无法识别：$combined";return null}
        val location=item.firstString("cdmc","cdxqmc","jxdd","location");val seat=item.firstString("zwh","seat");val notes=buildList{if(examName.isNotBlank()&&examName!=title)add(examName);if(seat.isNotBlank())add("座位号：$seat")}.joinToString("；");val external=item.firstString("ks_id","kch_id","kch","id").ifBlank{null};val id=if(external==null)StableId.of("exam",term.id,title,parsed.date.toString(),parsed.start.toString())else StableId.of("exam",term.id,external,examName)
        return ScheduleEvent(id,external,EventKind.EXAM,Recurrence.SINGLE,title,item.firstString("jsxx","xm","teacher"),location,item.firstString("cdxqmc","xqmc","campus").ifBlank{term.campus},notes,term.id,parsed.date.toString(),null,emptySet(),parsed.start.toString(),parsed.end.toString(),null,null,null,TimeSource.EXPLICIT,ResolutionStatus.RESOLVED,StableId.colorKey(base.ifBlank{title}),now)
    }

    private fun parseTimeRange(value:String):Pair<LocalTime,LocalTime>?{val m=timeRange.find(value)?:return null;return runCatching{val a=LocalTime.of(m.groupValues[1].toInt(),m.groupValues[2].toInt());val b=LocalTime.of(m.groupValues[3].toInt(),m.groupValues[4].toInt());require(b>a);a to b}.getOrNull()}
    private fun parseSeparateTimes(item:JSONObject):Pair<LocalTime,LocalTime>?=runCatching{val a=LocalTime.parse(normalizeTime(item.firstString("qssj","startTime","kssj_q")));val b=LocalTime.parse(normalizeTime(item.firstString("jssj","endTime","kssj_z")));require(b>a);a to b}.getOrNull()
    private fun parseExamSeparate(item:JSONObject):ParsedExamTime?{val d=dateRegex.find(item.firstString("ksrq","date","examDate"))?:return null;val range=parseTimeRange(item.firstString("kssj","time","examTime"))?:return null;return runCatching{ParsedExamTime(LocalDate.of(d.groupValues[1].toInt(),d.groupValues[2].toInt(),d.groupValues[3].toInt()),range.first,range.second)}.getOrNull()}
    private fun parseWeekday(raw:String):Int?{raw.trim().toIntOrNull()?.takeIf{it in 1..7}?.let{return it};return when{raw.contains("一")||raw.contains("Mon",true)->1;raw.contains("二")||raw.contains("Tue",true)->2;raw.contains("三")||raw.contains("Wed",true)->3;raw.contains("四")||raw.contains("Thu",true)->4;raw.contains("五")||raw.contains("Fri",true)->5;raw.contains("六")||raw.contains("Sat",true)->6;raw.contains("日")||raw.contains("天")||raw.contains("Sun",true)->7;else->null}}
    private fun semesterLabel(code:String)=when(code){"3"->"第一学期";"12"->"第二学期";"16"->"第三学期";else->"学期 $code"}
    private fun normalizeTime(value:String):String{val m=Regex("""([01]?\d|2[0-3])\s*[:：]\s*([0-5]\d)""").find(value.trim())?:return value.trim();return m.groupValues[1].padStart(2,'0')+":"+m.groupValues[2]}
    private fun findArray(value: Any?, keys: List<String>): List<JSONObject> = when (value) {
        is JSONArray -> value.objects()
        is JSONObject -> keys.firstNotNullOfOrNull { key ->
            when (val nested = value.opt(key)) {
                is JSONArray -> nested.objects().takeIf { it.isNotEmpty() }
                is JSONObject -> findArray(nested, keys).takeIf { it.isNotEmpty() }
                else -> null
            }
        } ?: value.keys().asSequence().firstNotNullOfOrNull { key ->
            when (val nested = value.opt(key)) {
                is JSONArray -> nested.objects().takeIf { it.isNotEmpty() }
                is JSONObject -> findArray(nested, keys).takeIf { it.isNotEmpty() }
                else -> null
            }
        }.orEmpty()
        else -> emptyList()
    }
    private fun JSONObject.firstString(vararg keys:String):String{for(key in keys){val v=opt(key);if(v!=null&&v!=JSONObject.NULL){val s=v.toString().trim();if(s.isNotBlank()&&s!="null")return s}};return ""}
    private fun JSONArray?.objects():List<JSONObject>{if(this==null)return emptyList();return buildList{for(i in 0 until length())optJSONObject(i)?.let(::add)}}
    private fun JSONArray?.strings():List<String>{if(this==null)return emptyList();return buildList{for(i in 0 until length())optString(i).takeIf{it.isNotBlank()}?.let(::add)}}
}
