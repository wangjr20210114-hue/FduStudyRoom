# 登录前端使用与交接

T05 使用 Vue 3、TypeScript、已有公共组件和中英文字典，接入 T04 身份接口。登录入口为 `/login`。T06 已接入首页工作台、个人信息与权限导航，见 [T06 说明](10-workspace-and-navigation.md)。

## 已实现行为

| 场景 | 页面行为 |
| --- | --- |
| 首次进入或刷新 | 同时请求认证配置和当前用户，等待服务端确认身份 |
| 未登录或凭据已失效 | `/auth/me` 返回 401 / 403 时展示登录入口，不保留本地身份 |
| 本地开发模式 | 显示明确的开发标识，可选择学生、管理员、维护人员固定演示账号 |
| 开发登录成功 | 再请求 `/auth/me` 确认 Cookie 会话，进入工作台；个人信息展示后端返回的身份 |
| 已登录 | 进入 T06 工作台，可通过用户菜单查看个人信息和退出 |
| 退出 | 重新取得 CSRF 后提交退出；成功或会话已过期时清除当前页面身份 |
| 服务故障 | 展示可翻译的错误与“重新检查”，不会自动退回开发身份 |
| 处理中 | 禁用重复提交和账号选择；应用卸载后取消请求，忽略迟到响应 |
| 中英文与手机 | 所有页面文案及现有错误随语言切换；窄屏使用单列布局 |

普通用户限复旦学生；管理员和维护人员允许复旦学号或工号，角色由后端授权。页面不接收学校密码，也不根据学号格式自行认定复旦身份。当前复旦 UIS 尚未配置，正式入口显示暂未开放；T07 完成校方认证后再联调跳转与回调。

## 文件与职责

| 文件 | 职责 |
| --- | --- |
| `frontend/src/modules/auth/types.ts` | 认证模式、开发选项和当前用户类型 |
| `frontend/src/modules/auth/api/auth.ts` | 响应校验、配置、当前用户、CSRF、开发登录和退出 |
| `frontend/src/modules/auth/model/login-flow.ts` | 加载、登录、确认会话、退出、重试及请求取消 |
| `frontend/src/modules/auth/model/error-message.ts` | 认证业务码对应的当前语言错误 |
| `frontend/src/modules/auth/pages/LoginPage.vue` | 精简登录布局、统一认证入口、开发表单与登录后跳转 |
| `frontend/src/app/App.vue` | T06 路由显示、共享会话检查与主题入口 |
| `frontend/src/shared/i18n/locales/zh-CN.ts`、`en.ts` | 登录页面的中英文文案 |
| `frontend/vite.config.ts`、`.env.example` | 本地同站点 API 代理和配置模板 |
| `frontend/vitest.config.ts` | 测试路径别名 |
| `frontend/tests/auth-api.test.ts` | 接口、响应和 CSRF 行为验证 |
| `frontend/tests/login-flow.test.ts`、`fixtures/auth.ts` | 会话状态、错误恢复与环境边界验证 |

本次没有增加依赖，没有修改后端、身份契约或数据库设计。

## 运行与代理

先按 [登录后端说明](08-login-backend.md) 启动 PostgreSQL 和 local 模式资源服务，后端默认地址为 `http://127.0.0.1:8080`。然后在 `frontend` 执行：

```sh
npm ci
npm run dev
```

打开 http://127.0.0.1:5173/login 。Windows 当前 UNC 共享路径的启动命令见 [README](../README.md)。前端通过同站点 `/api` 请求 Vite 代理，无需把后端地址写入浏览器代码或开启跨域凭据。

后端端口不为 8080 时，将 `frontend/.env.example` 复制为 `frontend/.env.local`，修改后重启 Vite：

```dotenv
CAMPUS_API_TARGET=http://127.0.0.1:8080
```

`CAMPUS_API_TARGET` 只用于 Vite 进程，允许本机回环 HTTP 地址；不使用 `VITE_` 前缀。前端不得配置数据库密码、签名密钥或服务间密钥。`.env.local` 由现有 Git 规则排除。

