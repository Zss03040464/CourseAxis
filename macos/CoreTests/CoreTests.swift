import XCTest
@testable import CourseAxisCore

final class CoreTests:XCTestCase{
    private var nfuProvider:AcademicProvider {
        AcademicProvider(
            id:"nfu-zhengfang",schoolName:"广州南方学院",vendor:"ZHENGFANG",
            allowedOrigin:"https://jwxt.nfu.edu.cn",pathPrefix:"/jwglxt/",
            loginPath:"/jwglxt/xtgl/login_slogin.html",homePath:"/jwglxt/xtgl/index_initMenu.html",
            requiresThirdPartyCookies:false,semesterCodes:["first":"3","second":"12","third":"16"],
            adapterId:"zhengfang-v1",sessionNamespace:"7c8cd13a-2094-5c1b-9d84-2ee62d7a4f70"
        )
    }
    private func nfuOptions(academicYear:String,semesterCode:String,termName:String,firstWeekMonday:String,totalWeeks:Int,campus:String="")->AcademicImportOptions {
        AcademicImportOptions(provider:nfuProvider,academicYear:academicYear,semesterCode:semesterCode,termName:termName,firstWeekMonday:firstWeekMonday,totalWeeks:totalWeeks,campus:campus)
    }
    private func parseNfu(_ raw:String,options:AcademicImportOptions)throws->AcademicImportBundle {
        try AcademicImportAdapterRegistry.adapter(for:options.provider).parse(raw,options:options)
    }
    func testAcademicProviderAllowsOnlyItsExactHttpsOriginAndPath() {
        let provider = AcademicProvider(
            id: "nfu-zhengfang",
            schoolName: "广州南方学院",
            vendor: "ZHENGFANG",
            allowedOrigin: "https://jwxt.nfu.edu.cn",
            pathPrefix: "/jwglxt/",
            loginPath: "/jwglxt/xtgl/login_slogin.html",
            homePath: "/jwglxt/xtgl/index_initMenu.html",
            requiresThirdPartyCookies: false
        )

        XCTAssertTrue(AcademicProviderPolicy.isAllowed(URL(string: "https://jwxt.nfu.edu.cn/jwglxt/")!, provider: provider))
        XCTAssertTrue(AcademicProviderPolicy.isAllowed(URL(string: "https://jwxt.nfu.edu.cn:443/jwglxt/")!, provider: provider))
        XCTAssertTrue(AcademicProviderPolicy.isAllowed(URL(string: "https://jwxt.nfu.edu.cn/jwglxt/xtgl/index_initMenu.html?gnmkdm=index")!, provider: provider))
        [
            "http://jwxt.nfu.edu.cn/jwglxt/",
            "https://user:pass@jwxt.nfu.edu.cn/jwglxt/",
            "https://jwxt.nfu.edu.cn:8443/jwglxt/",
            "https://nfu.edu.cn/jwglxt/",
            "https://portal.nfu.edu.cn/jwglxt/",
            "https://jwxt.nfu.edu.cn.evil.example/jwglxt/",
            "https://example.com/jwglxt/",
            "https://jwxt.nfu.edu.cn/",
            "https://jwxt.nfu.edu.cn/jwglxt.evil/",
            "https://jwxt.nfu.edu.cn/jwglxt/%2e%2e/",
            "https://jwxt.nfu.edu.cn/jwglxt/%5cadmin",
        ].forEach { rawURL in
            XCTAssertFalse(AcademicProviderPolicy.isAllowed(URL(string: rawURL)!, provider: provider), rawURL)
        }
    }

    func testAcademicWebViewNavigationUsesTheExactProviderBoundary() {
        XCTAssertTrue(
            AcademicNavigationPolicy.isEmbeddedURLAllowed(
                URL(string: "https://jwxt.nfu.edu.cn/jwglxt/xtgl/index_initMenu.html")!,
                provider: nfuProvider
            )
        )
        [
            "http://jwxt.nfu.edu.cn/jwglxt/",
            "https://portal.nfu.edu.cn/jwglxt/",
            "https://jwxt.nfu.edu.cn/",
            "https://jwxt.nfu.edu.cn/jwglxt.evil/",
        ].forEach { rawURL in
            let url = URL(string: rawURL)!
            XCTAssertFalse(AcademicNavigationPolicy.isEmbeddedURLAllowed(url, provider: nfuProvider), rawURL)
            XCTAssertEqual(
                AcademicNavigationPolicy.destination(for: url, provider: nfuProvider, hasTargetFrame: true),
                .externalBrowser,
                rawURL
            )
        }
    }

    func testCourseMetadataCompatibility() throws {
        let legacy = #"{"schemaVersion":1,"documentId":"legacy","updatedAt":"2026-07-15T00:00:00Z","terms":[],"timeRules":[],"events":[{"id":"legacy-course","externalId":null,"kind":"COURSE","recurrence":"WEEKLY","title":"人工智能原理","teacher":"魏俊华","location":"2实407","campus":"温泉校区","notes":"","termId":"term","date":null,"weekday":4,"weeks":[1,2,3],"startTime":"10:35","endTime":"12:00","startSection":4,"endSection":5,"timeRuleId":null,"timeSource":"EXPLICIT","resolutionStatus":"RESOLVED","colorKey":1,"updatedAt":"2026-07-15T00:00:00Z"}],"tombstones":[],"settings":{"activeTermId":null,"dayStart":"07:30","dayEnd":"22:30","showWeekends":true}}"#
        let decoded = try JSONDecoder().decode(ScheduleDocument.self, from: Data(legacy.utf8))
        let oldEvent = try XCTUnwrap(decoded.events.first)
        XCTAssertEqual(oldEvent.courseNature, "")
        XCTAssertEqual(oldEvent.totalHours, "")
        XCTAssertEqual(oldEvent.credits, "")

        var enriched = oldEvent
        enriched.courseNature = "专业必修课"
        enriched.totalHours = "34"
        enriched.credits = "2.0"
        let roundTrip = try JSONDecoder().decode(ScheduleDocument.self, from: JSONEncoder().encode(ScheduleDocument(events: [enriched])))
        XCTAssertEqual(roundTrip.events.first?.courseNature, "专业必修课")
        XCTAssertEqual(roundTrip.events.first?.totalHours, "34")
        XCTAssertEqual(roundTrip.events.first?.credits, "2.0")
    }
    func testImporterReadsCourseMetadata() throws {
        func raw(_ nature:String,_ hours:String,_ credits:String)->String { """
        {"ok":true,"diagnostics":[],"sectionTimes":[{"section":4,"start":"10:35","end":"11:15"},{"section":5,"start":"11:20","end":"12:00"}],"schedule":{"kbList":[{"kch_id":"AI001","kcmc":"人工智能原理","xm":"魏俊华","xqj":"4","jc":"4-5节","zcd":"1-17周","cdmc":"温泉校区 2实407","kcxz":"\(nature)","zxs":"\(hours)","xf":"\(credits)"}],"sjkList":[]},"exams":{"items":[]}}
        """ }
        let options=nfuOptions(academicYear:"2026",semesterCode:"3",termName:"2026-2027 第一学期",firstWeekMonday:"2026-09-07",totalWeeks:20)
        let first=try XCTUnwrap(parseNfu(raw("专业必修课","34","2.0"),options:options).events.first)
        let refreshed=try XCTUnwrap(parseNfu(raw("专业选修课","36","2.5"),options:options).events.first)
        XCTAssertEqual(first.courseNature,"专业必修课")
        XCTAssertEqual(first.totalHours,"34")
        XCTAssertEqual(first.credits,"2.0")
        XCTAssertEqual(first.id,refreshed.id)
    }
    func testStableTermIdentityIgnoresEditableDates() throws {
        let raw = #"{"ok":true,"term":{"xnm":"2026","xqm":"3"},"sources":{"courses":{"ok":true},"exams":{"ok":true},"sectionTimes":{"ok":true}},"sectionTimes":[{"section":1,"start":"08:00","end":"08:40"}],"schedule":{"kbList":[{"kch_id":"X","kcmc":"课程","xm":"教师","xqj":"1","jc":"1节","zcd":"1-18周","cdmc":"A"}]},"exams":{"items":[]}}"#
        let first = try parseNfu(raw, options: nfuOptions(academicYear:"2026",semesterCode:"3",termName:"",firstWeekMonday:"2026-09-01",totalWeeks:20,campus:"温泉校区"))
        let second = try parseNfu(raw, options: nfuOptions(academicYear:"2026",semesterCode:"3",termName:"",firstWeekMonday:"2026-09-07",totalWeeks:20,campus:"温泉校区"))
        XCTAssertEqual(first.term.id, second.term.id)
        XCTAssertEqual(first.term.firstWeekMonday, "2026-09-01")
        XCTAssertEqual(second.term.firstWeekMonday, "2026-09-07")
    }

