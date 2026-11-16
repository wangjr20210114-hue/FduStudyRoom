# 预约、审批和维修的集成补充

用户已确认整室撤销、开始时未落实的撤销、冲突等待重试，以及维修转派和独立验收规则；原始确认保存在本轮私有记录，不冒充六名真人已逐项验收。

## 个人预约

未曾成功确认的PENDING无法供用户签到，到截止作系统超时取消，不记用户违约。已确认且未签到才记NO_SHOW。占用确认迟到时仍补偿释放，不能复活取消记录。全员预约明细仅reservation:admin-read，普通reservation:read只允许本人；聚合statistics:read不隐含个人明细权限。

## 整室申请

本人可在开始前撤销所有未终结申请，包含已落实单；确认中取消显示处理中并补做释放。开始后只有approval:admin-cancel且填写理由的管理员可取消，已结束不再取消。保留原expiresAt快照；到startsAt仍未落实单以START_REACHED另行自动撤销，即使审批TTL尚未结束。已APPROVED不因审批期限到自动撤销。资源冲突在开始/审批截止前继续原操作编号、同参数重试并显示等待落实；同键异参数不盲重试。

流程配置归M04：approval:flow:read/write和approval:admin-cancel仅SYSTEM_ADMIN默认。至少两位不同且非申请人的有效审批人，查询a的approval:decide候选并在提交时复核，任务和期限快照不随配置修改。没有足够候选或流程配置时明确提示，不生成假审批人。

## 维修

首次派单保存dueAt，转派不延长。独立验收排除所有实际维修过此单的人。维修中取消由允许的人发起，管理员审查后独立安全验收；同一位没参与维修且具有两项权限的管理员可完成两步。当前状态、幂等回执、资源desired/applied版本、升级、历史与任务都只写m3。

通知收件人通过a的有效权限候选查询取得：repair:maintain用于派工、repair:accept用于完工验收、repair:assign用于超时升级。只把enabled且verified的邮箱带入普通通知，缺地址不影响站内通知。原始故障全文、邮箱、token、cookie不进入审计摘要。

## 维修广播接口

资源来源仍由b统一管理。e在b真实确认限制或恢复后，保存向c和d发送的任务，事务外HTTP，失败保留重试。

- POST /internal/reservations/maintenance-events
- POST /internal/approvals/maintenance-events

两者使用X-Service-Key，载荷相同：operationId(UUID)、sourceId(M3工单UUID)、sourceVersion(正整数)、roomId(UUID)、active(boolean)、effectiveAt(b真实回执时间)。接收域先经b /internal/resources/sources/M3/{sourceId}核实当前房间、版本、状态与生效时间；必要依赖不可达返回503，不猜成功。

接收域保存每工单高水位与请求指纹。相同ID同内容幂等、异内容409；旧active不得覆盖恢复墓碑。若b已更高版本，按实际较新来源收敛，不能用旧维修消息取消恢复后新预约。

c仅终结本室未结束个人预约为SYSTEM_MAINTENANCE，保存释放/通知/审计，不记违约；active=false只记恢复墓碑，不复活旧预约。

d不释放M2占用，而给未结束已落实申请标resourceAbnormal；同房间任意工单active就保持异常，恢复只解除本工单来源。没有已落实申请也先保留来源，避免后续确认漏标。

响应200：operationId、duplicate、ignored、appliedSourceVersion。400非法输入、401密钥错误、409同键异内容/同版本异内容/变更房间、503无法取得资源真实确认。

## 统计与配置

c的统计返回同一from/to/asOf/roomId、checkedInCount、noShowCount、excludedBeforeStartCancelledCount、excludedSystemTerminatedCount、notDueCount、algorithm/source。f仅合并同范围真实源，已确认P08口径不变。

正式拓扑服务名为resource:8080、workflow:8081；资源origin复用既有campus.workflow.identity-url，资源到通知复用campus.reservation.notification-url（local已有127.0.0.1）。工作流域间自调用用当前服务8081。不要持有DB事务等待同服务HTTP，不新增部署环境变量来掩盖默认地址错误。
