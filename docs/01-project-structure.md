# 项目目录结构与模块归属

更新：2026年10月29日。技术基线：前端 TypeScript 与 Vue 3，后端 Java 与 Spring Boot，数据库 PostgreSQL。四名开发分为后端 A、后端 B、前端 A、前端 B。

当前已完成 T01 前端运行骨架、T02 公共样式组件和 T03 公共请求与国际化，入口为 frontend/src/main.ts。公共视觉参数位于 shared/styles，主题、基础组件及语言选择位于 shared/components；公共请求位于 shared/api，中英文字典位于 shared/i18n；开发预览入口为 /dev/components，用法见 [公共组件说明](06-frontend-components.md)。流程服务和其余业务模块仍为预留目录；空目录使用 `.gitkeep` 保留。登录按 [分步任务清单](04-login-implementation-tasks.md) 继续实施，每项完成后停止交接。

T04 已实现 platform-common 安全基础和 resource-service 的 identity 模块，Java 21 / Spring Boot 4.1.2，通过 Maven Wrapper 构建；身份迁移和运行方法见 [登录后端](08-login-backend.md)。workflow-service 仍预留，尚未加入当前构建。

T05 已实现登录接口接入，T06 已加入 Vue Router、共享身份状态、权限导航、个人信息及空工作台。/login 为精简登录页，首页进入工作台；本地 API 通过 Vite 同站点代理。用法见 [登录前端](09-login-frontend.md) 和 [T06 说明](10-workspace-and-navigation.md)。

数据库字段、约束、外键和关系图见 [数据库表结构设计](02-database-design.md)。前端风格及复旦统一身份认证的新方案见 [前端与登录实施计划](03-ui-and-auth-plan.md)；用户表的旧本地密码设计在后续定稿时按该计划修订。

## 一 目录树

下列目录已建立。`.codeartsdoer/` 为原有 IDE 配置，保持原状；树中省略 `.gitkeep`。Java 包路径折叠展示，实际采用对应的多级目录。

```text
new-project/
├── .codeartsdoer/                         原有 IDE 配置
├── README.md                             启动与验证说明
├── .gitignore                            依赖、产物与本地配置排除
├── .gitattributes                        文本换行规则
├── docs/
│   ├── 01-project-structure.md            本文
│   ├── 02-database-design.md              数据库表结构与外键
│   ├── 03-ui-and-auth-plan.md             前端与复旦认证方案
│   ├── 04-login-implementation-tasks.md    分步任务与交接
│   ├── 05-codearts-git-guide.md           IDE 提交与推送操作
│   ├── 06-frontend-components.md          公共样式与组件用法
│   ├── 07-frontend-api-and-i18n.md        公共请求与国际化用法
│   ├── 08-login-backend.md                身份后端与运行说明
│   ├── 09-login-frontend.md               登录前端与接口联调
│   ├── 10-workspace-and-navigation.md      T06 导航与访问控制
│   ├── adr/                              架构与公共层归属决策
│   └── acceptance/                       需求验收标准
├── contracts/
│   └── openapi/                          公共层和各模块的接口契约
├── backend/
│   ├── pom.xml、mvnw、mvnw.cmd             当前两模块构建与 Wrapper
│   ├── .mvn/wrapper/                      Maven 版本与下载校验
│   ├── .env.example                       后端环境变量模板
│   ├── platform-common/
│   │   ├── src/main/java/com/campus/common/
│   │   │   ├── api/
│   │   │   ├── error/
│   │   │   ├── security/
│   │   │   ├── tracing/
│   │   │   └── integration/
│   │   └── src/test/java/com/campus/common/
│   ├── resource-service/
│   │   ├── src/main/java/com/campus/
│   │   │   ├── identity/
│   │   │   ├── resource/
│   │   │   └── reservation/
│   │   ├── src/main/resources/db/
│   │   │   ├── migration/
│   │   │   ├── seed/
│   │   │   └── demo/
│   │   └── src/test/java/com/campus/
│   └── workflow-service/
│       ├── src/main/java/com/campus/
│       │   ├── notification/
│       │   ├── audit/
│       │   ├── approval/
│       │   └── repair/
│       ├── src/main/resources/db/
│       │   ├── migration/
│       │   ├── seed/
│       │   └── demo/
│       └── src/test/java/com/campus/
├── frontend/
│   ├── package.json、package-lock.json    依赖与命令
│   ├── index.html                        HTML 入口
│   ├── tsconfig.json、tsconfig.node.json  类型检查
│   ├── vite.config.ts                    开发、构建与本地 API 代理
│   ├── .env.example                       前端代理配置模板
│   ├── public/favicon.svg                项目图标
│   └── src/
│       ├── app/
│       │   ├── App.vue                   主题、会话检查与路由显示
│       │   ├── router/                   路由守卫、权限菜单与返回地址
│       │   ├── layouts/AppShell.vue       导航、用户菜单与主内容区
│       │   ├── pages/StatusPage.vue       工作台与必要状态页
│       │   └── dev/ComponentPreview.vue  仅开发环境的组件预览
│       ├── shared/
│       │   ├── api/                     请求客户端、错误及公共导出
│       │   │   └── generated/
│       │   ├── components/              AppThemeProvider、AppButton 等组件与语言选择
│       │   ├── i18n/                    中英文字典、类型与语言保存
│       │   └── styles/                  tokens.css、base.css、theme.ts
│       └── modules/
│           ├── auth/
│           │   ├── types.ts              认证模式与当前用户
│           │   ├── api/auth.ts           身份接口与 CSRF
│           │   ├── model/                登录状态及错误文案
│           │   └── pages/                LoginPage.vue、AccountPage.vue
│           ├── system/
│           ├── resource/
│           ├── notification/
│           ├── audit/
│           ├── reservation/
│           ├── approval/
│           └── repair/
├── infra/
│   └── env/
├── scripts/
├── skills/
│   └── campus-build/
│       └── references/
└── tests/
    └── e2e/
```

