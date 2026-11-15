# M2-02 后端范围与接入

公开接口见 approvals.yaml。现有请求与任务是本轮前态：申请人、教室、起止时间、用途、人数、流程版本和截止时间在 m2.classroom_requests 保存；至少两级有序、不同、非申请人的指定处理人保存在 m2.approval_tasks。提交时的流程实例不会通过本次接口改派。M2-01 后续须通过可信身份与资源公开契约验证这些外部 UUID、资格和教室条件，再在一个事务中创建请求与任务；本轮测试直接构造本人隔离 PG 前态，不代表该提交入口已完成。

GET 需 approval:read，并且只能由申请人或分配的审批人读取。决定需当前可信会话 approval:decide、CSRF、指定处理人、匹配 expectedVersion、未到期且当前级；申请人自审与历史实际决定人的跨级重复均拒绝。权限从共同 IdentityLookup 得到；不依据角色名。隔离测试使用真实 HTTP 的身份响应夹具，尚需协调集成使用实际 IAM 服务验证撤权链路。

请求行 FOR UPDATE 与请求版本条件更新确保同版本两个决定仅一个生效，任务决定、下一级激活或后续取消、请求状态和版本同事务提交。已决定任务不可覆盖。数据库唯一约束防止同一申请重复级号、分配人和实际决定人；保留 acted_at、decided_by、comment。

最终一级通过后状态为 RESOURCE_PENDING（审批完成，资源待落实），不声明已占用。M2-03 资源确认成功后才可转 APPROVED。申请提交、全局流程配置、自动超时撤销、通知投递、前端和真实跨模块联调分别待后续，不由本轮后端证据代替。审批期已到的决定被拒绝，状态不会伪装资源落实。

迁移只新增 V2026111501__multi_level_approval.sql，使用 approval_meta 独立历史，未修改历史或其他 schema。RESOURCE_PENDING 是本模块明确的资源待落实阶段。无公共 Clock Bean，无公共依赖或锁文件变更。

自动验证使用本模块 ApprovalHttpTest，真实 PostgreSQL 18 和随机端口 Tomcat，Cookie/CSRF 经实际过滤链与 RemoteIdentityLookup HTTP 执行；对同级同版本通过/驳回以并发屏障同时请求并检查 200+409、版本只加一和单条决定。未将隔离身份夹具声称为共同联调或人工验收。
