<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
interface ResourceStatus {
 sampledAt?: string
 memory: { known: boolean; totalBytes?: number; availableBytes?: number }
 memoryUsedPercent: number | null
 pressure: boolean
 thresholdPercent: number
 disk: { known: boolean; totalBytes?: number; availableBytes?: number }
 jvm: { usedBytes: number; maxBytes: number }
 queue: { pending_count: number; pending_bytes: number; ready_count: number }
 verificationEnabled: boolean
 ciCanStart: boolean
 syncEnabled: boolean
}
const props=defineProps<{status:ResourceStatus;admin?:boolean}>()
const { t }=useI18n()
const samples=ref<number[]>([])
watch(()=>props.status.sampledAt,()=>{
 const percent=props.status.memoryUsedPercent
 if(percent!==null && Number.isFinite(percent)){
  samples.value.push(percent)
  if(samples.value.length>60)samples.value.splice(0,samples.value.length-60)
 }
},{immediate:true})
const points=computed(()=>samples.value.map((n,i)=>`${i*300/59},${100-n}`).join(' '))
const diskPercent=computed(()=>{
 const d=props.status.disk
 return d.known && d.totalBytes ? 100*(d.totalBytes-(d.availableBytes??0))/d.totalBytes : null
})
function bytes(value:number|undefined){
 if(value===undefined)return t('unavailable')
 const units=['B','KiB','MiB','GiB','TiB'];let unit=0
 while(value>=1024 && unit<units.length-1){value/=1024;unit++}
 return `${value.toFixed(2)} ${units[unit]}`
}
</script>
<template>
 <section class="resources" aria-labelledby="resource-title">
  <h2 id="resource-title">{{ t('resources') }} <span v-if="status.pressure" class="pressure">{{ t('memoryPressure') }}</span></h2>
  <div class="resource-grid">
   <div>
    <h3>{{ t('serverMemory') }}</h3>
    <template v-if="status.memory.known && status.memoryUsedPercent!==null">
     <strong>{{ status.memoryUsedPercent.toFixed(1) }}%</strong>
     <progress :value="status.memoryUsedPercent" max="100" :aria-label="t('serverMemory')"></progress>
     <p>{{ t('available') }} {{ bytes(status.memory.availableBytes) }} · {{ t('threshold') }} {{ status.thresholdPercent }}%</p>
    </template>
    <p v-else>{{ t('unavailable') }}</p>
    <svg v-if="samples.length" viewBox="0 0 300 100" role="img" :aria-label="t('memoryHistory')" class="memory-chart">
     <line x1="0" x2="300" :y1="100-status.thresholdPercent" :y2="100-status.thresholdPercent" class="threshold-line" />
     <polyline :points="points" fill="none" stroke="currentColor" stroke-width="2" />
    </svg>
   </div>
   <div>
    <h3>{{ t('dataDisk') }}</h3>
    <template v-if="diskPercent!==null">
     <strong>{{ diskPercent.toFixed(1) }}%</strong>
     <progress :value="diskPercent" max="100" :aria-label="t('dataDisk')"></progress>
     <p>{{ t('available') }} {{ bytes(status.disk.availableBytes) }}</p>
    </template>
    <p v-else>{{ t('unavailable') }}</p>
    <p>{{ t('jvmHeap') }} {{ bytes(status.jvm.usedBytes) }} / {{ bytes(status.jvm.maxBytes) }}</p>
   </div>
   <div>
    <h3>{{ admin?t('teamQueue'):t('myQueue') }}</h3>
    <strong>{{ status.queue.pending_count }}</strong>
    <p>{{ t('readyCount') }} {{ status.queue.ready_count }}</p>
    <p>{{ t('queueBytes') }} {{ bytes(status.queue.pending_bytes) }}</p>
    <p>{{ status.verificationEnabled?(status.ciCanStart?t('ciReady'):t('ciPaused')):t('ciDisabled') }}</p>
    <p>{{ status.syncEnabled?t('syncEnabled'):t('syncDisabled') }}</p>
   </div>
  </div>
 </section>
</template>
