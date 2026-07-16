import Foundation
@testable import CourseAxisCore

private var failures = 0
private let nfuProvider = AcademicProvider(
    id: "nfu-zhengfang",
    schoolName: "广州南方学院",
    vendor: "ZHENGFANG",
    allowedOrigin: "https://jwxt.nfu.edu.cn",
    pathPrefix: "/jwglxt/",
    loginPath: "/jwglxt/xtgl/login_slogin.html",
    homePath: "/jwglxt/xtgl/index_initMenu.html",
    requiresThirdPartyCookies: false,
    semesterCodes: ["first": "3", "second": "12", "third": "16"],
    adapterId: "zhengfang-v1",
    sessionNamespace: "7c8cd13a-2094-5c1b-9d84-2ee62d7a4f70"
)
private func check(_ condition: @autoclosure () -> Bool, _ message: String) {
    if !condition() {
        failures += 1
        fputs("FAIL: \(message)\n", stderr)
    }
}

private func nfuOptions(
    academicYear: String,
    semesterCode: String,
    termName: String,
    firstWeekMonday: String,
    totalWeeks: Int,
    campus: String = ""
) -> AcademicImportOptions {
    AcademicImportOptions(
        provider: nfuProvider,
        academicYear: academicYear,
        semesterCode: semesterCode,
        termName: termName,
        firstWeekMonday: firstWeekMonday,
        totalWeeks: totalWeeks,
        campus: campus
    )
}

private func parseNfu(_ raw: String, options: AcademicImportOptions) throws -> AcademicImportBundle {
    try AcademicImportAdapterRegistry.adapter(for: options.provider).parse(raw, options: options)
}

private func makeEvent(
    id: String,
    title: String,
    date: String = "2026-07-10",
    start: String,
    end: String,
    source: TimeSource = .USER,
    updatedAt: String = "2026-07-11T00:00:00Z"
) -> ScheduleEvent {
    ScheduleEvent(
        id: id,
        kind: .CUSTOM,
        recurrence: .SINGLE,
        title: title,
        date: date,
        startTime: start,
        endTime: end,
        timeSource: source,
        resolutionStatus: .RESOLVED,
        updatedAt: updatedAt
    )
}

private func validatePersistenceAndRepository() throws {
    let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
    let store = try DocumentFileStore(directoryURL: root)
    let original = ScheduleDocument(events: [makeEvent(id: "a", title: "A", start: "16:00", end: "18:00")])
    try store.save(original)
    let loaded = try store.load()
    check(loaded == original, "saved document must round-trip")

    let replacement = ScheduleDocument(events: [makeEvent(id: "b", title: "B", start: "17:30", end: "19:00")])
    try store.save(replacement)
    check(FileManager.default.fileExists(atPath: store.backupURL.path), "second save must preserve a backup")

    try Data("not-json".utf8).write(to: store.documentURL, options: .atomic)
    do {
        _ = try store.load()
        check(false, "corrupt documents must be rejected")
    } catch {
        check(true, "corrupt documents are rejected")
    }

    let repoStore = try DocumentFileStore(directoryURL: root.appendingPathComponent("repo", isDirectory: true))
    let repository = try ScheduleRepository(store: repoStore)
    try repository.upsertEvent(makeEvent(id: "user", title: "User corrected", start: "16:00", end: "18:00"))
    try repository.deleteEvent(id: "user")
    check(repository.document.events.isEmpty, "delete removes the event")
    check(repository.document.tombstones.contains { $0.id == "user" && $0.entityType == .EVENT }, "delete creates a tombstone")

    let term = Term(id: "term", name: "Term", firstWeekMonday: "2026-02-23")
    try repository.updateTerm(term, rule: nil)
    var corrected = makeEvent(id: "school", title: "Corrected", start: "16:00", end: "18:00")
    corrected.kind = .COURSE
    corrected.recurrence = .WEEKLY
    corrected.termId = term.id
    corrected.date = nil
    corrected.weekday = 2
    corrected.weeks = [1]
    try repository.upsertEvent(corrected)
    var imported = corrected
    imported.title = "School value"
    imported.startTime = "08:00"
    imported.endTime = "09:25"
    imported.timeSource = .SECTION_RULE
    try repository.applySchoolImport(AcademicImportBundle(term: term, timeRule: nil, events: [imported], courseCount: 1, examCount: 0, unresolvedCount: 0, diagnostics: []))
    check(repository.document.events.first { $0.id == "school" }?.startTime == "16:00", "school reimport preserves USER time")
}

