import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'
import { expect } from 'vitest'
import { startBrowser, type BrowserPage } from '../../shared/browser'
export const uid='10000000-0000-4000-8000-000000000001', target='10000000-0000-4000-8000-000000000002', roleId='20000000-0000-4000-8000-000000000001'
export const grants=['identity:read','identity:roles:read','identity:roles:write','identity:users:read','identity:users:update','identity:settings:read','identity:settings:write','notification:read']
export const initialSettings=()=>({version:1,maxReservationHours:4,beforeStartReminderMinutes:15,afterStartReminderMinutes:10,checkinDeadlineMinutes:15,approvalTtlMinutes:60,repairSlaMinutes:120})
export const token='A'.repeat(43)
export async function ui(permissions=grants, anonymous=false) {
 const state:any={actor:{id:uid,loginName:'admin1',displayName:'Admin',role:'CUSTOM',permissions:[...permissions],authVersion:1,profileIncomplete:false},anonymous,requests:[],roles:[{id:roleId,code:'USER',name:'普通用户',enabled:true}],users:[{id:target,loginName:'user1',displayName:'User One',roleId,role:'USER',enabled:true,authVersion:7,departmentCode:null,canApplyRoom:false}],settings:initialSettings(),history:[],email:{email:null,verified:false,verifiedAt:null,pendingVerificationExpiresAt:null,deliveryStatus:null,deliveryErrorCode:null},emailFailure:'',delivery:'ACCEPTED',conflict:false,expireNext:false}
 const root=fileURLToPath(new URL('../../../',import.meta.url));
 const server=await createServer({configFile:false,root,plugins:[{name:'identity-http-fixture',configureServer(s){s.middlewares.use(async(req,res,next)=>{const path=req.url?.split('?')[0]??'';if(!path.startsWith('/api/'))return next();let raw='';for await(const part of req)raw+=part;let body:any={};try{if(raw)body=JSON.parse(raw)}catch{};const method=req.method??'GET';state.requests.push({method,path,body});const send=(status:number,data:any)=>{res.statusCode=status;res.setHeader('Content-Type','application/json');res.setHeader('X-Request-Id','fixture-trace');res.end(status===204?'':JSON.stringify(data))};const error=(status:number,code:string)=>send(status,{code,message:'fixture error',traceId:'fixture-trace'});const allowed=(permission:string)=>state.actor.permissions.includes(permission);
 if(path==='/api/auth/config')return send(200,{mode:'password',available:true,registrationOpen:true});
 if(path==='/api/auth/csrf')return send(200,{headerName:'X-XSRF-TOKEN',token:'isolated-ui-csrf'});
 if(path==='/api/auth/login'){state.anonymous=false;return send(200,state.actor)}
 if(path==='/api/auth/me')return state.anonymous?error(401,'AUTHENTICATION_REQUIRED'):send(200,state.actor);
 if(path==='/api/auth/logout'){state.anonymous=true;return send(204,null)}
 if(state.anonymous)return error(401,'AUTHENTICATION_REQUIRED');
 if(state.expireNext){state.expireNext=false;state.anonymous=true;return error(401,'AUTHENTICATION_REQUIRED')}
 const permit=path.startsWith('/api/admin/roles')||path==='/api/admin/permissions'?('identity:roles:'+(method==='GET'?'read':'write')):path.startsWith('/api/admin/users')?('identity:users:'+(method==='GET'?'read':'update')):path.startsWith('/api/admin/settings')?('identity:settings:'+(method==='GET'?'read':'write')):'identity:read';if(!allowed(permit))return error(403,'ACCESS_DENIED');
 if(method!=='GET'&&req.headers['x-xsrf-token']!=='isolated-ui-csrf')return error(403,'CSRF_REJECTED');
 if(path==='/api/admin/permissions')return send(200,[...grants,'resource:rooms:read','resource:rooms:write','approval:decide'].map(code=>({code,name:code,module:'M01'})));
 if(path==='/api/admin/roles'&&method==='GET')return send(200,state.roles);
 if(path==='/api/admin/roles'&&method==='POST'){const row={id:'20000000-0000-4000-8000-000000000002',...body,enabled:true};state.roles.push(row);return send(201,row)}
 if(path.endsWith('/permissions')&&method==='PUT'){state.roles.find((r:any)=>path.includes(r.id)).permissions=body.permissions;return send(204,null)}
 if(path.endsWith('/enabled')&&method==='PATCH'){state.roles.find((r:any)=>path.includes(r.id)).enabled=body.enabled;return send(204,null)}
 if(path==='/api/admin/users')return send(200,state.users);
 if(path.startsWith('/api/admin/users/')&&method==='PATCH'){const row=state.users.find((u:any)=>path.includes(u.id));if(body.expectedAuthVersion!==row.authVersion||state.conflict)return error(409,'VERSION_CONFLICT');if(path.endsWith('/eligibility')){row.departmentCode=body.departmentCode;row.canApplyRoom=body.canApplyRoom}else{row.roleId=body.roleId;row.enabled=body.enabled}row.authVersion++;return send(204,null)}
 if(path==='/api/admin/settings/history')return send(200,state.history);
 if(path==='/api/admin/settings'&&method==='GET')return send(200,state.settings);
 if(path==='/api/admin/settings'&&method==='PUT'){if(body.expectedVersion!==state.settings.version||state.conflict)return error(409,'VERSION_CONFLICT');if(Object.entries(body).some(([k,v])=>k!=='expectedVersion'&&(!Number.isInteger(v)||Number(v)<1))||body.afterStartReminderMinutes>=body.checkinDeadlineMinutes)return error(400,'INVALID_SYSTEM_SETTINGS');const before={...state.settings};state.settings={...body,version:before.version+1};delete state.settings.expectedVersion;state.history.unshift({id:'history1',actorId:uid,changedAt:'2026-11-16T01:00:00Z',version:state.settings.version,before,after:{...state.settings}});return send(200,state.settings)}
 if(path==='/api/account/email'&&method==='GET')return send(200,state.email);
 if(path==='/api/account/email'&&method==='PATCH'){if(Object.keys(body).join()!=='email'||!/^[^\s@]+@[^\s@]+$/.test(body.email))return error(400,'INVALID_EMAIL');state.email={...state.email,email:body.email,verified:false,verifiedAt:null,deliveryStatus:null};return send(200,state.email)}
 if(path==='/api/account/email/verification'){if(!state.email.email)return error(400,'EMAIL_REQUIRED');state.email.deliveryStatus=state.delivery;return send(state.delivery==='UNKNOWN'?202:200,{operationId:'op1',status:state.delivery,retryable:['FAILED','NOT_CONFIGURED'].includes(state.delivery),nextRetryAt:null,errorCode:state.delivery==='NOT_CONFIGURED'?'SMTP_NOT_CONFIGURED':null})}
 if(path==='/api/account/email/verify'){if(state.emailFailure)return error(400,state.emailFailure);if(body.token!==token)return error(400,'EMAIL_VERIFICATION_INVALID');state.email.verified=true;state.email.verifiedAt='2026-11-16T01:00:00Z';return send(200,state.email)}
 return error(404,'NOT_FOUND')})}},vue()],resolve:{alias:{'@':fileURLToPath(new URL('../../../src',import.meta.url))}},server:{host:'127.0.0.1',port:0,strictPort:false},logLevel:'error'});await server.listen();const address=server.httpServer!.address();if(!address||typeof address==='string')throw new Error('FIXTURE_ORIGIN');const origin='http://127.0.0.1:'+address.port;const page=await startBrowser();
 const immediateFill=page.fill.bind(page)
 page.fill=async(selector,value)=>{
  const key=JSON.stringify(selector)
  const ready=`(()=>{const nodes=document.querySelectorAll(${key});const e=nodes[0];return nodes.length===1&&(e instanceof HTMLInputElement||e instanceof HTMLTextAreaElement)&&!e.disabled&&!e.readOnly&&!e.matches(':disabled')})()`
  if(!await page.evaluate(ready))console.log('SCOPED_FILL_WAIT',selector,await page.evaluate(`(()=>{const nodes=document.querySelectorAll(${key});const e=nodes[0];return {count:nodes.length,tag:e?.tagName,disabled:e?.disabled,readOnly:e?.readOnly}})()`))
  await page.waitFor(ready,{timeoutMs:5000})
  await immediateFill(selector,value)
 }
console.log('IDENTITY_REAL_CHROME',page.metadata.version);
 return {state,page,origin,async open(path:string){try{await page.goto(origin+path)}catch(error){if(!(error instanceof Error)||!error.message.startsWith('BROWSER_WAIT_TIMEOUT'))throw error;console.log('FIXTURE_COLD_DOCUMENT_WAIT');await page.waitFor("document.readyState==='complete'",{timeoutMs:20000})}await page.waitFor("!!document.querySelector('h1')",{timeoutMs:20000})},async close(){await page.close();await server.close()}}
}
export async function shown(page:BrowserPage,selector:string){const end=Date.now()+3500;while(Date.now()<end){if(await page.evaluate(`!!document.querySelector(${JSON.stringify(selector)})`))return true;await new Promise(r=>setTimeout(r,30))}return false}
export async function requireUI(page:BrowserPage,selector:string){expect(await shown(page,selector),'identity UI meets approved scenario: '+selector).toBe(true)}
export async function select(page:BrowserPage,selector:string,value:string){await page.evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});e.value=${JSON.stringify(value)};e.dispatchEvent(new Event('change',{bubbles:true}))})()`)}
export async function checked(page:BrowserPage,selector:string){return page.evaluate<boolean>(`document.querySelector(${JSON.stringify(selector)})?.checked??false`)}
export async function waitRequest(state:any,path:string){const end=Date.now()+4000;while(Date.now()<end){const x=state.requests.filter((x:any)=>x.path===path&&x.method!=='GET');if(x.length)return x.at(-1);await new Promise(r=>setTimeout(r,20))}return null}
