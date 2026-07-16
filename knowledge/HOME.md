# CourseAxis 知识主页

## 稳定产品规则

- 真实开始和结束时间是布局的唯一事实；节次只是学校语义和反向匹配元数据。
- 考试是有明确自然日期和起止时间的单次事件，不伪装成周期课程。
- 周投影只服务周课表；Widget、提醒和“下一项”只使用真实发生事件。
- 课程与考试分源导入；考试源暂时不可用时不删除已有考试。
- Provider 决定精确网络边界、学期语义、Adapter、凭据和 Web 会话命名空间；不同 Provider 的学期与 Stable ID 不相互合并。
- 数据默认留在本机；自托管同步只发送课程 JSON，不发送教务凭据、验证码或 Cookie。

## 公开技术文档

- [架构与数据流](../docs/ARCHITECTURE.md)
- [教务导入协议](../docs/IMPORT_PROTOCOL.md)
- [Provider 开发与学校贡献](../docs/PROVIDER_DEVELOPMENT.md)
- [构建与签名](../docs/BUILD_AND_SIGN.md)
- [维护者发行手册](../docs/RELEASE.md)
- [自托管同步部署](../docs/SYNC_DEPLOYMENT.md)
- [验收清单](../docs/ACCEPTANCE.md)
- [静态网站与赞助边界](../site/README.md)

## 公开安全与社区合同

- [隐私政策](../PRIVACY.md)
- [安全报告政策](../SECURITY.md)
- [威胁模型](../THREAT_MODEL.md)
- [贡献指南](../CONTRIBUTING.md)
- [行为准则](../CODE_OF_CONDUCT.md)
- [支持政策](../SUPPORT.md)
- [第三方软件与许可证](../THIRD_PARTY_NOTICES.md)

## 验证入口

```bash
./scripts/validate.sh
git diff --check
```

学校 Provider 变更还必须按 [Provider 贡献合同](../docs/PROVIDER_DEVELOPMENT.md) 运行跨端解析、origin、会话隔离和资源入包验证。

## 发布状态

当前源码目标版本为 1.5.0。包含静态网站的 269 文件本地单根公开候选及其 `--no-local` fresh clone 已通过公开卫生、网站全依赖审计和双端无签名全量构建；静态产品与自愿赞助页面已在本机完成响应式、交互、无障碍和安全缺省验收，尚未部署。真实 GitHub Actions、分支保护、设备升级迁移、Android 正式签名与 macOS Developer ID 公证尚未验收。软件本身永久免费；赞助完全自愿，不解锁功能、服务或优先级。
