# CourseAxis 项目

CourseAxis 是面向 Android 与 macOS 的本地优先真实时间课程表。两端使用统一数据语义，支持周期课程、单次考试、真实时间布局、跨学期周视图和原生 Widget。

## 产品边界

- 当前内置教务 Provider 仅支持广州南方学院正方系统；项目与学校及教务厂商没有官方隶属关系。
- 应用永久免费并使用 MIT License。赞助完全自愿，不解锁功能、服务、优先级或官方身份。
- 项目默认无分析、广告、崩溃上报和中心化账号；同步由用户自行选择和部署。
- 课程、考试和自定义事件统一显示在周课表；点击事件直接编辑，不维护重复的独立管理页面。

## 当前状态

- 第一阶段安全基线已完成：精确教务 origin/path、Android Keystore、macOS Keychain、App Sandbox/App Group、完整本机清除、Android 备份禁用、受支持依赖与严格供应链校验、公开隐私与安全合同。
- Android 设备侧 WebMessage 与 Cookie/WebStorage 清除测试仍需在可用真机或 AVD 上运行；macOS 旧数据真实升级迁移仍留待发布候选安装阶段做哈希对账。
- Provider Catalog、课程文档 Provider 隔离、通用 `AcademicProvider` / `AcademicImportAdapter` 边界及 Android Keystore/WebView Profile、macOS 持久化 `WKWebsiteDataStore` 隔离均已完成；Provider 贡献合同与全量门禁已经发布，当前仍只声明广州南方学院正方系统已验证。
- Phase 3 已完成：合成样例、社区合同、第三方声明、1.5.0 发行脚本、固定源码提交的 CI/CodeQL、依赖审查、Dependabot 与发行分类已通过全量门禁。
- Phase 4 本地候选已按最新公开面刷新：导出器只读取已提交 Git blob，以 allowlist 生成 269 个文件、0 个符号链接的公开树；本地候选只有一个中性 noreply 作者的无父根提交，`--no-local` fresh clone 已通过 public hygiene、固定 actionlint、Go/容器、Swift、Android Debug、macOS 无签名原生构建与静态网站全量门禁。
- Phase 5 静态产品与自愿赞助页面已完成：以选定的“星轨时间轴”方案实现桌面、平板与手机响应式页面；只使用合成课表数据，不含分析、广告、Cookie、支付 SDK、表单后端或私有二维码，GitHub 与赞助地址缺失时失败关闭。网站 11 项测试、ESLint、可移植生产构建、包含开发依赖的全量安全审计、运行时许可和真实浏览器设计 QA 已通过，尚未部署。
- 当前没有可用 ADB/AVD、Android 正式签名环境或 macOS Developer ID/公证环境；设备侧 WebView、真实升级迁移、正式签名、公证与 Gatekeeper 仍未验收。
- 当前尚未创建或推送公开 GitHub 仓库；没有用户最终明确授权前，不创建公开远程、不 push、不发布 Release。

## 开发入口

- 领域与安全规则：`AGENTS.md`
- 当前任务：`TASKS.md`
- 知识主页：`knowledge/HOME.md`
- 架构：`docs/ARCHITECTURE.md`
- 隐私政策：`PRIVACY.md`
- 安全政策：`SECURITY.md`
- 威胁模型：`THREAT_MODEL.md`
- 构建与发行：`docs/BUILD_AND_SIGN.md`、`docs/RELEASE.md`
- Provider 贡献：`docs/PROVIDER_DEVELOPMENT.md`
- 静态网站与赞助边界：`site/README.md`
- 统一验证：`scripts/validate.sh`
