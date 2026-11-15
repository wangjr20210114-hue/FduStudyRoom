# 执行与检查点

命令从协调checkout的项目根目录调用；实现和测试在模块容器内进行，状态管理及只读分析可在宿主完成。

| next.stage | 操作与证据 |
| --- | --- |
| workspace | team.py worktree <身份> <模块> --layer <backend或frontend>；sandbox --worktree <路径> --start |
| baseline | record --requirement <模块ID> --stage baseline，真实报告 |
| requirements | campus-req；本层analysis.json |
| test-plan | campus-test；analysis.json的cells和测试ID |
| tdd | campus-tdd；逐需求有效收据 |
| regression | 模块regression覆盖必测ID，并有check构建回执 |
| upload | guard；submit --workflow <流程ID> --requirements <本轮ID> --evidence <收据> --type feat --summary <标题> --detail <分点> |
| waiting/blocked | 保存单号、原因、材料和解除条件 |
| development_complete | 本成员本轮开发已处理，集成/合并/发布另列 |

每个阶段实际完成后：

```text
python scripts/team.py workflow-checkpoint --run <流程ID> --module M02 --stage test-plan
python scripts/team.py workflow-checkpoint --run <流程ID> --module M02 --stage tdd --artifact <收据路径>
```

分析文件位于工作树 `spec/progress/<模块key>/<层>/analysis.json`；收据位于 `.campus/tdd.local`。网站真实返回单号后才进入submitted，不能靠写success=true跳过。

## 中断恢复

状态在 `.campus/workflows/<成员>-<层>-<基线摘要>/state.local`，每次阶段校验追加独立检查点。使用原子替换和短期写锁；遇到WORKFLOW_BUSY先核查写入者，不删活跃锁。

恢复核对身份、HEAD、基线、技能摘要、工作树、analysis、输入快照与网站状态。输入变化后重跑受影响测试；analysis改变时从相应阶段重建：

```text
python scripts/team.py workflow-checkpoint --run <流程ID> --module M02 --stage requirements --reset
```

reset只使后续检查点失效，保留源文件、原始运行和历史。新基线用新流程；技能升级先记录理由并复核证据，不能偷偷替换原指纹。

## 依赖与受阻

仅把实际依赖场景阻塞，其他场景继续。接口stub可用于隔离开发，不能证明真实集成。

```text
python scripts/team.py workflow-block --run <流程ID> --module M02 --requirement RES-08 --kind external --reason <诊断结果> --resume-when <可验证条件>
python scripts/team.py workflow-unblock --run <流程ID> --module M02 --requirement RES-08 --resolution <条件及证据>
```

kind为external、clarification、scope。问人前查原文、既有授权与实际配置；常规实现选择自行决定。重大歧义一次问清，记录实际回复；无回复不算同意。状态与对话不写口令。

相同无进展失败两次后，记录已试动作及下一诊断，继续独立任务或交接。有新证据指向可修原因时继续处理，不机械要求批准，也不无限重跑。

## 缓冲流转

沿用人工审核→独立CI→正式commit→72小时推分支/建MR。等待时保存检查点，单次查询，不快速轮询。全部剩余项等待后结束本次会话，后续同一入口恢复。

边界确认属于 requirements 准备步骤，不新增 STAGES；工具仍只接受 workspace、baseline、requirements、test-plan、tdd、regression。
