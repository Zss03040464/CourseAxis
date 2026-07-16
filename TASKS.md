# 当前任务

## CourseAxis 1.5.0 安全开源发布

- 目标：以 MIT License 在 GitHub 永久免费开源；赞助完全自愿，不解锁功能、服务、优先级或官方学校关系。
- 当前状态：安全与隐私基线、Provider/Adapter 多学校边界、合成样例、社区合同、发行脚本、CI、CodeQL、依赖审查、Dependabot、本地净化公开候选，以及静态产品与自愿赞助页面均已完成。
- 当前阶段：包含网站的 269 文件单根公开候选及其 `--no-local` fresh clone 已通过全量门禁；现在只剩不公开的 GitHub Actions/分支保护验证、正式签名、公证、升级迁移和最终公开授权。

## 已关闭的公开阻断

- Android 教务凭据和同步令牌使用 Android Keystore 保护，macOS 同步令牌使用 Keychain；验证码不持久化。
- 教务 Web 会话按 Provider 隔离，导航、消息与 Adapter 执行受精确 HTTPS origin/path 合同限制。
- 学期、课程、考试、作息与 Stable ID 按 Provider 分区，不跨学校或学期合并。
- Android 正式构建关闭明文流量和系统备份；macOS 使用 App Sandbox 与 App Group。
- 公开样例和 Provider fixture 均从零编写为完全合成数据，公开卫生扫描会拒绝私钥、Token、个人路径、真实课表和支付二维码原图。
- Android JKS、Apple Developer ID 和公证凭据不进入仓库或 CI；正式制品只由维护者本地显式传入凭据后生成。
- 静态网站不含跟踪、Cookie、支付 SDK、表单后端或真实课表；外部 GitHub 与赞助地址只接受 HTTPS，缺少配置时失败关闭。

## 发布前必须完成

- [x] 净化候选只含一个无父根提交，不含旧作者邮箱、真机证据、本机路径或内部实施记录。
- [x] fresh clone 通过全部 Python、Provider、workflow/actionlint、Go/容器、Swift、Android 严格依赖/Lint/Debug APK 与 macOS 无签名构建。
- [ ] 私有 GitHub 候选的 Actions 真实通过，`main` 已启用 required checks、线性历史、禁止 force push 和禁止删除。
- [ ] Android Release APK 已用离线 JKS 签名并验签；macOS 制品已用 Developer ID 签名、公证、staple 并通过 Gatekeeper 检查。
- [ ] 公开 SHA-256 与实际制品一致，真实升级迁移和设备侧 WebView 隔离的未验证项已关闭或如实标注。
- [x] 隐私、安全、支持、学校非官方关系和赞助文案与当前代码及未部署网站一致；部署时仍须复核实际 URL 与外部提供方政策。
- [ ] 获得维护者最终明确授权后，才创建或切换公开远程、push 和发布 Release。

## 当前验证基线

- `./scripts/validate.sh`：公开卫生、合同、依赖、双端源码与原生无签名构建总门禁。
- `.github/workflows/ci.yml`：公开卫生/静态网站/Go/Android/macOS 五类独立检查。
- `.github/workflows/codeql.yml`：Java/Kotlin 与 Swift manual build 扫描。
- `.github/workflows/dependency-review.yml`：Pull Request 依赖变更审查。
- 2026-07-16 最新本地 fresh clone：56 项 Python、11 项网站测试与 0 个 npm 审计漏洞、actionlint 1.7.12、固定 Go 1.26.5 容器、68 项 Swift、Android 68 个严格依赖/测试/Lint/Debug 任务、macOS 主应用与 Widget 无签名构建均通过；构建后 Git 状态干净。
- 未验证环境：已授权 ADB 设备 0，本机当前没有可用 Android Emulator/AVD；Android 正式签名变量未注入，macOS Developer ID/公证变量未注入。

当前尚未创建或推送公开 GitHub 仓库，也没有发布 1.5.0 签名制品。
