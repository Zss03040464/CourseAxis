# 架构与领域模型

## 1. 产品边界

课轴解决的是“真实时间课程表”，不是把教务中的节次编号换一种样式显示。系统的唯一布局依据是事件在某个自然日内的真实开始分钟与结束分钟。学校节次用于解析、解释和校验，不用于强行定位。

Provider/Adapter 架构让不同学校拥有独立的网络边界、学期语义、凭据与 Web 会话，但不把“使用同一教务厂商”当作已经兼容。当前只验证广州南方学院正方教务。

## 2. 组件

~~~~text
providers/catalog.json + providers/<provider-id>/provider.json
        │ 精确 HTTPS origin/path、学期代码、会话 UUID
        ▼
Provider 独立 WebView / WKWebsiteDataStore
        │ 已登录的学校页面，主框架一次性消息
        ▼
providers/adapters/<adapter-id>/import.js
  ├─ 课程接口
  ├─ 考试接口
  └─ DOM 真实节次时间提取
        │ 适配器原始 JSON
        ▼
AcademicImportAdapter registry（Android / macOS）
  └─ ZhengfangImportAdapter（当前唯一已注册 Adapter）
        ├─ 周次、星期、节次和明确时间解析
        ├─ Provider + 学期分区的稳定 ID
        ├─ 来源状态与 UNRESOLVED 诊断
        └─ AcademicImportBundle
        │ ScheduleDocument v1
        ├───────────────┤
        ▼               ▼
Android 本地文件       macOS App Group 文件
Compose + Glance       SwiftUI + WidgetKit
        │               │
        └──── 可选自托管同步 ────┘
             GET/PUT + revision
~~~~

Provider 和 Adapter 的公开扩展合同见 [Provider 开发与学校贡献](PROVIDER_DEVELOPMENT.md)。

## 3. Provider 与学期分区

Provider 配置来自随安装包发布的本地 Catalog，包含稳定 ID、学校名称、精确允许的 origin/path、Adapter ID、学期代码映射和唯一会话 UUID。未知 Adapter、重复 UUID、损坏资源或过宽网络边界都会在启动或验证阶段失败关闭。

Term.providerId 将学期绑定到具体 Provider。学期的标准 ID 同时包含 Provider、学校、校区、学年和学期代码，使课程、考试、重导入与学期切换不会跨学校或跨学期混用。settings.selectedAcademicProviderId 只记录当前界面选择，不改变已存学期的所属关系。

## 4. 统一数据文档

顶层对象为 ScheduleDocument：

- terms：Provider、学期、第一周周一、总周数、时区。
- timeRules：某学期/校区每个节次的真实起止时间。
- events：课程、考试、自定义事件、调课。
- tombstones：跨设备传播删除。
- settings：当前学期、当前 Provider 和时间轴显示范围。

Schema 位于 schema/schedule.schema.json。Android 与 macOS 使用相同枚举名、字段名和稳定 ID 算法。

## 5. 事件定位

单次事件由 date + startTime + endTime 定位。startSection/endSection 即使存在，也不会改变课程块位置；编辑器反查的“约覆盖第几节”只是元数据。

周期事件由 termId + weeks + weekday + startTime + endTime 定位，实际日期按以下公式计算：

~~~~text
firstWeekMonday + (week - 1) × 7 + (weekday - 1)
~~~~

第一周日期错误会使整学期日期偏移，因此设置页要求用户按学校校历确认。

## 6. 时间解析优先级

1. 教务记录自带的明确起止时间：EXPLICIT。
2. 用户编辑或手动新增的真实时间：USER。
3. 当学期 TimeRule 解析连续节次：SECTION_RULE。
4. 日期覆盖：模型预留 DATE_OVERRIDE。
5. 无可靠来源：UNRESOLVED。

不存在“通用学校默认时间”兜底。解析失败的课程仍保留名称、周次、星期、节次、地点和教师，但不进入错误的时间轴。

## 7. 节次与真实时间轴

TimeResolver 直接取第一节开始和最后一节结束，不用“开始时间 + 节数 × 课长”，因此课间长度和不等长节次都会被保留。

macOS 保持连续分钟时间轴。Android 从活动学期的 TimeRule 建立分段比例尺：每一节课占一个视觉槽位，节内仍按真实分钟等比例定位，休息时段折叠为零高度。课程块、左侧作息、网格线和当前时间线共用同一比例尺。没有有效 TimeRule 时才回退到连续分钟比例尺。

## 8. 冲突与分栏

冲突判断采用半开区间：

~~~~text
a.start < b.end && b.start < a.end
~~~~

因此 10:00–11:00 与 11:00–12:00 不冲突。真实重叠事件允许保存，使用贪心区间分栏算法并排显示，避免覆盖或丢失。

## 9. 重导入语义

教务事件使用 Provider 和学期分区的稳定 ID。重新导入时：

