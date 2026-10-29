# T07 普通账号登录与注册

更新：2026-10-29。用户决定不接入学校认证，开放注册；注册账号固定为普通用户。T07 已完成，与后续角色导航和修复一并纳入本次提交；版本记录以 Git 日志为准。后续 T08 角色导航已完成，见 [角色导航与页面切换](13-role-navigation.md)；完整登录流程验收顺延为 T09。

## 使用方式与角色

- 登录：`/login`；注册：`/register`。注册成功后自动进入工作台。
- 账号为 3—32 位，首位为英文字母或数字，其余允许英文字母、数字、下划线、点和短横线；去除首尾空格并统一转成小写，大小写不能注册成两个账号。
- 普通密码为 8—64 个字符；注册时在前端重复输入确认。密码不去除空格，后端长度按 Java 字符串长度计算。
- 注册请求只接收账号和密码，不接收角色。新账号固定为 `USER`，显示名称初始为账号。
- `SYSTEM_ADMIN`、`MAINTAINER` 仍由管理员分配。当前账号管理前端是预留页，角色分配接口已实现。
- 登录仅验证本系统账号密码，不证明复旦学生或教职工身份。现有学校认证和开发身份选择入口已移除。
- 登录后提供当前角色的导航和工作台入口，用户菜单保留个人信息和退出，并支持语言切换；业务页面暂用骨架，视觉设计后续进行。

## 首个管理员

空库首次启动时，在后端运行终端或 IDE 启动配置中同时设置：

| 变量 | 要求 |
| --- | --- |
| `CAMPUS_BOOTSTRAP_ADMIN_LOGIN` | 未使用的账号，遵守上述账号规则 |
| `CAMPUS_BOOTSTRAP_ADMIN_PASSWORD` | 12—64 个字符，不写入仓库 |

不设置这两项时只开放普通用户注册。只设置一项会启动失败。已有同名本地管理员时保留原密码、角色、启用状态和资料；已有同名普通用户时启动失败，不提升其权限。初始化成功后可从后续启动配置中移除这两个变量。没有通用默认管理员密码。

具体数据库、密钥、Windows/macOS 构建命令见 [后端运行](08-login-backend.md)，前端代理见 [前端运行](09-login-frontend.md)。

## 接口与会话

| 操作 | 请求顺序 |
| --- | --- |
| 初始化或重新检查 | `GET /api/auth/config`、`GET /api/auth/me` |
| 登录 | `GET /api/auth/csrf` → `POST /api/auth/login` → `GET /api/auth/me` |
| 注册并登录 | `GET /api/auth/csrf` → `POST /api/auth/register`（201）→ `GET /api/auth/me` |
| 退出 | `GET /api/auth/csrf` → `POST /api/auth/logout`（204） |

登录和注册的 JSON 均只有 `loginName`、`password`。重复账号返回 409，账号或密码错误统一返回 401。提交期间禁止重复请求；密码在提交结束后清空。前端通过 `/me` 确认 Cookie 已生效，再跳转到有权限的页面。

登录 Cookie 为 HttpOnly、SameSite=Lax，默认有效期 30 分钟。生产使用 Secure 和 `__Host-` 前缀；仅 local profile 加回环绑定允许本地 HTTP Cookie。每次请求检查账号、角色启用状态和 `auth_version`；退出或改权会使该账号所有设备的旧会话失效。浏览器存储仅保存语言选择，不保存密码或令牌。

当前限流在单个后端进程内生效：登录每个来源 IP 每分钟 50 次、每个账号每分钟 10 次；注册每个 IP 每 10 分钟 20 次。以实际连接地址计数，不信任客户端提供的转发头；服务重启会清空计数。完整契约见 [identity.yaml](../contracts/openapi/identity.yaml)。

## 密码与迁移

密码使用带随机盐的 PBKDF2-HMAC-SHA256：16 字节盐、600,000 次迭代、256 位结果；保存为 `{pbkdf2-sha256-v1}` 前缀加 96 位十六进制值。查询接口不返回密码或哈希，日志不记录密码。

| 文件 | 作用 |
| --- | --- |
| `V1__identity_schema.sql` | 保持原有已发布身份表迁移 |
| `V2__password_accounts.sql` | 新增 password_hash、本地来源、条件必填及大小写唯一约束 |
| `R__identity_defaults.sql` | 保持幂等角色与权限种子，不建立固定密码账号 |

V2 保留所有旧账号、身份字段值和启用状态，不删除或批量停用。旧学校/演示账号没有本地密码，不能直接使用密码登录；新注册账号使用 LOCAL 来源，身份类型为空，学校资格字段不参与普通登录校验。新管理员也通过明确配置建立，不接管旧账号。字段、约束和外键见 [数据库设计](02-database-design.md)。

## 文件范围

