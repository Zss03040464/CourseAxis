import SwiftUI

@main
struct CourseAxisApp: App {
    @NSApplicationDelegateAdaptor(ApplicationLifecycleCoordinator.self)
    private var lifecycleCoordinator
    @StateObject private var model = AppModel()
    var body: some Scene {
        WindowGroup { RootView().environmentObject(model).frame(minWidth: 980, minHeight: 680) }
        .defaultSize(width: 1180, height: 760)
        Settings { SettingsView().environmentObject(model).frame(width: 620, height: 520) }
    }
}
