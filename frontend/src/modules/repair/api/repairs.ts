import { api, ApiError } from '@/shared/api'
import { getAppLocale } from '@/shared/i18n'
export const uuid=(v:unknown):v is string=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(v)
export interface RepairOrder {
 id:string;reporterId:string;technicianId:string|null;status:string;acceptedBy:string|null;acceptedAt:string|null;acceptanceComment:string|null;rating:number|null;evaluation:string|null;evaluatedAt:string|null;version:number;managed:boolean
 roomId?:string|null;seatId?:string|null;description?:string|null;dueAt?:string|null;escalationLevel?:number;repairReport?:string|null;assignedAt?:string|null;slaMinutes?:number|null
 desiredVersion?:number;appliedVersion?:number;desiredActive?:boolean;appliedActive?:boolean;restoreTerminal?:string|null;resourceError?:string|null;cancelReason?:string|null;cancelReviewedBy?:string|null;cancelReviewedAt?:string|null;safetyInspectedBy?:string|null;safetyInspectedAt?:string|null
}
export interface RepairEvent {id:string;actorId:string|null;eventType:string;fromStatus:string|null;toStatus:string;assignedTo:string|null;note:string|null;createdAt:string;orderVersion:number;rating:number|null}
export interface Room {id:string;code:string;name:string}
export interface Seat {id:string;roomId:string;code:string}
export interface Candidate {userId:string;displayName:string}
export interface Report {operationId:string;roomId:string;description:string;seatId?:string}
export interface Evaluation {version:number;rating:number;evaluation:string}
export type RepairAction='assignment'|'start'|'finish'|'acceptance'|'cancel'|'cancel-review'|'cancel-inspection'
export interface Command {orderId:string;action:RepairAction;actorId:string;body:Record<string,string|number|boolean>}
const record=(v:unknown):v is Record<string,any>=>!!v&&typeof v==='object'&&!Array.isArray(v)
const text=(v:unknown)=>typeof v==='string'
const integer=(v:unknown)=>Number.isSafeInteger(v)&&Number(v)>=0
const date=(v:unknown)=>typeof v==='string'&&Number.isFinite(Date.parse(v))
const optional=(v:unknown,check:(value:unknown)=>boolean)=>v===null||check(v)
const bad=()=>new ApiError('invalid_response')
const statuses=['SUBMITTED','ASSIGNED','RESTRICTION_PENDING','IN_PROGRESS','PENDING_ACCEPTANCE','RESTORATION_PENDING','CANCEL_REVIEW_PENDING','CANCEL_SAFETY_PENDING','COMPLETED','CANCELLED']
function common(v:unknown):asserts v is Record<string,any>{
 if(!record(v)||!uuid(v.id)||!uuid(v.reporterId)||!optional(v.technicianId,uuid)||!statuses.includes(v.status)||!optional(v.acceptedBy,uuid)||!optional(v.acceptedAt,date)||!optional(v.acceptanceComment,text)||!optional(v.rating,x=>integer(x)&&Number(x)>=1&&Number(x)<=5)||!optional(v.evaluation,text)||!optional(v.evaluatedAt,date)||!integer(v.version)||v.version<1)throw bad()
}
export function parseOrder(v:unknown):RepairOrder{
 common(v)
 if(v.managed!==undefined&&typeof v.managed!=='boolean')throw bad()
 if(v.managed===undefined&&('roomId'in v||'desiredVersion'in v))throw bad()
 if(v.managed===true){
  if(!uuid(v.roomId)||!optional(v.seatId,uuid)||!text(v.description)||!optional(v.dueAt,date)||!integer(v.escalationLevel)||!integer(v.desiredVersion)||!integer(v.appliedVersion)||typeof v.desiredActive!=='boolean'||typeof v.appliedActive!=='boolean'||!optional(v.resourceError,text)||!optional(v.repairReport,text)||!optional(v.restoreTerminal,text)||!optional(v.cancelReason,text)||!optional(v.cancelReviewedBy,uuid)||!optional(v.cancelReviewedAt,date)||!optional(v.safetyInspectedBy,uuid)||!optional(v.safetyInspectedAt,date))throw bad()
 }
 return {...v,managed:v.managed===true} as RepairOrder
}
function parseEvents(v:unknown):RepairEvent[]{
 if(!Array.isArray(v))throw bad()
 for(const e of v)if(!record(e)||!uuid(e.id)||!optional(e.actorId,uuid)||!text(e.eventType)||!optional(e.fromStatus,text)||!text(e.toStatus)||!optional(e.assignedTo,uuid)||!optional(e.note,text)||!date(e.createdAt)||!integer(e.orderVersion)||e.orderVersion<1||!optional(e.rating,n=>integer(n)&&Number(n)>=1&&Number(n)<=5))throw bad()
 return v as RepairEvent[]
}
async function csrf(signal?:AbortSignal){
 const v=await api.request<unknown>('/auth/csrf',{signal})
 if(!record(v)||v.headerName!=='X-XSRF-TOKEN'||typeof v.token!=='string'||!v.token||v.token.length>2048)throw bad()
 return {[v.headerName]:v.token}
}
async function write<T>(path:string,body:Record<string,any>,parse:(v:unknown)=>T,signal?:AbortSignal){
 return parse(await api.request<unknown>(path,{method:'POST',body,headers:await csrf(signal),signal}))
}
/** One published endpoint returns a genuinely empty200. Acknowledgement is not a completed repair. */
async function acceptance(id:string,body:Record<string,any>,signal?:AbortSignal):Promise<void>{
 if(!uuid(id))throw new ApiError('invalid_request')
 const headers=await csrf(signal),controller=new AbortController()
 let abortKind:'cancelled'|'timeout'|undefined
 const abort=()=>{abortKind='cancelled';controller.abort()}
 if(signal?.aborted)throw new ApiError('cancelled')
 signal?.addEventListener('abort',abort,{once:true})
 const timer=setTimeout(()=>{abortKind='timeout';controller.abort()},15000)
 try{
  const response=await fetch('/api/repairs/'+id+'/acceptance',{method:'POST',credentials:'same-origin',redirect:'error',headers:{...headers,Accept:'application/json','Content-Type':'application/json','Accept-Language':getAppLocale()},body:JSON.stringify(body),signal:controller.signal})
  const content=await response.text()
  if(!response.ok){
   let details:Record<string,any>={}
   if(response.headers.get('content-type')?.split(';')[0]?.toLowerCase()==='application/json'){try{const v:unknown=JSON.parse(content);if(record(v))details=v}catch{/* No request text or credentials in error output. */}}
   throw new ApiError('http',{status:response.status,code:typeof details.code==='string'?details.code:undefined,traceId:typeof details.traceId==='string'?details.traceId:undefined})
  }
  if(response.status!==200||content!=='')throw new ApiError('invalid_response',{status:response.status})
 }catch(e){if(e instanceof ApiError)throw e;if(abortKind)throw new ApiError(abortKind);throw new ApiError('network')}
 finally{clearTimeout(timer);signal?.removeEventListener('abort',abort)}
}
export const repairApi={
 async list(signal?:AbortSignal){const v=await api.request<unknown>('/repairs',{signal});if(!Array.isArray(v))throw bad();return v.map(parseOrder)},
 async detail(id:string,signal?:AbortSignal){if(!uuid(id))throw new ApiError('invalid_request');const value=parseOrder(await api.request<unknown>('/repairs/'+id,{signal}));if(value.id!==id)throw bad();return value},
 async history(id:string,signal?:AbortSignal){if(!uuid(id))throw new ApiError('invalid_request');return parseEvents(await api.request<unknown>('/repairs/'+id+'/history',{signal}))},
 report:(v:Report,signal?:AbortSignal)=>write('/repairs',v,parseOrder,signal),
 async command(v:Command,signal?:AbortSignal){if(!uuid(v.orderId))throw new ApiError('invalid_request');if(v.action==='acceptance'){await acceptance(v.orderId,v.body,signal);return undefined}return write('/repairs/'+v.orderId+'/'+v.action,v.body,value=>{const result=parseOrder(value);if(result.id!==v.orderId)throw bad();return result},signal)},
 evaluate:(id:string,v:Evaluation,signal?:AbortSignal)=>write('/repairs/'+id+'/evaluation',v,x=>{common(x);if(x.id!==id)throw bad();return x},signal),
 async candidates(signal?:AbortSignal):Promise<Candidate[]>{const v=await api.request<unknown>('/repairs/candidates',{signal});if(!Array.isArray(v)||v.some(x=>!record(x)||!uuid(x.userId)||!text(x.displayName)))throw bad();return v as Candidate[]},
 async rooms(signal?:AbortSignal):Promise<Room[]>{const v=await api.request<unknown>('/resources/rooms',{signal});if(!Array.isArray(v)||v.some(x=>!record(x)||!uuid(x.id)||!text(x.code)||!text(x.name)))throw bad();return v as Room[]},
 async seats(roomId:string,signal?:AbortSignal):Promise<Seat[]>{if(!uuid(roomId))throw new ApiError('invalid_request');const v=await api.request<unknown>('/resources/rooms/'+roomId+'/seats',{signal});if(!Array.isArray(v)||v.some(x=>!record(x)||!uuid(x.id)||x.roomId!==roomId||!text(x.code)))throw bad();return v as Seat[]},
}
