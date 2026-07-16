# CourseAxis 开发规则

## 不可破坏的领域规则

1. `date + startTime + endTime` 是单次事件的事实来源；真实时间决定布局。
2. 周期课程只可由教务显式时间或当学期 `TimeRule` 解析为真实时间；解析失败必须保留为 `UNRESOLVED`，不得套默认作息。
3. `startSection/endSection` 可以为空，只是学校语义和反向匹配结果，不是坐标。
4. 冲突按半开区间 `[start, end)` 判断；相邻事件不冲突，冲突事件允许保存并分栏显示。
5. 考试是 `EXAM + SINGLE`，不得伪装成某周某节的周期课程。
6. 同步按记录 `updatedAt` 做 LWW；删除必须写 tombstone。
7. Android 与 macOS 的 Stable ID、JSON 字段和合并规则必须保持一致。

## 安全与隐私规则

1. 教务密码只可由平台安全存储保护在本机；验证码不得持久化；Cookie 只可留在平台 WebView/WKWebView 站点数据中。
2. 密码、验证码、Cookie、同步令牌和真实 Authorization 请求头不得进入课程文档、日志、测试 fixture、截图、issue 或版本库。
3. 教务内嵌浏览器必须使用 Provider 的精确 HTTPS origin、端口和路径合同；不得用通配子域或关闭校验来修复兼容问题。
4. 学校适配脚本随源码和正式构建发布，禁止运行时下载并执行远程脚本。
5. 新增 Provider 只能使用完全合成或可公开证明的数据，并同时提供 Schema、origin、课程和考试解析测试。
6. Provider ID 和会话 UUID 发布后不得改名或复用；新 Provider 必须同时验证凭据、Web 会话和学期数据隔离。
7. 任何新遥测、远程服务、备份范围、秘密持久化或删除语义都必须同步更新 `PRIVACY.md` 与 `THREAT_MODEL.md`。
8. 发布签名私钥、密码、服务账号和个人收款二维码原图不得进入仓库或 CI。

Provider 的目录、Schema、Fixture、隔离和 Pull Request 合同以 `docs/PROVIDER_DEVELOPMENT.md` 为准。

## 质量门禁

修改功能或 Bug 时使用失败回归测试固定合同，再实现修复。提交前至少运行：

```bash
./scripts/validate.sh
git diff --check
```

Android 设备侧 WebView 行为、应用覆盖升级、macOS 签名/公证和发布制品需要在对应真实环境另行验收；编译通过不能替代这些验证。
