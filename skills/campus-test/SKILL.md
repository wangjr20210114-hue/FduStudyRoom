---
name: campus-test
description: 为 FduStudyRoom 场景设计等价类，编写并运行现有 Web / Java 测试，记录实际结果和浏览器待验项。用于测试设计、自测及模块工作流的测试阶段。
---

# 测试编写与运行

## 目标

为当前场景划分等价类，为每个等价类编写并运行测试，记录实际结果与证据。
预期只来自 analysis.json 中保存的场景原文与批准验收；原文没写的口径进需求问题表问人，测试不自行补。

## 执行条件

- 设计模式进入 test-plan，只设计方案；运行模式由 TDD / 回归给出 baseline、red、green、mutation、regression 或 check。
- 身份、模块、层、白名单与容器已核对；缺需求依据回到 campus-req，缺执行条件记录受阻。

## 输入

模块工作树、analysis.json、边界事实、当前阶段、冻结基线与公开契约。
设计记录和命令见 [检查方案](references/check-plan.md)；工具适配见 [Web 与后端测试接口](references/web-adapter.md)。

## 执行要求

- 检查方案里的每个等价类至少映射到一个测试，按 [检查方案](references/check-plan.md) 选类型，不按省事选。
  有界面行为的场景需要交互证据；后端场景选 API / 数据库 / 契约检查。纯后端行为不强制补 UI。
  node / SSR 不等于浏览器交互，缺浏览器条件的必需类保持 BLOCKED，不靠类型标签补成通过
- 每个测试实际运行，按 [TDD 记录协议](../campus-tdd/references/protocol.md) 保存结果；
  阶段快照遵循 `campus-tdd`「执行流程」，不在人工验收前生成正式提交
- 前置场景未 PASS 时，当前场景标 `BLOCKED`，先做其他无依赖场景
- 多个场景合并成一轮实现时（见 `campus-tdd`「实现轮次」），轮内每个场景各建检查方案、各写测试文件，
  按轮组织构建和测试（每条需求单独运行并保存完整 Red / Green 收据）；结果仍按场景、按等价类汇报
- 需要人裁定时按 [向人提问](../campus-workflow/references/human-decision.md) 提问
- **结果按等价类汇报，不按测试个数**：如 `RES-01.S01：6 个等价类，3 PASS，3 BLOCKED（#2–#4 缺可用浏览器环境）`。
  受阻等价类的测试不能从运行里拿掉或不写，来凑 `N/N OK`；有受阻等价类的场景不能报"通过"
- 只验证本模块本轮新增或修改的部分，以及受其直接影响的测试；
  基线失败和外部阻塞按 `campus-tdd`「基线失败处理」归类

## 执行流程

### Stage 1: 建立检查方案

读取当前场景的 Spec 原文、场景原文和对象对应，按 `references/check-plan.md`
划分等价类（`references/equivalence-classes.md`），为每个等价类写清怎样算对、选测试类型、选样本、定观察位置和比较方法，
写入 analysis.json 当前场景的 cells。设计模式固定 test-plan 检查点后交回 campus-tdd；运行模式继续以下步骤。
涉及集合、分页、多步写入时同时读 `references/boundary-and-recovery.md`。

### Stage 2: 生成测试

复用已验证的准备、等待、取证和清理结构，只填入检查方案中的操作与断言。
必需的等价类未定位、缺观察条件时保留缺口继续其他等价类，不能删断言后报告完成。

### Stage 3: 运行单元、组件或后端测试

测试只放 manifest 对应模块路径。前端使用锁定 Vitest，后端使用现有 JUnit / Surefire。
按 [运行接口](references/web-adapter.md) 通过 team.py record 在模块容器执行，保存实际报告和必测 ID。
核对 N > 0、必需用例全部执行、退出码与报告一致；构建成功不能替代行为断言。

### Stage 4: 运行浏览器测试并保存证据