private func validateAppGroupMigration() throws {
    let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
    let legacyDirectory = root.appendingPathComponent("legacy-group", isDirectory: true)
    let groupDirectory = root.appendingPathComponent("app-group", isDirectory: true)
    let legacyStore = try DocumentFileStore(directoryURL: legacyDirectory)
    let legacyDocument = ScheduleDocument(events: [makeEvent(id: "migrated", title: "Migrated", start: "16:00", end: "18:00")])
    try legacyStore.save(legacyDocument)

    let migration = try AppGroupMigration.migrate(
        legacyDirectory: legacyDirectory,
        groupDirectory: groupDirectory
    )
    if case .migrated = migration {
        check(true, "App Group migration reports success")
    } else {
        check(false, "App Group migration reports success")
    }
    let migratedStore = try DocumentFileStore(directoryURL: groupDirectory)
    let migratedDocument = try migratedStore.load()
    check(migratedDocument == legacyDocument, "App Group migration preserves the existing document")

    let localDocument = ScheduleDocument(events: [makeEvent(id: "local", title: "Local", start: "17:00", end: "19:00")])
    try migratedStore.save(localDocument)
    try legacyStore.save(legacyDocument)
    let repeated = try AppGroupMigration.migrate(
        legacyDirectory: legacyDirectory,
        groupDirectory: groupDirectory
    )
    check(repeated == .targetAlreadyExists, "App Group migration detects an existing target")
    let reopenedDocument = try DocumentFileStore(directoryURL: groupDirectory).load()
    check(reopenedDocument == localDocument, "App Group migration never overwrites an existing target")

    let isolatedGroup = root.appendingPathComponent("isolated-group", isDirectory: true)
    let isolatedLegacy = root.appendingPathComponent("isolated-legacy", isDirectory: true)
    let isolatedResult = try AppGroupMigration.migrate(
        legacyDirectory: isolatedLegacy,
        groupDirectory: isolatedGroup
    )
    check(isolatedResult == .noLegacyDocument, "App Group migration handles a fresh install")
    let isolatedStore = try DocumentFileStore(directoryURL: isolatedGroup)
    let isolatedDocument = try isolatedStore.load()
    check(isolatedDocument.events.isEmpty, "fresh App Group store starts empty")
}

private func validateWidgetModel() {
    let now = LocalValue.calendar.date(from: DateComponents(year: 2026, month: 7, day: 10, hour: 16, minute: 30))!
    let current = makeEvent(id: "exam", title: "Database Exam", start: "16:00", end: "18:00")
    let next = makeEvent(id: "next", title: "Next class", start: "19:00", end: "20:00")
    let document = ScheduleDocument(events: [current, next])
    let model = WidgetScheduleModel.make(document: document, now: now)
    check(model.primary?.title == "Database Exam", "widget identifies current event")
    check(model.next?.title == "Next class", "widget identifies next event")
    check(model.upcoming.map(\.title) == ["Next class"], "widget keeps an ordered upcoming agenda")
    let refresh = WidgetScheduleModel.nextRefreshDate(document: document, now: now)
    check(refresh == LocalValue.calendar.date(from: DateComponents(year: 2026, month: 7, day: 10, hour: 18)), "widget refreshes at the next actual event boundary without polling")
    let emptyRefresh = WidgetScheduleModel.nextRefreshDate(document: .empty, now: now)
    check(emptyRefresh == LocalValue.calendar.date(from: DateComponents(year: 2026, month: 7, day: 11)), "empty widget waits until next midnight instead of waking every 30 minutes")
}

