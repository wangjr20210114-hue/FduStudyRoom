import { api, ApiError, getApiErrorMessage } from '@/shared/api'
import { i18n } from '@/shared/i18n'

export interface Room { id:string;code:string;name:string;campus:string;building:string;capacity:number;openTime:string;closeTime:string;departmentCode:string|null;bookingEnabled:boolean;enabled:boolean;version:number }
export type RoomFields=Omit<Room,'id'|'version'>
export interface Seat { id:string;roomId:string;code:string;socketType:'NONE'|'FIXED'|'RAIL';hasSocket:boolean;enabled:boolean;version:number }
export type SeatFields=Pick<Seat,'code'|'socketType'|'enabled'>
export interface AvailableSeat { id:string;roomId:string;code:string }
export interface DailyCode { roomId:string;roomName:string;date:string;code:string }
const invalid=()=>new ApiError('invalid_response')
function object(v:unknown):Record<string,unknown>{if(!v||typeof v!=='object'||Array.isArray(v))throw invalid();return v as Record<string,unknown>}
function text(v:unknown){if(typeof v!=='string'||!v.trim())throw invalid();return v}
function bool(v:unknown){if(typeof v!=='boolean')throw invalid();return v}
function number(v:unknown){if(typeof v!=='number'||!Number.isInteger(v)||v<1)throw invalid();return v}
function list(v:unknown){if(!Array.isArray(v))throw invalid();return v}
function room(v:unknown):Room{const r=object(v);if(r.departmentCode!==null&&typeof r.departmentCode!=='string')throw invalid();return{id:text(r.id),code:text(r.code),name:text(r.name),campus:text(r.campus),building:text(r.building),capacity:number(r.capacity),openTime:text(r.openTime),closeTime:text(r.closeTime),departmentCode:r.departmentCode as string|null,bookingEnabled:bool(r.bookingEnabled),enabled:bool(r.enabled),version:number(r.version)}}
function seat(v:unknown):Seat{const r=object(v),socket=text(r.socketType);if(!['NONE','FIXED','RAIL'].includes(socket))throw invalid();return{id:text(r.id??r.seatId),roomId:text(r.roomId),code:text(r.code),socketType:socket as Seat['socketType'],hasSocket:bool(r.hasSocket),enabled:bool(r.enabled),version:number(r.version)}}
async function csrf(signal?:AbortSignal){const r=object(await api.request<unknown>('/auth/csrf',{signal}));const name=text(r.headerName),token=text(r.token);if(!/^[A-Za-z0-9-]{1,64}$/.test(name)||/[\r\n]/.test(token))throw invalid();return{[name]:token}}
export async function getRooms(signal?:AbortSignal){return list(await api.request<unknown>('/resources/rooms',{signal})).map(room)}
export async function getSeats(id:string,hasSocket:string,signal?:AbortSignal){return list(await api.request<unknown>('/resources/rooms/'+encodeURIComponent(id)+'/seats',{query:{hasSocket:hasSocket===''?undefined:hasSocket==='true'},signal})).map(seat)}
export async function saveRoom(id:string|undefined,input:RoomFields,version?:number){const body:Record<string,string|number|boolean|null>={code:input.code,name:input.name,campus:input.campus,building:input.building,capacity:input.capacity,openTime:input.openTime,closeTime:input.closeTime,departmentCode:input.departmentCode,bookingEnabled:input.bookingEnabled,enabled:input.enabled};if(version!==undefined)body.expectedVersion=version;return room(await api.request<unknown>('/resources/rooms'+(id?'/'+encodeURIComponent(id):''),{method:id?'PUT':'POST',body,headers:await csrf()}))}
export async function saveSeat(roomId:string,id:string|undefined,input:SeatFields,version?:number){const body:Record<string,string|number|boolean|null>={code:input.code,socketType:input.socketType,enabled:input.enabled};if(version!==undefined)body.expectedVersion=version;return seat(await api.request<unknown>(id?'/resources/seats/'+encodeURIComponent(id):'/resources/rooms/'+encodeURIComponent(roomId)+'/seats',{method:id?'PUT':'POST',body,headers:await csrf()}))}
export async function availability(roomId:string,startsAt:string,endsAt:string,signal?:AbortSignal):Promise<AvailableSeat[]>{const values=list(await api.request<unknown>('/reservations/availability',{query:{roomId,startsAt,endsAt},signal}));return values.map(value=>{const r=object(value);return{id:text(r.id??r.seatId),roomId:text(r.roomId),code:text(r.code)}})}
export async function dailyCode(id:string,signal?:AbortSignal):Promise<DailyCode>{const r=object(await api.request<unknown>('/resources/rooms/'+encodeURIComponent(id)+'/checkin-code',{signal}));const result={roomId:text(r.roomId),roomName:text(r.roomName),date:text(r.date),code:text(r.code)};if(result.roomId!==id||!/^\d{4}-\d{2}-\d{2}$/.test(result.date)||!/^\d{6}$/.test(result.code))throw invalid();return result}
export function resourceMessage(error:unknown){if(error instanceof ApiError){const key=error.code==='STALE_RESOURCE_VERSION'?'changed':['CAPACITY_BELOW_SEAT_COUNT','ROOM_CAPACITY_EXCEEDED'].includes(error.code??'')?'capacity':error.code==='ROOM_DISABLED'?'disabled':null;if(key)return i18n.global.t('modules.resource.errors.'+key)}return getApiErrorMessage(error)}
export function cancelled(error:unknown){return error instanceof ApiError&&error.kind==='cancelled'}
