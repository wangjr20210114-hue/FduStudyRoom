# Harmony workflow 的原文复用与平台差异

本轮是协调基线的技能改造，未执行六模块业务开发。参考来源为用户提供的“鸿蒙杯/提交物/system-config/skills”v2.5.1；原文件未修改。
需求分析、检查方案、等价类、TDD 判据、失败归因与分级回归以参考原文为底稿，保留可用段落，逐处替换平台、记录接口及项目规则冲突。
这不等于 Harmony 的工具已在 Web 上移植。精确来源与目标摘要见 [复用清单](reference-reuse.json)。

## 直接使用或只换名称 / 路径的部分

| 参考部分 | 保留的内容 | 本项目落点 |
| --- | --- | --- |
| harmony-req | 全文保留、一个原文场景一个分析单元、名词到对象对应、排除采样缺口再判缺实现、Q/U/A、逐段回查 | [campus-req](../../skills/campus-req/SKILL.md) |
| harmony-test / check-plan | 每类怎样算对、来源、测试类型、样本初态、观察位置、精确值 / 关系 / 不变式；按等价类汇报 | [campus-test](../../skills/campus-test/SKILL.md)、[检查方案](../../skills/campus-test/references/check-plan.md) |
| equivalence-classes | 以数据为主、常规情况 #1、多入口、边界、少量相关界面状态；不以现状当预期 | [等价类](../../skills/campus-test/references/equivalence-classes.md) |
| boundary-and-recovery | 真实触发证据、p−1/p/p+1、虚拟列表、分阶段中断、幂等恢复与检出验证 | [边界与恢复](../../skills/campus-test/references/boundary-and-recovery.md) |
| harmony-tdd-code | 有效 Red、同断言 Green、已有行为验证、基线失败归因、先找真实文件、最小实现、分级回归 | [campus-tdd](../../skills/campus-tdd/SKILL.md) |
| harmony-boundary | UI / 数据事实清单、未核验项必须有已试动作、声明不等于可达、事实不替代需求 | [边界确认](../../skills/campus-workflow/references/boundary.md)；总流程 Stage 2.5 |
| human-decision | 查得到先查、只问自己定不了的口径 / 资源、自然语言问题、真实回复回写 | [向人提问](../../skills/campus-workflow/references/human-decision.md) |
| 单题 / 全局主流程 | 基线→边界→需求→逐场景测试实现→回归→人工验收与交付；检查点恢复、继续独立项 | [campus-workflow](../../skills/campus-workflow/SKILL.md) |

## 不能原样复制的部分

| 参考写法 / 工具 | 不能直接复制的原因 | 当前处理 / 后续可选方案 |
| --- | --- | --- |
| DevEco / ArkTS / ohpm / hvigor / HAP / HDC / Hilog | Web 与 Java 工程没有这些构建、安装和设备接口 | 保留 campus-build 的 Node 24、Java 21、Maven Wrapper、PostgreSQL、Docker 步骤 |
| Hypium、设备 UI 树、.ets / Python UI 模板 | 浏览器需要 DOM、页面状态、真实服务和浏览器驱动 | 复用测试设计方法，替换执行器；未接入前列 UI 自动化缺口，不复制无法运行的命令 / 模板 |
| 每个场景同时单元 + UI | 我们还有纯后端的权限、事务、约束、持久化场景 | 按观察目标选 unit/component/e2e/api/db/contract；有界面行为的场景仍需交互证据，不能全部变纯逻辑 |
| requirements-analysis.md / components.md / boundary.md | 现有白名单与检查点使用本层 analysis.json；任意新增文档路径可能越界 | 原文快照、边界、对象、问题、cells 映射到 analysis.json；新增字段由会话回查，不虚构自动校验 |
| S01… 与一题一 Spec | 当前已有业务 Sxxx.SCxx，执行校验器另用 <需求ID>.Sxx | 关联两套 ID，保留业务原文与用户指定场景格式，不重编号，不把草案升级成批准验收 |
| 固定三轮、每轮一次 Red / Green | 原本优化 HAP 安装；当前工具逐需求保存完整测试集收据，多轮局部测试会覆盖证据 | 按依赖和改动分轮，每条需求独立完整证据；若坚持固定三轮，需先改造收据聚合与检查点 |
| 每个 Red / Green / Refactor 直接 commit | 违反本项目“人工验收前不得正式提交”的规则 | record 保存快照；人工审核与独立 CI 后由缓冲站提交，72 小时推功能分支 / MR |
| Green 后改 components 状态 | analysis 已被 test-plan 固定摘要；原地更新将使证据失效 | 实现状态记私有 runs，沿用对象 key；需求 / 方案变化先 reset，再重建证据 |
| 场景把“已授权不测”也汇总 PASS | 现有门禁要求必需用例全部实际执行通过 | 豁免单列，正式验收范围改变须更新批准基线；不能拿口头不测伪造测试通过 |
| PreToolUse 守卫、跨题来源黑名单 | 绑定 Claude hooks、题包与特定来源库，不能等同本项目 OS 级文件权限 | 保留模块容器强制隔离和 guard；不因复制 hook 就声称隔离完成 |
| 多终端交互式 Claude + 模拟器预留 | 当前入口兼容 Codex / Claude；模块工作单、认证、端口、数据库与容器才是资源边界 | 沿用 worktree / sandbox / workflow 状态；没有新增并行代理调度或伪造 a–f 授权 |
| 赛事 add / report / seal / pack | 依赖鸿蒙赛事模板、官方检查、题目封存和 ZIP 格式 | 使用现有缓冲站上传、人工审核、独立 CI、正式提交、72 小时分支 / MR |
| 原生会话全文 export-trace 与跨题轨迹审计 | 依赖客户端会话存储，可能含项目之外信息；本项目上传边界是模块 diff 与测试证据 | 不自动复制全部个人会话；如要加入，另定客户端适配、范围和敏感内容处理 |

