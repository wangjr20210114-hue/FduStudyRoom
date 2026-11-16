# 整室申请公开契约

仅 M04 写 m2，外部用户、教室和工单编号为逻辑引用。系统栈与公共 Cookie/CSRF 契约不变，内部维修接口只认 X-Service-Key。

| 方法与路径 | 输入 | 输出与权限 |
| --- | --- | --- |
| POST /api/approvals/requests | operationId, roomId, startsAt, endsAt, purpose, attendeeCount | 201新建/200同操作；approval:apply及真实资格 |
| GET /api/approvals/requests | page从0，size为1至100 | items/page/size/total，仅本人 |
| GET /api/approvals/tasks | 同上 | 仅指定给本人的任务申请；approval:decide |
| GET /api/approvals/requests/{id} | — | 原字段加申请人、教室、时段、人数、用途、resourceState、resourceAbnormal、cancelReason、history、deliveries；approval:read且为本人/指定审批人 |
| POST /api/approvals/requests/{id}/cancel | operationId, expectedVersion, reason | 开始前本人；开始后approval:admin-cancel及理由，结束后拒绝 |
| GET/PUT /api/approvals/configuration | PUT expectedVersion(初始0), approverIds | version, approverIds；approval:flow:read/write |
| GET /api/approvals/candidates | — | 当前有approval:decide的有效候选；flow:read |
| POST /internal/approvals/maintenance-events | operationId, sourceId, sourceVersion, roomId, active, effectiveAt | 200 operationId/duplicate/ignored/appliedSourceVersion；400/401/409/503按集成契约 |

既有逐级决定路径及 ApprovalView 字段保留。JSON拒绝未知字段。过期返回EXPIRED；开始时未落实返回CANCELLED且cancelReason=START_REACHED，expiresAt不改。已落实不因审批TTL取消。冲突维持RESOURCE_PENDING并在deliveries显示真实失败码/重试时间。撤销后resourceState=RELEASE_PENDING直到资源ACK，之后RELEASED。资源真正确认后才APPROVED/CONFIRMED。

资源、通知、审计HTTP全部位于数据库事务外。outbox与业务同事务，领取任务短事务，失败保留原编号/载荷和30秒租期重试。不可重试幂等冲突显示ERROR，不能循环造新编号。通知固定双语，服务端查询验证邮箱后冻结内容，缺邮箱仍保留站内通知。

维修来源按工单高水位与房间锁聚合；恢复只清自己来源。已落实整室单只标记资源异常，不释放占用。反查资源真实状态，旧active不覆盖恢复墓碑，回执原始effectiveAt保留不重新舍入。

测试：隔离PG和HTTP故障场景单列；realTwoProcessesRecoverOutboxAndReleaseThroughHttp实际启动资源JAR与当前workflow独立JVM，经公开IAM和资源API准备数据，断网、kill/restart同库workflow并查询真实占用和释放。前端和人工验收另行完成。

写回安全复核：每次发占用、写APPROVED前重新读取当前enabled、canApplyRoom、approval:apply与教室部门。已知资格失效则等待；已经获资源ACK却在最后复核失效时，事务内保存更高版本释放，先显示RELEASE_PENDING。释放真实ACK后才创建越过墓碑的新版本待占用命令，资格恢复可继续原申请，原startsAt/expiresAt不延长。已经APPROVED的申请不会因此自动取消。
