# 登录功能分步实施与交接

每次只执行一个编号任务。完成当前任务并进行相应验证后，报告新增或修改文件、实现功能、验证结果和下一项，然后停止。用户提出状态或 Git 问题时，先回答，再完成当前任务；只有明确要求暂停或改换目标时才停止当前任务。收到继续指令后再进入下一编号。

公共脚本使用 TypeScript 编写，构建为浏览器运行的 JavaScript。整体方案继续采用 Vue 3、Java Spring Boot 和 PostgreSQL；当前登录规则见 [普通账号登录](12-password-login.md)。T04—T06 下方记录是对应提交时的历史状态；旧学校认证与开发身份入口已由 T07 替代。

## 1 任务划分

| 编号 | 任务 | 主要文件范围 | 验收 | 状态 |
| --- | --- | --- | --- | --- |
| T01 | 前端运行骨架 | frontend 的依赖、入口、构建与类型配置，根目录启动说明及 Git 规则 | 可启动并返回前端资源；类型检查和正式打包通过 | 已完成 |
| T02 | 公共样式与基础组件 | shared/styles、shared/components | 统一主题、按钮、面板、输入框、加载和错误提示；桌面与手机可用 | 已完成 |
| T03 | 公共逻辑与国际化 | shared 的 API 客户端、工具和 i18n，zh-CN / en 字典 | 请求错误处理一致；语言可切换并保留选择；后续页面复用字典 | 已完成 |
| T04 | 登录后端 | backend 构建、identity、security、认证契约和必要数据库迁移 | 用户与角色、当前用户、退出和后端鉴权；开发身份入口与正式模式隔离；关键安全行为有测试 | 已提交 ec0ab34 |
| T05 | 登录前端 | modules/auth 与公共组件组合 | 复旦认证入口、开发模式标识、处理中与失败状态，使用公共样式和字典 | 已提交 21a1f32 |
| T06 | 登录后基础布局 | app 路由与布局、导航、用户菜单 | 已登录可进入空工作台；未登录不能访问；菜单按权限显示；可以退出 | 已提交并推送 fe00720 |
| T07 | 本系统账号密码登录与注册 | identity 密码认证、V2 迁移、登录注册页面和契约 | 注册固定 USER；密码验证、首个管理员、会话、权限和真实浏览器检查通过 | 已完成，纳入本次提交 |
| T08 | 角色导航与页面切换 | app 布局与路由、各业务模块 routes.ts、共享骨架页和字典 | 三种角色入口不同，菜单可切换页面，直接访问也检查角色权限 | 已完成，纳入本次提交 |
| T09 | 完整登录流程验收 | 两服务、前端、构建入口及使用说明 | 完整运行入口与两服务身份一致性通过；Windows/macOS 实际验收 | 待开始 |

当前决策：取消学校认证，允许注册普通账号，注册固定 USER；管理员、维护人员由管理员授权。普通登录不验证学工号或学校资格。按用户新要求，T08 改为角色导航与页面切换，原完整登录验收顺延为 T09。T08 已完成并停止交接；当前说明见 [角色导航](13-role-navigation.md)。

## 2 T01 文件与用途

| 文件 | 用途 |
| --- | --- |
| .gitignore、.gitattributes | 排除依赖、产物和本地配置；统一跨平台文本换行 |
| frontend/package.json、package-lock.json | 定义启动、类型检查、构建和预览入口，锁定依赖 |
| frontend/index.html、public/favicon.svg | 浏览器入口、页面元数据和项目图标 |
| frontend/tsconfig.json、tsconfig.node.json、vite.config.ts | Vue 与构建配置的类型检查、路径别名和固定端口 |
| frontend/src/main.ts、src/app/App.vue | 挂载 Vue 应用，展示简单起始页 |
| frontend/src/shared/styles/base.css | 浏览器基础排版和布局重置；组件主题在 T02 建立 |
| README.md | 本机启动与验证方法 |
| docs/01-project-structure.md | 同步已实现入口与待创建文件 |
| docs/04-login-implementation-tasks.md | 当前任务、状态和交接要求 |
| docs/05-codearts-git-guide.md | CodeArts IDE 内置 Git 与 Gitee 的提交和推送步骤 |