## Web UI 测试的实际状态

当前 frontend/vitest.config.ts 明确使用 node。已有逻辑、路由、SSR 和 API mock 自动测试；例如 text-field 与 role-shell 使用 renderToString。
这些测试有价值，但不能证明真实浏览器点击、焦点、布局、CSS 动画和跨服务 Cookie。
目前 package.json 没有 Playwright，仓库没有完整的浏览器 runner 配置。不能把“有 Vitest”说成“浏览器 UI 自动测试已完成”。

可以使用 Playwright 承担与 Hypium 对应的浏览器自动交互。它支持 Chromium、Firefox、WebKit，以及 Windows / macOS / Linux；
提供自动断言、隔离和 HTML / JSON 等报告。依据：[官方介绍](https://playwright.dev/docs/intro)、[报告文档](https://playwright.dev/docs/test-reporters)。
这里仅提出适配方案，本轮没有安装依赖、下载浏览器或修改公共锁文件。

如果采用，需要一起补齐以下公共基线，不能只在 skill 里换个工具名：

1. 锁定依赖与浏览器版本、模块测试发现路径、可访问测试 URL 与测试账号 / 隔离数据。
2. 容器内浏览器运行条件；每例独立上下文，避免成员共享登录态；不挂 Docker socket 或生产凭据。
3. DOM / role / testid 定位、条件等待、截图 / trace、网络和控制台证据；与场景、等价类 ID 对应。
4. 报告适配与稳定 test_ids：当前记录器解析 Vitest JSON / JUnit XML，但尚未验证 Playwright 输出兼容性与失败归因。
5. 真正的业务 Red→相同测试 Green、独立变异与当前源码回归，以及缓冲站独立 CI 的同等执行能力。

在此之前，必需 UI 类保持 BLOCKED / NOT_RUN；可以写好检查方案、完成独立逻辑和后端部分，
人工浏览器结果另列，不能冒充自动 TDD 证据，也不能将 UI 未验的需求标为全部完成。

## 保留的项目约束

五个技能入口和现有六个工具检查点保持兼容；Stage 2.5 是 requirements 前的准备步骤，没有新增不存在的命令。
六模块、成员工作单、TypeScript / Vue 3、Java 21 / Spring Boot 4.1.2、PostgreSQL、模块中英字典与迁移边界保持现有规则。
固定三轮、双测试、阶段提交、赛事交付等有差异的地方均明确列出，没有借“原样照抄”绕过项目授权。

本轮修改技能与工作流说明，不改业务 spec、运行工具实现、公共依赖或缓冲站审核规则，不安装覆盖用户已安装技能。
安装目录中的旧技能不会自动变更；发布优化基线后再按 docs/16 的安装流程核对更新。
