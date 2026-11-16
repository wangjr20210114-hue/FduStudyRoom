import { ApiError } from '@/shared/api'
import type { CurrentUser } from '@/modules/auth/types'
import type { ApprovalTask, Room, RoomRequest, Submission } from '../api/approvals'
export const has=(u:CurrentUser|null,p:string)=>!!u?.permissions.includes(p)
export const terminal=(r:RoomRequest)=>['REJECTED','CANCELLED','EXPIRED'].includes(r.status)
export const fulfilled=(r:RoomRequest)=>r.status==='APPROVED'&&r.resourceState==='CONFIRMED'&&!r.resourceAbnormal
export function decidingTask(r:RoomRequest,u:CurrentUser|null,now=Date.now()):ApprovalTask|undefined{
 if(!u||!has(u,'approval:decide')||r.status!=='PENDING'||r.applicantId===u.id||Date.parse(r.expiresAt)<=now)return
 if(r.tasks.some(t=>t.decidedBy===u.id&&t.status==='APPROVED'))return
 const t=r.tasks.find(t=>t.status==='PENDING')
 return t&&t.dueAt!==null&&Date.parse(t.dueAt)>now&&t.approverId===u.id&&r.tasks.filter(x=>x.stepNo<t.stepNo).every(x=>x.status==='APPROVED')?t:undefined
}
export function canCancel(r:RoomRequest,u:CurrentUser|null,now=Date.now()){
 if(!u||terminal(r)||now>=Date.parse(r.endsAt))return false
 return (r.applicantId===u.id&&now<Date.parse(r.startsAt))||has(u,'approval:admin-cancel')
}
export const cancellationNeedsReason=(r:RoomRequest,u:CurrentUser|null)=>r.applicantId!==u?.id||Date.now()>=Date.parse(r.startsAt)
export function formatTime(v:string|null|undefined){
 if(!v)return '—'
 const p=new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(new Date(v))
 const part=(k:string)=>p.find(x=>x.type===k)?.value
 return part('year')+'/'+part('month')+'/'+part('day')+' '+part('hour')+':'+part('minute')
}
export function validSubmission(c:Submission,room:Room|undefined){
 if(!room||!room.enabled||!room.bookingEnabled||!c.purpose.trim()||c.purpose.trim().length>2000||!Number.isInteger(c.attendeeCount)||c.attendeeCount<1||c.attendeeCount>room.capacity)return false
 const from=new Date(c.startsAt),to=new Date(c.endsAt)
 if(!Number.isFinite(+from)||!Number.isFinite(+to)||+from<=Date.now()||+to<=+from)return false
 const start=c.startsAt.slice(11,19),begin=room.openTime.slice(0,8),end=room.closeTime.slice(0,8)
 if(begin===end)return true
 const day=new Date(c.startsAt.slice(0,10)+'T00:00:00+08:00')
 if(begin>end&&start<end)day.setUTCDate(day.getUTCDate()-1)
 const base=+day
 const seconds=(v:string)=>v.split(':').reduce((a,n,i)=>a+Number(n)*[3600,60,1][i]!,0)*1000
 return +from>=base+seconds(begin)&&+to<=base+seconds(end)+(begin>end?86400000:0)
}
export function errorKey(error:unknown){
 if(error instanceof ApiError){
  if(error.status===401)return 'signedOut'
  if(error.status===409)return error.code==='APPROVAL_FLOW_NOT_CONFIGURED'?'flowMissing':'stale'
  if(error.code&&['APPROVAL_NOT_ELIGIBLE','APPROVAL_DEPARTMENT_DENIED','ROOM_APPLICATION_NOT_ALLOWED'].includes(error.code))return 'ineligible'
  if(error.code==='SELF_APPROVAL_FORBIDDEN')return 'selfApproval'
  if(error.status===403)return 'forbidden'
  if(error.status===404)return 'notFound'
  if(error.status===400||error.status===422)return 'invalid'
  if(error.kind==='invalid_response')return 'invalidResponse'
 }
 return 'unavailable'
}
export const uncertain=(error:unknown)=>!(error instanceof ApiError)||['network','timeout','invalid_response'].includes(error.kind)||(error.status??0)>=500