## 3 T01 验证记录

验证环境：Windows，Node.js v24.19.0，npm 11.17.0；项目文件位于 WSL 共享目录。依赖版本已精确记录在 package.json 和 package-lock.json。

| 检查 | 结果 |
| --- | --- |
| npm ci | 使用锁文件重新安装成功 |
| Vue 应用与构建配置类型检查 | 通过；由 npm run build 先行执行 |
| npm run build | 通过，生成正式 dist 产物 |
| 开发服务器 | 在 http://127.0.0.1:5173 启动成功 |
| HTTP 资源 | HTML、入口脚本、Vue 模块、样式和图标均返回 200 |
| Windows/WSL 文件监听 | 使用终端内轮询配置，修改与恢复源码后缓存均正确更新 |
| 原有文件 | 除同步目录文档外保留原样，原 IDE 配置未变 |
| 浏览器外观 | 等待用户打开地址检查；未声称完成真实浏览器视觉验收 |
| macOS | 已提供运行步骤，尚未在实际 macOS 环境执行 |

T01 的交付范围为基础页面；公共组件体系由 T02 交付，国际化、认证后端、登录页及登录后工作台仍按后续编号实施。

## 4 T02 文件与用途

| 文件 | 用途 |
| --- | --- |
| frontend/package.json、package-lock.json | 加入并精确锁定 Naive UI 2.45.3 |
| frontend/src/shared/styles/tokens.css | 统一颜色、字体、字号、间距、圆角、高度、阴影及动效 |
| frontend/src/shared/styles/base.css | 导入公共变量，统一基础排版、键盘焦点和减少动效支持 |
| frontend/src/shared/styles/theme.ts | 从 CSS 变量生成 Naive UI 主题 |
| frontend/src/shared/components/AppThemeProvider.vue | 应用主题和组件库中文区域设置 |
| frontend/src/shared/components/AppButton.vue | 按钮变体、尺寸、加载、禁用及提交类型 |
| frontend/src/shared/components/AppPanel.vue | 面板标题、说明、内容、操作和页脚 |
| frontend/src/shared/components/AppTextField.vue | 标签、输入、提示、错误和 v-model；关联无障碍属性 |
| frontend/src/shared/components/AppStatusTag.vue | 以文字和颜色展示状态 |
| frontend/src/shared/components/AppLoading.vue | 加载图标、文案及状态播报 |
| frontend/src/shared/components/AppAlert.vue | 信息、成功、警告和错误提示，可附操作 |
| frontend/src/shared/components/types.ts、index.ts | 公共类型与导出 |
| frontend/src/app/App.vue | 接入主题和仅开发环境可用的预览入口 |
| frontend/src/app/dev/ComponentPreview.vue | 组件展示、输入校验和加载重试演示，适配手机 |
| README.md、docs/01-project-structure.md | 同步当前实现范围、目录和预览入口 |
| docs/04-login-implementation-tasks.md | 更新任务状态、验证记录及本次 Git 审查停点 |
| docs/06-frontend-components.md | 组件属性、插槽、使用示例与样式约定 |

## 5 T02 验证记录

验证环境：Windows，Node.js v24.19.0、npm 11.17.0；源码位于 WSL 共享目录。使用隔离的无头 Chrome 执行真实页面交互，并检查桌面与手机截图。

