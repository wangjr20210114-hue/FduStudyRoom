# 六模块需求基线

> 当前[业务Spec与关系图](17-spec-topology-and-assignment.md)只分解产品功能；开发分工、环境和过程要求见[workflow清单](18-workflow-obligations.md)。本页保留原需求目录，不表示已实现或已切换成员权限。

来源：`sources/task-book.pdf`，共6页。`explicit` 为任务书原文要求，`derived` 为实现这些要求必须落实并可调整的设计决策，`user` 为用户补充，`optional` 为附加项。原任务书未规定具体多级审批人数、签到窗口、取消规则、超时升级时长等，具体配置须评审，不能把推导伪称原文。

| 模块 | 编号 | 需求 | 来源/页 | 基线状态 |
| --- | --- | --- | --- | --- |
| M01 | IAM-01 | 本系统账号注册、登录和退出 | user / 1,5 | accepted |
| M01 | IAM-02 | 可配置角色和权限 | explicit / 5 | pending |
| M01 | IAM-03 | 账号管理和资格维护 | derived / 5 | pending |
| M01 | IAM-04 | 系统参数管理 | explicit / 5,6 | pending |
| M01 | IAM-05 | 身份与权限公开校验契约 | derived / 2,4 | pending |
| M01 | IAM-06 | 三角色导航与会话恢复 | user / 5 | accepted |
| M02 | RES-01 | 教室登记、注销与可用性 | explicit / 5 | pending |
| M02 | RES-02 | 座位主数据和特殊标记 | explicit / 5,6 | pending |
| M02 | RES-03 | 开放时间和院系最小限制 | derived / 5 | pending |
| M02 | RES-04 | 统一时段占用和并发互斥 | derived / 2,6 | pending |
| M02 | RES-05 | 维修限制与按来源恢复 | derived / 2 | pending |
| M02 | RES-06 | 占用来源追溯公开接口 | explicit / 2,4 | pending |
| M02 | RES-07 | 教室每日签到编码 | explicit / 6 | pending |
| M02 | RES-08 | 复旦空教室只读接入 | user / 用户 | pending |
| M03 | M1-01 | 按条件搜索可用座位 | explicit / 1,6 | pending |
| M03 | M1-02 | 整点小时预约及最长时长 | explicit / 6 | pending |
| M03 | M1-03 | 预约取消与释放 | derived / 6 | pending |
| M03 | M1-04 | Web编码签到 | explicit / 6 | pending |
| M03 | M1-05 | 预约前15分钟提醒 | explicit / 6 | pending |
| M03 | M1-06 | 开始后10分钟未签到再次提醒 | explicit / 6 | pending |
| M03 | M1-07 | 开始后15分钟违约自动取消 | explicit / 6 | pending |
| M03 | M1-08 | 历史查询与再次预约 | explicit / 1,6 | pending |
| M04 | M2-01 | 整教室申请资格和提交 | explicit / 1,2 | pending |
| M04 | M2-02 | 多级审批通过与驳回 | derived / 1 | pending |
| M04 | M2-03 | 审批通过后网络占用整室 | explicit / 2 | pending |
| M04 | M2-04 | 驳回、超时撤销和释放 | explicit / 1,2 | pending |
| M04 | M2-05 | 申请进度与审批历史 | derived / 1 | pending |
| M04 | M2-06 | 写回失败可重试与对账 | derived / 2,3 | pending |
| M05 | M3-01 | 故障上报和工单查询 | explicit / 1 | pending |
| M05 | M3-02 | 派单和维修人员任务 | derived / 1 | pending |
| M05 | M3-03 | 维修中网络下架教室 | derived / 2 | pending |
| M05 | M3-04 | 超时升级 | explicit / 1 | pending |
| M05 | M3-05 | 完工申请与独立验收 | derived / 1,2 | pending |
| M05 | M3-06 | 验收评价和过程历史 | derived / 1 | pending |
| M05 | M3-07 | 工单取消与幂等恢复 | derived / 4 | pending |
| M06 | SUP-01 | 站内通知和真实邮件适配 | explicit / 1,6 | pending |
| M06 | SUP-02 | 关键操作持久审计 | derived / 1,2 | pending |
| M06 | SUP-03 | 运营统计 | explicit / 6 | pending |
| M06 | ENG-01 | 机器可读规则、需求与验收追踪 | explicit / 1,2,3 | pending |
| M06 | ENG-02 | 严格TDD与有效测试 | user / 3 | pending |
| M06 | ENG-03 | 人工验收与合并请求 | explicit / 2,3 | pending |
| M06 | ENG-04 | 提交与合并请求触发流水线 | explicit / 2,3 | pending |
| M06 | ENG-05 | 一键环境和干净环境部署 | user / 3,5 | pending |
| M06 | ENG-06 | 测试环境整组自动部署 | explicit / 3,5 | pending |
| M06 | ENG-07 | 生产审批、版本与回滚 | explicit / 2,3,5 | pending |
| M06 | ENG-08 | 模块化迁移和幂等种子 | explicit / 3 | pending |
| M06 | ENG-09 | 真实跨模块集成验收 | explicit / 2,4,5 | pending |
| M06 | ENG-10 | 环境文件与密钥隔离 | user / 1,3 | pending |
| M06 | ENG-11 | 中英国际化与基线栈锁定 | user / 用户 | pending |
| M06 | ENG-12 | 缓冲网站及三天定时同步 | user / 用户 | pending |
| M06 | ETH-01 | 工程伦理与个人报告证据 | explicit / 3 | pending |
| M06 | AGT-01 | 可选自然语言业务代理 | optional / 4,5 | pending |
| M06 | ENG-13 | 评审节点与最终现场演示 | explicit / 4,5 | pending |
| M06 | ENG-14 | 组内共同环境与公共层决策 | explicit / 1 | pending |

