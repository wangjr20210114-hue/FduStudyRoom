# Web 与后端测试接口

## 当前基线的真实能力

- frontend/vitest.config.ts 的 test.environment 是 node；目前包含业务逻辑、路由、SSR 和 API mock 测试。
- SSR renderToString 能检查输出 HTML，不能执行浏览器点击、焦点、布局、CSS 动画或真实 Cookie 链路。
- frontend/package.json 没有 Playwright / 浏览器 runner；不要将 SSR 测试标成 component 交互来通过门禁。
- 后端使用现有 JUnit / Surefire。记录器接受 Vitest JSON 和 JUnit XML；支持某种格式不代表已验证任意 runner 的报告兼容。
- Playwright 是可选基线方案，不是当前已安装能力。补齐依赖、浏览器镜像、隔离初态、报告解析 / ID 映射和独立 CI 后才能写正式命令。
- 尚无可运行的 UI 自动化时，保留必需 UI 类的 BLOCKED 和人工观察；不静默降级为 SSR / mock，不把人工结果塞进自动收据。

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
