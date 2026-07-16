import AppIntents

struct CourseAxisWidgetConfigurationIntent:WidgetConfigurationIntent {
    static var title:LocalizedStringResource="课轴显示设置"
    static var description=IntentDescription("选择桌面课程表的隐私和考试强调方式。")
    @Parameter(title:"隐私模式") var privacyMode:Bool?
    @Parameter(title:"强调考试") var emphasizeExams:Bool?
    init(){privacyMode=false;emphasizeExams=true}

    var resolvedPrivacyMode:Bool { privacyMode ?? false }
    var resolvedEmphasizeExams:Bool { emphasizeExams ?? true }
}
