import Foundation
import XCTest
@testable import CourseAxisCore

final class AcademicProviderCatalogTests: XCTestCase {
    private let catalog = #"{"schemaVersion":1,"providers":["synthetic-school-a"]}"#.data(using: .utf8)!
    private let provider = #"""
    {
        "schemaVersion":1,
        "id":"synthetic-school-a",
        "schoolName":"合成大学 A",
        "vendor":"ZHENGFANG",
        "adapterId":"zhengfang-v1",
        "sessionNamespace":"11111111-1111-4111-8111-111111111111",
        "allowedOrigin":"https://jw.synthetic-a.example",
        "pathPrefix":"/jwglxt/",
        "loginPath":"/jwglxt/xtgl/login_slogin.html",
        "homePath":"/jwglxt/xtgl/index_initMenu.html",
        "requiresThirdPartyCookies":false,
        "semesterCodes":{"first":"3","second":"12"}
    }
    """#.data(using: .utf8)!

    @MainActor
    func testAcademicProviderCatalogAndWebSessionIsolation() throws {
        let providers = try AcademicProviderCatalog.decode(
            catalogData: catalog,
            providerData: ["synthetic-school-a": provider]
        )
        let selected = try XCTUnwrap(providers.first)

        XCTAssertEqual(selected.id, "synthetic-school-a")
        XCTAssertEqual(
            try AcademicWebSession.identifier(for: selected),
            UUID(uuidString: "11111111-1111-4111-8111-111111111111")
        )
        XCTAssertEqual(
            try AcademicWebSession.dataStore(for: selected).identifier,
            UUID(uuidString: "11111111-1111-4111-8111-111111111111")
        )

        let other = AcademicProvider(
            id: "synthetic-school-b",
            schoolName: "合成大学 B",
            vendor: "ZHENGFANG",
            allowedOrigin: "https://jw.synthetic-b.example",
            pathPrefix: "/jwglxt/",
            loginPath: "/jwglxt/xtgl/login_slogin.html",
            homePath: "/jwglxt/xtgl/index_initMenu.html",
            requiresThirdPartyCookies: false,
            semesterCodes: ["first": "3", "second": "12"],
            adapterId: "zhengfang-v1",
            sessionNamespace: "22222222-2222-4222-8222-222222222222"
        )
        XCTAssertNotEqual(
            try AcademicWebSession.identifier(for: selected),
            try AcademicWebSession.identifier(for: other)
        )
    }

    func testAcademicProviderCatalogFailsClosed() throws {
        XCTAssertThrowsError(
            try AcademicProviderCatalog.decode(
                catalogData: #"{"schemaVersion":1,"providers":["synthetic-school-a","synthetic-school-a"]}"#.data(using: .utf8)!,
                providerData: ["synthetic-school-a": provider]
            )
        )
        XCTAssertThrowsError(
            try AcademicProviderCatalog.decode(
                catalogData: catalog,
                providerData: ["synthetic-school-a": replacing("zhengfang-v1", with: "unknown-v9")]
            )
        )
        XCTAssertThrowsError(
            try AcademicProviderCatalog.decode(
                catalogData: catalog,
                providerData: ["synthetic-school-a": replacing("11111111-1111-4111-8111-111111111111", with: "not-a-uuid")]
            )
        )
        XCTAssertThrowsError(
            try AcademicProviderCatalog.decode(
                catalogData: #"{"schemaVersion":1,"providers":["../unsafe"]}"#.data(using: .utf8)!,
                providerData: ["../unsafe": replacing("synthetic-school-a", with: "../unsafe")]
            )
        )
        XCTAssertThrowsError(
            try AcademicProviderCatalog.decode(
                catalogData: catalog,
                providerData: [:]
            )
        )
    }

    func testAcademicProviderCatalogRejectsSharedSessionNamespace() throws {
        let second = String(decoding: provider, as: UTF8.self)
            .replacingOccurrences(of: "synthetic-school-a", with: "synthetic-school-b")
            .replacingOccurrences(of: "合成大学 A", with: "合成大学 B")
            .replacingOccurrences(of: "jw.synthetic-a.example", with: "jw.synthetic-b.example")
            .data(using: .utf8)!
        let twoProviders = #"{"schemaVersion":1,"providers":["synthetic-school-a","synthetic-school-b"]}"#.data(using: .utf8)!

        XCTAssertThrowsError(
            try AcademicProviderCatalog.decode(
                catalogData: twoProviders,
                providerData: [
                    "synthetic-school-a": provider,
                    "synthetic-school-b": second,
                ]
            )
        )
    }

    func testAcademicProviderSelectionNeverBorrowsAnotherProvidersTerm() throws {
        let first = try AcademicProviderCatalog.decode(
            catalogData: catalog,
            providerData: ["synthetic-school-a": provider]
        ).first!
        let second = AcademicProvider(
            id: "synthetic-school-b",
            schoolName: "合成大学 A",
            vendor: "OTHER_VENDOR",
            allowedOrigin: "https://jw.synthetic-b.example",
            pathPrefix: "/jwglxt/",
            loginPath: "/jwglxt/xtgl/login_slogin.html",
            homePath: "/jwglxt/xtgl/index_initMenu.html",
            requiresThirdPartyCookies: false,
            semesterCodes: ["first": "A", "second": "B"],
            adapterId: "zhengfang-v1",
            sessionNamespace: "22222222-2222-4222-8222-222222222222"
        )
        let firstTerm = Term(
            id: "term-a",
            name: "A",
            providerId: first.id,
            firstWeekMonday: "2026-09-07",
            updatedAt: "2026-01-01T00:00:00Z"
        )
        let secondTerm = Term(
            id: "term-b",
            name: "B",
            providerId: second.id,
            firstWeekMonday: "2027-09-06",
            updatedAt: "2027-01-01T00:00:00Z"
        )

        let selected = AcademicProviderSelectionPolicy.resolve(
            providers: [first, second],
            requestedProviderID: first.id,
            terms: [firstTerm, secondTerm],
            activeTermID: secondTerm.id
        )
        XCTAssertEqual(selected.provider?.id, first.id)
        XCTAssertEqual(selected.term?.id, firstTerm.id)

        let withoutTerm = AcademicProviderSelectionPolicy.resolve(
            providers: [first, second],
            requestedProviderID: second.id,
            terms: [firstTerm],
            activeTermID: firstTerm.id
        )
        XCTAssertEqual(withoutTerm.provider?.id, second.id)
        XCTAssertNil(withoutTerm.term)

        let labels = AcademicProviderDisplayPolicy.labels(for: [first, second])
        XCTAssertNotEqual(labels[first.id], labels[second.id])
    }

    func testAcademicProviderSemesterCodesUseSemanticOptions() throws {
        let selected = try AcademicProviderCatalog.decode(
            catalogData: catalog,
            providerData: ["synthetic-school-a": provider]
        ).first!

        XCTAssertEqual(AcademicProviderSemesterPolicy.options(for: selected), [.first, .second])
        XCTAssertEqual(AcademicProviderSemesterPolicy.code(for: .first, provider: selected), "3")
        XCTAssertEqual(AcademicProviderSemesterPolicy.semester(for: "12", provider: selected), .second)
        XCTAssertNil(AcademicProviderSemesterPolicy.semester(for: "unknown", provider: selected))
    }

    func testProviderTermActivationAndDeletionKeepProviderSelectionAligned() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let repository = try ScheduleRepository(store: DocumentFileStore(directoryURL: root))
        let first = Term(
            id: "term-a",
            name: "A",
            providerId: "synthetic-provider-a",
            firstWeekMonday: "2026-09-07",
            updatedAt: "2026-01-01T00:00:00Z"
        )
        let second = Term(
            id: "term-b",
            name: "B",
            providerId: "synthetic-provider-b",
            firstWeekMonday: "2027-09-06",
            updatedAt: "2027-01-01T00:00:00Z"
        )

        try repository.updateTerm(first, rule: nil)
        try repository.updateTerm(second, rule: nil)
        XCTAssertEqual(repository.document.settings.activeTermId, second.id)
        XCTAssertEqual(repository.document.settings.selectedAcademicProviderId, "synthetic-provider-b")

        try repository.deleteTerm(id: second.id)
        XCTAssertEqual(repository.document.settings.activeTermId, first.id)
        XCTAssertEqual(repository.document.settings.selectedAcademicProviderId, "synthetic-provider-a")
    }

    private func replacing(_ oldValue: String, with newValue: String) -> Data {
        String(decoding: provider, as: UTF8.self)
            .replacingOccurrences(of: oldValue, with: newValue)
            .data(using: .utf8)!
    }
}