| 检查 | 结果 |
| --- | --- |
| 类型检查与正式构建 | npm run build 通过，包含 Vue 应用与构建配置的类型检查 |
| 统一主题 | 浏览器中 CSS 主色与 Naive UI 主按钮颜色一致 |
| 按钮交互 | 点击、禁用、加载与提交类型符合预期；加载时阻止重复点击 |
| 输入与校验 | 标签可聚焦输入框；v-model 更新；错误文案和 aria-invalid、aria-describedby 关联正确 |
| 加载与重试 | 重试经过加载状态后呈现成功反馈 |
| 减少动效 | 系统偏好启用时应用减少动效样式 |
| 手机布局 | 375px 视口面板纵向排列，无横向溢出 |
| 浏览器运行 | 检查过程中未出现 JavaScript 异常 |
| 正式产物 | 开发组件预览及示例逻辑不进入 dist |
| macOS | 已提供启动步骤，尚未在实际 macOS 环境执行 |

预览地址：http://127.0.0.1:5173/dev/components 。完整组件用法见 [公共组件说明](06-frontend-components.md)。

## 6 T03 文件与用途

| 文件 | 用途 |
| --- | --- |
| frontend/src/shared/api/client.ts | JSON 请求、参数编码、语言请求头、超时、取消和响应格式检查 |
| frontend/src/shared/api/errors.ts | 统一异常分类、HTTP 状态、业务码及追踪号 |
| frontend/src/shared/api/message.ts、index.ts | 当前语言错误提示和同站点 /api 入口 |
| frontend/src/shared/i18n/locales/zh-CN.ts、en.ts | 首页、公共提示和组件预览的中英文字典 |
| frontend/src/shared/i18n/schema.d.ts | 翻译函数的字典结构类型 |
| frontend/src/shared/i18n/locale.ts、index.ts | 语言保存、回退、全局实例、页面语言和标题同步 |
| frontend/src/shared/components/LanguageSwitcher.vue | 语言选择控件 |
| frontend/src/shared/components/AppThemeProvider.vue、index.ts | 同步 Naive UI 语言、日期区域设置并导出新控件 |
| frontend/src/main.ts、src/app/App.vue | 注册国际化插件，首页接入字典和语言选择 |
| frontend/src/app/dev/ComponentPreview.vue | 预览文案接入字典，已有错误及反馈随语言更新 |
| frontend/tests/api-client.test.ts、i18n.test.ts | 请求和国际化共 29 项行为测试 |
| frontend/vitest.config.ts、tsconfig.json、tsconfig.node.json | 测试入口及应用、测试、配置的类型检查 |
| frontend/package.json、package-lock.json | 精确锁定 Vue I18n 11.4.16、Vitest 5.0.5，增加 npm test |
| .gitignore | 忽略测试工具产物 |
| README.md、docs/01-project-structure.md | 同步实现范围、目录与验证入口 |
| docs/04-login-implementation-tasks.md | T03 任务记录和待审查提交说明 |
| docs/06-frontend-components.md | 更新语言选择组件及主题区域设置说明 |
| docs/07-frontend-api-and-i18n.md | 公共请求、错误处理和国际化用法 |

## 7 T03 验证记录

验证环境仍为 Windows 与 WSL 共享目录，Node.js v24.19.0、npm 11.17.0。浏览器使用隔离的无头 Chrome。

| 检查 | 结果 |
| --- | --- |
| npm test | 2 个测试文件、29 项测试全部通过 |
| 类型检查与正式构建 | npm run build 通过，包含应用、测试及构建配置类型检查 |
| 请求行为 | 查询编码、JSON 提交、当前语言请求头、HTTP 状态及安全错误信息符合预期 |
| 异常与取消 | 网络故障、格式异常、超时、主动取消分类正确；写请求不自动重试 |
| 语言选择 | 默认中文，中英文可切换；刷新和首页/预览跳转后保留选择 |
| 已有提示 | 已展示的表单错误和按钮反馈切换语言时即时翻译 |
| 页面及组件区域设置 | html lang、页面标题随语言更新；主题容器响应语言变化 |
| 存储受限 | 不支持的已存语言回退为中文；存储禁止或写入失败时仍可在当前页面切换 |
| 手机布局 | 英文页面 375px 视口无横向溢出；面板宽 343px，纵向排列 |
| 浏览器运行与外观 | 检查期间无 JavaScript 异常，已检查英文桌面和手机截图 |
| 正式产物 | 移除开发预览组件及其交互逻辑 |
| macOS 与真实后端 | 尚未实际验收 macOS；请求测试未连接真实业务后端或复旦服务 |

