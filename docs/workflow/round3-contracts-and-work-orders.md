# 六人完整功能开发工作单

本轮接续已上线的 c369a01，使用 a—f 六个本机缓冲站身份。先完成剩余后端，再完成各域前端；保持独立工作树、容器、需求分析、TDD、变异、回归和收件。已验证项核对证据后跳过。新交付的人工验收独立进行，不沿用上轮仅六项后端的验收结论。

## 分工

| 身份 | 领域 | 剩余后端 | 剩余前端 |
| --- | --- | --- | --- |
| a | 账号和设置 | IAM-03/04/05/07 | IAM-02/03/04/07 |
| b | 教室、座位、占用 | RES-01/02/03/04/05/06；RES-08真实数据源受阻 | RES-01/02/03/04/05/07；RES-08显示未接入 |
| c | 个人预约和签到 | M1-01/02/03/04/05/07/08 | M1-01/02/03/04/05/07/08 |
| d | 整室申请和审批 | M2-01/03/04/05/06 | M2-01至05 |
| e | 报修和维修 | M3-01/02/03/04/05/07 | M3-01至07 |
| f | 通知、审计、统计 | SUP-02/03/04；AGT-01外部模型受阻 | SUP-01/02/03；AGT-01如实显示配置缺口 |

IAM-01/06前后端已验收；IAM-02、RES-07、M1-06、M2-02、M3-06、SUP-01后端已在上一轮验证。邮箱验证原有S081由M01负责账户状态，登记IAM-07；邮件瞬时投递由M06负责，登记SUP-04/S082。旧SUP-01的验收历史不改写成覆盖邮箱验证。

## 已确认的规则

用户于2026-11-15明确确认P02–P09、P12，审批默认60分钟、维修默认120分钟，可由管理员调整。其他默认值为最长预约4小时、提前15分钟提醒、开始后10分钟提醒、15分钟未签到取消。设置整组原子更新且校验；业务创建或派单时保留截止时间快照，改设置不追溯改旧单。

统计按P08。停用/维修从实际生效时刻截断可服务窗口，恢复起重新开放，不追溯整段失效。释放生效时间是资源模块成功持久应用的服务器时刻。容量与实际保留占用按[from,to)、asOf和历史可服务窗口取交集；先取消后开始的占用为0。未知历史明确显示不可计算，不能拿当前active状态伪造历史。

## 公开接口

所有内部接口使用X-Service-Key，普通页面不持有该密钥。公共IdentityPrincipal保持原结构，各域不读他域私有表。

### 账号、资格和参数

- GET /internal/identity/users/{id}/eligibility：userId、enabled、role、permissions、departmentCode、canApplyRoom、authVersion；停用用户或角色返回enabled=false且没有有效权限。
- GET /internal/identity/users/{id}/notification-email：userId、enabled、email、verified，仅可信服务可查。资格字段只由管理员维护。
- GET /internal/system/settings：version、maxReservationHours、beforeStartReminderMinutes、afterStartReminderMinutes、checkinDeadlineMinutes、approvalTtlMinutes、repairSlaMinutes。
- 管理员GET/PUT /api/admin/settings（PUT带expectedVersion），GET /api/admin/settings/history；所有数值正整数且开始后提醒早于签到截止，整组更新版本+1并留痕。
- 系统设置与历史归iam，使用本模块新增迁移，不写resource表。

### 资源

- GET/POST /api/resources/rooms，GET/PUT /api/resources/rooms/{id}；GET/POST /api/resources/rooms/{id}/seats，PUT /api/resources/seats/{id}；写入带expectedVersion。
- POST /internal/resources/allocations：operationId、sourceModule(M1/M2)、sourceId、sourceVersion、roomId、seatId(M1必需)、userId(M1必需)、startsAt、endsAt；M2全室原子占用。
- POST /internal/resources/releases：operationId、sourceModule、sourceId、sourceVersion。
- PUT /internal/resources/restrictions/{sourceId}：operationId、sourceVersion、roomId、active，归M3维修限制。
- GET /internal/resources/availability、/capacity、/sources/{sourceModule}/{sourceId}；搜索参数startsAt、endsAt、userId、campus、building、hasSocket。
- 首次写入201、幂等重放200，400非法格式、403资格不符、404主数据不存在、409冲突/旧版本/同键异参数、503必要依赖不可用。
- 本域追加resource_operations来源版本/操作指纹和历史，保留整室教室级来源范围。新建/启用座位不能绕过整室占用；先释放的墓碑不能被旧消息复活。
- 公共V3安装PostgreSQL可信btree_gist扩展，本域V2使用它建立座位与用户时间范围排斥约束；不修改已发布V1。

