<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { AppAlert, AppButton, AppPanel } from '@/shared/components'
import { ApiError } from '@/shared/api'
import { useAuthSession } from '@/modules/auth/model/auth-session'
import { approvalApi, uuid, type Cancellation, type Page, type RoomRequest } from '../api/approvals'
import { canCancel, decidingTask, errorKey, formatTime, has, uncertain } from '../model/presentation'
import RequestForm from '../components/RequestForm.vue'
import RequestDetail from '../components/RequestDetail.vue'
import ApprovalFlow from '../components/ApprovalFlow.vue'
const route=useRoute(),router=useRouter(),session=useAuthSession(),{user}=session
const {t}=useI18n({useScope:'global'}),k=(key:string)=>t('modules.approval.'+key)
const tasks=computed(()=>route.name==='approvals'),allowedList=computed(()=>has(user.value,tasks.value?'approval:decide':'approval:read'))
const list=ref<Page>({items:[],page:0,size:20,total:0}),selected=ref<RoomRequest|null>(null),selectedId=ref('')
const currentTime=ref(Date.now())
const loading=ref(false),actionBusy=ref(false),showForm=ref(false),error=ref('')
const cancelCommand=ref<Cancellation|null>(null),cancelUnknown=ref(false)
let alive=true,listSequence=0,detailSequence=0,listController:AbortController|undefined,detailController:AbortController|undefined
let timer:ReturnType<typeof setInterval>|undefined,clockTimer:ReturnType<typeof setInterval>|undefined
async function failure(e:unknown,detail=false){
 if(e instanceof ApiError&&e.kind==='cancelled')return
 const key=errorKey(e)
 error.value=detail&&e instanceof ApiError&&e.status===403?'errors.detailForbidden':'errors.'+key
 if(e instanceof ApiError&&[401,403,404].includes(e.status??0)&&detail){selected.value=null;selectedId.value='';cancelCommand.value=null;cancelUnknown.value=false}
 if(e instanceof ApiError&&e.status===401){
  list.value={items:[],page:0,size:20,total:0};selected.value=null;selectedId.value=''
  await session.initialize({preserveIdentity:true})
  if(!session.user.value&&router.currentRoute.value.meta.requiresAuth)await router.replace({name:'login'})
 }
}
async function loadList(page=list.value.page){
 if(!allowedList.value)return
 const current=++listSequence;listController?.abort();const controller=new AbortController();listController=controller;loading.value=true
 try{
  const result=await approvalApi.list(tasks.value,page,controller.signal)
  if(alive&&current===listSequence)list.value=result
 }catch(e){if(alive&&current===listSequence)await failure(e)}
 finally{if(alive&&current===listSequence)loading.value=false}
}
async function select(request:RoomRequest){
 selectedId.value=request.requestId;selected.value=request;cancelCommand.value=null;cancelUnknown.value=false;error.value=''
 if(has(user.value,'approval:read'))await refreshDetail()
}
async function refreshDetail(){
 const id=selectedId.value
 if(!uuid(id))return
 const current=++detailSequence;detailController?.abort();const controller=new AbortController();detailController=controller
 try{
  let value:RoomRequest|undefined
  if(has(user.value,'approval:read'))value=await approvalApi.detail(id,controller.signal)
  else if(tasks.value&&has(user.value,'approval:decide')){
   const page=await approvalApi.list(true,list.value.page,controller.signal)
   if(alive&&current===detailSequence)list.value=page
   value=page.items.find(item=>item.requestId===id)
   if(!value)throw new ApiError('http',{status:403})
  }else throw new ApiError('http',{status:403})
  if(alive&&current===detailSequence&&id===selectedId.value&&(!selected.value||value.version>=selected.value.version))selected.value=value
 }catch(e){if(alive&&current===detailSequence&&id===selectedId.value)await failure(e,true)}
}
async function refresh(){
 error.value=''
 if(selectedId.value)await refreshDetail()
 else await loadList()
}
async function created(value:RoomRequest){
 showForm.value=false;error.value='';selected.value=value;selectedId.value=value.requestId
 await loadList(0)
}
async function decide(decision:'APPROVE'|'REJECT',comment:string){
 const row=selected.value;if(!row||actionBusy.value)return
 const task=decidingTask(row,user.value);if(!task)return
 error.value='';actionBusy.value=true;detailSequence++;detailController?.abort()
 try{await approvalApi.decide(row.requestId,task.id,row.version,decision,comment);await refreshDetail();await loadList()}
 catch(e){await failure(e,true);if(e instanceof ApiError&&e.status===409)await refreshDetail()}
 finally{actionBusy.value=false}
}
async function cancel(reason:string){
 const row=selected.value;if(!row||actionBusy.value)return
 if(!cancelCommand.value){
  if(!canCancel(row,user.value))return
  cancelCommand.value={operationId:crypto.randomUUID(),expectedVersion:row.version,reason}
 }
 error.value='';actionBusy.value=true;detailSequence++;detailController?.abort()
 try{
  const result=await approvalApi.cancel(row.requestId,cancelCommand.value)
  selected.value=result;cancelCommand.value=null;cancelUnknown.value=false;await loadList()
 }catch(e){
  cancelUnknown.value=uncertain(e)
  if(cancelUnknown.value)error.value='unknown'
  else {cancelCommand.value=null;await failure(e,true);if(e instanceof ApiError&&e.status===409)await refreshDetail()}
 }finally{actionBusy.value=false}
}
function changePage(delta:number){
 selectedId.value='';selected.value=null;detailSequence++;detailController?.abort();cancelCommand.value=null;cancelUnknown.value=false
 void loadList(list.value.page+delta)
}
function poll(){
 const r=selected.value
 if(!alive||document.visibilityState!=='visible'||actionBusy.value||cancelUnknown.value||!r)return
 if(['PENDING','RESOURCE_PENDING','CONFLICT'].includes(r.status)||r.resourceState==='RELEASE_PENDING'||(r.status==='APPROVED'&&Date.parse(r.endsAt)>Date.now()))void refreshDetail()
}
function visible(){if(document.visibilityState==='visible')poll()}
watch(()=>user.value?.id,()=>{listController?.abort();detailController?.abort();list.value={items:[],page:0,size:20,total:0};selected.value=null;selectedId.value='';showForm.value=false;cancelCommand.value=null;cancelUnknown.value=false})
onMounted(async()=>{
 await loadList()
 const query=route.query.requestId
 if(typeof query==='string'&&uuid(query)){selectedId.value=query;await refreshDetail()}
 clockTimer=setInterval(()=>currentTime.value=Date.now(),1000);timer=setInterval(poll,5000);document.addEventListener('visibilitychange',visible)
})
onBeforeUnmount(()=>{alive=false;listController?.abort();detailController?.abort();if(timer)clearInterval(timer);if(clockTimer)clearInterval(clockTimer);document.removeEventListener('visibilitychange',visible)})
</script>
<template>
 <div data-approval-root class="approval-page">
  <h1 tabindex="-1">{{ k(tasks?'manageTitle':'title') }}</h1>
  <AppAlert v-if="error" type="error" :title="k(error)" />
  <AppButton v-if="!tasks&&has(user,'approval:apply')" data-test="new-request" @click="showForm=!showForm">{{ k(showForm?'close':'new') }}</AppButton>
  <RequestForm v-if="showForm&&has(user,'approval:apply')" :room-access="has(user,'resource:rooms:read')" :enabled="has(user,'approval:apply')" @created="created" @failure="failure" />
  <ApprovalFlow v-if="tasks&&has(user,'approval:flow:read')" :writable="has(user,'approval:flow:write')" @failure="failure" />
  <AppPanel v-if="allowedList">
   <template #actions><AppButton v-if="!selected" data-test="refresh" :disabled="loading" @click="refresh">{{ k('refresh') }}</AppButton></template>
   <p v-if="loading" role="status">{{ k('loading') }}</p>
   <p v-else-if="!list.items.length">{{ k('empty') }}</p>
   <ul class="request-list">
    <li v-for="row in list.items" :key="row.requestId">
     <span>{{ row.requestId }} · {{ formatTime(row.startsAt) }} · {{ k('status.'+row.status) }}</span>
     <AppButton :data-open="row.requestId" @click="select(row)">{{ k('view') }}</AppButton>
    </li>
   </ul>
   <div class="pagination">
    <AppButton data-test="previous-page" :disabled="loading||list.page===0" @click="changePage(-1)">{{ k('previous') }}</AppButton>
    <span>{{ t('modules.approval.page',{page:list.page+1,total:list.total}) }}</span>
    <AppButton data-test="next-page" :disabled="loading||(list.page+1)*list.size>=list.total" @click="changePage(1)">{{ k('next') }}</AppButton>
   </div>
  </AppPanel>
  <RequestDetail v-if="selected" :request="selected" :user="user" :now="currentTime" :busy="actionBusy" :cancel-unknown="cancelUnknown" @refresh="refresh" @decide="decide" @cancel="cancel" />
 </div>
</template>
<style scoped>
.approval-page {display:grid;gap:var(--space-4)}h1 {margin:0;font-size:24px}
.request-list {list-style:none;padding:0;margin:0}.request-list li {display:flex;flex-wrap:wrap;justify-content:space-between;align-items:center;gap:var(--space-3);padding-block:var(--space-3);border-bottom:1px solid var(--color-border)}
.request-list span {overflow-wrap:anywhere}.pagination {display:flex;flex-wrap:wrap;align-items:center;gap:var(--space-3);margin-top:var(--space-4)}
</style>