操作入口：http://127.0.0.1:5173/ 与 http://127.0.0.1:5173/dev/components 。详细用法见 [公共请求与国际化](07-frontend-api-and-i18n.md)。

## 8 T04 文件与用途

| 文件 | 用途 |
| --- | --- |
| backend/pom.xml | Java 21、Spring Boot 4.1.2，构建公共库和资源身份服务 |
| backend/platform-common/pom.xml、backend/resource-service/pom.xml | 公共安全依赖、数据库迁移与可运行 JAR |
| backend/mvnw、mvnw.cmd、.mvn/wrapper/maven-wrapper.properties | Windows / Unix Maven Wrapper，锁定 3.9.16 及发行包校验和 |
| backend/resource-service/src/main/java/com/campus/ResourceApplication.java | 资源服务启动入口 |
| platform-common/.../error/ApiException.java、ApiErrors.java、ApiAdvice.java | 统一异常、错误响应及输入错误处理 |
| platform-common/.../tracing/RequestTraceFilter.java | 生成请求追踪号与禁止缓存响应 |
| platform-common/.../security/AuthSettings.java | 认证配置、密钥、开发环境及安全 Cookie 边界 |
| platform-common/.../security/TokenService.java | 签发并校验短期凭据，验证模式、签名及期限 |
| platform-common/.../security/AuthCookies.java | HttpOnly 登录 Cookie 设置、读取和清除 |
| platform-common/.../security/IdentityPrincipal.java、IdentityLookup.java | 当前身份及后端资格校验接口 |
| platform-common/.../security/CookieAuthenticationFilter.java、SecurityConfiguration.java | Cookie 认证、CSRF、请求与方法权限控制 |
| resource-service/.../identity/domain/Account.java | 学生、教职工、开发/正式来源及有效资格规则 |
| resource-service/.../identity/infrastructure/AccountRepository.java、DatabaseErrors.java | PostgreSQL 访问与安全的数据库异常响应 |
| resource-service/.../identity/application/IdentityService.java | 登录资格、实时权限、全设备退出及带版本的改权 |
| resource-service/.../identity/api/AuthController.java | 模式、CSRF、当前用户、退出、内部校验及 UIS 未配置响应 |
| resource-service/.../identity/api/DevelopmentLoginController.java | 仅本地开发环境开放的固定身份入口 |
| resource-service/.../identity/api/AccountAdminController.java | 受权限保护的账号、角色查询和账号授权接口 |
| resource-service/.../identity/infrastructure/DevelopmentData.java | 只在本地开发模式初始化演示数据 |
| resource-service/src/main/resources/application.yml、application-local.yml | 数据库、Flyway、Cookie、认证模式及故障超时 |
| resource-service/src/main/resources/db/migration/V1__identity_schema.sql | 身份四表、真实外键和字段约束 |
| resource-service/src/main/resources/db/seed/R__identity_defaults.sql | 默认角色和权限的幂等初始化，保留已有配置 |
| resource-service/src/main/resources/db/demo/identity.sql | 与正式种子分离的开发与拒绝测试账号 |
| platform-common/src/test/.../TokenServiceTest.java、AuthSettingsTest.java | 签名、过期、模式隔离与环境配置边界 |
| resource-service/src/test/.../identity/domain/AccountTest.java | 身份资格与角色规则 |
| tests/backend-auth-smoke.py | 真实 HTTP 登录、权限、CSRF、改权与退出验收 |
| contracts/openapi/identity.yaml | 身份接口、响应及权限契约 |
| infra/compose.yaml、infra/.env.example、backend/.env.example | 独立 PostgreSQL、持久数据卷及无真实秘密的配置模板 |
| README.md、docs/01-project-structure.md | 运行入口与当前目录状态 |
| docs/02-database-design.md、03-ui-and-auth-plan.md | 同步身份字段、非空、外键、来源及资格规则 |
| docs/04-login-implementation-tasks.md、08-login-backend.md | 交接记录、启动方法与安全控制说明 |

