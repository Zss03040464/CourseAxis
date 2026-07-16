# 第三方软件与许可证声明

CourseAxis 自身源码使用 MIT License。下列第三方组件保留各自版权与许可证；本文件不改变其条款。精确解析版本以 android/app/gradle.lockfile、android/gradle/verification-metadata.xml、server/go.mod、site/package-lock.json 和固定 CI 配置为准。

## 随 Android 应用链接或打包

| 组件 | 用途 | 许可证 |
| --- | --- | --- |
| AndroidX Activity、Compose、Glance、Lifecycle、Navigation、WebKit、WorkManager 与其 AndroidX 传递依赖 | Android UI、Widget、生命周期、导航、WebView 安全接口和后台刷新 | Apache License 2.0 |
| Kotlin 标准库与 Kotlin Coroutines | Kotlin 运行时与协程 | Apache License 2.0 |
| Material Icons | Android 界面图标 | Apache License 2.0 |

Apache License 2.0 全文见 [licenses/APACHE-2.0.txt](licenses/APACHE-2.0.txt)。

## 随静态网站构建或分发

| 组件 | 用途 | 许可证 |
| --- | --- | --- |
| React 19.2.0 与 React DOM 19.2.0 | 静态页面运行时 | MIT License |
| Phosphor Icons React 2.1.10 | 网站界面图标 | MIT License |
| Vite 6.4.3 与 Rollup/esbuild 等锁定传递依赖 | 网站生产构建，不作为远程运行时加载 | MIT License 等，以 `site/package-lock.json` 和上游包声明为准 |
| Vitest、ESLint 与相关插件 | 网站测试和静态检查，不进入生产页面 | MIT License |

CourseAxis 应用图标属于本项目资产；`site/public/courseaxis-starline-background.png` 是为本项目生成的独立无文字背景，不含第三方品牌、真实课表或用户截图。

## 构建与测试工具

| 组件 | 用途 | 许可证 |
| --- | --- | --- |
| Android Gradle Plugin 与 Gradle Wrapper | Android 构建 | Apache License 2.0 |
| Kotlin Gradle Plugin | Kotlin/Compose 编译 | Apache License 2.0 |
| JUnit 4.13.2 | JVM 单元测试，不进入正式 APK | Eclipse Public License 1.0 |
| JSON-java 20240303 | JVM 导入器单元测试，不进入正式 APK | Public Domain |
| XcodeGen 2.45.4 | 从 project.yml 生成本机 Xcode 工程，不随应用分发 | MIT License |
| actionlint 1.7.12 | GitHub Actions 静态检查，不随应用分发 | MIT License |
| actions/checkout、actions/setup-java、actions/setup-python、actions/setup-go、actions/setup-node 与 actions/dependency-review-action | GitHub Actions 检出、工具链和依赖审查，不随应用分发 | MIT License |
| github/codeql-action | GitHub 代码扫描工作流，不随应用分发 | MIT License；其调用的 CodeQL CLI 另受 CodeQL Terms and Conditions 约束 |

Eclipse Public License 1.0 见 [licenses/EPL-1.0.txt](licenses/EPL-1.0.txt)，JSON-java 声明见 [licenses/JSON.txt](licenses/JSON.txt)。

## Go 与 Apple 工具链

- 同步服务当前只使用 Go 标准库。Go 源码使用 BSD-style License；官方文本见 [Go LICENSE](https://go.dev/LICENSE)。
- Swift 标准库与 SwiftPM 使用其上游许可证；Xcode、macOS SDK、Apple Framework 与公证工具由 Apple 提供并受其许可约束，不随本仓库重新分发。
- CourseAxis macOS 应用只链接系统 Framework，没有通过 SwiftPM 引入第三方运行时 package。

## 上游链接

- [AndroidX](https://github.com/androidx/androidx)
- [Kotlin](https://github.com/JetBrains/kotlin)
- [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)
- [Gradle](https://github.com/gradle/gradle)
- [JUnit 4](https://github.com/junit-team/junit4)
- [JSON-java](https://github.com/stleary/JSON-java)
- [React](https://github.com/facebook/react)
- [Phosphor Icons](https://github.com/phosphor-icons/react)
- [Vite](https://github.com/vitejs/vite)
- [Vitest](https://github.com/vitest-dev/vitest)
- [ESLint](https://github.com/eslint/eslint)
- [XcodeGen](https://github.com/yonaskolb/XcodeGen)
- [actionlint](https://github.com/rhysd/actionlint)
- [GitHub Actions](https://github.com/actions)
- [CodeQL Action](https://github.com/github/codeql-action)

如发现遗漏或许可证与锁定版本不一致，请按 [贡献指南](CONTRIBUTING.md) 提交修正；在确认前不得通过删除声明或扩大打包排除规则来规避。
