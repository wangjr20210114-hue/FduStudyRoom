# 校园自习室预约系统

前端采用 TypeScript、Vue 3 与 Vite，后端采用 Java、Spring Boot，数据库采用 PostgreSQL。当前已完成前端公共层、登录后端、登录页面及登录后基础布局；预约等业务按 [分步任务清单](docs/04-login-implementation-tasks.md) 逐项实现，每完成一项停止交接。

## 前端运行

准备 Node.js 24 和 npm。进入 frontend 后执行：

```sh
npm ci
npm run dev
```

浏览器访问 http://127.0.0.1:5173/login。端口被占用时会明确报错，不会自动改用其他端口。使用 Ctrl+C 停止服务。

开发环境组件预览：http://127.0.0.1:5173/dev/components 。包含按钮、面板、输入、状态及加载重试；正式构建移除该预览页。组件接口见 [公共组件说明](docs/06-frontend-components.md)。

首次由项目根目录启动时，先执行 cd frontend。npm ci 使用已提交的 package-lock.json，依赖目录 node_modules 不纳入 Git。

登录页及组件预览右上方可切换中英文，选择会保留到下次访问。公共请求与字典用法见 [T03 使用说明](docs/07-frontend-api-and-i18n.md)。

## 当前 Windows 与 WSL 路径

当前项目位于 WSL 的 /opt/new-project，Windows 路径为 \\wsl.localhost\CampusStudyRoom\opt\new-project。Windows 已有 Node.js，WSL 内尚未配置 Linux 版 Node。使用 Windows 工具运行该 UNC 路径时，在 IDE 新建 Command Prompt 或命令提示符终端，执行：

```bat
pushd "\\wsl.localhost\CampusStudyRoom\opt\new-project\frontend"
set CHOKIDAR_USEPOLLING=1
set CHOKIDAR_INTERVAL=300
npm ci
npm run dev
```

pushd 临时映射目录，避免 Windows npm 脚本的 UNC 工作目录限制。两项 CHOKIDAR 设置让 Windows 通过轮询监听 WSL 文件，修复跨文件系统的监控错误；只影响当前终端。停止服务后可执行 popd 返回原目录。

若后续切换到 WSL 开发，先配置 Linux 版 Node.js，再在该环境安装依赖；不要在同一份 node_modules 中混用 Windows 与 Linux 的原生构建工具。macOS 在项目 frontend 目录使用常规 npm 命令。

## 验证与打包

```sh
npm test
npm run typecheck
npm run build
npm run preview
```

build 自带类型检查并生成 dist；preview 在 http://127.0.0.1:4173 预览产物。Node.js 与 npm 的实际验证版本、构建结果和当前停点见 [任务交接](docs/04-login-implementation-tasks.md)。macOS 运行步骤已提供，待在实际 macOS 环境验收。

## 登录后端

T04 已实现资源服务的身份接口、角色与权限校验、Cookie/CSRF 和 PostgreSQL 身份表迁移。使用 JDK 21 与 backend 内的 Maven Wrapper 构建，启动方式见 [登录后端说明](docs/08-login-backend.md)。正式复旦 UIS 仍等待校方配置；本地开发身份会明确标记。

## 登录页面

T05 已接入认证配置、CSRF、开发登录、当前用户和退出。先启动登录后端，再访问 http://127.0.0.1:5173/login；本地开发可选择学生、管理员和维护人员，页面明确标记演示身份。后端未启动时显示服务错误。登录成功后进入空工作台，用户菜单提供个人信息和退出；具有相应权限时显示账号管理预留入口。登录页已精简，视觉设计后续再做。

Vite 默认将 /api 代理至 http://127.0.0.1:8080。需要修改后端端口时，将 frontend/.env.example 复制为 frontend/.env.local，调整 CAMPUS_API_TARGET 并重启 Vite。具体行为与验证见 [登录前端说明](docs/09-login-frontend.md)。

## 文档与协作

- [项目目录与模块归属](docs/01-project-structure.md)
- [数据库设计](docs/02-database-design.md)
- [前端风格与复旦认证计划](docs/03-ui-and-auth-plan.md)
- [分步任务与交接](docs/04-login-implementation-tasks.md)
- [CodeArts IDE 与 Gitee 操作指南](docs/05-codearts-git-guide.md)
- [公共样式与组件用法](docs/06-frontend-components.md)
- [公共请求与国际化](docs/07-frontend-api-and-i18n.md)
- [登录后端与运行说明](docs/08-login-backend.md)
- [登录前端与接口联调](docs/09-login-frontend.md)
- [T06 导航、工作台与访问控制](docs/10-workspace-and-navigation.md)

使用 CodeArts IDE 或码道 IDE 的内置 Git 面板查看差异、暂存和提交，再推送到 Gitee 的 FduStudyRoom 项目仓库。正式账号、密钥及本机 IDE 配置不提交。复旦认证接入依赖校方批准的参数，开发身份不会作为正式认证验收结果。