表中 platform-common 和 resource-service 路径均相对 backend；Java 省略部分为 src/main/java/com/campus（公共库位于 com/campus/common）。

## 9 T04 验证记录

验证环境：Windows，隔离的 Amazon Corretto 21.0.13、Maven Wrapper 3.9.16、Spring Boot 4.1.2；独立 PostgreSQL 18.6 测试容器。未修改本机 Java 安装，不使用其他项目数据库。

| 检查 | 结果 |
| --- | --- |
| Maven Wrapper verify | 通过；15 项单元测试、公共库及可运行资源服务 JAR |
| 真实 HTTP | 39 项检查通过，覆盖开发身份、当前用户、权限、CSRF、改权、退出、重放及生产隔离 |
| 账号资格 | 学生可登录；未授管理角色的教职工、资格失效及停用账号被拒绝 |
| 角色与权限 | 浏览器自报角色被拒绝；普通用户不能访问账号管理接口 |
| 凭据失效 | 改权与退出撤销旧版本，另一个设备和内部校验接口也拒绝旧凭据 |
| CSRF 与 Cookie | 登录和写请求验证 CSRF；登录凭据不出现在 JSON；生产使用 Secure / HttpOnly / __Host- Cookie |
| 生产配置 | 不加载开发控制器；拒绝开发凭据；UIS 缺少校方配置时返回 503，不退回模拟认证 |
| 真实迁移 | 建立身份四表与 resource_meta Flyway 历史；无 password_hash |
| 重复启动与种子 | 迁移重复验证通过；种子与开发数据重复执行不覆盖已修改名称、权限和账号 |
| 数据库故障 | 暂停本任务测试数据库后约 5.03 秒返回 503；恢复后同一身份可重新访问 |
| 构建入口与 Compose | Windows Wrapper 下载校验及运行通过；Unix 脚本语法和 Compose 配置检查通过 |
| macOS / 正式 UIS / 流程服务 | 尚未在 macOS 实跑；真实校方登录和流程服务端接入仍按后续任务执行 |

启动与验证说明见 [登录后端](08-login-backend.md)。当前安全事件写结构化日志；持久审计投递随公共审计模块接入，未宣称完整审计链完成。

## 10 T05 文件与用途

| 文件 | 用途 |
| --- | --- |
| frontend/src/modules/auth/types.ts | 模式、当前用户与开发账号类型 |
| frontend/src/modules/auth/api/auth.ts | 响应校验、认证配置、CSRF、登录、当前用户和退出 |
| frontend/src/modules/auth/model/login-flow.ts | 登录状态、会话确认、刷新恢复、失败重试及取消 |
| frontend/src/modules/auth/model/error-message.ts | 认证业务错误的中英文提示 |
| frontend/src/modules/auth/pages/LoginPage.vue | 登录布局、开发身份选择及登录成功确认 |
| frontend/src/app/App.vue | 以登录页作为当前入口 |
| frontend/src/shared/i18n/locales/zh-CN.ts、en.ts | 登录与身份确认字典 |
| frontend/vite.config.ts、frontend/.env.example | 回环地址 API 代理与配置模板 |
| frontend/vitest.config.ts | 测试路径别名 |
| frontend/tests/auth-api.test.ts、login-flow.test.ts、fixtures/auth.ts | CSRF、响应检查、会话状态及环境隔离测试 |
| README.md、docs/01-project-structure.md、08-login-backend.md | 同步入口、目录与前后端联调说明 |
| docs/04-login-implementation-tasks.md、09-login-frontend.md | T05 文件清单、运行、验证与交接 |

