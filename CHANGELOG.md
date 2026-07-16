# 变更记录

本项目使用语义化版本。尚未发布的内容不会被描述为已经提供下载。

## 1.5.0（未发布）

### 新增

- Android 与 macOS 共用经 Schema 校验的学校 Provider Catalog、正方 Adapter 和纯合成 fixture。
- Android Provider 级 Keystore 凭据、支持时的独立 WebView Profile，以及旧 WebView 的切换前清除回退。
- macOS Provider 级持久 WKWebsiteDataStore、受限旧 Cookie 迁移和历史会话完整清除。
- Provider 开发合同、隐私政策、安全政策、威胁模型、公开卫生扫描与社区协作文件。

### 变更

- 双端学期、课程、考试、作息、Stable ID 和当前学校选择按 Provider 隔离。
- Android 教务导入只保留学校、当前学期、账号保存和唯一读取动作；验证码仍由用户手动完成。
- Android 与 macOS 统一以周课表展示和编辑课程/考试，考试筛选严格留在当前学期。
- Android 周视图使用紧凑同排学期/模式控制和真实作息分段时间轴。

### 安全

- Android 教务凭据与同步令牌分别使用 Keystore AES-256-GCM；正式构建关闭明文流量和系统云备份。
- macOS 同步令牌迁入 Keychain，应用与 Widget 使用 App Sandbox 和同一 App Group。
- 教务导航与脚本消息限定精确 Provider HTTPS origin/path、主框架和主动读取会话。
- Gradle 分发包、依赖校验和锁文件、Go/Docker 镜像均已固定并纳入门禁。

### 尚未验证

- Android WebView Multi-Profile Cookie 隔离和完整浏览数据清除仍需在可用真机或 AVD 上运行 instrumentation。
- macOS 旧版数据到 App Group 的真实覆盖升级、Developer ID 签名与公证将在发布候选阶段验证。
- 1.5.0 尚未创建 GitHub Release，也没有可称为官方发布的二进制。
