<script setup lang="ts">
import { computed,onMounted,onUnmounted,ref,shallowRef,watch } from 'vue'
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthSession } from '@/modules/auth/model/auth-session'
import { AppAlert,AppButton,AppLoading,AppPanel,AppStatusTag } from '@/shared/components'
import { ApiError } from '@/shared/api'
import { availability,cancelled,getRooms,getSeats,resourceMessage,saveRoom,saveSeat,type AvailableSeat,type Room,type RoomFields,type Seat,type SeatFields } from '../api'
import RoomForm from '../components/RoomForm.vue'
import SeatForm from '../components/SeatForm.vue'
import '../resource.css'
const props=withDefaults(defineProps<{manage?:boolean}>(),{manage:false});const session=useAuthSession();const{t}=useI18n({useScope:'global'});const m=(key:string,params:Record<string,string|number>={})=>t('modules.resource.'+key,params)
const has=(permission:string)=>session.user.value?.permissions.includes(permission)??false
const canWrite=computed(()=>props.manage&&has('resource:rooms:write'));const canQuery=computed(()=>has('reservation:read'))
const rooms=ref<Room[]>([]),selectedId=ref(''),roomsLoading=ref(true),roomsError=shallowRef<unknown>(null);const current=computed(()=>rooms.value.find(room=>room.id===selectedId.value))
const seats=ref<Seat[]>([]),socketFilter=ref(''),seatsLoading=ref(false),seatsError=shallowRef<unknown>(null)
const roomForm=ref<Room|null|undefined>(),seatForm=ref<Seat|null|undefined>(),roomSaving=ref(false),seatSaving=ref(false),roomError=shallowRef<unknown>(null),seatError=shallowRef<unknown>(null)
const starts=ref(''),ends=ref(''),querying=ref(false),available=ref<AvailableSeat[]|null>(null),queryError=shallowRef<unknown>(null),confirmedInterval=ref<{startsAt:string;endsAt:string}|null>(null)
let roomController:AbortController|undefined,seatController:AbortController|undefined,queryController:AbortController|undefined;let seatSequence=0,querySequence=0
async function loadRooms(){roomController?.abort();const active=new AbortController();roomController=active;roomsLoading.value=true;roomsError.value=null;try{const values=await getRooms(active.signal);if(active.signal.aborted)return;rooms.value=values;if(!values.some(room=>room.id===selectedId.value))selectedId.value=values[0]?.id??''}catch(error){if(!cancelled(error)&&!active.signal.aborted)roomsError.value=error}finally{if(roomController===active)roomsLoading.value=false}}
async function loadSeats(){seatController?.abort();const id=selectedId.value;const number=++seatSequence;if(!id){seats.value=[];return}const active=new AbortController();seatController=active;seatsLoading.value=true;seatsError.value=null;try{const values=await getSeats(id,socketFilter.value,active.signal);if(number===seatSequence&&!active.signal.aborted)seats.value=values}catch(error){if(number===seatSequence&&!cancelled(error)&&!active.signal.aborted)seatsError.value=error}finally{if(number===seatSequence)seatsLoading.value=false}}
watch([selectedId,socketFilter],loadSeats)
function clearAvailability(){queryController?.abort();++querySequence;available.value=null;querying.value=false;queryError.value=null;confirmedInterval.value=null}
watch(selectedId,()=>{roomForm.value=undefined;seatForm.value=undefined;roomError.value=null;seatError.value=null;seats.value=[];clearAvailability()})
function putRoom(room:Room){const index=rooms.value.findIndex(value=>value.id===room.id);if(index<0)rooms.value.push(room);else rooms.value[index]=room;selectedId.value=room.id}
async function submitRoom(input:RoomFields){if(roomSaving.value)return;const original=roomForm.value;roomSaving.value=true;roomError.value=null;try{const saved=await saveRoom(original?.id,input,original?.version);putRoom(saved);roomForm.value=undefined;clearAvailability()}catch(error){roomError.value=error}finally{roomSaving.value=false}}
async function disableRoom(){if(!current.value||roomSaving.value)return;const original=current.value;roomSaving.value=true;roomError.value=null;try{const saved=await saveRoom(original.id,{...original,enabled:!original.enabled},original.version);putRoom(saved);clearAvailability()}catch(error){roomError.value=error}finally{roomSaving.value=false}}
async function submitSeat(input:SeatFields){if(!current.value||seatSaving.value)return;const original=seatForm.value;seatSaving.value=true;seatError.value=null;try{await saveSeat(current.value.id,original?.id,input,original?.version);seatForm.value=undefined;await loadSeats();clearAvailability()}catch(error){seatError.value=error}finally{seatSaving.value=false}}
async function toggleSeat(seat:Seat){if(seatSaving.value)return;seatSaving.value=true;seatError.value=null;try{await saveSeat(seat.roomId,seat.id,{...seat,enabled:!seat.enabled},seat.version);await loadSeats();clearAvailability()}catch(error){seatError.value=error}finally{seatSaving.value=false}}
function instant(value:string){return value.length===16?value+':00+08:00':value+'+08:00'}
async function query(){if(!current.value||!canQuery.value)return;clearAvailability();const startsAt=instant(starts.value),endsAt=instant(ends.value);if(!starts.value||!ends.value||!Number.isFinite(Date.parse(startsAt))||!(Date.parse(endsAt)>Date.parse(startsAt))){queryError.value=new ApiError('invalid_request');return}const seq=++querySequence;const active=new AbortController();queryController=active;querying.value=true;try{const values=await availability(current.value.id,startsAt,endsAt,active.signal);if(seq===querySequence&&!active.signal.aborted){available.value=values;confirmedInterval.value={startsAt,endsAt}}}catch(error){if(seq===querySequence&&!cancelled(error)&&!active.signal.aborted)queryError.value=error}finally{if(seq===querySequence)querying.value=false}}
function departmentLabel(code:string|null){return code?m('status.department',{code}):m('status.public')}
function reservationTarget(seat:AvailableSeat){return{name:'reservations',query:{roomId:seat.roomId,seatId:seat.id,startsAt:confirmedInterval.value?.startsAt,endsAt:confirmedInterval.value?.endsAt}}}
onMounted(loadRooms);onUnmounted(()=>{roomController?.abort();seatController?.abort();queryController?.abort()})
</script>