    func testDocumentMigrationMergesSemanticDuplicateTermsAndRewritesReferences() {
        let older = Term(id:"old",name:"旧名称",school:"广州南方学院",campus:"温泉校区",academicYear:"2025",semesterCode:"12",firstWeekMonday:"2026-02-23",updatedAt:"2026-07-01T00:00:00Z")
        let newer = Term(id:"new",name:"2025-2026 第二学期",school:"广州南方学院",campus:"温泉校区",academicYear:"2025",semesterCode:"12",firstWeekMonday:"2026-03-02",updatedAt:"2026-07-02T00:00:00Z")
        let eventA = ScheduleEvent(id:"a",kind:.COURSE,recurrence:.WEEKLY,title:"A",termId:"old",weekday:1,weeks:[1],startTime:"08:00",endTime:"09:00",timeSource:.USER,resolutionStatus:.RESOLVED)
        let eventB = ScheduleEvent(id:"b",kind:.EXAM,recurrence:.SINGLE,title:"B",termId:"new",date:"2026-07-01",startTime:"10:00",endTime:"11:00",timeSource:.EXPLICIT,resolutionStatus:.RESOLVED)
        let rule = TimeRule(id:"rule",termId:"old",sections:[SectionTime(section:1,start:"08:00",end:"08:40")])
        let document = ScheduleDocument(terms:[older,newer],timeRules:[rule],events:[eventA,eventB],settings:AppSettings(activeTermId:"old"))

        let result = DocumentMigration.migrate(document)
        let canonicalID = ProviderTermIdentity.canonicalID(providerId:"nfu-zhengfang",school:"广州南方学院",campus:"温泉校区",academicYear:"2025",semesterCode:"12")
        XCTAssertTrue(result.changed)
        XCTAssertEqual(result.document.terms.map(\.id), [canonicalID])
        XCTAssertEqual(result.document.terms.first?.firstWeekMonday, "2026-03-02")
        XCTAssertEqual(Set(result.document.events.compactMap(\.termId)), [canonicalID])
        XCTAssertEqual(result.document.timeRules.first?.termId, canonicalID)
        XCTAssertEqual(result.document.settings.activeTermId, canonicalID)
    }

    func testProviderAwareDocumentMigration() {
        let older = Term(id:"legacy-nfu-old",name:"2026-2027 第一学期",school:"广州南方学院",campus:"温泉校区",academicYear:"2026",semesterCode:"3",firstWeekMonday:"2026-09-01",updatedAt:"2026-07-01T00:00:00Z")
        let newer = Term(id:"legacy-nfu-new",name:"2026-2027 第一学期",school:"广州南方学院",campus:"温泉校区",academicYear:"2026",semesterCode:"3",firstWeekMonday:"2026-09-07",updatedAt:"2026-07-02T00:00:00Z")
        let otherProvider = Term(id:"other-provider",name:"2026-2027 第一学期",providerId:"other-zhengfang",school:"广州南方学院",campus:"温泉校区",academicYear:"2026",semesterCode:"3",firstWeekMonday:"2026-09-07",updatedAt:"2026-07-03T00:00:00Z")
        let manual = Term(id:"manual-term",name:"手工学期",providerId:nil,school:"示例手工学校",campus:"温泉校区",academicYear:"2026",semesterCode:"3",firstWeekMonday:"2026-09-07",updatedAt:"2026-07-04T00:00:00Z")
        let oldRule = TimeRule(id:"rule-old",termId:older.id,sections:[SectionTime(section:1,start:"08:00",end:"08:40")],updatedAt:"2026-07-01T00:00:00Z")
        let newRule = TimeRule(id:"rule-new",termId:newer.id,sections:[SectionTime(section:1,start:"08:10",end:"08:50")],updatedAt:"2026-07-02T00:00:00Z")
        let course = ScheduleEvent(id:"legacy-course",kind:.COURSE,recurrence:.WEEKLY,title:"示例课程",termId:older.id,weekday:1,weeks:[1],startTime:"08:00",endTime:"08:40",timeRuleId:oldRule.id,timeSource:.SECTION_RULE,resolutionStatus:.RESOLVED)
        let document = ScheduleDocument(
            terms:[older,newer,otherProvider,manual],
            timeRules:[oldRule,newRule],
            events:[course],
            tombstones:[
                Tombstone(id:older.id,entityType:.TERM),
                Tombstone(id:oldRule.id,entityType:.TIME_RULE),
                Tombstone(id:"deleted-event",entityType:.EVENT),
            ],
            settings:AppSettings(activeTermId:older.id,selectedAcademicProviderId:"nfu-zhengfang")
        )

        let result = DocumentMigration.migrate(document)
        let canonical = "term-376b9897fd1a5c7a"

        XCTAssertTrue(result.changed)
        XCTAssertEqual(result.document.terms.count,3)
        XCTAssertEqual(result.document.terms.first{$0.id==canonical}?.providerId,"nfu-zhengfang")
        XCTAssertEqual(result.document.terms.first{$0.providerId=="other-zhengfang"}?.providerId,"other-zhengfang")
        XCTAssertNil(result.document.terms.first{$0.school=="示例手工学校"}?.providerId)
        XCTAssertEqual(result.document.events.first?.termId,canonical)
        XCTAssertEqual(result.document.events.first?.timeRuleId,newRule.id)
        XCTAssertEqual(result.document.timeRules.first?.termId,canonical)
        XCTAssertEqual(result.document.settings.activeTermId,canonical)
        XCTAssertEqual(result.document.settings.selectedAcademicProviderId,"nfu-zhengfang")
        XCTAssertFalse(result.document.tombstones.contains{$0.id==older.id || $0.id==oldRule.id})
        XCTAssertTrue(result.document.tombstones.contains{$0.id=="deleted-event"})
    }

    func testLegacyProviderJSONDefaultsAndMigrates() throws {
        let legacy = #"{"schemaVersion":1,"documentId":"legacy-provider","updatedAt":"2026-07-15T00:00:00Z","terms":[{"id":"legacy-term","name":"2026-2027 第一学期","school":"广州南方学院","campus":"温泉校区","academicYear":"2026","semesterCode":"3","firstWeekMonday":"2026-09-07","totalWeeks":20,"timeZone":"Asia/Shanghai","updatedAt":"2026-07-15T00:00:00Z"}],"timeRules":[],"events":[],"tombstones":[],"settings":{"activeTermId":"legacy-term","dayStart":"07:30","dayEnd":"22:30","showWeekends":true}}"#
        let decoded = try JSONDecoder().decode(ScheduleDocument.self,from:Data(legacy.utf8))
        XCTAssertNil(decoded.terms.first?.providerId)
        XCTAssertEqual(decoded.settings.selectedAcademicProviderId,"nfu-zhengfang")

        let migrated = DocumentMigration.migrate(decoded).document
        XCTAssertEqual(migrated.terms.first?.providerId,"nfu-zhengfang")
        XCTAssertEqual(migrated.terms.first?.id,"term-376b9897fd1a5c7a")
        XCTAssertEqual(migrated.settings.activeTermId,migrated.terms.first?.id)
    }

