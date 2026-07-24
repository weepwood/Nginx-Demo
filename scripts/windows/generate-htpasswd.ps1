param(
    [string]$Username = "mrr-image",
    [string]$OutputFile = "C:\nginx\conf\image.htpasswd"
)

$ErrorActionPreference = "Stop"
$command = Get-Command htpasswd.exe -ErrorAction SilentlyContinue
if (-not $command) {
    throw "未找到 htpasswd.exe。请安装 Apache HTTP Server 工具，或使用 Docker 运行本项目。"
}
$directory = Split-Path -Parent $OutputFile
if ($directory -and -not (Test-Path $directory)) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
}
& $command.Source -c -B $OutputFile $Username
if ($LASTEXITCODE -ne 0) {
    throw "htpasswd 生成失败，退出码：$LASTEXITCODE"
}
Write-Host "已生成：$OutputFile"
Write-Host "请将相同用户名和明文密码配置到 IMAGE_USERNAME / IMAGE_PASSWORD。"
