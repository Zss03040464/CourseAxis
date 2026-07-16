# 贡献指南

感谢你帮助改进 CourseAxis。项目接受缺陷修复、无障碍与界面改进、文档、测试、安全加固和新学校 Provider，但所有贡献都必须守住本地优先、真实时间、隐私和学校会话隔离边界。

## 开始之前

1. 普通使用问题先查看 [支持政策](SUPPORT.md) 和现有 issue。
2. 未修复漏洞、真实账号或无法脱敏的数据必须按 [安全政策](SECURITY.md) 私密报告，不能创建公开 issue。
3. 新学校或 Adapter 先完整阅读 [Provider 开发与学校贡献](docs/PROVIDER_DEVELOPMENT.md)。
4. 大型功能先创建不含敏感数据的提案，说明用户问题、平台范围、数据变化、安全影响和验收方法。

## 本机开发

完整环境和命令见 [README](README.md) 与 [构建、签名与发布](docs/BUILD_AND_SIGN.md)。最小提交前检查：

~~~~bash
python3 scripts/check-public-hygiene.py --mode source
python3 scripts/validate-providers.py
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
./scripts/validate.sh
git diff --check
~~~~

如果环境缺少 Android SDK、完整 Xcode、Docker 或真实设备，请在 Pull Request 中明确列出未运行项。编译 instrumentation 源码不等于真机通过，无签名构建不等于签名、公证或安装通过。

修改 `site/` 时还要运行：

~~~~bash
cd site
npm ci --ignore-scripts
npm test
npm run lint
npm run build
npm audit --omit=dev
~~~~

## 修改规则

- 功能与缺陷修复先提供能失败的回归测试，再实现最小修复。
- Android 与 macOS 的 Schema、Stable ID、Provider、学期、课程、考试和合并语义必须保持一致。
- 不得用默认时间猜测课程位置，不得把考试改成周期课程，不得跨 Provider 或跨学期借用数据。
- 不得扩大教务 origin、关闭证书/路径检查、重新启用明文流量或下载执行远程 Adapter。
- 不得加入遥测、广告、崩溃收集、中心化账号或远程服务，除非先完成独立设计、明确选择加入和隐私政策更新。
- 静态网站不得加入跟踪、Cookie、浏览器持久化、支付 SDK、第三方嵌入或表单后端；外部地址必须为显式配置的 HTTPS URL，缺失时失败关闭。
- 不得通过吞异常、空实现、死代码、放宽测试或扩大 ignore 让门禁表面通过。

## 数据与截图

禁止提交真实学号、姓名、账号、密码、验证码、Cookie、Token、Authorization、课程、考试、教师、地点、座位、完整接口响应、抓包、设备标识或带身份信息的截图。

测试输入必须从零编写，并清楚使用“示例大学、合成课程、虚构教师”等标识。不能把真实响应删字段、改姓名后称为合成数据。Provider fixture 还必须声明 synthetic: true，并通过公开卫生和 Provider 校验。

## Provider 贡献

每个 Provider 必须具有稳定 ID、唯一且不可复用的会话 UUID、精确 HTTPS origin/path、语义学期代码和显式 Adapter。Pull Request 必须同时包含：

- Catalog/Schema 和损坏资源失败关闭测试；
- origin、路径穿越、相似域名与端口测试；
- 课程、考试、作息和跨端 Stable ID 测试；
- Android 凭据/Profile 与 macOS WKWebsiteDataStore 会话隔离测试；
- APK 和 macOS 应用资源打包一致性验证；
- 完全合成 fixture 和实际验证范围说明。

同一厂商不代表同一学校已经兼容。没有学校账号或设备证据时，只能声明代码与合成测试通过。

## Pull Request

Pull Request 应保持单一目的，并说明：

- 解决的问题与用户可见变化；
- 安全、隐私、迁移和兼容性影响；
- 新增或修改的测试；
- 已运行命令及结果；
- 明确未验证项；
- 新依赖的用途、版本、许可证和替代方案。

提交贡献即表示你有权提交相关内容，并同意按仓库的 [MIT License](LICENSE) 发布你的贡献。项目不要求转让版权。

## 社区行为

所有参与者都必须遵守 [行为准则](CODE_OF_CONDUCT.md)。赞助不会换取合并、优先修复、支持时限或特殊审核待遇。
