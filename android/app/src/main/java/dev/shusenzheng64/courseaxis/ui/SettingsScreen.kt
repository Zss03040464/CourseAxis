package dev.shusenzheng64.courseaxis.ui

import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.shusenzheng64.courseaxis.CourseAxisApplication
import dev.shusenzheng64.courseaxis.core.SettingsJellyVisualPolicy
import dev.shusenzheng64.courseaxis.core.ScheduleTemplateParameters
import dev.shusenzheng64.courseaxis.core.ScheduleTemplatePolicy
import dev.shusenzheng64.courseaxis.core.SemesterOption
import dev.shusenzheng64.courseaxis.core.StableId
import dev.shusenzheng64.courseaxis.core.TermSetupPolicy
import dev.shusenzheng64.courseaxis.data.ScheduleJsonCodec
import dev.shusenzheng64.courseaxis.data.ProviderTermIdentity
import dev.shusenzheng64.courseaxis.data.CredentialProviderRegistry
import dev.shusenzheng64.courseaxis.data.SecureCredentialStore
import dev.shusenzheng64.courseaxis.data.SyncResult
import dev.shusenzheng64.courseaxis.importer.AcademicDefaultProfileState
import dev.shusenzheng64.courseaxis.importer.AcademicProviderCatalog
import dev.shusenzheng64.courseaxis.importer.AcademicProviderDisplayPolicy
import dev.shusenzheng64.courseaxis.importer.AcademicProviderSelectionPolicy
import dev.shusenzheng64.courseaxis.importer.AcademicProviderSemesterPolicy
import dev.shusenzheng64.courseaxis.importer.AcademicWebDataCleaner
import dev.shusenzheng64.courseaxis.model.AppSettings
import dev.shusenzheng64.courseaxis.model.SectionTime
import dev.shusenzheng64.courseaxis.model.Term
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

