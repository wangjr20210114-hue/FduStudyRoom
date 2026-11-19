@echo off
setlocal EnableExtensions
chcp 65001 >nul
set PYTHONUTF8=1
set "PATH=%LOCALAPPDATA%\Programs\Python\Python313;%ProgramFiles%\Python313;%ProgramFiles%\Git\cmd;%ProgramFiles%\GitHub CLI;%ProgramFiles%\Docker\Docker\resources\bin;%PATH%"
python -c "import sys;sys.exit(sys.version_info < (3,12))" >nul 2>nul
if errorlevel 1 (
  call :install Python.Python.3.13
  if errorlevel 1 goto fail
)
for %%G in (git gh docker) do (
  call :ensure_%%G
  if errorlevel 1 goto fail
)
docker info --format "{{.ServerVersion}}" >nul 2>nul
if not errorlevel 1 goto ready
if exist "%ProgramFiles%\Docker\Docker\Docker Desktop.exe" start "" "%ProgramFiles%\Docker\Docker\Docker Desktop.exe"
echo 等待 Docker Desktop。请完成 Windows 或 WSL 的首次授权。
for /l %%T in (1,1,60) do (
  docker info --format "{{.ServerVersion}}" >nul 2>nul && goto ready
  timeout /t 3 /nobreak >nul
)
goto fail
:ready
python -X utf8 "%~dp0join.py"
if errorlevel 1 goto fail
exit /b 0
:ensure_git
where git >nul 2>nul && exit /b 0
call :install Git.Git
exit /b %errorlevel%
:ensure_gh
where gh >nul 2>nul && exit /b 0
call :install GitHub.cli
exit /b %errorlevel%
:ensure_docker
where docker >nul 2>nul && exit /b 0
call :install Docker.DockerDesktop
exit /b %errorlevel%
:install
where winget >nul 2>nul || (echo 请从 Microsoft Store 安装“应用安装程序”后再运行。& exit /b 1)
winget install --id %1 --exact --source winget --accept-source-agreements --accept-package-agreements
exit /b %errorlevel%
:fail
echo 启动未完成。如系统要求重启，请重启后再打开本文件。
pause
exit /b 1
