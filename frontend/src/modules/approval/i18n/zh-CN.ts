export default {
 title:'整室申请',manageTitle:'整室审批',new:'新建申请',submit:'提交申请',retrySubmit:'重试原申请',retryCancel:'重试原撤销',refresh:'刷新',retry:'重试',loading:'加载中',empty:'暂无申请',previous:'上一页',next:'下一页',page:'第 {page} 页，共 {total} 项',view:'查看',close:'关闭',shanghai:'上海时间',
 room:'教室',chooseRoom:'选择教室',noRooms:'没有可申请的教室',noRoomAccess:'没有教室查询权限，暂时不能选择教室',startsAt:'开始时间',endsAt:'结束时间',purpose:'用途',attendeeCount:'人数',capacity:'容量 {count}',openHours:'开放 {from}–{to}',submitNote:'提交后进入审批，尚未占用教室',validation:'请检查申请信息',unknown:'结果尚未确认，请重试原操作',
 detail:'申请详情',requestId:'申请编号',applicant:'申请人',flowVersion:'流程版本',version:'版本 {version}',expiresAt:'审批截止',resourceVersion:'资源版本',source:'资源来源标识',fulfilled:'已落实',confirming:'资源确认中',releasePending:'资源释放中',released:'资源已释放',abnormal:'教室受维修影响，请查看最新进度',startReached:'已到开始时间，申请未落实',cancelReason:'撤销理由',
 taskList:'审批进度',step:'第 {step} 级',approver:'审批人',dueAt:'办理期限',actedAt:'办理时间',approve:'通过',reject:'驳回',decisionComment:'审批意见',rejectReason:'请填写驳回理由',cancel:'撤销申请',cancelRequired:'请填写撤销理由',history:'审批历史',system:'系统',deliveries:'通知与操作记录交付',noHistory:'暂无办理记录',noDeliveries:'暂无交付记录',attempts:'尝试 {count} 次',nextRetry:'下次重试',noticeAccepted:'已交付通知服务',auditAccepted:'已交付操作记录服务',deliveryPending:'等待交付',deliveryProcessing:'正在交付',deliveryError:'交付失败，待处理',
 flow:'审批流程',flowSnapshot:'更改仅用于新申请',flowVersionLabel:'配置版本 {version}',addApprover:'加入审批流程',removeApprover:'移除',moveUp:'上移',moveDown:'下移',saveFlow:'保存配置',reloadFlow:'重新加载配置',flowValidation:'至少选择两位不同审批人',noCandidates:'没有足够的有效审批人',flowSaved:'配置已保存',notCandidate:'当前已无审批资格',
 status:{PENDING:'待审批',RESOURCE_PENDING:'等待落实',APPROVED:'审批通过',REJECTED:'已驳回',CANCELLED:'已撤销',EXPIRED:'已过期',CONFLICT:'等待落实',WAITING:'等待前一级',APPROVE:'已通过'},
 taskStatus:{PENDING:'待办理',WAITING:'等待前一级',APPROVED:'已通过',REJECTED:'已驳回',EXPIRED:'已过期',CANCELLED:'已撤销'},
 resource:{UNCONFIRMED:'尚未确认资源',PENDING:'资源确认中',CONFIRMED:'资源已确认',RELEASE_PENDING:'资源释放中',RELEASED:'资源已释放'},
 kind:{NOTICE:'状态通知',AUDIT:'操作记录',ALLOCATE:'整室占用',RELEASE:'占用释放',RESOURCE:'资源同步'},
 errors:{signedOut:'登录已过期',stale:'资料已变化，请重新确认',flowMissing:'审批流程尚未配置或审批人已失效',ineligible:'没有整室申请资格或不符合教室资格',selfApproval:'申请人不能在本申请的审批流程中',forbidden:'无权执行此操作',detailForbidden:'无权查看此申请',notFound:'申请不存在',invalid:'请检查申请信息',invalidResponse:'服务返回的数据不完整，请重试',unavailable:'服务暂时不可用，请重试'},
}
