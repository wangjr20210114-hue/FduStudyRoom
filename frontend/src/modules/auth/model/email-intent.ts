import { ref } from 'vue'
let pendingToken:string|undefined
export const hasEmailVerification=ref(false)
export const invalidEmailVerification=ref(false)
// This eager module runs before createLoginFlow and before the router can redirect.
function capture(){
 if(!window.location.hash.startsWith('#verify-email='))return
 pendingToken=undefined
 hasEmailVerification.value=false
 invalidEmailVerification.value=false
 const fragment=window.location.hash.slice('#verify-email='.length)
 window.history.replaceState(window.history.state,'',window.location.pathname+window.location.search)
 try{const candidate=decodeURIComponent(fragment);if(/^[A-Za-z0-9_-]{43}$/.test(candidate)){pendingToken=candidate;hasEmailVerification.value=true}else invalidEmailVerification.value=true}catch{invalidEmailVerification.value=true}
}
if(typeof window!=='undefined'){capture();window.addEventListener('hashchange',capture)}
export function emailVerificationToken(){return pendingToken}
export function clearEmailVerification(){pendingToken=undefined;hasEmailVerification.value=false;invalidEmailVerification.value=false}
