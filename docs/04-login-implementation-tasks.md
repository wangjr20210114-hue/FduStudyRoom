# 登录功能分步实施与交接

每次只执行一个编号任务。完成当前任务并进行相应验证后，报告新增或修改文件、实现功能、验证结果和下一项，然后停止。用户提出状态或 Git 问题时，先回答，再完成当前任务；只有明确要求暂停或改换目标时才停止当前任务。收到继续指令后再进入下一编号。

公共脚本使用 TypeScript 编写，构建为浏览器运行的 JavaScript。整体方案继续采用 Vue 3、Java Spring Boot 和 PostgreSQL；登录身份规则见 [前端与认证计划](03-ui-and-auth-plan.md)。

## 1 任务划分

| 编号 | 任务 | 主要文件范围 | 验收 | 状态 |
| --- | --- | --- | --- | --- |
| T01 | 前端运行骨架 | frontend 的依赖、入口、构建与类型配置，根目录启动说明及 Git 规则 | 可启动并返回前端资源；类型检查和正式打包通过 | 已完成 |
| T02 | 公共样式与基础组件 | shared/styles、shared/components | 统一主题、按钮、面板、输入框、加载和错误提示；桌面与手机可用 | 已完成 |
| T03 | 公共逻辑与国际化 | shared 的 API 客户端、工具和 i18n，zh-CN / en 字典 | 请求错误处理一致；语言可切换并保留选择；后续页面复用字典 | 已完成 |
| T04 | 登录后端 | backend 构建、identity、security、认证契约和必要数据库迁移 | 用户与角色、当前用户、退出和后端鉴权；开发身份入口与正式模式隔离；关键安全行为有测试 | 待开始 |
| T05 | 登录前端 | modules/auth 与公共组件组合 | 复旦认证入口、开发模式标识、处理中与失败状态，使用公共样式和字典 | 待开始 |
| T06 | 登录后基础布局 | app 路由与布局、导航、用户菜单 | 已登录可进入空工作台；未登录不能访问；菜单按权限显示；可以退出 | 待开始 |
| T07 | 正式复旦 UIS 联调 | identity 协议适配、回调和部署配置 | 真实校方凭据与资格核验通过；拒绝过期、重放及不符合资格的登录 | 待校方配置 |
| T08 | 完整登录流程验收 | 两服务、前端、构建入口及使用说明 | 类型、构建和真实流程验证通过，生产无模拟入口；Windows/macOS 运行步骤完成验收 | 待开始 |

T04 与 T05 可以先联调明确标识的开发身份，T07 才能认定正式复旦身份接入完成。普通用户限复旦学生；管理员和维护人员允许复旦学号或工号，权限由本系统授予。校方协议、回调和授权属性确认前，不将模拟认证当作真实登录，也不让用户在本系统提交学校密码。

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

## 8 当前停点与 Git 审查

T02 已按用户审查后的说明本地提交，提交号 1be62d3；当前尚未推送。远程仓库为 https://gitee.com/wangjr2021/fdu-study-room.git ，分支 main；T01 已推送，提交号 90d4e3c。

T03 已完成，用户已审查提交说明并授权本地提交；下一项为 T04 登录后端。每个任务完成后先报告文件、功能及验证结果，并展示拟用 Git 提交说明，等待用户审查；IDE 操作步骤见 [Git 指南](05-codearts-git-guide.md)。

下一项为 T04 登录后端，收到继续指令后再开始。届时定稿身份接口、Cookie 与 CSRF、开发身份隔离和数据库用户字段；本阶段的公共请求工具不代表认证已经接通。

T03 已审查的提交说明：

```text
feat(frontend): 添加公共请求与中英文切换

- 统一接口请求、超时取消和错误提示
- 加入中英文字典及语言选择持久化
```
