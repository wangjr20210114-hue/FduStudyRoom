<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppButton, AppPanel, AppAlert } from '@/shared/components'
import type { CurrentUser } from '@/modules/auth/types'
import type { RoomRequest } from '../api/approvals'
import { canCancel, cancellationNeedsReason, decidingTask, formatTime, fulfilled } from '../model/presentation'
const props=defineProps<{request:RoomRequest;user:CurrentUser|null;now:number;busy:boolean;cancelUnknown:boolean}>()
const emit=defineEmits<{refresh:[];cancel:[reason:string];decide:[decision:'APPROVE'|'REJECT',comment:string]}>()
const {t,te}=useI18n({useScope:'global'}),k=(key:string)=>t('modules.approval.'+key)
const comment=ref(''),reason=ref(''),validation=ref('')
watch(()=>props.request.requestId,()=>{comment.value='';reason.value='';validation.value=''})
const task=computed(()=>decidingTask(props.request,props.user,props.now)),allowedCancel=computed(()=>canCancel(props.request,props.user,props.now))
const status=(value:string,scope='status')=>te('modules.approval.'+scope+'.'+value)?k(scope+'.'+value):value
function decide(decision:'APPROVE'|'REJECT'){
 validation.value=''
 if(!task.value||props.busy)return
 if(comment.value.length>2000||(decision==='REJECT'&&!comment.value.trim())){validation.value='rejectReason';return}
 emit('decide',decision,comment.value.trim())
}
function cancel(){
 validation.value=''
 if(props.busy||(!props.cancelUnknown&&!allowedCancel.value))return
 if(!props.cancelUnknown&&(reason.value.length>2000||(cancellationNeedsReason(props.request,props.user)&&!reason.value.trim()))){validation.value='cancelRequired';return}
 emit('cancel',reason.value.trim())
}
function delivery(d:{kind:string;state:string}){
 if(d.state==='DONE')return d.kind==='NOTICE'?k('noticeAccepted'):d.kind==='AUDIT'?k('auditAccepted'):k('resource.'+(d.kind==='RELEASE'?'RELEASED':'CONFIRMED'))
 return d.state==='ERROR'||d.state==='FAILED'?k('deliveryError'):d.state==='INFLIGHT'?k('deliveryProcessing'):k('deliveryPending')
}
</script>
<template>
 <AppPanel data-test="request-detail" :title="k('detail')">
  <template #actions><AppButton data-test="refresh" @click="emit('refresh')">{{ k('refresh') }}</AppButton></template>
  <p data-test="request-status" role="status">{{ status(request.status) }}</p>
  <p v-if="fulfilled(request)" data-test="fulfilled" role="status">{{ k('fulfilled') }}</p>
  <AppAlert v-if="request.resourceAbnormal" type="warning" :title="k('abnormal')" />
  <p>{{ status(request.resourceState,'resource') }}</p>
  <dl>
   <dt>{{ k('requestId') }}</dt><dd>{{ request.requestId }}</dd>
   <dt>{{ k('applicant') }}</dt><dd>{{ request.applicantId }}</dd>
   <dt>{{ k('room') }}</dt><dd>{{ request.roomId }}</dd>
   <dt>{{ k('startsAt') }} · {{ k('shanghai') }}</dt><dd>{{ formatTime(request.startsAt) }}</dd>
   <dt>{{ k('endsAt') }} · {{ k('shanghai') }}</dt><dd>{{ formatTime(request.endsAt) }}</dd>
   <dt>{{ k('purpose') }}</dt><dd>{{ request.purpose }}</dd>
   <dt>{{ k('attendeeCount') }}</dt><dd>{{ request.attendeeCount }}</dd>
   <dt>{{ k('expiresAt') }}</dt><dd>{{ formatTime(request.expiresAt) }}</dd>
   <dt>{{ k('flowVersion') }}</dt><dd>{{ request.flowVersion }}</dd>
   <dt>{{ t('modules.approval.version',{version:request.version}) }}</dt><dd>{{ request.version }}</dd>
   <dt>{{ k('resourceVersion') }}</dt><dd>{{ request.resourceVersion }}</dd>
   <dt>{{ k('source') }}</dt><dd>M2 / {{ request.requestId }}</dd>
   <template v-if="request.cancelReason"><dt>{{ k('cancelReason') }}</dt><dd>{{ request.cancelReason==='START_REACHED'?k('startReached'):request.cancelReason }}</dd></template>
  </dl>
  <AppAlert v-if="validation" type="error" :title="k(validation)" />
  <div v-if="task" class="decision-controls">
   <label>{{ k('decisionComment') }}<textarea v-model="comment" data-test="decision-comment" maxlength="2000" :disabled="busy" /></label>
   <AppButton data-test="approve" variant="primary" :loading="busy" @click="decide('APPROVE')">{{ k('approve') }}</AppButton>
   <AppButton data-test="reject" variant="danger" :loading="busy" @click="decide('REJECT')">{{ k('reject') }}</AppButton>
  </div>
  <div v-if="allowedCancel||cancelUnknown" class="cancel-controls">
   <label>{{ k('cancelReason') }}<textarea v-model="reason" data-test="cancel-reason" maxlength="2000" :disabled="busy||cancelUnknown" /></label>
   <AppButton v-if="cancelUnknown" data-test="retry-cancel" variant="danger" :loading="busy" @click="cancel">{{ k('retryCancel') }}</AppButton>
   <AppButton v-else data-test="cancel-request" variant="danger" :loading="busy" @click="cancel">{{ k('cancel') }}</AppButton>
  </div>
  <h3>{{ k('taskList') }}</h3>
  <ol data-test="task-list">
   <li v-for="step in request.tasks" :key="step.id">
    {{ t('modules.approval.step',{step:step.stepNo}) }} · {{ step.approverId }} · {{ status(step.status,'taskStatus') }}
    <span v-if="step.dueAt"> · {{ k('dueAt') }} {{ formatTime(step.dueAt) }}</span>
    <span v-if="step.actedAt"> · {{ k('actedAt') }} {{ formatTime(step.actedAt) }} · {{ step.decidedBy }}</span>
    <p v-if="step.comment">{{ step.comment }}</p>
   </li>
  </ol>
  <h3>{{ k('history') }}</h3>
  <ol data-test="history">
   <li v-for="(event,index) in request.history" :key="index">
    {{ formatTime(event.occurredAt) }} · {{ event.actorId ?? k('system') }} · {{ status(event.status) }} · {{ t('modules.approval.version',{version:event.version}) }}
    <p v-if="event.reason">{{ event.reason }}</p>
   </li>
  </ol>
  <p v-if="!request.history.length">{{ k('noHistory') }}</p>
  <h3>{{ k('deliveries') }}</h3>
  <ul data-test="deliveries">
   <li v-for="item in request.deliveries" :key="item.id">
    {{ status(item.kind,'kind') }} · {{ delivery(item) }} · {{ t('modules.approval.attempts',{count:item.attempts}) }}
    <span v-if="item.lastError"> · {{ item.lastError }}</span>
    <span v-if="item.nextRetryAt&&item.state!=='DONE'"> · {{ k('nextRetry') }} {{ formatTime(item.nextRetryAt) }}</span>
   </li>
  </ul>
  <p v-if="!request.deliveries.length">{{ k('noDeliveries') }}</p>
 </AppPanel>
</template>
<style scoped>
dl {display:grid;grid-template-columns:minmax(80px,160px) minmax(0,1fr);gap:var(--space-2)} dd {margin:0;overflow-wrap:anywhere}
li {margin-bottom:var(--space-3);overflow-wrap:anywhere}.decision-controls,.cancel-controls {display:flex;flex-wrap:wrap;align-items:end;gap:var(--space-2);margin-block:var(--space-4)}
label {display:grid;gap:var(--space-2);flex-basis:100%} textarea {min-height:64px;padding:var(--space-2);font:inherit;color:var(--color-text);background:var(--color-surface);border:1px solid var(--color-border);border-radius:var(--radius-control)}
</style>