共54条需求。表中accepted沿用原基线已验收记录，本次工程基础改动尚待用户审查，不自报已验收。机器验收标准和每层状态以 `../spec/requirements.json` 为准。页面骨架不算业务完成；单元mock通过不算真实集成完成。

## 外部依赖清单

| 依赖 | 当前限制 | 可先独立完成 |
| --- | --- | --- |
| 校方UIS/学生身份 | 用户已取消接入；本地账号不能证明复旦身份 | 账号系统、管理员核实资格 |
| 复旦空教室数据/场地 | 无合法授权与稳定接口不能承诺接入或实际开放 | 本系统教室管理、可替换只读适配契约 |
| SMTP/微信推送 | 未配置可用服务；微信不是本项目必选实现 | 本机测试邮箱、站内消息、投递任务 |
| Gitee权限/凭据 | 用户名不是授权；后台需要实际写权限和MR令牌 | 本机接收、审查、隔离验证、本地Git远程演练 |
| 队友在线访问 | 127.0.0.1仅本机；异机需可达主机、HTTPS和账户配置 | 本机缓冲站与部署配置 |
| CI/测试/生产服务器 | 缺runner及可用服务器时不能声称全流程已部署 | 本地容器流水线与真实网络集成 |
| Docker/虚拟化/IDE | IDE缺失会提示；初次Docker许可、虚拟化、重启可能需人操作 | 环境诊断、工具用户级下载及可恢复stage |
| 生产审批/人工评审/论文 | 必须真人参与，skill不能代签或编造 | 证据采集、审查界面和报告模板 |
| 可选业务Agent | 无模型服务/DSH不能证明真实会话调用 | 公开API、意图确认与适配边界 |

工程基线条目由coordinator负责，人类报告/评审由human负责，不作为普通成员可写模块任务分派。integration_dependencies表示真实集成依赖，独立开发可使用公开契约stub，但不能把stub标记为集成完成。
