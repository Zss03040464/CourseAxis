import Foundation

struct AcademicProviderSelection: Equatable {
    var provider: AcademicProvider?
    var term: Term?
}

enum AcademicProviderSelectionPolicy {
    static func resolve(
        providers: [AcademicProvider],
        requestedProviderID: String?,
        terms: [Term],
        activeTermID: String?
    ) -> AcademicProviderSelection {
        guard let provider = providers.first(where: { $0.id == requestedProviderID }) ?? providers.first else {
            return AcademicProviderSelection(provider: nil, term: nil)
        }
        let providerTerms = terms.filter { $0.providerId == provider.id }
        let active = providerTerms.first { $0.id == activeTermID }
        let latest = providerTerms.max {
            LocalValue.instant($0.updatedAt) < LocalValue.instant($1.updatedAt)
        }
        return AcademicProviderSelection(provider: provider, term: active ?? latest)
    }
}

enum AcademicProviderDisplayPolicy {
    static func labels(for providers: [AcademicProvider]) -> [String: String] {
        let counts = Dictionary(grouping: providers, by: \.schoolName).mapValues(\.count)
        return Dictionary(uniqueKeysWithValues: providers.map { provider in
            let label = counts[provider.schoolName] == 1
                ? provider.schoolName
                : "\(provider.schoolName) · \(provider.vendor) · \(provider.id)"
            return (provider.id, label)
        })
    }
}

enum AcademicSemester: String, CaseIterable, Identifiable {
    case first
    case second
    case third

    var id: String { rawValue }

    var label: String {
        switch self {
        case .first: "第一学期"
        case .second: "第二学期"
        case .third: "第三学期"
        }
    }
}

enum AcademicProviderSemesterPolicy {
    static func options(for provider: AcademicProvider) -> [AcademicSemester] {
        AcademicSemester.allCases.filter { provider.semesterCodes[$0.rawValue] != nil }
    }

    static func code(for semester: AcademicSemester, provider: AcademicProvider) -> String? {
        provider.semesterCodes[semester.rawValue]
    }

    static func semester(for code: String, provider: AcademicProvider) -> AcademicSemester? {
        AcademicSemester.allCases.first { provider.semesterCodes[$0.rawValue] == code }
    }
}
