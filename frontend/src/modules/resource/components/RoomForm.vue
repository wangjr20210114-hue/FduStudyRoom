<script setup lang="ts">
import { reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppButton } from '@/shared/components'
import type { Room,RoomFields } from '../api'
const props=defineProps<{initial:Room|null;saving:boolean}>();const emit=defineEmits<{save:[value:RoomFields];cancel:[]}>();const{t}=useI18n({useScope:'global'});const m=(key:string)=>t('modules.resource.'+key)
const form=reactive({code:props.initial?.code??'',name:props.initial?.name??'',campus:props.initial?.campus??'',building:props.initial?.building??'',capacity:props.initial?.capacity??1,openTime:props.initial?.openTime??'08:00',closeTime:props.initial?.closeTime??'22:00',departmentCode:props.initial?.departmentCode??'',enabled:props.initial?.enabled??true,bookingEnabled:props.initial?.bookingEnabled??true})
function submit(){if(!Number.isInteger(Number(form.capacity))||Number(form.capacity)<1)return;emit('save',{...form,code:form.code.trim(),name:form.name.trim(),campus:form.campus.trim(),building:form.building.trim(),capacity:Number(form.capacity),departmentCode:form.departmentCode.trim()||null})}
</script>
<template>
 <form data-testid="resource-room-form" class="resource-form" @submit.prevent="submit">
  <h2>{{m(initial?'actions.editRoom':'actions.addRoom')}}</h2>
  <div class="resource-fields">
   <label>{{m('fields.roomCode')}}<input id="resource-room-code" v-model="form.code" maxlength="64" required :disabled="saving"></label>
   <label>{{m('fields.roomName')}}<input id="resource-room-name" v-model="form.name" maxlength="128" required :disabled="saving"></label>
   <label>{{m('fields.campus')}}<input id="resource-room-campus" v-model="form.campus" maxlength="64" required :disabled="saving"></label>
   <label>{{m('fields.building')}}<input id="resource-room-building" v-model="form.building" maxlength="128" required :disabled="saving"></label>
   <label>{{m('fields.capacity')}}<input id="resource-room-capacity" v-model.number="form.capacity" type="number" min="1" max="2147483647" step="1" required :disabled="saving"></label>
   <label>{{m('fields.department')}}<input id="resource-room-department" v-model="form.departmentCode" maxlength="64" :disabled="saving"></label>
   <label>{{m('fields.opens')}}<input id="resource-room-open" v-model="form.openTime" type="time" step="1" required :disabled="saving"></label>
   <label>{{m('fields.closes')}}<input id="resource-room-close" v-model="form.closeTime" type="time" step="1" required :disabled="saving"></label>
  </div>
  <div class="resource-actions"><label><input v-model="form.enabled" type="checkbox" :disabled="saving">{{m('status.enabled')}}</label><label><input v-model="form.bookingEnabled" type="checkbox" :disabled="saving">{{m('status.bookingEnabled')}}</label></div>
  <div class="resource-actions"><AppButton data-testid="resource-room-save" html-type="submit" variant="primary" :loading="saving">{{m('actions.save')}}</AppButton><AppButton :disabled="saving" @click="emit('cancel')">{{m('actions.cancel')}}</AppButton></div>
 </form>
</template>
