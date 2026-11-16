<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { AppAlert, AppButton, AppPanel } from '@/shared/components'
import { ApiError } from '@/shared/api'
import { useAuthSession } from '@/modules/auth/model/auth-session'
import { repairApi, uuid, type Candidate, type Command, type Evaluation, type RepairAction, type RepairEvent, type RepairOrder } from '../api/repairs'
import { canAssign, canCancel, canEvaluate, canFinish, canInspect, canReview, canSafety, canStart, errorKey, goalConfirmed, has, terminal, uncertain } from '../model/presentation'
import ReportForm from '../components/ReportForm.vue'
import RepairDetail from '../components/RepairDetail.vue'
const route=useRoute(),router=useRouter(),session=useAuthSession(),{user}=session
const {t}=useI18n({useScope:'global'}),k=(key:string)=>t('modules.repair.'+key)
const mode=computed(()=>route.name==='manage-repairs'?'manage':route.name==='maintenance-tasks'?'tasks':route.name==='maintenance-history'?'history':'repairs')
const rows=ref<RepairOrder[]>([]),candidates=ref<Candidate[]>([]),selected=ref<RepairOrder|null>(null),selectedId=ref(''),events=ref<RepairEvent[]>([]),loaded=ref(false)
const showForm=ref(false),createdReceipt=ref<RepairOrder|null>(null),listLoading=ref(false),busy=ref(false),error=ref(''),notice=ref(''),now=ref(Date.now())
const pending=ref<Command|null>(null),commandUnknown=ref(false),commandBlocked=ref(false),lastReceipt=ref<Command|null>(null),scopeLeft=ref(false)
const evaluation=ref<{orderId:string;actorId:string;value:Evaluation}|null>(null),evaluationUnknown=ref(false)
let alive=true,listSequence=0,detailSequence=0,listController:AbortController|undefined,detailController:AbortController|undefined
const lifetime=new AbortController();let timer:ReturnType<typeof setInterval>|undefined,clockTimer:ReturnType<typeof setInterval>|undefined
const visibleRows=computed(()=>rows.value.filter(r=>{
 if(mode.value==='repairs')return r.reporterId===user.value?.id
 if(mode.value==='tasks')return r.technicianId===user.value?.id&&!terminal(r)
 if(mode.value==='manage'&&!has(user.value,'repair:assign'))return (has(user.value,'repair:accept')&&['PENDING_ACCEPTANCE','CANCEL_SAFETY_PENDING'].includes(r.status))||(has(user.value,'repair:admin-cancel')&&r.status==='CANCEL_REVIEW_PENDING')
 return true
}))
const blockedSelection=computed(()=>busy.value||commandUnknown.value||evaluationUnknown.value)
function clearPrivate(){
 selected.value=null;selectedId.value='';events.value=[];loaded.value=false;detailSequence++;detailController?.abort()
}
async function expired(){
 clearPrivate();rows.value=[];pending.value=null;lastReceipt.value=null;evaluation.value=null;commandUnknown.value=false;evaluationUnknown.value=false;createdReceipt.value=null
 await session.initialize({preserveIdentity:true})
 if(!session.user.value&&router.currentRoute.value.meta.requiresAuth)await router.replace({name:'login'})
}
async function formFailure(e:unknown){if(e instanceof ApiError&&e.status===401)await expired()}
async function loadList(){
 if(!has(user.value,'repair:history:read'))return
 const current=++listSequence;listController?.abort();const controller=new AbortController();listController=controller;listLoading.value=true
 try{const value=await repairApi.list(controller.signal);if(alive&&current===listSequence)rows.value=value}
 catch(e){if(alive&&current===listSequence&&!(e instanceof ApiError&&e.kind==='cancelled')){error.value='errors.'+errorKey(e);if(e instanceof ApiError&&e.status===401)await expired()}}
 finally{if(alive&&current===listSequence)listLoading.value=false}
}
async function loadCandidates(){
 if(!has(user.value,'repair:assign'))return
 try{const value=await repairApi.candidates(lifetime.signal);if(alive)candidates.value=value}
 catch(e){if(alive&&!(e instanceof ApiError&&e.kind==='cancelled')){error.value='errors.'+errorKey(e);if(e instanceof ApiError&&e.status===401)await expired()}}
}
async function readDetail(ack?:Command):Promise<RepairOrder|null>{
 const id=selectedId.value
 if(!uuid(id)||!has(user.value,'repair:history:read'))return null
 const current=++detailSequence;detailController?.abort();const controller=new AbortController();detailController=controller
 try{
  const [row,history]=await Promise.all([repairApi.detail(id,controller.signal),repairApi.history(id,controller.signal)])
  if(!alive||current!==detailSequence||selectedId.value!==id)return null
  if(selected.value&&row.version<selected.value.version)return selected.value
  selected.value=row;events.value=history;loaded.value=true;rows.value=rows.value.map(old=>old.id===row.id?row:old)
  return row
 }catch(e){
  if(!alive||current!==detailSequence||controller.signal.aborted)return null
  loaded.value=false
  if(e instanceof ApiError&&e.status===401){await expired();throw e}
  if(e instanceof ApiError&&[403,404].includes(e.status??0)){
   clearPrivate()
   if(ack&&e.status===403){
    lastReceipt.value=ack;pending.value=null;commandUnknown.value=false;commandBlocked.value=false;scopeLeft.value=true;notice.value='scopeLeft';error.value=''
   }else {error.value=e.status===403?'errors.detailForbidden':'errors.notFound'}
   await loadList()
  }else error.value='errors.'+errorKey(e)
  throw e
 }
}
async function select(row:RepairOrder){
 if(blockedSelection.value)return
 detailSequence++;detailController?.abort();selected.value=row;selectedId.value=row.id;events.value=[];loaded.value=false
 pending.value=null;commandUnknown.value=false;commandBlocked.value=false;evaluation.value=null;evaluationUnknown.value=false;error.value='';notice.value='';scopeLeft.value=false
 try{await readDetail()}catch{/* Current scope/error is already represented above. */}
}
async function refresh(){
 error.value=''
 if(selectedId.value){try{await readDetail()}catch{/* The detail handler clears inaccessible content. */}}
 else await loadList()
}
async function created(row:RepairOrder){
 if(!alive)return
 if(row.reporterId!==user.value?.id){error.value='errors.invalidResponse';return}
 showForm.value=false;createdReceipt.value=row;error.value=''
 if(has(user.value,'repair:history:read')){await loadList();await select(row)}
}
function allowed(action:RepairAction,row:RepairOrder){
 const u=user.value
 return action==='assignment'?canAssign(row,u):action==='start'?canStart(row,u):action==='finish'?canFinish(row,u):action==='cancel'?canCancel(row,u):action==='acceptance'?canInspect(row,u,events.value,loaded.value):action==='cancel-review'?canReview(row,u):canSafety(row,u,events.value,loaded.value)
}
async function submitCommand(action:RepairAction,fields:Record<string,string|boolean>){
 const row=selected.value;if(!row||!user.value||busy.value||commandUnknown.value||!allowed(action,row))return
 pending.value={orderId:row.id,actorId:user.value.id,action,body:{operationId:crypto.randomUUID(),version:row.version,...fields}}
 commandBlocked.value=false;await executePending()
}
async function executePending(){
 const command=pending.value
 if(!command||busy.value||commandBlocked.value||command.actorId!==user.value?.id)return
 busy.value=true;error.value='';notice.value='';scopeLeft.value=false;detailSequence++;detailController?.abort()
 try{
  const result=await repairApi.command(command,lifetime.signal)
  if(!alive)return
  lastReceipt.value=command
  if(result&&(!selected.value||result.version>=selected.value.version)){selected.value=result;selectedId.value=result.id}
  let current:RepairOrder|null=null
  try{current=await readDetail(command)}catch(e){if(scopeLeft.value)return;if(e instanceof ApiError&&[401,403,404].includes(e.status??0))return;commandUnknown.value=true;error.value='unknown';return}
  if(!alive)return
  if(current&&goalConfirmed(command,current)){
   pending.value=null;commandUnknown.value=false;error.value='';notice.value=''
  }else if(current&&current.version>Number(command.body.version)){
   pending.value=null;commandUnknown.value=false;notice.value='alreadyChanged';error.value=''
  }else {commandUnknown.value=true;error.value='unknown'}
  await loadList()
 }catch(e){
  if(!alive)return
  if(uncertain(e)){commandUnknown.value=true;error.value='unknown'}
  else {
   commandBlocked.value=errorKey(e)==='idempotency';commandUnknown.value=commandBlocked.value;error.value='errors.'+errorKey(e)
   if(!commandBlocked.value)pending.value=null
   if(e instanceof ApiError&&e.status===401)await expired()
   else if(e instanceof ApiError&&e.status===409){try{await readDetail()}catch{/* Do not blind resubmit a changed version. */}}
  }
 }finally{if(alive)busy.value=false}
}
async function retryCommand(){
 if(!pending.value&&scopeLeft.value&&lastReceipt.value){pending.value=lastReceipt.value;selectedId.value=lastReceipt.value.orderId}
 await executePending()
}
const evaluationMatches=(row:RepairOrder,value:Evaluation)=>!!row.evaluatedAt&&row.rating===value.rating&&row.evaluation===value.evaluation&&row.version>value.version
async function reconcileEvaluation():Promise<RepairOrder|null>{
 const command=evaluation.value;if(!command)return null
 try{
  const current=await readDetail()
  if(current&&evaluationMatches(current,command.value)){evaluation.value=null;evaluationUnknown.value=false;error.value='';return current}
  if(current?.evaluatedAt||current&&current.version!==command.value.version){evaluation.value=null;evaluationUnknown.value=false;error.value='errors.stale'}
  return current
 }catch{return null}
}
async function submitEvaluation(value:Evaluation){
 const row=selected.value;if(!row||!user.value||busy.value||evaluationUnknown.value||!canEvaluate(row,user.value,events.value,loaded.value))return
 evaluation.value={orderId:row.id,actorId:user.value.id,value:{...value}}
 await executeEvaluation()
}
async function executeEvaluation(){
 const command=evaluation.value;if(!command||busy.value||command.actorId!==user.value?.id)return
 busy.value=true;error.value='';notice.value='';detailSequence++;detailController?.abort()
 try{
  await repairApi.evaluate(command.orderId,command.value,lifetime.signal)
  if(!alive)return
  const current=await reconcileEvaluation()
  if(evaluation.value&&!current){evaluationUnknown.value=true;error.value='unknown'}
  else if(evaluation.value){evaluationUnknown.value=true;error.value='unknown'}
  await loadList()
 }catch(e){
  if(!alive)return
  if(uncertain(e)){evaluationUnknown.value=true;error.value='unknown';await reconcileEvaluation()}
  else {evaluation.value=null;evaluationUnknown.value=false;error.value='errors.'+errorKey(e);if(e instanceof ApiError&&e.status===401)await expired();else if(e instanceof ApiError&&e.status===409){try{await readDetail()}catch{/* Preserve existing immutable result. */}}}
 }finally{if(alive)busy.value=false}
}
async function retryEvaluation(){
 if(busy.value||!evaluation.value)return
 const before=evaluation.value
 await reconcileEvaluation()
 if(evaluation.value===before&&selected.value&&selected.value.version===before.value.version&&!selected.value.evaluatedAt&&canEvaluate(selected.value,user.value,events.value,loaded.value))await executeEvaluation()
}
function poll(){
 if(!alive||document.visibilityState!=='visible'||busy.value||commandUnknown.value||evaluationUnknown.value||!selected.value)return
 const row=selected.value
 if(!terminal(row)||row.managed&&row.desiredVersion!==row.appliedVersion)void readDetail().catch(()=>{})
}
function visibility(){if(document.visibilityState==='visible')poll()}
watch(()=>user.value?.id,()=>{listController?.abort();clearPrivate();rows.value=[];candidates.value=[];showForm.value=false;createdReceipt.value=null;pending.value=null;lastReceipt.value=null;evaluation.value=null;commandUnknown.value=false;evaluationUnknown.value=false})
onMounted(async()=>{
 await Promise.all([loadList(),loadCandidates()])
 const id=route.query.orderId
 if(typeof id==='string'&&uuid(id)&&has(user.value,'repair:history:read')){selectedId.value=id;try{await readDetail()}catch{/* Access remains server-authoritative. */}}
 clockTimer=setInterval(()=>now.value=Date.now(),1000);timer=setInterval(poll,5000);document.addEventListener('visibilitychange',visibility)
})
onBeforeUnmount(()=>{alive=false;lifetime.abort();listController?.abort();detailController?.abort();if(timer)clearInterval(timer);if(clockTimer)clearInterval(clockTimer);document.removeEventListener('visibilitychange',visibility)})
</script>
<template>
 <div data-repair-root class="repair-page">
  <h1 tabindex="-1">{{ k('titles.'+mode) }}</h1>
  <AppAlert v-if="error" type="error" :title="k(error)" />
  <AppAlert v-if="notice" type="info" :title="k(notice)" />
  <AppButton v-if="scopeLeft&&lastReceipt" data-test="retry-command" :loading="busy" @click="retryCommand">{{ k('retryCommand') }}</AppButton>
  <AppButton v-if="mode==='repairs'&&has(user,'repair:report')" data-test="new-repair" :disabled="blockedSelection" @click="showForm=!showForm">{{ k(showForm?'close':'new') }}</AppButton>
  <ReportForm v-if="showForm&&has(user,'repair:report')" :enabled="has(user,'repair:report')" :room-access="has(user,'resource:rooms:read')" @created="created" @failure="formFailure" />
  <AppPanel v-if="createdReceipt&&!has(user,'repair:history:read')" :title="k('submitted')"><p>{{ createdReceipt.id }}</p><p>{{ k('reportInfo') }}</p></AppPanel>
  <AppPanel v-if="has(user,'repair:history:read')">
   <template #actions><AppButton v-if="!selected" data-test="refresh" :disabled="listLoading" @click="refresh">{{ k('refresh') }}</AppButton></template>
   <p v-if="listLoading" role="status">{{ k('loading') }}</p><p v-else-if="!visibleRows.length">{{ k('empty') }}</p>
   <ul data-test="order-list" class="order-list"><li v-for="row in visibleRows" :key="row.id"><span>{{ row.id }} · {{ k('status.'+row.status) }}</span><AppButton :data-open="row.id" :disabled="blockedSelection" @click="select(row)">{{ k('view') }}</AppButton></li></ul>
  </AppPanel>
  <RepairDetail v-if="selected" :order="selected" :user="user" :events="events" :loaded="loaded" :candidates="candidates" :busy="busy" :unknown="commandUnknown" :blocked="commandBlocked" :evaluation-unknown="evaluationUnknown" :now="now"
   @refresh="refresh" @command="submitCommand" @evaluate="submitEvaluation" @retry-command="retryCommand" @retry-evaluation="retryEvaluation" />
 </div>
</template>
<style scoped>
.repair-page {display:grid;gap:var(--space-4)}h1 {margin:0;font-size:24px}.order-list {list-style:none;margin:0;padding:0}.order-list li {display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:var(--space-3);padding-block:var(--space-3);border-bottom:1px solid var(--color-border)}.order-list span {overflow-wrap:anywhere}
</style>
