import { api, ApiError, getApiErrorMessage } from '@/shared/api'
import { i18n } from '@/shared/i18n'
export interface Policy { version?:number;maxReservationHours?:number;beforeStartReminderMinutes?:number;afterStartReminderMinutes?:number;checkinDeadlineMinutes?:number }
export interface ReleaseProgress {status:'PENDING'|'CLAIMED'|'DONE'|'CANCELLED';attempts:number;nextRetryAt:string|null;errorCode:string|null}
export interface Delivery {eventId:string;status:string;receipt:{emailStatus:string|null;inAppStatus:string|null}}
export interface Reservation {
 id:string;userId:string;roomId:string|null;seatId:string;roomName:string|null;roomCode:string|null;seatCode:string|null
 startsAt:string;endsAt:string;status:'PENDING'|'RESERVED'|'CHECKED_IN'|'CANCEL_PENDING'|'CANCELLED'|'NO_SHOW'|'COMPLETED'|'REJECTED'
 checkedInAt:string|null;checkinDeadlineAt:string|null;cancelReason:string|null;cancelledAt:string|null;policySnapshot:Policy
 resourceState:string;resourceVersion:number;version:number;failureCode:string|null;metadataAvailable:boolean;timeZone:'Asia/Shanghai'
 releaseProgress?:ReleaseProgress;notifications?:Delivery[]
}
export interface AvailableSeat {id:string;roomId:string;code:string;roomName:string;roomCode:string;hasSocket:boolean}
export interface History {items:Reservation[];nextCursor:string|null;limit:number;timeZone:'Asia/Shanghai'}
export interface Filters {roomId?:string;seatId?:string;from?:string;to?:string}
export interface AvailabilityQuery {startsAt:string;endsAt:string;campus?:string;building?:string;hasSocket?:boolean;roomId?:string}
export interface Rebook {sourceReservationId:string;seatId:string;roomId:string;roomName:string;seatCode:string;policy:Policy;startsAt:string|null;endsAt:string|null;requiresNewTimeSelection:boolean}
export interface Command {kind:'create'|'cancel'|'admin-cancel'|'checkin';path:string;body:Readonly<Record<string,string>>}
const invalid=()=>new ApiError('invalid_response')
const object=(v:unknown):Record<string,unknown>=>{if(!v||typeof v!=='object'||Array.isArray(v))throw invalid();return v as Record<string,unknown>}
const text=(v:unknown)=>{if(typeof v!=='string'||!v.trim())throw invalid();return v}
const optionalText=(v:unknown)=>v===null||v===undefined?null:text(v)
const number=(v:unknown,min=0)=>{if(typeof v!=='number'||!Number.isSafeInteger(v)||v<min)throw invalid();return v}
const bool=(v:unknown)=>{if(typeof v!=='boolean')throw invalid();return v}
const instant=(v:unknown)=>{const s=text(v);if(!Number.isFinite(Date.parse(s)))throw invalid();return s}
const optionalInstant=(v:unknown)=>v===null||v===undefined?null:instant(v)
const list=(v:unknown)=>{if(!Array.isArray(v))throw invalid();return v}
function policy(v:unknown):Policy {const r=object(v??{}),out:Policy={};for(const key of ['version','maxReservationHours','beforeStartReminderMinutes','afterStartReminderMinutes','checkinDeadlineMinutes'] as const)if(r[key]!==undefined)out[key]=number(r[key]);return out}
function release(v:unknown):ReleaseProgress {
 const r=object(v),status=text(r.status);if(!['PENDING','CLAIMED','DONE','CANCELLED'].includes(status))throw invalid()
 const error=optionalText(r.errorCode),next=optionalInstant(r.nextRetryAt)
 if(status!=='PENDING'&&next!==null)throw invalid()
 const safe=error&&/^(?:DEPENDENCY_HTTP_[1-5][0-9]{2}|INVALID_RESOURCE_RELEASE|RESERVATION_DEPENDENCY_UNAVAILABLE|RESERVATION_DELIVERY_UNAVAILABLE|RELEASE_DEPENDENCY_UNAVAILABLE)$/.test(error)?error:error?'RELEASE_DEPENDENCY_UNAVAILABLE':null
 return{status:status as ReleaseProgress['status'],attempts:number(r.attempts),nextRetryAt:next,errorCode:['DONE','CANCELLED'].includes(status)?null:safe}
}
function reservation(v:unknown):Reservation {
 const r=object(v),status=text(r.status);if(!['PENDING','RESERVED','CHECKED_IN','CANCEL_PENDING','CANCELLED','NO_SHOW','COMPLETED','REJECTED'].includes(status)||r.timeZone!=='Asia/Shanghai')throw invalid()
 const out:Reservation={id:text(r.id),userId:text(r.userId),roomId:optionalText(r.roomId),seatId:text(r.seatId),roomName:optionalText(r.roomName),roomCode:optionalText(r.roomCode),seatCode:optionalText(r.seatCode),startsAt:instant(r.startsAt),endsAt:instant(r.endsAt),status:status as Reservation['status'],checkedInAt:optionalInstant(r.checkedInAt),checkinDeadlineAt:optionalInstant(r.checkinDeadlineAt),cancelReason:optionalText(r.cancelReason),cancelledAt:optionalInstant(r.cancelledAt),policySnapshot:policy(r.policySnapshot),resourceState:text(r.resourceState),resourceVersion:number(r.resourceVersion),version:number(r.version),failureCode:optionalText(r.failureCode),metadataAvailable:bool(r.metadataAvailable),timeZone:'Asia/Shanghai'}
 if(r.releaseProgress!==undefined&&r.releaseProgress!==null)out.releaseProgress=release(r.releaseProgress)
 if(r.notifications!==undefined)out.notifications=list(r.notifications).map(v=>{const n=object(v),receipt=object(n.receipt??{});return{eventId:text(n.eventId),status:text(n.status),receipt:{emailStatus:optionalText(receipt.emailStatus),inAppStatus:optionalText(receipt.inAppStatus)}}})
 return out
}
export const uuid=(value:string)=>/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)
export async function availability(query:AvailabilityQuery,signal?:AbortSignal):Promise<AvailableSeat[]> {return list(await api.request<unknown>('/reservations/availability',{query:{...query},signal})).map(v=>{const s=object(v);return{id:text(s.id??s.seatId),roomId:text(s.roomId),code:text(s.code),roomName:text(s.roomName),roomCode:text(s.roomCode),hasSocket:bool(s.hasSocket)}})}
export async function history(manage:boolean,filters:Filters,cursor?:string,signal?:AbortSignal):Promise<History> {
 const r=object(await api.request<unknown>(manage?'/admin/reservations':'/reservations',{query:{...filters,limit:20,cursor},signal}));if(r.timeZone!=='Asia/Shanghai')throw invalid();const limit=number(r.limit,1);if(limit>100)throw invalid();return{items:list(r.items).map(reservation),nextCursor:optionalText(r.nextCursor),limit,timeZone:'Asia/Shanghai'}
}
export async function detail(id:string,signal?:AbortSignal){if(!uuid(id))throw new ApiError('invalid_request');return reservation(await api.request<unknown>('/reservations/'+encodeURIComponent(id),{signal}))}
export async function rebook(id:string,signal?:AbortSignal):Promise<Rebook>{if(!uuid(id))throw new ApiError('invalid_request');const r=object(await api.request<unknown>('/reservations/'+encodeURIComponent(id)+'/rebook',{signal}));return{sourceReservationId:text(r.sourceReservationId),seatId:text(r.seatId),roomId:text(r.roomId),roomName:text(r.roomName),seatCode:text(r.seatCode),policy:policy(r.policy),startsAt:optionalInstant(r.startsAt),endsAt:optionalInstant(r.endsAt),requiresNewTimeSelection:bool(r.requiresNewTimeSelection)}}
async function csrf(){const r=object(await api.request<unknown>('/auth/csrf'));const name=text(r.headerName),token=text(r.token);if(!/^[A-Za-z0-9-]{1,64}$/.test(name)||/[\r\n]/.test(token))throw invalid();return{[name]:token}}
export async function send(command:Command){return reservation(await api.request<unknown>(command.path,{method:'POST',body:{...command.body},headers:await csrf()}))}
export function uncertain(error:unknown){if(!(error instanceof ApiError))return true;return ['network','timeout','invalid_response','cancelled'].includes(error.kind)||(error.kind==='http'&&(error.status===undefined||error.status>=500))}
export const cancelled=(error:unknown)=>error instanceof ApiError&&error.kind==='cancelled'
export function message(error:unknown){
 const mapping:Record<string,string>={INVALID_RESERVATION_TIME:'time',INVALID_RESERVATION_DURATION:'duration',RESOURCE_CONFLICT:'unavailable',RESERVATION_OPERATION_CONFLICT:'operation',RESERVATION_OWNER_REQUIRED:'owner',CHECKIN_WINDOW_CLOSED:'window',INVALID_CHECKIN_CODE:'code',RESERVATION_CANNOT_CANCEL:'cancel',ADMIN_CANCEL_REASON_REQUIRED:'reason',LEGACY_ROOM_METADATA_UNAVAILABLE:'metadata'}
 if(error instanceof ApiError&&error.code&&mapping[error.code])return i18n.global.t('modules.reservation.errors.'+mapping[error.code])
 return getApiErrorMessage(error)
}
