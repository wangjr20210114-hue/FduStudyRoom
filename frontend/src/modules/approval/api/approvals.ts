import { api, ApiError } from '@/shared/api'

export const uuid = (v: unknown): v is string => typeof v === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(v)
export interface ApprovalTask { id: string; stepNo: number; approverId: string; status: string; dueAt: string | null; actedAt: string | null; decidedBy: string | null; comment: string | null }
export interface History { actorId: string | null; status: string; reason: string | null; occurredAt: string; version: number }
export interface Delivery { id: string; kind: string; state: string; attempts: number; lastError: string | null; nextRetryAt: string | null }
export interface RoomRequest {
 requestId:string; applicantId:string; roomId:string; startsAt:string; endsAt:string; purpose:string; attendeeCount:number
 status:string; version:number; flowVersion:string|number; expiresAt:string; resourceState:string; resourceVersion:number; resourceAbnormal:boolean; cancelReason:string|null
 tasks:ApprovalTask[]; history:History[]; deliveries:Delivery[]
}
export interface Page {items:RoomRequest[];page:number;size:number;total:number}
export interface Room {id:string;code:string;name:string;capacity:number;openTime:string;closeTime:string;bookingEnabled:boolean;enabled:boolean}
export interface Configuration {version:number;approverIds:string[]}
export interface Candidate {userId:string;displayName:string}
export interface Submission {operationId:string;roomId:string;startsAt:string;endsAt:string;purpose:string;attendeeCount:number}
export interface Cancellation {operationId:string;expectedVersion:number;reason:string}
const object=(v:unknown):v is Record<string,any>=>!!v&&typeof v==='object'&&!Array.isArray(v)
const integer=(v:unknown)=>Number.isSafeInteger(v)&&Number(v)>=0
const date=(v:unknown)=>typeof v==='string'&&Number.isFinite(Date.parse(v))
const nullable=(v:unknown,check:(v:unknown)=>boolean)=>v===null||check(v)
const string=(v:unknown)=>typeof v==='string'
const bad=()=>new ApiError('invalid_response')
export function parseRequest(v:unknown):RoomRequest {
 if(!object(v)||![v.requestId,v.applicantId,v.roomId].every(uuid)||![v.startsAt,v.endsAt,v.expiresAt].every(date)
 ||!string(v.purpose)||!integer(v.attendeeCount)||!integer(v.version)||v.version<1
 ||!(string(v.flowVersion)||integer(v.flowVersion))||!integer(v.resourceVersion)||typeof v.resourceAbnormal!=='boolean'||!nullable(v.cancelReason,string)
 ||!['PENDING','RESOURCE_PENDING','APPROVED','REJECTED','CANCELLED','EXPIRED','CONFLICT'].includes(v.status)
 ||!['UNCONFIRMED','PENDING','CONFIRMED','RELEASE_PENDING','RELEASED'].includes(v.resourceState)
 ||!Array.isArray(v.tasks)||!Array.isArray(v.history)||!Array.isArray(v.deliveries))throw bad()
 for(const t of v.tasks)if(!object(t)||!uuid(t.id)||!uuid(t.approverId)||!integer(t.stepNo)||!string(t.status)||!nullable(t.dueAt,date)||!nullable(t.actedAt,date)||!nullable(t.decidedBy,uuid)||!nullable(t.comment,string))throw bad()
 for(const h of v.history)if(!object(h)||!nullable(h.actorId,uuid)||!string(h.status)||!nullable(h.reason,string)||!date(h.occurredAt)||!integer(h.version))throw bad()
 for(const d of v.deliveries)if(!object(d)||!string(d.id)||!string(d.kind)||!string(d.state)||!integer(d.attempts)||!nullable(d.lastError,string)||!nullable(d.nextRetryAt,date))throw bad()
 return v as RoomRequest
}
function parsePage(v:unknown):Page {
 if(!object(v)||!Array.isArray(v.items)||![v.page,v.size,v.total].every(integer))throw bad()
 return {...v,items:v.items.map(parseRequest)} as Page
}
function parseConfiguration(v:unknown):Configuration{
 if(!object(v)||!integer(v.version)||!Array.isArray(v.approverIds)||!v.approverIds.every(uuid))throw bad()
 return v as Configuration
}
async function write<T>(path:string,body:Record<string,any>,parse:(value:unknown)=>T,method:'POST'|'PUT'='POST'){
 const token=await api.request<unknown>('/auth/csrf')
 if(!object(token)||token.headerName!=='X-XSRF-TOKEN'||!string(token.token)||!token.token||token.token.length>2048)throw bad()
 return parse(await api.request<unknown>(path,{method,body,headers:{[token.headerName]:token.token}}))
}
export const approvalApi={
 list:async(tasks:boolean,page:number,signal?:AbortSignal)=>parsePage(await api.request<unknown>(tasks?'/approvals/tasks':'/approvals/requests',{query:{page,size:20},signal})),
 detail:async(id:string,signal?:AbortSignal)=>parseRequest(await api.request<unknown>('/approvals/requests/'+id,{signal})),
 submit:(command:Submission)=>write('/approvals/requests',command,parseRequest),
 cancel:(id:string,command:Cancellation)=>write('/approvals/requests/'+id+'/cancel',command,parseRequest),
 async decide(id:string,taskId:string,version:number,decision:'APPROVE'|'REJECT',comment:string){
  // The compatible decision response is intentionally a subset. Never replace full details with it.
  await write('/approvals/requests/'+id+'/tasks/'+taskId+'/decision',{expectedVersion:version,decision,comment},v=>{
   if(!object(v)||v.requestId!==id||!integer(v.version)||!Array.isArray(v.tasks))throw bad()
   return v
  })
 },
 configuration:async()=>parseConfiguration(await api.request<unknown>('/approvals/configuration')),
 saveConfiguration:(v:Configuration)=>write('/approvals/configuration',{expectedVersion:v.version,approverIds:[...v.approverIds]},parseConfiguration,'PUT'),
 async candidates():Promise<Candidate[]>{
  const v=await api.request<unknown>('/approvals/candidates')
  if(!Array.isArray(v)||v.some(c=>!object(c)||!uuid(c.userId)||!string(c.displayName)))throw bad()
  return v as Candidate[]
 },
 async rooms(signal?:AbortSignal):Promise<Room[]>{
  const v=await api.request<unknown>('/resources/rooms',{signal})
  if(!Array.isArray(v)||v.some(r=>!object(r)||!uuid(r.id)||!string(r.name)||!string(r.code)||!integer(r.capacity)||!string(r.openTime)||!string(r.closeTime)||typeof r.enabled!=='boolean'||typeof r.bookingEnabled!=='boolean'))throw bad()
  return (v as Room[]).filter(r=>r.enabled&&r.bookingEnabled)
 },
}
