#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "[0/11] Supported toolchains and supply-chain pins"
python3 scripts/validate-providers.py
python3 scripts/check-public-hygiene.py --mode source
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
python3 scripts/validate-workflows.py
test -x scripts/export-public-candidate.py
python3 scripts/export-public-candidate.py --help >/dev/null
test -s site/package-lock.json
test "$(cat site/.node-version)" = "26.0.0"
(
  cd site
  npm ci --ignore-scripts
  npm test
  npm run lint
  npm run build
  test -s dist/LICENSE.txt
  test -s dist/THIRD_PARTY_NOTICES.txt
  cd ..
  python3 scripts/validate-site-build.py
  cd site
  npm audit --audit-level=high
)
test -x scripts/install-xcodegen.sh
bash -n scripts/install-xcodegen.sh
! grep -Fq 'rm -rf' scripts/install-xcodegen.sh
ruby -e 'require "yaml"; Dir[".github/**/*.yml"].sort.each { |path| YAML.load_file(path); puts "#{path}: OK" }'
for release_script in scripts/build-android-release.sh scripts/build-macos-release.sh scripts/generate-release-metadata.sh; do
  test -x "$release_script"
  bash -n "$release_script"
done
! test -e scripts/build-macos-clt.sh
! test -e macos/Config/CLTAppInfo.plist
grep -q 'versionCode = 13' android/app/build.gradle.kts
grep -q 'versionName = "1.5.0"' android/app/build.gradle.kts
test "$(grep -c 'MARKETING_VERSION: 1.5.0' macos/project.yml)" = "2"
test "$(grep -c 'CURRENT_PROJECT_VERSION: 10' macos/project.yml)" = "2"
grep -q 'ENABLE_HARDENED_RUNTIME: YES' macos/project.yml
grep -Fq ': "${COURSEAXIS_KEYSTORE_FILE:?' scripts/build-android-release.sh
grep -Fq ': "${COURSEAXIS_MACOS_SIGNING_IDENTITY:?' scripts/build-macos-release.sh
grep -Fq 'notarytool submit' scripts/build-macos-release.sh
PRIVATE_USER_PATH='/'"Users/"
! grep -Eq "security find|${PRIVATE_USER_PATH}|\\\$HOME/Library/Developer|1\\.4\\." scripts/build-android-release.sh scripts/build-macos-release.sh scripts/generate-release-metadata.sh
test -s docs/BUILD_AND_SIGN.md
test -s docs/RELEASE.md
grep -q 'Actions 不保存' docs/RELEASE.md
grep -q '^go 1\.26\.5$' server/go.mod
grep -Fq 'FROM golang:1.26.5-alpine3.24@sha256:0178a641fbb4858c5f1b48e34bdaabe0350a330a1b1149aabd498d0699ff5fb2 AS build' server/Dockerfile
grep -Fq 'FROM alpine:3.24.1@sha256:28bd5fe8b56d1bd048e5babf5b10710ebe0bae67db86916198a6eec434943f8b' server/Dockerfile
grep -q '^distributionSha256Sum=20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78$' android/gradle/wrapper/gradle-wrapper.properties
test -s android/gradle/verification-metadata.xml
test -s android/app/gradle.lockfile
grep -q 'dependencyLocking' android/build.gradle.kts
! grep -Eq '<trusted-artifacts|<trust ' android/gradle/verification-metadata.xml
! grep -REq '(version[[:space:]]*=[[:space:]]*"[^"]*\+|:[^"[:space:]]*\+"|latest\.(release|integration))' android --include='*.gradle.kts' --include='*.toml'
python3 - <<'PY'
from pathlib import Path
from xml.etree import ElementTree

metadata = Path("android/gradle/verification-metadata.xml")
root = ElementTree.parse(metadata).getroot()
namespace = {"v": "https://schema.gradle.org/dependency-verification"}
components = root.findall(".//v:component", namespace)
checksums = root.findall(".//v:sha256", namespace)
if not components or not checksums:
    raise SystemExit("Gradle dependency verification metadata is empty")
