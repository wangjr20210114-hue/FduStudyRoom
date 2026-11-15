/// <reference types="node" />
import { createServer } from 'node:http'
import { readFile, stat } from 'node:fs/promises'
import { resolve, extname, join } from 'node:path'
import { build } from 'vite'
import { expect } from 'vitest'
import { startBrowser, type BrowserPage } from '../../shared/browser'
export const applicant='10000000-0000-4000-8000-000000000001', approver1='10000000-0000-4000-8000-000000000002', approver2='10000000-0000-4000-8000-000000000003', approver3='10000000-0000-4000-8000-000000000004', roomId='20000000-0000-4000-8000-000000000001', requestId='30000000-0000-4000-8000-000000000001',otherId='30000000-0000-4000-8000-000000000002'
export const allPermissions=['identity:read','approval:read','approval:apply','approval:decide','approval:flow:read','approval:flow:write','approval:admin-cancel','resource:rooms:read']
export const user=(id=applicant,permissions=allPermissions,role='CUSTOM')=>({id,loginName:'fixture-user',displayName:'Fixture user',role,permissions:[...permissions],authVersion:1,profileIncomplete:false})
export function request(overrides:Record<string,any>={}):any {
 return {requestId,applicantId:applicant,roomId,startsAt:'2030-06-03T10:00:00+08:00',endsAt:'2030-06-03T12:00:00+08:00',purpose:'课程讨论',attendeeCount:2,status:'PENDING',version:1,flowVersion:1,expiresAt:'2030-06-03T09:00:00+08:00',resourceState:'UNCONFIRMED',resourceVersion:0,resourceAbnormal:false,cancelReason:null,
 tasks:[{id:'40000000-0000-4000-8000-000000000001',stepNo:1,approverId:approver1,status:'PENDING',dueAt:'2030-06-03T09:00:00+08:00',actedAt:null,decidedBy:null,comment:null},{id:'40000000-0000-4000-8000-000000000002',stepNo:2,approverId:approver2,status:'WAITING',dueAt:'2030-06-03T09:00:00+08:00',actedAt:null,decidedBy:null,comment:null}],
 history:[{actorId:applicant,status:'PENDING',reason:null,occurredAt:'2030-06-03T08:00:00+08:00',version:1}],deliveries:[],...overrides}
}
export interface Captured {method:string;path:string;body:any;csrf:string|undefined}
let buildPromise:Promise<string>|undefined
async function productBuild(){buildPromise??=(async()=>{const outDir='/tmp/campus-reports/approval-product';await build({root:resolve('.'),logLevel:'silent',build:{outDir,emptyOutDir:true}});return outDir})();return buildPromise}
export async function fixture(){
 const dist=await productBuild()
 const state={user:user(),eligible:true,rows:[request()],configuration:{version:1,approverIds:[approver1,approver2]},candidates:[{userId:approver1,displayName:'Admin One'},{userId:approver2,displayName:'Admin Two'},{userId:approver3,displayName:'Admin Three'}],calls:[] as Captured[],unexpected:[] as string[],failDetail:0,unknownSubmit:false,unknownCancel:false,decisionConflict:false,delayedDetail:false,requestsByOp:new Map<string,any>(),cancelsByOp:new Map<string,any>()}
 const reply=(res:any,status:number,value:any)=>{res.writeHead(status,{'Content-Type':'application/json','Cache-Control':'no-store'});res.end(JSON.stringify(value))}
 const deny=(res:any,status=403,code='FORBIDDEN')=>reply(res,status,{code,traceId:'fixture-trace'})
 const server=createServer(async(req,res)=>{
  try{
   const url=new URL(req.url!,'http://127.0.0.1'),path=url.pathname,method=req.method!
   if(path.startsWith('/api/')){
    let raw='';for await(const chunk of req)raw+=chunk
    const body=raw?JSON.parse(raw):null
    state.calls.push({method,path:path+url.search,body,csrf:req.headers['x-xsrf-token'] as string|undefined})
    if(path==='/api/auth/config')return reply(res,200,{mode:'password',available:true,registrationOpen:true})
    if(path==='/api/auth/me')return state.failDetail===401?deny(res,401,'UNAUTHENTICATED'):reply(res,200,state.user)
    if(path==='/api/auth/csrf')return reply(res,200,{headerName:'X-XSRF-TOKEN',token:'fixture-csrf'})
    if(method!=='GET'&&req.headers['x-xsrf-token']!=='fixture-csrf')return deny(res,403,'CSRF_INVALID')
    const has=(p:string)=>state.user.permissions.includes(p)
    if(path==='/api/resources/rooms')return has('resource:rooms:read')?reply(res,200,[{id:roomId,code:'R101',name:'R101',campus:'邯郸',building:'教学楼A',capacity:2,openTime:'08:00:00',closeTime:'22:00:00',departmentCode:null,bookingEnabled:true,enabled:true,version:1}]):deny(res)
    if(path==='/api/approvals/candidates')return has('approval:flow:read')?reply(res,200,state.candidates):deny(res)
    if(path==='/api/approvals/configuration'){
     if(method==='GET')return has('approval:flow:read')?reply(res,200,state.configuration):deny(res)
     if(!has('approval:flow:write'))return deny(res)
     if(body.expectedVersion!==state.configuration.version)return deny(res,409,'STALE_VERSION')
     if(!Array.isArray(body.approverIds)||body.approverIds.length<2||new Set(body.approverIds).size!==body.approverIds.length||body.approverIds.some((id:string)=>!state.candidates.some(c=>c.userId===id)))return deny(res,400,'INVALID_APPROVERS')
     state.configuration={version:state.configuration.version+1,approverIds:body.approverIds};return reply(res,200,state.configuration)
    }
    if(path==='/api/approvals/requests'&&method==='POST'){
     if(!has('approval:apply')||!state.eligible)return deny(res,403,'ROOM_APPLICATION_NOT_ALLOWED')
     if(Object.keys(body).some(k=>!['operationId','roomId','startsAt','endsAt','purpose','attendeeCount'].includes(k)))return deny(res,400,'INVALID_INPUT')
     if(state.requestsByOp.has(body.operationId))return reply(res,200,state.requestsByOp.get(body.operationId))
     if(body.roomId!==roomId||!body.purpose?.trim()||new Date(body.endsAt)<=new Date(body.startsAt)||body.attendeeCount<1||body.attendeeCount>2)return deny(res,400,'INVALID_INPUT')
     const created=request({...body,requestId:otherId,applicantId:state.user.id,flowVersion:state.configuration.version,tasks:request().tasks.map((t:any,i:number)=>({...t,approverId:state.configuration.approverIds[i]}))})
     state.requestsByOp.set(body.operationId,created);state.rows.push(created)
     if(state.unknownSubmit){state.unknownSubmit=false;return deny(res,503,'SERVICE_UNAVAILABLE')}
     return reply(res,201,created)
    }
    if((path==='/api/approvals/requests'||path==='/api/approvals/tasks')&&method==='GET'){
     const tasks=path.endsWith('/tasks')
     if(!has(tasks?'approval:decide':'approval:read'))return deny(res)
     const rows=state.rows.filter(r=>tasks?r.tasks.some((t:any)=>t.approverId===state.user.id):r.applicantId===state.user.id)
     const page=Number(url.searchParams.get('page')??0),size=Number(url.searchParams.get('size')??20)
     return reply(res,200,{items:rows.slice(page*size,(page+1)*size),page,size,total:rows.length})
    }
    const match=path.match(/^\/api\/approvals\/requests\/([0-9a-f-]+)(?:\/(.*))?$/)
    if(match){
     const row=state.rows.find(r=>r.requestId===match[1]);if(!row)return deny(res,404,'NOT_FOUND')
     if(!match[2]){
      if(state.failDetail)return deny(res,state.failDetail,state.failDetail===401?'UNAUTHENTICATED':'FORBIDDEN')
      if(!has('approval:read')||!(row.applicantId===state.user.id||row.tasks.some((t:any)=>t.approverId===state.user.id)))return deny(res)
      const snapshot=structuredClone(row)
      if(state.delayedDetail){state.delayedDetail=false;setTimeout(()=>reply(res,200,snapshot),350);return}
      return reply(res,200,row)
     }
     if(match[2]==='cancel'){
      if(state.cancelsByOp.has(body.operationId))return reply(res,200,state.cancelsByOp.get(body.operationId))
      if(row.version!==body.expectedVersion)return deny(res,409,'STALE_VERSION')
      const now=Date.now(),before=now<new Date(row.startsAt).getTime(),ended=now>=new Date(row.endsAt).getTime()
      if(ended||!((before&&row.applicantId===state.user.id)||has('approval:admin-cancel')))return deny(res)
      if((!before||row.applicantId!==state.user.id)&&!body.reason?.trim())return deny(res,400,'REASON_REQUIRED')
      Object.assign(row,{status:'CANCELLED',resourceState:'RELEASE_PENDING',cancelReason:body.reason||'APPLICANT_CANCELLED',version:row.version+1})
      state.cancelsByOp.set(body.operationId,structuredClone(row))
      if(state.unknownCancel){state.unknownCancel=false;return deny(res,503,'SERVICE_UNAVAILABLE')}
      return reply(res,200,row)
     }
     const taskMatch=match[2].match(/^tasks\/([0-9a-f-]+)\/decision$/)
     if(taskMatch){
      const t=row.tasks.find((t:any)=>t.id===taskMatch[1])
      if(state.decisionConflict){state.decisionConflict=false;row.version++;return deny(res,409,'STALE_VERSION')}
      if(row.version!==body.expectedVersion)return deny(res,409,'STALE_VERSION')
      if(!has('approval:decide')||!t||t.approverId!==state.user.id||t.status!=='PENDING'||row.applicantId===state.user.id||row.tasks.some((old:any)=>old.decidedBy===state.user.id))return deny(res)
      if(body.decision==='REJECT'&&!body.comment?.trim())return deny(res,400,'REASON_REQUIRED')
      Object.assign(t,{status:body.decision==='REJECT'?'REJECTED':'APPROVED',actedAt:new Date().toISOString(),decidedBy:state.user.id,comment:body.comment||null});row.version++
      if(body.decision==='REJECT')row.status='REJECTED'
      else{const next=row.tasks.find((n:any)=>n.status==='WAITING');if(next)next.status='PENDING';else{row.status='RESOURCE_PENDING';row.resourceState='PENDING'}}
      row.history.push({actorId:state.user.id,status:row.status,reason:body.comment||null,occurredAt:new Date().toISOString(),version:row.version})
      return reply(res,200,Object.fromEntries(['requestId','status','version','flowVersion','expiresAt','tasks'].map(k=>[k,row[k]])))
     }
    }
    state.unexpected.push(method+' '+path);return deny(res,500,'UNEXPECTED_FIXTURE_ROUTE')
   }
   let file=join(dist,decodeURIComponent(path))
   try{if(!(await stat(file)).isFile())file=join(dist,'index.html')}catch{file=join(dist,'index.html')}
   const content=await readFile(file)
   res.writeHead(200,{'Content-Type':({'.html':'text/html','.js':'application/javascript','.css':'text/css','.svg':'image/svg+xml'} as Record<string,string>)[extname(file)]??'application/octet-stream'});res.end(content)
  }catch(error){state.unexpected.push(String(error));reply(res,500,{code:'FIXTURE_ERROR'})}
 })
 await new Promise<void>(done=>server.listen(0,'127.0.0.1',done))
 const origin='http://127.0.0.1:'+((server.address() as any).port),pages:BrowserPage[]=[]
 async function open(path='/room-requests'){
  for(const old of pages.splice(0))await old.close();const page=await startBrowser();pages.push(page);console.info("REAL_CHROME "+page.metadata.version);await page.goto(origin+path)
  await page.waitFor("document.querySelector('.shell-page h1')",{timeoutMs:15000})
  expect(await page.evaluate("!!document.querySelector('[data-approval-root]')")).toBe(true)
  return page
 }
 async function close(){for(const p of pages){const shot='/tmp/campus-reports/approval-'+crypto.randomUUID()+'.png';await p.screenshot(shot);console.info('BROWSER_SCREENSHOT '+shot);await p.close();}server.closeAllConnections();await new Promise<void>(r=>server.close(()=>r()));expect(state.unexpected).toEqual([])}
 return {state,open,close,origin}
}
export const count=(p:BrowserPage,s:string)=>p.evaluate<number>('document.querySelectorAll('+JSON.stringify(s)+').length')
export const text=(p:BrowserPage)=>p.evaluate<string>(()=>document.body.innerText)
export async function click(p:BrowserPage,s:string){await p.waitFor('!!document.querySelector('+JSON.stringify(s)+')');await p.click(s)}
export async function waitText(p:BrowserPage,s:string){await p.waitFor('document.body.innerText.includes('+JSON.stringify(s)+')')}
export async function fillApplication(p:BrowserPage){
 await click(p,'[data-test=new-request]');await click(p,'[data-test=room-option]')
 await p.fill('[data-test=starts-at]','2030-06-03T10:00');await p.fill('[data-test=ends-at]','2030-06-03T12:00')
 await p.fill('[data-test=purpose]','课程讨论');await p.fill('[data-test=attendee-count]','2')
}
export const posts=(f:Awaited<ReturnType<typeof fixture>>,suffix:string)=>f.state.calls.filter(c=>c.method==='POST'&&c.path.endsWith(suffix))
