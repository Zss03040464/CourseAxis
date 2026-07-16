import Foundation

struct SyncEnvelope: Codable { var revision: Int64; var document: ScheduleDocument? }
struct SyncPutRequest: Codable { var baseRevision: Int64; var document: ScheduleDocument }
enum SyncClientResult: Equatable { case success(revision: Int64, message: String); case failure(String) }

protocol SyncTransport {
    func send(_ request: URLRequest) async throws -> (Data, HTTPURLResponse)
}
struct URLSessionSyncTransport: SyncTransport {
    func send(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
        return (data, http)
    }
}

final class SyncClient {
    private let repository: ScheduleRepository
    private let settings: SyncSettingsStore
    private let transport: SyncTransport
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    init(repository: ScheduleRepository, settings: SyncSettingsStore = SyncSettingsStore(), transport: SyncTransport = URLSessionSyncTransport()) {
        self.repository = repository
        self.settings = settings
        self.transport = transport
    }

    func pull() async -> SyncClientResult {
        do {
            guard var configuration = try settings.load() else { return .failure("请先保存有效的同步设置") }
            let envelope = try await get(configuration)
            if let remote = envelope.document { try repository.replace(MergeEngine.merge(repository.document, remote)) }
            configuration.revision = envelope.revision
            try settings.save(configuration)
            return .success(revision: envelope.revision, message: envelope.document == nil ? "服务器尚无数据" : "已拉取并合并")
        } catch { return .failure(error.localizedDescription) }
    }

    func push() async -> SyncClientResult {
        do {
            guard var configuration = try settings.load() else { return .failure("请先保存有效的同步设置") }
            var document = repository.document
            for attempt in 1...3 {
                let response = try await put(configuration, document: document)
                if response.1.statusCode == 409, attempt < 3 {
                    let envelope = try await get(configuration)
                    if let remote = envelope.document { document = MergeEngine.merge(document, remote); try repository.replace(document) }
                    configuration.revision = envelope.revision
                    try settings.save(configuration)
                    continue
                }
                guard (200...299).contains(response.1.statusCode) else { throw NSError(domain: "CourseAxis.Sync", code: response.1.statusCode, userInfo: [NSLocalizedDescriptionKey: String(data: response.0, encoding: .utf8) ?? "同步失败"]) }
                let envelope = migrated(try decoder.decode(SyncEnvelope.self, from: response.0))
                configuration.revision = envelope.revision
                try settings.save(configuration)
                return .success(revision: envelope.revision, message: "已上传")
            }
            return .failure("同步冲突重试失败")
        } catch { return .failure(error.localizedDescription) }
    }

    private func get(_ configuration: SyncConfiguration) async throws -> SyncEnvelope {
        var request = URLRequest(url: configuration.serverURL.appendingPathComponent("v1/document"))
        applyHeaders(configuration, to: &request)
        let (data, response) = try await transport.send(request)
        guard (200...299).contains(response.statusCode) else { throw NSError(domain: "CourseAxis.Sync", code: response.statusCode) }
        return migrated(try decoder.decode(SyncEnvelope.self, from: data))
    }

    private func put(_ configuration: SyncConfiguration, document: ScheduleDocument) async throws -> (Data, HTTPURLResponse) {
        var request = URLRequest(url: configuration.serverURL.appendingPathComponent("v1/document"))
        request.httpMethod = "PUT"
        request.httpBody = try encoder.encode(SyncPutRequest(baseRevision: configuration.revision, document: document))
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        applyHeaders(configuration, to: &request)
        return try await transport.send(request)
    }

    private func applyHeaders(_ configuration: SyncConfiguration, to request: inout URLRequest) {
        request.timeoutInterval = 15
        request.setValue("Bearer \(configuration.token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
    }

    private func migrated(_ envelope: SyncEnvelope) -> SyncEnvelope {
        guard let document = envelope.document else { return envelope }
        return SyncEnvelope(
            revision: envelope.revision,
            document: DocumentMigration.migrate(document).document
        )
    }
}