print(f"Gradle verification metadata: {len(components)} components, {len(checksums)} checksums")
PY

echo "[1/11] Go server tests and container build"
GO_BUILD_IMAGE='golang:1.26.5-alpine3.24@sha256:0178a641fbb4858c5f1b48e34bdaabe0350a330a1b1149aabd498d0699ff5fb2'
if command -v go >/dev/null 2>&1; then
  (cd server && CGO_ENABLED=0 go test -mod=readonly ./... && CGO_ENABLED=0 go vet -mod=readonly ./...)
elif command -v docker >/dev/null 2>&1; then
  docker run --rm --network=none -e CGO_ENABLED=0 -v "$ROOT/server:/src:ro" -w /src "$GO_BUILD_IMAGE" sh -c 'go test -mod=readonly ./... && go vet -mod=readonly ./...'
else
  echo "ERROR: Go 1.26.5+ or Docker is required to validate the sync server." >&2
  exit 1
fi
if command -v docker >/dev/null 2>&1; then
  docker build --pull=false -t courseaxis-sync:validation server
else
  echo "SKIP: Docker image build requires Docker; native Go tests passed."
fi

echo "[2/11] Swift XCTest suite"
(cd macos && swift test)

echo "[3/11] Swift core validation"
(cd macos && swift run courseaxis-core-validation)

