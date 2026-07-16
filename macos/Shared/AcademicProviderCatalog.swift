import Foundation

enum AcademicProviderCatalogError: LocalizedError, Equatable {
    case invalidCatalog(String)
    case invalidProvider(String)
    case missingResource(String)

    var errorDescription: String? {
        switch self {
        case .invalidCatalog(let message), .invalidProvider(let message): message
        case .missingResource(let path): "缺少教务 Provider 资源：\(path)"
        }
    }
}

enum AcademicProviderCatalog {
    static func load(bundle: Bundle = .main) throws -> [AcademicProvider] {
        let catalogPath = "providers/catalog.json"
        guard let catalogURL = bundle.url(
            forResource: "catalog",
            withExtension: "json",
            subdirectory: "providers"
        ) else { throw AcademicProviderCatalogError.missingResource(catalogPath) }
        let catalogData = try Data(contentsOf: catalogURL)
        let ids = try catalogIDs(from: catalogData)
        var providerData: [String: Data] = [:]
        for id in ids {
            let path = "providers/\(id)/provider.json"
            guard let url = bundle.url(
                forResource: "provider",
                withExtension: "json",
                subdirectory: "providers/\(id)"
            ) else { throw AcademicProviderCatalogError.missingResource(path) }
            providerData[id] = try Data(contentsOf: url)
        }
        return try decode(catalogData: catalogData, providerData: providerData)
    }

    static func decode(
        catalogData: Data,
        providerData: [String: Data]
    ) throws -> [AcademicProvider] {
        let ids = try catalogIDs(from: catalogData)
        guard Set(providerData.keys) == Set(ids) else {
            throw AcademicProviderCatalogError.invalidCatalog("Provider Catalog 与配置文件不一致")
        }

        let decoder = JSONDecoder()
        let providers = try ids.map { expectedID -> AcademicProvider in
            guard let data = providerData[expectedID] else {
                throw AcademicProviderCatalogError.missingResource("providers/\(expectedID)/provider.json")
            }
            try validateProviderObject(data, expectedID: expectedID)
            let provider: AcademicProvider
            do {
                provider = try decoder.decode(AcademicProvider.self, from: data)
            } catch {
                throw AcademicProviderCatalogError.invalidProvider("Provider \(expectedID) JSON 无法解析")
            }
            try validate(provider, expectedID: expectedID)
            return provider
        }
        guard Set(providers.map { $0.sessionNamespace.lowercased() }).count == providers.count else {
            throw AcademicProviderCatalogError.invalidCatalog("Provider 会话命名空间不能重复")
        }
        return providers
    }

    private static func catalogIDs(from data: Data) throws -> [String] {
        let object: [String: Any]
        do {
            object = try requireObject(JSONSerialization.jsonObject(with: data), context: "Provider Catalog")
        } catch let error as AcademicProviderCatalogError {
            throw error
        } catch {
            throw AcademicProviderCatalogError.invalidCatalog("Provider Catalog JSON 无法解析")
        }
        guard Set(object.keys) == ["schemaVersion", "providers"], object["schemaVersion"] as? Int == 1 else {
            throw AcademicProviderCatalogError.invalidCatalog("Provider Catalog 版本或字段无效")
        }
        guard let ids = object["providers"] as? [String], !ids.isEmpty else {
            throw AcademicProviderCatalogError.invalidCatalog("Provider Catalog 不能为空")
        }
        guard Set(ids).count == ids.count else {
            throw AcademicProviderCatalogError.invalidCatalog("Provider Catalog 包含重复学校")
        }
        guard ids.allSatisfy(isSafeID) else {
            throw AcademicProviderCatalogError.invalidCatalog("Provider Catalog 包含不安全 ID")
        }
        return ids
    }

    private static func validateProviderObject(_ data: Data, expectedID: String) throws {
        let object: [String: Any]
        do {
            object = try requireObject(JSONSerialization.jsonObject(with: data), context: expectedID)
        } catch let error as AcademicProviderCatalogError {
            throw error
        } catch {
            throw AcademicProviderCatalogError.invalidProvider("Provider \(expectedID) JSON 无法解析")
        }
        let expectedKeys: Set<String> = [
            "schemaVersion", "id", "schoolName", "vendor", "adapterId", "sessionNamespace",
            "allowedOrigin", "pathPrefix", "loginPath", "homePath", "requiresThirdPartyCookies",
            "semesterCodes",
        ]
        guard Set(object.keys) == expectedKeys else {
            throw AcademicProviderCatalogError.invalidProvider("Provider \(expectedID) 字段不完整或包含未知字段")
        }
    }

    private static func validate(_ provider: AcademicProvider, expectedID: String) throws {
        func reject(_ message: String) throws -> Never {
            throw AcademicProviderCatalogError.invalidProvider("Provider \(expectedID) \(message)")
        }

        guard provider.schemaVersion == 1 else { try reject("版本无效") }
        guard provider.id == expectedID, isSafeID(provider.id) else { try reject("ID 无效") }
        guard !provider.schoolName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            try reject("学校名称为空")
        }
        guard provider.vendor.range(of: "^[A-Z][A-Z0-9_]*$", options: .regularExpression) != nil else {
            try reject("厂商标识无效")
        }
        guard isSafeID(provider.adapterId) else { try reject("Adapter ID 无效") }
        guard
            let identifier = UUID(uuidString: provider.sessionNamespace),
            identifier.uuidString.lowercased() == provider.sessionNamespace.lowercased()
        else { try reject("会话命名空间无效") }
        guard
            let prefixURL = URL(string: provider.allowedOrigin + provider.pathPrefix),
            let loginURL = URL(string: provider.allowedOrigin + provider.loginPath),
            let homeURL = URL(string: provider.allowedOrigin + provider.homePath),
            AcademicProviderPolicy.isAllowed(prefixURL, provider: provider),
            AcademicProviderPolicy.isAllowed(loginURL, provider: provider),
            AcademicProviderPolicy.isAllowed(homeURL, provider: provider)
        else { try reject("origin 或路径越界") }
        let allowedSemesters: Set<String> = ["first", "second", "third"]
        guard
            !provider.semesterCodes.isEmpty,
            Set(provider.semesterCodes.keys).isSubset(of: allowedSemesters),
            provider.semesterCodes.values.allSatisfy({ !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }),
            Set(provider.semesterCodes.values).count == provider.semesterCodes.count
        else { try reject("学期代码无效") }
        do {
            _ = try AcademicImportAdapterRegistry.adapter(for: provider)
        } catch {
            try reject("Adapter 不受支持")
        }
    }

    private static func requireObject(_ value: Any, context: String) throws -> [String: Any] {
        guard let object = value as? [String: Any] else {
            throw AcademicProviderCatalogError.invalidCatalog("\(context) 必须是 JSON 对象")
        }
        return object
    }

    private static func isSafeID(_ value: String) -> Bool {
        value.range(of: "^[a-z0-9]+(?:-[a-z0-9]+)*$", options: .regularExpression) != nil
    }
}
