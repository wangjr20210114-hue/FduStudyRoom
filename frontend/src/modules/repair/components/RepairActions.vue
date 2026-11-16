<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { CurrentUser } from '@/modules/auth/types'
import { AppAlert, AppButton } from '@/shared/components'
import type { Candidate, Evaluation, RepairAction, RepairEvent, RepairOrder } from '../api/repairs'
import { canAssign, canCancel, canEvaluate, canFinish, canInspect, canReview, canSafety, canStart, validEvaluation, validRating } from '../model/presentation'
const props=defineProps<{order:RepairOrder;user:CurrentUser|null;events:RepairEvent[];loaded:boolean;candidates:Candidate[];busy:boolean;unknown:boolean;blocked:boolean;evaluationUnknown:boolean}>()
const emit=defineEmits<{command:[action:RepairAction,fields:Record<string,string|boolean>];evaluate:[value:Evaluation];retryCommand:[];retryEvaluation:[]}>()
const {t}=useI18n({useScope:'global'}),k=(key:string)=>t('modules.repair.'+key)
const technician=ref(''),report=ref(''),inspection=ref(''),reason=ref(''),review=ref(''),safety=ref(''),rating=ref(''),evaluation=ref(''),validation=ref('')
const disabled=computed(()=>props.busy||props.unknown||props.blocked)
watch(()=>props.order.id,()=>{technician.value='';report.value='';inspection.value='';reason.value='';review.value='';safety.value='';rating.value='';evaluation.value='';validation.value=''})
function send(action:RepairAction){
 validation.value=''
 if(disabled.value)return
 const r=props.order,u=props.user
 let fields:Record<string,string|boolean>
 if(action==='assignment'){
  if(!canAssign(r,u))return
  if(!props.candidates.some(c=>c.userId===technician.value)){validation.value='candidateRequired';return}
  fields={technicianId:technician.value}
 }else if(action==='start'){if(!canStart(r,u))return;fields={}}
 else if(action==='finish'){if(!canFinish(r,u))return;if(!report.value.trim()||report.value.length>4000){validation.value='reportRequired';return}fields={report:report.value}}
 else if(action==='cancel'){if(!canCancel(r,u))return;if(!reason.value.trim()||reason.value.length>4000){validation.value='cancelRequired';return}fields={reason:reason.value}}
 else return
 emit('command',action,fields)
}
function inspect(action:'acceptance'|'cancel-review'|'cancel-inspection',approved:boolean){
 validation.value=''
 if(disabled.value)return
 const r=props.order,u=props.user,h=props.events,ready=props.loaded
 if(action==='acceptance'&&!canInspect(r,u,h,ready))return
 if(action==='cancel-review'&&!canReview(r,u))return
 if(action==='cancel-inspection'&&!canSafety(r,u,h,ready))return
 const comment=action==='acceptance'?inspection.value:action==='cancel-review'?review.value:safety.value
 if(!comment.trim()||comment.length>4000){validation.value=action==='acceptance'?'inspectionRequired':action==='cancel-review'?'reviewRequired':'safetyRequired';return}
 emit('command',action,{approved,comment})
}
function evaluate(){
 validation.value=''
 if(disabled.value||props.evaluationUnknown||!canEvaluate(props.order,props.user,props.events,props.loaded))return
 if(!validRating(rating.value)){validation.value='ratingRequired';return}
 if(!validEvaluation(evaluation.value)){validation.value='evaluationRequired';return}
 emit('evaluate',{version:props.order.version,rating:Number(rating.value),evaluation:evaluation.value})
}
</script>
<template>
 <section class="repair-actions">
  <AppAlert v-if="validation" type="error" :title="k(validation)" />
  <AppButton v-if="unknown&&!blocked" data-test="retry-command" variant="primary" :loading="busy" @click="emit('retryCommand')">{{ k('retryCommand') }}</AppButton>
  <div v-if="canAssign(order,user)" class="action-group">
   <p>{{ k('candidate') }}</p><p v-if="!candidates.length">{{ k('noCandidates') }}</p>
   <AppButton v-for="person in candidates" :key="person.userId" :data-tech="person.userId" :aria-pressed="technician===person.userId" :disabled="disabled" @click="technician=person.userId">{{ person.displayName }} · {{ person.userId }}</AppButton>
   <AppButton data-test="assign" variant="primary" :disabled="disabled" @click="send('assignment')">{{ k('assign') }}</AppButton>
  </div>
  <div v-if="canStart(order,user)" class="action-group">
   <p>{{ k('startImpact') }}</p><AppButton data-test="start" variant="primary" :disabled="disabled" @click="send('start')">{{ k('start') }}</AppButton>
  </div>
  <div v-if="canFinish(order,user)" class="action-group">
   <label>{{ k('repairReport') }}<textarea v-model="report" data-test="repair-report" :disabled="disabled" /></label>
   <AppButton data-test="finish" variant="primary" :disabled="disabled" @click="send('finish')">{{ k('finish') }}</AppButton>
  </div>
  <div v-if="canInspect(order,user,events,loaded)" class="action-group">
   <label>{{ k('inspect') }}<textarea v-model="inspection" data-test="inspection-comment" :disabled="disabled" /></label>
   <AppButton data-test="accept" variant="primary" :disabled="disabled" @click="inspect('acceptance',true)">{{ k('accept') }}</AppButton>
   <AppButton data-test="return-repair" variant="danger" :disabled="disabled" @click="inspect('acceptance',false)">{{ k('reject') }}</AppButton>
  </div>
  <div v-if="canCancel(order,user)" class="action-group">
   <label>{{ k('cancelReason') }}<textarea v-model="reason" data-test="cancel-reason" :disabled="disabled" /></label>
   <AppButton data-test="cancel" variant="danger" :disabled="disabled" @click="send('cancel')">{{ k('cancel') }}</AppButton>
  </div>
  <div v-if="canReview(order,user)" class="action-group">
   <label>{{ k('reviewComment') }}<textarea v-model="review" data-test="review-comment" :disabled="disabled" /></label>
   <AppButton data-test="approve-cancel-review" variant="primary" :disabled="disabled" @click="inspect('cancel-review',true)">{{ k('reviewApprove') }}</AppButton>
   <AppButton data-test="reject-cancel-review" variant="danger" :disabled="disabled" @click="inspect('cancel-review',false)">{{ k('reviewReject') }}</AppButton>
  </div>
  <div v-if="canSafety(order,user,events,loaded)" class="action-group">
   <label>{{ k('safetyComment') }}<textarea v-model="safety" data-test="safety-comment" :disabled="disabled" /></label>
   <AppButton data-test="approve-safety" variant="primary" :disabled="disabled" @click="inspect('cancel-inspection',true)">{{ k('safetyApprove') }}</AppButton>
   <AppButton data-test="reject-safety" variant="danger" :disabled="disabled" @click="inspect('cancel-inspection',false)">{{ k('safetyReject') }}</AppButton>
  </div>
  <div v-if="canEvaluate(order,user,events,loaded)||evaluationUnknown" class="action-group">
   <label>{{ k('rating') }}<input v-model="rating" data-test="rating" type="text" inputmode="numeric" :disabled="disabled||evaluationUnknown" /></label>
   <label>{{ k('evaluation') }}<textarea v-model="evaluation" data-test="evaluation" :disabled="disabled||evaluationUnknown" /></label>
   <AppButton v-if="evaluationUnknown" data-test="retry-evaluation" variant="primary" :loading="busy" @click="emit('retryEvaluation')">{{ k('retryEvaluation') }}</AppButton>
   <AppButton v-else data-test="evaluate" variant="primary" :disabled="disabled" @click="evaluate">{{ k('evaluate') }}</AppButton>
  </div>
 </section>
</template>
<style scoped>
.repair-actions {display:grid;gap:var(--space-4);margin-top:var(--space-4)}.action-group {display:flex;flex-wrap:wrap;gap:var(--space-2);align-items:end}.action-group p {flex-basis:100%;margin:0}
label {display:grid;gap:var(--space-2);flex-basis:100%}textarea,input {box-sizing:border-box;max-width:100%;padding:var(--space-2);font:inherit;color:var(--color-text);background:var(--color-surface);border:1px solid var(--color-border);border-radius:var(--radius-control)}textarea {min-height:64px}[aria-pressed="true"] {outline:2px solid var(--color-primary)}
</style>