private func validatePolicies() {
    check(AcademicNavigationPolicy.isEmbeddedURLAllowed(URL(string: "https://jwxt.nfu.edu.cn/jwglxt/")!, provider: nfuProvider), "exact NFU provider path is embedded")
    check(!AcademicNavigationPolicy.isEmbeddedURLAllowed(URL(string: "https://portal.nfu.edu.cn/jwglxt/")!, provider: nfuProvider), "other NFU subdomains are rejected")
    check(!AcademicNavigationPolicy.isEmbeddedURLAllowed(URL(string: "https://jwxt.nfu.edu.cn/")!, provider: nfuProvider), "paths outside the provider prefix are rejected")
    check(!AcademicNavigationPolicy.isEmbeddedURLAllowed(URL(string: "https://example.com/")!, provider: nfuProvider), "external domain is rejected")
    check(AcademicNavigationPolicy.destination(for: URL(string: "https://jwxt.nfu.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html")!, provider: nfuProvider, hasTargetFrame: false) == .currentWebView, "NFU target=_blank pages stay inside the current web view")
    check(AcademicNavigationPolicy.destination(for: URL(string: "https://jwxt.nfu.edu.cn/jwglxt/")!, provider: nfuProvider, hasTargetFrame: true) == .normalNavigation, "normal NFU links use ordinary navigation")
    check(AcademicNavigationPolicy.destination(for: URL(string: "https://example.com/")!, provider: nfuProvider, hasTargetFrame: false) == .externalBrowser, "external popups leave the embedded session")
    check(AcademicNavigationPolicy.destination(for: URL(string: "javascript:alert(1)")!, provider: nfuProvider, hasTargetFrame: false) == .blocked, "dangerous schemes are blocked")
    check(SyncConfigurationPolicy.validate(serverURL: "http://127.0.0.1:8787", token: String(repeating: "a", count: 24)) != nil, "localhost HTTP is accepted")
    check(SyncConfigurationPolicy.validate(serverURL: "http://example.com", token: String(repeating: "a", count: 24)) == nil, "public HTTP is rejected")
    let invocation = try! AcademicImportAdapterRegistry.adapter(for: nfuProvider).makeInvocation(provider: nfuProvider, academicYear: "2025", semesterCode: "12", script: "(async function(){ return 1; })();")
    check(invocation.contains("window.__COURSEAXIS_OPTIONS__"), "NFU invocation injects term options")
    check(invocation.contains("https://jwxt.nfu.edu.cn"), "NFU invocation injects the exact provider origin")
    check(invocation.contains("/jwglxt/"), "NFU invocation injects the provider path prefix")
    check(invocation.hasSuffix("\nvoid 0;"), "NFU async script result is discarded before WKWebView bridges it")
}

private func validateExistingCoreAndImporter() throws {
    check(WeekParser.parse("1-17周(单)") == Set([1,3,5,7,9,11,13,15,17]), "odd weeks parse correctly")
    let rule = TimeRule(id: "rule", termId: "term", sections: [SectionTime(section: 1, start: "08:00", end: "08:40"), SectionTime(section: 2, start: "08:45", end: "09:25")])
    let resolved = TimeResolver.resolve([1,2], rule: rule)
    check(resolved?.0 == 480 && resolved?.1 == 565, "section rules retain the real break and end time")
    let raw = #"{"ok":true,"diagnostics":[],"sectionTimes":[{"section":1,"start":"08:00","end":"08:40"},{"section":2,"start":"08:45","end":"09:25"}],"schedule":{"kbList":[{"kch_id":"X","kcmc":"课程","xm":"教师","xqj":"2","jc":"1-2节","zcd":"1-18周","cdmc":"A"}],"sjkList":[]},"exams":{"items":[{"kch":"Y","kcmc":"考试课","kssj":"2026-07-10 16:00-18:00","cdmc":"B"}]}}"#
    let bundle = try parseNfu(raw, options: nfuOptions(academicYear: "2025", semesterCode: "12", termName: "term", firstWeekMonday: "2026-02-23", totalWeeks: 20))
    check(bundle.courseCount == 1 && bundle.examCount == 1, "NFU importer maps course and exam")
    check(bundle.events.first { $0.kind == .COURSE }?.startTime == "08:00", "imported course uses real section start")
    check(bundle.events.first { $0.kind == .EXAM }?.recurrence == .SINGLE, "imported exam is a single event")
}

