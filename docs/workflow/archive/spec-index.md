> 历史资料：已从业务spec拆分中移出，由workflow文档管理；不代表已经实现或验收。

# 全部任务和前后顺序

主说明见[六人分工与依赖图](17-previous-plan.md)。打开[可筛选拓扑图](previous-topology.html)查看先做什么、接着做什么及负责人；图的文字源文件见[topology.mmd](../../../spec/topology.mmd)。

本目录共81项任务、236个场景，覆盖54条原要求和8组用户补充。所有任务仍是草案，不表示代码已经完成。

- [每条要求对应哪些场景](coverage.md)
- [测试准备、待确认规则和外部条件](policies.md)
- [工具使用的源数据和完整场景](../../../spec/spec-network.json)

## 六人清单

- [a的工作单](../assignments/a.md)：账号权限、公共页面组件、任务安排和自动开发流程
- [b的工作单](../assignments/b.md)：教室座位、是否可用的共同规则和数据库升级
- [c的工作单](../assignments/c.md)：个人预约、签到、提醒、违约和完整业务验证
- [d的工作单](../assignments/d.md)：整室申请审批、自动测试和合并流程、课程评审
- [e的工作单](../assignments/e.md)：报修维修、安全验收、本机运行和部署
- [f的工作单](../assignments/f.md)：通知、操作记录、统计、缓冲网站和AI助手

## 修改后怎样更新全部文档

spec-network.json保存这份方案的原始数据。各任务文档、个人清单、要求对照表和图都由它生成。要修改场景，请先改源文件，再运行：

```sh
python tools/spec_plan/render.py
python tools/spec_plan/render.py --check
```

--check检查来源、要求是否齐全、链接、任务是否循环等待，以及生成文件有没有过时。它不开发业务、不登记成员，也不推送代码。当前6人运行权限尚未启用，先按S080处理公共工具准备。
