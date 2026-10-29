# T08 角色导航与页面切换

更新：2026-10-29。按用户新要求，T08 实现角色导航和页面切换；原“完整登录流程验收”顺延为 T09。当前已完成并停止交接。用户已授权本阶段与 T07 登录注册改动一并提交和推送，提交记录以 Git 日志为准。

## 角色与入口

| 角色 | 导航 |
| --- | --- |
| 普通用户 USER | 工作台、座位预约、我的预约、教室申请、我的报修、消息 |
| 系统管理员 SYSTEM_ADMIN | 工作台、教室与座位、预约管理、教室审批、报修管理、账号管理、系统设置、运营统计、审计日志、消息 |
| 维护人员 MAINTAINER | 工作台、维修任务、维修记录、消息 |

三种角色都可以通过用户菜单访问个人信息和退出登录。页面切换使用导航链接；角色来自后端当前用户接口，切换账号需退出后重新登录。账号管理入口继续按 `identity:users:read` 授权，已获得此权限的自定义角色也可访问。

业务页面目前只显示标题和“暂未开放”，预约、申请、审批、维修等操作由后续模块接入。工作台展示当前账号可访问的入口。页面不展示虚构的统计或业务记录。

## 页面路径

| 页面 | 路径 | 默认角色 |
| --- | --- | --- |
| 工作台 | /workspace | 全部 |
| 座位预约 | /rooms | USER |
| 我的预约 | /reservations | USER |
| 教室申请 | /room-requests | USER |
| 我的报修 | /repairs | USER |
| 教室与座位 | /manage/rooms | SYSTEM_ADMIN |
| 预约管理 | /manage/reservations | SYSTEM_ADMIN |
| 教室审批 | /manage/approvals | SYSTEM_ADMIN |
| 报修管理 | /manage/repairs | SYSTEM_ADMIN |
| 账号管理 | /system/users | 具有 identity:users:read 权限的账号 |
| 系统设置 | /system/settings | SYSTEM_ADMIN |
| 运营统计 | /statistics | SYSTEM_ADMIN |
| 审计日志 | /audit | SYSTEM_ADMIN |
| 维修任务 | /maintenance/tasks | MAINTAINER |
| 维修记录 | /maintenance/history | MAINTAINER |
| 消息 | /notifications | 全部 |
| 个人信息 | /account | 全部 |

“全部”仍要求已登录且拥有基础身份读取权限。业务骨架按默认角色归类，同时检查基础权限；实际业务操作授权随相应接口实现。

## 导航与切换

- 桌面显示侧栏，当前页面对应菜单高亮。
- 手机端提供“导航”按钮；切换页面后关闭菜单，Esc 可收起并返回按钮焦点。
- 工作台快捷入口与侧栏来自同一套路由定义，随当前身份变化。
- 未登录访问页面返回登录并保留允许的目标；不符合角色或权限时进入无权限页。
- 路由跳转、标签页恢复和会话重新检查都采用相同的访问条件。
- 页面标题、菜单和角色名称使用中英文字典。

## 导航流畅性修复

原先每次导航都会清除当前身份，并用整页加载画面替换 RouterView，造成侧栏和页面反复卸载。现在导航仍等待服务器重新确认身份，但检查期间保留已确认的当前页面；成功后才进入目标页面，失败时清除身份并返回登录。首次加载和标签页恢复的隐藏策略保留。

AppShell 的内容区使用 Vue Transition，页面以 160ms 淡入淡出并轻微上下位移；新旧内容在同一网格区域交替，侧栏和顶栏保持原位，菜单高亮有颜色过渡。不同路径使用独立 key，保证共用骨架组件的页面之间也触发切换；没有引入页面缓存或动画依赖。

连续点击导航共享正在进行的身份检查，并以最后一个目标为准。切换完成后焦点进入新页面标题；开启系统“减少动态效果”时取消位移并缩短过渡。

## 文件与归属

以下路径相对 `frontend/src`。