| 范围 | 主要文件 | 实现 |
| --- | --- | --- |
| 后端公共安全 | `platform-common/.../security/{AuthSettings,IdentityPrincipal,CookieAuthenticationFilter,SecurityConfiguration}.java` | 密码认证模式、Cookie、CSRF 与公开路径 |
| 后端身份 | `resource-service/.../identity/application/{IdentityService,AuthRateLimiter}.java` | 注册、密码验证、限流、改权与会话撤销 |
| 密码与持久层 | `application/PasswordConfiguration.java`、`infrastructure/{BootstrapAdministrator,AccountRepository}.java`、`domain/Account.java` | 哈希、首个管理员、账号查询与状态校验 |
| 后端接口配置 | `api/AuthController.java`、`application*.yml`、`backend/.env.example` | 登录、注册、初始化配置；移除旧开发登录和数据初始化 |
| 前端认证 | `modules/auth/{types.ts,api/auth.ts,model/login-flow.ts,model/error-message.ts,pages/LoginPage.vue,pages/AccountPage.vue}` | 登录注册、确认密码、错误、会话与个人信息 |
| 前端公共与布局 | `shared/components/AppTextField.vue`、中英文字典、`app/router/index.ts`、`app/layouts/AppShell.vue` | 密码输入、长度与格式约束、注册路由及简洁导航 |
| 契约与验证 | `contracts/openapi/identity.yaml`、`tests/backend-auth-smoke.py`、前后端测试 | 与实现同步的请求和行为验证 |

后端 Java 路径分别相对 `backend/platform-common/src/main/java/com/campus/common` 和 `backend/resource-service/src/main/java/com/campus/identity`；迁移相对资源服务 `src/main/resources/db`。本次没有增加依赖。

## 验证与边界

| 检查 | 结果 |
| --- | --- |
| 后端 `clean verify` | 21 项测试通过，生成可运行 JAR |
| 前端 `npm test` | 74 项测试通过 |
| 类型与正式构建 | `npm run typecheck`、`npm run build` 通过 |
| 真实 HTTP | 33 项通过：CSRF、注册固定角色、重复账号、错误密码、限流、改权、停用、全设备退出等 |
| 真实浏览器 | 初版 12 组已验证；本次自动化导航持续返回 net::ERR_ABORTED，未完成修复后的浏览器回归 |
| 界面 | 初版已检查中文桌面、英文 375px 手机及管理员个人信息截图；本次没有新增有效浏览器截图 |
| PostgreSQL | 空库实际执行 Flyway V1/V2/种子；已有 V1 表和旧账号执行 V2 后原字段值与状态保持不变 |
| 重复启动 | 用户、密码哈希、角色和权限保持一致；已有管理员原密码仍可登录 |
| 生产 Cookie | 实际启动生产配置，CSRF Cookie 带 Secure 和 HttpOnly；生产会话 Cookie 策略沿用已测试公共安全组件 |
| macOS 与流程服务 | 尚未在实际 macOS 实跑；流程服务的身份接入与完整运行入口仍待 T09/后续任务 |

HTTP 验证脚本需要专用测试库和管理员凭据，创建并变更隔离测试账号，命令见后端文档。当前没有找回密码或密码修改页面；此任务只覆盖登录、注册和既有权限会话。学校认证调研作为历史资料保留在 [调研记录](11-uis-and-danxi-research.md)，不再是当前实施前提。

## 注册失败修复

现象：输入不足 8 位的密码提交注册，页面只显示“请求失败”。原因是 AppTextField 把 minlength/maxlength 放在 input-props 内，被 Naive UI 的同名属性覆盖；后端参数校验返回 400，而前端没有给出具体输入要求。

已修正公共输入框的长度属性传递，登录和注册在发送请求前独立检查账号及密码。后端在身份服务中统一校验密码，返回 INVALID_PASSWORD，避免通过 Bean Validation 字段异常回显密码原值；普通 HTTP 400 使用输入错误提示。已有错误随中英文切换，账号规则中的 HTML pattern 短横线已转义。

相关文件：AppTextField.vue、login-flow.ts、error-message.ts、shared/api/message.ts、中英文字典、LoginPage.vue、IdentityService.java、AuthController.java、application.yml 和相应测试。前端 74 项、后端 21 项测试通过；类型检查、正式构建及 33 项真实接口检查通过。新增 text-field.test.ts 使用 Vue 服务端渲染真实 Naive UI 组件，验证最终 input 的 minlength=8、maxlength=64，覆盖本次长度属性被覆盖的问题。浏览器自动化在 Chrome/Edge 中均返回 net::ERR_ABORTED；客户端浏览器工具也未能启动，未完成本次真实页面交互回归。

### 预览数据库修正

此前预览沿用了 tmpfs 临时验收容器；该容器停止后，新建测试库不保留。原临时预览账号需重新注册。现在预览使用 infra/compose.yaml 的 PostgreSQL 命名卷 fdu-study-room_postgres_data，端口 55432，并配置 unless-stopped 重启策略；管理员通过显式启动配置初始化。本地配置和密码由 Git 忽略。

已实际重启新数据库容器，确认当时已有的 2 个账号、密码哈希、角色与启用状态保持一致，原管理员密码仍可登录。V2 迁移仍保留表内已有记录，本次未修改 V1 或角色权限种子。
