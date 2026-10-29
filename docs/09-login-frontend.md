# 登录前端使用与交接

更新：2026-10-29。T07 已按用户决定改为本系统账号密码登录，允许注册。当前规则与完整验证见 [T07 登录注册](12-password-login.md)；T05/T06 的历史记录保留在 [任务交接](04-login-implementation-tasks.md)。T08 的三种角色菜单与页面路径见 [角色导航](13-role-navigation.md)。

| 地址 | 功能 |
| --- | --- |
| `/login` | 输入账号、密码，登录后进入允许的返回地址 |
| `/register` | 账号、密码及确认密码；固定注册为普通用户，成功后自动登录 |
| `/workspace` | 当前角色可访问的页面入口 |
| `/account` | 当前账号、名称和角色 |
| `/system/users` | 有管理权限时显示预留页 |

登录和注册复用 `modules/auth/pages/LoginPage.vue`；状态、会话确认和请求取消由 `model/login-flow.ts` 管理，API 位于 `api/auth.ts`，文案位于公共中英文字典。公共 `AppTextField` 支持密码输入、长度和格式约束。页面仅保留表单、语言切换、必要提示和入口链接，视觉设计后续进行。

先按 [后端说明](08-login-backend.md) 启动 PostgreSQL 和 local 模式资源服务，再进入 `frontend`：

```sh
npm ci
npm run dev
```

访问 http://127.0.0.1:5173/login ，也可直接注册。Windows 当前 UNC 路径的命令见 [README](../README.md)。

Vite 默认将同站点 `/api` 代理到 `http://127.0.0.1:8080`。后端端口不同时，将 `frontend/.env.example` 复制为 `frontend/.env.local`，调整 `CAMPUS_API_TARGET` 后重启 Vite。变量只供 Vite 进程使用，不使用 `VITE_` 前缀，不能在前端配置数据库密码或后端密钥。

每次写请求先获取 CSRF，再提交账号密码；登录/注册后通过 `GET /api/auth/me` 确认会话。处理中禁用重复提交，结束后清空密码。密码不存入浏览器持久存储；HttpOnly 登录 Cookie 仅由后端管理，localStorage 只保存语言选择。

导航切换时保留已确认的布局和当前页面，重新检查身份成功后切换目标内容；失败时清除身份及受保护内容。首次加载、标签页恢复等重新检查仍隐藏旧内容；服务故障显示错误和重试，401/403 返回登录；退出失败保留当前身份供重试。角色与权限来自后端，导航、路由和返回地址同时检查权限。管理员和维护人员也使用自己的本地密码登录。

T07 检查记录：74 项前端测试、类型检查、正式构建及 33 项真实后端接口检查通过；真实组件渲染验证了输入长度属性。本次浏览器自动化导航中断，未完成修复后页面回归，详见 T07 记录。角色导航与动画修复的最新验证见 [T08 说明](13-role-navigation.md)。Windows UNC 路径的 npm/Maven 检查应依次执行，避免临时盘符被另一进程退出时移除。macOS 尚未实跑。开发服务器和 `vite preview` 用于本地调试；正式环境需同域 HTTPS 代理与 History 路由回退，后续部署任务继续完成。