| 文件 | 功能 | 归属 |
| --- | --- | --- |
| app/layouts/AppShell.vue | 角色标识、侧栏、手机菜单、用户菜单、内容过渡和切换后的标题焦点 | 前端 B |
| app/pages/WorkspacePage.vue | 当前账号的工作台入口 | 前端 B |
| app/router/pages.ts | 汇总各模块路由 | 前端 B |
| app/router/index.ts、meta.d.ts | 注册路由、角色与权限守卫、路由元数据 | 前端 B |
| app/router/navigation.ts | 菜单筛选、页面访问条件与安全返回地址 | 前端 B |
| app/App.vue | 标签页恢复时重新检查角色访问条件、导航期间保留布局及页面标题 |
| modules/auth/model/login-flow.ts | 导航检查时保留已确认身份，失败时清除身份与受保护内容 | 前端 B |
| shared/navigation/types.ts、feature-page.ts | 模块路由类型和骨架页工厂 | 公共层 |
| shared/pages/FeaturePage.vue | 简洁业务页面骨架 | 公共层 |
| modules/resource/routes.ts | 自习室、教室与座位入口 | 前端 A |
| modules/reservation/routes.ts | 本人预约与预约管理入口 | 前端 A |
| modules/approval/routes.ts | 教室申请与审批入口 | 前端 B |
| modules/repair/routes.ts | 本人报修、派单和维修人员入口 | 前端 B |
| modules/system/routes.ts | 账号、参数和统计入口 | 前端 B |
| modules/audit/routes.ts、notification/routes.ts | 审计与消息入口 | 前端 B |
| shared/i18n/locales/zh-CN.ts、en.ts | 必要导航、页面和状态文字 | 公共层 |

后续实现某个模块时，在该模块的 `routes.ts` 将骨架组件替换为实际页面；新增入口也在所属模块登记。公共路由与导航自动汇总，减少多人修改同一文件。

新增测试位于 `frontend/tests/fixtures/navigation.ts`、`role-shell.test.ts`，并扩展 `app-router.test.ts`。

## 运行与验证

访问 http://127.0.0.1:5173/login，登录后进入工作台。现有持久化数据库和认证接口继续提供身份。

本机三种审查账号保存在 Git 忽略的 `infra/env/role-navigation.local`；管理员由原初始化配置提供，维护人员使用新建普通测试账号，经管理员接口授权。普通用户和维护人员访问账号管理接口均被后端拒绝。密码不进入文档、日志或提交。

| 检查 | 结果 |
| --- | --- |
| 前端测试 | 7 个文件、110 项通过；覆盖三种角色菜单、所有允许的入口、越权拒绝、权限与角色变化、安全返回地址，以及慢请求期间布局保留、失败清除和连续点击 |
| 真实组件渲染 | 使用真实 App、AppShell、WorkspacePage、FeaturePage 做 SSR，验证导航、入口、高亮、英文及检查期间的布局；Node 测试中只替换依赖浏览器 CSS 计算的主题读取 |
| 类型与生产构建 | vue-tsc、构建配置 tsc、Vite build 均通过 |
| 真实身份接口 | 三个账号登录与角色确认通过；普通用户和维护人员访问账号管理接口返回 403 |
| 业务与数据范围 | 本阶段仅实现前端导航和页面骨架；没有更改后端、数据库结构或种子，没有新增依赖 |

浏览器自动化工具此前存在本地导航中断，本次未完成实际浏览器点击和手机布局验收；SSR 与路由测试不代替此项。macOS 尚未实际运行。

## 当前停点

T08 已完成，用户已授权提交和推送。T09 完整登录流程、两服务与跨平台验收未开始。

本次合并提交描述：

```text
feat: 添加账号注册登录、角色导航与平滑页面切换

- 支持普通账号注册登录、密码哈希及首个管理员初始化
- 移除学校认证与一键登录，保留 Cookie、CSRF 和角色权限校验
- 添加三种角色导航、工作台入口和业务页面骨架
- 修复注册报错与切页闪烁，加入内容过渡动画
```
