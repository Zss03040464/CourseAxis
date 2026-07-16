package dev.shusenzheng64.courseaxis.data

import dev.shusenzheng64.courseaxis.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

object ScheduleJsonCodec {
    fun encode(document: ScheduleDocument, indent: Int = 0): String = toJson(document).toString(indent)
    fun decode(text: String): ScheduleDocument = decodeWithMigration(text).document
    internal fun decodeWithMigration(text: String): DocumentMigrationResult {
        val root = JSONObject(text)
        val version = root.optInt("schemaVersion", 1)
        require(version == 1) { "不支持的数据版本：$version" }
        val document = ScheduleDocument(
            schemaVersion = version,
            documentId = root.optString("documentId").ifBlank { UUID.randomUUID().toString() },
            updatedAt = root.optString("updatedAt").ifBlank { Instant.now().toString() },
            terms = root.optJSONArray("terms").objects(::termFromJson),
            timeRules = root.optJSONArray("timeRules").objects(::ruleFromJson),
            events = root.optJSONArray("events").objects(::eventFromJson),
            tombstones = root.optJSONArray("tombstones").objects(::tombstoneFromJson),
            settings = root.optJSONObject("settings")?.let(::settingsFromJson) ?: AppSettings(),
        )
        return DocumentMigration.migrate(document)
    }
    fun toJson(document: ScheduleDocument) = JSONObject().apply {
        put("schemaVersion", document.schemaVersion); put("documentId", document.documentId); put("updatedAt", document.updatedAt)
        put("terms", JSONArray().apply { document.terms.forEach { put(termToJson(it)) } })
        put("timeRules", JSONArray().apply { document.timeRules.forEach { put(ruleToJson(it)) } })
        put("events", JSONArray().apply { document.events.forEach { put(eventToJson(it)) } })
        put("tombstones", JSONArray().apply { document.tombstones.forEach { put(tombstoneToJson(it)) } })
        put("settings", settingsToJson(document.settings))
    }
    private fun termToJson(v:Term)=JSONObject().apply{put("id",v.id);put("name",v.name);putNull("providerId",v.providerId);put("school",v.school);put("campus",v.campus);put("academicYear",v.academicYear);put("semesterCode",v.semesterCode);put("firstWeekMonday",v.firstWeekMonday);put("totalWeeks",v.totalWeeks);put("timeZone",v.timeZone);put("updatedAt",v.updatedAt)}
    private fun termFromJson(j:JSONObject)=Term(j.getString("id"),j.optString("name"),j.optString("school"),j.optString("campus"),j.optString("academicYear"),j.optString("semesterCode"),j.getString("firstWeekMonday"),j.optInt("totalWeeks",20),j.optString("timeZone").ifBlank{"Asia/Shanghai"},j.optString("updatedAt").ifBlank{Instant.now().toString()},j.stringOrNull("providerId"))
    private fun ruleToJson(v:TimeRule)=JSONObject().apply{put("id",v.id);put("termId",v.termId);put("school",v.school);put("campus",v.campus);put("source",v.source.name);put("sections",JSONArray().apply{v.sections.sortedBy{it.section}.forEach{s->put(JSONObject().apply{put("section",s.section);put("start",s.start);put("end",s.end)})}});put("updatedAt",v.updatedAt)}
    private fun ruleFromJson(j:JSONObject)=TimeRule(j.getString("id"),j.getString("termId"),j.optString("school"),j.optString("campus"),enum(j.optString("source"),TimeRuleSource.USER),j.optJSONArray("sections").objects{s->SectionTime(s.getInt("section"),s.getString("start"),s.getString("end"))}.sortedBy{it.section},j.optString("updatedAt").ifBlank{Instant.now().toString()})
    private fun eventToJson(v:ScheduleEvent)=JSONObject().apply{
        put("id",v.id);putNull("externalId",v.externalId);put("kind",v.kind.name);put("recurrence",v.recurrence.name);put("title",v.title);put("teacher",v.teacher);put("location",v.location);put("campus",v.campus);put("notes",v.notes);put("courseNature",v.courseNature);put("totalHours",v.totalHours);put("credits",v.credits);putNull("termId",v.termId);putNull("date",v.date);putNull("weekday",v.weekday);put("weeks",JSONArray(v.weeks.sorted()));putNull("startTime",v.startTime);putNull("endTime",v.endTime);putNull("startSection",v.startSection);putNull("endSection",v.endSection);putNull("timeRuleId",v.timeRuleId);put("timeSource",v.timeSource.name);put("resolutionStatus",v.resolutionStatus.name);put("colorKey",v.colorKey);put("updatedAt",v.updatedAt)
    }
    private fun eventFromJson(j:JSONObject)=ScheduleEvent(
        id=j.getString("id"),externalId=j.stringOrNull("externalId"),kind=enum(j.optString("kind"),EventKind.CUSTOM),recurrence=enum(j.optString("recurrence"),Recurrence.SINGLE),title=j.optString("title"),teacher=j.optString("teacher"),location=j.optString("location"),campus=j.optString("campus"),notes=j.optString("notes"),termId=j.stringOrNull("termId"),date=j.stringOrNull("date"),weekday=j.intOrNull("weekday"),weeks=j.optJSONArray("weeks").ints().filter{it in 1..40}.toSortedSet(),startTime=j.stringOrNull("startTime"),endTime=j.stringOrNull("endTime"),startSection=j.intOrNull("startSection"),endSection=j.intOrNull("endSection"),timeRuleId=j.stringOrNull("timeRuleId"),timeSource=enum(j.optString("timeSource"),TimeSource.UNRESOLVED),resolutionStatus=enum(j.optString("resolutionStatus"),ResolutionStatus.UNRESOLVED),colorKey=j.optInt("colorKey",0).coerceIn(0,15),updatedAt=j.optString("updatedAt").ifBlank{Instant.now().toString()},courseNature=j.optString("courseNature").ifBlank{legacyCourseNature(j.optString("notes"))},totalHours=j.optString("totalHours"),credits=j.optString("credits"))
    private fun tombstoneToJson(v:Tombstone)=JSONObject().apply{put("id",v.id);put("entityType",v.entityType.name);put("deletedAt",v.deletedAt)}
    private fun tombstoneFromJson(j:JSONObject)=Tombstone(j.getString("id"),enum(j.optString("entityType"),EntityType.EVENT),j.optString("deletedAt").ifBlank{Instant.now().toString()})
    private fun settingsToJson(v:AppSettings)=JSONObject().apply{putNull("activeTermId",v.activeTermId);put("dayStart",v.dayStart);put("dayEnd",v.dayEnd);put("showWeekends",v.showWeekends);put("widgetUpcomingCount",v.widgetUpcomingCount);put("selectedAcademicProviderId",v.selectedAcademicProviderId)}
    private fun settingsFromJson(j:JSONObject)=AppSettings(j.stringOrNull("activeTermId"),j.optString("dayStart").ifBlank{"07:30"},j.optString("dayEnd").ifBlank{"22:30"},j.optBoolean("showWeekends",true),j.optInt("widgetUpcomingCount",5),j.optString("selectedAcademicProviderId").ifBlank{"nfu-zhengfang"})
    private fun legacyCourseNature(notes:String)=notes.split('；').firstOrNull{it.startsWith("课程性质：")}?.removePrefix("课程性质：").orEmpty()
    private inline fun<reified T:Enum<T>> enum(raw:String,fallback:T)=runCatching{enumValueOf<T>(raw)}.getOrDefault(fallback)
    private fun JSONObject.putNull(key:String,value:Any?){put(key,value?:JSONObject.NULL)}
    private fun JSONObject.stringOrNull(key:String)=if(!has(key)||isNull(key))null else optString(key).takeIf{it.isNotBlank()}
    private fun JSONObject.intOrNull(key:String)=if(!has(key)||isNull(key))null else optInt(key)
    private fun<T> JSONArray?.objects(transform:(JSONObject)->T):List<T>{if(this==null)return emptyList();return buildList{for(i in 0 until length())optJSONObject(i)?.let{add(transform(it))}}}
    private fun JSONArray?.ints():List<Int>{if(this==null)return emptyList();return buildList{for(i in 0 until length())add(optInt(i))}}
}
