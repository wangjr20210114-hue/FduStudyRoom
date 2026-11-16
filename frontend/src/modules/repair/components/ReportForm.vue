<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppAlert, AppButton, AppPanel } from '@/shared/components'
import { repairApi, type RepairOrder, type Report, type Room, type Seat } from '../api/repairs'
import { errorKey, uncertain, validReport } from '../model/presentation'
const props=defineProps<{enabled:boolean;roomAccess:boolean}>()
const emit=defineEmits<{created:[value:RepairOrder];failure:[error:unknown]}>()
const {t}=useI18n({useScope:'global'}),k=(key:string)=>t('modules.repair.'+key)
const rooms=ref<Room[]>([]),seats=ref<Seat[]>([]),roomId=ref(''),seatId=ref(''),description=ref('')
const busy=ref(false),loading=ref(false),seatsLoading=ref(false),unknown=ref(false),blocked=ref(false),error=ref(''),command=ref<Report|null>(null)
const controller=new AbortController();let seatsController:AbortController|undefined,sequence=0,alive=true
onMounted(async()=>{
 if(!props.roomAccess)return
 loading.value=true
 try{rooms.value=await repairApi.rooms(controller.signal)}catch(e){if(alive){error.value='errors.'+errorKey(e);emit('failure',e)}}finally{if(alive)loading.value=false}
})
async function chooseRoom(id:string){
 if(busy.value||unknown.value||blocked.value||!props.enabled)return
 roomId.value=id;seatId.value='';seats.value=[];error.value='';const current=++sequence
 seatsController?.abort();const active=new AbortController();seatsController=active;seatsLoading.value=true
 try{const value=await repairApi.seats(id,active.signal);if(alive&&current===sequence)seats.value=value}
 catch(e){if(alive&&!active.signal.aborted&&current===sequence){error.value='errors.'+errorKey(e);emit('failure',e)}}
 finally{if(alive&&current===sequence)seatsLoading.value=false}
}
async function submit(){
 if(busy.value||blocked.value||!props.enabled)return
 error.value=''
 if(!command.value){
  if(!validReport(description.value,roomId.value,seatId.value,rooms.value,seats.value)){error.value='descriptionRequired';return}
  command.value={operationId:crypto.randomUUID(),roomId:roomId.value,description:description.value,...(seatId.value?{seatId:seatId.value}:{})}
 }
 busy.value=true
 try{const result=await repairApi.report(command.value,controller.signal);if(alive){unknown.value=false;command.value=null;emit('created',result)}}
 catch(e){if(alive){blocked.value=errorKey(e)==='idempotency';unknown.value=uncertain(e)||blocked.value;error.value=blocked.value?'errors.idempotency':unknown.value?'unknown':'errors.'+errorKey(e);if(!unknown.value)command.value=null;emit('failure',e)}}
 finally{if(alive)busy.value=false}
}
onBeforeUnmount(()=>{alive=false;controller.abort();seatsController?.abort()})
</script>
<template>
 <AppPanel :title="k('new')">
  <p>{{ k('reportInfo') }}</p>
  <AppAlert v-if="error" type="error" :title="k(error)" />
  <p v-if="!roomAccess">{{ k('noRoomAccess') }}</p>
  <p v-else-if="loading">{{ k('loading') }}</p>
  <p v-else-if="!rooms.length">{{ k('noRooms') }}</p>
  <form class="report-form" @submit.prevent="submit">
   <fieldset :disabled="busy||unknown||blocked||!enabled">
    <legend>{{ k('room') }}</legend>
    <AppButton v-for="room in rooms" :key="room.id" :data-room="room.id" :aria-pressed="roomId===room.id" @click="chooseRoom(room.id)">{{ room.code }}{{ room.name!==room.code?' · '+room.name:'' }}</AppButton>
   </fieldset>
   <fieldset v-if="roomId" :disabled="busy||unknown||blocked||!enabled||seatsLoading">
    <legend>{{ k('seat') }}</legend>
    <AppButton data-test="no-seat" :aria-pressed="!seatId" @click="seatId=''">{{ k('noSeat') }}</AppButton>
    <AppButton v-for="seat in seats" :key="seat.id" :data-seat="seat.id" :aria-pressed="seatId===seat.id" @click="seatId=seat.id">{{ seat.code }}</AppButton>
    <span v-if="seatsLoading">{{ k('loading') }}</span>
   </fieldset>
   <label>{{ k('description') }}<textarea v-model="description" data-test="description" :disabled="busy||unknown||blocked||!enabled" /></label>
   <AppButton v-if="unknown&&!blocked" data-test="retry-create" variant="primary" :loading="busy" :disabled="!enabled" @click="submit">{{ k('retryCreate') }}</AppButton>
   <AppButton v-else-if="!blocked" data-test="submit-report" html-type="submit" variant="primary" :loading="busy" :disabled="!enabled||!roomAccess||!rooms.length||seatsLoading">{{ k('submit') }}</AppButton>
  </form>
 </AppPanel>
</template>
<style scoped>
.report-form {display:grid;gap:var(--space-4)}fieldset {display:flex;flex-wrap:wrap;gap:var(--space-2);border:1px solid var(--color-border)}
label {display:grid;gap:var(--space-2)}textarea {min-height:100px;padding:var(--space-2);font:inherit;color:var(--color-text);background:var(--color-surface);border:1px solid var(--color-border);border-radius:var(--radius-control)}
[aria-pressed="true"] {outline:2px solid var(--color-primary)}
</style>