echo "[4/11] macOS app type check"
(cd macos && swiftc -typecheck -parse-as-library Shared/*.swift CourseAxisApp/*.swift)

echo "[5/11] Widget extension type check"
(cd macos && swiftc -typecheck -parse-as-library Shared/*.swift CourseAxisWidget/*.swift)

echo "[6/11] Canonical provider adapter syntax and platform wiring"
node --check providers/adapters/zhengfang-v1/import.js
grep -q 'CourseAxisBridge.postMessage' providers/adapters/zhengfang-v1/import.js
! grep -q 'CourseAxisBridge.onResult' providers/adapters/zhengfang-v1/import.js
! grep -q 'COURSEAXIS_RESULT' providers/adapters/zhengfang-v1/import.js
grep -q '"../../providers"' android/app/build.gradle.kts
grep -q 'context.assets.open(adapter.scriptResource)' android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/ImportScreen.kt
grep -q 'path: ../providers' macos/project.yml
test -s android/app/src/main/java/dev/shusenzheng64/courseaxis/importer/AcademicImportAdapter.kt
test -s android/app/src/main/java/dev/shusenzheng64/courseaxis/importer/ZhengfangImportAdapter.kt
test -s macos/Shared/AcademicImportAdapter.swift
test -s macos/Shared/ZhengfangImportAdapter.swift
grep -q 'AcademicImportAdapterRegistry.adapter(options.provider)' android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/ImportScreen.kt
grep -q 'let resource = adapter.scriptResource' macos/CourseAxisApp/AcademicWebView.swift
! grep -REq 'NfuImportOptions|NfuImportBundle|NfuImporter|NfuSourceStatus|NfuScriptInvocation' android/app/src/main android/app/src/test macos README.md
! grep -RFq 'macos/Resources/nfu_import.js' README.md docs/ARCHITECTURE.md docs/IMPORT_PROTOCOL.md
! grep -RFq 'NfuImporter' README.md docs/ARCHITECTURE.md docs/IMPORT_PROTOCOL.md

echo "[7/11] Apple configuration"
plutil -lint macos/Config/AppInfo.plist macos/Config/WidgetInfo.plist macos/Config/App.entitlements macos/Config/Widget.entitlements macos/Resources/container-migration.plist
test "$(plutil -extract LSMultipleInstancesProhibited raw macos/Config/AppInfo.plist)" = "true"
test "$(plutil -extract NSExtension.NSExtensionPointIdentifier raw macos/Config/WidgetInfo.plist)" = "com.apple.widgetkit-extension"
plutil -convert json -o - macos/Config/App.entitlements | jq -e '.["com.apple.security.app-sandbox"] == true' >/dev/null
plutil -convert json -o - macos/Config/App.entitlements | jq -e '.["com.apple.security.application-groups"] == ["group.dev.shusenzheng64.courseaxis"]' >/dev/null
plutil -convert json -o - macos/Config/Widget.entitlements | jq -e '.["com.apple.security.app-sandbox"] == true' >/dev/null
plutil -convert json -o - macos/Config/Widget.entitlements | jq -e '.["com.apple.security.application-groups"] == ["group.dev.shusenzheng64.courseaxis"]' >/dev/null
! grep -R -q 'com.apple.security.temporary-exception.files.home-relative' macos/Config macos/project.yml
grep -q 'DocumentFileStore.appStore()' macos/CourseAxisApp/AppModel.swift
grep -q 'DocumentFileStore()' macos/CourseAxisWidget/CourseAxisWidget.swift
grep -q 'KeychainSecretStore()' macos/Shared/SyncSettings.swift
! grep -q 'defaults.set(configuration.token' macos/Shared/SyncSettings.swift
grep -q 'WKWebsiteDataStore(forIdentifier:' macos/Shared/AcademicWebSession.swift
grep -q 'WKWebsiteDataStore.allWebsiteDataTypes()' macos/Shared/AcademicWebSession.swift
grep -q 'AcademicProviderCatalog.load()' macos/CourseAxisApp/AppModel.swift
grep -q 'AcademicImportView()' macos/CourseAxisApp/RootView.swift
! test -e macos/CourseAxisApp/NfuWebView.swift
! test -e macos/CourseAxisApp/NfuImportView.swift
grep -q 'func clearAllLocalData() throws' macos/Shared/DocumentFileStore.swift
grep -q 'Button("仅清账号"' macos/CourseAxisApp/SettingsView.swift
grep -q 'Button("清除全部本机数据"' macos/CourseAxisApp/SettingsView.swift
grep -q 'Button("查看隐私说明"' macos/CourseAxisApp/SettingsView.swift
grep -q 'AppIntentConfiguration' macos/CourseAxisWidget/CourseAxisWidget.swift
grep -q 'ExamFocusPolicy.resolve(model.document)' macos/CourseAxisApp/WeekTimelineView.swift
test "$(grep -c 'MARKETING_VERSION: 1.5.0' macos/project.yml)" = "2"
test "$(grep -c 'CURRENT_PROJECT_VERSION: 10' macos/project.yml)" = "2"
test -s macos/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png

echo "[8/11] XcodeGen configuration syntax"
ruby -e 'require "yaml"; YAML.load_file(ARGV.fetch(0)); puts "project.yml: OK"' macos/project.yml
grep -q '^  CourseAxis:' macos/project.yml
grep -q '^  CourseAxisWidget:' macos/project.yml

echo "[9/11] JSON syntax and public privacy contracts"
test -s docs/PROVIDER_DEVELOPMENT.md
grep -q 'Provider 开发与学校贡献' docs/PROVIDER_DEVELOPMENT.md
grep -q '当前只验证广州南方学院' docs/PROVIDER_DEVELOPMENT.md
grep -q '禁止提交真实' docs/PROVIDER_DEVELOPMENT.md
grep -q '`allowedOrigin`' docs/PROVIDER_DEVELOPMENT.md
grep -q '会话隔离' docs/PROVIDER_DEVELOPMENT.md
grep -q '禁止运行时下载' docs/PROVIDER_DEVELOPMENT.md
grep -q '未知 Adapter' docs/PROVIDER_DEVELOPMENT.md
grep -q 'docs/PROVIDER_DEVELOPMENT.md' README.md
python3 - <<'PY'
import json
from pathlib import Path
for path in [Path("schema/schedule.schema.json"), *sorted(Path("samples").glob("*.json"))]:
    with path.open(encoding="utf-8") as handle:
        json.load(handle)
    print(f"{path}: OK")
PY
test -s samples/README.md
grep -q '完全合成' samples/README.md
! test -e samples/zhengfang_schedule.json
! test -e samples/zhengfang_exams.json
grep -q 'synthetic-courseaxis-demo' samples/schedule.document.json
for document in README.md PRIVACY.md SECURITY.md THREAT_MODEL.md; do
  test -s "$document"
done
for document in CONTRIBUTING.md CODE_OF_CONDUCT.md SUPPORT.md THIRD_PARTY_NOTICES.md CHANGELOG.md; do
  test -s "$document"
done
for license in licenses/APACHE-2.0.txt licenses/EPL-1.0.txt licenses/JSON.txt; do
  test -s "$license"
done
test -s .github/ISSUE_TEMPLATE/config.yml
test -s .github/ISSUE_TEMPLATE/bug_report.yml
test -s .github/ISSUE_TEMPLATE/provider_request.yml
test -s .github/pull_request_template.md
test -s .github/workflows/ci.yml
test -s .github/workflows/codeql.yml
test -s .github/workflows/dependency-review.yml
test -s .github/dependabot.yml
test -s .github/release.yml
grep -q 'Contributor Covenant 2.1' CODE_OF_CONDUCT.md
grep -q 'GitHub.*Security' CODE_OF_CONDUCT.md
grep -q 'Apache License 2.0' THIRD_PARTY_NOTICES.md
grep -q 'Eclipse Public License 1.0' THIRD_PARTY_NOTICES.md
grep -q 'Public Domain' THIRD_PARTY_NOTICES.md
grep -q 'CodeQL Terms and Conditions' THIRD_PARTY_NOTICES.md
grep -q '1.5.0' CHANGELOG.md
grep -q 'CONTRIBUTING.md' README.md
grep -q '禁止提交真实' .github/ISSUE_TEMPLATE/bug_report.yml
grep -q '禁止提交真实' .github/ISSUE_TEMPLATE/provider_request.yml
! grep -Eq '考试中心|分别读取课程|CourseAxisBridge\.onResult|31 项 XCTest|本次构建与验证报告' README.md
grep -q '不隶属于' README.md
grep -q 'Android Keystore' PRIVACY.md
grep -q 'macOS Keychain' PRIVACY.md
grep -q 'Cookie' PRIVACY.md
grep -q '验证码' PRIVACY.md
grep -q '课表' PRIVACY.md
grep -q '考试' PRIVACY.md
grep -q 'Widget' PRIVACY.md
grep -q '自托管同步' PRIVACY.md
grep -q '清除全部本机数据' PRIVACY.md
grep -q '公开 issue' SECURITY.md
grep -q '真实账号' SECURITY.md
grep -q '学校' THREAT_MODEL.md
grep -q '自托管同步' THREAT_MODEL.md
python3 - <<'PY'
import re
from pathlib import Path

documents = [
    Path("README.md"),
    Path("PRIVACY.md"),
    Path("SECURITY.md"),
    Path("THREAT_MODEL.md"),
    Path("CONTRIBUTING.md"),
    Path("CODE_OF_CONDUCT.md"),
    Path("SUPPORT.md"),
    Path("THIRD_PARTY_NOTICES.md"),
    Path("CHANGELOG.md"),
    Path("docs/ARCHITECTURE.md"),
    Path("docs/IMPORT_PROTOCOL.md"),
    Path("docs/PROVIDER_DEVELOPMENT.md"),
    Path("docs/BUILD_AND_SIGN.md"),
    Path("docs/RELEASE.md"),
    Path("PROJECT.md"),
    Path("knowledge/HOME.md"),
]
for document in documents:
    text = document.read_text(encoding="utf-8")
    if "/Users/" in text:
        raise SystemExit(f"absolute user path in public document: {document}")
    for target in re.findall(r"\[[^]]+\]\(([^)]+)\)", text):
        if target.startswith(("http://", "https://", "mailto:", "#")):
            continue
        relative = target.split("#", 1)[0]
        if relative and not (document.parent / relative).resolve().exists():
            raise SystemExit(f"broken local link in {document}: {target}")
print("Public documentation links: OK")
PY

echo "[10/11] Android tests, lint and debug build"
ANDROID_JAVA_HOME="${COURSEAXIS_JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
ANDROID_SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
if [[ -x android/gradlew && -d "$ANDROID_JAVA_HOME" && -d "$ANDROID_SDK/platforms/android-36" ]]; then
  grep -q 'distributionUrl=https\\://services.gradle.org/distributions/gradle-8.13-bin.zip' android/gradle/wrapper/gradle-wrapper.properties
  grep -q 'versionCode = 13' android/app/build.gradle.kts
  grep -q 'versionName = "1.5.0"' android/app/build.gradle.kts
  grep -q 'android:allowBackup="false"' android/app/src/main/AndroidManifest.xml
  grep -q '<exclude domain="file" path="\."' android/app/src/main/res/xml/backup_rules.xml
  grep -q '<exclude domain="sharedpref" path="\."' android/app/src/main/res/xml/backup_rules.xml
  test "$(grep -c '<exclude domain="file" path="\."' android/app/src/main/res/xml/data_extraction_rules.xml)" = "2"
  test "$(grep -c '<exclude domain="sharedpref" path="\."' android/app/src/main/res/xml/data_extraction_rules.xml)" = "2"
  ! grep -R -q '<include domain=' android/app/src/main/res/xml/backup_rules.xml android/app/src/main/res/xml/data_extraction_rules.xml
  ! grep -q 'var password by rememberSaveable' android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/ImportScreen.kt
  grep -q 'android:usesCleartextTraffic="false"' android/app/src/main/AndroidManifest.xml
  ! grep -R -q 'addJavascriptInterface\|@android.webkit.JavascriptInterface' android/app/src/main
  grep -q 'androidx.webkit:webkit:1.16.0' android/app/build.gradle.kts
  grep -q 'AcademicWebDataCleaner.clear(webView, sessionBinding)' android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/ImportScreen.kt
  grep -q 'storage.deleteAllData()' android/app/src/main/java/dev/shusenzheng64/courseaxis/importer/AcademicWebDataCleaner.kt
  grep -q 'WebStorageCompat.deleteBrowsingData(storage)' android/app/src/main/java/dev/shusenzheng64/courseaxis/importer/AcademicWebDataCleaner.kt
  grep -q 'WebViewCompat.setProfile(webView, plan.profileName)' android/app/src/main/java/dev/shusenzheng64/courseaxis/importer/AcademicWebSession.kt
  grep -q 'allProfileNames.filter { it.startsWith(COURSEAXIS_PROFILE_PREFIX) }' android/app/src/main/java/dev/shusenzheng64/courseaxis/importer/AcademicWebDataCleaner.kt
  grep -q 'CredentialProviderRegistry.providerIds(context)' android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/SettingsScreen.kt
  grep -Fq 'SecureCredentialStore(context, providerId = selected.id)' android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/ImportScreen.kt
  grep -q 'label = "学校教务"' android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/ImportScreen.kt
  grep -q 'text = "仅清账号"' android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/SettingsScreen.kt
  grep -q 'text = "清除全部本机数据"' android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/SettingsScreen.kt
  grep -q 'text = "查看隐私说明"' android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/SettingsScreen.kt
  grep -q 'syncSettings = SecureSyncSettings(this)' android/app/src/main/java/dev/shusenzheng64/courseaxis/CourseAxisApplication.kt
  grep -q 'KeyProperties.BLOCK_MODE_GCM' android/app/src/main/java/dev/shusenzheng64/courseaxis/data/SecureSyncSettings.kt
  ! grep -R -q '\.putString("token"' android/app/src/main
  grep -Fq 'ExamFocusPolicy.resolve(document, term.id)' android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/ScheduleScreens.kt
  ! grep -Fq 'onActivateTerm(focus.termId)' android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/ScheduleScreens.kt
  grep -q 'WeekTermSelector(' android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/ScheduleScreens.kt
  grep -q 'WeekTermSwitchPolicy.initialWeek' android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/ScheduleScreens.kt
  grep -q 'contentDescription = "切换学期"' android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/ScheduleScreens.kt
  grep -q 'WeekCompactControls(' android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/ScheduleScreens.kt
  grep -q 'WeekTimelineDensityPolicy.showEndTime' android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/ScheduleScreens.kt
  grep -q 'WeekTimelineDensityPolicy.courseCard' android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/ScheduleScreens.kt
  grep -q 'WeekEventMode.COURSE to "全部课程"' android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/ScheduleScreens.kt
  grep -q 'ImportWorkspacePolicy.primaryActionLabel' android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/ImportScreen.kt
  ! grep -Eq '个人课表|考试信息|学期代码|账号密码仅以' android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/ImportScreen.kt
  grep -q 'updateTermMetadata(value)' android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/SettingsScreen.kt
  grep -q '生成并保存作息' android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/SettingsScreen.kt
  grep -q '自托管同步（高级）' android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/SettingsScreen.kt
  ! grep -Eq 'sectionLines|parseSectionLines|学期代码' android/app/src/main/java/dev/shusenzheng64/courseaxis/ui/SettingsScreen.kt
  (cd android && JAVA_HOME="$ANDROID_JAVA_HOME" ANDROID_HOME="$ANDROID_SDK" ./gradlew --dependency-verification=strict :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug :app:assembleDebug :app:processReleaseMainManifest --console=plain)
  unzip -p android/app/build/outputs/apk/debug/app-debug.apk assets/adapters/zhengfang-v1/import.js | cmp - providers/adapters/zhengfang-v1/import.js
  unzip -p android/app/build/outputs/apk/debug/app-debug.apk assets/catalog.json | cmp - providers/catalog.json
  unzip -p android/app/build/outputs/apk/debug/app-debug.apk assets/nfu-zhengfang/provider.json | cmp - providers/nfu-zhengfang/provider.json
else
  echo "SKIP: JDK 17 and/or Android SDK 36 is unavailable."
fi

echo "[11/11] Optional native Xcode build"
if command -v xcodegen >/dev/null 2>&1 && xcodebuild -version >/dev/null 2>&1; then
  (cd macos && xcodegen generate && xcodebuild -project CourseAxis.xcodeproj -scheme CourseAxis -configuration Debug -derivedDataPath DerivedData CODE_SIGNING_ALLOWED=NO build)
  cmp providers/adapters/zhengfang-v1/import.js macos/DerivedData/Build/Products/Debug/CourseAxis.app/Contents/Resources/providers/adapters/zhengfang-v1/import.js
  cmp providers/catalog.json macos/DerivedData/Build/Products/Debug/CourseAxis.app/Contents/Resources/providers/catalog.json
  cmp providers/nfu-zhengfang/provider.json macos/DerivedData/Build/Products/Debug/CourseAxis.app/Contents/Resources/providers/nfu-zhengfang/provider.json
  cmp macos/Resources/container-migration.plist macos/DerivedData/Build/Products/Debug/CourseAxis.app/Contents/Resources/container-migration.plist
else
  echo "SKIP: full Xcode and/or XcodeGen is unavailable; source type checks above still ran."
fi

echo "CourseAxis available validation passed"
