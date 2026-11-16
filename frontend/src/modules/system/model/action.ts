import { computed,ref,onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ApiError,getApiErrorMessage } from '@/shared/api'
import { useAuthSession } from '@/modules/auth/model/auth-session'
export function useIdentityAction(){
 const session=useAuthSession(),router=useRouter(),{t}=useI18n({useScope:'global'}),busy=ref(false),error=ref<unknown>(null),saved=ref(false)
 const controller=new AbortController();onBeforeUnmount(()=>controller.abort())
 const can=(permission:string)=>session.user.value?.permissions.includes(permission)??false
 const errorText=computed(()=>{const e=error.value;if(e instanceof ApiError){const map:Record<string,string>={VERSION_CONFLICT:'conflict',INVALID_SYSTEM_SETTINGS:'invalidSettings',INVALID_EMAIL:'invalidEmail',EMAIL_REQUIRED:'missingEmail',EMAIL_VERIFICATION_INVALID:'invalidLink',EMAIL_VERIFICATION_EXPIRED:'expiredLink',EMAIL_ALREADY_VERIFIED:'alreadyVerified'};if(e.code&&map[e.code])return t('modules.system.errors.'+map[e.code])}return e?getApiErrorMessage(e):''})
 async function run(operation:(signal:AbortSignal)=>Promise<void>,permission?:string,writing=false){if(busy.value)return;busy.value=true;error.value=null;saved.value=false
  try{if(permission&&!can(permission))throw new ApiError('http',{status:403});await operation(controller.signal);if(writing){saved.value=true;await session.initialize({preserveIdentity:true});if(!session.user.value)await router.replace({name:'login'})}}
  catch(e){if(!controller.signal.aborted){error.value=e;if(e instanceof ApiError&&[401,403].includes(e.status??0)){await session.initialize({preserveIdentity:true});if(!session.user.value)await router.replace({name:'login'})}}}
  finally{busy.value=false}
 }
 return {session,can,busy,error,errorText,saved,run}
}