没有新增前端依赖；后端、身份契约与数据库设计保持本任务开始时的内容。

## 11 T05 验证记录

验证日期：2026-10-29。Windows / WSL 共享目录，Node.js 24.19.0、npm 11.17.0；隔离 Chrome、独立 PostgreSQL 测试库及 T04 资源服务。

| 检查 | 结果 |
| --- | --- |
| npm test | 4 个文件、48 项全部通过，T05 新增 19 项 |
| 类型与构建 | npm run typecheck、npm run build 通过 |
| 真实登录 | 学生、管理员、维护人员经 CSRF 和 Cookie 登录，身份由后端确认 |
| 会话与退出 | 刷新恢复当前身份，退出后回到登录入口 |
| 重复提交 | 加载时禁用操作，重复提交不增加登录请求 |
| 凭据 | Cookie 为 HttpOnly，页面无法读取；localStorage 无用户、角色或令牌 |
| 错误与恢复 | 注入账号停用和服务故障，验证已有错误随语言更新、重新检查恢复 |
| 正式环境 | 正式模式无开发表单；正式前端误连开发后端时拒绝该配置 |
| 正式产物 | 移除开发登录请求地址和开发组件预览 |
| 布局与运行 | 中文桌面、英文 375px 手机、成功页及正式模式截图已检查；无横向溢出或 JavaScript 异常 |
| macOS / 正式 UIS | 尚未实际验收 macOS；真实校方认证仍待 T07 |

使用说明见 [登录前端](09-login-frontend.md)。当前登录成功后展示身份确认卡片，T06 才实现受保护工作台与按权限显示的导航。

## 12 登录页精简与 T06 文件

按用户要求先精简 T05 页面，再实现 T06。删除宣传语、功能介绍、插画、帮助长文与重复提示，保留操作和必要状态；视觉设计后续再做。

| 文件 | 用途 |
| --- | --- |
| frontend/package.json、package-lock.json | 锁定 Vue Router 5.4.0 |
| frontend/src/main.ts、app/App.vue | 路由注册、共享身份、加载期间隐藏页面及标签页恢复检查 |
| frontend/src/app/router/index.ts、navigation.ts | 导航守卫、权限菜单及安全返回地址 |
| frontend/src/app/layouts/AppShell.vue | 导航栏、语言选择、用户菜单及退出 |
| frontend/src/app/pages/StatusPage.vue | 空工作台、账号管理预留页、无权限及不存在页面 |
| frontend/src/modules/auth/model/auth-session.ts、role-label.ts | 共享身份注入与角色翻译 |
| frontend/src/modules/auth/model/login-flow.ts | 并发检查复用，重新核验前清除旧身份 |
| frontend/src/modules/auth/pages/LoginPage.vue、AccountPage.vue | 精简登录、成功跳转与个人信息 |
| frontend/src/shared/i18n/locales/zh-CN.ts、en.ts | 必要导航与状态文案，移除多余登录文案 |
| frontend/tests/app-router.test.ts、vitest.config.ts | 路由、权限变化、过期和返回地址测试 |
| README.md、docs/01-project-structure.md、09-login-frontend.md | 同步入口与当前页面结构 |
| docs/04-login-implementation-tasks.md、10-workspace-and-navigation.md | T06 交接与验证说明 |

当前只展示已有真实权限对应的入口；账号管理为预留页面，不包含用户列表或授权操作。后端、身份契约与数据库结构未改动。

## 13 T06 验证记录

验证环境：Windows / WSL 共享目录，Node.js 24.19.0、npm 11.17.0；隔离 Chrome 连接 T04 独立测试库与资源服务。

