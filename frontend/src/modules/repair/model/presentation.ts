import type { CurrentUser } from '@/modules/auth/types'
import { ApiError } from '@/shared/api'
import type { Command, RepairEvent, RepairOrder, Room, Seat } from '../api/repairs'
export const has=(u:CurrentUser|null,p:string)=>!!u?.permissions.includes(p)
export const terminal=(r:RepairOrder)=>['COMPLETED','CANCELLED'].includes(r.status)
export const independent=(r:RepairOrder,u:CurrentUser|null,history:RepairEvent[],loaded:boolean)=>!!u&&loaded&&r.technicianId!==u.id&&!history.some(e=>e.actorId===u.id&&['START','FINISH'].includes(e.eventType))
export const canAssign=(r:RepairOrder,u:CurrentUser|null)=>r.managed&&has(u,'repair:assign')&&['SUBMITTED','ASSIGNED','IN_PROGRESS'].includes(r.status)
export const canStart=(r:RepairOrder,u:CurrentUser|null)=>r.managed&&has(u,'repair:maintain')&&r.technicianId===u?.id&&r.status==='ASSIGNED'
export const canFinish=(r:RepairOrder,u:CurrentUser|null)=>r.managed&&has(u,'repair:maintain')&&r.technicianId===u?.id&&r.status==='IN_PROGRESS'
export const canInspect=(r:RepairOrder,u:CurrentUser|null,h:RepairEvent[],ready:boolean)=>r.managed&&has(u,'repair:accept')&&r.status==='PENDING_ACCEPTANCE'&&independent(r,u,h,ready)
export const canReview=(r:RepairOrder,u:CurrentUser|null)=>r.managed&&has(u,'repair:admin-cancel')&&r.status==='CANCEL_REVIEW_PENDING'
export const canSafety=(r:RepairOrder,u:CurrentUser|null,h:RepairEvent[],ready:boolean)=>r.managed&&has(u,'repair:accept')&&r.status==='CANCEL_SAFETY_PENDING'&&independent(r,u,h,ready)
export const canCancel=(r:RepairOrder,u:CurrentUser|null)=>r.managed&&has(u,'repair:history:read')&&(r.reporterId===u?.id||has(u,'repair:admin-cancel'))&&['SUBMITTED','ASSIGNED','RESTRICTION_PENDING','IN_PROGRESS','PENDING_ACCEPTANCE'].includes(r.status)
export const canEvaluate=(r:RepairOrder,u:CurrentUser|null,h:RepairEvent[],ready:boolean)=>has(u,'repair:evaluate')&&r.status==='COMPLETED'&&!r.evaluatedAt&&r.acceptedBy===u?.id&&independent(r,u,h,ready)
export const restrictionConfirmed=(r:RepairOrder)=>r.managed&&r.desiredActive===true&&r.appliedActive===true&&Number(r.desiredVersion)>0&&r.desiredVersion===r.appliedVersion
export const sourceReleased=(r:RepairOrder)=>r.managed&&terminal(r)&&r.desiredActive===false&&r.appliedActive===false&&Number(r.desiredVersion)>0&&r.desiredVersion===r.appliedVersion
export const complete=(r:RepairOrder)=>r.status==='COMPLETED'&&sourceReleased(r)
export const overdue=(r:RepairOrder,now:number)=>!!r.dueAt&&Date.parse(r.dueAt)<=now&&!r.acceptedAt&&!terminal(r)&&r.status!=='RESTORATION_PENDING'
export function formatTime(value:string|null|undefined){
 if(!value)return '—'
 const p=new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(new Date(value))
 const part=(name:string)=>p.find(x=>x.type===name)?.value
 return part('year')+'/'+part('month')+'/'+part('day')+' '+part('hour')+':'+part('minute')
}
export function validReport(description:string,roomId:string,seatId:string,rooms:Room[],seats:Seat[]){
 return !!description.trim()&&description.length<=4000&&rooms.some(r=>r.id===roomId)&&(!seatId||seats.some(s=>s.id===seatId&&s.roomId===roomId))
}
export const validRating=(value:string)=>/^[1-5]$/.test(value)
export const validEvaluation=(text:string)=>text.length<=500&&(text===''||!!text.trim())
export function goalConfirmed(c:Command,r:RepairOrder){
 if(r.id!==c.orderId||r.version<=Number(c.body.version))return false
 if(c.action!=='acceptance')return true
 return c.body.approved===true?r.acceptedBy===c.actorId&&['RESTORATION_PENDING','COMPLETED'].includes(r.status)&&r.desiredActive===false:r.status==='IN_PROGRESS'
}
export function errorKey(e:unknown){
 if(e instanceof ApiError){
  if(e.status===401)return 'signedOut'
  if(e.code==='REPAIR_INVALID_TECHNICIAN')return 'technicianUnavailable'
  if(e.code==='REPAIR_IDEMPOTENCY_CONFLICT')return 'idempotency'
  if(e.code==='ACCEPTANCE_INTEGRATION_PENDING')return 'legacy'
  if(e.status===409)return 'stale'
  if(e.status===403)return 'forbidden'
  if(e.status===404)return 'notFound'
  if(e.status===400||e.status===422)return 'invalid'
  if(e.kind==='invalid_response')return 'invalidResponse'
 }
 return 'unavailable'
}
export const uncertain=(e:unknown)=>!(e instanceof ApiError)||['network','timeout','invalid_response'].includes(e.kind)||(e.status??0)>=500