后端未启动时会展示服务错误；仅启动前端不能完成登录。开发代理和 `vite preview` 用于本地调试，正式部署需要同域 HTTPS 反向代理，部署配置随后续任务实现。

## 请求顺序与会话边界

| 操作 | 顺序 |
| --- | --- |
| 页面初始化 / 重新检查 | `GET /api/auth/config` 与 `GET /api/auth/me`；全部成功后采用配置和身份 |
| 开发登录 | `GET /api/auth/csrf` → `POST /api/auth/dev/login` → `GET /api/auth/me` |
| 退出 | `GET /api/auth/csrf` → `POST /api/auth/logout` |
| 正式认证入口 | 仅在后端报告正式模式且可用时跳转 `/api/auth/fudan/login`；当前不可用 |

登录与退出可能更新 CSRF Cookie，因此每次写请求前重新获取令牌。前端验证 CSRF 响应的头名称和令牌，再通过 `X-XSRF-TOKEN` 提交；登录请求只传固定账号选项，不传角色或用户身份声明。

登录凭据仅由后端通过 HttpOnly Cookie 管理。前端不读取凭据，不在 localStorage 保存用户、角色或令牌；localStorage 只沿用公共层的语言偏好。Cookie 是否生效通过 `/auth/me` 确认。退出请求失败时保留当前已知身份并展示错误，避免把网络故障显示成退出成功；重新检查可再次核验服务端状态。

开发表单要求开发版前端和后端 development 模式同时成立；正式前端遇到 development 配置时显示配置不可用。正式构建中开发登录请求地址被移除。后端仍独立检查环境、资格与权限，前端限制不能代替后端鉴权。

## 验证结果

验证日期：2026-10-29。环境：Windows，项目位于 WSL 共享目录；Node.js 24.19.0、npm 11.17.0。浏览器采用隔离的无头 Chrome，真实接口连接独立 PostgreSQL 测试库及 T04 资源服务。

| 检查 | 结果 |
| --- | --- |
| `npm test` | 4 个文件、48 项测试通过；其中 T05 新增 19 项 |
| `npm run typecheck`、`npm run build` | 通过；没有新增依赖 |
| 三种开发身份 | 学生、管理员、维护人员均通过真实 CSRF、Cookie 登录，展示后端角色 |
| 会话恢复与退出 | 刷新保留服务端会话，退出后回到登录入口 |
| 重复提交 | 请求暂停期间按钮与选择禁用，再次提交不会增加登录 POST |
| Cookie 与浏览器存储 | 登录 Cookie 为 HttpOnly，页面无法读取；localStorage 未保存身份或凭据 |
| 账号停用与服务故障 | 通过浏览器拦截注入错误，验证中英文错误即时更新、重试恢复及故障时不开放开发入口 |
| 正式前端 + 正式模式后端 | 统一认证未配置时禁用入口，无开发表单 |
| 正式前端 + 开发模式后端 | 明确拒绝该配置，不显示开发表单或已登录身份 |
| 正式产物 | 无 `/auth/dev/login` 请求地址及开发组件预览实现 |
| 页面外观 | 已检查中文桌面、英文 375px 手机、登录成功与正式模式截图；手机无横向溢出 |
| 浏览器运行 | 上述检查期间无 JavaScript 异常 |
| macOS / 正式 UIS | 本次未在 macOS 实跑；未连接或声称通过真实校方认证 |

Windows UNC 路径下通过 `pushd` 启动的 npm 检查应依次运行，避免多个进程共用临时盘符后因其中一个退出而失去工作目录。测试用后端、数据库和浏览器均与其他项目隔离。

以上截图与身份确认卡片为 T05 交付时的验证记录。后续已按用户要求删除宣传语、装饰和重复说明，并在 T06 将身份信息移到个人信息页；当前入口与验证见 [T06 说明](10-workspace-and-navigation.md)。
