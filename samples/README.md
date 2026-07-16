# 合成样例

本目录所有文件均为从零编写的完全合成数据，不来自任何学校接口、真实课程表、真实用户导出或删减后的真实响应。

- schedule.document.json：跨平台 CourseAxis 文档样例，使用保留的 synthetic-courseaxis-demo 标识和“示例大学”。
- section_times.json：人为设计的节次时间，仅用于解析与界面开发，不代表任何学校作息。

正方 Adapter 的合成课程与考试输入位于 providers/fixtures/zhengfang-v1/，每个文件都必须显式声明 synthetic: true。贡献新样例前请阅读 [Provider 开发与学校贡献](../docs/PROVIDER_DEVELOPMENT.md)。
