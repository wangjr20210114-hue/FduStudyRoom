<script setup lang="ts">
import { computed,onMounted,ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ApiError } from '@/shared/api'
import { emailApi,type EmailProfile } from '../api/email'
import { hasEmailVerification,invalidEmailVerification,emailVerificationToken,clearEmailVerification } from '../model/email-intent'
import { useIdentityAction } from '@/modules/system/model/action'
import '@/modules/system/styles/forms.css'
const {t}=useI18n({useScope:'global'}),a=useIdentityAction(),{can,busy,errorText,saved}=a
const profile=ref<EmailProfile|null>(null),address=ref(''),delivery=ref<string|null>(null)
const status=computed(()=>!profile.value?.email?'missing':profile.value.verified?'verified':'pending')
const deliveryText=computed(()=>{const map:Record<string,string>={ACCEPTED:'accepted',UNKNOWN:'unknown',FAILED:'failed',NOT_CONFIGURED:'notConfigured'};return t('modules.auth.email.'+(map[delivery.value??'']??(!profile.value?.email?'missing':'pending')))})
function apply(value:EmailProfile){profile.value=value;address.value=value.email??'';delivery.value=value.deliveryStatus}
onMounted(()=>a.run(async signal=>apply(await emailApi.profile(signal)),'identity:read'))
async function save(){await a.run(async signal=>{if(address.value.length>254||!/^[^\s@]+@[^\s@]+$/.test(address.value))throw new ApiError('invalid_request',{code:'INVALID_EMAIL'});apply(await emailApi.save(address.value,signal))},'identity:read',true)}
async function send(){await a.run(async signal=>{const receipt=await emailApi.requestVerification(signal);delivery.value=receipt.status},'identity:read')}
async function confirm(){await a.run(async signal=>{const token=emailVerificationToken();if(!token)throw new ApiError('invalid_request',{code:'EMAIL_VERIFICATION_INVALID'});apply(await emailApi.verify(token,signal));clearEmailVerification()},'identity:read')}
</script>
<template><section v-if="can('identity:read')" class="identity-page"><h2>{{t('modules.auth.email.title')}}</h2><p v-if="errorText" role="alert">{{errorText}}</p><p v-if="invalidEmailVerification" role="alert">{{t('modules.system.errors.invalidLink')}}</p><p v-if="saved" data-result="success" role="status">{{t('modules.system.saved')}}</p><p id="email-status" role="status">{{t('modules.auth.email.'+status)}}</p><form @submit.prevent="save"><label for="notification-email">{{t('modules.auth.email.address')}}<input id="notification-email" v-model="address" type="email" maxlength="254" required autocomplete="email" :disabled="busy"></label><button id="save-email" :disabled="busy">{{t('modules.system.save')}}</button></form><button v-if="profile?.email&&!profile.verified" id="send-verification" :disabled="busy" @click="send">{{t('modules.auth.email.send')}}</button><p id="email-delivery" role="status">{{deliveryText}}</p><button v-if="hasEmailVerification" id="confirm-email" :disabled="busy" @click="confirm">{{t('modules.auth.email.confirm')}}</button></section></template>
