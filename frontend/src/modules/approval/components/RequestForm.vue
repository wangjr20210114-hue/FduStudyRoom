<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppAlert, AppButton, AppPanel } from '@/shared/components'
import { approvalApi, type Room, type RoomRequest, type Submission } from '../api/approvals'
import { errorKey, uncertain, validSubmission } from '../model/presentation'
const props=defineProps<{roomAccess:boolean;enabled:boolean}>()
const emit=defineEmits<{created:[value:RoomRequest];failure:[error:unknown]}>()
const {t}=useI18n({useScope:'global'}), k=(key:string)=>t('modules.approval.'+key)
const rooms=ref<Room[]>([]),roomId=ref(''),startsAt=ref(''),endsAt=ref(''),purpose=ref(''),attendeeCount=ref('1')
const busy=ref(false),loading=ref(false),unknown=ref(false),error=ref(''),command=ref<Submission|null>(null)
const controller=new AbortController()
onMounted(async()=>{
 if(!props.roomAccess)return
 loading.value=true
 try{rooms.value=await approvalApi.rooms(controller.signal)}catch(e){if(!controller.signal.aborted){error.value='errors.'+errorKey(e);emit('failure',e)}}finally{loading.value=false}
})
onBeforeUnmount(()=>controller.abort())
function local(value:string){
 if(!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value))return ''
 const result=value+':00+08:00',time=new Date(result)
 if(!Number.isFinite(+time))return ''
 const parts=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).format(time).replace(' ','T')
 return parts===value?result:''
}
async function submit(){
 if(busy.value||!props.enabled)return
 error.value=''
 if(!command.value){
  const from=local(startsAt.value),to=local(endsAt.value)
  const value:Submission={operationId:crypto.randomUUID(),roomId:roomId.value,startsAt:from,endsAt:to,purpose:purpose.value.trim(),attendeeCount:Number(attendeeCount.value)}
  if(!from||!to||!validSubmission(value,rooms.value.find(r=>r.id===roomId.value))){error.value='validation';return}
  command.value=value
 }
 busy.value=true
 try{const result=await approvalApi.submit(command.value);unknown.value=false;command.value=null;emit('created',result)}
 catch(e){unknown.value=uncertain(e);error.value=unknown.value?'unknown':'errors.'+errorKey(e);if(!unknown.value)command.value=null;emit('failure',e)}
 finally{busy.value=false}
}
</script>
<template>
 <AppPanel :title="k('new')">
  <p>{{ k('submitNote') }}</p><p>{{ k('shanghai') }} · YYYY-MM-DDTHH:mm</p>
  <AppAlert v-if="error" type="error" :title="k(error)" />
  <p v-if="!roomAccess">{{ k('noRoomAccess') }}</p>
  <p v-else-if="loading">{{ k('loading') }}</p>
  <p v-else-if="!rooms.length">{{ k('noRooms') }}</p>
  <form class="approval-form" @submit.prevent="submit">
   <fieldset :disabled="busy || unknown || !enabled">
    <legend>{{ k('chooseRoom') }}</legend>
    <AppButton v-for="room in rooms" :key="room.id" data-test="room-option" :aria-pressed="roomId===room.id" @click="roomId=room.id">
     {{ room.code }} · {{ room.name }} · {{ t('modules.approval.capacity',{count:room.capacity}) }} · {{ t('modules.approval.openHours',{from:room.openTime,to:room.closeTime}) }}
    </AppButton>
   </fieldset>
   <label>{{ k('startsAt') }}<input v-model="startsAt" data-test="starts-at" type="text" placeholder="YYYY-MM-DDTHH:mm" :disabled="busy||unknown||!enabled" /></label>
   <label>{{ k('endsAt') }}<input v-model="endsAt" data-test="ends-at" type="text" placeholder="YYYY-MM-DDTHH:mm" :disabled="busy||unknown||!enabled" /></label>
   <label>{{ k('purpose') }}<textarea v-model="purpose" data-test="purpose" maxlength="2000" :disabled="busy||unknown||!enabled" /></label>
   <label>{{ k('attendeeCount') }}<input v-model="attendeeCount" data-test="attendee-count" type="text" inputmode="numeric" :disabled="busy||unknown||!enabled" /></label>
   <AppButton v-if="unknown" data-test="retry-submit" variant="primary" :loading="busy" :disabled="!enabled" @click="submit">{{ k('retrySubmit') }}</AppButton>
   <AppButton v-else data-test="submit-request" html-type="submit" variant="primary" :loading="busy" :disabled="!enabled||!roomAccess||!rooms.length">{{ k('submit') }}</AppButton>
  </form>
 </AppPanel>
</template>
<style scoped>
.approval-form {display:grid;gap:var(--space-4)} label {display:grid;gap:var(--space-2)}
fieldset {display:flex;flex-wrap:wrap;gap:var(--space-2);border:1px solid var(--color-border)}
input,textarea {box-sizing:border-box;max-width:100%;padding:var(--space-2);border:1px solid var(--color-border);border-radius:var(--radius-control);background:var(--color-surface);color:var(--color-text);font:inherit}
textarea {min-height:80px} [aria-pressed="true"] {outline:2px solid var(--color-primary)}
</style>
