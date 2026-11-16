<script setup lang="ts">
import { computed,onMounted,onUnmounted,reactive,ref,shallowRef,watch } from 'vue'
import { RouterLink,useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthSession } from '@/modules/auth/model/auth-session'
import { AppAlert,AppButton,AppLoading,AppPanel,AppStatusTag } from '@/shared/components'
import { ApiError } from '@/shared/api'
import * as requests from '../api'
import type { AvailableSeat,Command,Filters,Policy,Reservation } from '../api'
import { displayTime,inputTime,instant,hourly,queryInstant } from '../time'
import '../reservation.css'

const props=withDefaults(defineProps<{manage?:boolean}>(),{manage:false})
const session=useAuthSession(),route=useRoute(),{t}=useI18n({useScope:'global'})
const m=(key:string,values:Record<string,string|number>={})=>t('modules.reservation.'+key,values)
const has=(permission:string)=>session.user.value?.permissions.includes(permission)??false
const currentUser=computed(()=>session.user.value?.id)
const known=new Map<string,Reservation>()
function remember(next:Reservation){const old=known.get(next.id);if(old&&old.version>next.version)return old;const merged={...next,notifications:next.notifications??old?.notifications};known.set(next.id,merged);return merged}

const booking=reactive({campus:'',building:'',roomId:'',starts:'',ends:'',socket:false})
const selected=ref<{id:string;roomId:string;code:string;roomName:string}|null>(null)
const available=ref<AvailableSeat[]|null>(null),searching=ref(false),searchError=shallowRef<unknown>(null),prefillError=ref(false)
const rebookInfo=ref<{hours:number|null;policy:Policy;needsTime:boolean}|null>(null),rebooking=ref(false)
const historyForm=reactive({roomId:'',seatId:'',from:'',to:''}),activeFilters=ref<Filters>({})
const items=ref<Reservation[]>([]),historyLoading=ref(false),historyError=shallowRef<unknown>(null),historyDirty=ref(false)
const cursor=ref<string|undefined>(),nextCursor=ref<string|null>(null),previous=ref<(string|undefined)[]>([]),historyRetryTarget=ref<string|undefined>()
const selectedDetail=ref<Reservation|null>(null),detailLoading=ref(false),detailError=shallowRef<unknown>(null)
const now=ref(Date.now()),checkinCode=ref(''),adminReason=ref(''),writeError=shallowRef<unknown>(null),pending=shallowRef<Command|null>(null),commandBusy=ref(false),lastCreated=ref('')
const locked=computed(()=>commandBusy.value||pending.value!==null)
let searchController:AbortController|undefined,historyController:AbortController|undefined,detailController:AbortController|undefined,rebookController:AbortController|undefined,timer:ReturnType<typeof setInterval>|undefined
let searchVersion=0,historyVersion=0,detailVersion=0
const deadlineReads=new Set<string>()
function snapshotSignature(){return JSON.stringify([selected.value?.id,booking.starts,booking.ends])}
function range(){const startsAt=instant(booking.starts),endsAt=instant(booking.ends);if(!startsAt||!endsAt||Date.parse(endsAt)<=Date.parse(startsAt))throw new ApiError('invalid_request',{code:'INVALID_RESERVATION_TIME'});return{startsAt,endsAt}}
function query(){const times=range();return{...times,campus:booking.campus.trim()||undefined,building:booking.building.trim()||undefined,roomId:booking.roomId||undefined,hasSocket:booking.socket?true:undefined}}
function invalidateSearch(){searchController?.abort();++searchVersion;available.value=null;searchError.value=null;searching.value=false;writeError.value=null}
watch(()=>[booking.campus,booking.building,booking.roomId,booking.starts,booking.ends,booking.socket],invalidateSearch)
async function search(){
 if(locked.value)return
 invalidateSearch();prefillError.value=false;const sequence=++searchVersion,controller=new AbortController();searchController=controller;searching.value=true
 try{const results=await requests.availability(query(),controller.signal);if(sequence===searchVersion&&!controller.signal.aborted)available.value=results}
 catch(error){if(sequence===searchVersion&&!requests.cancelled(error))searchError.value=error}
 finally{if(sequence===searchVersion)searching.value=false}
}
function select(seat:AvailableSeat){if(locked.value)return;selected.value={id:seat.id,roomId:seat.roomId,code:seat.code,roomName:seat.roomName};writeError.value=null}
function filters():Filters {
 const roomId=historyForm.roomId.trim(),seatId=historyForm.seatId.trim(),from=historyForm.from?instant(historyForm.from):null,to=historyForm.to?instant(historyForm.to):null
 if((roomId&&!requests.uuid(roomId))||(seatId&&!requests.uuid(seatId))||(historyForm.from&&!from)||(historyForm.to&&!to)||(from&&to&&Date.parse(to)<=Date.parse(from)))throw new ApiError('invalid_request',{code:'INVALID_RESERVATION_QUERY'})
 return{roomId:roomId||undefined,seatId:seatId||undefined,from:from??undefined,to:to??undefined}
}
watch(()=>[historyForm.roomId,historyForm.seatId,historyForm.from,historyForm.to],()=>{historyController?.abort();++historyVersion;historyDirty.value=true;historyLoading.value=false;items.value=[];cursor.value=undefined;nextCursor.value=null;previous.value=[];historyError.value=null})
async function loadHistory(target?:string){
 const sequence=++historyVersion,controller=new AbortController();historyController?.abort();historyController=controller;historyRetryTarget.value=target;historyLoading.value=true;historyError.value=null;items.value=[]
 try{const page=await requests.history(props.manage,activeFilters.value,target,controller.signal);if(sequence!==historyVersion||controller.signal.aborted)return;items.value=page.items.map(remember);cursor.value=target;nextCursor.value=page.nextCursor;historyDirty.value=false;return true}
 catch(error){if(sequence===historyVersion&&!requests.cancelled(error))historyError.value=error;return false}
 finally{if(sequence===historyVersion)historyLoading.value=false}
}
async function applyFilters(){if(locked.value)return;try{activeFilters.value=filters();previous.value=[];await loadHistory()}catch(error){historyError.value=error;historyDirty.value=true}}
async function next(){if(!nextCursor.value||historyLoading.value||historyDirty.value)return;const target=nextCursor.value,old=cursor.value;if(await loadHistory(target))previous.value.push(old)}
async function back(){if(!previous.value.length||historyLoading.value||historyDirty.value)return;if(await loadHistory(previous.value.at(-1)))previous.value.pop()}
async function readDetail(id:string){
 const sequence=++detailVersion,controller=new AbortController();detailController?.abort();detailController=controller;detailLoading.value=true;detailError.value=null
 if(selectedDetail.value?.id!==id)selectedDetail.value=null
 try{const result=await requests.detail(id,controller.signal);if(sequence===detailVersion&&!controller.signal.aborted)selectedDetail.value=remember(result)}
 catch(error){if(sequence===detailVersion&&!requests.cancelled(error)){detailError.value=error;if(error instanceof ApiError&&[401,403,404].includes(error.status??0))selectedDetail.value=null}}
 finally{if(sequence===detailVersion)detailLoading.value=false}
}
function showDetail(item:Reservation){
 if(locked.value)return
 writeError.value=null;checkinCode.value='';adminReason.value='';detailError.value=null
 if(props.manage){detailController?.abort();++detailVersion;detailLoading.value=false;selectedDetail.value=remember(item);return}
 void readDetail(item.id)
}
function refreshDetail(){const item=selectedDetail.value;if(!item||detailLoading.value)return;if(props.manage){const id=item.id;void loadHistory(cursor.value).then(()=>{const value=items.value.find(r=>r.id===id);if(value)selectedDetail.value=value});return}void readDetail(item.id)}
const own=computed(()=>selectedDetail.value?.userId===currentUser.value)
const canCancel=computed(()=>!props.manage&&own.value&&has('reservation:cancel')&&selectedDetail.value?.status==='RESERVED'&&selectedDetail.value.resourceState==='CONFIRMED'&&now.value<Date.parse(selectedDetail.value.startsAt))
const canAdminCancel=computed(()=>props.manage&&has('reservation:admin-cancel')&&!!selectedDetail.value&&['PENDING','RESERVED','CHECKED_IN'].includes(selectedDetail.value.status)&&now.value<Date.parse(selectedDetail.value.endsAt))
const canCheckin=computed(()=>!props.manage&&own.value&&has('reservation:checkin')&&selectedDetail.value?.status==='RESERVED'&&selectedDetail.value.resourceState==='CONFIRMED'&&selectedDetail.value.metadataAvailable)
const windowOpen=computed(()=>canCheckin.value&&!!selectedDetail.value?.checkinDeadlineAt&&now.value>=Date.parse(selectedDetail.value.startsAt)&&now.value<Math.min(Date.parse(selectedDetail.value.checkinDeadlineAt),Date.parse(selectedDetail.value.endsAt)))
const deadlineWait=computed(()=>selectedDetail.value?.status==='RESERVED'&&!!selectedDetail.value.checkinDeadlineAt&&now.value>=Date.parse(selectedDetail.value.checkinDeadlineAt))
const canCreate=computed(()=>!props.manage&&has('reservation:create')&&!!selected.value&&!locked.value&&snapshotSignature()!==lastCreated.value)
const canRebook=computed(()=>!props.manage&&own.value&&has('reservation:create')&&selectedDetail.value?.metadataAvailable)
async function execute(command:Command){
 try{
  const result=await requests.send(command);selectedDetail.value=remember(result);detailError.value=null;pending.value=null
  if(command.kind==='create')lastCreated.value=snapshotSignature()
  previous.value=[];await loadHistory()
 }catch(error){writeError.value=error;if(!requests.uncertain(error))pending.value=null}
 finally{commandBusy.value=false}
}
async function create(){
 if(!canCreate.value)return
 commandBusy.value=true;writeError.value=null;detailError.value=null
 let command:Command
 try{const times=range();if(!hourly(times.startsAt,times.endsAt))throw new ApiError('invalid_request',{code:'INVALID_RESERVATION_DURATION'});const results=await requests.availability(query());const seat=results.find(value=>value.id===selected.value!.id);if(!seat)throw new ApiError('invalid_request',{code:'RESOURCE_CONFLICT'});selected.value={id:seat.id,roomId:seat.roomId,code:seat.code,roomName:seat.roomName};selectedDetail.value=null;command={kind:'create',path:'/reservations',body:Object.freeze({operationId:crypto.randomUUID(),seatId:seat.id,...times})}}
 catch(error){writeError.value=error;commandBusy.value=false;return}
 pending.value=command;await execute(command)
}
async function action(kind:'cancel'|'admin-cancel'|'checkin'){
 if(locked.value||!selectedDetail.value)return
 writeError.value=null
 const value=selectedDetail.value
 if((kind==='cancel'&&!canCancel.value)||(kind==='admin-cancel'&&!canAdminCancel.value)||(kind==='checkin'&&!windowOpen.value)){writeError.value=new ApiError('invalid_request',{code:kind==='checkin'?'CHECKIN_WINDOW_CLOSED':'RESERVATION_CANNOT_CANCEL'});return}
 const body:Record<string,string>={operationId:crypto.randomUUID()}
 if(kind==='admin-cancel'){const reason=adminReason.value.trim();if(!reason){writeError.value=new ApiError('invalid_request',{code:'ADMIN_CANCEL_REASON_REQUIRED'});return}body.reason=reason}
 if(kind==='checkin'){const code=checkinCode.value;checkinCode.value='';if(!/^[0-9]{6}$/.test(code)){writeError.value=new ApiError('invalid_request',{code:'INVALID_CHECKIN_CODE'});return}body.code=code}
 const command:Command={kind:kind==='checkin'?'checkin':kind,path:'/reservations/'+encodeURIComponent(value.id)+'/'+(kind==='checkin'?'check-in':kind),body:Object.freeze(body)}
 pending.value=command;commandBusy.value=true;await execute(command)
}
async function retry(){if(commandBusy.value||!pending.value)return;commandBusy.value=true;writeError.value=null;await execute(pending.value)}
async function rebook(){
 if(!canRebook.value||locked.value||rebooking.value||!selectedDetail.value)return
 const original=selectedDetail.value,controller=new AbortController();rebookController?.abort();rebookController=controller;rebooking.value=true;writeError.value=null
 try{const value=await requests.rebook(original.id,controller.signal);if(controller.signal.aborted)return;const duration=(Date.parse(original.endsAt)-Date.parse(original.startsAt))/3600000;rebookInfo.value={hours:Number.isFinite(duration)&&duration>0?duration:null,policy:value.policy,needsTime:value.requiresNewTimeSelection};booking.campus='';booking.building='';booking.roomId=value.roomId;booking.starts=inputTime(value.startsAt);booking.ends=inputTime(value.endsAt);selected.value={id:value.seatId,roomId:value.roomId,code:value.seatCode,roomName:value.roomName};lastCreated.value=''}
 catch(error){if(!requests.cancelled(error))writeError.value=error}
 finally{rebooking.value=false}
}
function statusKey(value:Reservation){if(value.status==='PENDING'||(value.status==='RESERVED'&&value.resourceState!=='CONFIRMED'))return'PENDING';if(value.status==='CANCEL_PENDING'||value.resourceState==='RELEASE_PENDING')return'CANCEL_PENDING';return value.status}
function reason(value:string|null){return value&&['USER','ADMIN','NO_SHOW','SYSTEM_TIMEOUT','SYSTEM_MAINTENANCE'].includes(value)?m('reasons.'+value):m('unknown')}
function emailStatus(value:string|null){return m('email.'+(['NOT_CONFIGURED','PENDING','SENT','FAILED','UNKNOWN','CANCELLED'].includes(value??'')?value:'UNAVAILABLE'))}
function notificationStatus(value:string){return m('delivery.'+(['PENDING','CLAIMED','DONE','CANCELLED'].includes(value)?value:'UNAVAILABLE'))}
function reminder(item:Reservation){const minutes=item.policySnapshot.beforeStartReminderMinutes;return minutes===undefined?'':displayTime(Date.parse(item.startsAt)-minutes*60000)}
function releaseLabel(item:Reservation){const progress=item.releaseProgress;if(!progress)return m('release.unknown');if(progress.status==='DONE'&&item.resourceState==='RELEASED')return m('release.done');if(progress.status==='CLAIMED')return m('release.processing');if(progress.status==='CANCELLED')return m('release.stopped');return m('release.waiting')}
function prefill(){
 if(props.manage)return
 const keys=['roomId','seatId','startsAt','endsAt'],present=keys.some(key=>route.query[key]!==undefined)
 if(present){const seatId=route.query.seatId,roomId=route.query.roomId,start=queryInstant(route.query.startsAt),end=queryInstant(route.query.endsAt);if(typeof seatId!=='string'||!requests.uuid(seatId)||typeof roomId!=='string'||!requests.uuid(roomId)||!start||!end||Date.parse(end)<=Date.parse(start)){prefillError.value=true}else{booking.roomId=roomId;booking.starts=inputTime(start);booking.ends=inputTime(end);selected.value={id:seatId,roomId,code:seatId,roomName:''}}}
 const requested=route.query.reservationId
 if(requested!==undefined){if(typeof requested==='string'&&requests.uuid(requested))void readDetail(requested);else detailError.value=new ApiError('invalid_request')}
}
function tick(){now.value=Date.now();const item=selectedDetail.value;if(!props.manage&&item?.status==='RESERVED'&&item.checkinDeadlineAt&&now.value>=Date.parse(item.checkinDeadlineAt)&&!detailLoading.value){const key=item.id+':'+item.checkinDeadlineAt;if(!deadlineReads.has(key)){deadlineReads.add(key);void readDetail(item.id)}}}
onMounted(()=>{prefill();void loadHistory();timer=setInterval(tick,1000)})
onUnmounted(()=>{if(timer)clearInterval(timer);searchController?.abort();historyController?.abort();detailController?.abort();rebookController?.abort();checkinCode.value='';pending.value=null})
</script>