@Composable
fun SettingsScreen(
    app: CourseAxisApplication,
    contentPadding: PaddingValues,
    snackbar: SnackbarHostState,
) {
    val document by app.repository.document.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val providers = remember {
        runCatching { AcademicProviderCatalog.load(context.assets) }.getOrDefault(emptyList())
    }
    val providerLabels = remember(providers) { AcademicProviderDisplayPolicy.labels(providers) }
    var forcedProviderId by remember { mutableStateOf<String?>(null) }
    val providerSelection = remember(
        providers,
        forcedProviderId,
        document.settings.selectedAcademicProviderId,
        document.settings.activeTermId,
        document.terms,
    ) {
        AcademicProviderSelectionPolicy.resolve(
            providers = providers,
            requestedProviderId = forcedProviderId
                ?: document.settings.selectedAcademicProviderId,
            terms = document.terms,
            activeTermId = document.settings.activeTermId,
        )
    }
    val selectedProvider = providerSelection.provider
    val activeDocumentTerm = document.activeTerm()
    val selectedProviderTerm = providerSelection.term
    val term = when {
        forcedProviderId != null -> selectedProviderTerm
        activeDocumentTerm?.providerId == null -> activeDocumentTerm
        activeDocumentTerm.providerId == selectedProvider?.id -> activeDocumentTerm
        else -> selectedProviderTerm
    }
    val termProvider = providers.firstOrNull { it.id == term?.providerId }
    val metadataProvider = termProvider ?: selectedProvider.takeIf { term == null }
    val rule = document.timeRuleFor(term?.id)

    LaunchedEffect(document.settings.activeTermId, forcedProviderId) {
        if (
            forcedProviderId != null &&
            document.activeTerm()?.providerId == forcedProviderId
        ) {
            forcedProviderId = null
        }
    }
    val semesterOptions = metadataProvider
        ?.let(AcademicProviderSemesterPolicy::options)
        .orEmpty()
        .ifEmpty { TermSetupPolicy.semesterOptions }

    val today = LocalDate.now()
    var school by remember(term?.id, term?.updatedAt, metadataProvider?.id) {
        mutableStateOf(term?.school ?: metadataProvider?.schoolName.orEmpty())
    }
    var campus by remember(term?.id, term?.updatedAt, metadataProvider?.id) {
        mutableStateOf(
            term?.campus ?: if (metadataProvider?.id == SecureCredentialStore.DEFAULT_PROVIDER_ID) {
                "温泉校区"
            } else {
                ""
            },
        )
    }
    var academicYear by remember(term?.id, term?.updatedAt, metadataProvider?.id) {
        mutableStateOf(term?.academicYear?.takeIf { it.toIntOrNull() != null } ?: defaultAcademicYear(today))
    }
    var semester by remember(term?.id, term?.updatedAt, metadataProvider?.id, semesterOptions) {
        mutableStateOf(
            metadataProvider?.let { AcademicProviderSemesterPolicy.option(it, term?.semesterCode.orEmpty()) }
                ?: TermSetupPolicy.semesterForCode(term?.semesterCode.orEmpty())
                ?: defaultSemester(today).takeIf { it in semesterOptions }
                ?: semesterOptions.first(),
        )
    }
    var firstMonday by remember(term?.id, term?.updatedAt, metadataProvider?.id) {
        mutableStateOf(
            term?.let { runCatching { it.firstWeekMondayDate() }.getOrNull() }
                ?: suggestedFirstMonday(academicYear, semester),
        )
    }
    var totalWeeks by remember(term?.id, term?.updatedAt, metadataProvider?.id) {
        mutableIntStateOf(term?.totalWeeks ?: 20)
    }
    var firstStart by remember(rule?.id, rule?.updatedAt) {
        mutableStateOf(rule?.sections?.minByOrNull { it.section }?.start?.let(LocalTime::parse) ?: LocalTime.of(8, 0))
    }
    var lessonMinutes by remember(rule?.id, rule?.updatedAt) {
        mutableIntStateOf(inferredLessonMinutes(rule?.sections.orEmpty()))
    }
    var smallBreakMinutes by remember(rule?.id, rule?.updatedAt) {
        mutableIntStateOf(inferredBreakMinutes(rule?.sections.orEmpty(), large = false))
    }
    var largeBreakMinutes by remember(rule?.id, rule?.updatedAt) {
        mutableIntStateOf(inferredBreakMinutes(rule?.sections.orEmpty(), large = true))
    }
    var sectionCount by remember(rule?.id, rule?.updatedAt) {
        mutableIntStateOf((rule?.sections?.size ?: 15).coerceIn(1, 24))
    }

    var dayStart by remember(document.settings) { mutableStateOf(document.settings.dayStart) }
    var dayEnd by remember(document.settings) { mutableStateOf(document.settings.dayEnd) }
    var weekends by remember(document.settings) { mutableStateOf(document.settings.showWeekends) }
    var widgetUpcomingCount by remember(document.settings) {
        mutableIntStateOf(document.settings.widgetUpcomingCount.coerceIn(1, 5))
    }

    val initialSync = remember { app.syncSettings.load() }
    var server by remember { mutableStateOf(initialSync.serverUrl) }
    var token by remember { mutableStateOf(initialSync.token) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmClearAccount by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var showPrivacyNotice by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(ScheduleJsonCodec.encode(document, 2).toByteArray())
                        } ?: error("无法打开输出文件")
                    }
                }.onSuccess {
                    snackbar.showSnackbar("JSON 已导出")
                }.onFailure {
                    error = it.message
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                            ScheduleJsonCodec.decode(it.readText())
                        } ?: error("无法读取文件")
                    }
                }.onSuccess {
                    app.repository.replace(it)
                    snackbar.showSnackbar("JSON 已导入")
                }.onFailure {
                    error = it.message
                }
            }
        }
    }

    val saveTermMetadata: () -> Unit = {
        scope.launch {
            runCatching {
                require(totalWeeks in 1..40)
                val name = TermSetupPolicy.standardName(academicYear, semester)
                val providerForTerm = termProvider ?: selectedProvider.takeIf { term == null }
                val providerId = term?.providerId ?: providerForTerm?.id
                val semesterCode = providerForTerm
                    ?.let { AcademicProviderSemesterPolicy.code(it, semester) }
                    ?: semester.code
                val id = term?.id ?: ProviderTermIdentity.canonicalID(
                    requireNotNull(providerId) { "请选择已支持的学校" },
                    school,
                    campus,
                    academicYear,
                    semesterCode,
                )
                val value = Term(
                    id = id,
                    name = name,
                    school = school.trim(),
                    campus = campus.trim(),
                    academicYear = academicYear.trim(),
                    semesterCode = semesterCode,
                    firstWeekMonday = firstMonday.toString(),
                    totalWeeks = totalWeeks,
                    providerId = providerId,
                )
                app.repository.updateTermMetadata(value)
            }.onSuccess {
                snackbar.showSnackbar("学期信息已保存，真实作息未改变")
            }.onFailure {
                error = it.message ?: "保存失败"
            }
        }
    }
    val saveScheduleTemplate: () -> Unit = {
        scope.launch {
            runCatching {
                val target = term ?: error("请先保存学期信息")
                val generated = ScheduleTemplatePolicy.generate(
                    ruleId = rule?.id ?: StableId.of("rule", target.id, "guided-schedule"),
                    termId = target.id,
                    school = target.school,
                    campus = target.campus,
                    parameters = ScheduleTemplateParameters(
                        firstStart = firstStart,
                        lessonMinutes = lessonMinutes,
                        smallBreakMinutes = smallBreakMinutes,
                        largeBreakMinutes = largeBreakMinutes,
                        sectionCount = sectionCount,
                    ),
                )
                app.repository.updateTermAndRule(target, generated)
            }.onSuccess {
                snackbar.showSnackbar("真实作息已生成并保存")
            }.onFailure {
                error = it.message ?: "作息生成失败"
            }
        }
    }
    val saveDisplaySettings: () -> Unit = {
        scope.launch {
            runCatching {
                val start = parseTimeMinutes(dayStart) ?: error("开始时间格式错误")
                val end = parseTimeMinutes(dayEnd) ?: error("结束时间格式错误")
                require(end > start)
                app.repository.setSettings(
                    document.settings.copy(
                        dayStart = formatMinutes(start),
                        dayEnd = formatMinutes(end),
                        showWeekends = weekends,
                        widgetUpcomingCount = widgetUpcomingCount,
                    ),
                )
            }.onSuccess {
                snackbar.showSnackbar("显示设置已保存")
            }.onFailure {
                error = it.message
            }
        }
    }

    SettingsJellyBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsJellyHero()

            SettingsJellyCard(
                icon = Icons.Default.CalendarMonth,
                title = "学期信息",
                supporting = "教务导入会直接使用这里选择的学期",
            ) {
                val schoolOptions = providerLabels.values.toList()
                    .plus(term?.school?.takeIf { termProvider == null && it.isNotBlank() })
                    .filterNotNull()
                    .distinct()
                val schoolPickerValue = (termProvider ?: selectedProvider.takeIf { term == null })
                    ?.let { providerLabels[it.id] }
                    ?: school
                val campusOptions = document.terms
                    .filter {
                        if (term?.providerId != null) {
                            it.providerId == term.providerId
                        } else {
                            it.providerId == null && it.school == school
                        }
                    }
                    .map { it.campus }
                    .filter { it.isNotBlank() }
                    .plus(
                        "温泉校区".takeIf {
                            metadataProvider?.id == SecureCredentialStore.DEFAULT_PROVIDER_ID
                        },
                    )
                    .plus(campus.takeIf { it.isNotBlank() })
                    .filterNotNull()
                    .distinct()
                JellyDropdownField(
                    value = schoolPickerValue,
                    options = schoolOptions,
                    onValueChange = { selectedSchool ->
                        val providerId = providerLabels.entries
                            .firstOrNull { it.value == selectedSchool }
                            ?.key
                        val next = providers.firstOrNull { it.id == providerId }
                        if (next == null) {
                            school = selectedSchool
                            return@JellyDropdownField
                        }
                        school = next.schoolName
                        if (next.id == selectedProvider?.id && term?.providerId == next.id) {
                            return@JellyDropdownField
                        }
                        forcedProviderId = next.id
                        scope.launch {
                            app.repository.setSettings(
                                app.repository.document.value.settings.copy(
                                    selectedAcademicProviderId = next.id,
                                ),
                            )
                            document.terms
                                .filter { it.providerId == next.id }
                                .maxByOrNull { it.updatedAt }
                                ?.let { app.repository.setActiveTerm(it.id) }
                        }
                    },
                    label = "学校",
                    modifier = Modifier.fillMaxWidth(),
                )
                JellyDropdownField(campus, campusOptions, { campus = it }, "校区", Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val yearLabels = TermSetupPolicy.academicYearOptions(academicYear, today)
                        .associateWith { year -> "$year–${year.toInt() + 1}" }
                    JellyDropdownField(
                        value = yearLabels.getValue(academicYear),
                        options = yearLabels.values.toList(),
                        onValueChange = { selected ->
                            academicYear = yearLabels.entries.first { it.value == selected }.key
                            firstMonday = suggestedFirstMonday(academicYear, semester)
                        },
                        label = "学年",
                        modifier = Modifier.weight(1f),
                    )
                    JellyDropdownField(
                        value = semester.label,
                        options = semesterOptions.map { it.label },
                        onValueChange = { selected ->
                            semester = semesterOptions.first { it.label == selected }
                            firstMonday = suggestedFirstMonday(academicYear, semester)
                        },
                        label = "学期",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JellyDatePickerField(
                        value = firstMonday,
                        onValueChange = { firstMonday = it },
                        label = "第一周周一",
                        modifier = Modifier.weight(1.3f),
                    )
                    val weekLabels = TermSetupPolicy.totalWeekOptions(totalWeeks).map { "$it 周" }
                    JellyDropdownField(
                        value = "$totalWeeks 周",
                        options = weekLabels,
                        onValueChange = { totalWeeks = it.substringBefore(" ").toInt() },
                        label = "总周数",
                        modifier = Modifier.weight(.7f),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(SettingsJellyVisualPolicy.accentSoftArgb),
                    border = BorderStroke(.6.dp, Color(SettingsJellyVisualPolicy.highlightArgb)),
                ) {
                    Text(
                        text = TermSetupPolicy.standardName(academicYear, semester),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        color = Color(SettingsJellyVisualPolicy.primaryTextArgb),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                JellyPrimaryButton(
                    Icons.Default.Save,
                    "保存学期信息",
                    saveTermMetadata,
                    enabled = term != null || selectedProvider != null,
                )
            }

            SettingsJellyCard(
                icon = Icons.Default.AccessTime,
                title = "真实作息",
                supporting = "当前已保存 ${rule?.sections?.size ?: 0} 节；生成前不会改动原作息",
            ) {
                JellyTimePickerField(
                    value = firstStart,
                    onValueChange = { firstStart = it },
                    label = "第一节开始时间",
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JellyDropdownField(
                        value = "$lessonMinutes 分钟",
                        options = (ScheduleTemplatePolicy.lessonMinuteOptions + lessonMinutes)
                            .distinct().sorted().map { "$it 分钟" },
                        onValueChange = { lessonMinutes = it.substringBefore(" ").toInt() },
                        label = "每节课",
                        modifier = Modifier.weight(1f),
                    )
                    JellyDropdownField(
                        value = "$sectionCount 节",
                        options = (ScheduleTemplatePolicy.sectionCountOptions + sectionCount)
                            .distinct().sorted().map { "$it 节" },
                        onValueChange = { sectionCount = it.substringBefore(" ").toInt() },
                        label = "每日节数",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JellyDropdownField(
                        value = "$smallBreakMinutes 分钟",
                        options = (ScheduleTemplatePolicy.smallBreakMinuteOptions + smallBreakMinutes)
                            .distinct().sorted().map { "$it 分钟" },
                        onValueChange = { smallBreakMinutes = it.substringBefore(" ").toInt() },
                        label = "小课间",
                        modifier = Modifier.weight(1f),
                    )
                    JellyDropdownField(
                        value = "$largeBreakMinutes 分钟",
                        options = (ScheduleTemplatePolicy.largeBreakMinuteOptions + largeBreakMinutes)
                            .distinct().sorted().map { "$it 分钟" },
                        onValueChange = { largeBreakMinutes = it.substringBefore(" ").toInt() },
                        label = "大课间（第 5/11 节后）",
                        modifier = Modifier.weight(1f),
                    )
                }
                val preview = term?.let { currentTerm ->
                    runCatching {
                        ScheduleTemplatePolicy.generate(
                            ruleId = rule?.id ?: "preview",
                            termId = currentTerm.id,
                            school = currentTerm.school,
                            campus = currentTerm.campus,
                            parameters = ScheduleTemplateParameters(
                                firstStart,
                                lessonMinutes,
                                smallBreakMinutes,
                                largeBreakMinutes,
                                sectionCount,
                            ),
                        )
                    }.getOrNull()
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(SettingsJellyVisualPolicy.accentSoftArgb),
                    border = BorderStroke(.6.dp, Color(SettingsJellyVisualPolicy.highlightArgb)),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(
                            text = preview?.sections?.let {
                                "预览：第 1 节 ${it.first().start}–${it.first().end} · 第 ${it.size} 节 ${it.last().start}–${it.last().end}"
                            } ?: "请先保存学期信息",
                            color = Color(SettingsJellyVisualPolicy.primaryTextArgb),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "只有点击下方按钮才会替换当前学期作息。",
                            color = Color(SettingsJellyVisualPolicy.secondaryTextArgb),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                JellyPrimaryButton(
                    Icons.Default.Save,
                    "生成并保存作息",
                    saveScheduleTemplate,
                    enabled = term != null && preview != null,
                )
            }

            SettingsJellyCard(
                icon = Icons.Default.Tune,
                title = "时间轴与桌面组件",
                supporting = "控制主界面范围和桌面最近课程数量",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JellyTimePickerField(
                        value = runCatching { LocalTime.parse(dayStart) }.getOrDefault(LocalTime.of(7, 30)),
                        onValueChange = { dayStart = formatMinutes(it.hour * 60 + it.minute) },
                        label = "时间轴开始",
                        modifier = Modifier.weight(1f),
                    )
                    JellyTimePickerField(
                        value = runCatching { LocalTime.parse(dayEnd) }.getOrDefault(LocalTime.of(22, 30)),
                        onValueChange = { dayEnd = formatMinutes(it.hour * 60 + it.minute) },
                        label = "时间轴结束",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "显示周末",
                            color = Color(SettingsJellyVisualPolicy.primaryTextArgb),
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "周视图保留周六和周日",
                            color = Color(SettingsJellyVisualPolicy.secondaryTextArgb),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = weekends,
                        onCheckedChange = { weekends = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(SettingsJellyVisualPolicy.accentArgb),
                            uncheckedThumbColor = Color(SettingsJellyVisualPolicy.secondaryTextArgb),
                            uncheckedTrackColor = Color(SettingsJellyVisualPolicy.fieldSurfaceArgb),
                            uncheckedBorderColor = Color(SettingsJellyVisualPolicy.highlightArgb),
                        ),
                    )
                }
                Text(
                    text = "桌面显示未来最近 $widgetUpcomingCount 项",
                    color = Color(SettingsJellyVisualPolicy.primaryTextArgb),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "小组件会根据桌面高度逐行显示 1 至 5 项。",
                    color = Color(SettingsJellyVisualPolicy.secondaryTextArgb),
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = widgetUpcomingCount.toFloat(),
                    onValueChange = { widgetUpcomingCount = it.toInt().coerceIn(1, 5) },
                    valueRange = 1f..5f,
                    steps = 3,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(SettingsJellyVisualPolicy.accentArgb),
                        activeTrackColor = Color(SettingsJellyVisualPolicy.accentArgb),
                        inactiveTrackColor = Color(SettingsJellyVisualPolicy.secondaryButtonArgb),
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    (1..5).forEach {
                        Text(
                            text = it.toString(),
                            color = Color(SettingsJellyVisualPolicy.secondaryTextArgb),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                JellyPrimaryButton(Icons.Default.Save, "保存显示设置", saveDisplaySettings)
            }

            SettingsJellyCard(
                icon = Icons.Default.FolderOpen,
                title = "数据文件",
                supporting = "导入或备份统一的 CourseAxis JSON",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JellySecondaryButton(
                        icon = Icons.Default.FileUpload,
                        text = "导出 JSON",
                        onClick = { exportLauncher.launch("courseaxis-${LocalDate.now()}.json") },
                        modifier = Modifier.weight(1f),
                    )
                    JellySecondaryButton(
                        icon = Icons.Default.FileDownload,
                        text = "导入 JSON",
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(SettingsJellyVisualPolicy.accentSoftArgb),
                    border = BorderStroke(.6.dp, Color(SettingsJellyVisualPolicy.highlightArgb)),
                ) {
                    Text(
                        text = "${document.events.size} 个事件 · ${document.timeRules.size} 套作息 · schema ${document.schemaVersion}",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        color = Color(SettingsJellyVisualPolicy.secondaryTextArgb),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    text = "清除账号只删除教务凭据、Cookie 与网页缓存；清除全部还会删除课表、同步设置和本机备份。",
                    color = Color(SettingsJellyVisualPolicy.secondaryTextArgb),
                    style = MaterialTheme.typography.bodySmall,
                )
                JellySecondaryButton(
                    icon = Icons.Default.Tune,
                    text = "查看隐私说明",
                    onClick = { showPrivacyNotice = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JellySecondaryButton(
                        icon = Icons.Default.PersonOff,
                        text = "仅清账号",
                        onClick = { confirmClearAccount = true },
                        modifier = Modifier.weight(1f),
                    )
                    JellySecondaryButton(
                        icon = Icons.Default.DeleteForever,
                        text = "清除全部本机数据",
                        onClick = { confirmClearAll = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SettingsJellyCard(
                icon = Icons.Default.CloudSync,
                title = "自托管同步（高级）",
                supporting = "多台设备通过你自己的服务器交换课程 JSON",
            ) {
                Text(
                    text = "它让多台设备通过你自己的 HTTPS 服务器交换课程 JSON。不会上传教务账号、密码或 Cookie；只有一台设备时无需设置。",
                    color = Color(SettingsJellyVisualPolicy.secondaryTextArgb),
                    style = MaterialTheme.typography.bodySmall,
                )
                JellyOutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = "服务器地址，如 https://schedule.example.com",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                JellyOutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = "Bearer 令牌（至少 24 字符）",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    JellySecondaryButton(
                        icon = Icons.Default.CloudDownload,
                        text = "拉取并合并",
                        enabled = !busy,
                        onClick = {
                            app.syncSettings.save(server, token)
                            busy = true
                            scope.launch {
                                when (val result = app.syncClient.pull()) {
                                    is SyncResult.Success -> snackbar.showSnackbar("${result.message} · revision ${result.revision}")
                                    is SyncResult.Failure -> error = result.message
                                }
                                busy = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    JellySecondaryButton(
                        icon = Icons.Default.CloudUpload,
                        text = "上传",
                        enabled = !busy,
                        onClick = {
                            app.syncSettings.save(server, token)
                            busy = true
                            scope.launch {
                                when (val result = app.syncClient.push()) {
                                    is SyncResult.Success -> snackbar.showSnackbar("${result.message} · revision ${result.revision}")
                                    is SyncResult.Failure -> error = result.message
                                }
                                busy = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = Color(SettingsJellyVisualPolicy.accentArgb),
                            trackColor = Color(SettingsJellyVisualPolicy.secondaryButtonArgb),
                            strokeWidth = 3.dp,
                        )
                    }
                }
            }
            Spacer(Modifier.size(30.dp))
        }
    }

    if (showPrivacyNotice) {
        AlertDialog(
            onDismissRequest = { showPrivacyNotice = false },
            title = { Text("隐私说明") },
            text = {
                Text(
                    "CourseAxis 默认不含分析、广告或崩溃上报。课表与考试保存在本机；可保存的教务账号和密码由 Android Keystore 加密，验证码不保存，Cookie 留在系统 WebView。启用自托管同步后，仅课程 JSON 会发往你配置的服务器。",
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyNotice = false }) { Text("知道了") }
            },
        )
    }

    if (confirmClearAccount) {
        AlertDialog(
            onDismissRequest = { confirmClearAccount = false },
            title = { Text("清除${selectedProvider?.schoolName.orEmpty()}教务账号与会话？") },
            text = { Text("其他学校、课表和同步设置会保留；当前学校的账号与浏览数据会被删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearAccount = false
                        val cleanupWebView = WebView(context)
                        val clearSession: () -> Unit = {
                            cleanupWebView.destroy()
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        credentialProviderIds(selectedProvider?.id).forEach { providerId ->
                                            SecureCredentialStore(context, providerId).clear()
                                        }
                                        AcademicDefaultProfileState(context).clear()
                                    }
                                }.onSuccess {
                                    snackbar.showSnackbar("已清除教务账号与登录会话")
                                }.onFailure {
                                    error = it.message ?: "账号清除失败"
                                }
                            }
                            Unit
                        }
                        if (selectedProvider != null) {
                            AcademicWebDataCleaner.clearProvider(
                                provider = selectedProvider,
                                fallbackWebView = cleanupWebView,
                                onComplete = clearSession,
                                onFailure = {
                                    cleanupWebView.destroy()
                                    error = it.message ?: "登录会话清除失败"
                                },
                            )
                        } else {
                            AcademicWebDataCleaner.clear(
                                webView = cleanupWebView,
                                onComplete = clearSession,
                            )
                        }
                    },
                ) { Text("确认清除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAccount = false }) { Text("取消") }
            },
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("清除全部本机数据？") },
            text = { Text("这会永久删除课表、教务账号与会话、同步令牌及本机备份。请先导出需要保留的 JSON。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearAll = false
                        val cleanupWebView = WebView(context)
                        AcademicWebDataCleaner.clearAllProviders(
                            providers = providers,
                            fallbackWebView = cleanupWebView,
                            onComplete = {
                                cleanupWebView.destroy()
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            val registeredProviderIds = providers.map { it.id } +
                                                CredentialProviderRegistry.providerIds(context)
                                            credentialProviderIds(*registeredProviderIds.toTypedArray())
                                                .forEach { providerId ->
                                                    SecureCredentialStore(context, providerId).clear()
                                                }
                                            CredentialProviderRegistry.clear(context)
                                            AcademicDefaultProfileState(context).clear()
                                            app.syncSettings.clear()
                                        }
                                        app.repository.clearAllLocalData()
                                    }.onSuccess {
                                        server = ""
                                        token = ""
                                        snackbar.showSnackbar("已清除全部本机数据")
                                    }.onFailure {
                                        error = it.message ?: "本机数据清除失败"
                                    }
                                }
                            },
                            onFailure = {
                                cleanupWebView.destroy()
                                error = it.message ?: "网页会话清除失败，本机数据未删除"
                            },
                        )
                    },
                ) { Text("永久清除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("取消") }
            },
        )
    }

    if (error != null) {
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("操作失败") },
            text = { Text(error.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { error = null }) {
                    Text("确定", color = Color(SettingsJellyVisualPolicy.accentArgb))
                }
            },
            shape = RoundedCornerShape(SettingsJellyVisualPolicy.cardCornerDp.dp),
            containerColor = Color(SettingsJellyVisualPolicy.cardStrongSurfaceArgb),
            titleContentColor = Color(SettingsJellyVisualPolicy.primaryTextArgb),
            textContentColor = Color(SettingsJellyVisualPolicy.secondaryTextArgb),
        )
    }
}

private fun defaultAcademicYear(today: LocalDate): String =
    (if (today.monthValue >= 8) today.year else today.year - 1).toString()

private fun defaultSemester(today: LocalDate): SemesterOption =
    if (today.monthValue >= 8 || today.monthValue <= 1) SemesterOption.FIRST else SemesterOption.SECOND

private fun suggestedFirstMonday(
    academicYear: String,
    semester: SemesterOption,
): LocalDate {
    val startYear = academicYear.toIntOrNull() ?: LocalDate.now().year
    val approximateStart = when (semester) {
        SemesterOption.FIRST -> LocalDate.of(startYear, 9, 1)
        SemesterOption.SECOND -> LocalDate.of(startYear + 1, 2, 20)
        SemesterOption.THIRD -> LocalDate.of(startYear + 1, 6, 1)
    }
    return approximateStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
}

private fun inferredLessonMinutes(sections: List<SectionTime>): Int = sections
    .mapNotNull { section ->
        val start = runCatching { LocalTime.parse(section.start) }.getOrNull() ?: return@mapNotNull null
        val end = runCatching { LocalTime.parse(section.end) }.getOrNull() ?: return@mapNotNull null
        (end.hour * 60 + end.minute - start.hour * 60 - start.minute).takeIf { it in 1..180 }
    }
    .groupingBy { it }
    .eachCount()
    .maxByOrNull { it.value }
    ?.key ?: 45

private fun inferredBreakMinutes(sections: List<SectionTime>, large: Boolean): Int {
    val values = sections.sortedBy { it.section }.zipWithNext().mapNotNull { (current, next) ->
        val isLarge = current.section == 5 || current.section == 11
        if (isLarge != large) return@mapNotNull null
        val end = runCatching { LocalTime.parse(current.end) }.getOrNull() ?: return@mapNotNull null
        val start = runCatching { LocalTime.parse(next.start) }.getOrNull() ?: return@mapNotNull null
        (start.hour * 60 + start.minute - end.hour * 60 - end.minute).takeIf { it in 0..240 }
    }
    return values.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        ?: if (large) 60 else 10
}

private fun parseTimeMinutes(value: String): Int? = runCatching {
    LocalTime.parse(value.trim()).let { it.hour * 60 + it.minute }
}.getOrNull()

private fun formatMinutes(value: Int): String = "%02d:%02d".format(value / 60, value % 60)

private fun credentialProviderIds(vararg providerIds: String?): List<String> =
    providerIds.filterNotNull().distinct().ifEmpty {
        listOf(SecureCredentialStore.DEFAULT_PROVIDER_ID)
    }
