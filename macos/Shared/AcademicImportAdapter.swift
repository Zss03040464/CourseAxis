import Foundation

struct AcademicImportOptions {
    var provider: AcademicProvider
    var academicYear: String
    var semesterCode: String
    var termName: String
    var firstWeekMonday: String
    var totalWeeks: Int
    var campus: String = ""
}

struct AcademicSourceStatus: Equatable {
    var ok: Bool
    var endpoint: String = ""
    var count: Int = 0
}

struct AcademicImportBundle {
    var term: Term
    var timeRule: TimeRule?
    var events: [ScheduleEvent]
    var courseCount: Int
    var examCount: Int
    var unresolvedCount: Int
    var diagnostics: [String]
    var courseSource: AcademicSourceStatus = AcademicSourceStatus(ok: true)
    var examSource: AcademicSourceStatus = AcademicSourceStatus(ok: true)
    var sectionSource: AcademicSourceStatus = AcademicSourceStatus(ok: false)
    var courseUnresolvedCount: Int = 0
    var detectedTerm: Bool = false
    var calendarSourceURL: String? = nil
    var canCommit: Bool = true
}

protocol AcademicImportAdapter {
    var id: String { get }
    var scriptResource: String { get }

    func makeInvocation(
        provider: AcademicProvider,
        academicYear: String,
        semesterCode: String,
        script: String
    ) throws -> String

    func parse(_ raw: String, options: AcademicImportOptions) throws -> AcademicImportBundle
}

enum AcademicImportAdapterRegistry {
    static func adapter(for provider: AcademicProvider) throws -> any AcademicImportAdapter {
        switch provider.adapterId {
        case ZhengfangImportAdapter.adapterID:
            return ZhengfangImportAdapter()
        default:
            let value = provider.adapterId.trimmingCharacters(in: .whitespacesAndNewlines)
            throw NSError(
                domain: "CourseAxis.Import.Adapter",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "不支持的教务适配器：\(value.isEmpty ? "未配置" : value)"]
            )
        }
    }
}