<template>
 <div data-reservations-page class="reservation-page">
  <h1 tabindex="-1">{{m(manage?'manage':'title')}}</h1>
  <span class="reservation-zone">{{m('timezone')}}</span>
  <AppPanel v-if="!manage&&has('reservation:read')" :title="m('searchTitle')">
   <AppAlert v-if="prefillError" data-testid="reservation-prefill-error" :title="m('errors.prefill')" type="error" />
   <form @submit.prevent="search"><div class="reservation-fields">
    <label>{{m('fields.campus')}}<input id="reservation-campus" v-model="booking.campus" :disabled="locked" maxlength="64"></label>
    <label>{{m('fields.building')}}<input id="reservation-building" v-model="booking.building" :disabled="locked" maxlength="128"></label>
    <label>{{m('fields.starts')}}<input id="reservation-start" v-model="booking.starts" type="datetime-local" :disabled="locked" required></label>
    <label>{{m('fields.ends')}}<input id="reservation-end" v-model="booking.ends" type="datetime-local" :disabled="locked" required></label>
   </div><div class="reservation-actions"><label class="reservation-check"><input id="reservation-socket" v-model="booking.socket" type="checkbox" :disabled="locked">{{m('fields.socket')}}</label><AppButton data-testid="reservation-search" html-type="submit" :loading="searching" :disabled="locked">{{m('actions.search')}}</AppButton></div></form>
   <AppLoading v-if="searching" :label="m('loading')" />
   <AppAlert v-if="searchError" data-testid="reservation-search-error" :title="m('errors.search')" :description="requests.message(searchError)" type="error"><template #actions><AppButton data-testid="reservation-search-retry" @click="search">{{m('actions.retry')}}</AppButton></template></AppAlert>
   <div v-if="available!==null" data-testid="reservation-results"><p v-if="!available.length">{{m('noSeats')}}</p><ul v-else class="reservation-list"><li v-for="seat in available" :key="seat.id">{{seat.roomName}} / {{seat.code}} · {{m(seat.hasSocket?'outlet':'noOutlet')}} <AppButton v-if="has('reservation:create')" :data-testid="'reservation-select-'+seat.id" :disabled="locked" @click="select(seat)">{{m('actions.select')}}</AppButton></li></ul></div>
   <p v-if="selected">{{m('selected')}} {{selected.roomName}} / {{selected.code}}</p>
   <div v-if="rebookInfo" data-testid="reservation-rebook-prefill"><span v-if="rebookInfo.hours!==null">{{m('duration',{hours:rebookInfo.hours})}}</span><span v-if="rebookInfo.policy.maxReservationHours!==undefined"> · {{m('maxHours',{hours:rebookInfo.policy.maxReservationHours})}}</span><p v-if="rebookInfo.needsTime">{{m('chooseNewTime')}}</p></div>
   <AppButton v-if="selected&&has('reservation:create')" data-testid="reservation-create" variant="primary" :disabled="!canCreate" :loading="commandBusy" @click="create">{{m('actions.create')}}</AppButton>
  </AppPanel>
  <section data-testid="reservation-command-state" :aria-busy="commandBusy?'true':'false'">
   <AppLoading v-if="commandBusy" :label="m('processing')" />
   <AppAlert v-if="pending&&!commandBusy" :title="m('unknownResult')" type="warning"><template #actions><AppButton data-testid="reservation-command-retry" @click="retry">{{m('actions.retryOriginal')}}</AppButton></template></AppAlert>
   <AppAlert v-if="writeError" data-testid="reservation-write-error" :title="m('errors.operation')" :description="requests.message(writeError)" type="error" />
  </section>
  <AppPanel :title="m(manage?'managementHistory':'historyTitle')">
   <section data-testid="reservation-history" :aria-busy="historyLoading?'true':'false'">
    <form @submit.prevent="applyFilters"><div class="reservation-fields">
     <label>{{m('fields.roomId')}}<input id="reservation-history-room" v-model="historyForm.roomId" :disabled="locked"></label>
     <label>{{m('fields.seatId')}}<input id="reservation-history-seat" v-model="historyForm.seatId" :disabled="locked"></label>
     <label>{{m('fields.from')}}<input id="reservation-history-from" v-model="historyForm.from" type="datetime-local" :disabled="locked"></label>
     <label>{{m('fields.to')}}<input id="reservation-history-to" v-model="historyForm.to" type="datetime-local" :disabled="locked"></label>
    </div><AppButton data-testid="reservation-history-search" html-type="submit" :loading="historyLoading" :disabled="locked">{{m('actions.search')}}</AppButton></form>
    <AppLoading v-if="historyLoading" :label="m('loading')" />
    <AppAlert v-if="historyError" :title="m('errors.history')" :description="requests.message(historyError)" type="error"><template #actions><AppButton @click="loadHistory(historyRetryTarget)">{{m('actions.retry')}}</AppButton></template></AppAlert>
    <p v-if="!historyLoading&&!historyError&&!historyDirty">{{m('pageCount',{count:items.length})}}</p>
    <div data-testid="reservation-history-list" class="reservation-scroll"><table class="reservation-table"><thead><tr><th>{{m('fields.roomSeat')}}</th><th>{{m('fields.time')}}</th><th>{{m('fields.status')}}</th><th v-if="manage">{{m('fields.user')}}</th><th>{{m('fields.actions')}}</th></tr></thead><tbody><tr v-for="item in items" :key="item.id" :data-reservation-id="item.id"><td>{{item.roomName??m('unknown')}} / {{item.seatCode??m('unknown')}}</td><td>{{displayTime(item.startsAt)}}—{{displayTime(item.endsAt)}}</td><td>{{m('states.'+statusKey(item))}}</td><td v-if="manage">{{item.userId}}</td><td><AppButton :data-testid="'reservation-detail-'+item.id" :disabled="locked" @click="showDetail(item)">{{m('actions.details')}}</AppButton></td></tr></tbody></table></div>
    <div class="reservation-actions"><AppButton data-testid="reservation-previous" :disabled="!previous.length||historyLoading||historyDirty||locked||historyError!==null" @click="back">{{m('actions.previous')}}</AppButton><AppButton data-testid="reservation-next" :disabled="!nextCursor||historyLoading||historyDirty||locked||historyError!==null" @click="next">{{m('actions.next')}}</AppButton></div>
   </section>
  </AppPanel>
  <AppAlert v-if="detailError" data-testid="reservation-detail-error" :title="m('errors.details')" :description="requests.message(detailError)" type="error" />
  <AppPanel v-if="detailLoading||selectedDetail" :title="m('detailsTitle')">
   <section data-testid="reservation-detail" :aria-busy="detailLoading?'true':'false'">
    <AppLoading v-if="detailLoading" :label="m('loading')" />
    <template v-if="selectedDetail">
     <p data-testid="reservation-detail-id">{{selectedDetail.id}}</p>
     <h2>{{selectedDetail.roomName??m('unknown')}} / {{selectedDetail.seatCode??m('unknown')}}</h2>
     <p>{{displayTime(selectedDetail.startsAt)}}—{{displayTime(selectedDetail.endsAt)}}</p>
     <AppStatusTag><span data-testid="reservation-status">{{m('states.'+statusKey(selectedDetail))}}</span></AppStatusTag>
     <p v-if="manage">{{m('fields.user')}} {{selectedDetail.userId}}</p>
     <p v-if="!selectedDetail.metadataAvailable">{{m('incomplete')}}</p>
     <p v-if="selectedDetail.checkedInAt">{{m('fields.checkedIn')}} {{displayTime(selectedDetail.checkedInAt)}}</p>
     <p v-if="selectedDetail.checkinDeadlineAt">{{m('fields.deadline')}} {{displayTime(selectedDetail.checkinDeadlineAt)}}</p>
     <p v-if="selectedDetail.cancelReason">{{m('fields.reason')}} {{reason(selectedDetail.cancelReason)}} <span v-if="selectedDetail.cancelledAt">{{displayTime(selectedDetail.cancelledAt)}}</span></p>
     <p v-if="selectedDetail.policySnapshot.maxReservationHours!==undefined">{{m('recordPolicy',{hours:selectedDetail.policySnapshot.maxReservationHours})}}</p>
     <p v-if="reminder(selectedDetail)" data-testid="reservation-reminder-time">{{m('fields.reminder')}} {{reminder(selectedDetail)}}</p>
     <AppAlert v-if="deadlineWait" data-testid="reservation-deadline-wait" :title="m('deadlineWaiting')" type="warning" />
     <div v-if="selectedDetail.releaseProgress||selectedDetail.status==='CANCEL_PENDING'" data-testid="reservation-release-progress" class="reservation-progress">
      <span>{{releaseLabel(selectedDetail)}}</span>
      <template v-if="selectedDetail.releaseProgress"><span>{{m('release.attempts',{count:selectedDetail.releaseProgress.attempts})}}</span><span v-if="selectedDetail.releaseProgress.nextRetryAt">{{m('release.next')}} {{displayTime(selectedDetail.releaseProgress.nextRetryAt)}}</span><span v-if="selectedDetail.releaseProgress.errorCode">{{m('release.failed')}}</span></template>
     </div>
     <div class="reservation-actions"><AppButton data-testid="reservation-refresh" :loading="detailLoading" :disabled="locked" @click="refreshDetail">{{m('actions.refresh')}}</AppButton><AppButton v-if="canRebook" data-testid="reservation-rebook" :loading="rebooking" :disabled="locked" @click="rebook">{{m('actions.rebook')}}</AppButton><AppButton v-if="!manage&&own&&has('reservation:cancel')&&['PENDING','RESERVED','CHECKED_IN'].includes(selectedDetail.status)" data-testid="reservation-cancel" :disabled="!canCancel||locked" @click="action('cancel')">{{m('actions.cancel')}}</AppButton></div>
     <p v-if="!manage&&own&&has('reservation:cancel')&&!canCancel&&['PENDING','RESERVED','CHECKED_IN'].includes(selectedDetail.status)">{{m('errors.cancel')}}</p>
     <div v-if="canAdminCancel" class="reservation-form"><label>{{m('fields.adminReason')}}<textarea id="reservation-admin-reason" v-model="adminReason" maxlength="500" :disabled="locked" /></label><AppButton data-testid="reservation-admin-cancel" variant="danger" :disabled="locked" @click="action('admin-cancel')">{{m('actions.adminCancel')}}</AppButton></div>
     <form v-if="canCheckin" class="reservation-actions" @submit.prevent="action('checkin')"><label>{{m('fields.code')}}<input id="reservation-checkin-code" v-model="checkinCode" inputmode="numeric" autocomplete="off" maxlength="6" pattern="[0-9]{6}" :disabled="locked"></label><AppButton data-testid="reservation-checkin" html-type="submit" :disabled="!windowOpen||locked">{{m('actions.checkin')}}</AppButton></form>
     <p v-if="canCheckin&&!windowOpen">{{m('errors.window')}}</p>
     <section v-if="!manage" data-testid="reservation-notifications"><h2>{{m('notificationsTitle')}}</h2><p>{{m('receiptHint')}}</p><ul class="reservation-list"><li v-for="event in selectedDetail.notifications??[]" :key="event.eventId" :data-notification-event="event.eventId"><span>{{event.eventId}}</span><span>{{notificationStatus(event.status)}}</span><span>{{emailStatus(event.receipt.emailStatus)}}</span></li></ul><RouterLink v-if="has('notification:read')" data-testid="reservation-notifications-link" :to="{name:'notifications'}">{{m('actions.notifications')}}</RouterLink></section>
    </template>
   </section>
  </AppPanel>
 </div>
</template>
