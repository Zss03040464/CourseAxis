// swift-tools-version: 5.10
import PackageDescription
let package = Package(name:"CourseAxisCoreValidation",platforms:[.macOS(.v14)],products:[
    .library(name:"CourseAxisCore",targets:["CourseAxisCore"]),
    .executable(name:"courseaxis-core-validation",targets:["CourseAxisCoreValidation"])
],targets:[
    .target(
        name:"CourseAxisCore",
        path:"Shared",
        swiftSettings:[.unsafeFlags(["-enable-testing"])]
    ),
    .executableTarget(
        name:"CourseAxisCoreValidation",
        dependencies:["CourseAxisCore"],
        path:"CoreValidation"
    ),
    .testTarget(
        name:"CourseAxisCoreTests",
        dependencies:["CourseAxisCore"],
        path:"CoreTests"
    )
])