| 检查 | 结果 |
| --- | --- |
| npm test | 5 个文件、68 项通过；T06 新增 20 项 |
| 类型与正式构建 | npm run build 通过，包含应用、测试和配置类型检查 |
| 未登录与深链接 | 直接打开工作台返回登录，不挂载受保护布局；学生直接访问管理地址被拦截 |
| 三种真实身份 | 学生、管理员、维护人员均经 T04 接口登录，菜单与后端权限一致 |
| 个人信息与恢复 | 显示后端账号、角色和身份；刷新可恢复会话 |
| 过期与权限变化 | 路由测试覆盖会话过期、权限撤销和角色名不能代替权限；浏览器验证过期后重新登录返回允许的地址 |
| 服务与退出失败 | 注入服务错误后旧内容隐藏，重试可恢复；退出失败保留身份，重试成功返回登录 |
| 浏览器返回与标签页 | 退出后返回仍被拦截；标签页重新可见时重新核验过期会话 |
| 界面 | 已检查精简登录、学生工作台、管理员工作台及英文 375px 手机截图，无横向溢出 |
| 正式前端 | 误连开发后端时拒绝该配置；产物无开发登录请求地址或组件预览代码 |
| 浏览器异常 | 检查期间无 JavaScript 异常 |
| macOS / 正式 UIS | 本次未在 macOS 实跑；真实校方认证继续等待 T07 |

浏览器检查使用真实后端和独立测试库；服务故障场景由浏览器拦截注入。修复了重试期间登录页重新挂载导致跳转遗漏或重复的问题，改为在登录或重试完成后显式跳转。

## 14 Git 提交与推送

T02 为 1be62d3，T03 为 1cabf40，T04 为 ec0ab34，T05 为 21a1f32。已按用户指令提交登录页精简与 T06 为 fe00720，并将全部本地提交推送至 https://gitee.com/wangjr2021/fdu-study-room.git 的 main 分支。

推送后已确认本地 HEAD 与 origin/main 同为 fe007203e80de2335696a521491fcc53bf5235bd。没有强制推送。

## 15 T07 普通账号登录与注册

用户决定无法申请学校接入，改为本系统账号密码登录，并明确允许普通用户注册。原 UIS/旦挞调研保留为历史文档，不再是实现前提。

| 范围 | 交付 |
| --- | --- |
| 后端 | 密码哈希、注册固定 USER、登录、限流、可选首个管理员初始化、Cookie/CSRF 与现有实时权限 |
| 数据库 | V2 增加本地密码字段及约束；V1 和种子不修改，旧账号和身份值保留 |
| 前端 | 简洁登录/注册表单、确认密码、错误提示、中英文、三种角色自己的密码登录、个人信息和导航 |
| 移除 | 原学校跳转/回调占位和一键开发登录、固定演示数据初始化 |
| 文档契约 | identity.yaml、数据库字段约束与解释、运行说明、历史方案状态及 T07 交接 |
| 验证 | 后端 21 项、前端 74 项、类型与正式构建、真实 HTTP 33 项及组件渲染回归通过；浏览器初版 12 组已验，本次导航中断 |
| 数据兼容 | 空库 Flyway V1/V2/种子通过；既有 V1 表执行 V2 后旧字段值与状态保持不变；重复启动不覆盖密码、账号和权限 |

详细文件清单、初始化规则、请求顺序和验证边界见 [T07 普通登录](12-password-login.md)。本任务没有新增依赖。macOS 尚未实际运行，流程服务仍未实现。此处为 T07 交付记录，当前进度见第 17 节。

T07 初次交付时尚未提交、推送，当时 HEAD 与 origin/main 为 fe00720；此处保留原交付记录，最新提交安排见第 18 节。原建议描述：

```text
feat(auth): 改用账号密码登录并开放普通用户注册

- 添加密码哈希、注册、登录及管理员初始化
- 移除学校认证与一键登录，更新会话和权限校验
```

T07 交付时停止等待审查；后续角色导航实施见第 17 节。

## 16 注册失败修复

