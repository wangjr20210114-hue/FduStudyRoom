# 本轮六人后端协作契约

本轮分别处理 a：IAM-02、b：RES-07、c：M1-06、d：M2-02、e：M3-06、f：SUP-01 的完整后端验收。前端、未实现的上游业务和整体联调分别记录，不能把本轮后端通过写成全部业务完成。旧 pilot 的局部收据不作为正式需求收据。

## 数据与边界

身份四张表已实现，其余业务表尚未实现。各成员在本模块新增必要的真实持久化表：M02 使用 resource.rooms；M03 使用 m1.seat_reservations；M04 使用 m2.classroom_requests、m2.approval_tasks；M05 使用 m3.repair_orders、m3.repair_events；M06 使用 support.notifications。字段参照 docs/02-database-design.md，不能假定表已存在。调用其他模块必须走公开契约，不查询或写入其他模块的私有库。必要的外部 UUID 通过可信服务校验。

本轮每模块只新增一个未发布版本的追加式迁移，不修改历史、不删除表或字段、不清理已有数据。新增数据字段、索引和约束需留有真实 PostgreSQL 验证。测试前态可在自己的隔离数据库构造，不据此宣称预约创建、申请提交或工单创建功能已经完成。

## 身份与权限

外部 API 沿用 Cookie、CSRF 和 IdentityPrincipal.permissions()。流程服务沿用 RemoteIdentityLookup 真实网络校验身份，不能从请求体接受用户 ID、角色或资格作为授权依据，也不按 SYSTEM_ADMIN 字符串替代权限判断。

内部服务密钥沿用 AuthSettings.serviceKey() / CAMPUS_AUTH_SERVICE_KEY，通过 X-Service-Key 发送，使用 MessageDigest.isEqual 恒定时长比较，不进入日志或 Git。当前公共安全链只放行精确路径 /internal/auth/introspect。f 可在 notification 包定义更高优先级且只匹配通知内部路径的 SecurityFilterChain，单独处理服务密钥及这些路径的 CSRF；不得放开所有 /internal/**。/api/notifications/** 仍受 Cookie 和 CSRF 保护。

| 成员 | 公开权限码 |
| --- | --- |
| a | identity:roles:read、identity:roles:write |
| b | resource:rooms:read、resource:rooms:write、resource:checkin-codes:read |
| c | reservation:read |
| d | approval:read、approval:decide |
| e | repair:history:read、repair:accept、repair:evaluate |
| f | notification:read |

a 在自己的 iam 模块登记上述权限目录。普通用户只默认获得本人通知读取权限；管理操作权限按实际角色授予，维护人员不自动拥有管理员权限。其他成员不能修改 iam，可在隔离身份适配测试中构造有权限或无权限的可信响应；最终共同测试必须使用实际身份服务。角色调整仍须校验当前操作者，停用或撤权后下一次请求生效。

## 通知契约

以 [notifications.yaml](../../contracts/openapi/modules/support/notifications.yaml) 为唯一接口定义。

| 接口 | 鉴权 | 预期 |
| --- | --- | --- |
| POST /internal/notifications/events | X-Service-Key | 首次 201，相同事件重试 200，同 ID 不同内容 409 |
| POST /internal/notifications/events/{eventId}/cancel | X-Service-Key | 取消尚未发送的事件；已发送保持历史；重复调用幂等 |
| GET /api/notifications | Cookie、notification:read | 只返回本人通知，支持分页和中英文 |
| POST /api/notifications/{notificationId}/read | Cookie、notification:read、CSRF | 只可标记本人通知；重复操作幂等 |

事件包含稳定 eventId、recipientId、sourceModule、sourceId、scheduledAt，以及中文 title/body、英文 titleEn/bodyEn，可选 emailAddress。事件 ID 必须持久保存，不能每次重试生成新值。时间传带时区的 ISO 时间点，数据库使用 timestamptz。

c 在预约开始后十分钟仍未签到时才发送：09:09:59 不发送，09:10:00 可以发送；已签到或取消不发送。网络失败保留持久任务，同一事件重试不重复建立通知。c 不访问 support 表，也不引用 f 的 Java 内部类。

M1-06 可以只发送站内提醒，省略 emailAddress。f 的邮件适配必须实际连接本轮独立的 Mailpit SMTP 收件箱，验证收件人、内容和失败状态。未配置 SMTP 不得宣称发送成功；服务器接收结果不确定时保留 UNKNOWN 状态，不盲目重发造成重复邮件。公网真实邮件提供商仍是外部部署条件。

## 共同测试与交付

每人独立 runner、数据库、缓存、工作树和访问码。测试和产品只在本人白名单目录内写入，不把共享宿主路径检查当作强制隔离。仅协调测试环境可运行多个服务和独立 Mailpit，通过真实 HTTP 验证 c 与 f 的通知链路。stub 只证明隔离测试，不能替代共同联调。

六份补丁完成后先核验各自收据和边界，再在独立目录检查能否干净叠加。若有 Git 冲突立即停止交给人。使用真实 PostgreSQL 预演六分支先后迁移及旧应用启动，确认无破坏性变化后集中调用 submit；不把仓库所有者凭据交给成员容器。

GitHub 推送、CI、华为体验站更新和缓冲站审核分别记录真实结果。统一体验站展示最近成功部署的分支快照，不自动包含另外五人的功能；最终还需独立集成分支。现有部署器只备份数据库并尝试应用回退，不能保证任意 schema 变更均可回退。本轮不依赖回退保护执行破坏性迁移，也不关闭自动更新开关。

Gitee 仍需真人审核与独立 CI，通过后按 72 小时周期发布功能分支或创建 MR。AI 不代替真人签署审核，不以推送 GitHub 成功代替 Gitee 成功。