private func validateNfuImportCompleteness() throws {
    let calendar = NfuAcademicCalendar.lookup(academicYear: "2025", semesterCode: "12")
    check(calendar?.firstWeekMonday == "2026-03-02", "official NFU spring calendar starts on 2026-03-02")
    check(calendar?.totalWeeks == 20, "official NFU spring calendar has 20 weeks")
    let raw = #"{"ok":true,"term":{"xnm":"2025","xqm":"12"},"sources":{"courses":{"ok":true,"endpoint":"N2151"},"exams":{"ok":true,"endpoint":"N358105"},"sectionTimes":{"ok":true,"count":2}},"diagnostics":[],"sectionTimes":[{"section":1,"start":"08:00","end":"08:40"},{"section":2,"start":"08:50","end":"09:30"}],"schedule":{"kbList":[{"kch_id":"X","kcmc":"课程","xm":"教师","xqj":"2","jc":"1-2节","zcd":"1-18周","cdmc":"A","kcxz":"必修"}],"sjkList":[]},"exams":{"items":[{"kch":"Y","kcmc":"考试课","kssj":"2026-07-06 09:00-11:00","cdmc":"B"}]}}"#
    let bundle = try parseNfu(raw, options: nfuOptions(academicYear: "2026", semesterCode: "3", termName: "", firstWeekMonday: "2026-03-02", totalWeeks: 20))
    check(bundle.term.academicYear == "2025" && bundle.term.semesterCode == "12", "page-selected term overrides stale form values")
    check(bundle.term.firstWeekMonday == "2026-03-02", "user-supplied first Monday is retained")
    check(bundle.courseUnresolvedCount == 0 && bundle.canCommit, "complete course, time-rule, and exam sources can commit")
    check(bundle.events.first { $0.kind == .COURSE }?.courseNature == "必修", "course nature is retained")
    let exam = bundle.events.first { $0.kind == .EXAM }
    check(exam.flatMap { ScheduleEngine.weekFor(term: bundle.term, date: LocalValue.date($0.date!)!) } == 19, "July 6 exam maps to official week 19")

    let incompleteRaw = raw.replacingOccurrences(of: #"{"ok":true,"count":2}"#, with: #"{"ok":false,"count":0}"#)
        .replacingOccurrences(of: #"[{"section":1,"start":"08:00","end":"08:40"},{"section":2,"start":"08:50","end":"09:30"}]"#, with: "[]")
    let incomplete = try parseNfu(incompleteRaw, options: nfuOptions(academicYear: "2025", semesterCode: "12", termName: "", firstWeekMonday: "2026-03-02", totalWeeks: 20))
    check(incomplete.courseUnresolvedCount == 1 && !incomplete.canCommit, "imports with unresolved real course times are blocked")
    let store = try DocumentFileStore(directoryURL: FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true))
    let repository = try ScheduleRepository(store: store)
    do { try repository.applySchoolImport(incomplete); check(false, "repository must reject an incomplete import bundle") } catch { check(true, "repository rejects incomplete imports") }
}

private func validateEventDraft() {
    var draft = EventDraft()
    draft.title = "Manual exam"
    draft.kind = .EXAM
    draft.recurrence = .SINGLE
    draft.date = "2026-07-10"
    draft.startTime = "16:00"
    draft.endTime = "18:00"
    draft.startSection = 1
    draft.endSection = 1
    draft.courseNature = "必修"
    let event = try? draft.makeEvent(existingID: "manual", activeTerm: nil)
    check(event?.startTime == "16:00" && event?.startSection == 1, "real time remains authoritative when section metadata disagrees")
    check(event?.courseNature == "必修", "course nature is stored in the formal cross-platform field")
    check(event.map(EventDraft.init(event:))?.courseNature == "必修", "course nature round-trips through the editor draft")
    let old = makeEvent(id: "old", title: "Old", start: "17:30", end: "19:00")
    let conflicts = event.map { ScheduleEngine.conflicts(candidate: $0, document: ScheduleDocument(events: [old])) } ?? []
    check(conflicts.first.map { ScheduleEngine.overlapMinutes($0.0, $0.1) } == 30, "draft conflict summary uses real minutes")
}

private func validateCatalogAndLifecycle() throws {
    var course = makeEvent(id: "course", title: "Database Systems", start: "08:00", end: "09:30")
    course.kind = .COURSE; course.termId = "term"; course.teacher = "Teacher"; course.location = "Room A"
    var exam = makeEvent(id: "exam", title: "Database Exam", start: "16:00", end: "18:00")
    exam.kind = .EXAM; exam.termId = "term"
    var unresolved = course; unresolved.id = "unresolved"; unresolved.title = "Machine Learning"; unresolved.teacher = ""; unresolved.location = ""; unresolved.startTime = nil; unresolved.endTime = nil; unresolved.resolutionStatus = .UNRESOLVED
    let searched = EventCatalog.filter([course, exam, unresolved], query: "room a", kind: .all, resolution: .all, termID: "term")
    check(searched.map(\.id) == ["course"], "catalog searches location, teacher, notes, and title")
    let unresolvedOnly = EventCatalog.filter([course, exam, unresolved], query: "", kind: .course, resolution: .unresolved, termID: nil)
    check(unresolvedOnly.map(\.id) == ["unresolved"], "catalog combines kind and resolution filters")

    let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
    let repository = try ScheduleRepository(store: DocumentFileStore(directoryURL: root))
    let term = Term(id: "term", name: "Term", firstWeekMonday: "2026-03-02")
    let rule = TimeRule(id: "rule", termId: "term", sections: [SectionTime(section: 1, start: "08:00", end: "08:40")])
    try repository.updateTerm(term, rule: rule)
    try repository.upsertEvent(course)
    let copy = try repository.duplicateEvent(id: course.id)
    check(copy.id != course.id && copy.title.contains("副本") && copy.externalId == nil && copy.timeSource == .USER, "duplicate creates an independent user event")
    try repository.deleteTerm(id: term.id)
    check(repository.document.terms.isEmpty && repository.document.timeRules.isEmpty && repository.document.events.isEmpty, "term deletion removes terms, rules, and linked events")
    check(repository.document.tombstones.contains { $0.id == term.id && $0.entityType == .TERM }, "term deletion writes a term tombstone")
    check(repository.document.tombstones.contains { $0.id == rule.id && $0.entityType == .TIME_RULE }, "term deletion writes rule tombstones")
    check(repository.document.tombstones.contains { $0.entityType == .EVENT }, "term deletion writes event tombstones")
}

private func validateTermIsolationAndBulkDeletion() throws {
    let termA = Term(id: "term-a", name: "A", firstWeekMonday: "2026-03-02", totalWeeks: 20)
    let termB = Term(id: "term-b", name: "B", firstWeekMonday: "2026-03-02", totalWeeks: 20)
    var courseA = makeEvent(id: "course-a", title: "A Course", start: "08:00", end: "09:00")
    courseA.kind = .COURSE; courseA.recurrence = .WEEKLY; courseA.termId = termA.id; courseA.date = nil; courseA.weekday = 1; courseA.weeks = [1]
    var courseB = courseA
    courseB.id = "course-b"; courseB.title = "B Course"; courseB.termId = termB.id
    var examA = makeEvent(id: "exam-a", title: "A Exam", date: "2026-03-02", start: "10:00", end: "11:00")
    examA.kind = .EXAM; examA.termId = termA.id
    var examB = examA
    examB.id = "exam-b"; examB.title = "B Exam"; examB.termId = termB.id
    let document = ScheduleDocument(terms: [termA, termB], events: [courseA, courseB, examA, examB])
    let date = LocalValue.date("2026-03-02")!
    let onlyA = ScheduleEngine.occurrences(on: date, document: document, termID: termA.id)
    check(Set(onlyA.map(\.event.id)) == Set(["course-a", "exam-a"]), "term-scoped week query excludes courses and exams from other terms")

    let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
    let repository = try ScheduleRepository(store: DocumentFileStore(directoryURL: root))
    try repository.updateTerm(termA, rule: nil)
    try repository.updateTerm(termB, rule: nil)
    try repository.upsertEvent(courseA)
    try repository.upsertEvent(courseB)
    try repository.upsertEvent(examA)
    try repository.deleteEvents(ids: Set([courseA.id, examA.id]))
    check(Set(repository.document.events.map(\.id)) == Set([courseB.id]), "bulk deletion removes only selected events")
    check(Set(repository.document.tombstones.filter { $0.entityType == .EVENT }.map(\.id)) == Set([courseA.id, examA.id]), "bulk deletion creates one tombstone for every selected event")
}

private func validateSafeSchoolReimport() throws {
    let term = Term(id: "reimport-term", name: "Reimport", school: "NFU", academicYear: "2026", semesterCode: "3", firstWeekMonday: "2026-09-07", totalWeeks: 20)
    var course = makeEvent(id: "school-course", title: "Distributed Systems", start: "08:00", end: "09:30", source: .SECTION_RULE, updatedAt: "2026-09-01T00:00:00Z")
    course.kind = .COURSE; course.recurrence = .WEEKLY; course.termId = term.id; course.date = nil; course.weekday = 1; course.weeks = Set(1...18); course.startSection = 1; course.endSection = 2
    var exam = makeEvent(id: "school-exam", title: "Distributed Systems · Exam", date: "2027-01-04", start: "09:00", end: "11:00", source: .EXPLICIT, updatedAt: "2026-12-20T00:00:00Z")
    exam.kind = .EXAM; exam.termId = term.id

    let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
    let repository = try ScheduleRepository(store: DocumentFileStore(directoryURL: root))
    let early = AcademicImportBundle(term: term, timeRule: nil, events: [course], courseCount: 1, examCount: 0, unresolvedCount: 0, diagnostics: [], examSource: AcademicSourceStatus(ok: true, count: 0))
    try repository.applySchoolImport(early)
    let storedCourseTimestamp = repository.document.events.first { $0.id == course.id }!.updatedAt
    var userCorrected = course
    userCorrected.id = "user-corrected-school-course"; userCorrected.title = "User Corrected Course"; userCorrected.timeSource = .USER
    try repository.upsertEvent(userCorrected)

    var sameCourse = course
    sameCourse.updatedAt = "2026-12-20T00:00:00Z"
    let finalBundle = AcademicImportBundle(term: term, timeRule: nil, events: [sameCourse, exam], courseCount: 1, examCount: 1, unresolvedCount: 0, diagnostics: [], examSource: AcademicSourceStatus(ok: true, count: 1))
    let plan = SchoolImportPlan.make(document: repository.document, bundle: finalBundle)
    check(plan.inserted.count == 1 && plan.unchanged.count == 1 && plan.updated.isEmpty, "late exam import plans one exam insertion without duplicating the unchanged course")
    check(plan.protected.map(\.id) == [userCorrected.id], "reimport preview counts user-protected school events even when absent from incoming data")
    try repository.applySchoolImport(finalBundle)
    check(repository.document.events.filter { $0.id == course.id }.count == 1, "late exam import keeps exactly one copy of the course")
    check(repository.document.events.filter { $0.id == exam.id }.count == 1, "late exam import adds the published exam")
    check(repository.document.events.contains { $0.id == userCorrected.id }, "reimport preserves a user-corrected school event absent from incoming data")
    check(repository.document.events.first { $0.id == course.id }?.updatedAt == storedCourseTimestamp, "identical reimport preserves event updatedAt and avoids sync noise")

    let emptyExamBundle = AcademicImportBundle(term: term, timeRule: nil, events: [sameCourse], courseCount: 1, examCount: 0, unresolvedCount: 0, diagnostics: [], examSource: AcademicSourceStatus(ok: true, count: 0))
    let emptyPlan = SchoolImportPlan.make(document: repository.document, bundle: emptyExamBundle)
    check(emptyPlan.preservedExams.map(\.id) == [exam.id], "empty exam result protects existing published exams")
    let documentTimestampBeforeNoOpImport = repository.document.updatedAt
    Thread.sleep(forTimeInterval: 1.1)
    try repository.applySchoolImport(emptyExamBundle)
    check(repository.document.events.contains { $0.id == exam.id }, "empty exam result cannot erase an existing exam set")
    check(repository.document.updatedAt == documentTimestampBeforeNoOpImport, "a semantically unchanged protected reimport does not rewrite the document")
}

private func validateVisualMotionBudget() {
    check(VisualMotionPolicy.scale(isHovering: true, reduceMotion: false) == 1.018, "interactive cards use a subtle bounded hover scale")
    check(VisualMotionPolicy.scale(isHovering: true, reduceMotion: true) == 1.0, "reduce-motion disables hover transforms")
    check(VisualMotionPolicy.duration(reduceMotion: false) <= 0.25, "visual motion stays within the low-power duration budget")
    check(VisualMotionPolicy.duration(reduceMotion: true) == 0, "reduce-motion removes animation duration")
}

private func validateApplicationPresencePolicy() {
    check(ApplicationPresencePolicy.desiredPresence(visibleUserWindowCount: 0) == .silentBackground, "zero visible windows enters silent background")
    check(ApplicationPresencePolicy.desiredPresence(visibleUserWindowCount: 1) == .foreground, "one visible window keeps the app in the foreground")
    check(ApplicationPresencePolicy.desiredPresence(visibleUserWindowCount: 3) == .foreground, "multiple visible windows keep the app in the foreground")
}

private func runValidation(_ name: String, _ body: () throws -> Void) {
    do { try body() }
    catch {
        failures += 1
        fputs("FAIL: \(name): \(error)\n", stderr)
    }
}

runValidation("persistence and repository", validatePersistenceAndRepository)
runValidation("App Group migration", validateAppGroupMigration)
runValidation("widget model", validateWidgetModel)
runValidation("policies", validatePolicies)
runValidation("core and importer", validateExistingCoreAndImporter)
runValidation("import completeness", validateNfuImportCompleteness)
runValidation("event draft", validateEventDraft)
runValidation("catalog and lifecycle", validateCatalogAndLifecycle)
runValidation("term isolation and bulk deletion", validateTermIsolationAndBulkDeletion)
runValidation("safe school reimport", validateSafeSchoolReimport)
runValidation("visual motion budget", validateVisualMotionBudget)
runValidation("application presence", validateApplicationPresencePolicy)

if failures > 0 {
    fputs("CourseAxis core validation failed: \(failures) failure(s)\n", stderr)
    exit(1)
}
print("CourseAxis core validation passed")
