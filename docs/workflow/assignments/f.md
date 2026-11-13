> 历史资料：已从业务spec拆分中移出，由workflow文档管理；不代表已经实现或验收。

# f的任务清单

领域：通知、操作记录、统计、缓冲网站和AI助手。本模块前后端都由本人负责；字母代号只是分工，不是真实登录账号。

另外负责：维护成员登录与审核、72小时同步、邮件发送、AI助手和故障验证。

## 负责清单

| 任务 | 类型 | 场景数 | 大致难度 | 验收前需要的任务 |
| --- | --- | --- | --- | --- |
| [S036 实际测试断网、重复消息和消息晚到的情况](../archive/S036.md) | 真实服务连通验证 | 3 | 4 | S034、S035、S044、S060 |
| [S061 约定通知、操作记录和统计怎样交给支持模块](../archive/S061.md) | 接口约定 | 2 | 2 | S001、S010 |
| [S062 查看站内消息，避免重复通知和过时提醒](../../../spec/specs/S062.md) | 页面和业务功能 | 3 | 3 | S007、S023、S061 |
| [S063 保存关键操作记录，并隐藏密码和密钥](../../../spec/specs/S063.md) | 页面和业务功能 | 3 | 3 | S007、S023、S061 |
| [S064 管理员查看预约、签到和违约统计](../../../spec/specs/S064.md) | 页面和业务功能 | 3 | 3 | S020、S024、S032、S033、S061 |
| [S065 成员领取自己的任务，并自动交给缓冲网站](../archive/S065.md) | 全组共用的准备工作 | 3 | 3 | S010、S011、S012、S061、S080 |
| [S066 缓冲网站先收人工审核，再独立运行测试](../archive/S066.md) | 全组共用的准备工作 | 3 | 3 | S045、S065 |
| [S067 每72小时把已审核的功能分支推到Gitee](../archive/S067.md) | 全组共用的准备工作 | 3 | 3 | S066 |
| [S068 接入AI助手，先问清需求再操作](../archive/S068.md) | 附加AI助手 | 4 | 4 | S001、S025、S037、S049、S061、S045 |
| [S069 用AI助手查座位、预约和查看自己的历史](../../../spec/specs/S069.md) | 附加AI助手 | 3 | 3 | S026、S027、S033、S068 |
| [S070 用AI助手提交整室申请、查进度和撤销](../../../spec/specs/S070.md) | 附加AI助手 | 3 | 3 | S038、S042、S043、S068 |
| [S071 用AI助手报修、查询工单和申请取消](../../../spec/specs/S071.md) | 附加AI助手 | 3 | 3 | S050、S056、S068 |
| [S072 f记录实际风险，并完成自己的个人报告](../archive/S072.md) | 真人参与的任务 | 2 | 2 | S010 |
| [S078 实际发送邮件，失败可重试，结果不明就提示待核实](../../../spec/specs/S078.md) | 页面和业务功能 | 3 | 3 | S062 |

## 计划由本人维护的文件

以下是交给团队评审的分工目标，不是已经开放的写权限。本人模块原有测试资料、数据库升级、接口和进度目录仍按正式清单区分前后端；不能修改别的模块的升级脚本。

- `frontend/src/modules/notification/`
- `frontend/src/modules/audit/`
- `frontend/src/modules/statistics/`
- `frontend/src/modules/agent/`
- `frontend/tests/modules/support/`
- `backend/workflow-service/src/main/java/com/campus/notification/`
- `backend/workflow-service/src/main/java/com/campus/audit/`
- `backend/workflow-service/src/main/java/com/campus/statistics/`
- `backend/workflow-service/src/main/java/com/campus/agent/`
- `backend/workflow-service/src/test/java/com/campus/notification/`
- `backend/workflow-service/src/test/java/com/campus/audit/`
- `backend/workflow-service/src/test/java/com/campus/statistics/`
- `backend/workflow-service/src/test/java/com/campus/agent/`
- `contracts/openapi/modules/support/`
- `tools/buffer/`
- `tools/team/buffer_job.py`
- `tools/team/commands/buffer/`
- `docs/ethics/f/`
- `tests/integration/resilience/`

开发工具数据库与业务数据库分开。同一成员分别领取前后端任务是待做能力，不能说旧的单层账号已经支持。

## 哪些任务先做

- 验收先后第1层：S072。接口准备好后，可以提前独立开发，不必等整组全部做完。
- 验收先后第2层：S061。接口准备好后，可以提前独立开发，不必等整组全部做完。
- 验收先后第3层：S065。接口准备好后，可以提前独立开发，不必等整组全部做完。
- 验收先后第4层：S066。接口准备好后，可以提前独立开发，不必等整组全部做完。
- 验收先后第5层：S067、S068。接口准备好后，可以提前独立开发，不必等整组全部做完。
- 验收先后第6层：S062、S063。接口准备好后，可以提前独立开发，不必等整组全部做完。
- 验收先后第7层：S078。接口准备好后，可以提前独立开发，不必等整组全部做完。
- 验收先后第13层：S064、S069。接口准备好后，可以提前独立开发，不必等整组全部做完。
- 验收先后第14层：S070。接口准备好后，可以提前独立开发，不必等整组全部做完。
- 验收先后第15层：S071。接口准备好后，可以提前独立开发，不必等整组全部做完。
- 验收先后第16层：S036。接口准备好后，可以提前独立开发，不必等整组全部做完。

评审和个人报告由实际成员完成；暂时做不了的任务仍留在清单里。进度、来源和共用规则见上级说明。
