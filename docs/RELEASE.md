# CourseAxis 发行手册

本手册面向维护者。1.5.0 当前仍是未发布版本；只有以下步骤全部完成、制品与源码对应且 GitHub Release 实际公开后，才能称为正式发布。

## 发行边界

- GitHub Actions 不保存 Android JKS、Apple 证书或公证凭据，也不生成官方签名包。
- Android 与 macOS 正式制品只在维护者受控的本机离线签名。
- 发行不得包含真实账号、课程、考试、响应、Cookie、日志、截图、导出数据或个人收款二维码原图。
- 赞助不影响版本、功能、支持、issue 优先级或发布决定。

## 1. 准备版本

1. 确认 `CHANGELOG.md` 没有把未验证事项写成已完成。
2. Android `versionName/versionCode`、macOS `MARKETING_VERSION/CURRENT_PROJECT_VERSION` 和 tag 必须一致。
3. 确认工作区干净，依赖锁、验证元数据和 Provider fixture 已提交。
4. 运行：

```bash
python3 scripts/check-public-hygiene.py --mode public
./scripts/validate.sh
git diff --check
```

公开候选必须在 fresh clone 再执行同一组门禁。任何秘密扫描、CodeQL、依赖审查或 required check 失败都停止发布。

静态网站还必须在同一候选中执行：

```bash
cd site
npm ci --ignore-scripts
npm test
npm run lint
npm run build
npm audit --audit-level=high
```

检查生产构建只包含公开图标、独立背景和合成课程示例；不得含 `.env`、个人二维码、支付密钥、商户凭据、真实课表、分析脚本或支付 SDK。

## 2. 本机签名制品

按 [构建与签名](BUILD_AND_SIGN.md) 配置本地环境，依次运行：

```bash
./scripts/build-android-release.sh
./scripts/build-macos-release.sh
```

两个脚本都必须自然成功；禁止通过跳过 `apksigner`、`codesign`、公证、staple 或 Gatekeeper 检查制造“通过”结果。

## 3. 独立验签

Android：

```bash
apksigner verify --verbose --print-certs \
  artifacts/android/1.5.0-13/CourseAxis-1.5.0-13-release.apk
```

证书 SHA-256 必须与上一正式版本和 Release 声明一致。然后在不会覆盖开发者真实数据的测试设备上验证全新安装、覆盖升级、导入、学期隔离、课程/考试编辑、清除范围和 Widget。

macOS：

```bash
APP="artifacts/macos/1.5.0-10/CourseAxis.xcarchive/Products/Applications/CourseAxis.app"
codesign --verify --deep --strict --verbose=2 "$APP"
xcrun stapler validate "$APP"
spctl --assess --type execute --verbose=4 "$APP"
```

在隔离测试账户或备份完成的测试机验证旧数据迁移前后字节哈希、主应用/Widget App Group、登录会话、通知和单进程行为。开发工作区不得直接覆盖当前自用正式安装。

## 4. 生成并复核元数据

```bash
./scripts/generate-release-metadata.sh \
  --output artifacts/release/1.5.0 \
  artifacts/android/1.5.0-13/CourseAxis-1.5.0-13-release.apk \
  artifacts/macos/1.5.0-10/CourseAxis-1.5.0-10-macos.zip
```

复核 `SHA256SUMS` 与两个原始制品的 `shasum -a 256` 输出，并检查 `RELEASE-METADATA.txt` 的 commit、版本与工具链；文件中不得出现个人路径或秘密。由另一终端把公开 Release 的所有附件下载到同一空目录，再执行 `shasum -a 256 -c SHA256SUMS`，不能只检查上传前的本机副本。

## 5. GitHub Release

1. 从已通过保护规则的公开 `main` 创建签名 tag `v1.5.0`。
2. 创建 Draft Release，粘贴对应 `CHANGELOG.md` 内容和明确的未验证项。
3. 附加 Android APK、macOS ZIP、`SHA256SUMS`、`RELEASE-METADATA.txt` 和 Android 证书 SHA-256。
4. 不上传 JKS、证书导出文件、provisioning profile、notary profile、`.env`、课程数据或内部测试报告。
5. 从 Draft 下载附件独立验签；确认 tag、commit、附件哈希与签名完全一致后再公开。

## 6. 发布静态网站

1. GitHub 仓库实际公开后，设置 `VITE_GITHUB_URL` 为已核验的公开 HTTPS 仓库地址。
2. 只有外部赞助说明或支付提供方页面已经由维护者控制并核验隐私政策后，才设置 `VITE_SPONSOR_URL`；未准备好时保持缺失，让页面显示“尚未开放”。
3. 在私有发布环境生成 `site/dist/`，运行 `python3 scripts/validate-site-build.py`，复核页面无分析、广告、Cookie、第三方嵌入、跟踪参数或表单提交，再部署到只提供静态文件和 HTTPS 的托管服务；不得删除 `dist/LICENSE.txt` 或 `dist/THIRD_PARTY_NOTICES.txt`。
4. `.env`、个人收款二维码、支付密钥和商户凭据不得进入 Git、CI artifact 或静态目录。赞助按钮不得附带功能开关、用户标识或课程信息。
5. 部署后从全新浏览器验证 GitHub 跳转、赞助缺省/启用状态、手机布局、隐私文案和外部提供方域名，再把实际网址加入 README。

## 7. 发布后

- 在干净设备上从公开 Release 下载并完成安装验证。
- 观察公开 issue 与 GitHub Security 私密报告，不索取用户账号或真实教务响应。
- 若发现制品、签名、隐私或迁移错误，立即撤下受影响附件并发布安全说明；不得静默替换同名附件。
- 只有公开 Release 和赞助网站真实存在后，README 才能加入对应链接。