修正 AppTextField 长度属性被 Naive UI 覆盖、提交前缺少独立校验及 HTTP 400 只显示“请求失败”的问题；后端改为安全的 INVALID_PASSWORD 校验错误，避免字段异常记录密码原值。新增真实组件渲染回归。

已通过 74 项前端测试、21 项后端测试、类型检查、构建和 33 项真实 HTTP 检查。修复后的浏览器自动化导航被中断，未计作通过。预览改用项目持久化 PostgreSQL；容器重启后账号和原密码登录验证通过。原临时预览账号需重新注册。完整说明见 [T07](12-password-login.md)。

此处为注册修复交付记录；后续角色导航实施见第 17 节。

## 17 T08 角色导航与页面切换

按用户要求先实现各角色导航和切换界面，原完整登录流程验收顺延为 T09。三种角色使用各自账号登录，由后端当前用户结果决定菜单。

| 文件范围 | 交付 |
| --- | --- |
| app/layouts/AppShell.vue | 桌面侧栏、手机导航开关、角色标识、选中高亮与内容过渡；保留语言和用户菜单 |
| app/pages/WorkspacePage.vue | 从当前可见菜单生成工作台快捷入口 |
| app/router/pages.ts、index.ts、navigation.ts、meta.d.ts 与 App.vue | 汇总模块路由，统一角色与权限检查、安全返回地址和标签页恢复检查 |
| modules 的 resource、reservation、approval、repair、system、audit、notification/routes.ts | 各自维护入口，后续在所属模块替换实际业务页面 |
| shared/navigation、shared/pages/FeaturePage.vue、shared/i18n/locales | 路由类型、通用骨架和必要中英文文字 |
| tests/role-shell.test.ts、app-router.test.ts、fixtures/navigation.ts | 三种角色真实组件渲染、入口、路由切换、越权与身份变化回归 |

验证：前端 7 个测试文件、110 项全部通过；应用与构建配置类型检查、生产构建通过。真实接口确认三个预览账号身份，普通用户和维护人员访问账号管理接口均返回 403。T08 未新增依赖或更改后端、迁移、种子；与此前 T07 一并纳入本次提交。

浏览器自动化此前存在本地导航中断，本次使用真实 Vue 组件 SSR 渲染与路由测试；未将实际浏览器点击、手机布局及 macOS 计为通过。查看入口、路径、文件归属和本机审查账号位置见 [T08 说明](13-role-navigation.md)。

补充流畅性修复：导航身份检查期间保留当前布局，成功后内容区用 160ms 淡入和轻微位移切换；检查失败仍清除受保护内容。连续点击共享检查并进入最后请求的页面，减少动态效果设置得到保留。新增 5 项回归，包含三种角色等待检查时的真实根布局渲染、失败隐藏内容和连续点击；Node 测试只替换浏览器 CSS 主题读取。

当前停点：T08 已完成，用户已授权提交并推送；T09 未开始。业务页面只显示标题和“暂未开放”，没有实现预约、审批或维修操作。

## 18 T07、T08 合并提交

用户已授权将此前未提交的 T07 普通账号登录注册、注册报错修复、T08 角色导航和内容过渡一并提交并推送至 Gitee 的 main 分支。具体提交编号和推送状态以 Git 日志及远程分支为准；第 15—16 节保留对应阶段的历史交付记录。

本次提交描述：

```text
feat: 添加账号注册登录、角色导航与平滑页面切换

- 支持普通账号注册登录、密码哈希及首个管理员初始化
- 移除学校认证与一键登录，保留 Cookie、CSRF 和角色权限校验
- 添加三种角色导航、工作台入口和业务页面骨架
- 修复注册报错与切页闪烁，加入内容过渡动画
```

本机账号、数据库密码、环境文件、日志、依赖和构建产物保持 Git 忽略。前端最近一次验证为 110 项测试、类型检查和生产构建通过；T07 后端及真实 HTTP 验证见其交付记录。T09 未开始。
