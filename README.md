# Nginx-Demo

一个可直接运行的完整 Demo，用于演示：

- Nginx 使用 HTTP Basic Auth 保护静态图片与目录索引；
- Spring Boot 后端保存 Nginx 凭据并代理目录、图片请求；
- 浏览器只访问后端，不接触 Nginx 用户名、密码和内部地址；
- 前端携带 Bearer Token，通过 Fetch 获取 Blob，再生成临时 `blob:` URL；
- 支持 Nginx `autoindex_format json`、图片流、Range 请求和下载；
- 提供 Docker Compose、Windows Nginx 示例、测试和 GitHub Actions。

> 本项目中的默认密码只用于公开演示。任何真实环境都必须覆盖默认凭据。

## 架构

```text
浏览器
  │ Authorization: Bearer <应用令牌>
  ▼
Spring Boot :8002
  │ Authorization: Basic base64(IMAGE_USERNAME:IMAGE_PASSWORD)
  ▼
Nginx :8081 /images/
  │
  ▼
受保护的静态资源目录
```

关键点是浏览器不会直接访问受保护的 Nginx。图片由前端调用后端接口获取 Blob，再显示为 `blob:` URL。

## 一键运行

前提：安装 Docker Desktop 或 Docker Engine，并启用 Compose。

```bash
docker compose up --build
```

打开：

```text
http://localhost:8002
```

默认 Demo 后端账号：

```text
用户名：demo
密码：demo-pass
```

默认 Nginx 内部账号：

```text
用户名：mrr-image
密码：change-me
```

验证 Nginx 确实受到保护：

```bash
# 未认证，应返回 401
curl -i http://localhost:8081/images/

# 正确认证，应返回 JSON 目录索引
curl -u mrr-image:change-me http://localhost:8081/images/
```

停止服务：

```bash
docker compose down
```

## 使用环境变量覆盖密码

复制示例文件：

```bash
cp .env.example .env
```

修改 `.env`：

```dotenv
IMAGE_USERNAME=mrr-image
IMAGE_PASSWORD=一个强密码
APP_AUTH_USERNAME=demo
APP_AUTH_PASSWORD=另一个强密码
APP_AUTH_TOKEN=至少32位随机字符串
```

同时需要重新生成 `nginx/.htpasswd`，确保 Nginx 中的账号密码与 `IMAGE_USERNAME`、`IMAGE_PASSWORD` 完全一致。

Linux：

```bash
./scripts/linux/generate-htpasswd.sh mrr-image ./nginx/.htpasswd
```

Windows PowerShell：

```powershell
.\scripts\windows\generate-htpasswd.ps1 `
  -Username "mrr-image" `
  -OutputFile "C:\nginx\conf\image.htpasswd"
```

## 原生 Windows 部署

### 1. 配置 Nginx

参考 `nginx/windows-images.conf.example`：

```nginx
location /images/ {
    alias D:/MRR/images/;
    autoindex on;
    autoindex_format json;
    auth_basic "MRR Image Server";
    auth_basic_user_file C:/nginx/conf/image.htpasswd;
    allow 127.0.0.1;
    deny all;
}
```

检查并重载：

```powershell
cd C:\nginx
.\nginx.exe -t
.\nginx.exe -s reload
```

### 2. 打包后端

需要 Java 21 和 Maven 3.9+：

```powershell
mvn clean package
```

### 3. 启动后端

```powershell
$env:IMAGE_BASE_URL = "http://127.0.0.1:8081/images/"
$env:IMAGE_USERNAME = "mrr-image"
$env:IMAGE_PASSWORD = "你的密码"
$env:APP_AUTH_USERNAME = "demo"
$env:APP_AUTH_PASSWORD = "你的应用登录密码"
$env:APP_AUTH_TOKEN = "你的随机令牌"
java -jar .\target\nginx-demo-1.0.0.jar
```

或使用：

```powershell
.\scripts\windows\start-backend.ps1
```

## API

### 登录

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "demo",
  "password": "demo-pass"
}
```

### 浏览目录

```http
GET /api/images?path=cases/0001/
Authorization: Bearer demo-token-change-me
```

### 获取图片

```http
GET /api/images/content?path=cases/0001/medical-record.svg
Authorization: Bearer demo-token-change-me
```

支持合法的 Range 请求：

```http
Range: bytes=0-1023
```

### 检查上游图片服务

```http
GET /api/images/health
Authorization: Bearer demo-token-change-me
```

## 与 MRR 的配置对应关系

本 Demo 的环境变量与 MRR 的配置可以直接对应：

```properties
image.username=${IMAGE_USERNAME:}
image.password=${IMAGE_PASSWORD:}
image.server-url-default=${IMAGE_SERVER_URL_DEFAULT:}
```

在 MRR 中建议增加按扫描记录 ID 获取图片流的接口，例如：

```text
GET /api/v1/img/content/{id}
```

后端根据数据库记录构建受控图片路径，然后使用这里演示的 Basic Auth 客户端读取 Nginx。前端通过 Axios 获取 Blob，不再把真实 Nginx URL 放进 `<img src>`。

## 安全设计

当前实现包含以下边界：

- `image.base-url` 只允许 HTTP/HTTPS 完整 URL；
- 禁止在 `image.base-url` 中写入用户名、密码、查询参数和片段；
- 拒绝 `..`、绝对路径、Windows 盘符、反斜杠、控制字符等危险路径；
- 最终 URL 必须保持在配置的同源根目录下；
- 不跟随 Nginx 重定向，避免跳转到未受信任地址；
- 前端看不到 Nginx Basic Auth 凭据；
- Nginx 可以通过 `allow/deny` 仅允许后端服务器访问；
- 图片响应使用私有缓存策略并设置 `nosniff`。

生产环境还应补充：

- 使用正式 JWT、Session 或统一身份认证替换固定 Demo Token；
- 对每次病案和图片访问记录审计日志；
- 使用 HTTPS，避免 Basic Auth 在网络中被窃听；
- 根据用户权限验证其是否能够访问目标病案，而不是只验证图片 ID；
- 设置并发、带宽、单文件大小和请求超时限制；
- 不要把真实医疗影像、密码文件或 `.env` 提交到 Git。

## 项目结构

```text
.
├─ demo-images/                     # 公开示例资源
├─ nginx/
│  ├─ default.conf                  # Docker Nginx 配置
│  ├─ .htpasswd                     # 公开演示密码文件
│  └─ windows-images.conf.example   # Windows 配置示例
├─ scripts/
│  ├─ linux/
│  └─ windows/
├─ src/main/java/                   # Spring Boot 后端
├─ src/main/resources/static/       # 无构建步骤的演示前端
├─ src/test/                        # 路径安全测试
├─ docker-compose.yml
├─ Dockerfile
└─ pom.xml
```

## 测试

```bash
mvn clean verify
```

GitHub Actions 会执行 Maven 测试、打包并验证 Docker 镜像构建。

## 说明

固定 Token 鉴权是为了让 Demo 足够小并清楚展示“前端 Bearer Token”和“后端 Nginx Basic Auth”是两套独立凭据。不要直接把该鉴权实现作为生产身份系统使用。
