<script setup lang="ts">
import { reactive,ref,onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ApiError } from '@/shared/api'
import { identityApi,type SettingValues,type SettingsHistory } from '../api/identity'
import { useIdentityAction } from '../model/action'
import '../styles/forms.css'
const {t}=useI18n({useScope:'global'}),a=useIdentityAction(),{can,busy,errorText,saved}=a
const keys=['maxReservationHours','beforeStartReminderMinutes','afterStartReminderMinutes','checkinDeadlineMinutes','approvalTtlMinutes','repairSlaMinutes'] as const
const values=reactive<SettingValues>({maxReservationHours:4,beforeStartReminderMinutes:15,afterStartReminderMinutes:10,checkinDeadlineMinutes:15,approvalTtlMinutes:60,repairSlaMinutes:120}),version=ref(0),history=ref<SettingsHistory[]>([])
async function load(signal?:AbortSignal){if(!can('identity:settings:read'))return;const current=await identityApi.settings(signal);version.value=current.version;for(const key of keys)values[key]=current[key];history.value=await identityApi.history(signal)}
onMounted(()=>a.run(signal=>load(signal)))
async function save(){await a.run(async signal=>{if(!Number.isInteger(version.value)||version.value<1||keys.some(k=>!Number.isInteger(values[k])||values[k]<1)||values.afterStartReminderMinutes>=values.checkinDeadlineMinutes)throw new ApiError('invalid_request',{code:'INVALID_SYSTEM_SETTINGS'});const current=await identityApi.saveSettings(version.value,{...values},signal);version.value=current.version;for(const key of keys)values[key]=current[key];if(can('identity:settings:read'))history.value=await identityApi.history(signal)},'identity:settings:write',true)}
</script>
<template><section class="identity-page" v-if="can('identity:settings:read')||can('identity:settings:write')">
<h1 id="settings-title" tabindex="-1">{{t('modules.system.settings')}}</h1><p v-if="errorText" role="alert">{{errorText}}</p><p v-if="saved" data-result="success" role="status">{{t('modules.system.saved')}}</p>
<button v-if="can('identity:settings:read')" :disabled="busy" @click="a.run(signal=>load(signal))">{{t('modules.system.refresh')}}</button>
<p v-if="can('identity:settings:read')" id="settings-version">{{t('modules.system.version')}} {{version}}</p>
<form novalidate @submit.prevent="save"><label v-if="!can('identity:settings:read')" for="expected-settings-version">{{t('modules.system.version')}}<input id="expected-settings-version" v-model.number="version" type="number" min="1" required></label><label v-for="key in keys" :key="key" :for="key">{{t('modules.system.fields.'+key)}}<input :id="key" v-model.number="values[key]" type="number" min="1" step="1" required :disabled="busy||!can('identity:settings:write')"></label><button v-if="can('identity:settings:write')" id="save-settings" :disabled="busy">{{t('modules.system.save')}}</button></form>
<section v-if="can('identity:settings:read')"><h2>{{t('modules.system.history')}}</h2><div id="settings-history"><p v-if="!history.length">{{t('modules.system.empty')}}</p><article v-for="row in history" :key="row.id"><p>{{t('modules.system.version')}} {{row.version}} · {{row.actorId}} · <time :datetime="row.changedAt">{{new Date(row.changedAt).toLocaleString()}}</time></p><table><thead><tr><th>{{t('modules.system.name')}}</th><th>{{t('modules.system.before')}}</th><th>{{t('modules.system.after')}}</th></tr></thead><tbody><tr v-for="key in keys" :key="key"><td>{{t('modules.system.fields.'+key)}}</td><td>{{row.before[key]}}</td><td>{{row.after[key]}}</td></tr></tbody></table></article></div></section>
</section></template>
