# CourseAxis 网站

这是 CourseAxis 的静态产品与自愿赞助页面。网站本身不包含分析、广告、Cookie、账号、支付 SDK 或表单后端，不接收或保存支付信息。

## 本地验证

使用 `.node-version` 指定的 Node.js 26：

```bash
npm ci --ignore-scripts
npm test
npm run lint
npm run build
python3 ../scripts/validate-site-build.py --dist dist
npm audit --audit-level=high
```

## 发布配置

复制 `.env.example` 为发布环境的私有配置，只设置维护者已经核验的 HTTPS 地址：

- `VITE_GITHUB_URL`：公开 CourseAxis 仓库；
- `VITE_SPONSOR_URL`：由维护者控制的外部赞助说明或支付提供方页面。

缺少配置或 URL 不是 HTTPS 时，对应按钮只显示“尚未开放”对话框，不跳转到占位地址。只把生产构建生成的 `dist/` 交给静态托管服务，并保留其中的 `LICENSE.txt` 和 `THIRD_PARTY_NOTICES.txt`；不要把 `.env`、`node_modules/`、`.npm-cache/`、个人收款二维码、支付密钥、商户凭据或 `private-config.*` 提交到仓库或上传到静态目录。

赞助完全自愿，不解锁 CourseAxis 功能、服务、优先级、支持时效或学校官方身份。项目与学校及教务厂商没有官方隶属关系。
