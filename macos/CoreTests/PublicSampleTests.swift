import Foundation
import XCTest
@testable import CourseAxisCore

final class PublicSampleTests: XCTestCase {
    func testSyntheticScheduleSampleDecodesAndPassesDomainValidation() throws {
        let repositoryRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
        let sampleURL = repositoryRoot
            .appendingPathComponent("samples")
            .appendingPathComponent("schedule.document.json")

        let document = try JSONDecoder().decode(
            ScheduleDocument.self,
            from: Data(contentsOf: sampleURL)
        )

        try ScheduleDocumentValidator.validate(document)
        XCTAssertEqual(document.documentId, "synthetic-courseaxis-demo")
        XCTAssertEqual(document.terms.map(\.school), ["示例大学"])
        XCTAssertTrue(document.events.allSatisfy { $0.title.hasPrefix("合成") })
    }
}
