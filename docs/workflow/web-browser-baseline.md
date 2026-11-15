# Web 浏览器测试基线

## 执行环境

公共 helper 为 `frontend/tests/shared/browser.ts`。测试使用 Node24、锁定 Vitest 和已安装 Chrome/CDP，不增加 npm 依赖。`CAMPUS_TEST_BROWSER` 指定批准的可执行文件；未设置时检测常见 Chrome/Chromium 路径。浏览器不存在、启动失败、协议错误均立即暴露，不跳过必需测试。

Chrome 在成员前端容器内运行，容器保持 network=none、cap_drop=ALL、no-new-privileges、无 Docker socket，公共文件只读。Chrome 的 `--no-sandbox` 适配外层容器限制，不增加宿主或容器权限。测试只能导航到本容器的 loopback HTTP(S) 地址。页面本身的所有外部请求仍由容器网络边界限制。

## 编写测试

模块测试放 `frontend/tests/modules/<模块key>/*.browser.test.ts`，从 `../../shared/browser` 导入 `startBrowser`，由 Vitest 原生生成 JSON/JUnit 报告。测试自行启动本容器 HTTP fixture server；可用既有 Vite 编译真实 Vue 页面，或为接口提供确定的测试响应。不要改全局依赖、锁文件或共享入口。业务测试必须加载真实产品 Vue 组件/路由（例如由现有 Vite server 编译）；fixture 只提供初态与 API 响应，不能在测试 HTML 中重写一份同名页面来代替产品。公共 helper 自测的独立 Vue fixture 仅验证驱动本身，不计业务覆盖。

```ts
const page = await startBrowser()
try {
  await page.goto('http://127.0.0.1:' + port)
  await page.waitFor(() => !!document.querySelector('[data-testid="name"]'))
  await page.fill('[data-testid="name"]', '测试名称')
  await page.click('[data-testid="save"]')
  await page.waitFor(() => document.querySelector('[role="status"]')?.textContent === '已保存')
  expect(await page.text('[role="status"]')).toBe('已保存')
  await page.screenshot('/tmp/campus-reports/RES-01-S01-green.png')
} finally { await page.close() }
```

`evaluate` 与 `waitFor` 接收浏览器内的表达式字符串或不捕获 Node 闭包的函数。`waitFor` 是条件等待，超时会失败。`click` 通过 CDP 输入事件点击唯一可见元素，`fill` 聚焦输入框后通过键盘和插入文本事件替换内容；不使用 SSR 假装点击。`text` 要求唯一目标。需要下拉框等额外操作时可用 `evaluate` 操作实际 DOM，并按场景保留结果断言；自动化不支持的观察记为待验，不能跳过。

## 证据与边界

- 使用 team.py record 保存原生 Vitest JSON、真实测试ID和阶段快照，UI交互类标为 e2e。业务 Red 必须来自页面行为不符合需求，浏览器启动/脚本/报告错误属于 ERROR。
- screenshot 返回真实 PNG；落盘仅接受临时 `campus-reports` 或 `.campus` 私有路径。前端成员容器内 `.campus` 只读时，先写 `/tmp/campus-reports/`，再由协调人只读取回本人私有证据目录，关联场景、阶段、run ID及 SHA-256，不进入 Git。
- helper 只清理自己创建的临时 `campus-browser-*` profile；close 可重复调用。失败时先取截图，再 close。测试 server 也应在 finally/afterAll 中关闭。
- `page.metadata` 提供可执行文件、进程、临时 profile 和真实 user-agent，供运行证据记录。截图和账号数据必须使用隔离测试数据。
- fixture API 证明真实浏览器中的界面行为，不证明生产数据库、学校认证或跨服务联调。真实前后端联调、中英切换及 Cookie 会话另有协调环境验证后才报告整体验收。

## 基线自测

`frontend/tests/shared/browser.test.ts` 使用真实 Vue 运行时，覆盖输入、保存点击、中英切换、再次替换输入、PNG截图、条件超时、页面异常、缺失控件、外部导航拒绝、私有截图路径和 profile 清理。执行命令为：

```text
npm --prefix frontend test -- tests/shared/browser.test.ts --maxWorkers=1 --reporter=json --outputFile=/tmp/campus-reports/browser-baseline.json
```

实际镜像版本、执行退出码、报告和运行截图由本次协调人保存在私有 `.campus` 证据中。没有实测结果时，本文仅说明接口和检查方案，不代表验证已通过。

## 2026-11-15 协调验证

使用批准镜像 `fdu-campus-ci:browser-round3`（Chrome 157.0.8103.78），在 UID1000、只读源码、network=none、cap_drop=ALL、no-new-privileges 的独立容器中执行上述自测，4项全部通过，无错误或跳过。当前源码的 Vue/TypeScript 检查退出0。

独立容器只把 helper 的 `text()` 观察值改错，原测试立即以预期/实际不一致失败：4项执行，1项失败，无错误或跳过；原源码和测试不变。两份原生 Vitest JSON 均由现有报告解析器正确解析。真实 PNG 已取回并查看。

私有证据：`.campus/codex-round3-20261115-0e4fdd/private/browser-baseline/summary.local.json`、`green-report.local.json`、`mutation-report.local.json`、`green-screenshot.png`。这些结果只证明公共浏览器驱动与报告链路，不替代六模块业务验收、人工评审或真实跨服务联调。

## 构建可信镜像

先执行 `docker build -t fdu-campus-ci:core -f infra/team/ci.Dockerfile .`，再执行 `docker build -t fdu-campus-ci:baseline -f infra/team/browser.Dockerfile .`。核心镜像只预编译与预热锁定依赖；真实业务测试由带隔离数据库/邮件服务的workflow和CI执行。浏览器版本与SHA-256固定在Dockerfile，下载来源为Chrome for Testing官方存储，校验失败停止。
