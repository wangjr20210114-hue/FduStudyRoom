<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import ResourcePanel from './ResourcePanel.vue'
const { t, locale, te }=useI18n()
const accessCodeKey='fdu-buffer-access-code'
function readAccessCode(){try{return localStorage.getItem(accessCodeKey)??''}catch{return ''}}
function saveAccessCode(value:string){try{if(value)localStorage.setItem(accessCodeKey,value);else localStorage.removeItem(accessCodeKey)}catch{/* Browsers that block storage can still use this session. */}}
const token=ref(readAccessCode()), me=ref<{id:string;gitee?:string;admin:boolean}|null>(null)
const requirements=ref<any[]>([]), submissions=ref<any[]>([]), schedule=ref<any>(null)
const tab=ref<'requirements'|'submissions'>('submissions'), busy=ref(false), error=ref(''), patch=ref('')
const note=ref(''), confirmed=ref(false), status=ref<any>(null)
watch(locale,value=>{document.documentElement.lang=value;document.title=t('title')})
function label(value:string){return te(value)?t(value):value}
async function request(path:string,body?:unknown){
 const response=await fetch('/api'+path,{method:body===undefined?'GET':'POST',headers:{Authorization:'Bearer '+token.value,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),cache:'no-store'})
 if(response.status===401){clearSession();throw new Error('AUTHENTICATION_REQUIRED')}
 const data=await response.json();if(!response.ok)throw new Error(data.code??'error');return data
}
async function action(operation:()=>Promise<void>){busy.value=true;error.value='';try{await operation()}catch(failure){error.value=failure instanceof Error&&te(failure.message)?t(failure.message):t('error')}finally{busy.value=false}}
async function refresh(){
 [requirements.value,submissions.value,status.value]=await Promise.all([request('/requirements'),request('/submissions'),request('/status')])
 if(me.value?.admin)schedule.value=await request('/schedule')
}
async function login(){await action(async()=>{const presented=token.value.trim();token.value=presented;const member=await request('/me');token.value=presented;me.value=member;saveAccessCode(presented);await refresh()})}
function clearSession(){token.value='';saveAccessCode('');me.value=null;requirements.value=[];submissions.value=[];schedule.value=null;status.value=null;patch.value='';note.value='';confirmed.value=false}
function logout(){clearSession();error.value=''}
onMounted(()=>{if(token.value)void login()})
async function review(id:string,approved=true){await action(async()=>{await request('/submissions/'+id+'/review',{approved,note:note.value});confirmed.value=false;note.value='';await refresh()})}
async function verify(id:string){await action(async()=>{await request('/submissions/'+id+'/verify',{});await refresh()})}
async function showPatch(id:string){await action(async()=>{patch.value=(await request('/submissions/'+id+'/patch')).patch})}
async function sync(){await action(async()=>{await request('/sync',{});await refresh()})}
const timer=setInterval(()=>{if(me.value&&!busy.value)void action(refresh)},15000)
onBeforeUnmount(()=>clearInterval(timer))
</script>
<template>
 <div class="app">
  <header><h1>{{ t('title') }}</h1><select v-model="locale" aria-label="Language"><option value="zh-CN">中文</option><option value="en">English</option></select><template v-if="me"><span>{{ me.id }}</span><button @click="logout">{{ t('logout') }}</button></template></header>
  <p v-if="error" role="alert" class="error">{{ error }}</p>
  <form v-if="!me" class="login" @submit.prevent="login">
   <label>{{ t('token') }}<input v-model="token" type="password" autocomplete="current-password" required></label>
   <button :disabled="busy">{{ busy?t('loading'):t('login') }}</button>
  </form>
  <template v-else>
   <nav><button :aria-pressed="tab==='submissions'" @click="tab='submissions'">{{ t('submissions') }}</button><button :aria-pressed="tab==='requirements'" @click="tab='requirements'">{{ t('requirements') }}</button><button :disabled="busy" @click="action(refresh)">{{ t('refresh') }}</button></nav>
   <ResourcePanel v-if="status" :status="status" :admin="me.admin" />
   <p>{{ t('uploadHelp') }}</p>
   <section v-if="schedule"><h2>{{ t('schedule') }} · {{ schedule.enabled?t('enabled'):t('disabled') }}</h2><p>{{ t('threeDays') }}</p><p>{{ t('nextDue') }}: {{ schedule.next_due }} · {{ t('lastRun') }}: {{ schedule.last_run??'—' }}</p><button :disabled="busy||!schedule.enabled" @click="sync">{{ t('sync') }}</button></section>
   <section v-if="me.admin && tab==='submissions'" class="review-form"><label>{{ t('reviewNote') }}<textarea v-model="note" maxlength="2000"></textarea></label><label class="check"><input v-model="confirmed" type="checkbox">{{ t('confirmReview') }}</label></section>
   <div class="table-scroll"><table v-if="tab==='submissions'"><thead><tr><th>{{ t('id') }}</th><th>{{ t('member') }}</th><th>{{ t('module') }}</th><th>{{ t('summary') }}</th><th>{{ t('state') }}</th><th></th></tr></thead><tbody>
    <tr v-for="row in submissions" :key="row.id"><td>{{ row.id.slice(0,8) }}</td><td>{{ row.member_id }}</td><td>{{ row.module_id }}</td><td>{{ row.metadata.summary }}</td><td>{{ label(row.state) }}<p v-if="row.state==='VERIFIED'||row.state==='PUBLISHED'">{{ t('integrationPending') }}</p><a v-if="row.result.pull_request" :href="row.result.pull_request" target="_blank" rel="noopener">Gitee MR</a></td><td><button :disabled="busy" @click="showPatch(row.id)">{{ t('patch') }}</button><button v-if="me.admin&&row.state==='DRAFT'" :disabled="busy||!confirmed||note.length<4" @click="review(row.id)">{{ t('review') }}</button><button v-if="me.admin&&['DRAFT','REVIEWED','VERIFY_FAILED','CONFLICT'].includes(row.state)" :disabled="busy||note.length<4" @click="review(row.id,false)">{{ t('reject') }}</button><button v-if="me.admin&&['REVIEWED','VERIFY_FAILED'].includes(row.state)" :disabled="busy" @click="verify(row.id)">{{ t('verify') }}</button></td></tr>
    <tr v-if="!submissions.length"><td colspan="6">{{ t('empty') }}</td></tr>
   </tbody></table>
   <table v-else><thead><tr><th>{{ t('id') }}</th><th>{{ t('module') }}</th><th>{{ t('summary') }}</th><th>{{ t('state') }}</th></tr></thead><tbody><tr v-for="row in requirements" :key="row.id"><td>{{ row.id }}</td><td>{{ row.module }}</td><td>{{ row.title }}</td><td>{{ label(row.state) }}</td></tr><tr v-if="!requirements.length"><td colspan="4">{{ t('empty') }}</td></tr></tbody></table></div>
  </template>
  <section v-if="patch" class="diff"><button @click="patch=''">{{ t('close') }}</button><pre>{{ patch }}</pre></section>
 </div>
</template>
