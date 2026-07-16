import AppKit
import SwiftUI
import WebKit

enum AcademicPageState: Equatable {
    case loading
    case ready(String)
    case failed(String)
}

struct AcademicWebView: NSViewRepresentable {
    var provider: AcademicProvider
    var requestToken: Int
    var academicYear: String
    var semesterCode: String
    var onPageState: (AcademicPageState) -> Void
    var onResult: (String) -> Void
    var onError: (String) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    func makeNSView(context: Context) -> WKWebView {
        let controller = WKUserContentController()
        controller.add(context.coordinator, name: messageHandlerName)
        let configuration = WKWebViewConfiguration()
        configuration.userContentController = controller
        configuration.preferences.javaScriptCanOpenWindowsAutomatically = true

        do {
            let dataStore = try AcademicWebSession.dataStore(for: provider)
            configuration.websiteDataStore = dataStore
            let view = WKWebView(frame: .zero, configuration: configuration)
            view.navigationDelegate = context.coordinator
            view.uiDelegate = context.coordinator
            context.coordinator.webView = view
            onPageState(.loading)
            AcademicWebSession.prepare(provider: provider, dataStore: dataStore) { result in
                switch result {
                case .success: context.coordinator.loadHome()
                case .failure(let error):
                    context.coordinator.parent.onPageState(.failed(error.localizedDescription))
                    context.coordinator.parent.onError(error.localizedDescription)
                }
            }
            return view
        } catch {
            let view = WKWebView(frame: .zero, configuration: configuration)
            context.coordinator.webView = view
            onPageState(.failed(error.localizedDescription))
            onError(error.localizedDescription)
            return view
        }
    }

    func updateNSView(_ webView: WKWebView, context: Context) {
        context.coordinator.parent = self
        guard requestToken > 0, requestToken != context.coordinator.lastRequestToken else { return }
        context.coordinator.lastRequestToken = requestToken
        context.coordinator.collect(on: webView)
    }

    static func dismantleNSView(_ webView: WKWebView, coordinator: Coordinator) {
        webView.stopLoading()
        webView.configuration.userContentController.removeScriptMessageHandler(forName: messageHandlerName)
        coordinator.webView = nil
    }

    private static let messageHandlerName = "courseAxis"
    private var messageHandlerName: String { Self.messageHandlerName }

    @MainActor
    final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler {
        var parent: AcademicWebView
        weak var webView: WKWebView?
        var lastRequestToken = 0
        var collecting = false

        init(parent: AcademicWebView) {
            self.parent = parent
            super.init()
            NotificationCenter.default.addObserver(
                self,
                selector: #selector(willClearWebsiteData(_:)),
                name: AcademicWebSession.willClearData,
                object: nil
            )
            NotificationCenter.default.addObserver(
                self,
                selector: #selector(didClearWebsiteData(_:)),
                name: AcademicWebSession.didClearData,
                object: nil
            )
        }

        deinit { NotificationCenter.default.removeObserver(self) }

        func loadHome() {
            guard let url = URL(string: parent.provider.allowedOrigin + parent.provider.homePath) else {
                parent.onError("教务 Provider 首页地址无效")
                return
            }
            parent.onPageState(.loading)
            webView?.load(URLRequest(url: url))
        }

        @objc private func willClearWebsiteData(_ notification: Notification) {
            guard AcademicWebSession.notificationIncludesProvider(
                notification,
                providerID: parent.provider.id
            ) else { return }
            collecting = false
            webView?.stopLoading()
            webView?.loadHTMLString("", baseURL: nil)
        }

        @objc private func didClearWebsiteData(_ notification: Notification) {
            guard AcademicWebSession.notificationIncludesProvider(
                notification,
                providerID: parent.provider.id
            ) else { return }
            loadHome()
        }

        func webView(
            _ webView: WKWebView,
            didStartProvisionalNavigation navigation: WKNavigation!
        ) {
            if collecting {
                collecting = false
                parent.onError("页面已跳转，本次读取已取消")
            }
            parent.onPageState(.loading)
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            parent.onPageState(.ready(webView.title ?? "教务页面"))
        }

        func webView(
            _ webView: WKWebView,
            didFail navigation: WKNavigation!,
            withError error: Error
        ) {
            parent.onPageState(.failed(error.localizedDescription))
        }

        func webView(
            _ webView: WKWebView,
            didFailProvisionalNavigation navigation: WKNavigation!,
            withError error: Error
        ) {
            parent.onPageState(.failed(error.localizedDescription))
        }

        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
        ) {
            guard let url = navigationAction.request.url else {
                decisionHandler(.cancel)
                return
            }
            switch AcademicNavigationPolicy.destination(
                for: url,
                provider: parent.provider,
                hasTargetFrame: true
            ) {
            case .normalNavigation, .currentWebView:
                decisionHandler(.allow)
            case .externalBrowser:
                if navigationAction.targetFrame?.isMainFrame != false {
                    NSWorkspace.shared.open(url)
                }
                decisionHandler(.cancel)
            case .blocked:
                decisionHandler(.cancel)
            }
        }