## 二 服务与数据边界

| 范围 | 负责内容 | 数据 schema | 唯一维护人 |
| --- | --- | --- | --- |
| resource-service | 身份权限、教室座位、M1、统一占用与限制、外部教室接入 | iam、resource、m1、integration、resource_meta | 后端 A |
| workflow-service | M2、M3、通知投递、审计汇总 | m2、m3、support、workflow_meta | 后端 B |
| platform-common | 响应格式、异常、认证基础能力、追踪和服务调用基础能力 | 无业务数据库 | 按下表指定到子目录 |
| frontend | 同一个 Web 应用，按权限显示页面，各业务模块独立维护 | 不直接访问数据库 | 前端 A 与前端 B 按模块划分 |

本地可在同一个 PostgreSQL 实例、同一个应用数据库内使用不同 schema。schema 是表的命名空间，不是独立服务或独立环境。两个服务使用独立数据库账号，只能操作自己拥有的数据域。开发、测试、生产分别使用独立实例或数据库、账号和配置。

同一服务内部允许实际外键，包括 iam 与 resource 之间的外键。不同服务之间只保存稳定业务 ID，通过 HTTP API 或公开事件契约交互；不建跨服务外键，不共享 Repository，不跨服务联表查询。

身份服务负责账号、角色和权限。第一版每个账号配置一个角色；普通用户的数据范围按本人记录判断，维护人员按工单分配判断，不建立组织层级和多范围授权模型。两个服务都验证请求身份并执行自己的业务授权；流程服务不得信任浏览器自行提交的角色或用户标识。

## 三 公共代码与公共业务能力

| 目录 | 职责 | 负责人 |
| --- | --- | --- |
| platform-common 的 api、error | 统一响应、分页、错误码和异常映射 | 后端 B |
| platform-common 的 security | 令牌校验、身份上下文、权限校验接入 | 后端 A |
| platform-common 的 tracing | 请求编号、日志上下文和跨服务追踪传递 | 后端 B |
| platform-common 的 integration | HTTP 调用、超时、重试、幂等基础约定 | 后端 B |
| resource-service 的 identity | 复旦统一认证接入、用户、角色与操作权限；第一版单账号单角色 | 后端 A |
| resource-service 的 resource | 教室位置、座位、每日开放时间、可选院系限定、全局参数、统一占用 | 后端 A |
| workflow-service 的 notification | 站内通知和邮件投递、失败重试 | 后端 B |
| workflow-service 的 audit | 审计接收、去重与查询 | 后端 B |
| contracts/openapi | API 请求、响应、错误与安全要求 | 接口提供方维护，调用方评审 |

