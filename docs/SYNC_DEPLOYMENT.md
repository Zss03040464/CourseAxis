# 自托管同步服务

## 1. 协议

健康检查：

```http
GET /healthz
```

读取：

```http
GET /v1/document
Authorization 请求头使用 Bearer 方案和客户端令牌
```

返回：

```json
{"revision": 0}
```

或：

```json
{"revision": 4, "document": {"schemaVersion": 1}}
```

写入：

```http
PUT /v1/document
Authorization 请求头使用 Bearer 方案和客户端令牌
Content-Type: application/json
```

```json
{
  "baseRevision": 4,
  "document": {"schemaVersion": 1}
}
```

若 revision 已变化，返回 `409 revision conflict`。客户端会重新拉取、合并并重试。

## 2. Docker Compose

```bash
cd server
cat > .env <<EOF2
COURSEAXIS_TOKEN=$(openssl rand -hex 32)
EOF2
chmod 600 .env
docker compose up -d --build
docker compose logs -f
```

Compose 默认只监听宿主机 `127.0.0.1:8787`，避免未经反向代理直接暴露公网。

## 3. HTTPS 反向代理

Caddy 示例：

```caddyfile
schedule.example.com {
    reverse_proxy 127.0.0.1:8787
}
```

Nginx 示例：

```nginx
server {
    listen 443 ssl http2;
    server_name schedule.example.com;

    ssl_certificate     /path/fullchain.pem;
    ssl_certificate_key /path/privkey.pem;

    client_max_body_size 4m;
    location / {
        proxy_pass http://127.0.0.1:8787;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto https;
    }
}
```

服务本身不终止 TLS。客户端拒绝公网明文 HTTP。

## 4. 备份与恢复

数据文件：

```text
server/data/document.json
```

备份：

```bash
cd server
tar -czf "courseaxis-sync-$(date +%F-%H%M%S).tar.gz" data/
```

恢复：

```bash
docker compose down
mv data data.before-restore
mkdir data
# 将备份中的 document.json 放入 data/
chmod 700 data
chmod 600 data/document.json
docker compose up -d
```

验证：

```bash
curl -fsS http://127.0.0.1:8787/healthz
AUTH_SCHEME=Bearer
curl -fsS -H "Authorization: ${AUTH_SCHEME} ${COURSEAXIS_TOKEN}" \
  http://127.0.0.1:8787/v1/document
```

## 5. 滚动令牌

服务只接受一个令牌。安全轮换步骤：

1. 备份 `document.json`。
2. 停止服务。
3. 修改 `.env` 中令牌并启动。
4. 依次更新 Android 与 macOS 设置。
5. 两端执行“保存并同步”。

旧令牌立即失效。课程数据不受影响。

## 6. 风险与边界

- 服务是单用户、单文档设计，不提供用户注册、多租户或权限分级。
- Android 令牌使用 Android Keystore AES-256-GCM 密钥保护，macOS 令牌保存在 Keychain；已解锁或失陷设备上的账户安全仍然重要。
- 同步文档不做端到端加密，服务器管理员、主机系统和备份能够读取课程 JSON。
- 服务限制请求体为 4 MiB，课程数据通常远小于此值。
- 服务器只做结构版本检查，完整业务校验由客户端 Schema/模型完成。
- 不要将 8787 端口直接映射到公网；使用 HTTPS 反向代理和主机防火墙。
