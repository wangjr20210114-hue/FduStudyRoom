# Web 与后端测试接口

## 当前基线的真实能力

- frontend/vitest.config.ts 的 test.environment 是 node；普通逻辑、路由和 SSR 测试仍在 Node 中运行。SSR 不能执行浏览器交互，也不能证明真实 Cookie 链路。
- `frontend/tests/shared/browser.ts` 通过 Node24 内置 WebSocket 驱动批准镜像中的 Chrome/CDP，不增加 npm 依赖。浏览器必须由协调人预装，成员不临时 npx 下载或改锁文件。
- 模块的 `*.browser.test.ts` 放在 `frontend/tests/modules/<模块key>/`，导入公共 `startBrowser`。Vitest 启动真实浏览器并加载实际产品 Vue 组件/路由，fixture 只提供 API 响应与初态，不另写一份假页面来代替产品。按页面 DOM、输入和点击结果作断言；记录 `--kind e2e`，报告仍是原生 Vitest JSON。
- 成员前端容器继续 network=none、公共目录只读、无 Docker socket 和其他成员凭据。测试 HTTP server 与 Chrome 同在本容器，仅访问 loopback。fixture API 能证明界面行为，不能冒充真实后端集成；后者在协调测试环境另验。
- 后端使用现有 JUnit/Surefire。记录器接受 Vitest JSON 和 JUnit XML，Chrome/CDP 故障属于 ERROR，不算业务 Red。
- 浏览器安装、协议示例、截图归档和实际验证见 [Web 浏览器基线](../../../docs/workflow/web-browser-baseline.md)。缺少浏览器或真实业务前置条件时，必需观察保留 BLOCKED，不降级成 SSR 或人工 PASS。

## cells 与真实测试 ID

每类至少映射一个真实 test_ids。criterion 为 acceptance 的 1 起始编号；basis 逐字等于该验收项。
expected 是具体结果，sample 写数据来源 / 初态，observation 是 DOM / 响应 / 字段 / 约束，comparison 是精确值、关系式或不变式。

ID 如 RES-01.S01#1。kind 为 unit/component/api/db/e2e/contract；前端至少有真实组件交互或 e2e，后端至少有 api/db/contract。
是否真的交互由实际测试核对，工具目前只校验声明类型与覆盖，不会因 kind=component 自动证明已操作 DOM。
每个 cell 若涉及多个验收依据，按 criterion 分成对应记录，不改 basis 原文，也不删除该类的其他观察。

测试 ID：

- Vitest：testResults.name 与 assertionResults.fullName 用双冒号连接，如 /workspace/frontend/tests/modules/resource/room.test.ts::创建教室。
- JUnit：testcase.classname 与 testcase.name 用 # 连接，如 com.campus.resource.RoomTest#rejectsOverlap。
- ID 唯一，skip/todo/error 不计 PASS。

## record

全部选项在最后的--前，测试参数在其后：

```text
python scripts/team.py record --worktree <工作树> --requirement RES-01 --stage red --output <工作树>/.campus/tdd.local --assertion <真实失败断言> --report /tmp/campus-reports/RES-01.json --require-test <真实ID> --kind component -- npm --prefix frontend test -- --maxWorkers=1 --reporter=json --outputFile=/tmp/campus-reports/RES-01.json
```

Green使用相同测试命令、必测ID和报告路径。record先删除该容器内已验证路径的旧报告，再运行并归档新报告。--report、--require-test都可重复。每次run保留新目录，不能拿旧报告充数。

后端使用实际类名，例如 `mvn -o -f backend/pom.xml -pl resource-service -am -Dtest=com.campus.resource.RoomTest -Dsurefire.failIfNoSpecifiedTests=false test`，报告路径为/workspace/backend/resource-service/target/surefire-reports/TEST-com.campus.resource.RoomTest.xml。核对真实服务和类，不盲抄示例。

模块基线与回归：--requirement M02 --stage baseline或regression。构建/类型检查：--requirement M02 --stage check，此阶段可不传报告，例如前端npm --prefix frontend run build。check不代替行为测试。

浏览器命令只增加模块测试过滤条件与 `--kind e2e`，例如：

```text
python scripts/team.py record --worktree <工作树> --requirement RES-01 --stage green --output <工作树>/.campus/tdd.local --report /tmp/campus-reports/RES-01.json --require-test <实际VitestID> --kind e2e -- npm --prefix frontend test -- tests/modules/resource/rooms.browser.test.ts --maxWorkers=1 --reporter=json --outputFile=/tmp/campus-reports/RES-01.json
```

Red/Green/Mutation 使用同一组测试、必测ID及报告路径。截图通过 `page.screenshot("/tmp/campus-reports/<场景>-<阶段>.png")` 保存，由协调人只读取回 `.campus` 证据目录并记录哈希；不提交截图、不手造报告。

## 判据

| 结果 | 依据 |
| --- | --- |
| PASS | 当前输入的必需断言实际执行且成立 |
| FAIL | 已到达目标行为，结果违背预期 |
| ERROR | 构建、驱动、报告或脚本错误妨碍断言 |
| BLOCKED | 缺账号、接口、数据或决定，记录解除条件 |
| NOT_RUN | 必测项未注册、未选中、skip/todo或未执行 |

Red必须有必测项FAIL，无ERROR/SKIPPED；Green全部必测项PASS、执行数非零、退出0。修改源码、测试或口径后按影响复验；模块收尾回归当前全部可完成需求。相同输入与覆盖的Green可复用为回归，build另留check回执。

每次record保留私有runs/<UUID>/中的run.local、stdout/stderr、实际报告、source.patch及变化输入快照。汇总收据指向最新有效结果，旧run不覆盖。日志、快照不进Git，版本化分析存本模块progress目录。

报告解析防止误收；独立CI和真人审核仍是提交门禁。浏览器视觉、跨服务真实链路未执行时不得记为完成。