公共库只包含无业务数据所有权的基础能力。用户、教室、审批、工单的实体与数据库访问实现分别留在自己的服务。业务状态枚举按模块维护，公共错误码和权限编码通过契约协调。

## 四 前端模块与角色

| 目录 | 内容 | 负责人 |
| --- | --- | --- |
| app | 应用启动、公共布局、路由汇总、权限入口 | 前端 B |
| shared/api | 请求客户端、身份传递、统一错误处理 | 前端 B |
| shared/api/generated | 后续从 OpenAPI 生成的客户端与类型 | 接口更新后统一生成 |
| shared/components、shared/styles | 可复用视觉组件、样式规范 | 前端 A |
| modules/auth、modules/system | 登录、当前用户、用户角色和参数管理 | 前端 B |
| modules/resource | 教室、座位、每日开放时间与可选院系编码管理 | 前端 A |
| modules/notification、modules/audit | 通知、审计查询 | 前端 B |
| modules/reservation | M1 的普通用户与管理员页面 | 前端 A |
| modules/approval、modules/repair | M2、M3 的普通用户、管理员、维修页面 | 前端 B |

一个业务模块的普通用户页面和管理页面归同一位前端开发。模块自己的页面、状态、接口封装和路由在模块内维护；app 只汇总入口，避免所有业务集中修改同一个文件。

| 默认角色编码 | 名称 | 默认职责 |
| --- | --- | --- |
| USER | 普通用户 | 经核实的复旦学生进行查询、预约、签到、申请、报修、本人验收与评价 |
| SYSTEM_ADMIN | 系统管理员 | 身份权限、资源与参数、审批、工单调度、统计、审计和同步管理 |
| MAINTAINER | 维护人员 | 处理分配给自己的维修任务并申请验收 |

三种是默认角色，角色可扩展；第一版每个账号配置一个角色。普通用户限复旦学生；管理员和维护人员也允许复旦工号，但必须先由本系统授权。学生身份的管理员或维护人员可在其角色中包含学生预约权限，工号账号按管理或维护职责使用。管理员按指定节点进行多级审批，禁止申请人自审及同一账号重复通过同一申请的多级审批。维修人员不得验收自身维修。前端控制入口，后端控制操作与数据范围。

## 五 模块内部的组织约定

| 后端业务模块内部目录 | 职责 |
| --- | --- |
| api | Controller、请求响应对象、输入校验 |
| application | 用例编排、事务、跨模块协调 |
| domain | 业务状态、业务规则与领域接口 |
| infrastructure | 数据库、邮件和远端接口等具体实现 |

这些子目录在实际实现模块时创建；当前仅建立模块根目录。Java 基础包采用 com.campus，公共库为 com.campus.common。公共库编译依赖保持单向，两个服务不能互相作为构建依赖。

前端模块内部后续按 pages、components、api、store、routes 等实际需要组织。接口类型以 OpenAPI 为来源，生成目录中的文件通过生成流程更新。

## 六 数据文件与配置的归属

| 目录 | 内容 | 规则 |
| --- | --- | --- |
| 各服务 db/migration | 本服务 schema 与数据演进 | 版本化、可重放，已发布版本不原地修改 |
| 各服务 db/seed | 权限、默认角色、正式默认参数 | 幂等；不覆盖管理员已修改的业务配置 |
| 各服务 db/demo | 模拟账号、教室、预约和工单 | 仅开发、测试或演示环境启用 |
| infra | Compose 等基础运行定义 | 后端 B 维护，另一名后端评审 |
| infra/env | 配置模板与环境说明的预留位置 | 真实密码、令牌和密钥不纳入版本管理 |
| scripts | macOS 与 Windows 检查、构建、启动入口 | 后端 B 维护后端和容器流程，前端 B 维护前端步骤 |
| tests/e2e | 跨前后端和跨服务的行为验证 | 按需求分配维护人，使用隔离测试数据 |