- 只替换同一 Provider 和同一学期的旧教务记录。
- 课程源失败、课程为空或存在未解析真实时间时，本次数据不写入。
- 考试源暂时失败时，允许安全更新课程，但保留该学期旧考试。
- 教务中消失的旧记录写入 tombstone。
- 用户已编辑且 timeSource 为 USER 的同 ID 记录保留，不被重导入静默覆盖。
- 新导入未提取到作息时，移除该学期旧作息，避免继续使用过期时间。

## 10. 同步

客户端 GET 远端文档后按实体 updatedAt 做 LWW 合并，应用 tombstone，清理孤立作息和事件，再使用 baseRevision PUT。收到 409 后重新拉取，最多重试三次。服务端使用临时文件、fsync 和原子重命名落盘。

## 11. 小组件刷新

Android 在文档变化时立即更新 Glance，并用 WorkManager 安排下一场事件开始/结束边界；另有 15 分钟周期兜底。

macOS WidgetKit 时间线在当前事件结束、下一事件开始或午夜刷新。ScheduleEngine.nextOccurrence 按单次事件自然日期或周期课程真实周次生成候选。小组件只读共享文档，不执行教务登录。

## 12. 安全边界

- 每个 Provider 只能在其精确 allowedOrigin 与 pathPrefix 内使用内嵌会话；其他 HTTP(S) 链接交给系统浏览器，危险 scheme 被阻止。
- Android 教务凭据按 Provider 使用 Keystore AES-256-GCM 密钥分区。支持 WebView Multi-Profile 时，每个 Provider 使用 UUID 命名的独立 Profile；不支持时，切换 Provider 前清除默认 Profile，不声称并行会话隔离。
- macOS 每个 Provider 使用 UUID 命名的持久 WKWebsiteDataStore。首次迁移只复制符合旧 Provider host/path 的 Cookie，随后清除旧默认存储。
- Android 导入消息通道限定精确 origin、主框架和一次读取会话；macOS 还要求消息来自当前 Provider 主页面，且只在主动采集期间接受。
- 学校脚本随安装包发布，禁止运行时下载执行远程 Adapter。
- “仅清账号”只删除当前学校凭据与会话；“清除全部本机数据”还会清除已移除 Provider 的历史会话登记。
- 课程同步数据不包含密码、验证码和 Cookie。Android 同步令牌由 Keystore 保护，macOS 同步令牌保存在 Keychain。
- 公网同步必须 HTTPS；同步文档不做端到端加密，自托管服务器管理员能够读取课程 JSON。

完整边界见 [CourseAxis 威胁模型](../THREAT_MODEL.md)。

## 13. 持续集成与发行信任边界

GitHub CI 只负责在无发布凭据的环境中验证源码：公开卫生与合同、静态网站测试/ESLint/生产构建、Provider/Schema、Go 服务与容器、Android 单测/Lint/Debug APK、Swift 单测与无签名 macOS 构建相互独立。CodeQL 对 Java/Kotlin 和 Swift 使用 manual build，外部 XcodeGen 在 CodeQL 跟踪开始前完成构建，避免污染项目分析数据库。

信任边界遵循以下规则：

- 外部 GitHub Action 只允许白名单项，必须固定到 40 位提交 SHA；注解标签对象不充当源码提交。
- checkout 关闭凭据持久化；workflow 默认只有 `contents: read`，CodeQL 仅额外获得 `security-events: write`。
- Pull Request 不使用 `pull_request_target`，不读取发布 secrets，依赖审查只在 `pull_request` 运行。
- XcodeGen 从精确上游提交在 SwiftPM 默认沙箱中构建；安装器拒绝已存在目录，不递归删除调用方路径。
- CI 制品不是官方 Release。可分发制品只能由维护者本地显式传入 Android JKS、Apple Developer ID 和公证凭据后生成，再独立验签、计算哈希并人工上传。

具体命令与失败关闭条件见 [构建与签名](BUILD_AND_SIGN.md) 和 [维护者发行手册](RELEASE.md)。

## 14. 静态网站与赞助信任边界

`site/` 是独立静态前端，不读取应用文件、教务会话或自托管同步数据，也没有站点账号、服务端表单或支付后端。课表示例是显式合成数据；公开应用图标和独立生成的无文字背景是唯一位图资产。

- 页面不加入分析、广告、Cookie、localStorage、支付 SDK 或第三方嵌入 iframe。
- GitHub 与赞助目标只从构建环境读取，并由 `siteConfig` 限定为无用户名密码的 HTTPS URL；缺少或无效配置时失败关闭。
- 外部链接只在用户主动点击后由浏览器打开，不附加课程、设备、账号或跟踪参数。
- 个人收款二维码、支付密钥、商户凭据和私有 `.env` 永不进入公开树；实际支付数据只由用户选择的外部提供方按其政策处理。
- 赞助不参与授权、功能开关、版本发布、支持排队或社区治理。
