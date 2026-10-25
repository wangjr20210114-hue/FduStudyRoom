# 校园自习室预约系统

前端采用 TypeScript、Vue 3 与 Vite，后端计划采用 Java、Spring Boot，数据库采用 PostgreSQL。当前已完成前端运行骨架、公共主题与组件、公共请求层和中英文切换；登录及其他业务按 [分步任务清单](docs/04-login-implementation-tasks.md) 逐项实现，每完成一项停止交接。

## 前端运行

准备 Node.js 24 和 npm。进入 frontend 后执行：

```sh
npm ci
npm run dev
```

浏览器访问 http://127.0.0.1:5173。端口被占用时会明确报错，不会自动改用其他端口。使用 Ctrl+C 停止服务。

开发环境组件预览：http://127.0.0.1:5173/dev/components 。包含按钮、面板、输入、状态及加载重试；正式构建移除该预览页。组件接口见 [公共组件说明](docs/06-frontend-components.md)。

首次由项目根目录启动时，先执行 cd frontend。npm ci 使用已提交的 package-lock.json，依赖目录 node_modules 不纳入 Git。

首页及组件预览右上方可切换中英文，选择会保留到下次访问。公共请求与字典用法见 [T03 使用说明](docs/07-frontend-api-and-i18n.md)。

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

## 文档与协作

- [项目目录与模块归属](docs/01-project-structure.md)
- [数据库设计](docs/02-database-design.md)
- [前端风格与复旦认证计划](docs/03-ui-and-auth-plan.md)
- [分步任务与交接](docs/04-login-implementation-tasks.md)
- [CodeArts IDE 与 Gitee 操作指南](docs/05-codearts-git-guide.md)
- [公共样式与组件用法](docs/06-frontend-components.md)
- [公共请求与国际化](docs/07-frontend-api-and-i18n.md)

使用 CodeArts IDE 或码道 IDE 的内置 Git 面板查看差异、暂存和提交，再推送到 Gitee 的 FduStudyRoom 项目仓库。正式账号、密钥及本机 IDE 配置不提交。复旦认证接入依赖校方批准的参数，开发身份不会作为正式认证验收结果。
