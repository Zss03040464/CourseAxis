package dev.shusenzheng64.courseaxis

import dev.shusenzheng64.courseaxis.core.*
import dev.shusenzheng64.courseaxis.data.ScheduleJsonCodec
import dev.shusenzheng64.courseaxis.importer.AcademicProvider
import dev.shusenzheng64.courseaxis.importer.AcademicProviderPolicy
import dev.shusenzheng64.courseaxis.importer.AcademicImportAdapterRegistry
import dev.shusenzheng64.courseaxis.importer.AcademicImportOptions
import dev.shusenzheng64.courseaxis.importer.AcademicWebViewPolicy
import dev.shusenzheng64.courseaxis.model.*
import dev.shusenzheng64.courseaxis.widget.WidgetAgendaPolicy
import dev.shusenzheng64.courseaxis.widget.WidgetGlassVisualPolicy
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class CoreEngineTest {
    @Test
    fun academicProviderAllowsOnlyItsExactHttpsOriginAndPath() {
        val provider = AcademicProvider(
            id = "nfu-zhengfang",
            schoolName = "广州南方学院",
            vendor = "ZHENGFANG",
            allowedOrigin = "https://jwxt.nfu.edu.cn",
            pathPrefix = "/jwglxt/",
            loginPath = "/jwglxt/xtgl/login_slogin.html",
            homePath = "/jwglxt/xtgl/index_initMenu.html",
            requiresThirdPartyCookies = false,
        )

        assertTrue(AcademicProviderPolicy.isAllowed("https://jwxt.nfu.edu.cn/jwglxt/", provider))
        assertTrue(AcademicProviderPolicy.isAllowed("https://jwxt.nfu.edu.cn:443/jwglxt/", provider))
        assertTrue(AcademicProviderPolicy.isAllowed("https://jwxt.nfu.edu.cn/jwglxt/xtgl/index_initMenu.html?gnmkdm=index", provider))
        listOf(
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
        ).forEach { url ->
            assertFalse(url, AcademicProviderPolicy.isAllowed(url, provider))
        }
    }

    @Test fun weeksAndSections(){assertEquals(setOf(1,3,5,7,9,11,13,15,17),WeekParser.parse("1-17周(单)"));assertEquals(listOf(1,2,3),SectionParser.parse("(1-3节)"))}
    @Test fun dynamicRealTimes(){val rule=TimeRule("r","t",sections=listOf(SectionTime(1,"08:00","08:40"),SectionTime(2,"08:45","09:25"),SectionTime(9,"16:20","17:00"),SectionTime(10,"17:05","17:45")));assertEquals(LocalTime.of(8,0) to LocalTime.of(9,25),TimeResolver.resolve(listOf(1,2),rule));assertFalse(TimeResolver.inferSections(LocalTime.of(16,0),LocalTime.of(18,0),rule)!!.exact)}
    @Test fun manualTimeConflict(){val old=ScheduleEvent("a",kind=EventKind.COURSE,recurrence=Recurrence.SINGLE,title="A",date="2026-07-10",startTime="17:30",endTime="19:00",timeSource=TimeSource.USER,resolutionStatus=ResolutionStatus.RESOLVED);val fresh=ScheduleEvent("b",kind=EventKind.EXAM,recurrence=Recurrence.SINGLE,title="B",date="2026-07-10",startTime="16:00",endTime="18:00",timeSource=TimeSource.USER,resolutionStatus=ResolutionStatus.RESOLVED);val pair=ScheduleEngine.conflicts(fresh,ScheduleDocument(events=listOf(old))).single();assertEquals(30,ScheduleEngine.overlapMinutes(pair.first,pair.second))}
    @Test fun examAndStableId(){val exam=ExamTimeParser.parse("2026年7月10日 16:00—18:00")!!;assertEquals(LocalDate.of(2026,7,10),exam.date);assertEquals("term-d248459c24acef18",StableId.of("term","广州南方学院","2025","12","2026-02-23"))}
    @Test fun widgetUpcomingCountDefaultsAndRoundTrips() {
        val legacy = """{"schemaVersion":1,"settings":{"activeTermId":null,"dayStart":"07:30","dayEnd":"22:30","showWeekends":true}}"""
        assertEquals(5, ScheduleJsonCodec.decode(legacy).settings.widgetUpcomingCount)
        val configured = ScheduleDocument(settings = AppSettings(widgetUpcomingCount = 5))
        assertEquals(5, ScheduleJsonCodec.decode(ScheduleJsonCodec.encode(configured)).settings.widgetUpcomingCount)
    }

    @Test
    fun courseMetadataDefaultsAndRoundTrips() {
        val legacy = """{
            "schemaVersion":1,
            "events":[{
                "id":"legacy-course","externalId":null,"kind":"COURSE","recurrence":"WEEKLY",
                "title":"人工智能原理","teacher":"魏俊华","location":"2实407","campus":"温泉校区",
                "notes":"","termId":"term","date":null,"weekday":4,"weeks":[1,2,3],
                "startTime":"10:35","endTime":"12:00","startSection":4,"endSection":5,
                "timeRuleId":null,"timeSource":"EXPLICIT","resolutionStatus":"RESOLVED",
                "colorKey":1,"updatedAt":"2026-07-15T00:00:00Z"
            }]
        }""".trimIndent()
        val decodedLegacy = ScheduleJsonCodec.decode(legacy).events.single()
        assertEquals("", decodedLegacy.courseNature)
        assertEquals("", decodedLegacy.totalHours)
        assertEquals("", decodedLegacy.credits)

        val enriched = decodedLegacy.copy(
            courseNature = "专业必修课",
            totalHours = "34",
            credits = "2.0",
        )
        val roundTrip = ScheduleJsonCodec.decode(
            ScheduleJsonCodec.encode(ScheduleDocument(events = listOf(enriched))),
        ).events.single()

        assertEquals("专业必修课", roundTrip.courseNature)
        assertEquals("34", roundTrip.totalHours)
        assertEquals("2.0", roundTrip.credits)
    }

    @Test
    fun importerReadsCourseNatureHoursAndCredits() {
        fun raw(nature: String, hours: String, credits: String) = """{
            "ok":true,"diagnostics":[],
            "sectionTimes":[{"section":4,"start":"10:35","end":"11:15"},{"section":5,"start":"11:20","end":"12:00"}],
            "schedule":{"kbList":[{"kch_id":"AI001","kcmc":"人工智能原理","xm":"魏俊华","xqj":"4","jc":"4-5节","zcd":"1-17周","cdmc":"温泉校区 2实407","kcxz":"$nature","zxs":"$hours","xf":"$credits"}],"sjkList":[]},
            "exams":{"items":[]}
        }""".trimIndent()
        val options = AcademicImportOptions(AcademicWebViewPolicy.nfuProvider, "2026", "3", "2026-2027 第一学期", LocalDate.of(2026, 9, 7), 20)
        val adapter = AcademicImportAdapterRegistry.adapter(options.provider)
        val first = adapter.parse(raw("专业必修课", "34", "2.0"), options).events.single()
        val refreshed = adapter.parse(raw("专业选修课", "36", "2.5"), options).events.single()

        assertEquals("专业必修课", first.courseNature)
        assertEquals("34", first.totalHours)
        assertEquals("2.0", first.credits)
        assertEquals(first.id, refreshed.id)
    }

    @Test
    fun schoolReimportKeepsUserTimeAndRefreshesMetadata() {
        val existing = ScheduleEvent(
            id = "course",
            kind = EventKind.COURSE,
            recurrence = Recurrence.WEEKLY,
            title = "人工智能原理",
            termId = "term",
            weekday = 4,
            weeks = setOf(1, 2, 3),
            startTime = "16:00",
            endTime = "18:00",
            timeSource = TimeSource.USER,
            resolutionStatus = ResolutionStatus.RESOLVED,
            courseNature = "专业必修课",
            totalHours = "32",
            credits = "2.0",
        )
        val imported = existing.copy(
            startTime = "10:35",
            endTime = "12:00",
            timeSource = TimeSource.SECTION_RULE,
            teacher = "魏俊华",
            totalHours = "34",
        )

        val merged = SchoolImportMerge.mergeEvent(existing, imported)

        assertEquals("16:00", merged.startTime)
        assertEquals("18:00", merged.endTime)
        assertEquals(TimeSource.USER, merged.timeSource)
        assertEquals("魏俊华", merged.teacher)
        assertEquals("34", merged.totalHours)
    }

    @Test
    fun unavailableExamSourcePreservesExistingExamsEvenWhenSnapshotIsPartial() {
        val oldExam = ScheduleEvent(
            id = "old-exam",
            kind = EventKind.EXAM,
            recurrence = Recurrence.SINGLE,
            title = "旧考试",
            termId = "term",
            date = "2027-01-01",
            startTime = "09:00",
            endTime = "11:00",
            timeSource = TimeSource.EXPLICIT,
            resolutionStatus = ResolutionStatus.RESOLVED,
        )
        val partialExam = oldExam.copy(id = "partial-exam", title = "部分结果")

        val merged = SchoolImportMerge.mergeTermEvents(
            existing = listOf(oldExam),
            imported = listOf(partialExam),
            termId = "term",
            preserveExistingExams = true,
        )

        assertEquals(setOf("old-exam", "partial-exam"), merged.mapTo(mutableSetOf()) { it.id })
    }

    @Test
    fun androidWeekProjectionStopsAfterLastWeekAndKeepsEarlierGap() {
        val term = Term("term", "学期", firstWeekMonday = "2026-09-07", totalWeeks = 20)
        val course = ScheduleEvent(
            id = "gapped",
            kind = EventKind.COURSE,
            recurrence = Recurrence.WEEKLY,
            title = "人工智能原理",
            termId = term.id,
            weekday = 4,
            weeks = setOf(8, 10),
            startTime = "10:35",
            endTime = "12:00",
            timeSource = TimeSource.SECTION_RULE,
            resolutionStatus = ResolutionStatus.RESOLVED,
        )
        val document = ScheduleDocument(terms = listOf(term), events = listOf(course))

        val gap = WeekProjectionEngine.itemsOn(
            date = requireNotNull(ScheduleEngine.dateFor(term, 9, 4)),
            week = 9,
            term = term,
            document = document,
        )
        val ended = WeekProjectionEngine.itemsOn(
            date = requireNotNull(ScheduleEngine.dateFor(term, 11, 4)),
            week = 11,
            term = term,
            document = document,
        )

        assertEquals(listOf("gapped"), gap.map { it.event.id })
        assertFalse(gap.single().occursThisWeek)
        assertTrue(ended.isEmpty())
    }

    @Test
    fun androidWeekProjectionShowsSingleOnlyOnRealDate() {
        val term = Term("term", "学期", firstWeekMonday = "2026-09-07", totalWeeks = 20)
        val exam = ScheduleEvent(
            id = "exam",
            kind = EventKind.EXAM,
            recurrence = Recurrence.SINGLE,
            title = "考试",
            termId = term.id,
            date = "2026-09-10",
            startTime = "16:00",
            endTime = "18:00",
            timeSource = TimeSource.EXPLICIT,
            resolutionStatus = ResolutionStatus.RESOLVED,
        )
        val document = ScheduleDocument(terms = listOf(term), events = listOf(exam))

        assertEquals(1, WeekProjectionEngine.itemsOn(LocalDate.of(2026, 9, 10), 1, term, document).size)
        assertTrue(WeekProjectionEngine.itemsOn(LocalDate.of(2026, 9, 17), 2, term, document).isEmpty())
    }

    @Test
    fun weekEventFilterKeepsAllAndSeparatesCoursesFromExams() {
        val term = Term("term", "学期", firstWeekMonday = "2026-09-07", totalWeeks = 20)
        val course = ScheduleEvent(
            id = "course",
            kind = EventKind.COURSE,
            recurrence = Recurrence.WEEKLY,
            title = "课程",
            termId = term.id,
            weekday = 1,
            weeks = setOf(1),
            startTime = "08:00",
            endTime = "09:00",
            timeSource = TimeSource.EXPLICIT,
            resolutionStatus = ResolutionStatus.RESOLVED,
        )
        val exam = ScheduleEvent(
            id = "exam",
            kind = EventKind.EXAM,
            recurrence = Recurrence.SINGLE,
            title = "考试",
            termId = term.id,
            date = "2026-09-08",
            startTime = "10:00",
            endTime = "11:00",
            timeSource = TimeSource.EXPLICIT,
            resolutionStatus = ResolutionStatus.RESOLVED,
        )
        val custom = ScheduleEvent(
            id = "custom",
            kind = EventKind.CUSTOM,
            recurrence = Recurrence.SINGLE,
            title = "自定义",
            termId = term.id,
            date = "2026-09-09",
            startTime = "12:00",
            endTime = "13:00",
            timeSource = TimeSource.USER,
            resolutionStatus = ResolutionStatus.RESOLVED,
        )
        val items = WeekProjectionEngine.itemsInWeek(
            week = 1,
            term = term,
            document = ScheduleDocument(terms = listOf(term), events = listOf(course, exam, custom)),
        )

        assertEquals(
            listOf("course", "exam", "custom"),
            WeekEventFilter.apply(items, WeekEventMode.ALL).map { it.event.id },
        )
        assertEquals(
            listOf("course"),
            WeekEventFilter.apply(items, WeekEventMode.COURSE).map { it.event.id },
        )
        assertEquals(
            listOf("exam"),
            WeekEventFilter.apply(items, WeekEventMode.EXAM).map { it.event.id },
        )
    }

    @Test
    fun coursePresentationIncludesAppMetadataAndCompactWidgetWeeks() {
        val event = ScheduleEvent(
            id = "course",
            kind = EventKind.COURSE,
            recurrence = Recurrence.WEEKLY,
            title = "人工智能原理",
            teacher = "魏俊华",
            location = "温泉校区 2实407",
            weeks = (1..18).toSet(),
            startTime = "10:35",
            endTime = "12:00",
            courseNature = "专业必修课",
            totalHours = "34",
            credits = "2.0",
        )

        val details = CoursePresentation.fullDetails(event)

        assertTrue(details.contains("教师：魏俊华"))
        assertTrue(details.contains("地点：温泉校区 2实407"))
        assertTrue(details.contains("时间：10:35–12:00"))
        assertTrue(details.contains("周次：1-18周"))
        assertTrue(details.contains("性质：专业必修课"))
        assertTrue(details.contains("总学时：34"))
        assertTrue(details.contains("学分：2.0"))
        assertEquals("1-18", CoursePresentation.compactWeekRange(event))
        assertEquals(
            listOf("魏俊华 · 温泉校区 2实407", "1-18周"),
            CoursePresentation.timelineDetails(event),
        )
    }

    @Test
    fun weekLayoutPolicyFitsSevenDaysOnPhonesWithoutHorizontalScroll() {
        val phone = WeekLayoutPolicy.timelineMetrics(360)
        val wide = WeekLayoutPolicy.timelineMetrics(1200)

        assertEquals(WeekLayoutMode.TIMELINE, WeekLayoutPolicy.mode(360))
        assertEquals(WeekLayoutMode.TIMELINE, WeekLayoutPolicy.mode(699))
        assertEquals((1..7).toList(), WeekLayoutPolicy.visibleWeekdays())
        assertEquals(48, phone.gutterWidthDp)
        assertEquals(44, phone.dayWidthDp)
        assertFalse(phone.horizontallyScrollable)
        assertEquals(7, phone.dayCount)
        assertTrue(phone.contentWidthDp <= 360)
        assertFalse(wide.horizontallyScrollable)
        assertTrue(wide.dayWidthDp > phone.dayWidthDp)
        assertTrue(wide.contentWidthDp <= 1200)
    }

    @Test
    fun weekAxisUsesRealSectionTimesAndFallsBackHonestly() {
        val rule = TimeRule(
            id = "rule",
            termId = "term",
            sections = listOf(
                SectionTime(1, "08:00", "08:40"),
                SectionTime(2, "08:50", "09:30"),
                SectionTime(4, "10:35", "11:15"),
            ),
        )

        val sections = WeekAxisPolicy.markers(rule, startMinute = 450, endMinute = 750)

        assertEquals(listOf(1, 2, 4), sections.map { it.section })
        assertEquals(listOf("08:00", "08:50", "10:35"), sections.map { it.startText })
        assertEquals(listOf("08:40", "09:30", "11:15"), sections.map { it.endText })
        assertEquals(listOf(480, 530, 635), sections.map { it.anchorMinute })

        val fallback = WeekAxisPolicy.markers(null, startMinute = 450, endMinute = 630)
        assertTrue(fallback.all { it.section == null })
        assertEquals(listOf("08:00", "09:00", "10:00"), fallback.map { it.startText })
    }

    @Test
    fun weekAxisCompressesBreaksButKeepsExactTimeWithinSections() {
        val rawSections = listOf(
            Triple("08:00", "08:40", 1), Triple("08:50", "09:30", 2),
            Triple("09:45", "10:25", 3), Triple("10:35", "11:15", 4),
            Triple("11:20", "12:00", 5), Triple("12:50", "13:30", 6),
            Triple("13:40", "14:20", 7), Triple("14:30", "15:10", 8),
            Triple("15:15", "15:55", 9), Triple("16:10", "16:50", 10),
            Triple("16:55", "17:35", 11), Triple("18:45", "19:25", 12),
            Triple("19:30", "20:10", 13), Triple("20:15", "20:55", 14),
            Triple("21:05", "21:45", 15),
        )
        val rule = TimeRule(
            id = "rule",
            termId = "term",
            sections = rawSections.map { (start, end, section) -> SectionTime(section, start, end) },
        )
        val scale = WeekAxisPolicy.scale(rule, startMinute = 480, endMinute = 1305)

        assertEquals(0f, scale.positionSlots(480), 0.001f)
        assertEquals(0.5f, scale.positionSlots(500), 0.001f)
        assertEquals(5f, scale.positionSlots(720), 0.001f)
        assertEquals(5f, scale.positionSlots(770), 0.001f)
        assertEquals(15f, scale.positionSlots(1305), 0.001f)
        assertEquals(15f, scale.totalSlots, 0.001f)

        val slotHeight = WeekLayoutPolicy.sectionSlotHeightDp(
            availableHeightDp = 566,
            slotCount = scale.totalSlots,
        )
        assertEquals(36f, slotHeight, 0.001f)
        assertTrue(slotHeight * scale.totalSlots <= 566f)
    }

    @Test
    fun weekAxisMarkerLabelNeverEscapesTheTopBoundary() {
        assertEquals(0f, WeekAxisPolicy.markerTopDp(positionSlots = 0f, slotHeightDp = 36f), 0.001f)
        assertEquals(33f, WeekAxisPolicy.markerTopDp(positionSlots = 1f, slotHeightDp = 36f), 0.001f)
    }

    @Test
    fun weekTimelineDensityAvoidsOverlappingLabelsAndFitsCardContent() {
        assertFalse(WeekTimelineDensityPolicy.showEndTime(slotHeightDp = 24f))
        assertFalse(WeekTimelineDensityPolicy.showEndTime(slotHeightDp = 33f))
        assertTrue(WeekTimelineDensityPolicy.showEndTime(slotHeightDp = 34f))

        val compact = WeekTimelineDensityPolicy.courseCard(heightDp = 28f, isExam = false)
        assertEquals(1, compact.titleMaxLines)
        assertTrue(compact.showTime)
        assertFalse(compact.showLocation)
        assertFalse(compact.showTeacher)
        assertFalse(compact.showWeeks)

        val twoSections = WeekTimelineDensityPolicy.courseCard(heightDp = 48f, isExam = false)
        assertEquals(2, twoSections.titleMaxLines)
        assertTrue(twoSections.showTime)
        assertTrue(twoSections.showLocation)
        assertTrue(twoSections.showTeacher)
        assertFalse(twoSections.showWeeks)

        val tall = WeekTimelineDensityPolicy.courseCard(heightDp = 60f, isExam = false)
        assertEquals(2, tall.titleMaxLines)
        assertTrue(tall.showWeeks)

        val extraTall = WeekTimelineDensityPolicy.courseCard(heightDp = 72f, isExam = false)
        assertEquals(3, extraTall.titleMaxLines)
        assertTrue(extraTall.showWeeks)

        val exam = WeekTimelineDensityPolicy.courseCard(heightDp = 60f, isExam = true)
        assertTrue(exam.showTeacher)
        assertFalse(exam.showWeeks)
    }

    @Test
    fun weekGlassVisualPolicyIsTranslucentAndStatic() {
        assertTrue(WeekGlassVisualPolicy.alpha(WeekGlassVisualPolicy.backdropTopArgb) in 128..232)
        assertTrue(WeekGlassVisualPolicy.alpha(WeekGlassVisualPolicy.backdropBottomArgb) in 128..232)
        assertTrue(WeekGlassVisualPolicy.alpha(WeekGlassVisualPolicy.headerSurfaceArgb) in 32..224)
        assertTrue(WeekGlassVisualPolicy.alpha(WeekGlassVisualPolicy.courseSurfaceArgb) in 96..232)
        assertTrue(WeekGlassVisualPolicy.alpha(WeekGlassVisualPolicy.examSurfaceArgb) in 96..232)
        assertTrue(WeekGlassVisualPolicy.filterMinimumTouchDp >= 44f)
        assertEquals(255, WeekGlassVisualPolicy.alpha(WeekGlassVisualPolicy.primaryTextArgb))
        assertTrue(WeekGlassVisualPolicy.usesSystemBlur)
        assertTrue(WeekGlassVisualPolicy.systemBlurRadiusPx in 24..160)
        assertFalse(WeekGlassVisualPolicy.usesLiveBlur)
        assertFalse(WeekGlassVisualPolicy.usesContinuousAnimation)
    }

    @Test
    fun settingsJellyVisualPolicyIsTranslucentStaticAndAccessible() {
        assertTrue(SettingsJellyVisualPolicy.alpha(SettingsJellyVisualPolicy.backdropTopArgb) in 128..232)
        assertTrue(SettingsJellyVisualPolicy.alpha(SettingsJellyVisualPolicy.cardSurfaceArgb) in 48..190)
        assertEquals(255, SettingsJellyVisualPolicy.alpha(SettingsJellyVisualPolicy.primaryTextArgb))
        assertEquals(22f, SettingsJellyVisualPolicy.cardCornerDp, 0.001f)
        assertEquals(16f, SettingsJellyVisualPolicy.fieldCornerDp, 0.001f)
        assertTrue(SettingsJellyVisualPolicy.minimumTouchDp >= 48f)
        assertTrue(SettingsJellyVisualPolicy.usesSystemBlur)
        assertFalse(SettingsJellyVisualPolicy.usesLiveBlur)
        assertFalse(SettingsJellyVisualPolicy.usesContinuousAnimation)
    }

    @Test
    fun weekPagerPolicyClampsWeeksAndPagesToTheActiveTerm() {
        assertEquals(0, WeekPagerPolicy.pageForWeek(1, 20))
        assertEquals(19, WeekPagerPolicy.pageForWeek(20, 20))
        assertEquals(0, WeekPagerPolicy.pageForWeek(-2, 20))
        assertEquals(20, WeekPagerPolicy.weekForPage(19, 20))
        assertEquals(1, WeekPagerPolicy.weekForPage(-1, 20))
    }

    @Test
    fun weekTermSwitchUsesCurrentWeekInsideTermAndFirstWeekOutside() {
        val term = Term(
            id = "term",
            name = "2025-2026 第二学期",
            firstWeekMonday = "2026-03-02",
            totalWeeks = 20,
        )

        assertEquals(20, WeekTermSwitchPolicy.initialWeek(term, LocalDate.of(2026, 7, 15)))
        assertEquals(1, WeekTermSwitchPolicy.initialWeek(term, LocalDate.of(2026, 2, 23)))
        assertEquals(1, WeekTermSwitchPolicy.initialWeek(term, LocalDate.of(2026, 7, 20)))
    }

    @Test
    fun widgetGlassVisualPolicyIsTranslucentAndStatic() {
        assertTrue(WidgetGlassVisualPolicy.alpha(WidgetGlassVisualPolicy.backdropLightArgb) in 160..240)
        assertTrue(WidgetGlassVisualPolicy.alpha(WidgetGlassVisualPolicy.backdropDarkArgb) in 160..240)
        assertEquals(255, WidgetGlassVisualPolicy.alpha(WidgetGlassVisualPolicy.primaryTextArgb))
        assertFalse(WidgetGlassVisualPolicy.usesLiveBlur)
        assertFalse(WidgetGlassVisualPolicy.usesContinuousAnimation)
    }

    @Test
    fun nextOccurrencesFindsCoursesBeyondThirtyFiveDays() {
        val distantDate = LocalDate.of(2026, 9, 1)
        val distant = ScheduleEvent(
            id = "distant",
            kind = EventKind.COURSE,
            recurrence = Recurrence.SINGLE,
            title = "远期课程",
            date = distantDate.toString(),
            startTime = "09:00",
            endTime = "10:30",
            timeSource = TimeSource.EXPLICIT,
            resolutionStatus = ResolutionStatus.RESOLVED,
        )
        val document = ScheduleDocument(events = listOf(distant))

        val result = ScheduleEngine.nextOccurrences(
            document = document,
            nowDate = LocalDate.of(2026, 7, 15),
            nowTime = LocalTime.NOON,
            limit = 3,
        )

        assertEquals(listOf("distant"), result.map { it.event.id })
        assertEquals(
            distantDate to LocalTime.of(9, 0),
            ScheduleEngine.nextBoundary(
                document,
                LocalDate.of(2026, 7, 15),
                LocalTime.NOON,
            ),
        )
    }

    @Test
    fun nextOccurrencesAreOrderedAndLimited() {
        fun event(id: String, date: String, start: String) = ScheduleEvent(
            id = id,
            kind = EventKind.COURSE,
            recurrence = Recurrence.SINGLE,
            title = id,
            date = date,
            startTime = start,
            endTime = LocalTime.parse(start).plusHours(1).toString(),
            timeSource = TimeSource.EXPLICIT,
            resolutionStatus = ResolutionStatus.RESOLVED,
        )
        val document = ScheduleDocument(
            events = listOf(
                event("later", "2026-07-16", "14:00"),
                event("first", "2026-07-15", "13:00"),
                event("second", "2026-07-16", "09:00"),
            ),
        )

        val result = ScheduleEngine.nextOccurrences(
            document = document,
            nowDate = LocalDate.of(2026, 7, 15),
            nowTime = LocalTime.NOON,
            limit = 2,
        )

        assertEquals(listOf("first", "second"), result.map { it.event.id })
    }

    @Test
    fun widgetCapacityAdaptsToSizeAndUserLimit() {
        assertEquals(1, WidgetAgendaPolicy.capacity(widthDp = 180, heightDp = 90, requested = 5))
        assertEquals(2, WidgetAgendaPolicy.capacity(widthDp = 260, heightDp = 121, requested = 5))
        assertEquals(3, WidgetAgendaPolicy.capacity(widthDp = 260, heightDp = 159, requested = 5))
        assertEquals(4, WidgetAgendaPolicy.capacity(widthDp = 360, heightDp = 197, requested = 5))
        assertEquals(5, WidgetAgendaPolicy.capacity(widthDp = 360, heightDp = 235, requested = 5))
        assertEquals(2, WidgetAgendaPolicy.capacity(widthDp = 360, heightDp = 240, requested = 2))
    }

    @Test
    fun androidScheduleRoutesExcludeTodayAndSupportSettings() {
        assertEquals(AppDestination.WEEK, AppRoute.parse("week"))
        assertEquals(AppDestination.WEEK, AppRoute.parse(null))
        assertEquals(AppDestination.WEEK, AppRoute.parse("unknown"))
        assertEquals(AppDestination.SETTINGS, AppRoute.parse("settings"))
        assertFalse(AppDestination.entries.map { it.name }.contains("TODAY"))
    }

    @Test
    fun examFocusChoosesTheWeekWithFourFinalExams() {
        val earlier = Term("earlier", "较早考试", firstWeekMonday = "2026-04-20", totalWeeks = 20)
        val finals = Term("finals", "2025-2026 第二学期", firstWeekMonday = "2026-03-02", totalWeeks = 20)
        fun exam(id: String, term: Term, date: String) = ScheduleEvent(
            id = id,
            kind = EventKind.EXAM,
            recurrence = Recurrence.SINGLE,
            title = id,
            termId = term.id,
            date = date,
            startTime = "09:00",
            endTime = "11:00",
            timeSource = TimeSource.EXPLICIT,
            resolutionStatus = ResolutionStatus.RESOLVED,
        )
        val document = ScheduleDocument(
            terms = listOf(earlier, finals),
            events = listOf(
                exam("early-1", earlier, "2026-04-25"),
                exam("early-2", earlier, "2026-04-26"),
                exam("final-1", finals, "2026-07-06"),
                exam("final-2", finals, "2026-07-07"),
                exam("final-3", finals, "2026-07-07"),
                exam("final-4", finals, "2026-07-08"),
            ),
            settings = AppSettings(activeTermId = earlier.id),
        )

        assertEquals(ExamFocus(finals.id, 19, 4), ExamFocusPolicy.resolve(document, finals.id))
    }

    @Test
    fun examFocusStaysWithinTheRequestedTermAndIgnoresInvalidEvents() {
        val first = Term("first", "第一学期", firstWeekMonday = "2026-09-07", totalWeeks = 20)
        val second = Term("second", "第二学期", firstWeekMonday = "2026-09-07", totalWeeks = 20)
        fun exam(id: String, term: Term) = ScheduleEvent(
            id = id,
            kind = EventKind.EXAM,
            recurrence = Recurrence.SINGLE,
            title = id,
            termId = term.id,
            date = "2026-09-08",
            startTime = "09:00",
            endTime = "11:00",
            timeSource = TimeSource.EXPLICIT,
            resolutionStatus = ResolutionStatus.RESOLVED,
        )
        val invalid = exam("weekly", first).copy(recurrence = Recurrence.WEEKLY)
        val document = ScheduleDocument(
            terms = listOf(first, second),
            events = listOf(exam("first-exam", first), exam("second-exam", second), invalid),
            settings = AppSettings(activeTermId = second.id),
        )

        assertEquals(ExamFocus(second.id, 1, 1), ExamFocusPolicy.resolve(document, second.id))
        assertNull(ExamFocusPolicy.resolve(ScheduleDocument(terms = listOf(first)), first.id))
    }

    @Test
    fun examFocusNeverFallsBackToHistoricalTerms() {
        val historical = Term("historical", "2025-2026 第二学期", firstWeekMonday = "2026-03-02", totalWeeks = 20)
        val current = Term("current", "2026-2027 第一学期", firstWeekMonday = "2026-09-07", totalWeeks = 20)
        fun historicalExam(id: String, date: String) = ScheduleEvent(
            id = id,
            kind = EventKind.EXAM,
            recurrence = Recurrence.SINGLE,
            title = id,
            termId = historical.id,
            date = date,
            startTime = "09:00",
            endTime = "11:00",
            timeSource = TimeSource.EXPLICIT,
            resolutionStatus = ResolutionStatus.RESOLVED,
        )
        val document = ScheduleDocument(
            terms = listOf(historical, current),
            events = listOf(
                historicalExam("old-1", "2026-07-06"),
                historicalExam("old-2", "2026-07-07"),
                historicalExam("old-3", "2026-07-07"),
                historicalExam("old-4", "2026-07-08"),
            ),
            settings = AppSettings(activeTermId = current.id),
        )

        assertNull(ExamFocusPolicy.resolve(document, current.id))
    }

    @Test
    fun termSetupUsesHumanReadableSemesterChoicesAndKeepsCurrentValuesSelectable() {
        assertEquals("第一学期", TermSetupPolicy.semesterForCode("3")?.label)
        assertEquals("第二学期", TermSetupPolicy.semesterForCode("12")?.label)
        assertEquals("第三学期", TermSetupPolicy.semesterForCode("16")?.label)
        assertNull(TermSetupPolicy.semesterForCode("unknown"))
        assertEquals(
            "2025-2026 第二学期",
            TermSetupPolicy.standardName("2025", SemesterOption.SECOND),
        )
        assertEquals("2025–2026 学年", TermSetupPolicy.academicYearLabel("2025"))
        assertTrue(TermSetupPolicy.academicYearOptions("2025", LocalDate.of(2026, 7, 15)).contains("2025"))
        assertEquals(listOf(16, 18, 20, 21, 22, 24), TermSetupPolicy.totalWeekOptions(21))
    }

    @Test
    fun scheduleTemplateUsesLargeBreaksAfterSectionsFiveAndEleven() {
        val result = ScheduleTemplatePolicy.generate(
            ruleId = "rule",
            termId = "term",
            school = "广州南方学院",
            campus = "温泉校区",
            parameters = ScheduleTemplateParameters(
                firstStart = LocalTime.of(8, 0),
                lessonMinutes = 45,
                smallBreakMinutes = 10,
                largeBreakMinutes = 60,
                sectionCount = 15,
            ),
        )

        assertEquals(15, result.sections.size)
        assertEquals(SectionTime(1, "08:00", "08:45"), result.sections.first())
        assertEquals("12:25", result.sections.first { it.section == 5 }.end)
        assertEquals("13:25", result.sections.first { it.section == 6 }.start)
        assertEquals("18:45", result.sections.first { it.section == 11 }.end)
        assertEquals("19:45", result.sections.first { it.section == 12 }.start)
        assertEquals(SectionTime(15, "22:30", "23:15"), result.sections.last())
    }

    @Test
    fun scheduleTemplateRejectsAPlanThatCrossesMidnight() {
        val failure = runCatching {
            ScheduleTemplatePolicy.generate(
                ruleId = "rule",
                termId = "term",
                school = "广州南方学院",
                campus = "温泉校区",
                parameters = ScheduleTemplateParameters(
                    firstStart = LocalTime.of(22, 30),
                    lessonMinutes = 60,
                    smallBreakMinutes = 10,
                    largeBreakMinutes = 60,
                    sectionCount = 2,
                ),
            )
        }.exceptionOrNull()

        assertEquals("作息不能跨越午夜", failure?.message)
    }

    @Test
    fun termMetadataUpdateDoesNotRewriteRealSectionsOrEventTimes() {
        val original = Term(
            id = "term",
            name = "2025-2026 第二学期",
            campus = "温泉校区",
            academicYear = "2025",
            semesterCode = "12",
            firstWeekMonday = "2026-03-02",
        )
        val rule = TimeRule(
            id = "rule",
            termId = original.id,
            sections = listOf(SectionTime(1, "08:30", "09:10")),
        )
        val course = ScheduleEvent(
            id = "course",
            kind = EventKind.COURSE,
            recurrence = Recurrence.WEEKLY,
            title = "课程",
            termId = original.id,
            weekday = 1,
            weeks = setOf(1),
            startTime = "08:30",
            endTime = "09:10",
            startSection = 1,
            endSection = 1,
            timeRuleId = rule.id,
            timeSource = TimeSource.SECTION_RULE,
            resolutionStatus = ResolutionStatus.RESOLVED,
        )
        val document = ScheduleDocument(
            terms = listOf(original),
            timeRules = listOf(rule),
            events = listOf(course),
        )

        val result = TermMetadataUpdatePolicy.apply(
            document,
            original.copy(campus = "新校区", totalWeeks = 21),
        )

        assertEquals("新校区", result.activeTerm()?.campus)
        assertEquals(21, result.activeTerm()?.totalWeeks)
        assertEquals(document.timeRules, result.timeRules)
        assertEquals(document.events, result.events)
    }
}
