<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppAlert, AppButton, AppPanel } from '@/shared/components'
import { approvalApi, type Candidate, type Configuration } from '../api/approvals'
import { errorKey } from '../model/presentation'
defineProps<{writable:boolean}>()
const emit=defineEmits<{failure:[error:unknown]}>(),{t}=useI18n({useScope:'global'}),k=(key:string)=>t('modules.approval.'+key)
const config=ref<Configuration>({version:0,approverIds:[]}),candidates=ref<Candidate[]>([]),busy=ref(false),error=ref(''),saved=ref(false)
let sequence=0
async function load(){
 const current=++sequence;busy.value=true;error.value='';saved.value=false
 try{const [flow,people]=await Promise.all([approvalApi.configuration(),approvalApi.candidates()]);if(current===sequence){config.value=flow;candidates.value=people}}
 catch(e){if(current===sequence){config.value={version:0,approverIds:[]};candidates.value=[];error.value='errors.'+errorKey(e);emit('failure',e)}}
 finally{if(current===sequence)busy.value=false}
}
function move(index:number,delta:number){const ids=[...config.value.approverIds],next=index+delta;if(next<0||next>=ids.length)return;[ids[index],ids[next]]=[ids[next]!,ids[index]!];config.value.approverIds=ids;saved.value=false}
async function save(){
 if(busy.value)return
 saved.value=false;error.value=''
 const ids=config.value.approverIds
 if(ids.length<2||ids.length>20||new Set(ids).size!==ids.length||ids.some(id=>!candidates.value.some(c=>c.userId===id))){error.value='flowValidation';return}
 busy.value=true
 try{config.value=await approvalApi.saveConfiguration(config.value);saved.value=true}
 catch(e){error.value='errors.'+errorKey(e);emit('failure',e)}
 finally{busy.value=false}
}
onMounted(load)
</script>
<template>
 <AppPanel :title="k('flow')">
  <p>{{ k('flowSnapshot') }}</p><p>{{ t('modules.approval.flowVersionLabel',{version:config.version}) }}</p>
  <AppAlert v-if="error" type="error" :title="k(error)" />
  <p v-if="saved" role="status">{{ k('flowSaved') }}</p>
  <p v-if="!busy&&candidates.length<2">{{ k('noCandidates') }}</p>
  <ol>
   <li v-for="(id,index) in config.approverIds" :key="id">
    {{ candidates.find(c=>c.userId===id)?.displayName ?? k('notCandidate') }} · {{ id }}
    <template v-if="writable">
     <AppButton :data-remove="index" size="small" :disabled="busy" @click="config.approverIds.splice(index,1);saved=false">{{ k('removeApprover') }}</AppButton>
     <AppButton size="small" :disabled="busy||index===0" @click="move(index,-1)">{{ k('moveUp') }}</AppButton>
     <AppButton size="small" :disabled="busy||index===config.approverIds.length-1" @click="move(index,1)">{{ k('moveDown') }}</AppButton>
    </template>
   </li>
  </ol>
  <div v-if="writable" class="candidate-list">
   <AppButton v-for="person in candidates" :key="person.userId" :data-add="person.userId" :disabled="busy||config.approverIds.includes(person.userId)||config.approverIds.length>=20" @click="config.approverIds.push(person.userId);saved=false">
    {{ k('addApprover') }} · {{ person.displayName }} · {{ person.userId }}
   </AppButton>
  </div>
  <div class="flow-actions">
   <AppButton v-if="writable" data-test="save-flow" variant="primary" :loading="busy" @click="save">{{ k('saveFlow') }}</AppButton>
   <AppButton data-test="reload-flow" :disabled="busy" @click="load">{{ k('reloadFlow') }}</AppButton>
  </div>
 </AppPanel>
</template>
<style scoped>
li {margin-bottom:var(--space-3);overflow-wrap:anywhere}.candidate-list,.flow-actions {display:flex;flex-wrap:wrap;gap:var(--space-2);margin-top:var(--space-4)}
</style>
