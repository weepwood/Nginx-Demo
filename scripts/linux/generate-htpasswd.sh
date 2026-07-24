#!/usr/bin/env sh
set -eu

USERNAME="${1:-mrr-image}"
OUTPUT_FILE="${2:-./nginx/.htpasswd}"

if ! command -v htpasswd >/dev/null 2>&1; then
  echo "未找到 htpasswd。Debian/Ubuntu 可安装 apache2-utils。" >&2
  exit 1
fi
mkdir -p "$(dirname "$OUTPUT_FILE")"
htpasswd -cB "$OUTPUT_FILE" "$USERNAME"
echo "已生成：$OUTPUT_FILE"
echo "请将相同用户名和明文密码配置到 IMAGE_USERNAME / IMAGE_PASSWORD。"
