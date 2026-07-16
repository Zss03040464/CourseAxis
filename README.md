# 课轴 CourseAxis

CourseAxis 是一套面向 Android 与 macOS 的本地优先真实时间课程表。课程和考试按自然日期、真实开始时间与真实结束时间定位；学校节次只用于解析与展示，不会替代真实时间。

CourseAxis 是独立的社区项目，不隶属于、代表或受广州南方学院及其教务系统厂商认可。当前内置教务适配仅支持广州南方学院正方教务，其他学校不能假定兼容。

当前源码目标版本为 1.5.0，仍处于公开发布前验收阶段；尚未存在的 GitHub Release、签名下载或已部署赞助网址不会在本文中提前冒充为可用服务。

## 功能

- Android Compose 主应用、Glance 桌面 Widget 与按事件边界刷新的 WorkManager 任务。
- macOS SwiftUI 主应用、WidgetKit 小组件、App Sandbox 与 App Group 共享数据。
- 周课表在同一时间轴展示课程和考试，可按“全部课程 / 考试”筛选并在已保存学期间切换。
- 点击课程或考试直接编辑；考试按明确日期与时间保存为单次事件。
- 真实分钟布局、时间冲突分栏、单双周与上课周次、缺少时间时的待确认状态。
- 经严格 Catalog 校验的学校 Provider 会话内一次读取课程、考试与真实作息；当前只内置广州南方学院。
- Android 可在用户点击“保存账号”后使用 Android Keystore 加密保存教务用户名和密码；会话失效时自动填写，验证码仍由用户手动处理。
- JSON 导入与导出、删除恢复、数据健康检查和跨平台统一 Schema。
- 可选的单用户自托管同步服务；不使用同步时，课程数据不会离开设备。

## 隐私与安全摘要

项目默认不包含分析、广告、第三方崩溃收集或项目方运营的云服务。

- Android 教务凭据和同步令牌分别使用 Android Keystore AES-256-GCM 密钥保护；验证码不保存。
- macOS 同步令牌保存在 macOS Keychain；教务登录 Cookie 留在各 Provider 独立的持久化 WKWebView 站点数据中。
- Android 正式构建关闭明文流量与系统云备份；macOS 主应用和 Widget 在同一 App Group 中读取课程文档。
- 内嵌教务页面只允许当前 Provider 的精确 HTTPS origin 与路径范围；当前内置配置为 `https://jwxt.nfu.edu.cn/jwglxt/`。其他 HTTP(S) 页面交给系统浏览器，危险 scheme 被阻止。
- 自托管同步只发送课程 JSON，不发送教务账号、密码、验证码或 Cookie。同步内容没有端到端加密，服务器管理员能够读取其保存的课程文档。

完整说明见 [隐私政策](PRIVACY.md)、[安全政策](SECURITY.md) 和 [威胁模型](THREAT_MODEL.md)。

## 数据模型

1. 单次事件由 `date + startTime + endTime` 定位。
2. 周期课程由 `term + weeks + weekday + startTime + endTime` 定位。
3. `startSection/endSection` 可以为空，只是学校语义，不是布局坐标。
4. 教务显式时间优先于节次规则；用户编辑的真实时间不会被重新导入静默覆盖。
5. 缺少可靠时间时事件标记为 `UNRESOLVED`，不会被放入猜测的时段。
6. 考试是 `EXAM + SINGLE`。
7. 冲突按半开区间 `[start, end)` 检测，相邻事件不冲突。

## 仓库结构

```text
android/          Android 应用、测试与 Glance Widget
macos/            macOS 应用、核心、测试与 WidgetKit Widget
providers/        学校 Provider 描述
server/           Go 自托管同步服务
schema/           课程文档与 Provider JSON Schema
samples/          从零编写的完全合成样例
reference-core/   无 Android 依赖的 Kotlin 参考核心
docs/             架构、导入协议、构建与部署文档
scripts/          验证与打包脚本
site/             静态产品与自愿赞助页面
```

## 验证

完整本机门禁需要 macOS、Swift/Xcode、XcodeGen、Python 3、Node.js 26、JDK 17、Android SDK 36，以及 Go 1.26.5+ 或 Docker：

```bash
./scripts/validate.sh
```

门禁覆盖静态网站测试、ESLint、生产构建与生产依赖审计，Go 测试与容器构建、Swift XCTest、跨平台核心验证、Android 单元测试与 instrumentation 编译、Gradle 严格依赖校验、Lint、Debug APK、release manifest、macOS 源码类型检查和无签名 Xcode 构建。环境缺少 Android SDK 或完整 Xcode 时，对应原生构建会明确显示为跳过，不等同于已验证。

## 持续集成与发布边界

- `.github/workflows/ci.yml` 分别运行公开卫生与合同、静态网站、Go/容器、Android 与 macOS 构建门禁。
- `.github/workflows/codeql.yml` 对 Java/Kotlin 和 Swift 做 CodeQL 扫描；依赖变更另由 Pull Request 依赖审查工作流检查。
- 所有外部 Action 均固定到 40 位源码提交 SHA，checkout 不保留凭据，workflow 使用最小权限并取消过期运行。
- CI 不保存发布签名凭据，不自动产出或发布可安装 Release；Android JKS、Apple Developer ID 和公证凭据只在维护者本机显式传入。
- 当前运行基线为 Android 8.0+（API 26）与 macOS 14+；CI 工具链使用 Android SDK 36（Android 16）和 macOS 26 runner。这是构建基线，不代表所有厂商设备已实机验证。

