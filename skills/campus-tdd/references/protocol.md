# TDD 执行协议

模块代码、测试、字典与迁移只写spec白名单，公共部分只读。前端测试放frontend/tests/modules/<模块key>/，后端放本服务src/test/java/com/campus/<本域包>/。模块字典zh-CN.ts/en.ts使用modules.<前端目录名>.*，文案不回流全局。

迁移写db/modules/<模块key>/migration，种子写对应seed；跨模块走公开API，不写对方库或引用内部实现。独立开发后仍需真实集成。

## 1. 结果分类

| 结果 | 判定依据 | 后续动作 |
|---|---|---|
| PASS | 对应必需断言已执行且成立，运行器完成，退出码与计数一致 | 记录被测版本与证据 |
| FAIL | 测试已到达被测行为，实际结果与明示需求断言不同 | 关联场景 ID，作为 Red 或回归缺陷 |
| ERROR | 编译、服务启动、驱动、测试脚本或运行器异常妨碍断言执行 | 修复影响本题的执行问题并重跑 |
| BLOCKED | 必要账号、服务、容器、前置场景或用户决定尚未具备 | 记录解除条件，继续独立场景 |
| NOT_RUN | 必需用例未发现、未执行或被跳过 | 补齐注册 / 选择 / 执行并重跑 |

Spec 要求的入口或操作缺失，且已确认初态和定位正确时，归为 FAIL。
账号或外部服务尚未具备时，按依赖边界归为 BLOCKED。
驱动 API 错误、选错页面或连接了旧版本服务，先归为 ERROR 并校正执行条件。

运行器显示 `OK` 或返回 0 时，仍核对必需用例清单、实际计数与 skipped。
运行记录逐条保存结果；场景状态根据全部必需用例汇总。补充测试另列结果与发现。
必需断言缺少触发或观察证据时不能汇总 PASS；产品未响应和测试条件不足分别归因。


## 收据

每条需求version=2，completion_mode=tdd需red/green/mutation。baseline_satisfied需baseline/green/mutation，前两者产品摘要相同且来自模块起始产品。新增功能不能借“已有行为”省略Red。

命令格式见campus-test/references/check-plan.md。Red和Green测试命令、报告路径及必测ID相同；每次运行独立归档。场景可批量运行，各需求失败与覆盖证据仍单独保存，不能一个失败代表全部功能。

同一个收据文件默认串行执行 `record`，先等待上一命令退出，再启动下一阶段。原工作树与变异副本使用各自的 runner 和报告文件；只共享目标收据。记录器启动时短暂加锁分配运行顺序，测试期间不持锁，提交时重新读取收据、复核场景 / 正式需求边界并原子合并不同阶段。相同阶段（包括已有行为生成的 baseline / green）若已有更新运行提交，旧运行返回 `STALE_RECEIPT_RUN`，不覆盖新收据；其实际报告、输出和 `receipt_commit` 拒绝原因保留在独立 run 目录，不能手填阶段补回。

收据旁的 `.order.local` 保存运行顺序，随收据保留。`.lock/owner.local` 只用于短暂写入锁；锁忙最多等待 5 秒后返回 `RECEIPT_LOCK_BUSY`，不会按时间自动删除他人的锁。遇到遗留锁先核实拥有者和实际进程，确认没有活动记录器后再人工处理，不以删活跃锁绕过并发保护。

PowerShell 调用时把路径、失败断言、必测 ID 和 Maven `-D...` 属性各作为一个完整字符串参数（可用数组及 `& python @recordArgs`），不要拼成 shell 命令；例如 `"-Dmaven.repo.local=/opt/campus-cache/maven"`。含中文的参数或标准输入须使用 UTF-8，断言保持与实际报告一致。

## 独立变异

```text
python scripts/team.py mutation --worktree <原工作树>
python scripts/team.py sandbox --worktree <返回的副本路径> --start
python scripts/team.py record --worktree <副本路径> --requirement RES-01 --stage mutation --output <原工作树>/.campus/tdd.local --assertion <真实失败断言> --report <相同路径> --require-test <相同ID> -- <相同测试命令>
```

副本有不同runner/数据库命名，parent_sha256关联原Green输入。必须对应原Green源码摘要、相同命令及必测ID，并有产品差异。只破坏产品规则，不改弱测试；副本禁止submit。原源码保持不变，切回时显式用原工作树路径，不清理其他环境。

## 回归与交付

按改动文件、符号与引用关系做分级回归，模块收尾覆盖全部可完成需求并完成本层构建。相同输入和覆盖的最新Green可复用为回归证据。

上传diff后，真人审核和独立CI通过才由网站生成正式commit，72小时推分支和MR。冲突由人处理，生产需审批；缺外部条件保留原因和解除条件，mock不冒充真实验收。

## 参考证据与现有记录的对应

| 参考记录项 | 本项目真实位置 / 接口 |
| --- | --- |
| 每次新 run ID | .campus/runs/<UUID>/，record 自动生成，不手填 |
| 源码版本、未提交补丁和快照 | run.local、source.patch 及变化输入快照 |
| stdout / stderr、退出码 | 原始输出与实际退出码，record 自动归档 |
| 用例 ID / 计数 / FAIL 断言 | 原始 Vitest JSON / Surefire XML；解析后的必测结果 |
| 逐场景、等价类与触发 | analysis 的 cells / test_ids 关联实际 run；额外观察与截图保存到私有证据并注明来源 |
| HAP / 设备 / stage_commit | 改为容器与实际输入摘要；阶段证据留快照，正式提交由审核后的缓冲站产生 |

当前记录器不会自动采集浏览器截图 / trace，也不会自动证明观察器确已触发；无此证据不能填成已完成。
不能复制参考示例 JSON 后当成实际运行记录，也不能将人工记录伪造成 record 生成的回执。
