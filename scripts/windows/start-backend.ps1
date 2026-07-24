param(
    [string]$JarPath = ".\target\nginx-demo-1.0.0.jar",
    [string]$ImageBaseUrl = "http://127.0.0.1:8081/images/"
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path $JarPath)) {
    throw "未找到 JAR：$JarPath。请先执行 mvn clean package。"
}
if (-not $env:IMAGE_USERNAME) { $env:IMAGE_USERNAME = "mrr-image" }
if (-not $env:IMAGE_PASSWORD) { $env:IMAGE_PASSWORD = "change-me" }
if (-not $env:APP_AUTH_USERNAME) { $env:APP_AUTH_USERNAME = "demo" }
if (-not $env:APP_AUTH_PASSWORD) { $env:APP_AUTH_PASSWORD = "demo-pass" }
if (-not $env:APP_AUTH_TOKEN) { $env:APP_AUTH_TOKEN = "demo-token-change-me" }
$env:IMAGE_BASE_URL = $ImageBaseUrl
$env:SERVER_PORT = "8002"
java -jar $JarPath
