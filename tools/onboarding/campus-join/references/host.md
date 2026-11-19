# 首次准备与恢复

仅由技能执行下面的探测和安装。复用已可用的工具。需要系统授权时，打开相应图形界面，告知成员要确认的事项。工具路径含空格时按独立参数传递。在后续工具进程中沿用已验证的 PATH 和 Python 绝对路径。

## Windows

1. 用 PowerShell 的 Get-Command 查找 Python、Git、gh、docker 和 winget。验证 Python 能运行且版本至少 3.12，避开 Microsoft Store 的占位程序。也可用 py 启动器定位合适的解释器。
2. 仅对缺失项使用 WinGet，从 winget 源按精确 ID 安装。对应 ID 为 Python.Python.3.13、Git.Git、GitHub.cli、Docker.DockerDesktop。调用参数为 `install --id <ID> --exact --source winget --accept-source-agreements --accept-package-agreements`。安装后重读用户及系统 PATH，检查工具绝对路径。若缺少 WinGet，打开 Microsoft Store 的“应用安装程序”安装页，完成后重查。
3. 检查 Docker Engine 和 Compose。已使用 Docker Desktop 的机器可由技能启动 Docker Desktop；首次启用 WSL、虚拟化或需要重启时，展示系统给出的具体步骤。重试前等待引擎就绪。项目要求服务端 Engine 28 或以上。

## macOS

1. 查找可用的 Python 3.12+、Git、gh 和 Docker。Homebrew 默认路径包括 /opt/homebrew/bin 和 /usr/local/bin；Docker Desktop 的 CLI 也可能位于 /Applications/Docker.app/Contents/Resources/bin。Git 缺少 Apple 命令行工具时，由技能调用 `xcode-select --install` 打开安装对话框。
2. 已有 Homebrew 时，仅安装缺少的 gh、python@3.13 或 docker-desktop（cask）。通过 brew --prefix 取得解释器位置并验证版本。Apple Silicon 缺少 Homebrew 时，可从官方 Homebrew/brew Releases 获取 macOS 安装包，经系统签名检查后打开安装器。Intel Mac 若缺少 Homebrew，可分别使用 python.org 的 macOS 安装包、cli.github.com 对应架构的 gh 发行包和 Docker 官方 Intel 安装器。由助手下载并核验官方签名或发布摘要后安装；系统密码只在系统授权窗口输入。[Homebrew 安装说明](https://docs.brew.sh/Installation)
3. 通过 `open -a Docker` 启动已安装的 Docker Desktop。限定时间轮询 Docker Engine 和 Compose，遇到许可或权限提示时等待成员在系统窗口确认，完成后重新探测。

## 账号与权限

GitHub CLI 的浏览器设备授权由本人完成。已登录但 GitHub 暂时不可达时，先诊断连接。成员包和项目内的绑定文件含个人缓冲站凭据，助手使用随包工具读取。项目准备通过后，日常开发、测试、交付沿用当前 IDE 登录会话。[GitHub 浏览器授权说明](https://cli.github.com/manual/gh_auth_login)
