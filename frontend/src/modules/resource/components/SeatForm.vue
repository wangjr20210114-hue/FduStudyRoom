<script setup lang="ts">
import { reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppButton } from '@/shared/components'
import type{Seat,SeatFields}from'../api'
const props=defineProps<{initial:Seat|null;saving:boolean}>();const emit=defineEmits<{save:[value:SeatFields];cancel:[]}>();const{t}=useI18n({useScope:'global'});const m=(key:string)=>t('modules.resource.'+key)
const form=reactive<SeatFields>({code:props.initial?.code??'',socketType:props.initial?.socketType??'FIXED',enabled:props.initial?.enabled??true})
</script>
<template><form data-testid="resource-seat-form" class="resource-form" @submit.prevent="emit('save',{...form,code:form.code.trim()})"><h2>{{m(initial?'actions.editSeat':'actions.addSeat')}}</h2><div class="resource-fields"><label>{{m('fields.seatCode')}}<input id="resource-seat-code" v-model="form.code" maxlength="64" required :disabled="saving"></label><label>{{m('fields.socket')}}<select id="resource-seat-socket" v-model="form.socketType" :disabled="saving"><option v-for="socket in ['NONE','FIXED','RAIL']" :key="socket" :value="socket">{{m('sockets.'+socket)}}</option></select></label></div><label><input v-model="form.enabled" type="checkbox" :disabled="saving">{{m('status.enabled')}}</label><div class="resource-actions"><AppButton data-testid="resource-seat-save" html-type="submit" variant="primary" :loading="saving">{{m('actions.save')}}</AppButton><AppButton :disabled="saving" @click="emit('cancel')">{{m('actions.cancel')}}</AppButton></div></form></template>
