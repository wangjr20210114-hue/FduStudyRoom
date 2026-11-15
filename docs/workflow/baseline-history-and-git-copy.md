# 历史冻结版本验收与变异副本

## 已收件草稿如何保留原版本

缓冲站推进当前冻结版本后，已收件草稿仍按收件时的 `metadata.base` 和原补丁验收。协调人可在服务端私有配置中增加 `approvedBaselines`，内容只能是明确批准、仓库中存在的完整40位小写 Git commit SHA。

- `baseline` 始终是当前版本；不配置 `approvedBaselines` 时只允许当前版本，保持原行为。
- 列表仅来自服务端配置，不读取成员 metadata 中同名字段，不自动允许祖先或分支名。
- 无效列表、非完整 SHA、缺失对象、blob/tree/tag 等非 commit 对象会拒绝验证。删除批准项后，该历史版本不能继续验证。
- Java 上传接口仍只接收当前 `baseline`。历史列表只让已有旧单在真人审核后继续独立 CI，不重新接收旧版本上传。
- 历史草稿在自己的原始 base 上应用同一补丁；正式提交的父版本仍是原 base，不改补丁、不变基、不应用到新 base，也不自动处理 Git 冲突。
- 真人审核、模块白名单、真实独立 CI 和正式发布规则保持不变。允许历史版本不代表草稿自动验证通过。

只有协调人维护该配置；完整历史 commit 必须已保存在受信仓库，缺对象时先查明正确版本，不能把任意提交自动加进列表。

## Windows 下复制变异副本

`recording.mutation()` 的补丁应用统一调用现有 `policy.git_run`，保留原 patch 字节、`git apply --binary -` 和非零退出时失败的语义。命令取得已有的进程级长路径、精确仓库信任和受控 Git 配置，不修改全局配置，不改原模块文件或 TDD 收据。

Windows 回归实际创建 staged 新 SQL，并复制到大于260字符的变异目标路径，再检查副本内容、原 base、原补丁与全局配置均不变；Linux 明确跳过 Windows 专项，不能冒充已经验证 Windows。

测试中的宿主默认未显式设置 `core.longpaths`，与本次成员的故障条件一致。另已观察到宿主刻意设置全局 `core.longpaths=false` 时，Git for Windows 的深 UNC 操作仍可能拒绝；此最小修复不扩大修改共享 Git 策略。需要时继续使用协调人批准的进程级配置，并保留失败证据，不通过改名、改补丁或覆盖原输入绕过。

## 本次验证

- 原产品副本运行当前两项核心回归，得到2项预期断言失败，无运行错误；早期夹具配置错误不计为 Red。
- 修复后 Native Windows 相关回归26/26，包含实际大于260字符目标上的 staged 新 SQL 复制；输入补丁和全局配置保持不变。
- Linux 全工具回归124项，其中122通过、2项 Windows 专项明确跳过。
- 真实 Docker 历史单探针验证通过：新旧 Git commit 真实存在；先模拟 CI 失败不生成提交，再在同一原 base 上重试成功。使用真实隔离 PostgreSQL、SMTP 收件箱和 Java 数据库探针，检查源补丁、正式提交父版本、当前 HEAD 和自身资源清理。该探针不是六模块业务全量 Maven 验收。

私有证据位于 `.campus/codex-round3-20261115-0e4fdd/private/baseline-history-fixes-20261116/`，包含原产品副本、原始命令输出、补丁和 summary.local.json。不改 Java 上传规则、不改五个 skill 源、成员工作树或原收据。
