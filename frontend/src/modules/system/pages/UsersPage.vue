<script setup lang="ts">
import { onMounted,ref,reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { identityApi,type Role,type Permission,type Account } from '../api/identity'
import { useIdentityAction } from '../model/action'
import '../styles/forms.css'
const {t}=useI18n({useScope:'global'}),a=useIdentityAction(),{can,busy,errorText,saved}=a
const roles=ref<Role[]>([]),permissions=ref<Permission[]>([]),users=ref<Account[]>([]),selected=ref<string[]>([]),permissionCodes=ref(''),editingRole=ref('')
const role=reactive({code:'',name:''}),account=reactive({id:'',roleId:'',enabled:true,authVersion:1,departmentCode:'',canApplyRoom:false})
async function load(signal?:AbortSignal){await Promise.all([can('identity:roles:read')?Promise.all([identityApi.roles(signal).then(x=>roles.value=x),identityApi.permissions(signal).then(x=>permissions.value=x)]):undefined,can('identity:users:read')?identityApi.users(signal).then(x=>users.value=x):undefined])}
onMounted(()=>a.run(signal=>load(signal)))
const chosen=()=>can('identity:roles:read')?[...selected.value]:[...new Set(permissionCodes.value.split(/[\s,]+/).filter(Boolean))]
function editRole(row:Role){editingRole.value=row.id;selected.value=[];permissionCodes.value=''}
async function createRole(){await a.run(async signal=>{await identityApi.createRole(role.code,role.name,chosen(),signal);await load(signal)},'identity:roles:write',true)}
async function replace(){await a.run(async signal=>{await identityApi.replacePermissions(editingRole.value,chosen(),signal);await load(signal)},'identity:roles:write',true)}
async function toggle(row:Role){await a.run(async signal=>{await identityApi.enableRole(row.id,!row.enabled,signal);await load(signal)},'identity:roles:write',true)}
function editUser(row:Account){Object.assign(account,{...row,departmentCode:row.departmentCode??''})}
async function saveAccess(){await a.run(async signal=>{await identityApi.access(account.id,account.roleId,account.enabled,account.authVersion,signal);account.authVersion++;await load(signal)},'identity:users:update',true)}
async function saveEligibility(){await a.run(async signal=>{await identityApi.eligibility(account.id,account.departmentCode||null,account.canApplyRoom,account.authVersion,signal);account.authVersion++;await load(signal)},'identity:users:update',true)}
</script>
<template>
<section class="identity-page">
 <h1 tabindex="-1">{{t('shell.navigation.users')}}</h1>
 <p v-if="errorText" role="alert">{{errorText}}</p><p v-if="saved" data-result="success" role="status">{{t('modules.system.saved')}}</p>
 <button :disabled="busy" @click="a.run(signal=>load(signal))">{{t('modules.system.refresh')}}</button>
 <section v-if="can('identity:roles:read')||can('identity:roles:write')">
  <h2 id="roles-title">{{t('modules.system.roles')}}</h2>
  <table v-if="can('identity:roles:read')" id="roles-table"><thead><tr><th>{{t('modules.system.code')}}</th><th>{{t('modules.system.name')}}</th><th>{{t('modules.system.status')}}</th><th v-if="can('identity:roles:write')">{{t('modules.system.actions')}}</th></tr></thead><tbody><tr v-for="row in roles" :key="row.id"><td>{{row.code}}</td><td>{{row.name}}</td><td>{{t('modules.system.'+(row.enabled?'enabled':'disabled'))}}</td><td v-if="can('identity:roles:write')"><button :data-edit-role="row.id" :disabled="busy" @click="editRole(row)">{{t('modules.system.replacePermissions')}}</button><button :data-toggle-role="row.id" :disabled="busy" @click="toggle(row)">{{t('modules.system.'+(row.enabled?'disable':'enable'))}}</button></td></tr><tr v-if="!roles.length"><td colspan="4">{{t('modules.system.empty')}}</td></tr></tbody></table>
  <form v-if="can('identity:roles:write')" @submit.prevent="createRole">
   <label for="role-code">{{t('modules.system.code')}}<input id="role-code" v-model="role.code" pattern="[A-Z][A-Z0-9_]{1,63}" maxlength="64" required :disabled="busy"></label>
   <label for="role-name">{{t('modules.system.name')}}<input id="role-name" v-model="role.name" maxlength="64" required :disabled="busy"></label>
   <fieldset :disabled="busy"><legend>{{t('modules.system.permissions')}}</legend><label v-for="p in permissions" :key="p.code" class="check"><input v-model="selected" type="checkbox" :value="p.code" :data-permission="p.code">{{p.code}}</label><label v-if="!can('identity:roles:read')" for="permission-codes">{{t('modules.system.permissionCodes')}}<textarea id="permission-codes" v-model="permissionCodes"></textarea></label></fieldset>
   <button id="create-role" :disabled="busy">{{t('modules.system.createRole')}}</button>
   <button v-if="editingRole" id="replace-permissions" type="button" :disabled="busy" @click="replace">{{t('modules.system.replacePermissions')}}</button>
  </form>
 </section>
 <section v-if="can('identity:users:read')||can('identity:users:update')">
  <h2>{{t('modules.system.accounts')}}</h2>
  <table v-if="can('identity:users:read')" id="users-table"><thead><tr><th>{{t('modules.system.account')}}</th><th>{{t('modules.system.name')}}</th><th>{{t('modules.system.role')}}</th><th>{{t('modules.system.status')}}</th><th>{{t('modules.system.version')}}</th><th v-if="can('identity:users:update')">{{t('modules.system.actions')}}</th></tr></thead><tbody><tr v-for="row in users" :key="row.id"><td>{{row.loginName}}</td><td>{{row.displayName}}</td><td>{{row.role}}</td><td>{{t('modules.system.'+(row.enabled?'enabled':'disabled'))}}</td><td>{{row.authVersion}}</td><td v-if="can('identity:users:update')"><button :data-edit-user="row.id" :disabled="busy" @click="editUser(row)">{{t('modules.system.edit')}}</button></td></tr><tr v-if="!users.length"><td colspan="6">{{t('modules.system.empty')}}</td></tr></tbody></table>
  <form v-if="can('identity:users:update')&&(account.id||!can('identity:users:read'))" @submit.prevent="saveAccess">
   <label v-if="!can('identity:users:read')">{{t('modules.system.accountId')}}<input v-model="account.id" required></label>
   <label>{{t('modules.system.role')}}<select v-if="can('identity:roles:read')" v-model="account.roleId" :disabled="busy"><option v-for="r in roles" :key="r.id" :value="r.id" :disabled="!r.enabled">{{r.name}}</option></select><input v-else v-model="account.roleId" required :disabled="busy"></label>
   <label class="check"><input id="user-enabled" v-model="account.enabled" type="checkbox" :disabled="busy">{{t('modules.system.enabled')}}</label>
   <label>{{t('modules.system.version')}}<input v-model.number="account.authVersion" type="number" min="1" required :readonly="can('identity:users:read')" :disabled="busy"></label>
   <button id="save-access" :disabled="busy">{{t('modules.system.saveAccess')}}</button>
   <label for="department-code">{{t('modules.system.department')}}<input id="department-code" v-model="account.departmentCode" maxlength="64" :disabled="busy"></label>
   <label class="check"><input id="can-apply-room" v-model="account.canApplyRoom" type="checkbox" :disabled="busy">{{t('modules.system.canApplyRoom')}}</label>
   <button id="save-eligibility" type="button" :disabled="busy" @click="saveEligibility">{{t('modules.system.saveEligibility')}}</button>
  </form>
 </section>
</section>
</template>