        func webView(
            _ webView: WKWebView,
            createWebViewWith configuration: WKWebViewConfiguration,
            for navigationAction: WKNavigationAction,
            windowFeatures: WKWindowFeatures
        ) -> WKWebView? {
            guard let url = navigationAction.request.url else { return nil }
            switch AcademicNavigationPolicy.destination(
                for: url,
                provider: parent.provider,
                hasTargetFrame: navigationAction.targetFrame != nil
            ) {
            case .normalNavigation:
                return nil
            case .currentWebView:
                webView.load(navigationAction.request)
            case .externalBrowser:
                NSWorkspace.shared.open(url)
            case .blocked:
                break
            }
            return nil
        }

        func collect(on webView: WKWebView) {
            let provider = parent.provider
            guard
                let url = webView.url,
                AcademicNavigationPolicy.isEmbeddedURLAllowed(url, provider: provider)
            else {
                parent.onError("请先在\(provider.schoolName)教务页面内完成登录")
                return
            }
            do {
                let adapter = try AcademicImportAdapterRegistry.adapter(for: provider)
                let resource = adapter.scriptResource as NSString
                guard
                    let scriptURL = Bundle.main.url(
                        forResource: resource.lastPathComponent.replacingOccurrences(of: ".js", with: ""),
                        withExtension: "js",
                        subdirectory: "providers/\(resource.deletingLastPathComponent)"
                    ),
                    let script = try? String(contentsOf: scriptURL, encoding: .utf8)
                else {
                    parent.onError("缺少教务导入脚本")
                    return
                }
                collecting = true
                let invocation = try adapter.makeInvocation(
                    provider: provider,
                    academicYear: parent.academicYear,
                    semesterCode: parent.semesterCode,
                    script: script
                )
                webView.evaluateJavaScript(invocation) { [weak self] _, error in
                    guard let self, self.collecting, let error else { return }
                    self.collecting = false
                    self.parent.onError(error.localizedDescription)
                }
            } catch {
                parent.onError(error.localizedDescription)
            }
        }

        func userContentController(
            _ userContentController: WKUserContentController,
            didReceive message: WKScriptMessage
        ) {
            guard
                collecting,
                message.name == AcademicWebView.messageHandlerName,
                message.frameInfo.isMainFrame,
                let frameURL = message.frameInfo.request.url,
                AcademicNavigationPolicy.isEmbeddedURLAllowed(frameURL, provider: parent.provider),
                let pageURL = webView?.url,
                AcademicNavigationPolicy.isEmbeddedURLAllowed(pageURL, provider: parent.provider)
            else { return }
            collecting = false
            if let text = message.body as? String {
                parent.onResult(text)
            } else if JSONSerialization.isValidJSONObject(message.body),
                      let data = try? JSONSerialization.data(withJSONObject: message.body),
                      let text = String(data: data, encoding: .utf8) {
                parent.onResult(text)
            } else {
                parent.onError("教务返回格式无效")
            }
        }
    }
}
