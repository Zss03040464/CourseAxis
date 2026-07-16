# Provider 开发与学校贡献

## 兼容性边界

当前只验证广州南方学院正方教务。“使用正方系统”不等于“已经兼容 CourseAxis”：每个学校的域名、路径、学期代码、接口版本、登录方式与返回字段都可能不同。新增 Provider 后，只能声明其已通过的具体学校与系统，不得把同一厂商的其他学校一并标记为支持。

CourseAxis 不代表任何学校或教务厂商。贡献者必须遵守学校规则、适用法律和负责任披露要求，不得绕过验证码、访问控制或学校安全限制。

## 目录合同

每个学校 Provider 和适配器使用以下单一资源树，Android 与 macOS 构建直接打包同一份文件：

```text
providers/catalog.json
providers/<provider-id>/provider.json
providers/adapters/<adapter-id>/import.js
providers/fixtures/<adapter-id>/courses.json
providers/fixtures/<adapter-id>/exams.json
```

`providers/catalog.json` 只列出随正式应用发布的 Provider ID。Provider ID 必须是小写字母、数字和单个连字符组成的稳定标识，不得包含路径、空格或显示文案。

## `provider.json`

Provider 必须符合 `schema/academic-provider.schema.json`，并包含：

- `id`：与目录名和 Catalog 完全一致。
- `schoolName`：界面显示的学校名称。
- `vendor`：教务厂商或系统系列的稳定大写标识。
- `adapterId`：必须对应原生层明确注册的 Adapter；未知 Adapter 必须失败关闭，不得默认回退到正方解析。
- `allowedOrigin`：单一精确 HTTPS origin，不允许通配子域、用户信息、查询参数或非默认端口。
- `pathPrefix`、`loginPath`、`homePath`：必须是 origin 内的绝对路径，登录和首页必须位于路径前缀内。
- `semesterCodes`：使用 `first` / `second` / `third` 语义键映射该学校的真实学期代码。
- `sessionNamespace`：每个 Provider 唯一、永不复用的规范 UUID。它同时决定 Android WebView Profile 和 macOS `WKWebsiteDataStore` 的会话隔离边界。

已发布 Provider 的 `id` 和 `sessionNamespace` 不得更换。如果学校迁移到不兼容的新教务系统，应创建新 Provider，而不是让旧会话命名空间指向新 origin。

## Adapter 合同

Adapter 只能从安装包中加载 `providers/adapters/<adapter-id>/import.js`。禁止运行时下载、拼接或执行远程脚本。脚本只能在 Provider 允许的 origin/path 内运行，不得把 Cookie、密码、验证码或 Authorization 写入输出。

原生 `AcademicImportAdapter` 负责：

1. 把 Provider 边界和当前学期参数注入脚本。
2. 将课程、考试和真实作息解析为通用 `AcademicImportBundle`。
3. 将 Stable ID 按 Provider 和学期分区，避免不同学校相互覆盖。
4. 课程源失败或真实时间缺失时阻止写入；考试源暂时失败时保留本地已有考试。

新 Adapter 必须在 Android 和 macOS 的 registry 显式注册，并使用同一组合成输入固定跨端 Stable ID 和解析结果。

## Fixture 与隐私

禁止提交真实学号、姓名、教师、课程、考试、教室、座位、账号、密码、验证码、Cookie、Token、Authorization 头、完整教务响应或带身份信息的截图。

`providers/fixtures/` 下的每个文件必须：

- 显式声明 `"synthetic": true`；
- 使用虚构学校、课程、教师、地点和短标识；
- 不能由真实响应简单删减字段而来；
- 覆盖课程和考试的正常、空数据、缺失时间与字段别名边界。

如果无法证明数据完全合成，不得提交。可在本机用真实账号验证行为，但不得将输入、输出、日志或截图加入 Git、issue 或 Pull Request。

## 必需测试

每个新 Provider 或 Adapter 的 Pull Request 必须同时提供：

- Catalog/Schema 测试：拒绝重复 ID、重复 UUID、未知 Adapter 和损坏资源。
- Origin 测试：允许精确 HTTPS origin/path，拒绝 HTTP、相似域名、通配子域、非默认端口和路径穿越。
- 课程解析测试：周次、星期、节次、真实时间、地点、教师和 Stable ID。
- 考试解析测试：明确日期、开始/结束时间、地点、名称与空源保护。
- 会话隔离测试：凭据命名空间、Android WebView Profile 和 macOS `WKWebsiteDataStore` 不与其他 Provider 共享。
- 资源打包测试：APK 和 `.app` 中的 Catalog、Provider 和 Adapter 必须与源码逐字节一致。

## 本机验证

```bash
python3 scripts/validate-providers.py
node --check providers/adapters/<adapter-id>/import.js

cd android
./gradlew --dependency-verification=strict \
  :app:testDebugUnitTest \
  :app:compileDebugAndroidTestKotlin \
  :app:lintDebug \
  :app:assembleDebug

cd ../macos
swift test
swift run courseaxis-core-validation
swiftc -typecheck -parse-as-library Shared/*.swift CourseAxisApp/*.swift

cd ..
./scripts/validate.sh
git diff --check
```

没有 Android 真机、AVD 或实际学校账号时，必须明确标记未验证项。“测试源码已编译”不得写成“真机会话隔离已通过”，“同一厂商”不得写成“该学校已支持”。

## GitHub 检查映射

Provider Pull Request 会触发以下独立检查；它们是公开仓库分支保护的 required-check 候选，在分支规则实际启用前不宣称已强制：

- `Public hygiene and contracts`：Catalog、Schema、合成 fixture、公开卫生、文档、workflow 策略与 actionlint。
- `Go server and container`：自托管服务测试、vet 和固定基础镜像构建。
- `Android test, lint, and debug APK`：严格依赖校验、单测、instrumentation 编译、Lint、Debug APK 和 Provider 资源逐字节对账。
- `Swift tests and macOS app`：Swift 测试、双 target 类型检查、无签名 Xcode 构建和 Provider 资源逐字节对账。
- `Review dependency graph changes`：只在 Pull Request 上审查新增依赖的已知漏洞与许可变化。
- CodeQL：Java/Kotlin 与 Swift 使用 manual build 独立扫描。

本地全量门禁仍是提交 Provider 的前置条件；CI 通过不替代真实学校的登录、验证码、会话与导入验收。

## Pull Request 检查清单

- [ ] Provider ID、显示名称、origin、路径、学期代码和 UUID 已与学校真实系统核对。
- [ ] 所有 fixture 均为从零编写的合成数据，不含真实个人或教务信息。
- [ ] Android 与 macOS 均显式注册 Adapter，未知 Adapter 仍失败关闭。
- [ ] 课程、考试、origin、Stable ID 和会话隔离测试已提供。
- [ ] 隐私政策、威胁模型和支持列表已按真实验证结果更新。
- [ ] 全量门禁通过，未验证项已在 Pull Request 中明确列出。
