import { api, ApiError, type RequestOptions } from '@/shared/api'
export interface Role { id:string; code:string; name:string; enabled:boolean; permissions?:string[] }
export interface Permission { code:string; name:string; module:string }
export interface Account { id:string; loginName:string; displayName:string; roleId:string; role:string; enabled:boolean; authVersion:number; departmentCode:string|null; canApplyRoom:boolean }
export interface Settings { version:number; maxReservationHours:number; beforeStartReminderMinutes:number; afterStartReminderMinutes:number; checkinDeadlineMinutes:number; approvalTtlMinutes:number; repairSlaMinutes:number }
export type SettingValues=Omit<Settings,'version'>
export interface SettingsHistory { id:string; actorId:string; changedAt:string; version:number; before:Settings; after:Settings }
export async function identityWrite<T>(path:string,method:'POST'|'PUT'|'PATCH',body?:RequestOptions['body'],signal?:AbortSignal):Promise<T>{
 const csrf=await api.request<{headerName:string;token:string}>('/auth/csrf',{signal})
 if(csrf.headerName!=='X-XSRF-TOKEN'||typeof csrf.token!=='string'||!csrf.token)throw new ApiError('invalid_response')
 return api.request<T>(path,{method,body,signal,headers:{[csrf.headerName]:csrf.token}})
}
export const identityApi={
 roles:(signal?:AbortSignal)=>api.request<Role[]>('/admin/roles',{signal}),
 permissions:(signal?:AbortSignal)=>api.request<Permission[]>('/admin/permissions',{signal}),
 users:(signal?:AbortSignal)=>api.request<Account[]>('/admin/users',{signal}),
 createRole:(code:string,name:string,permissions:string[],signal?:AbortSignal)=>identityWrite<Role>('/admin/roles','POST',{code,name,permissions},signal),
 replacePermissions:(id:string,permissions:string[],signal?:AbortSignal)=>identityWrite<void>('/admin/roles/'+encodeURIComponent(id)+'/permissions','PUT',{permissions},signal),
 enableRole:(id:string,enabled:boolean,signal?:AbortSignal)=>identityWrite<void>('/admin/roles/'+encodeURIComponent(id)+'/enabled','PATCH',{enabled},signal),
 access:(id:string,roleId:string,enabled:boolean,expectedAuthVersion:number,signal?:AbortSignal)=>identityWrite<void>('/admin/users/'+encodeURIComponent(id)+'/access','PATCH',{roleId,enabled,expectedAuthVersion},signal),
 eligibility:(id:string,departmentCode:string|null,canApplyRoom:boolean,expectedAuthVersion:number,signal?:AbortSignal)=>identityWrite<void>('/admin/users/'+encodeURIComponent(id)+'/eligibility','PATCH',{departmentCode,canApplyRoom,expectedAuthVersion},signal),
 settings:(signal?:AbortSignal)=>api.request<Settings>('/admin/settings',{signal}),
 history:(signal?:AbortSignal)=>api.request<SettingsHistory[]>('/admin/settings/history',{signal}),
 saveSettings:(expectedVersion:number,values:SettingValues,signal?:AbortSignal)=>identityWrite<Settings>('/admin/settings','PUT',{expectedVersion,...values},signal),
}