<template>
 <div data-testid="resource-page" class="resource-page">
  <h1 tabindex="-1">{{m(manage?'manage':'title')}}</h1>
  <AppLoading v-if="roomsLoading" :label="m('status.loading')" />
  <AppAlert v-if="roomsError" :title="m('errors.load')" :description="resourceMessage(roomsError)" type="error"><template #actions><AppButton @click="loadRooms">{{m('actions.retry')}}</AppButton></template></AppAlert>
  <template v-if="!roomsLoading||rooms.length">
   <AppPanel>
    <div class="resource-actions"><label>{{m('fields.room')}}<select id="resource-room-select" v-model="selectedId" :disabled="!rooms.length||roomSaving||seatSaving"><option v-if="!rooms.length" value="">{{m('status.noRooms')}}</option><option v-for="room in rooms" :key="room.id" :value="room.id">{{room.code}} · {{room.name}}</option></select></label><AppButton v-if="canWrite" data-testid="resource-room-new" :disabled="roomSaving||seatSaving" @click="roomForm=null;roomError=null">{{m('actions.addRoom')}}</AppButton></div>
    <ul data-testid="resource-room-list" class="resource-results"><li v-for="room in rooms" :key="room.id">{{room.code}} · {{room.name}} · {{m(room.enabled?'status.enabled':'status.disabled')}}</li></ul>
    <p v-if="!rooms.length">{{m('status.noRooms')}}</p>
    <section v-if="current" data-testid="resource-room-detail">
     <h2>{{current.name}}</h2><div class="resource-details"><span>{{current.code}}</span><span>{{current.campus}}</span><span>{{current.building}}</span><span>{{m('fields.capacity')}} {{current.capacity}}</span><span>{{m('status.shanghai')}} {{current.openTime===current.closeTime?m('status.allDay'):current.openTime+'—'+current.closeTime}}</span><span>{{departmentLabel(current.departmentCode)}}</span><AppStatusTag :tone="current.enabled?'success':'warning'">{{m(current.enabled?'status.enabled':'status.disabled')}}</AppStatusTag><span>{{m(current.enabled && current.bookingEnabled?'status.bookingEnabled':'status.bookingDisabled')}}</span></div>
     <div class="resource-actions"><AppButton v-if="canWrite" data-testid="resource-room-edit" :disabled="roomSaving" @click="roomForm={...current};roomError=null">{{m('actions.editRoom')}}</AppButton><AppButton v-if="canWrite" data-testid="resource-room-disable" :loading="roomSaving" @click="disableRoom">{{m(current.enabled?'status.disabled':'status.enabled')}}</AppButton><RouterLink v-if="has('resource:checkin-codes:read')" :to="{name:'room-code',params:{roomId:current.id}}">{{m('actions.code')}}</RouterLink></div>
    </section>
    <RoomForm v-if="roomForm!==undefined" :key="roomForm?.id??'new'" :initial="roomForm" :saving="roomSaving" @save="submitRoom" @cancel="roomForm=undefined" />
    <AppAlert v-if="roomError" data-testid="resource-room-error" :title="m('errors.save')" :description="resourceMessage(roomError)" type="error"><template #actions><AppButton @click="roomForm=undefined;loadRooms()">{{m('actions.reload')}}</AppButton></template></AppAlert>
   </AppPanel>
   <AppPanel v-if="current">
    <div class="resource-actions"><h2>{{m('fields.seatCode')}}</h2><label>{{m('fields.socket')}}<select id="resource-socket-filter" v-model="socketFilter"><option value="">{{m('status.all')}}</option><option value="true">{{m('status.withSocket')}}</option><option value="false">{{m('status.withoutSocket')}}</option></select></label><AppButton v-if="canWrite" data-testid="resource-seat-new" :disabled="seatSaving" @click="seatForm=null;seatError=null">{{m('actions.addSeat')}}</AppButton></div>
    <AppLoading v-if="seatsLoading" :label="m('status.loading')" />
    <AppAlert v-if="seatsError" :title="m('errors.load')" :description="resourceMessage(seatsError)" type="error"><template #actions><AppButton @click="loadSeats">{{m('actions.retry')}}</AppButton></template></AppAlert>
    <div data-testid="resource-seat-list" class="resource-scroll"><table class="resource-table"><thead><tr><th>{{m('fields.seatCode')}}</th><th>{{m('fields.socket')}}</th><th>{{m('fields.state')}}</th><th v-if="canWrite">{{m('fields.actions')}}</th></tr></thead><tbody><tr v-for="seat in seats" :key="seat.id"><td>{{seat.code}}</td><td>{{m('sockets.'+seat.socketType)}}</td><td>{{m(seat.enabled?'status.enabled':'status.disabled')}}</td><td v-if="canWrite"><div class="resource-actions"><AppButton :disabled="seatSaving" @click="seatForm={...seat};seatError=null">{{m('actions.editSeat')}}</AppButton><AppButton :data-testid="'resource-seat-toggle-'+seat.id" :disabled="seatSaving" @click="toggleSeat(seat)">{{m(seat.enabled?'status.disabled':'status.enabled')}}</AppButton></div></td></tr></tbody></table><p v-if="!seats.length&&!seatsLoading">{{m('status.noSeats')}}</p></div>
    <SeatForm v-if="seatForm!==undefined" :key="seatForm?.id??'new'" :initial="seatForm" :saving="seatSaving" @save="submitSeat" @cancel="seatForm=undefined" />
    <AppAlert v-if="seatError" data-testid="resource-seat-error" :title="m('errors.save')" :description="resourceMessage(seatError)" type="error" />
   </AppPanel>
   <AppPanel v-if="current&&canQuery" :title="m('availability')">
    <form @submit.prevent="query"><div class="resource-fields"><label>{{m('fields.starts')}} · {{m('status.shanghai')}}<input id="resource-start" v-model="starts" type="datetime-local" required></label><label>{{m('fields.ends')}} · {{m('status.shanghai')}}<input id="resource-end" v-model="ends" type="datetime-local" required></label></div><div class="resource-actions"><AppButton data-testid="resource-query" html-type="submit" :loading="querying">{{m('actions.query')}}</AppButton></div></form>
    <AppLoading v-if="querying" :label="m('status.loading')" />
    <AppAlert v-if="queryError" :title="m('errors.load')" :description="resourceMessage(queryError)" type="error" />
    <div v-if="available!==null" data-testid="resource-availability-results"><p v-if="!available.length">{{m('status.noAvailable')}}</p><ul v-else class="resource-results"><li v-for="seat in available" :key="seat.id">{{seat.code}} <RouterLink v-if="has('reservation:create')" :data-testid="'resource-reserve-link-'+seat.id" :to="reservationTarget(seat)">{{m('actions.reserve')}}</RouterLink></li></ul></div>
   </AppPanel>
   <div v-if="current" class="resource-actions"><RouterLink v-if="has('approval:read')" data-testid="resource-request-link" :to="{name:'room-requests',query:{roomId:current.id}}">{{m('actions.request')}}</RouterLink><RouterLink v-if="has('repair:report')||has('repair:history:read')" data-testid="resource-repair-link" :to="{name:'repairs',query:{roomId:current.id}}">{{m('actions.repair')}}</RouterLink></div>
  </template>
 </div>
</template>
