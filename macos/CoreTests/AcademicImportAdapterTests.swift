import XCTest
@testable import CourseAxisCore

final class AcademicImportAdapterTests: XCTestCase {
    private func provider(_ id:String,_ school:String,_ origin:String,_ namespace:String)->AcademicProvider {
        AcademicProvider(schemaVersion:1,id:id,schoolName:school,vendor:"ZHENGFANG",allowedOrigin:origin,pathPrefix:"/jwglxt/",loginPath:"/jwglxt/xtgl/login_slogin.html",homePath:"/jwglxt/xtgl/index_initMenu.html",requiresThirdPartyCookies:false,semesterCodes:["first":"3","second":"12"],adapterId:"zhengfang-v1",sessionNamespace:namespace)
    }

    func testRegistryScopesStableIdentityByProvider() throws {
        let providerA=provider("synthetic-school-a","合成大学 A","https://jw.synthetic-a.example","11111111-1111-4111-8111-111111111111")
        let providerB=provider("synthetic-school-b","合成大学 B","https://jw.synthetic-b.example","22222222-2222-4222-8222-222222222222")
        let raw=#"{"ok":true,"term":{"xnm":"2026","xqm":"3"},"sources":{"courses":{"ok":true,"count":1},"exams":{"ok":true,"count":1},"sectionTimes":{"ok":true,"count":2}},"sectionTimes":[{"section":1,"start":"08:00","end":"08:40"},{"section":2,"start":"08:45","end":"09:25"}],"schedule":{"kbList":[{"jxb_id":"CLASS-A","kcmc":"合成课程","xm":"合成教师","xqj":"2","jc":"1-2节","zcd":"1-16周","cdmc":"合成楼 101"}],"sjkList":[]},"exams":{"items":[{"ks_id":"EXAM-A","kcmc":"合成课程","ksmc":"期末考试","kssj":"2027-01-05 09:00-11:00","cdmc":"合成楼 201"}]}}"#
        func options(_ provider:AcademicProvider)->AcademicImportOptions { AcademicImportOptions(provider:provider,academicYear:"2026",semesterCode:"3",termName:"2026-2027 第一学期",firstWeekMonday:"2026-09-07",totalWeeks:20,campus:"主校区") }
        let adapter=try AcademicImportAdapterRegistry.adapter(for:providerA)
        let first=try adapter.parse(raw,options:options(providerA))
        let second=try AcademicImportAdapterRegistry.adapter(for:providerB).parse(raw,options:options(providerB))

        XCTAssertEqual(adapter.id,"zhengfang-v1")
        XCTAssertEqual(adapter.scriptResource,"adapters/zhengfang-v1/import.js")
        XCTAssertEqual(first.term.providerId,providerA.id)
        XCTAssertEqual(second.term.providerId,providerB.id)
        XCTAssertNotEqual(first.term.id,second.term.id)
        XCTAssertNotEqual(first.events.first{$0.kind == .COURSE}?.id,second.events.first{$0.kind == .COURSE}?.id)
        XCTAssertNotEqual(first.events.first{$0.kind == .EXAM}?.id,second.events.first{$0.kind == .EXAM}?.id)
        XCTAssertEqual(first.events.first{$0.kind == .COURSE}?.id,"course-7a087308e6a00825")
        XCTAssertEqual(first.events.first{$0.kind == .EXAM}?.id,"exam-6ecab76fecd79cbc")
    }

    func testInvocationUsesSelectedProviderBoundary() throws {
        let selected=provider("synthetic-school-a","合成大学 A","https://jw.synthetic-a.example","11111111-1111-4111-8111-111111111111")
        let invocation=try AcademicImportAdapterRegistry.adapter(for:selected).makeInvocation(provider:selected,academicYear:"2026",semesterCode:"3",script:"(async function(){ return 1; })();")
        XCTAssertTrue(invocation.contains(#""allowedOrigin":"https://jw.synthetic-a.example""#))
        XCTAssertTrue(invocation.contains(#""pathPrefix":"/jwglxt/""#))
        XCTAssertTrue(invocation.contains(#""xnm":"2026""#))
        XCTAssertTrue(invocation.contains(#""xqm":"3""#))
        XCTAssertTrue(invocation.hasSuffix("void 0;"))
    }

    func testRegistryRejectsUnknownAdapterWithoutFallback() {
        var selected=provider("synthetic-school-a","合成大学 A","https://jw.synthetic-a.example","11111111-1111-4111-8111-111111111111")
        selected.adapterId="unknown-v9"
        XCTAssertThrowsError(try AcademicImportAdapterRegistry.adapter(for:selected)){error in XCTAssertTrue(error.localizedDescription.contains("unknown-v9"))}
    }
}