公共层先建立 roles、permissions、role_permissions、users，再建立 rooms、seats、system_settings 和通知、审计、service_tasks。M1、M2、M3 按后续迁移增加；第一版没有组织表、用户角色范围表或通用流程模板。两个服务的 Flyway 历史分别保留在各自 meta schema。

## 七 build skill 的阶段

skills/campus-build 保存项目内可版本管理的 skill 源文件；SKILL.md 及脚本在后续实现，不在本次创建。入口先支持 macOS 与 Windows，统一使用容器运行应用依赖。

| Stage | 工作 | 通过条件 |
| --- | --- | --- |
| 0 | 读取项目规则、配置和构建入口 | 项目实现与运行目标明确 |
| 1 | 检查系统、架构、资源、端口、网络、Git 与 Docker | 缺项可定位 |
| 2 | 从受信来源补齐缺失依赖，确认容器引擎运行 | 已有兼容环境得到复用 |
| 3 | 首次获取仓库，按明确更新模式使用源码并记录 commit | 保留本地改动，版本可追溯 |
| 4 | 准备本地环境配置与开发密钥 | 必填项齐全，秘密不入库或日志 |
| 5 | 构建前后端并运行基础检查 | 构建和检查通过 |
| 6 | 启动 PostgreSQL 等基础依赖并等待就绪 | 数据库可连接、数据卷正确 |
| 7 | 执行迁移和幂等种子初始化 | 空库与已有库均可正确启动 |
| 8 | 启动两个后端和前端 | 就绪检查通过 |
| 9 | 执行当前已实现能力的真实验证 | 登录、权限、资源及服务调用符合预期 |
| 10 | 输出地址、版本、服务状态和日志位置 | 可以访问，失败可定位并恢复执行 |

依赖缺失可自动补齐；缺少业务实现时报告未完成项。失败停止后续阶段并保留日志，重复运行保留数据库数据。首次系统安装涉及重启、虚拟化或必要人工操作时，记录停留阶段供处理后继续。

## 八 后续按阶段新增的文件

T04 已加入后端根构建、公共库和资源服务 pom.xml、Maven Wrapper、身份迁移，以及 PostgreSQL Compose 和环境模板。下列文件仍待后续实现时创建。前端 package.json、锁文件、类型与构建配置，以及根目录 README.md、.gitignore、.gitattributes 已在 T01 加入；详情见分步任务清单。

| 文件 | 用途 | 负责人 |
| --- | --- | --- |
| AGENTS.md | 项目规则；启动说明已放入 README.md | 团队评审，后端 B 汇总 |
| backend/workflow-service/pom.xml | 后续流程工单服务构建 | 后端 B 协调，服务负责人维护本模块 |
| contracts/openapi 下其余模块契约 | 身份契约已实现，按后续模块补充 | 接口提供方 |
| infra 生产部署与完整服务编排 | 当前 Compose 已提供独立 PostgreSQL，完整部署随后续任务实现 | 后端 B |
| scripts/build.ps1、scripts/build.sh | 两种平台的运行入口 | 后端 B 与前端 B |
| skills/campus-build/SKILL.md | build skill 的判定和阶段流程 | 后端 B 汇总评审 |

## 九 公共层实施顺序与验收

| 轮次 | 交付 | 验收 |
| --- | --- | --- |
| 1 | 运行骨架、迁移入口、健康检查、build 入口 | macOS 与 Windows 均能启动前端、两个后端和 PostgreSQL |
| 2 | 登录、三种默认角色、权限与关键操作审计 | 角色入口正确，后端拒绝越权操作 |
| 3 | 教室位置、座位、每日开放时间、可选院系限定与参数 | 管理员可维护，普通用户可查询符合条件的资源 |
| 4 | 通知、审计查询、服务认证、幂等与追踪 | 真实网络调用可追溯，重复请求与失败恢复正确 |

