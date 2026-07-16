# 构建与签名

本页区分开发验证与维护者正式发行。Debug 或无签名构建可证明源码能够编译，不能证明二进制来自项目维护者。任何签名私钥、JKS、密码、Apple 证书导出文件和公证凭据都不得进入仓库、CI、日志或 Actions artifact。

完整发行流程见 [发行手册](RELEASE.md)。

## 通用验证

```bash
./scripts/validate.sh
```

门禁不读取教务凭据，也不会安装应用或修改用户课程数据。

## 静态网站构建

网站要求 `.node-version` 指定的 Node.js 26。发布构建只接受维护者显式提供的 HTTPS GitHub 与赞助地址，不读取应用或签名凭据：

```bash
cd site
npm ci --ignore-scripts
npm test
npm run lint
npm run build
python3 ../scripts/validate-site-build.py --dist dist
npm audit --audit-level=high
```

只有 `dist/` 可交给静态托管服务，且必须保留 `dist/LICENSE.txt` 和 `dist/THIRD_PARTY_NOTICES.txt`；`node_modules/`、`.npm-cache/`、`.env`、个人二维码、支付密钥和商户凭据都不得上传或提交。缺少 `VITE_GITHUB_URL` 或 `VITE_SPONSOR_URL` 时对应入口失败关闭，不应为构建成功而配置虚假地址。

## Android 开发构建

要求 JDK 17、Android SDK Platform 36 和可访问锁定依赖的 Maven 仓库。SDK 路径放在未跟踪的 `android/local.properties`，不要提交个人绝对路径。

```bash
cd android
./gradlew --dependency-verification=strict \
  :app:testDebugUnitTest \
  :app:compileDebugAndroidTestKotlin \
  :app:lintDebug \
  :app:assembleDebug
```

Debug APK 使用 Android 调试证书，只能用于开发，不能作为官方发行包。

## Android 正式签名

维护者使用源码树外、离线备份的同一 JKS 保持覆盖升级能力。发行脚本不读取个人目录或 macOS Keychain 的固定服务，也没有默认 alias；四项签名参数必须由调用者显式传入环境变量：

```bash
export COURSEAXIS_JAVA_HOME="/path/to/jdk-17/Contents/Home"
export ANDROID_HOME="/path/to/android-sdk"
export COURSEAXIS_KEYSTORE_FILE="/absolute/path/outside/repository/courseaxis-release.jks"
export COURSEAXIS_KEYSTORE_PASSWORD="..."
export COURSEAXIS_KEY_ALIAS="..."
export COURSEAXIS_KEY_PASSWORD="..."
./scripts/build-android-release.sh
```

建议从不会记录命令行和环境变量的本地秘密管理流程启动脚本；不要把以上值写进 shell profile、`.env`、GitHub Secret 或仓库脚本。脚本会运行单元测试、Release Lint、R8、严格依赖校验和 `apksigner verify --print-certs`，然后写入被 Git 忽略的：

```text
artifacts/android/1.5.0-13/
```

目录包含 APK、单文件 SHA-256 和签名证书信息。丢失原 JKS 后不能用新密钥覆盖升级既有安装；不得以降低 Android 安装校验来绕过这一限制。

## macOS 开发构建

要求支持 macOS 14 SDK 与 Swift 5.10 的完整 Xcode，以及 XcodeGen。

```bash
cd macos
xcodegen generate
xcodebuild \
  -project CourseAxis.xcodeproj \
  -scheme CourseAxis \
  -configuration Debug \
  -derivedDataPath DerivedData \
  CODE_SIGNING_ALLOWED=NO \
  build
```

主应用和 Widget 使用同一 App Group：

```text
group.dev.shusenzheng64.courseaxis
```

Fork 修改 bundle ID 时，必须同步修改两个 target、entitlement 和 `DocumentFileStore` 的 App Group；否则应用与 Widget 会使用不同容器。

## macOS Developer ID 与公证

正式脚本要求本机已经安装有效的 Developer ID Application 证书、匹配的 App Group provisioning profile，并由维护者事先用 `notarytool store-credentials` 创建 Keychain profile。仓库不会保存或创建这些秘密。

```bash
export COURSEAXIS_DEVELOPMENT_TEAM="ABCDE12345"
export COURSEAXIS_MACOS_SIGNING_IDENTITY="Developer ID Application: Example Name (ABCDE12345)"
export COURSEAXIS_NOTARY_PROFILE="courseaxis-notary"
./scripts/build-macos-release.sh
```

如果确实需要让 Xcode 自动更新本机 provisioning profile，可在明确了解会访问 Apple Developer 服务后设置：

```bash
export COURSEAXIS_ALLOW_PROVISIONING_UPDATES=1
```

脚本使用 Xcode archive 完整构建主应用与 Widget，核对 TeamIdentifier，提交 Apple 公证，staple ticket，再执行 `codesign --verify`、`stapler validate` 与 `spctl --assess`。通过后产物写入：

```text
artifacts/macos/1.5.0-10/
```

没有 Developer ID 与 Apple 公证条件时，只能发布源码和明确标注的本地开发构建，不能把 ad-hoc 包描述为已签名或已通过 Gatekeeper。

## 发行元数据

对最终 APK 与 macOS ZIP 生成统一校验文件：

```bash
./scripts/generate-release-metadata.sh \
  --output artifacts/release/1.5.0 \
  artifacts/android/1.5.0-13/CourseAxis-1.5.0-13-release.apk \
  artifacts/macos/1.5.0-10/CourseAxis-1.5.0-10-macos.zip
```

输出 `SHA256SUMS` 和 `RELEASE-METADATA.txt`，只记录制品文件名、版本、提交和工具链，不记录制品绝对路径或签名秘密。

## 自托管同步服务

```bash
cd server
go test -mod=readonly ./...
go build -trimpath -ldflags="-s -w" -o courseaxis-sync ./cmd/courseaxis-sync
COURSEAXIS_TOKEN="$(openssl rand -hex 32)" ./courseaxis-sync
```

容器构建使用 Dockerfile 中固定到镜像摘要的 Go 与 Alpine 基础镜像。公开部署仍必须由用户自己的反向代理提供 HTTPS。

## 用户侧二进制核验

- 只从项目仓库的正式 Release 下载，并核对 `SHA256SUMS`。
- Android 同时核对 Release 页面公布的证书 SHA-256；未知来源同名 APK 不能视为官方包。
- macOS 解压后运行 `codesign --verify --deep --strict`、`spctl --assess --type execute` 和 `xcrun stapler validate`。
- 第三方 Fork、第三方网盘和自行重签名的 APK/应用由相应发布者负责。