### 通知、审计和统计

- 现有POST /internal/notifications/events沿用已发布契约。
- POST /internal/audit/events：eventKey、sourceService、actorId(系统任务可空)、action、sourceModule、sourceId、success、occurredAt、traceId、受限beforeData/afterData。首次201、相同键同内容200、异内容409；成功变更与待发审计同事务，越权失败也留安全记录。
- GET /api/audit：audit:read鉴权、分页、traceId/sourceModule/sourceId筛选；不新增组织树或院系范围。
- GET /internal/resources/statistics?from&to&asOf&roomId：capacitySeatSeconds、occupiedSeatSeconds、occupiedM1SeatSeconds、occupiedM2SeatSeconds、confirmedBookingCount、confirmedM1BookingCount、confirmedM2BookingCount、algorithm、historyAvailableFrom、historyComplete。占用数按来源ID去重且与有效窗口正交集；historyComplete=false不伪装为0。
- GET /internal/reservations/statistics使用同一时间/教室范围：checkedInCount、noShowCount、排除数及来源说明；只纳入已到签到截止的队列，排除开始前取消和系统维修终结。
- GET /api/statistics由f通过HTTP合并，statistics:read鉴权；来源失败、历史不足、0容量分别表达，不能补造0或NaN。

### 邮箱验证

- POST /internal/notifications/verification-email：operationId(UUID)、userId(UUID)、email、token、expiresAt，X-Service-Key；不接收URL、subject、body或locale。
- f用受信origin生成/account#verify-email=...，固定双语模板。a本人页面读取fragment后立即清URL，再仅通过POST body验证。
- 回执operationId、status、duplicate、retryable、nextRetryAt、errorCode。status为ACCEPTED/FAILED/NOT_CONFIGURED/UNKNOWN；ACCEPTED只表示SMTP接收，不称最终送达。
- 200表示确定状态或重复，202表示UNKNOWN，400仅非法DTO，401密钥无效，409同键异参数。配置缺失返回NOT_CONFIGURED；过期未发为FAILED不可重试。
- f发送前持久提交UNKNOWN；相同ID的ACCEPTED/UNKNOWN仅回读不重发。只有确定失败或未配置、尚未过期且到nextRetryAt，才允许同ID同参数重试。a超时保留原ID。
- a仅持久nonce/hash等并从受保护密钥重建令牌；f仅存规范化请求指纹和安全回执，不存邮箱、token、URL或整封正文。禁止令牌进入日志或普通通知表。

## 权限

新增动作由a统一登记；其他领域只使用权限，不写IAM。沿用USER、MAINTAINER、SYSTEM_ADMIN，支持自定义角色。

| 默认范围 | 权限 |
| --- | --- |
| 三角色，另检查本人/被派/申请资格 | resource:rooms:read；reservation:read/create/cancel/checkin；approval:read/apply；repair:report/history:read；notification:read |
| MAINTAINER和SYSTEM_ADMIN，另检查实际被派人员 | repair:maintain |
| SYSTEM_ADMIN | reservation:admin-cancel；reservation:admin-read；approval:decide；approval:flow:read；approval:flow:write；approval:admin-cancel；repair:assign/accept/evaluate/admin-cancel；audit:read；statistics:read；identity:settings:read/write |

原权限不删除；角色名不能代替对象归属与权限检查。IAM的已有管理权限和b签到码权限继续沿用。

## 浏览器与交付

真实浏览器测试复用frontend/tests/shared/browser.ts，业务测试加载真实产品组件或路由；API fixture只隔离数据来源。Vitest原生JSON通过现有record --kind e2e保存，不新增依赖/锁文件。纯SSR或假页面不能报UI通过。各域全部本层TDD/变异/回归通过再收件，前后端真实联调在协调QA环境单独验。

本轮submit --no-github先自动收件，协调人保留同一差异并统一安排导出推送，避免半成品快照轮流覆盖体验站。所有GitHub推送仍按既有CI和站点规则执行；Gitee新交付仍须真人验收和独立CI，不沿用上一轮的验收。Git冲突由人处理，不自动合并main。

## 当前基线证据

公共工具117项（116通过、1项Windows专用在Linux跳过）；后端81/81；浏览器基础4/4、独立破坏观察值检出1个业务断言失败、类型检查通过；btree_gist在非超级用户数据库所有者下真实安装，重叠范围插入产生23P01。详情保存在本轮private目录。正式业务实现仍须各自完整需求收据，不能复用这些基线检查冒充功能完成。