先核对基线是否已有经批准、可执行的浏览器 runner、报告适配和隔离配置。
当前 node 环境与 SSR 测试不能证明真实点击、布局、焦点、动画或 Cookie 跨服务链路。
尚未具备自动浏览器链路时，保留检查方案、初态、步骤、预期和缺口；人工执行的结果单列，不能写入自动 TDD 收据冒充通过。
浏览器工具尚未接入时不临时 npx 下载、不改公共依赖；按项目根目录的 `docs/workflow/harmony-reuse.md` 提出基线方案。

按 PASS / FAIL / ERROR / BLOCKED / NOT_RUN 记录真实结果和触发状态，运行器 OK 不代替逐个等价类验证。
必需 UI 类尚未验证时，当前需求保持未完成；其他无依赖场景继续。

## UI 测试失败升级

同一个 UI 测试错误最多修改并复验两轮。两轮后仍失败：停止盲目修改，保留最后的错误、截图和日志，
提交问题与证据；需要用户决定时等待回复，其他独立任务继续。有新证据指向可修原因时再处理，不无限重跑。

## 编写要点

- 1 个 `it()` / 1 个 JUnit 测试方法 = 1 个独立行为；用例名或注释标出覆盖的等价类（`S01#2`）
- 期望值只来自场景原文、接口约定或关系式；当前实现的返回值不能当预期
- 不能在生成或修复测试时自行增加预期、编造字段或削弱必需断言
- 分支用例同时写触发前置与触发证据；条件不满足记 BLOCKED，不记 PASS
- 条件等待（按实际 runner 的条件等待）加超时；定位优先级：稳定 key > 唯一文本 > 层级属性 > 坐标
- 每个用例独立准备初始页面和数据；失败先截图再报告
- 实际截图保存在本模块工作树 .campus 的私有证据目录，关联场景、阶段、run ID 与 SHA-256；不写入业务源码或 Git
- 新增界面文案与交互覆盖本模块 zh-CN / en；验证切换语言后的结果，文案不写回全局字典
- 报告缺陷写全控件、操作、实际表现、预期依据和证据位置

Spec 之外的补充异常测试单独统计，不自动升格为需求；已确认的崩溃、数据丢失、无法恢复问题写入交付待办。

## 输出

- 设计模式：analysis.json 的 cells、真实 test_ids、覆盖关系和未决问题。
- 运行模式：测试文件、实际报告、原始输出、输入快照及收据；逐场景 / 等价类汇报。
- 浏览器与真实集成的已运行、未运行、受阻及人工结果分别列明。

## 参考文件

| 文件 | 用途 |
| --- | --- |
| [check-plan.md](references/check-plan.md) | 等价类、怎样算对、样本、观察、比较与 cells 映射 |
| [equivalence-classes.md](references/equivalence-classes.md) | 以数据为主、补少量界面状态；沿用参考原文的切分方法 |
| [boundary-and-recovery.md](references/boundary-and-recovery.md) | 分页、真实触发、多步写入与恢复；沿用参考方法 |
| [web-adapter.md](references/web-adapter.md) | 现有 Vitest / Surefire 命令与 Web 能力边界 |
| [protocol.md](../campus-tdd/references/protocol.md) | 实际记录与独立变异协议 |

## 完成条件

- **设计**：每个必需类都有来源、怎样算对、样本、观察、比较、类型和真实 test_ids；test-plan 检查点通过。
- **Red**：所需测试已执行，满足 campus-tdd「阶段判据」，保存失败断言与运行证据；全部断言已满足时记录已有行为验证。
- **Green**：全部必需等价类的测试 PASS，目标分支已触发且有证据，保存测试证据及版本关联；未触发、未覆盖的等价类单列。
- **交接**：BLOCKED / NOT_RUN / ERROR 不计 PASS；不将 SSR、mock 或人工观察冒充真实浏览器自动验证。
