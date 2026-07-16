import Foundation
import Security

protocol KeychainSecretStoring {
    func read(service: String, account: String) throws -> String?
    func save(_ value: String, service: String, account: String) throws
    func delete(service: String, account: String) throws
}

enum KeychainSecretStoreError: LocalizedError {
    case operationFailed(OSStatus)
    case invalidStoredValue

    var errorDescription: String? {
        switch self {
        case .operationFailed(let status):
            let description = SecCopyErrorMessageString(status, nil) as String? ?? "OSStatus \(status)"
            return "无法访问系统钥匙串：\(description)"
        case .invalidStoredValue:
            return "系统钥匙串中的同步令牌无效，请重新配置"
        }
    }
}

struct KeychainSecretStore: KeychainSecretStoring {
    func read(service: String, account: String) throws -> String? {
        var result: CFTypeRef?
        let status = SecItemCopyMatching(
            baseQuery(service: service, account: account).merging([
                kSecReturnData as String: true,
                kSecMatchLimit as String: kSecMatchLimitOne,
            ]) { _, new in new } as CFDictionary,
            &result
        )
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess else { throw KeychainSecretStoreError.operationFailed(status) }
        guard let data = result as? Data, let value = String(data: data, encoding: .utf8) else {
            try? delete(service: service, account: account)
            throw KeychainSecretStoreError.invalidStoredValue
        }
        return value
    }

    func save(_ value: String, service: String, account: String) throws {
        let data = Data(value.utf8)
        let query = baseQuery(service: service, account: account)
        let updateStatus = SecItemUpdate(
            query as CFDictionary,
            [kSecValueData as String: data] as CFDictionary
        )
        if updateStatus == errSecSuccess { return }
        guard updateStatus == errSecItemNotFound else {
            throw KeychainSecretStoreError.operationFailed(updateStatus)
        }
        let addStatus = SecItemAdd(
            query.merging([
                kSecValueData as String: data,
                kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            ]) { _, new in new } as CFDictionary,
            nil
        )
        guard addStatus == errSecSuccess else {
            throw KeychainSecretStoreError.operationFailed(addStatus)
        }
    }

    func delete(service: String, account: String) throws {
        let status = SecItemDelete(baseQuery(service: service, account: account) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainSecretStoreError.operationFailed(status)
        }
    }

    private func baseQuery(service: String, account: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }
}