维护者的签名、公证、哈希与人工上传步骤见 [构建与签名](docs/BUILD_AND_SIGN.md) 和 [发行手册](docs/RELEASE.md)。

## Android 构建

前置条件：JDK 17、Android SDK 36、可访问 Google Maven 与 Maven Central。

```bash
cd android
./gradlew --dependency-verification=strict \
  :app:testDebugUnitTest \
  :app:compileDebugAndroidTestKotlin \
  :app:lintDebug \
  :app:assembleDebug
```

也可以用 Android Studio 打开 `android/`。Debug APK 仅用于本地开发；正式发布包必须使用维护者离线保管的发布密钥签名，密钥不得进入仓库或 CI。

## macOS 构建

前置条件：支持 Swift 5.10 与 macOS 14 SDK 的 Xcode，以及 XcodeGen。

```bash
cd macos
xcodegen generate
xcodebuild \
  -project CourseAxis.xcodeproj \
  -scheme CourseAxis \
  -configuration Debug \
  CODE_SIGNING_ALLOWED=NO \
  build
```

需要在本机运行主应用与 Widget 时，请为两个 target 选择同一开发团队，并启用同一个 App Group：

```text
group.dev.shusenzheng64.courseaxis
```

## 教务导入

1. 先在“设置”中创建或选择学期，并确认第一周周一、总周数和作息。
2. 打开“教务导入”。Android 可选择保存账号；macOS 直接在学校页面中登录。
3. 如学校要求验证码，由用户在教务页面手动完成。
4. 点击“读取课程、考试与真实作息”。应用只在允许的教务页面和当前登录会话内执行随安装包发布的脚本。
5. 检查预览后写入。课程源失败会停止导入；考试源暂时不可用时不会删除已有考试；缺少可靠时间的课程不会套用默认作息。

导入器当前尝试正方课程与考试接口，具体协议见 [教务导入协议](docs/IMPORT_PROTOCOL.md)。学校接口变化可能导致导入失败，但不应绕过 origin、路径或数据校验来恢复兼容。

## 自托管同步

```bash
cd server
export COURSEAXIS_TOKEN="$(openssl rand -hex 32)"
docker compose up -d --build
curl http://127.0.0.1:8787/healthz
```

服务是单用户、单文档设计。公网部署必须由反向代理提供 HTTPS，且不得直接暴露服务端口。课程文档以明文 JSON 保存在你控制的服务器磁盘中；请自行配置磁盘权限、备份、TLS 与访问日志。完整说明见 [同步部署](docs/SYNC_DEPLOYMENT.md)。

## 静态网站与自愿赞助

`site/` 是 CourseAxis 的静态产品页与自愿赞助入口。页面没有分析、广告、Cookie、账号、表单后端或支付 SDK；GitHub 和赞助按钮只接受维护者显式配置的 HTTPS 地址。缺少配置时按钮会显示“尚未开放”，不会跳转到占位地址。

```bash
cd site
npm ci --ignore-scripts
npm test
npm run lint
npm run build
python3 ../scripts/validate-site-build.py --dist dist
npm audit --audit-level=high
```

发布配置、数据边界和外部赞助页要求见 [网站说明](site/README.md)。部署时必须保留 `dist/LICENSE.txt` 和 `dist/THIRD_PARTY_NOTICES.txt`。个人收款二维码、支付密钥、商户凭据和私有 `.env` 不进入仓库；赞助始终不解锁软件功能、服务、支持时效或 issue 优先级。

## 文档

- [架构与数据流](docs/ARCHITECTURE.md)
- [教务导入协议](docs/IMPORT_PROTOCOL.md)
- [Provider 开发与学校贡献](docs/PROVIDER_DEVELOPMENT.md)
- [构建、签名与发布](docs/BUILD_AND_SIGN.md)
- [维护者发行手册](docs/RELEASE.md)
- [同步服务部署](docs/SYNC_DEPLOYMENT.md)
- [验收清单](docs/ACCEPTANCE.md)
- [静态网站与赞助边界](site/README.md)

## 参与与支持

- 提交代码或学校适配前，请阅读 [贡献指南](CONTRIBUTING.md) 与 [行为准则](CODE_OF_CONDUCT.md)。
- 普通问题与支持边界见 [支持政策](SUPPORT.md)；未修复漏洞必须按 [安全政策](SECURITY.md) 私密报告。
- 发布变化见 [变更记录](CHANGELOG.md)，依赖与许可证见 [第三方声明](THIRD_PARTY_NOTICES.md)。
- Issue、Pull Request 和 fixture 禁止包含真实账号、课程、考试、接口响应、Cookie、Token 或身份截图。

## 许可证

项目使用 [MIT License](LICENSE)。许可证允许使用、修改、分发和商业使用；维护者提供的软件本身永久免费，自愿赞助不解锁功能、服务或优先级。
