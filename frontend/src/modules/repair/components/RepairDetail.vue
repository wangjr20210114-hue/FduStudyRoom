<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { CurrentUser } from '@/modules/auth/types'
import { AppAlert, AppButton, AppPanel } from '@/shared/components'
import type { Candidate, Evaluation, RepairAction, RepairEvent, RepairOrder } from '../api/repairs'
import { complete, formatTime, overdue, restrictionConfirmed, sourceReleased } from '../model/presentation'
import RepairActions from './RepairActions.vue'
const props=defineProps<{order:RepairOrder;user:CurrentUser|null;events:RepairEvent[];loaded:boolean;candidates:Candidate[];busy:boolean;unknown:boolean;blocked:boolean;evaluationUnknown:boolean;now:number}>()
const emit=defineEmits<{refresh:[];command:[action:RepairAction,fields:Record<string,string|boolean>];evaluate:[value:Evaluation];retryCommand:[];retryEvaluation:[]}>()
const {t,te}=useI18n({useScope:'global'}),k=(key:string)=>t('modules.repair.'+key)
const label=(value:string,scope:string)=>te('modules.repair.'+scope+'.'+value)?k(scope+'.'+value):value
const status=computed(()=>props.order.managed&&props.order.status==='COMPLETED'&&!sourceReleased(props.order)?k('restorationPending'):label(props.order.status,'status'))
</script>
<template>
 <AppPanel data-test="repair-detail" :data-loaded="loaded?'true':'false'" :title="k('detail')">
  <template #actions><AppButton data-test="refresh" @click="emit('refresh')">{{ k('refresh') }}</AppButton></template>
  <p data-test="order-status" role="status">
   <span v-if="complete(order)" data-test="repair-completed">{{ status }}</span>
   <span v-else-if="order.status==='CANCELLED'" data-test="repair-cancelled">{{ status }}</span>
   <template v-else>{{ status }}</template>
  </p>
  <p v-if="restrictionConfirmed(order)" data-test="restriction-confirmed">{{ k('restrictionConfirmed') }}</p>
  <p v-else-if="order.managed&&order.appliedActive">{{ k('restrictionActive') }}</p>
  <p v-if="sourceReleased(order)" data-test="source-released">{{ k('sourceReleased') }}</p>
  <p v-else-if="order.managed&&order.desiredVersion===0">{{ k('noRestriction') }}</p>
  <p v-if="!order.managed">{{ k('legacyInfo') }}</p>
  <p v-if="(order.escalationLevel??0)>0" data-test="escalated" role="status">{{ k('escalated') }}</p>
  <p v-else-if="overdue(order,now)" data-test="deadline-overdue">{{ k('deadlineOverdue') }}</p>
  <dl>
   <dt>{{ k('id') }}</dt><dd>{{ order.id }}</dd>
   <dt>{{ k('reporter') }}</dt><dd>{{ order.reporterId }}</dd>
   <template v-if="order.roomId"><dt>{{ k('room') }}</dt><dd>{{ order.roomId }}</dd></template>
   <template v-if="order.seatId"><dt>{{ k('seat') }}</dt><dd>{{ order.seatId }}</dd></template>
   <template v-if="order.description"><dt>{{ k('description') }}</dt><dd class="note">{{ order.description }}</dd></template>
   <dt>{{ k('technician') }}</dt><dd data-test="current-technician">{{ order.technicianId??k('unassigned') }}</dd>
   <template v-if="order.assignedAt"><dt>{{ k('assignedAt') }} · {{ k('shanghai') }}</dt><dd>{{ formatTime(order.assignedAt) }}</dd></template>
   <template v-if="order.dueAt"><dt>{{ k('deadline') }} · {{ k('shanghai') }}</dt><dd>{{ formatTime(order.dueAt) }}</dd></template>
   <template v-if="order.slaMinutes"><dt>{{ k('sla') }}</dt><dd>{{ order.slaMinutes }}</dd></template>
   <dt>{{ t('modules.repair.version',{version:order.version}) }}</dt><dd>{{ order.version }}</dd>
   <template v-if="order.managed">
    <dt>{{ k('source') }}</dt><dd>M3 / {{ order.id }}</dd>
    <dt>{{ k('desiredVersion') }}</dt><dd>{{ order.desiredVersion }}</dd>
    <dt>{{ k('appliedVersion') }}</dt><dd>{{ order.appliedVersion }}</dd>
   </template>
   <template v-if="order.resourceError"><dt>{{ k('resourceError') }}</dt><dd>{{ order.resourceError }}</dd></template>
   <template v-if="order.repairReport"><dt>{{ k('repairReport') }}</dt><dd class="note">{{ order.repairReport }}</dd></template>
   <template v-if="order.acceptedBy"><dt>{{ k('acceptedBy') }}</dt><dd>{{ order.acceptedBy }}</dd><dt>{{ k('acceptedAt') }}</dt><dd>{{ formatTime(order.acceptedAt) }}</dd></template>
   <template v-if="order.acceptanceComment"><dt>{{ k('acceptanceComment') }}</dt><dd class="note">{{ order.acceptanceComment }}</dd></template>
   <template v-if="order.cancelReason"><dt>{{ k('cancelReason') }}</dt><dd class="note">{{ order.cancelReason }}</dd></template>
   <template v-if="order.cancelReviewedBy"><dt>{{ k('reviewedBy') }}</dt><dd>{{ order.cancelReviewedBy }}</dd><dt>{{ k('reviewedAt') }}</dt><dd>{{ formatTime(order.cancelReviewedAt) }}</dd></template>
   <template v-if="order.safetyInspectedBy"><dt>{{ k('inspectedBy') }}</dt><dd>{{ order.safetyInspectedBy }}</dd><dt>{{ k('inspectedAt') }}</dt><dd>{{ formatTime(order.safetyInspectedAt) }}</dd></template>
  </dl>
  <section v-if="order.evaluatedAt" data-test="evaluation-result">
   <p>{{ t('modules.repair.ratingValue',{rating:order.rating}) }} · {{ formatTime(order.evaluatedAt) }}</p><p v-if="order.evaluation" class="note">{{ order.evaluation }}</p>
  </section>
  <AppAlert v-if="!loaded" type="warning" :title="k('historyUnavailable')" />
  <RepairActions :order="order" :user="user" :events="events" :loaded="loaded" :candidates="candidates" :busy="busy" :unknown="unknown" :blocked="blocked" :evaluation-unknown="evaluationUnknown"
   @command="(action,fields)=>emit('command',action,fields)" @evaluate="value=>emit('evaluate',value)" @retry-command="emit('retryCommand')" @retry-evaluation="emit('retryEvaluation')" />
  <h3>{{ k('history') }}</h3>
  <ol data-test="history">
   <li v-for="item in events" :key="item.id">
    {{ label(item.eventType,'event') }} · {{ formatTime(item.createdAt) }} · {{ item.actorId??k('system') }} · {{ label(item.toStatus,'status') }} · {{ t('modules.repair.eventVersion',{version:item.orderVersion}) }}
    <span v-if="item.assignedTo"> · {{ k('assignmentTarget') }} {{ item.assignedTo }}</span>
    <span v-if="item.rating!==null"> · {{ t('modules.repair.ratingValue',{rating:item.rating}) }}</span>
    <p v-if="item.note" class="note">{{ item.note }}</p>
   </li>
  </ol>
  <p v-if="loaded&&!events.length">{{ k('noHistory') }}</p>
 </AppPanel>
</template>
<style scoped>
dl {display:grid;grid-template-columns:minmax(80px,170px) minmax(0,1fr);gap:var(--space-2)}dd {margin:0;overflow-wrap:anywhere}li {margin-bottom:var(--space-4);overflow-wrap:anywhere}.note {white-space:pre-wrap;overflow-wrap:anywhere}
</style>
