<script setup lang="ts">
import { computed,onMounted,onUnmounted,ref,shallowRef,watch } from 'vue'
import { RouterLink,useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { AppAlert,AppButton,AppLoading,AppPanel } from '@/shared/components'
import { dailyCode,cancelled,resourceMessage,type DailyCode } from '../api'
import '../resource.css'
const route=useRoute();const{t}=useI18n({useScope:'global'});const m=(key:string)=>t('modules.resource.'+key);const value=ref<DailyCode|null>(null),loading=ref(false),error=shallowRef<unknown>(null);const roomId=computed(()=>typeof route.params.roomId==='string'?route.params.roomId:'');let controller:AbortController|undefined,timer:ReturnType<typeof setInterval>|undefined
function day(){const parts=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date());return ['year','month','day'].map(key=>parts.find(p=>p.type===key)?.value??'').join('-')}
let observedDay=day()
async function refresh(){controller?.abort();const active=new AbortController();controller=active;value.value=null;error.value=null;loading.value=true;try{const response=await dailyCode(roomId.value,active.signal);if(!active.signal.aborted)value.value=response}catch(failure){if(!active.signal.aborted&&!cancelled(failure))error.value=failure}finally{if(controller===active)loading.value=false}}
function focused(){if(document.visibilityState==='visible')void refresh()}
watch(roomId,refresh);onMounted(()=>{void refresh();timer=setInterval(()=>{const current=day();if(current!==observedDay){observedDay=current;void refresh()}},1000);window.addEventListener('focus',focused);document.addEventListener('visibilitychange',focused)});onUnmounted(()=>{controller?.abort();if(timer)clearInterval(timer);window.removeEventListener('focus',focused);document.removeEventListener('visibilitychange',focused)})
</script>
<template><div data-testid="resource-code-display" class="resource-page"><h1 tabindex="-1">{{m('code.title')}}</h1><AppPanel><div class="resource-code"><AppLoading v-if="loading" :label="m('status.loading')" /><AppAlert v-if="error" :title="m('code.unavailable')" :description="resourceMessage(error)" type="error" /><template v-if="value"><h2 data-testid="resource-code-room">{{value.roomName}}</h2><time data-testid="resource-code-date">{{value.date}}</time><strong data-testid="resource-code-value">{{value.code}}</strong><span>{{m('status.shanghai')}}</span></template><AppButton data-testid="resource-code-refresh" :loading="loading" @click="refresh">{{m('actions.refresh')}}</AppButton><RouterLink :to="{name:'rooms'}">{{m('actions.back')}}</RouterLink></div></AppPanel></div></template>