    func testProviderTermIdentityMatchesAndroidAndIgnoresSchoolDisplayName() {
        let first = ProviderTermIdentity.canonicalID(providerId:"nfu-zhengfang",school:"广州南方学院",campus:"温泉校区",academicYear:"2026",semesterCode:"3")
        let second = ProviderTermIdentity.canonicalID(providerId:"nfu-zhengfang",school:"不同展示名称",campus:"温泉校区",academicYear:"2026",semesterCode:"3")
        XCTAssertEqual(first,"term-376b9897fd1a5c7a")
        XCTAssertEqual(first,second)
    }

    func testIncompleteManualTermsNeverMerge() {
        let first = Term(id:"manual-a",name:"A",firstWeekMonday:"2026-09-07")
        let second = Term(id:"manual-b",name:"B",firstWeekMonday:"2026-09-07")
        let migrated = DocumentMigration.migrate(ScheduleDocument(terms:[first,second])).document
        XCTAssertEqual(Set(migrated.terms.map(\.id)),Set(["manual-a","manual-b"]))
    }

    func testReimportPreservesExistingTermDateAndAllowsUnavailableExamSource() throws {
        let raw = #"{"ok":true,"term":{"xnm":"2026","xqm":"3"},"sources":{"courses":{"ok":true},"exams":{"ok":false},"sectionTimes":{"ok":true}},"sectionTimes":[{"section":1,"start":"08:00","end":"08:40"}],"schedule":{"kbList":[{"kch_id":"X","kcmc":"课程","xm":"教师","xqj":"1","jc":"1节","zcd":"1-18周","cdmc":"A"}]},"exams":{"items":[]}}"#
        let bundle = try parseNfu(raw, options: nfuOptions(academicYear:"2026",semesterCode:"3",termName:"",firstWeekMonday:"2026-09-01",totalWeeks:20,campus:"温泉校区"))
        XCTAssertTrue(bundle.canCommit)
        XCTAssertFalse(bundle.examSource.ok)

        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory:true)
        let repository = try ScheduleRepository(store:DocumentFileStore(directoryURL:root))
        var existing = bundle.term
        existing.firstWeekMonday = "2026-09-07"
        existing.name = "我的大四上"
        try repository.updateTerm(existing, rule:nil)
        try repository.applySchoolImport(bundle)

