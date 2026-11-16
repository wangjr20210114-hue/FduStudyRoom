/// <reference types="node" />
import { createServer } from 'node:http'
import { createHash } from 'node:crypto'
import { appendFile, readFile, readdir, stat } from 'node:fs/promises'
import { resolve, extname, join } from 'node:path'
import { build } from 'vite'
import { expect } from 'vitest'
import { startBrowser, type BrowserPage } from '../../shared/browser'
export const reporter='11000000-0000-4000-8000-000000000001',worker1='11000000-0000-4000-8000-000000000002',worker2='11000000-0000-4000-8000-000000000003',admin1='11000000-0000-4000-8000-000000000004',admin2='11000000-0000-4000-8000-000000000005'
export const room1='22000000-0000-4000-8000-000000000001',room2='22000000-0000-4000-8000-000000000002',seat1='23000000-0000-4000-8000-000000000001',seat2='23000000-0000-4000-8000-000000000002',order1='33000000-0000-4000-8000-000000000001',order2='33000000-0000-4000-8000-000000000002'
export const permissions=['identity:read','resource:rooms:read','repair:report','repair:history:read','repair:maintain','repair:assign','repair:accept','repair:evaluate','repair:admin-cancel']
export const user=(id=reporter,allowed=permissions,role='CUSTOM')=>({id,loginName:'fixture-user',displayName:'Fixture user',role,permissions:[...allowed],authVersion:1,profileIncomplete:false})
export function order(value:Record<string,any>={}):any {
 return {id:order1,reporterId:reporter,roomId:room1,seatId:seat1,description:'插座损坏',technicianId:null,status:'SUBMITTED',dueAt:null,escalationLevel:0,repairReport:null,acceptedBy:null,acceptedAt:null,acceptanceComment:null,rating:null,evaluation:null,evaluatedAt:null,version:1,managed:true,assignedAt:null,slaMinutes:null,desiredVersion:0,appliedVersion:0,desiredActive:false,appliedActive:false,restoreTerminal:null,resourceError:null,cancelReason:null,cancelReviewedBy:null,cancelReviewedAt:null,safetyInspectedBy:null,safetyInspectedAt:null,createdAt:'2030-06-03T08:00:00+08:00',updatedAt:'2030-06-03T08:00:00+08:00',...value}
}
export function event(value:Record<string,any>={}):any{return {id:crypto.randomUUID(),actorId:reporter,eventType:'SUBMIT',fromStatus:null,toStatus:'SUBMITTED',assignedTo:null,note:null,createdAt:'2030-06-03T08:00:00+08:00',orderVersion:1,rating:null,...value}}
export const active=(value:Record<string,any>={})=>order({status:'IN_PROGRESS',technicianId:worker1,version:4,assignedAt:'2030-06-03T08:00:00+08:00',dueAt:'2030-06-03T10:00:00+08:00',slaMinutes:120,desiredVersion:3,appliedVersion:3,desiredActive:true,appliedActive:true,...value})
export const completed=(value:Record<string,any>={})=>active({status:'COMPLETED',acceptedBy:admin2,acceptedAt:'2030-06-03T09:00:00+08:00',acceptanceComment:'安全可用',desiredActive:false,appliedActive:false,desiredVersion:7,appliedVersion:7,version:8,...value})
export interface Call {method:string;path:string;body:any;csrf:string|undefined;cookie:boolean;locale:string|undefined;serviceKey:string|undefined}
let compilation:Promise<{dist:string;sourceHash:string}>|undefined
async function digestModule(){
 const hash=createHash('sha256')
 async function walk(folder:string){for(const name of(await readdir(folder)).sort()){const p=join(folder,name);if((await stat(p)).isDirectory())await walk(p);else{hash.update(p);hash.update(await readFile(p))}}}
 await walk(resolve('src/modules/repair'));return hash.digest('hex')
}
async function compiled(){
 compilation??=(async()=>{const dist='/tmp/campus-reports/repair-product-'+process.pid,sourceHash=await digestModule();await build({root:resolve('.'),logLevel:'silent',build:{outDir:dist,emptyOutDir:true}});return {dist,sourceHash}})()
 return compilation
}
export async function fixture(){
 const {dist,sourceHash}=await compiled()
 const state={
  user:user(),rows:[order()],histories:new Map<string,any[]>(),candidates:[{userId:worker1,displayName:'Worker One'},{userId:worker2,displayName:'Worker Two'}],
  rooms:[{id:room1,code:'R101',name:'R101',enabled:true,bookingEnabled:true,capacity:2,openTime:'08:00:00',closeTime:'22:00:00'},{id:room2,code:'R102',name:'R102',enabled:false,bookingEnabled:false,capacity:2,openTime:'08:00:00',closeTime:'22:00:00'}],
  calls:[] as Call[],unexpected:[] as string[],commands:new Map<string,{fingerprint:string;response:any}>(),
  failDetail:0,failHistory:false,unknownAction:'',failBeforeCommit:'',conflictAction:'',contradictAcceptance:false,delayNextDetail:false,invalidTechnician:false,badSeatList:false,
 }
 const history=(id:string)=>{if(!state.histories.has(id))state.histories.set(id,[event()]);return state.histories.get(id)!}
 const has=(p:string)=>state.user.permissions.includes(p)
 const independent=(r:any)=>r.technicianId!==state.user.id&&!history(r.id).some(e=>['START','FINISH'].includes(e.eventType)&&e.actorId===state.user.id)
 const visible=(r:any)=>r.reporterId===state.user.id||r.technicianId===state.user.id||r.acceptedBy===state.user.id||has('repair:assign')||(has('repair:accept')&&['PENDING_ACCEPTANCE','CANCEL_SAFETY_PENDING'].includes(r.status))||(has('repair:admin-cancel')&&r.status==='CANCEL_REVIEW_PENDING')
 const reply=(res:any,status:number,value?:any)=>{res.writeHead(status,{'Content-Type':value===undefined?'text/plain':'application/json','Cache-Control':'no-store'});res.end(value===undefined?'':JSON.stringify(value))}
 const deny=(res:any,status=403,code='ACCESS_DENIED')=>reply(res,status,{code,traceId:'fixture-trace'})
 function projection(r:any){return r.managed===false?Object.fromEntries(['id','reporterId','technicianId','status','acceptedBy','acceptedAt','acceptanceComment','rating','evaluation','evaluatedAt','version'].map(k=>[k,r[k]])):structuredClone(r)}
 function logicalTime(r:any){return new Date(Math.max(Date.parse('2030-06-03T08:00:00+08:00'),...history(r.id).map(e=>Date.parse(e.createdAt)),r.acceptedAt?Date.parse(r.acceptedAt):0)+60000).toISOString()}
 function add(r:any,type:string,from:string,note:string|null=null,rating:number|null=null){history(r.id).push(event({actorId:state.user.id,eventType:type,fromStatus:from,toStatus:r.status,assignedTo:r.technicianId,note,orderVersion:r.version,createdAt:logicalTime(r),rating}))}
 function ack(id=order1,activeFlag=true){
  const r=state.rows.find(r=>r.id===id);if(!r)throw new Error('fixture unknown order')
  const from=r.status;r.appliedVersion=r.desiredVersion;r.appliedActive=activeFlag;r.resourceError=null
  if(activeFlag&&r.status==='RESTRICTION_PENDING')r.status='IN_PROGRESS'
  if(!activeFlag&&r.status==='RESTORATION_PENDING')r.status=r.restoreTerminal
  if(from!==r.status){r.version++;history(r.id).push(event({actorId:null,eventType:'RESOURCE_ACK',fromStatus:from,toStatus:r.status,assignedTo:r.technicianId,orderVersion:r.version,createdAt:logicalTime(r)}))}
 }
 const server=createServer(async(req,res)=>{
  try{
   const url=new URL(req.url!,'http://127.0.0.1'),path=url.pathname,method=req.method!
   if(path.startsWith('/api/')){
    let raw='';for await(const c of req)raw+=c
    const body=raw?JSON.parse(raw):null,cookie=!!req.headers.cookie?.includes('fixture_session=active')
    state.calls.push({method,path:path+url.search,body,csrf:req.headers['x-xsrf-token'] as string|undefined,cookie,locale:req.headers['accept-language'],serviceKey:req.headers['x-service-key'] as string|undefined})
    if(path==='/api/auth/config')return reply(res,200,{mode:'password',available:true,registrationOpen:true})
    if(path==='/api/auth/me')return state.failDetail===401||!cookie?deny(res,401,'UNAUTHENTICATED'):reply(res,200,state.user)
    if(path==='/api/auth/csrf')return reply(res,200,{headerName:'X-XSRF-TOKEN',token:'fixture-csrf'})
    if(!cookie)return deny(res,401,'UNAUTHENTICATED')
    if(method!=='GET'&&(req.headers['x-xsrf-token']!=='fixture-csrf'||!req.headers.cookie?.includes('fixture_csrf=fixture-csrf')))return deny(res,403,'CSRF_INVALID')
    if(path==='/api/resources/rooms')return has('resource:rooms:read')?reply(res,200,state.rooms):deny(res)
    const seatMatch=path.match(/^\/api\/resources\/rooms\/([0-9a-f-]+)\/seats$/)
    if(seatMatch){if(!has('resource:rooms:read'))return deny(res);return reply(res,200,[{id:seatMatch[1]===room1?seat1:seat2,seatId:seatMatch[1]===room1?seat1:seat2,roomId:state.badSeatList?room2:seatMatch[1],code:seatMatch[1]===room1?'A01':'B01',socketType:'FIXED',hasSocket:true,enabled:true,version:1}])}
    if(path==='/api/repairs/candidates')return has('repair:assign')?reply(res,200,state.candidates):deny(res)
    if(path==='/api/repairs'&&method==='GET')return has('repair:history:read')?reply(res,200,state.rows.filter(visible)):deny(res)
    let row:any,action=''
    if(path==='/api/repairs'&&method==='POST')action='create'
    const match=path.match(/^\/api\/repairs\/([0-9a-f-]+)(?:\/([^/]+))?$/)
    if(match){row=state.rows.find(r=>r.id===match[1]);if(!row)return deny(res,404,'REPAIR_NOT_FOUND')
     if(method==='GET'){
      if(state.failDetail)return deny(res,state.failDetail,state.failDetail===401?'UNAUTHENTICATED':'REPAIR_NOT_PARTICIPANT')
      if(!has('repair:history:read')||!visible(row))return deny(res,403,'REPAIR_NOT_PARTICIPANT')
      if(match[2]==='history')return state.failHistory?deny(res,503,'REPAIR_DEPENDENCY_UNAVAILABLE'):reply(res,200,history(row.id))
      if(!match[2]){const snap=projection(row);if(state.delayNextDetail){state.delayNextDetail=false;setTimeout(()=>reply(res,200,snap),300);return}return reply(res,200,snap)}
     }
     action=match[2]??''
    }
    if(action&&method==='POST'){
     const required=({create:'repair:report',assignment:'repair:assign',start:'repair:maintain',finish:'repair:maintain',acceptance:'repair:accept','cancel-review':'repair:admin-cancel','cancel-inspection':'repair:accept',cancel:'repair:history:read',evaluation:'repair:evaluate'} as Record<string,string>)[action]
     if(!required||!has(required))return deny(res)
     if(action==='cancel'&&row.reporterId!==state.user.id&&!has('repair:admin-cancel'))return deny(res)
     if(state.failBeforeCommit===action){state.failBeforeCommit='';return deny(res,503,'REPAIR_DEPENDENCY_UNAVAILABLE')}
     if(action==='evaluation'){
      if(row.status!=='COMPLETED')return deny(res,409,'REPAIR_NOT_COMPLETED')
      if(row.acceptedBy!==state.user.id||!independent(row))return deny(res,403,'REPAIR_NOT_ACCEPTOR')
      if(Object.keys(body).sort().join(',')!=='evaluation,rating,version'||!Number.isInteger(body.rating)||body.rating<1||body.rating>5||typeof body.evaluation!=='string'||body.evaluation.length>500||(body.evaluation!==''&&!body.evaluation.trim()))return deny(res,400,'REPAIR_INVALID_RATING')
      if(row.version!==body.version||row.evaluatedAt)return deny(res,409,'REPAIR_VERSION_CONFLICT')
      row.rating=body.rating;row.evaluation=body.evaluation;row.evaluatedAt=logicalTime(row);row.version++;add(row,'EVALUATE','COMPLETED',body.evaluation,body.rating)
      if(state.unknownAction===action){state.unknownAction='';return deny(res,503,'REPAIR_DEPENDENCY_UNAVAILABLE')}
      return reply(res,200,Object.fromEntries(['id','reporterId','technicianId','status','acceptedBy','acceptedAt','acceptanceComment','rating','evaluation','evaluatedAt','version'].map(k=>[k,row[k]])))
     }
     const fp=JSON.stringify([state.user.id,action,row?.id,body]),prior=state.commands.get(body.operationId)
     if(prior){if(prior.fingerprint!==fp)return deny(res,409,'REPAIR_IDEMPOTENCY_CONFLICT');return reply(res,action==='create'?201:200,action==='acceptance'?undefined:prior.response)}
     if(state.conflictAction===action){state.conflictAction='';row.version++;if(action==='assignment'){const previous=row.status;row.technicianId=worker2;add(row,'REASSIGN',previous)}if(action==='cancel'){row.status='RESTORATION_PENDING';row.desiredActive=false;row.desiredVersion++;row.acceptedBy=admin2;row.acceptedAt=logicalTime(row);row.restoreTerminal='COMPLETED';history(row.id).push(event({actorId:admin2,eventType:'ACCEPT',fromStatus:'PENDING_ACCEPTANCE',toStatus:'RESTORATION_PENDING',orderVersion:row.version,note:'concurrent inspection'}))}return deny(res,409,'REPAIR_VERSION_CONFLICT')}
     if(action==='acceptance'&&state.contradictAcceptance){state.contradictAcceptance=false;return reply(res,200,{unexpected:'not a valid void acknowledgement'})}
     const fields=({create:['operationId','roomId','description',...(body.seatId!==undefined?['seatId']:[])],assignment:['operationId','version','technicianId'],start:['operationId','version'],finish:['operationId','version','report'],acceptance:['operationId','version','approved','comment'],cancel:['operationId','version','reason'],'cancel-review':['operationId','version','approved','comment'],'cancel-inspection':['operationId','version','approved','comment']} as Record<string,string[]>)[action]!
     if(Object.keys(body).sort().join(',')!==[...fields].sort().join(','))return deny(res,400,'INVALID_REQUEST')
     let type=action.toUpperCase(),note:string|null=null,from=row?.status??''
     if(action==='create'){
      if(!state.rooms.some(r=>r.id===body.roomId)||typeof body.description!=='string'||!body.description.trim()||body.description.length>4000||(body.seatId&& !((body.roomId===room1&&body.seatId===seat1)||(body.roomId===room2&&body.seatId===seat2))))return deny(res,400,'REPAIR_INVALID_ROOM')
      row=order({...body,id:order2,reporterId:state.user.id,seatId:body.seatId??null});state.rows.push(row);type='SUBMIT';state.histories.set(row.id,[]);add(row,type,from,body.description)
     }else{
      if(row.managed===false)return deny(res,409,'ACCEPTANCE_INTEGRATION_PENDING')
      if(row.version!==body.version)return deny(res,409,'REPAIR_VERSION_CONFLICT')
      if(['start','finish'].includes(action)&&row.technicianId!==state.user.id)return deny(res,403,'REPAIR_NOT_ASSIGNED')
      if(['acceptance','cancel-inspection'].includes(action)&&!independent(row))return deny(res,403,'REPAIR_SELF_ACCEPTANCE_DENIED')
      if(['finish','cancel','acceptance','cancel-review','cancel-inspection'].includes(action)){
       const content=body.report??body.reason??body.comment
       if(typeof content!=='string'||!content.trim()||content.length>4000)return deny(res,400,'INVALID_REQUEST');note=content
      }
      if(action==='assignment'){
       if(!['SUBMITTED','ASSIGNED','IN_PROGRESS'].includes(row.status))return deny(res,409,'REPAIR_STATE_CONFLICT')
       if(state.invalidTechnician||!state.candidates.some(c=>c.userId===body.technicianId))return deny(res,400,'REPAIR_INVALID_TECHNICIAN')
       type=row.status==='SUBMITTED'?'ASSIGN':'REASSIGN';row.technicianId=body.technicianId;if(!row.assignedAt){row.assignedAt='2030-06-03T08:00:00+08:00';row.dueAt='2030-06-03T10:00:00+08:00';row.slaMinutes=120}if(row.status==='SUBMITTED')row.status='ASSIGNED'
      }else if(action==='start'){
       if(row.status!=='ASSIGNED')return deny(res,409,'REPAIR_STATE_CONFLICT');row.status='RESTRICTION_PENDING';row.desiredVersion=row.version+1;row.desiredActive=true;type='START'
      }else if(action==='finish'){
       if(row.status!=='IN_PROGRESS')return deny(res,409,'REPAIR_STATE_CONFLICT');row.status='PENDING_ACCEPTANCE';row.repairReport=body.report;type='FINISH'
      }else if(action==='acceptance'){
       if(row.status!=='PENDING_ACCEPTANCE')return deny(res,409,'REPAIR_STATE_CONFLICT')
       if(body.approved){row.status='RESTORATION_PENDING';row.acceptedBy=state.user.id;row.acceptedAt=logicalTime(row);row.acceptanceComment=body.comment;row.restoreTerminal='COMPLETED';row.desiredVersion=row.version+1;row.desiredActive=false;type='ACCEPT'}else{row.status='IN_PROGRESS';type='REJECT'}
      }else if(action==='cancel'){
       if(!['SUBMITTED','ASSIGNED','RESTRICTION_PENDING','IN_PROGRESS','PENDING_ACCEPTANCE'].includes(row.status))return deny(res,409,'REPAIR_STATE_CONFLICT')
       row.cancelReason=body.reason;row.status=['SUBMITTED','ASSIGNED'].includes(row.status)?'CANCELLED':'CANCEL_REVIEW_PENDING';type=row.status==='CANCELLED'?'CANCEL':'CANCEL_REQUEST'
      }else if(action==='cancel-review'){
       if(row.status!=='CANCEL_REVIEW_PENDING')return deny(res,409,'REPAIR_STATE_CONFLICT');row.status=body.approved?'CANCEL_SAFETY_PENDING':(row.appliedActive?'IN_PROGRESS':'RESTRICTION_PENDING');if(body.approved){row.cancelReviewedBy=state.user.id;row.cancelReviewedAt=logicalTime(row)}type='CANCEL_REVIEW'
      }else if(action==='cancel-inspection'){
       if(row.status!=='CANCEL_SAFETY_PENDING')return deny(res,409,'REPAIR_STATE_CONFLICT');if(body.approved){row.status='RESTORATION_PENDING';row.restoreTerminal='CANCELLED';row.desiredVersion=row.version+1;row.desiredActive=false;row.safetyInspectedBy=state.user.id;row.safetyInspectedAt=logicalTime(row)}else row.status=row.appliedActive?'IN_PROGRESS':'RESTRICTION_PENDING';type='CANCEL_INSPECT'
      }
      row.version++;add(row,type,from,note)
     }
     const result=structuredClone(row);state.commands.set(body.operationId,{fingerprint:fp,response:result})
     if(state.unknownAction===action){state.unknownAction='';return deny(res,503,'REPAIR_DEPENDENCY_UNAVAILABLE')}
     return reply(res,action==='create'?201:200,action==='acceptance'?undefined:result)
    }
    state.unexpected.push(method+' '+path);return deny(res,500,'UNEXPECTED_FIXTURE_ROUTE')
   }
   let file=join(dist,decodeURIComponent(path));try{if(!(await stat(file)).isFile())file=join(dist,'index.html')}catch{file=join(dist,'index.html')}
   const body=await readFile(file);res.writeHead(200,{'Content-Type':({'.html':'text/html','.js':'application/javascript','.css':'text/css','.svg':'image/svg+xml'} as Record<string,string>)[extname(file)]??'application/octet-stream','Set-Cookie':['fixture_session=active; Path=/; HttpOnly; SameSite=Lax','fixture_csrf=fixture-csrf; Path=/; HttpOnly; SameSite=Lax']});res.end(body)
  }catch(e){state.unexpected.push(String(e));reply(res,500,{code:'FIXTURE_ERROR'})}
 })
 await new Promise<void>(r=>server.listen(0,'127.0.0.1',r));const origin='http://127.0.0.1:'+((server.address() as any).port),pages:BrowserPage[]=[]
 async function open(path='/repairs'){
  for(const old of pages.splice(0))await old.close()
  const p=await startBrowser();
 const immediateFill=p.fill.bind(p)
 p.fill=async(selector,value)=>{
  const key=JSON.stringify(selector)
  const ready=`(()=>{const nodes=document.querySelectorAll(${key});const e=nodes[0];return nodes.length===1&&(e instanceof HTMLInputElement||e instanceof HTMLTextAreaElement)&&!e.disabled&&!e.readOnly&&!e.matches(':disabled')})()`
  if(!await p.evaluate(ready))console.log('SCOPED_FILL_WAIT',selector,await p.evaluate(`(()=>{const nodes=document.querySelectorAll(${key});const e=nodes[0];return {count:nodes.length,tag:e?.tagName,disabled:e?.disabled,readOnly:e?.readOnly}})()`))
  await p.waitFor(ready,{timeoutMs:5000})
  await immediateFill(selector,value)
 }
pages.push(p);await p.goto(origin+path);await p.waitFor("document.querySelector('.shell-page h1')",{timeoutMs:15000})
  expect(await p.evaluate("!!document.querySelector('[data-repair-root]')")).toBe(true);return p
 }
 async function close(){
  for(const p of pages){const imagePath='/tmp/campus-reports/repair-'+crypto.randomUUID()+'.png';const bytes=await p.screenshot(imagePath);await appendFile('/tmp/campus-reports/repair-browser-frames.jsonl',JSON.stringify({test:expect.getState().currentTestName,imagePath,sha256:createHash('sha256').update(bytes).digest('hex'),sourceHash,browser:p.metadata.version,time:new Date().toISOString()})+'\n');await p.close()}
  server.closeAllConnections();await new Promise<void>(r=>server.close(()=>r()));expect(state.unexpected).toEqual([])
 }
 return {state,open,close,origin,ack,history}
}
export const count=(p:BrowserPage,s:string)=>p.evaluate<number>('document.querySelectorAll('+JSON.stringify(s)+').length')
export const text=(p:BrowserPage)=>p.evaluate<string>(()=>document.body.innerText)
export async function click(p:BrowserPage,s:string){await p.waitFor('(()=>{const e=document.querySelector('+JSON.stringify(s)+');return e&&!e.disabled&&e.getAttribute("aria-disabled")!=="true"})()');await p.click(s)}
export async function waitText(p:BrowserPage,s:string){await p.waitFor('document.body.innerText.includes('+JSON.stringify(s)+')')}
export async function detail(p:BrowserPage,id=order1){await click(p,'[data-open="'+id+'"]');await p.waitFor("!!document.querySelector('[data-test=repair-detail][data-loaded=true]')")}
export async function reportForm(p:BrowserPage,seat=true){await click(p,'[data-test=new-repair]');await click(p,'[data-room="'+room1+'"]');if(seat)await click(p,'[data-seat="'+seat1+'"]');await p.fill('[data-test=description]','插座损坏')}
export const posts=(f:Awaited<ReturnType<typeof fixture>>,suffix:string)=>f.state.calls.filter(c=>c.method==='POST'&&c.path.endsWith(suffix))
export async function english(p:BrowserPage){await p.evaluate("document.querySelector('.language-switcher select').value='en';document.querySelector('.language-switcher select').dispatchEvent(new Event('change',{bubbles:true}))")}
