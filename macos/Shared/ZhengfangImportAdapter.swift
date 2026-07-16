import Foundation

struct ZhengfangImportAdapter: AcademicImportAdapter {
    static let adapterID = "zhengfang-v1"
    let id = adapterID
    let scriptResource = "adapters/zhengfang-v1/import.js"

    func makeInvocation(provider: AcademicProvider, academicYear: String, semesterCode: String, script: String) throws -> String {
        guard provider.adapterId == id else { throw importError("教务 Provider 与适配器不匹配：\(provider.adapterId)") }
        let values = [
            "xnm": academicYear,
            "xqm": semesterCode,
            "allowedOrigin": provider.allowedOrigin,
            "pathPrefix": provider.pathPrefix,
        ]
        let data = try? JSONSerialization.data(withJSONObject: values, options: [.sortedKeys, .withoutEscapingSlashes])
        let json = data.flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
        return "window.__COURSEAXIS_OPTIONS__=\(json);\n\(script)\nvoid 0;"
    }

    func parse(_ raw:String,options:AcademicImportOptions)throws->AcademicImportBundle{
        guard options.provider.adapterId == id else { throw importError("教务 Provider 与适配器不匹配：\(options.provider.adapterId)") }
        guard let data=raw.data(using:.utf8),let root=try JSONSerialization.jsonObject(with:data)as?[String:Any]else{throw importError("无效导入数据")};guard root["ok"]as?Bool==true else{throw importError((root["diagnostics"]as?[String])?.joined(separator:"\n") ?? "教务读取失败")}
        let rawTerm=root["term"]as?[String:Any] ?? [:]
        let pageYear=first(rawTerm,["xnm","academicYear"]),pageSemester=first(rawTerm,["xqm","semesterCode"])
        let academicYear=pageYear.isEmpty ? options.academicYear:pageYear,semesterCode=pageSemester.isEmpty ? options.semesterCode:pageSemester
        let schedule=root["schedule"]as?[String:Any] ?? [:];var courseItems=schedule["kbList"]as?[[String:Any]] ?? []
        let practical=schedule["sjkList"]as?[[String:Any]] ?? [];var diagnostics=root["diagnostics"]as?[String] ?? []
        for item in practical{if !first(item,["xqj","xqjmc","weekday"]).isEmpty && !first(item,["jc","jcs","sections"]).isEmpty{courseItems.append(item)}else if !first(item,["qtkcgs","sjkcgs","kcmc"]).isEmpty{diagnostics.append("实践/其他课程未提供可定位时间：\(first(item,["qtkcgs","sjkcgs","kcmc"]))")}}
        if courseItems.isEmpty{courseItems=findArray(root["schedule"],keys:["kbList","items","rows","data"])}
        let examItems=findArray(root["exams"],keys:["items","rows","data","ksList","list"])
        let maximumCourseWeek=courseItems.flatMap{WeekParser.parse(first($0,["zcd","zc","weeks","weekDescription"]),maxWeek:40)}.max() ?? 1
        let firstMonday=options.firstWeekMonday
        let totalWeeks=max(options.totalWeeks,maximumCourseWeek)
        guard(1...40).contains(totalWeeks),LocalValue.date(firstMonday) != nil else{throw importError("学期日期或周数无效")}
        if academicYear != options.academicYear || semesterCode != options.semesterCode{diagnostics.append("已按教务页面当前学期覆盖顶部参数：\(academicYear) / \(semesterCode)")}
        if options.provider.id == ProviderTermIdentity.defaultProviderID,let suggestion=NfuAcademicCalendar.lookup(academicYear:academicYear,semesterCode:semesterCode),suggestion.firstWeekMonday != firstMonday{diagnostics.append("校历建议第一周周一为 \(suggestion.firstWeekMonday)；已保留用户设置 \(firstMonday)")}
        let school=options.provider.schoolName
        let now=LocalValue.isoNow(),termID=ProviderTermIdentity.canonicalID(providerId:options.provider.id,school:school,campus:options.campus,academicYear:academicYear,semesterCode:semesterCode)
        let defaultName="\(academicYear)-\((Int(academicYear) ?? 0)+1) \(semesterLabel(semesterCode))"
        let term=Term(id:termID,name:options.termName.isEmpty ? defaultName:options.termName,providerId:options.provider.id,school:school,campus:options.campus,academicYear:academicYear,semesterCode:semesterCode,firstWeekMonday:firstMonday,totalWeeks:totalWeeks,updatedAt:now)
        let sections=(root["sectionTimes"]as?[[String:Any]] ?? []).compactMap{item->SectionTime? in guard let n=number(item["section"]),let start=normalizeTime(string(item["start"])),let end=normalizeTime(string(item["end"])),let a=LocalValue.minutes(start),let b=LocalValue.minutes(end),(1...40).contains(n),b>a else{return nil};return SectionTime(section:n,start:start,end:end)}.reduce(into:[Int:SectionTime]()){$0[$1.section]=$1}.values.sorted{$0.section<$1.section}
        let rule=sections.isEmpty ? nil:TimeRule(id:StableID.make("rule",termID,options.campus,sections.map{"\($0.section):\($0.start)-\($0.end)"}.joined(separator:";")),termId:termID,school:school,campus:options.campus,source:.JIAOWU_DOM,sections:sections,updatedAt:now)
        let courses=courseItems.compactMap{mapCourse($0,term:term,rule:rule,now:now,diagnostics:&diagnostics)},exams=examItems.compactMap{mapExam($0,term:term,now:now,diagnostics:&diagnostics)};let events=Dictionary(grouping:courses+exams,by:\.id).compactMap{$0.value.first}
        let sources=root["sources"]as?[String:Any] ?? [:]
        func status(_ key:String,defaultOK:Bool,count:Int)->AcademicSourceStatus{let value=sources[key]as?[String:Any] ?? [:];return AcademicSourceStatus(ok:value["ok"]as?Bool ?? defaultOK,endpoint:first(value,["endpoint"]),count:number(value["count"]) ?? count)}
        let courseSource=status("courses",defaultOK:root["schedule"] != nil,count:courses.count)
        let examSource=status("exams",defaultOK:root["exams"] != nil,count:exams.count)
        let sectionSource=status("sectionTimes",defaultOK:!sections.isEmpty,count:sections.count)
        let courseUnresolved=courses.filter{!$0.isResolved}.count
        let canCommit=courseSource.ok && !courses.isEmpty && courseUnresolved == 0
        if !examSource.ok{diagnostics.append("考试源暂时不可用；本次仍可安全更新课程，已有考试会保留")}
        if !canCommit{diagnostics.append("导入未达到写入标准：课程源=\(courseSource.ok)，课程待确认=\(courseUnresolved)")}
        return AcademicImportBundle(term:term,timeRule:rule,events:events,courseCount:courses.count,examCount:exams.count,unresolvedCount:events.filter{!$0.isResolved}.count,diagnostics:diagnostics,courseSource:courseSource,examSource:examSource,sectionSource:sectionSource,courseUnresolvedCount:courseUnresolved,detectedTerm:!pageYear.isEmpty && !pageSemester.isEmpty,calendarSourceURL:nil,canCommit:canCommit)
    }
    private func mapCourse(_ item:[String:Any],term:Term,rule:TimeRule?,now:String,diagnostics:inout[String])->ScheduleEvent?{
        let title=first(item,["kcmc","courseName","title","kcbj"]);guard !title.isEmpty else{diagnostics.append("跳过没有课程名称的记录");return nil};let weekday=parseWeekday(first(item,["xqj","xqjmc","weekday","weekDay"])),weekText=first(item,["zcd","zc","weeks","weekDescription"]),weeks=WeekParser.parse(weekText,maxWeek:term.totalWeeks),sectionText=first(item,["jcs","jc","sections","section"]),numbers=SectionParser.parse(sectionText);guard let weekday,!weeks.isEmpty else{diagnostics.append("课程“\(title)”缺少星期或周次");return nil}
        let explicit=parseTimeRange(first(item,["sksj","kssj","sj","time","qssj"])) ?? parseSeparate(item);let resolved=explicit ?? (rule.flatMap{TimeResolver.resolve(numbers,rule:$0)});let source:TimeSource=explicit != nil ? .EXPLICIT:(resolved != nil ? .SECTION_RULE:.UNRESOLVED);if resolved==nil{diagnostics.append("课程“\(title)”无法解析真实时间（节次：\(sectionText)），未套用默认作息")}
        let teacher=first(item,["xm","jsxm","jsxx","teacher"]),location=first(item,["cdmc","jxdd","location","classroom"]),campus=first(item,["xqmc","campus"]).isEmpty ? term.campus:first(item,["xqmc","campus"]),external=first(item,["jxb_id","kch_id","kch","id"]);let id=external.isEmpty ? StableID.make("course",term.id,title,weekday,sectionText) : StableID.make("course",term.id,external,weekday,sectionText)
        let nature=first(item,["kcxz","kcxzmc","kclbmc","courseNature","courseType"]),totalHours=first(item,["zxs","zxs1","totalHours","hours"]),credits=first(item,["xf","credits","credit"]),notes=first(item,["khfsmc","notes"])
        if nature.isEmpty{diagnostics.append("课程“\(title)”缺少课程性质")};if totalHours.isEmpty{diagnostics.append("课程“\(title)”缺少总学时")};if credits.isEmpty{diagnostics.append("课程“\(title)”缺少学分")}
        return ScheduleEvent(id:id,externalId:external.isEmpty ? nil:external,kind:.COURSE,recurrence:.WEEKLY,title:title,teacher:teacher,location:location,campus:campus,notes:notes,termId:term.id,weekday:weekday,weeks:weeks,startTime:resolved.map{LocalValue.timeString($0.0)},endTime:resolved.map{LocalValue.timeString($0.1)},startSection:numbers.first,endSection:numbers.last,timeRuleId:source == .SECTION_RULE ? rule?.id:nil,timeSource:source,resolutionStatus:resolved == nil ? .UNRESOLVED:.RESOLVED,colorKey:StableID.colorKey(title),updatedAt:now,courseNature:nature,totalHours:totalHours,credits:credits)
    }
    private func mapExam(
        _ item: [String: Any],
        term: Term,
        now: String,
        diagnostics: inout [String]
    ) -> ScheduleEvent? {
        let base = first(item, ["kcmc", "courseName", "title"])
        let examName = first(item, ["ksmc", "examName"])
        let title = !base.isEmpty ? "\(base) · 考试" : (!examName.isEmpty ? examName : "考试")
        let combined = [
            first(item, ["kssj", "examTime", "time"]),
            first(item, ["ksrq", "date"]),
            first(item, ["kssjms", "timeDescription"])
        ].filter { !$0.isEmpty }.joined(separator: " ")
        guard let parsed = ExamTimeParser.parse(combined) ?? parseExamSeparate(item) else {
            diagnostics.append("考试“\(title)”时间无法识别：\(combined)")
            return nil
        }
        let location = first(item, ["cdmc", "cdxqmc", "jxdd", "location"])
        let seat = first(item, ["zwh", "seat"])
        let external = first(item, ["ks_id", "kch_id", "kch", "id"])
        var notes: [String] = []
        if !examName.isEmpty { notes.append(examName) }
        if !seat.isEmpty { notes.append("座位号：\(seat)") }
        let campusValue = first(item, ["cdxqmc", "xqmc", "campus"])
        return ScheduleEvent(
            id: external.isEmpty ? StableID.make("exam",term.id,title,LocalValue.dateString(parsed.date),LocalValue.timeString(parsed.start)) : StableID.make("exam",term.id,external,examName),
            externalId: external.isEmpty ? nil : external,
            kind: .EXAM,
            recurrence: .SINGLE,
            title: title,
            teacher: first(item, ["jsxx", "xm", "teacher"]),
            location: location,
            campus: campusValue.isEmpty ? term.campus : campusValue,
            notes: notes.joined(separator: "；"),
            termId: term.id,
            date: LocalValue.dateString(parsed.date),
            startTime: LocalValue.timeString(parsed.start),
            endTime: LocalValue.timeString(parsed.end),
            timeSource: .EXPLICIT,
            resolutionStatus: .RESOLVED,
            colorKey: StableID.colorKey(base.isEmpty ? title : base),
            updatedAt: now
        )
    }
    private func parseTimeRange(_ value:String)->(Int,Int)?{guard let m=value.firstMatch(#"([01]?\d|2[0-3])\s*[:：]\s*([0-5]\d)\s*[-~—–至到]\s*([01]?\d|2[0-3])\s*[:：]\s*([0-5]\d)"#),m.count>=5,let h1=Int(m[1]),let m1=Int(m[2]),let h2=Int(m[3]),let m2=Int(m[4])else{return nil};let a=h1*60+m1,b=h2*60+m2;return b>a ? (a,b):nil}
    private func parseSeparate(_ item:[String:Any])->(Int,Int)?{guard let a=LocalValue.minutes(first(item,["qssj","startTime","kssj_q"])),let b=LocalValue.minutes(first(item,["jssj","endTime","kssj_z"])),b>a else{return nil};return(a,b)}
    private func parseExamSeparate(_ item:[String:Any])->ParsedExamTime?{guard let d=first(item,["ksrq","date","examDate"]).firstMatch(#"(20\d{2})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})"#),d.count>=4,let y=Int(d[1]),let m=Int(d[2]),let day=Int(d[3]),let date=LocalValue.calendar.date(from:DateComponents(year:y,month:m,day:day)),let range=parseTimeRange(first(item,["kssj","time","examTime"]))else{return nil};return ParsedExamTime(date:date,start:range.0,end:range.1)}
    private func parseWeekday(_ raw:String)->Int?{if let n=Int(raw.trimmingCharacters(in:.whitespaces)),(1...7).contains(n){return n};for(v,keys)in[(1,["一","Mon"]),(2,["二","Tue"]),(3,["三","Wed"]),(4,["四","Thu"]),(5,["五","Fri"]),(6,["六","Sat"]),(7,["日","天","Sun"])]{if keys.contains(where:{raw.localizedCaseInsensitiveContains($0)}){return v}};return nil}
    private func semesterLabel(_ code:String)->String{code=="3" ? "第一学期":code=="12" ? "第二学期":"学期 \(code)"}
    private func normalizeTime(_ value:String)->String?{guard let m=value.firstMatch(#"([01]?\d|2[0-3])\s*[:：]\s*([0-5]\d)"#),m.count>=3,let h=Int(m[1])else{return nil};return String(format:"%02d:%@",h,m[2])}
    private func first(_ item:[String:Any],_ keys:[String])->String{for key in keys{if let value=item[key],!(value is NSNull){let s=String(describing:value).trimmingCharacters(in:.whitespacesAndNewlines);if !s.isEmpty && s != "null"{return s}}};return ""}
    private func string(_ value:Any?)->String{value.map{String(describing:$0)} ?? ""};private func number(_ value:Any?)->Int?{if let n=value as?NSNumber{return n.intValue};return Int(string(value))}
    private func findArray(_ value:Any?,keys:[String])->[[String:Any]]{if let a=value as?[[String:Any]]{return a};guard let d=value as?[String:Any]else{return[]};for key in keys{let r=findArray(d[key],keys:keys);if !r.isEmpty{return r}};for value in d.values{let r=findArray(value,keys:keys);if !r.isEmpty{return r}};return[]}
    private func importError(_ value:String)->Error{NSError(domain:"CourseAxis.Import",code:1,userInfo:[NSLocalizedDescriptionKey:value])}
}