公共层完成后，从空数据库运行 build skill，应能够登录三类账号、维护和查询资源、看到关键操作审计。随后按需求与验收标准实现 M1、M2、M3 和跨模块状态写回。

## 十 数据库精简决策

完整保留任务书的 M1、M2、M3、公共层与工程要求，数据库从 40 张应用表精简到 19 张；工具维护的两张迁移历史表另计。字段、约束、每个字段的用途以及最终关系图统一见数据库设计文档。

| 原设计 | 当前处理 | 保留的能力 |
| --- | --- | --- |
| 组织表、组织类型、组织树、范围授权 | 删除；用户和教室各保留可选 department_code | 院系专用自习室的最小资格匹配 |
| 校区和楼宇独立主表 | 合并为教室上的 campus、building 文本属性 | 校区、楼宇筛选和教室定位 |
| 一人多角色及多客户端会话体系 | 第一版单角色；复旦统一身份认证与 auth_version | 三种默认角色、可新增角色、令牌失效和权限校验 |
| 角色和权限目录 | 保留 roles、permissions、role_permissions | 任务书明确要求的可配置角色及权限 |
| 独立签到码、签到和违约表 | 每日签到码放 rooms；签到时间和 NO_SHOW 放预约记录 | 每日教室码签到、违约记录、历史与运营统计 |
| 通用审批模板与版本管理 | 申请加逐级审批任务；系统参数保存至少两级审批人顺序 | 权限门槛、多级审批、驳回与超时撤销 |
| 独立多轮验收表 | 最终验收与评价放工单，历史过程放 repair_events | 维修中、验收失败返工、通过后恢复与评价 |
| 附件和照片体系 | 第一版采用文字报修；统一身份认证按新增登录方案单独接入 | 故障上报、派单、维修和完整操作流程 |
| 专门的资源变化日志 | 合入统一 audit_logs，记录来源、业务 ID、前后摘要 | M1、M2、M3 写入来源和状态追溯 |
| 多数据源、映射审批和同步批次平台 | 教室保存外部编号，一张按教室与日期的查询缓存 | 用户希望的复旦空教室只读查询、范围与时效 |
| 通用 outbox 与独立幂等框架 | 每服务一张 service_tasks；业务 UUID、唯一约束和来源版本去重 | 真实网络调用、失败重试、乱序保护与状态对账 |

用户院系编码仅为经核实的在读学生填写，其他用户为空；由管理员或可信导入维护，普通用户不能自行修改。教室的 department_code 为空时全校开放；非空时必须匹配用户的 department_code。这是任务书对院系自习室的必要限制，不扩展为院系业务系统。

service_tasks 只处理本项目公开接口的占用、释放、维修下架、恢复、通知和审计。业务变化与任务同一事务提交，接收方按来源 ID 和版本幂等；不引入事件总线、插件化流程设计器或通用分布式事务框架。

数据库精简不减免工程交付：需求与验收可追踪，项目规则、决策、迁移和流水线随 Git 管理；提交及合并请求触发有效测试，测试不过禁止合并；测试环境自动整组部署，生产需人工审批、版本 tag 和可演示的回退；部署窗口或互斥避免集成测试撞上部署。AI 生成变更由人工验收，代码冲突由人处理，工程伦理问题在开发中记录。

## 十一 前端风格与复旦登录的新决策

前端保持 Vue 3 与 TypeScript，参考 WisePen 的浅色面板、统一圆角、可折叠侧栏和柔和装饰背景。公共样式及组件由前端 A 维护，布局汇总和认证页面由前端 B 维护。设计参数、原型范围和实现阶段见 [前端与登录实施计划](03-ui-and-auth-plan.md)。

正式登录目标改为复旦 UIS：普通用户限经核实的复旦学生，管理员和维护人员允许学号或工号，管理权限由本系统授予。学校具体协议、返回属性、应用获批及回调登记仍待确认；数据库用户字段按新增计划统一修订。

build skill 不能自动获得校方接入授权。开发可用明确标识的模拟身份进行联调，生产必须禁用模拟入口；正式认证验收依赖已获批配置和测试账号，缺失时准确报告未就绪。