        XCTAssertEqual(repository.document.terms.count, 1)
        XCTAssertEqual(repository.document.terms.first?.firstWeekMonday, "2026-09-07")
        XCTAssertEqual(repository.document.terms.first?.name, "我的大四上")
    }

    func testUpdatingIdenticalSettingsDoesNotRewriteDocument() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        let store = try DocumentFileStore(directoryURL: root)
        let term = Term(id: "term", name: "当前学期", firstWeekMonday: "2026-09-07")
        let original = ScheduleDocument(
            documentId: "no-op-settings",
            updatedAt: "2026-07-15T00:00:00Z",
            terms: [term],
            settings: AppSettings(activeTermId: term.id)
        )
        try store.save(original)
        let repository = try ScheduleRepository(store: store)
        let before = try Data(contentsOf: store.documentURL)
        var changeCount = 0
        repository.onChange = { _ in changeCount += 1 }

        try repository.updateSettings(original.settings)

        XCTAssertEqual(try Data(contentsOf: store.documentURL), before)
        XCTAssertEqual(repository.document.updatedAt, original.updatedAt)
        XCTAssertEqual(changeCount, 0)
        XCTAssertFalse(FileManager.default.fileExists(atPath: store.backupURL.path))
    }

    func testUnavailableExamSourcePreservesExistingExams() {
        let term = Term(id:"term",name:"Term",firstWeekMonday:"2026-09-07")
        let exam = ScheduleEvent(id:"exam",kind:.EXAM,recurrence:.SINGLE,title:"Exam",termId:"term",date:"2027-01-01",startTime:"09:00",endTime:"11:00",timeSource:.EXPLICIT,resolutionStatus:.RESOLVED)
        let course = ScheduleEvent(id:"course",kind:.COURSE,recurrence:.WEEKLY,title:"Course",termId:"term",weekday:1,weeks:[1],startTime:"08:00",endTime:"09:00",timeSource:.SECTION_RULE,resolutionStatus:.RESOLVED)
        let bundle = AcademicImportBundle(term:term,timeRule:nil,events:[course],courseCount:1,examCount:0,unresolvedCount:0,diagnostics:[],examSource:AcademicSourceStatus(ok:false))
        let plan = SchoolImportPlan.make(document:ScheduleDocument(terms:[term],events:[exam]),bundle:bundle)
        XCTAssertEqual(plan.preservedExams.map(\.id), ["exam"])
        XCTAssertFalse(plan.removed.contains { $0.id == "exam" })
    }

    func testWeekProjectionShowsNonCurrentWeekWithoutChangingRealOccurrences() {
        let term = Term(id:"term",name:"Term",firstWeekMonday:"2026-09-07",totalWeeks:20)
        let course = ScheduleEvent(id:"late",kind:.COURSE,recurrence:.WEEKLY,title:"第八周课程",termId:"term",weekday:4,weeks:[8],startTime:"14:30",endTime:"15:55",timeSource:.SECTION_RULE,resolutionStatus:.RESOLVED)
        let document = ScheduleDocument(terms:[term],events:[course])
        let weekOneDate = ScheduleEngine.dateFor(term:term,week:1,weekday:4)!
        let projected = WeekProjectionEngine.items(on:weekOneDate,week:1,term:term,document:document)
        XCTAssertEqual(projected.count, 1)
        XCTAssertFalse(projected[0].occursThisWeek)
        XCTAssertTrue(ScheduleEngine.occurrences(on:weekOneDate,document:document).isEmpty)

        let weekEightDate = ScheduleEngine.dateFor(term:term,week:8,weekday:4)!
        let active = WeekProjectionEngine.items(on:weekEightDate,week:8,term:term,document:document)
        XCTAssertEqual(active.count, 1)
        XCTAssertTrue(active[0].occursThisWeek)
    }

    func testWeekProjectionStopsAfterCourseLastWeek() {
        let term = Term(id:"term",name:"Term",firstWeekMonday:"2026-09-07",totalWeeks:20)
        let course = ScheduleEvent(id:"ended",kind:.COURSE,recurrence:.WEEKLY,title:"第八周结课",termId:"term",weekday:4,weeks:[8],startTime:"14:30",endTime:"15:55",timeSource:.SECTION_RULE,resolutionStatus:.RESOLVED)
        let weekNineDate = ScheduleEngine.dateFor(term:term,week:9,weekday:4)!

        let projected = WeekProjectionEngine.items(on:weekNineDate,week:9,term:term,document:ScheduleDocument(terms:[term],events:[course]))

        XCTAssertTrue(projected.isEmpty)
    }

    func testWeekProjectionKeepsGapBeforeCourseLastWeek() {
        let term = Term(id:"term",name:"Term",firstWeekMonday:"2026-09-07",totalWeeks:20)
        let course = ScheduleEvent(id:"gapped",kind:.COURSE,recurrence:.WEEKLY,title:"间隔周课程",termId:"term",weekday:4,weeks:[8,10],startTime:"14:30",endTime:"15:55",timeSource:.SECTION_RULE,resolutionStatus:.RESOLVED)
        let weekNineDate = ScheduleEngine.dateFor(term:term,week:9,weekday:4)!
        let weekElevenDate = ScheduleEngine.dateFor(term:term,week:11,weekday:4)!
        let document = ScheduleDocument(terms:[term],events:[course])

        let gap = WeekProjectionEngine.items(on:weekNineDate,week:9,term:term,document:document)
        let ended = WeekProjectionEngine.items(on:weekElevenDate,week:11,term:term,document:document)

        XCTAssertEqual(gap.map(\.event.id), ["gapped"])
        XCTAssertEqual(gap.first?.occursThisWeek, false)
        XCTAssertTrue(ended.isEmpty)
    }

    func testWeekProjectionPrefersActiveVariantOverOverlappingGhost() {
        let term=Term(id:"term",name:"Term",firstWeekMonday:"2026-09-07",totalWeeks:20)
        let active=ScheduleEvent(id:"active",kind:.COURSE,recurrence:.WEEKLY,title:"实践课",teacher:"教师",termId:"term",weekday:5,weeks:[1],startTime:"14:30",endTime:"16:50",timeSource:.SECTION_RULE,resolutionStatus:.RESOLVED)
        let ghost=ScheduleEvent(id:"ghost",kind:.COURSE,recurrence:.WEEKLY,title:"实践课",teacher:"教师",termId:"term",weekday:5,weeks:[14],startTime:"14:30",endTime:"15:10",timeSource:.SECTION_RULE,resolutionStatus:.RESOLVED)
        let date=ScheduleEngine.dateFor(term:term,week:1,weekday:5)!
        let items=WeekProjectionEngine.items(on:date,week:1,term:term,document:ScheduleDocument(terms:[term],events:[active,ghost]))
        XCTAssertEqual(items.map(\.event.id),["active"])
    }

    func testWeekProjectionCountMatchesVisibleCardsAfterGhostDeduplication() {
        let term=Term(id:"term",name:"Term",firstWeekMonday:"2026-09-07",totalWeeks:20)
        let active=ScheduleEvent(id:"active",kind:.COURSE,recurrence:.WEEKLY,title:"实践课",teacher:"教师",termId:"term",weekday:5,weeks:[1],startTime:"14:30",endTime:"16:50",timeSource:.SECTION_RULE,resolutionStatus:.RESOLVED)
        let hiddenGhost=ScheduleEvent(id:"hidden",kind:.COURSE,recurrence:.WEEKLY,title:"实践课",teacher:"教师",termId:"term",weekday:5,weeks:[14],startTime:"14:30",endTime:"15:10",timeSource:.SECTION_RULE,resolutionStatus:.RESOLVED)
        let visibleGhost=ScheduleEvent(id:"visible",kind:.COURSE,recurrence:.WEEKLY,title:"晚课",teacher:"教师",termId:"term",weekday:4,weeks:[8],startTime:"20:15",endTime:"21:45",timeSource:.SECTION_RULE,resolutionStatus:.RESOLVED)
        let document=ScheduleDocument(terms:[term],events:[active,hiddenGhost,visibleGhost])

        XCTAssertEqual(WeekProjectionEngine.items(inWeek:1,term:term,document:document,showWeekends:true).count,2)
    }

    func testWeekCardMetadataFormatsTeacherAndWeeks() {
        let event=ScheduleEvent(id:"course",kind:.COURSE,recurrence:.WEEKLY,title:"课程",teacher:"李老师",weekday:2,weeks:[1,2,3,8])

        let metadata=WeekCardMetadata.make(event:event)

        XCTAssertEqual(metadata.teacherText,"李老师")
        XCTAssertEqual(metadata.weeksText,"1-3,8周")
    }

    func testWeekCardMetadataMarksMissingTeacher() {
        let event=ScheduleEvent(id:"course",kind:.COURSE,recurrence:.WEEKLY,title:"课程",teacher:"  ",weekday:2,weeks:[1])

        XCTAssertEqual(WeekCardMetadata.make(event:event).teacherText,"教师待确认")
    }

    func testWeekCardMetadataCompactsOddAndEvenWeekRanges() {
        let odd=ScheduleEvent(id:"odd",kind:.COURSE,recurrence:.WEEKLY,title:"单周课",weeks:[1,3,5,7,9,11,13,15,17])
        let even=ScheduleEvent(id:"even",kind:.COURSE,recurrence:.WEEKLY,title:"双周课",weeks:[2,4,6,8,10,12,14,16,18])

        XCTAssertEqual(WeekCardMetadata.make(event:odd).weeksText,"1-17周（单）")
        XCTAssertEqual(WeekCardMetadata.make(event:even).weeksText,"2-18周（双）")
    }

    func testWeekCardCompactDetailsPrioritizeTeacherAndWeeks() {
        let event=ScheduleEvent(id:"course",kind:.COURSE,recurrence:.WEEKLY,title:"很长的课程名",teacher:"李老师",location:"实验楼A101",weeks:[1,2,3,4])

        XCTAssertEqual(WeekCardMetadata.make(event:event).compactDetails(location:event.location),"李老师 · 1-4周 · 实验楼A101")
    }

    func testDashboardFindsNextEventAcrossDays() {
        let now = LocalValue.calendar.date(from:DateComponents(year:2026,month:7,day:13,hour:12))!
        let tomorrow = ScheduleEvent(id:"tomorrow",kind:.CUSTOM,recurrence:.SINGLE,title:"明日事件",date:"2026-07-14",startTime:"09:00",endTime:"10:00",timeSource:.USER,resolutionStatus:.RESOLVED)
        let summary = DashboardSummary.make(document:ScheduleDocument(events:[tomorrow]),now:now)
        XCTAssertEqual(summary.next?.event.id, "tomorrow")
        XCTAssertEqual(summary.upcomingSevenDays, 1)
        XCTAssertTrue(summary.today.isEmpty)
    }

    func testDestructiveDeleteCanBeUndoneFromPersistentSnapshot() throws {
        let root=FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString,isDirectory:true)
        let store=try DocumentFileStore(directoryURL:root)
        let repository=try ScheduleRepository(store:store)
        let event=ScheduleEvent(id:"undo",kind:.CUSTOM,recurrence:.SINGLE,title:"可恢复",date:"2026-07-13",startTime:"10:00",endTime:"11:00",timeSource:.USER,resolutionStatus:.RESOLVED)
        try repository.upsertEvent(event)
        try repository.deleteEvent(id:event.id)
        XCTAssertTrue(repository.document.events.isEmpty)
        XCTAssertTrue(repository.canUndoDestructiveChange)

        let reopened=try ScheduleRepository(store:store)
        XCTAssertTrue(reopened.canUndoDestructiveChange)
        try reopened.undoLastDestructiveChange()
        XCTAssertEqual(reopened.document.events.map(\.id),["undo"])
        XCTAssertFalse(reopened.document.tombstones.contains{$0.id=="undo"})
    }

    func testDataHealthFindsSemanticDuplicatesOrphansAndUnresolvedEvents() {
        let first=Term(id:"a",name:"A",school:"NFU",campus:"C",academicYear:"2026",semesterCode:"3",firstWeekMonday:"2026-09-07")
        let second=Term(id:"b",name:"B",school:"NFU",campus:"C",academicYear:"2026",semesterCode:"3",firstWeekMonday:"2026-09-07")
        let orphan=ScheduleEvent(id:"orphan",kind:.COURSE,recurrence:.WEEKLY,title:"Orphan",termId:"missing",weekday:1,weeks:[1])
        let report=DataHealth.inspect(ScheduleDocument(terms:[first,second],events:[orphan]))
        XCTAssertEqual(report.semanticDuplicateTermCount,1)
        XCTAssertEqual(report.orphanEventIDs,["orphan"])
        XCTAssertEqual(report.unresolvedEventCount,1)
        XCTAssertFalse(report.isHealthy)
    }

    func testReminderPlannerUsesOnlyRealOccurrences() {
        let term=Term(id:"term",name:"Term",firstWeekMonday:"2026-09-07",totalWeeks:20)
        let course=ScheduleEvent(id:"course",kind:.COURSE,recurrence:.WEEKLY,title:"课程",termId:"term",weekday:1,weeks:[8],startTime:"08:00",endTime:"09:00",timeSource:.SECTION_RULE,resolutionStatus:.RESOLVED)
        let document=ScheduleDocument(terms:[term],events:[course])
        let now=LocalValue.calendar.date(from:DateComponents(year:2026,month:9,day:7,hour:7))!
        let requests=ReminderPlanner.requests(document:document,now:now,offsetsByEventID:["course":[30]],horizonDays:70)
        XCTAssertEqual(requests.count,1)
        XCTAssertEqual(LocalValue.dateString(requests[0].fireDate),"2026-10-26")
        XCTAssertEqual(requests[0].fireDate,LocalValue.calendar.date(from:DateComponents(year:2026,month:10,day:26,hour:7,minute:30)))
    }

    func testWidgetNextEventIncludesDayContext() {
        let now=LocalValue.calendar.date(from:DateComponents(year:2026,month:7,day:13,hour:20))!
        let event=ScheduleEvent(id:"tomorrow",kind:.EXAM,recurrence:.SINGLE,title:"考试",date:"2026-07-14",startTime:"09:00",endTime:"11:00",timeSource:.EXPLICIT,resolutionStatus:.RESOLVED)
        let model=WidgetScheduleModel.make(document:ScheduleDocument(events:[event]),now:now)
        XCTAssertEqual(model.next?.dayLabel,"明天")
    }

    func testWidgetFindsNextEventBeyondLegacyThirtyFiveDayWindow() {
        let now=LocalValue.calendar.date(from:DateComponents(year:2026,month:7,day:1,hour:12))!
        let term=Term(id:"future-term",name:"未来学期",firstWeekMonday:"2026-09-07",totalWeeks:20)
        let course=ScheduleEvent(id:"future-course",kind:.COURSE,recurrence:.WEEKLY,title:"未来课程",termId:term.id,weekday:1,weeks:[1],startTime:"08:00",endTime:"09:25",timeSource:.SECTION_RULE,resolutionStatus:.RESOLVED)

        let model=WidgetScheduleModel.make(document:ScheduleDocument(terms:[term],events:[course]),now:now)

        XCTAssertEqual(model.next?.id,"future-course@2026-09-07")
        XCTAssertEqual(model.next?.dayLabel,"9月7日 周一")
    }

    func testWidgetChoosesNearestFutureOccurrenceAcrossTerms() {
        let now=LocalValue.calendar.date(from:DateComponents(year:2026,month:7,day:1,hour:12))!
        let oldTerm=Term(id:"old",name:"旧学期",firstWeekMonday:"2026-02-23",totalWeeks:20)
        let newTerm=Term(id:"new",name:"新学期",firstWeekMonday:"2026-09-07",totalWeeks:20)
        let oldCourse=ScheduleEvent(id:"old-course",kind:.COURSE,recurrence:.WEEKLY,title:"旧课程",termId:oldTerm.id,weekday:1,weeks:[1],startTime:"08:00",endTime:"09:00",timeSource:.SECTION_RULE,resolutionStatus:.RESOLVED)
        let futureCourse=ScheduleEvent(id:"new-course",kind:.COURSE,recurrence:.WEEKLY,title:"新课程",termId:newTerm.id,weekday:2,weeks:[1],startTime:"10:00",endTime:"11:00",timeSource:.SECTION_RULE,resolutionStatus:.RESOLVED)

        let next=ScheduleEngine.nextOccurrence(after:now,document:ScheduleDocument(terms:[oldTerm,newTerm],events:[oldCourse,futureCourse]))

        XCTAssertEqual(next?.event.id,"new-course")
        XCTAssertEqual(next.map{LocalValue.dateString($0.date)},"2026-09-08")
    }

    func testWidgetUsesRealOpeningWeekInsteadOfNonCurrentWeekProjection() {
        let now=LocalValue.calendar.date(from:DateComponents(year:2026,month:9,day:7,hour:7))!
        let term=Term(id:"term",name:"学期",firstWeekMonday:"2026-09-07",totalWeeks:20)
        let course=ScheduleEvent(id:"week-eight",kind:.COURSE,recurrence:.WEEKLY,title:"第八周课程",termId:term.id,weekday:4,weeks:[8],startTime:"15:00",endTime:"16:00",timeSource:.SECTION_RULE,resolutionStatus:.RESOLVED)
        let document=ScheduleDocument(terms:[term],events:[course])
        let projected=WeekProjectionEngine.items(inWeek:1,term:term,document:document,showWeekends:true)

        XCTAssertEqual(projected.first?.occursThisWeek,false)
        XCTAssertEqual(WidgetScheduleModel.make(document:document,now:now).next?.id,"week-eight@2026-10-29")
    }

    func testNextOccurrenceExcludesEndedAndOngoingSingleEvents() {
        let now=LocalValue.calendar.date(from:DateComponents(year:2026,month:7,day:14,hour:10,minute:30))!
        let ended=ScheduleEvent(id:"ended",kind:.CUSTOM,recurrence:.SINGLE,title:"已结束",date:"2026-07-14",startTime:"08:00",endTime:"09:00",timeSource:.USER,resolutionStatus:.RESOLVED)
        let ongoing=ScheduleEvent(id:"ongoing",kind:.CUSTOM,recurrence:.SINGLE,title:"进行中",date:"2026-07-14",startTime:"10:00",endTime:"11:00",timeSource:.USER,resolutionStatus:.RESOLVED)
        let upcoming=ScheduleEvent(id:"upcoming",kind:.CUSTOM,recurrence:.SINGLE,title:"待开始",date:"2026-07-14",startTime:"12:00",endTime:"13:00",timeSource:.USER,resolutionStatus:.RESOLVED)

        let next=ScheduleEngine.nextOccurrence(after:now,document:ScheduleDocument(events:[ended,ongoing,upcoming]))

        XCTAssertEqual(next?.event.id,"upcoming")
    }

    func testNextOccurrenceUsesStableTieBreakers() {
        let now=LocalValue.calendar.date(from:DateComponents(year:2026,month:7,day:14,hour:8))!
        let laterEnd=ScheduleEvent(id:"a",kind:.CUSTOM,recurrence:.SINGLE,title:"A",date:"2026-07-14",startTime:"10:00",endTime:"12:00",timeSource:.USER,resolutionStatus:.RESOLVED)
        let titleB=ScheduleEvent(id:"b",kind:.CUSTOM,recurrence:.SINGLE,title:"B",date:"2026-07-14",startTime:"10:00",endTime:"11:00",timeSource:.USER,resolutionStatus:.RESOLVED)
        let titleASecond=ScheduleEvent(id:"z",kind:.CUSTOM,recurrence:.SINGLE,title:"A",date:"2026-07-14",startTime:"10:00",endTime:"11:00",timeSource:.USER,resolutionStatus:.RESOLVED)
        let titleAFirst=ScheduleEvent(id:"c",kind:.CUSTOM,recurrence:.SINGLE,title:"A",date:"2026-07-14",startTime:"10:00",endTime:"11:00",timeSource:.USER,resolutionStatus:.RESOLVED)

        let next=ScheduleEngine.nextOccurrence(after:now,document:ScheduleDocument(events:[laterEnd,titleB,titleASecond,titleAFirst]))

        XCTAssertEqual(next?.event.id,"c")
    }

    func testWidgetPrivacyPresentationHidesSensitiveFutureDetails() {
        let item=WidgetEventSummary(id:"future",title:"数据库原理",startTime:"10:00",endTime:"11:00",location:"5教301",teacher:"李老师",kind:.COURSE,date:Date(),dayLabel:"明天")

        let presentation=WidgetEventPresentation.make(item:item,privacyMode:true)

        XCTAssertEqual(presentation.title,"课程")
        XCTAssertEqual(presentation.details,"10:00–11:00")
        XCTAssertFalse(presentation.details.contains("李老师"))
        XCTAssertFalse(presentation.details.contains("5教301"))
    }
    func testWidgetUpcomingSequenceUsesConfiguredLimitAndExcludesCurrent() {
        let now=LocalValue.calendar.date(from:DateComponents(year:2026,month:7,day:14,hour:10,minute:30))!
        let current=ScheduleEvent(id:"current",kind:.COURSE,recurrence:.SINGLE,title:"当前",date:"2026-07-14",startTime:"10:00",endTime:"11:00",timeSource:.USER,resolutionStatus:.RESOLVED)
        let today=ScheduleEvent(id:"today",kind:.COURSE,recurrence:.SINGLE,title:"今天稍后",date:"2026-07-14",startTime:"12:00",endTime:"13:00",timeSource:.USER,resolutionStatus:.RESOLVED)
        let tomorrow=ScheduleEvent(id:"tomorrow",kind:.EXAM,recurrence:.SINGLE,title:"明天考试",date:"2026-07-15",startTime:"09:00",endTime:"11:00",timeSource:.EXPLICIT,resolutionStatus:.RESOLVED)
        let later=ScheduleEvent(id:"later",kind:.COURSE,recurrence:.SINGLE,title:"后天课程",date:"2026-07-16",startTime:"08:00",endTime:"09:00",timeSource:.USER,resolutionStatus:.RESOLVED)
        let document=ScheduleDocument(events:[later,current,tomorrow,today],settings:AppSettings(widgetUpcomingCount:2))

        let model=WidgetScheduleModel.make(document:document,now:now)

        XCTAssertEqual(model.primary?.id,"current@2026-07-14")
        XCTAssertEqual(model.upcoming.map(\.id),["today@2026-07-14","tomorrow@2026-07-15"])
        XCTAssertEqual(model.next?.id,"today@2026-07-14")
    }
    func testWidgetAgendaPolicyRespectsFamilyCapacity() {
        let day=LocalValue.calendar.startOfDay(for:Date())
        let items=(1...5).map{WidgetEventSummary(id:"\($0)",title:"课程\($0)",startTime:"10:00",endTime:"11:00",location:"A",teacher:"教师",kind:.COURSE,date:day,dayLabel:"今天")}
        let current=WidgetEventSummary(id:"current",title:"当前",startTime:"09:00",endTime:"10:30",location:"A",teacher:"教师",kind:.COURSE,date:day,dayLabel:"今天")
        let model=WidgetScheduleModel(primary:current,upcoming:items,today:[],status:"正在进行")

        XCTAssertEqual(WidgetAgendaPolicy.make(model:model,size:.small).upcoming.count,0)
        XCTAssertEqual(WidgetAgendaPolicy.make(model:model,size:.medium).upcoming.map(\.id),["1","2"])
        XCTAssertEqual(WidgetAgendaPolicy.make(model:model,size:.large).upcoming.map(\.id),["1","2","3","4","5"])
    }
    func testSchoolRecordIdentityIgnoresMutableTeacherLocationAndTime() throws {
        let template = #"{"ok":true,"term":{"xnm":"2026","xqm":"3"},"sources":{"courses":{"ok":true},"exams":{"ok":true},"sectionTimes":{"ok":true}},"sectionTimes":[{"section":1,"start":"08:00","end":"08:40"}],"schedule":{"kbList":[{"jxb_id":"CLASS-X","kcmc":"课程","xm":"TEACHER","xqj":"1","jc":"1节","zcd":"1-18周","cdmc":"ROOM"}]},"exams":{"items":[{"ks_id":"EXAM-X","kcmc":"课程","kssj":"DATE 09:00-11:00","cdmc":"ROOM"}]}}"#
        let firstRaw=template.replacingOccurrences(of:"TEACHER",with:"甲").replacingOccurrences(of:"ROOM",with:"A101").replacingOccurrences(of:"DATE",with:"2027-01-01")
        let secondRaw=template.replacingOccurrences(of:"TEACHER",with:"乙").replacingOccurrences(of:"ROOM",with:"B202").replacingOccurrences(of:"DATE",with:"2027-01-02")
        let options=nfuOptions(academicYear:"2026",semesterCode:"3",termName:"",firstWeekMonday:"2026-09-07",totalWeeks:20,campus:"温泉校区")
        let first=try parseNfu(firstRaw,options:options)
        let second=try parseNfu(secondRaw,options:options)
        XCTAssertEqual(first.events.first{$0.kind == .COURSE}?.id,second.events.first{$0.kind == .COURSE}?.id)
        XCTAssertEqual(first.events.first{$0.kind == .EXAM}?.id,second.events.first{$0.kind == .EXAM}?.id)
    }
    func testValidatorRejectsOrphanReferencesAndInvalidTimelineSettings() {
        let event=ScheduleEvent(id:"orphan",kind:.COURSE,recurrence:.WEEKLY,title:"课程",termId:"missing",weekday:1,weeks:[1],startTime:"08:00",endTime:"09:00",timeSource:.USER,resolutionStatus:.RESOLVED)
        XCTAssertThrowsError(try ScheduleDocumentValidator.validate(ScheduleDocument(events:[event])))
        XCTAssertThrowsError(try ScheduleDocumentValidator.validate(ScheduleDocument(settings:AppSettings(dayStart:"22:00",dayEnd:"08:00"))))
    }
    func testWidgetUpcomingCountDefaultsForLegacyJSONAndRoundTrips() throws {
        let legacy = #"{"activeTermId":null,"dayStart":"07:30","dayEnd":"22:30","showWeekends":true}"#.data(using: .utf8)!
        let decoded = try JSONDecoder().decode(AppSettings.self, from: legacy)
        XCTAssertEqual(decoded.widgetUpcomingCount, 5)

        var configured = decoded
        configured.widgetUpcomingCount = 5
        let roundTrip = try JSONDecoder().decode(AppSettings.self, from: JSONEncoder().encode(configured))
        XCTAssertEqual(roundTrip.widgetUpcomingCount, 5)
    }
    func testWidgetUpcomingCountValidatorAcceptsOnlyOneThroughFive() {
        XCTAssertNoThrow(try ScheduleDocumentValidator.validate(ScheduleDocument(settings:AppSettings(widgetUpcomingCount:1))))
        XCTAssertNoThrow(try ScheduleDocumentValidator.validate(ScheduleDocument(settings:AppSettings(widgetUpcomingCount:5))))
        XCTAssertThrowsError(try ScheduleDocumentValidator.validate(ScheduleDocument(settings:AppSettings(widgetUpcomingCount:0))))
        XCTAssertThrowsError(try ScheduleDocumentValidator.validate(ScheduleDocument(settings:AppSettings(widgetUpcomingCount:6))))
    }
    func testApplicationPresencePolicy(){XCTAssertEqual(ApplicationPresencePolicy.desiredPresence(visibleUserWindowCount:0),.silentBackground);XCTAssertEqual(ApplicationPresencePolicy.desiredPresence(visibleUserWindowCount:1),.foreground);XCTAssertEqual(ApplicationPresencePolicy.desiredPresence(visibleUserWindowCount:3),.foreground)}
    func testWidgetTimelineRefreshPolicyReloadsAfterLifecycleBoundaries() {
        XCTAssertTrue(WidgetTimelineRefreshPolicy.shouldReload(for: .applicationLaunch))
        XCTAssertTrue(WidgetTimelineRefreshPolicy.shouldReload(for: .applicationReopen))
        XCTAssertTrue(WidgetTimelineRefreshPolicy.shouldReload(for: .applicationActivation))
        XCTAssertFalse(WidgetTimelineRefreshPolicy.shouldReload(for: .windowVisibilityChange))

        let firstReload = Date(timeIntervalSince1970: 100)
        XCTAssertFalse(WidgetTimelineRefreshPolicy.shouldReload(
            for: .applicationActivation,
            lastReloadAt: firstReload,
            now: firstReload.addingTimeInterval(1)
        ))
        XCTAssertTrue(WidgetTimelineRefreshPolicy.shouldReload(
            for: .applicationReopen,
            lastReloadAt: firstReload,
            now: firstReload.addingTimeInterval(3)
        ))
    }
    func testSyncSettingsMigratesLegacyUserDefaultsTokenIntoSecretStore() throws {
        let suite = "courseaxis-sync-\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let secrets = TestKeychainSecretStore()
        defaults.set("https://sync.example.test", forKey: "sync.serverURL")
        defaults.set("legacy-token-value-123456789", forKey: "sync.token")
        defaults.set(9, forKey: "sync.revision")
        let store = SyncSettingsStore(defaults: defaults, secretStore: secrets)

        let migrated = try XCTUnwrap(store.load())

        XCTAssertEqual(migrated.token, "legacy-token-value-123456789")
        XCTAssertEqual(migrated.revision, 9)
        XCTAssertNil(defaults.object(forKey: "sync.token"))
        XCTAssertEqual(secrets.values.count, 1)

        var updated = migrated
        updated.token = "updated-token-value-123456789"
        try store.save(updated)
        XCTAssertEqual(try store.load()?.token, updated.token)
        XCTAssertNil(defaults.object(forKey: "sync.token"))

        try store.clear()
        XCTAssertNil(try store.load())
        XCTAssertTrue(secrets.values.isEmpty)
    }

    func testSyncSettingsNeverFallsBackToLegacyPlaintextWhenKeychainSaveFails() throws {
        let suite = "courseaxis-sync-failure-\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let secrets = TestKeychainSecretStore()
        secrets.failSave = true
        defaults.set("https://sync.example.test", forKey: "sync.serverURL")
        defaults.set("fallback-must-not-be-used", forKey: "sync.token")
        let store = SyncSettingsStore(defaults: defaults, secretStore: secrets)

        XCTAssertThrowsError(try store.load())
        XCTAssertNil(defaults.object(forKey: "sync.token"))
        XCTAssertTrue(secrets.values.isEmpty)
    }

    func testAppGroupMigrationPreservesDocumentBytesAndCreatesReadOnlyBackup() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let legacy = root.appendingPathComponent("legacy", isDirectory: true)
        let group = root.appendingPathComponent("group", isDirectory: true)
        let legacyStore = try DocumentFileStore(directoryURL: legacy)
        let document = ScheduleDocument(documentId: "migration-source")
        try legacyStore.save(document)
        let original = try Data(contentsOf: legacyStore.documentURL)

        let result = try AppGroupMigration.migrate(
            legacyDirectory: legacy,
            groupDirectory: group,
            now: Date(timeIntervalSince1970: 1_700_000_000)
        )

        let targetStore = try DocumentFileStore(directoryURL: group)
        XCTAssertEqual(try Data(contentsOf: targetStore.documentURL), original)
        XCTAssertEqual(try targetStore.load(), document)
        XCTAssertFalse(FileManager.default.fileExists(atPath: legacyStore.documentURL.path))
        guard case .migrated(let backupURL) = result else {
            return XCTFail("expected migrated result")
        }
        XCTAssertEqual(try Data(contentsOf: backupURL), original)
        let permissions = try XCTUnwrap(
            FileManager.default.attributesOfItem(atPath: backupURL.path)[.posixPermissions] as? NSNumber
        ).intValue
        XCTAssertEqual(permissions & 0o222, 0)
    }

    func testAppGroupMigrationNeverOverwritesExistingTarget() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let legacy = root.appendingPathComponent("legacy", isDirectory: true)
        let group = root.appendingPathComponent("group", isDirectory: true)
        let legacyStore = try DocumentFileStore(directoryURL: legacy)
        let targetStore = try DocumentFileStore(directoryURL: group)
        try legacyStore.save(ScheduleDocument(documentId: "legacy"))
        try targetStore.save(ScheduleDocument(documentId: "target"))
        let targetBefore = try Data(contentsOf: targetStore.documentURL)

        XCTAssertEqual(
            try AppGroupMigration.migrate(legacyDirectory: legacy, groupDirectory: group),
            .targetAlreadyExists
        )

        XCTAssertEqual(try Data(contentsOf: targetStore.documentURL), targetBefore)
        XCTAssertTrue(FileManager.default.fileExists(atPath: legacyStore.documentURL.path))
    }

    func testAppGroupMigrationFailureKeepsLegacyAndRemovesPartialTarget() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let legacy = root.appendingPathComponent("legacy", isDirectory: true)
        let group = root.appendingPathComponent("group", isDirectory: true)
        try FileManager.default.createDirectory(at: legacy, withIntermediateDirectories: true)
        let legacyURL = legacy.appendingPathComponent("schedule.document.json")
        try Data("not-json".utf8).write(to: legacyURL)

        XCTAssertThrowsError(
            try AppGroupMigration.migrate(legacyDirectory: legacy, groupDirectory: group)
        )

        XCTAssertTrue(FileManager.default.fileExists(atPath: legacyURL.path))
        XCTAssertFalse(
            FileManager.default.fileExists(
                atPath: group.appendingPathComponent("schedule.document.json").path
            )
        )
    }

    func testClearAllLocalDataRemovesDocumentsBackupsAndRecovery() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let legacyDirectory = root.appendingPathComponent("legacy", isDirectory: true)
        try FileManager.default.createDirectory(at: legacyDirectory, withIntermediateDirectories: true)
        try Data("old".utf8).write(to: legacyDirectory.appendingPathComponent("schedule.document.json"))
        let store = try DocumentFileStore(
            directoryURL: root.appendingPathComponent("group", isDirectory: true),
            legacyDirectoryURL: legacyDirectory
        )
        let document = ScheduleDocument(documentId: "private-local-data")
        try store.save(document)
        try store.saveRecoverySnapshot(document)
        try Data("backup".utf8).write(to: store.backupURL)
        try Data("migration".utf8).write(to: store.migrationBackupURL)
        let migrationBackups = store.directoryURL.appendingPathComponent("Migration Backups", isDirectory: true)
        try FileManager.default.createDirectory(at: migrationBackups, withIntermediateDirectories: true)
        try Data("legacy".utf8).write(to: migrationBackups.appendingPathComponent("legacy.json"))
        let repository = try ScheduleRepository(store: store)

        try repository.clearAllLocalData()

        XCTAssertEqual(repository.document, .empty)
        XCTAssertFalse(FileManager.default.fileExists(atPath: store.documentURL.path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: store.backupURL.path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: store.migrationBackupURL.path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: store.recoveryURL.path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: migrationBackups.path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: legacyDirectory.path))
    }
    func testCourseMetadataPresentation() {
        let event=ScheduleEvent(id:"course",kind:.COURSE,recurrence:.WEEKLY,title:"人工智能原理",teacher:"魏俊华",location:"温泉校区 2实407",weekday:4,weeks:Set(1...18),startTime:"10:35",endTime:"12:00",courseNature:"专业必修课",totalHours:"34",credits:"2.0")

        let metadata=WeekCardMetadata.make(event:event)

        XCTAssertEqual(metadata.teacherText,"魏俊华")
        XCTAssertEqual(metadata.weeksText,"1-18周")
        XCTAssertTrue(metadata.fullDetails(event:event).contains("性质：专业必修课"))
        XCTAssertTrue(metadata.fullDetails(event:event).contains("总学时：34"))
        XCTAssertTrue(metadata.fullDetails(event:event).contains("学分：2.0"))
    }
    func testWidgetWeekRoute() {
        let now=LocalValue.calendar.date(from:DateComponents(year:2026,month:9,day:7,hour:7))!
        let term=Term(id:"term",name:"学期",firstWeekMonday:"2026-09-07",totalWeeks:20)
        let course=ScheduleEvent(id:"course",kind:.COURSE,recurrence:.WEEKLY,title:"人工智能原理",teacher:"魏俊华",location:"温泉校区 2实407",termId:term.id,weekday:1,weeks:Set(1...18),startTime:"08:00",endTime:"09:25",timeSource:.SECTION_RULE,resolutionStatus:.RESOLVED,courseNature:"专业必修课",totalHours:"34",credits:"2.0")
        let summary=WidgetScheduleModel.make(document:ScheduleDocument(terms:[term],events:[course]),now:now).next!
        let presentation=WidgetEventPresentation.make(item:summary,privacyMode:false)

        XCTAssertEqual(summary.weeksText,"1-18")
        XCTAssertTrue(presentation.details.contains("1-18"))
        XCTAssertFalse(presentation.details.contains("专业必修课"))
        XCTAssertFalse(presentation.details.contains("34"))
        XCTAssertFalse(presentation.details.contains("2.0"))
        XCTAssertEqual(AppNavigationRoute.parse(URL(string:"courseaxis://week")!),.week)
        XCTAssertEqual(AppNavigationRoute.parse(URL(string:"courseaxis://unknown")!),.week)
        XCTAssertEqual(PrimaryNavigationDestination.allCases,[.week,.importSchool,.settings])
        XCTAssertEqual(PrimaryNavigationPolicy.defaultDestination,.week)
    }
    func testWeekEventFilterKeepsCoursesAndExamsInOneTimeline(){
        let date=LocalValue.calendar.date(from:DateComponents(year:2026,month:7,day:15))!
        let course=ProjectedOccurrence(event:ScheduleEvent(id:"c",kind:.COURSE,recurrence:.WEEKLY,title:"课程"),date:date,start:480,end:540,occursThisWeek:true)
        let exam=ProjectedOccurrence(event:ScheduleEvent(id:"e",kind:.EXAM,recurrence:.SINGLE,title:"考试"),date:date,start:600,end:720,occursThisWeek:true)
        let custom=ProjectedOccurrence(event:ScheduleEvent(id:"x",kind:.CUSTOM,recurrence:.SINGLE,title:"自定义"),date:date,start:750,end:780,occursThisWeek:true)
        let items=[course,exam,custom]

        XCTAssertEqual(WeekEventFilter.apply(items,mode:.all),items)
        XCTAssertEqual(WeekEventFilter.apply(items,mode:.course),[course])
        XCTAssertEqual(WeekEventFilter.apply(items,mode:.exam),[exam])
    }
    func testWeeksAndSections(){XCTAssertEqual(WeekParser.parse("1-17周(单)"),Set([1,3,5,7,9,11,13,15,17]));XCTAssertEqual(SectionParser.parse("(1-3节)"),[1,2,3])}
    func testDynamicTimes(){let rule=TimeRule(id:"r",termId:"t",sections:[SectionTime(section:1,start:"08:00",end:"08:40"),SectionTime(section:2,start:"08:45",end:"09:25"),SectionTime(section:9,start:"16:20",end:"17:00"),SectionTime(section:10,start:"17:05",end:"17:45")]);let resolved=TimeResolver.resolve([1,2],rule:rule);XCTAssertEqual(resolved?.0,480);XCTAssertEqual(resolved?.1,565);XCTAssertEqual(TimeResolver.infer(start:960,end:1080,rule:rule)?.exact,false)}
    func testManualConflict(){let a=ScheduleEvent(id:"a",kind:.COURSE,recurrence:.SINGLE,title:"A",date:"2026-07-10",startTime:"17:30",endTime:"19:00",timeSource:.USER,resolutionStatus:.RESOLVED),b=ScheduleEvent(id:"b",kind:.EXAM,recurrence:.SINGLE,title:"B",date:"2026-07-10",startTime:"16:00",endTime:"18:00",timeSource:.USER,resolutionStatus:.RESOLVED);let pair=ScheduleEngine.conflicts(candidate:b,document:ScheduleDocument(events:[a])).first!;XCTAssertEqual(ScheduleEngine.overlapMinutes(pair.0,pair.1),30)}
    func testExamAndStableID(){let exam=ExamTimeParser.parse("2026年7月10日 16:00—18:00");XCTAssertEqual(exam?.start,960);XCTAssertEqual(StableID.make("term","广州南方学院","2025","12","2026-02-23"),"term-d248459c24acef18")}
    func testExamFocusChoosesTheWeekWithFourFinalExams() {
        let earlier=Term(id:"earlier",name:"较早考试",firstWeekMonday:"2026-04-20",totalWeeks:20)
        let finals=Term(id:"finals",name:"2025-2026 第二学期",firstWeekMonday:"2026-03-02",totalWeeks:20)
        func exam(_ id:String,_ term:Term,_ date:String)->ScheduleEvent{
            ScheduleEvent(id:id,kind:.EXAM,recurrence:.SINGLE,title:id,termId:term.id,date:date,startTime:"09:00",endTime:"11:00",timeSource:.EXPLICIT,resolutionStatus:.RESOLVED)
        }
        let document=ScheduleDocument(
            terms:[earlier,finals],
            events:[
                exam("early-1",earlier,"2026-04-25"),exam("early-2",earlier,"2026-04-26"),
                exam("final-1",finals,"2026-07-06"),exam("final-2",finals,"2026-07-07"),
                exam("final-3",finals,"2026-07-07"),exam("final-4",finals,"2026-07-08")
            ],
            settings:AppSettings(activeTermId:earlier.id)
        )

        XCTAssertEqual(ExamFocusPolicy.resolve(document),ExamFocus(termId:finals.id,weekNumber:19,examCount:4))
    }
    func testExamFocusBreaksCountTiesWithTheActiveTermAndIgnoresInvalidEvents() {
        let first=Term(id:"first",name:"第一学期",firstWeekMonday:"2026-09-07",totalWeeks:20)
        let second=Term(id:"second",name:"第二学期",firstWeekMonday:"2026-09-07",totalWeeks:20)
        func exam(_ id:String,_ term:Term)->ScheduleEvent{
            ScheduleEvent(id:id,kind:.EXAM,recurrence:.SINGLE,title:id,termId:term.id,date:"2026-09-08",startTime:"09:00",endTime:"11:00",timeSource:.EXPLICIT,resolutionStatus:.RESOLVED)
        }
        var invalid=exam("weekly",first)
        invalid.recurrence = .WEEKLY
        let document=ScheduleDocument(
            terms:[first,second],
            events:[exam("first-exam",first),exam("second-exam",second),invalid],
            settings:AppSettings(activeTermId:second.id)
        )

        XCTAssertEqual(ExamFocusPolicy.resolve(document),ExamFocus(termId:second.id,weekNumber:1,examCount:1))
        XCTAssertNil(ExamFocusPolicy.resolve(ScheduleDocument(terms:[first])))
    }
    func testMergeTombstone(){let a=ScheduleEvent(id:"a",kind:.CUSTOM,recurrence:.SINGLE,title:"A",date:"2026-07-10",startTime:"10:00",endTime:"11:00",timeSource:.USER,resolutionStatus:.RESOLVED,updatedAt:"2026-07-11T00:00:00Z");let local=ScheduleDocument(updatedAt:"2026-07-11T00:00:00Z",events:[a]);let remote=ScheduleDocument(updatedAt:"2026-07-12T00:00:00Z",tombstones:[Tombstone(id:"a",entityType:.EVENT,deletedAt:"2026-07-12T00:00:00Z")]);XCTAssertTrue(MergeEngine.merge(local,remote).events.isEmpty)}
    func testImporter()throws{let raw=#"{"ok":true,"diagnostics":[],"sectionTimes":[{"section":1,"start":"08:00","end":"08:40"},{"section":2,"start":"08:45","end":"09:25"}],"schedule":{"kbList":[{"kch_id":"X","kcmc":"课程","xm":"教师","xqj":"2","jc":"1-2节","zcd":"1-18周","cdmc":"A"}],"sjkList":[]},"exams":{"items":[{"kch":"Y","kcmc":"考试课","kssj":"2026-07-10 16:00-18:00","cdmc":"B"}]}}"#;let bundle=try parseNfu(raw,options:nfuOptions(academicYear:"2025",semesterCode:"12",termName:"term",firstWeekMonday:"2026-02-23",totalWeeks:20));XCTAssertEqual(bundle.courseCount,1);XCTAssertEqual(bundle.examCount,1);XCTAssertEqual(bundle.events.first{$0.kind == .COURSE}?.startTime,"08:00");XCTAssertEqual(bundle.events.first{$0.kind == .EXAM}?.startTime,"16:00")}
}

private final class TestKeychainSecretStore: KeychainSecretStoring {
    var values: [String: String] = [:]
    var failSave = false

    func read(service: String, account: String) throws -> String? {
        values["\(service)|\(account)"]
    }

    func save(_ value: String, service: String, account: String) throws {
        if failSave { throw NSError(domain: "TestKeychain", code: 1) }
        values["\(service)|\(account)"] = value
    }

    func delete(service: String, account: String) throws {
        values.removeValue(forKey: "\(service)|\(account)")
    }
}
