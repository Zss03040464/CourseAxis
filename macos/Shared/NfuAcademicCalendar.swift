import Foundation

struct NfuAcademicTermCalendar: Equatable {
    var academicYear: String
    var semesterCode: String
    var firstWeekMonday: String
    var totalWeeks: Int
    var sourceURL: String
}
enum NfuAcademicCalendar {
    private static let verified: [NfuAcademicTermCalendar] = [
        NfuAcademicTermCalendar(
            academicYear: "2025",
            semesterCode: "12",
            firstWeekMonday: "2026-03-02",
            totalWeeks: 20,
            sourceURL: "https://staff.nfu.edu.cn/attachment/files/2025/11/10/file_1762753450_knF9fNgz.pdf"
        )
    ]

    static func lookup(academicYear: String, semesterCode: String) -> NfuAcademicTermCalendar? {
        verified.first { $0.academicYear == academicYear && $0.semesterCode == semesterCode }
    }
}
