# 校园自习室预约系统

前端采用 TypeScript、Vue 3 与 Vite，后端采用 Java、Spring Boot，数据库采用 PostgreSQL。当前已完成前端公共层、账号登录注册、三种角色导航和页面切换；历史登录开发记录见 [分步任务清单](docs/04-login-implementation-tasks.md)，后续团队开发使用下方统一工作流。

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

当前采用本系统账号密码登录，允许注册，注册账号固定为普通用户。三种角色继续保留，管理员和维护人员由管理员授权；不再接入学校身份认证。后端支持密码哈希、Cookie/CSRF、权限校验及首个管理员初始化。使用 JDK 21 与 backend 内 Maven Wrapper 构建，命令见 [后端运行](docs/08-login-backend.md)。

## 登录页面

先启动登录后端，再访问 http://127.0.0.1:5173/login；注册入口为 http://127.0.0.1:5173/register。登录后根据普通用户、系统管理员或维护人员身份显示侧栏和工作台入口；点击菜单切换页面，用户菜单提供个人信息和退出。业务页面只保留标题和“暂未开放”，入口与路径见 [角色导航](docs/13-role-navigation.md)。页面只保留必要文字，视觉设计后续进行。功能、迁移和验证见 [T07 普通登录](docs/12-password-login.md)。

Vite 默认将 /api 代理至 http://127.0.0.1:8080。需要修改后端端口时，将 frontend/.env.example 复制为 frontend/.env.local，调整 CAMPUS_API_TARGET 并重启 Vite。具体行为与验证见 [登录前端说明](docs/09-login-frontend.md)。

## 文档与协作

- [项目目录与模块归属](docs/01-project-structure.md)
- [数据库设计](docs/02-database-design.md)
- [前端风格与历史认证计划](docs/03-ui-and-auth-plan.md)
- [分步任务与交接](docs/04-login-implementation-tasks.md)
- [CodeArts IDE 与 Gitee 操作指南](docs/05-codearts-git-guide.md)
- [公共样式与组件用法](docs/06-frontend-components.md)
- [公共请求与国际化](docs/07-frontend-api-and-i18n.md)
- [登录后端与运行说明](docs/08-login-backend.md)
- [登录前端与接口联调](docs/09-login-frontend.md)
- [T06 导航、工作台与访问控制](docs/10-workspace-and-navigation.md)
- [UIS 与旦挞历史调研](docs/11-uis-and-danxi-research.md)
- [T07 普通账号登录与注册](docs/12-password-login.md)
- [T08 角色导航与页面切换](docs/13-role-navigation.md)

使用 CodeArts IDE 或码道 IDE 的内置 Git 面板查看差异、暂存和提交，再推送到 Gitee 的 FduStudyRoom 项目仓库。正式账号、密钥及本机 IDE 配置不提交。本地账号不证明复旦身份，真实密码和初始化凭据不纳入 Git。

## 团队技能与协作缓冲站

首次入组请解压组长发给自己的 a–f 成员包。组员可以自行登记 GitHub 用户名和安装环境，步骤见[六人入组与交付](docs/22-team-onboarding.md)。

业务功能与关系图见 [业务需求说明](docs/17-spec-topology-and-assignment.md)，具体场景见 [业务Spec索引](spec/README.md)。分工、环境、测试、报告和交付管理见 [workflow要求](docs/18-workflow-obligations.md)。这些仍是需求规划，不自动改变成员权限。

六模块分工、机器可读需求、TDD边界、环境启动和72小时同步流程见 [团队工作流](docs/14-team-workflow.md)，完整需求与外部阻塞见 [需求基线](docs/15-requirements-baseline.md)。Windows入口为 scripts/build.ps1，macOS入口为 sh scripts/build.sh；缓冲站使用 -Buffer / --buffer。

五个skill位于 skills/，统一入口是 `$campus-workflow 我的身份是 frontend-a，处理未完成需求`。环境、需求分解、测试设计和限界TDD按检查点推进，支持中断恢复。借鉴Harmony及本轮验证见 [全流程说明](docs/16-workflow-reference.md)。协调人先初始化真实成员和访问码，成员再只提供身份与需求状态；公共代码与锁文件保持冻结，模块路由、字典、测试和迁移各自维护。

缓冲站默认仅本机 http://127.0.0.1:7780 ，专用PostgreSQL与业务数据分离。它自动接收diff草稿，人工审核和独立CI通过后才生成正式提交；72小时任务发布功能分支和MR，不直推main。首次需配置可信CI镜像、baseline和服务端Gitee凭据后启用同步，详见工作流说明。

缓冲站的服务器部署、资源限额和 GitHub/Gitee 交付分流见 [部署说明](docs/19-buffer-server-deployment.md)。
