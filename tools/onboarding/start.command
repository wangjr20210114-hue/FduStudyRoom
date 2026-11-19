#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH" PYTHONUTF8=1
if ! xcode-select -p >/dev/null 2>&1; then
  xcode-select --install
  echo "安装 Apple 命令行工具后，再次打开 start.command。"
  exit 1
fi
if ! command -v brew >/dev/null; then
  install_script=$(mktemp)
  trap 'rm -f "$install_script"' EXIT
  curl --proto '=https' -fsSL --retry 2 'https://raw.githubusercontent.com/Homebrew/install/eaa2af62ca1d5349c9743f82b1b0c4e5d365fb2d/install.sh' -o "$install_script"
  echo "8fd2f1a5287a7c2e3d7362924c677edcc04c5a0e16448c77132d056bc88cf1ed  $install_script" | shasum -a 256 -c -
  /bin/bash "$install_script"
fi
command -v gh >/dev/null || brew install gh
if python3 -c 'import sys;sys.exit(sys.version_info < (3,12))' 2>/dev/null; then
  py=$(command -v python3)
else
  brew install python@3.13
  py="$(brew --prefix python@3.13)/bin/python3.13"
fi
if ! command -v docker >/dev/null && [ ! -d /Applications/Docker.app ]; then brew install --cask docker-desktop; fi
export PATH="/Applications/Docker.app/Contents/Resources/bin:$PATH"
if ! docker info >/dev/null 2>&1; then
  open -a Docker
  echo "等待 Docker Desktop 启动，请完成系统弹出的首次授权。"
  for (( i=0; i<60; i++ )); do docker info >/dev/null 2>&1 && break; sleep 3; done
  docker info >/dev/null || { echo "Docker 尚未就绪。完成系统提示后再次运行。"; exit 1; }
fi
exec "$py" -X utf8 join.py
