import AppKit
import WidgetKit

@MainActor
final class ApplicationLifecycleCoordinator: NSObject, NSApplicationDelegate {
    private var observers: [NSObjectProtocol] = []
    private var lastWidgetReloadAt: Date?

    func applicationDidFinishLaunching(_ notification: Notification) {
        observeWindowLifecycle()
        reloadWidgetTimeline(for: .applicationLaunch)
        DispatchQueue.main.async { [weak self] in
            self?.refreshPresence()
        }
    }

    func applicationDidBecomeActive(_ notification: Notification) {
        reloadWidgetTimeline(for: .applicationActivation)
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        false
    }

    func applicationShouldHandleReopen(
        _ sender: NSApplication,
        hasVisibleWindows flag: Bool
    ) -> Bool {
        reloadWidgetTimeline(for: .applicationReopen)
        restoreForegroundApplication()
        return true
    }

    func application(_ application: NSApplication, open urls: [URL]) {
        reloadWidgetTimeline(for: .applicationReopen)
        restoreForegroundApplication()
        if urls.contains(where:{AppNavigationRoute.parse($0) == .week}) {
            DispatchQueue.main.async {
                NotificationCenter.default.post(name:.courseAxisOpenWeek,object:nil)
            }
        }
    }

    private func reloadWidgetTimeline(for trigger: WidgetTimelineRefreshTrigger) {
        let now = Date()
        guard WidgetTimelineRefreshPolicy.shouldReload(
            for: trigger,
            lastReloadAt: lastWidgetReloadAt,
            now: now
        ) else { return }
        WidgetCenter.shared.reloadTimelines(ofKind: "CourseAxisWidget")
        lastWidgetReloadAt = now
    }

    private func observeWindowLifecycle() {
        let center = NotificationCenter.default
        let foregroundNotifications: [Notification.Name] = [
            NSWindow.didBecomeKeyNotification,
            NSWindow.didDeminiaturizeNotification
        ]

        for name in foregroundNotifications {
            observers.append(center.addObserver(forName: name, object: nil, queue: .main) { [weak self] _ in
                Task { @MainActor in self?.refreshPresence() }
            })
        }

        observers.append(center.addObserver(forName: NSWindow.willCloseNotification, object: nil, queue: .main) { [weak self] notification in
            let closingWindow = notification.object as? NSWindow
            Task { @MainActor in self?.refreshPresence(excluding: closingWindow) }
        })

        observers.append(center.addObserver(forName: NSWindow.didMiniaturizeNotification, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.refreshPresence() }
        })
    }

    private func refreshPresence(excluding closingWindow: NSWindow? = nil) {
        let visibleUserWindowCount = NSApp.windows.filter { window in
            guard window !== closingWindow else { return false }
            return isUserWindowVisible(window)
        }.count
        switch ApplicationPresencePolicy.desiredPresence(visibleUserWindowCount: visibleUserWindowCount) {
        case .foreground:
            if NSApp.activationPolicy() != .regular {
                NSApp.setActivationPolicy(.regular)
            }
        case .silentBackground:
            if NSApp.activationPolicy() != .accessory {
                NSApp.setActivationPolicy(.accessory)
            }
        }
    }

    private func restoreForegroundApplication() {
        NSApp.setActivationPolicy(.regular)
        NSApp.activate(ignoringOtherApps: true)
        guard let window = NSApp.windows.first(where: isUserWindow) else { return }
        if window.isMiniaturized {
            window.deminiaturize(nil)
        }
        window.makeKeyAndOrderFront(nil)
    }

    private func isUserWindowVisible(_ window: NSWindow) -> Bool {
        isUserWindow(window) && (window.isVisible || window.isMiniaturized)
    }

    private func isUserWindow(_ window: NSWindow) -> Bool {
        !(window is NSPanel) && window.styleMask.contains(.titled)
    }

    deinit {
        for observer in observers {
            NotificationCenter.default.removeObserver(observer)
        }
    }
}
