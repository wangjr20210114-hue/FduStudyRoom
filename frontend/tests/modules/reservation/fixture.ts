import { createServer, type Server } from 'node:http'
import { fileURLToPath } from 'node:url'
import { randomUUID } from 'node:crypto'
import { createServer as createViteServer, type ViteDevServer } from 'vite'
import { expect } from 'vitest'
import { startBrowser, type BrowserPage } from '../../shared/browser'

export const U1='33333333-3333-4333-8333-333333333301', U2='33333333-3333-4333-8333-333333333302', UA='33333333-3333-4333-8333-333333333303'
export const R1='11111111-1111-4111-8111-111111111101', R2='11111111-1111-4111-8111-111111111102'
export const S1='22222222-2222-4222-8222-222222222201', S2='22222222-2222-4222-8222-222222222202'
export const ID='44444444-4444-4444-8444-444444444401'
export const ordinary=['identity:read','resource:rooms:read','reservation:read','reservation:create','reservation:cancel','reservation:checkin','notification:read']
export const admin=['identity:read','reservation:admin-read','reservation:admin-cancel']
export const iso=(hour:number,minute=0,day='2030-06-03')=>`${day}T${String(hour).padStart(2,'0')}:${String(minute).padStart(2,'0')}:00+08:00`
export const row=(id=ID,extra:Record<string,any>={})=>({id,userId:U1,roomId:R1,seatId:S1,roomName:'R101',roomCode:'R101',seatCode:'A01',startsAt:iso(9),endsAt:iso(10),status:'RESERVED',checkedInAt:null,checkinDeadlineAt:iso(9,15),cancelReason:null,cancelledAt:null,policySnapshot:{version:1,maxReservationHours:4,beforeStartReminderMinutes:15,afterStartReminderMinutes:10,checkinDeadlineMinutes:15},resourceState:'CONFIRMED',resourceVersion:1,version:3,failureCode:null,metadataAvailable:true,timeZone:'Asia/Shanghai',...extra})
export const seat=(id=S1,code='A01',hasSocket=true)=>({id,roomId:R1,code,roomName:'R101',roomCode:'R101',campus:'邯郸',building:'教学楼A',hasSocket,socketType:hasSocket?'FIXED':'NONE'})
export class Fixture {
 server!:Server; vite!:ViteDevServer; origin=''; apiOrigin=''
 rows:Record<string,any>[]=[]; available:Record<string,any>[]=[]; requests:{method:string;path:string;query:Record<string,string>;body:any;headers:Record<string,any>}[]=[]
 currentUser=U1; permissions:string[]=[]; management=false; now=Date.parse(iso(8)); maxHours=4
 failAvailability=false; failHistory=false; failDetail=false; dropCreates=0; dropCancels=0; createPending=false; cancelPending=false; resolveAllocation=false; resolveCancellation=false; allocationConflict=false
 notifications:Record<string,any>[]=[]; commands=new Map<string,{actor:string;path:string;body:string;id:string}>(); cursorTokens=new Map<string,{offset:number;scope:string}>(); navigationNotes:unknown[]=[]
 reset(){this.rows=[row()];this.available=[seat(),seat(S2,'A02',false)];this.currentUser=U1;this.permissions=[...ordinary];this.management=false;this.now=Date.parse(iso(8));this.maxHours=4;this.failAvailability=this.failHistory=this.failDetail=this.createPending=this.cancelPending=this.resolveAllocation=this.resolveCancellation=this.allocationConflict=false;this.dropCreates=this.dropCancels=0;this.notifications=[];this.requests=[];this.commands.clear();this.cursorTokens.clear();this.navigationNotes=[]}
 async start(){
  this.reset()
  this.server=createServer((req,res)=>{void(async()=>{
   const url=new URL(req.url!,'http://127.0.0.1');let raw='';for await(const chunk of req)raw+=chunk;const body=raw?JSON.parse(raw):undefined,method=req.method!,path=url.pathname
   this.requests.push({method,path,query:Object.fromEntries(url.searchParams),body,headers:req.headers})
   const send=(status:number,value?:unknown)=>{res.statusCode=status;res.setHeader('Content-Type','application/json');res.setHeader('X-Request-Id',randomUUID());res.end(value===undefined?'':JSON.stringify(value))}
   const fail=(status:number,code:string)=>send(status,{code,traceId:randomUUID()});const has=(p:string)=>this.permissions.includes(p)
   if(path==='/api/auth/config')return send(200,{mode:'password',available:true,registrationOpen:true})
   if(path==='/api/auth/me')return send(200,{id:this.currentUser,loginName:'fixture-user',displayName:'测试用户',role:this.management?'SYSTEM_ADMIN':'USER',permissions:this.permissions,authVersion:1,profileIncomplete:false})
   if(path==='/api/auth/csrf')return send(200,{headerName:'X-CSRF-TOKEN',token:'reservation-fixture-csrf'})
   if(path.startsWith('/internal/'))return fail(401,'SERVICE_AUTHENTICATION_REQUIRED')
   if(method!=='GET'&&req.headers['x-csrf-token']!=='reservation-fixture-csrf')return fail(403,'CSRF_INVALID')
   if(path==='/api/reservations/availability'){
    if(!has('reservation:read'))return fail(403,'FORBIDDEN');if(this.failAvailability)return fail(503,'RESOURCE_UNAVAILABLE')
    const start=url.searchParams.get('startsAt'),end=url.searchParams.get('endsAt');if(!start||!end||!(Date.parse(end)>Date.parse(start)))return fail(400,'INVALID_RESERVATION_TIME')
    return send(200,this.available.filter(s=>(!url.searchParams.get('campus')||s.campus===url.searchParams.get('campus'))&&(!url.searchParams.get('building')||s.building===url.searchParams.get('building'))&&(!url.searchParams.get('roomId')||s.roomId===url.searchParams.get('roomId'))&&(url.searchParams.get('hasSocket')!=='true'||s.hasSocket)))
   }
   if((path==='/api/reservations'||path==='/api/admin/reservations')&&method==='GET'){
    const all=path.includes('/admin/');if(!has(all?'reservation:admin-read':'reservation:read'))return fail(403,'FORBIDDEN');if(this.failHistory)return fail(503,'HISTORY_UNAVAILABLE')
    const limit=Number(url.searchParams.get('limit')??20),cursor=url.searchParams.get('cursor');const scope=JSON.stringify({actor:this.currentUser,all,room:url.searchParams.get('roomId'),seat:url.searchParams.get('seatId'),from:url.searchParams.get('from'),to:url.searchParams.get('to')});const token=cursor?this.cursorTokens.get(cursor):null
    if(cursor&&(!token||token.scope!==scope))return fail(400,'INVALID_RESERVATION_CURSOR')
    const list=this.rows.filter(r=>(all||r.userId===this.currentUser)&&(!url.searchParams.get('roomId')||r.roomId===url.searchParams.get('roomId'))&&(!url.searchParams.get('seatId')||r.seatId===url.searchParams.get('seatId'))&&(!url.searchParams.get('from')||Date.parse(r.startsAt)>=Date.parse(url.searchParams.get('from')!))&&(!url.searchParams.get('to')||Date.parse(r.startsAt)<Date.parse(url.searchParams.get('to')!))).sort((a,b)=>Date.parse(b.startsAt)-Date.parse(a.startsAt)||b.id.localeCompare(a.id))
    const offset=token?.offset??0;let nextCursor=null
    if(offset+limit<list.length){nextCursor='opaque-'+randomUUID()+'.signed-fixture';this.cursorTokens.set(nextCursor,{offset:offset+limit,scope})}
    return send(200,{items:list.slice(offset,offset+limit),nextCursor,limit,timeZone:'Asia/Shanghai'})
   }
   const match=path.match(/^\/api\/reservations\/([^/]+)(?:\/(cancel|admin-cancel|check-in|rebook))?$/)
   let record=match?this.rows.find(r=>r.id===match[1]):undefined
   if(match&&!record)return fail(404,'RESERVATION_NOT_FOUND')
   if(match&&method==='GET'){
    if(!has(match[2]==='rebook'?'reservation:create':'reservation:read')||record!.userId!==this.currentUser)return fail(403,'RESERVATION_OWNER_REQUIRED');if(this.failDetail)return fail(503,'DETAIL_UNAVAILABLE')
    if(match[2]==='rebook'){const past=Date.parse(record!.startsAt)<=this.now;return send(200,{sourceReservationId:record!.id,seatId:record!.seatId,roomId:record!.roomId,roomName:record!.roomName,seatCode:record!.seatCode,policy:{...record!.policySnapshot,maxReservationHours:this.maxHours},startsAt:past?null:record!.startsAt,endsAt:past?null:record!.endsAt,requiresNewTimeSelection:past,timeZone:'Asia/Shanghai'})}
    if(this.resolveAllocation&&record!.status==='PENDING')Object.assign(record!,{status:'RESERVED',resourceState:'CONFIRMED',version:record!.version+1})
    if(this.resolveCancellation&&record!.status==='CANCEL_PENDING')Object.assign(record!,{status:record!.cancelReason==='NO_SHOW'?'NO_SHOW':'CANCELLED',resourceState:'RELEASED',version:record!.version+1})
    return send(200,{...record,notifications:this.notifications})
   }
   if(method==='POST'&&(path==='/api/reservations'||match)){
    const action=match?.[2]??'create',permission=action==='create'?'reservation:create':action==='check-in'?'reservation:checkin':action==='admin-cancel'?'reservation:admin-cancel':'reservation:cancel'
    if(!has(permission))return fail(403,'FORBIDDEN');if(match&&action!=='admin-cancel'&&record!.userId!==this.currentUser)return fail(403,'RESERVATION_OWNER_REQUIRED')
    if(!body?.operationId)return fail(400,'INVALID_OPERATION');const cached=this.commands.get(body.operationId),signature=JSON.stringify(body)
    if(cached){if(cached.actor!==this.currentUser||cached.path!==path||cached.body!==signature)return fail(409,'RESERVATION_OPERATION_CONFLICT');record=this.rows.find(r=>r.id===cached.id)!}
    else {
     if(action==='create'){
      const start=Date.parse(body.startsAt),end=Date.parse(body.endsAt);if(!Number.isFinite(start)||!Number.isFinite(end)||end<=start||start<this.now)return fail(400,'INVALID_RESERVATION_TIME')
      if(start%3600000||end%3600000||(end-start)>this.maxHours*3600000)return fail(400,'INVALID_RESERVATION_DURATION')
      const hour=Number(new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Shanghai',hour:'2-digit',hourCycle:'h23'}).format(new Date(end)));if(hour>22)return fail(409,'RESOURCE_CONFLICT')
      if(this.allocationConflict||!this.available.some(s=>s.id===body.seatId))return fail(409,'RESOURCE_CONFLICT')
      const s=this.available.find(s=>s.id===body.seatId)!;record=row(randomUUID(),{userId:this.currentUser,seatId:s.id,roomId:s.roomId,seatCode:s.code,roomName:s.roomName,startsAt:body.startsAt,endsAt:body.endsAt,checkinDeadlineAt:new Date(start+15*60000).toISOString(),policySnapshot:{...row().policySnapshot,maxReservationHours:this.maxHours},status:this.createPending?'PENDING':'RESERVED',resourceState:this.createPending?'PENDING':'CONFIRMED',version:1});this.rows.push(record)
     } else if(action==='cancel'||action==='admin-cancel'){
      if(action==='admin-cancel'&&!body.reason?.trim())return fail(400,'ADMIN_CANCEL_REASON_REQUIRED')
      if(!['CANCELLED','NO_SHOW','CANCEL_PENDING'].includes(record!.status)){
       if(!['PENDING','RESERVED','CHECKED_IN'].includes(record!.status)||(action==='cancel'&&(record!.status!=='RESERVED'||this.now>=Date.parse(record!.startsAt)))||(action==='admin-cancel'&&this.now>=Date.parse(record!.endsAt)))return fail(409,'RESERVATION_CANNOT_CANCEL')
       Object.assign(record!,{status:this.cancelPending?'CANCEL_PENDING':'CANCELLED',resourceState:this.cancelPending?'RELEASE_PENDING':'RELEASED',cancelReason:action==='admin-cancel'?'ADMIN':'USER',cancelledAt:new Date(this.now).toISOString(),version:record!.version+1})
      }
     } else if(action==='check-in'){
      if(record!.status!=='RESERVED'||this.now<Date.parse(record!.startsAt)||this.now>=Date.parse(record!.checkinDeadlineAt))return fail(409,'CHECKIN_WINDOW_CLOSED')
      if(body.code!=='123456')return fail(400,'INVALID_CHECKIN_CODE')
      Object.assign(record!,{status:'CHECKED_IN',checkedInAt:new Date(this.now).toISOString(),version:record!.version+1})
     }
     this.commands.set(body.operationId,{actor:this.currentUser,path,body:signature,id:record!.id})
    }
    if(action==='create'&&this.dropCreates-->0){res.destroy();return}
    if((action==='cancel'||action==='admin-cancel')&&this.dropCancels-->0){res.destroy();return}
    return send(['PENDING','CANCEL_PENDING'].includes(record!.status)?202:action==='create'&&!cached?201:200,record)
   }
   return fail(404,'NOT_FOUND')
  })().catch(()=>{if(!res.destroyed){res.statusCode=500;res.end(JSON.stringify({code:'FIXTURE_FAILURE'}))}})})
  await new Promise<void>(resolve=>this.server.listen(0,'127.0.0.1',resolve));this.apiOrigin='http://127.0.0.1:'+(this.server.address() as any).port;process.env.CAMPUS_API_TARGET=this.apiOrigin;process.env.CAMPUS_WORKFLOW_TARGET=this.apiOrigin
  const root=fileURLToPath(new URL('../../../',import.meta.url)),f=this
  this.vite=await createViteServer({root,configFile:root+'vite.config.ts',logLevel:'error',plugins:[{name:'reservation-test-clock',transformIndexHtml(){return [{tag:'script',injectTo:'head-prepend',children:`(()=>{const NativeDate=Date;window.__reservationNow=${JSON.stringify(f.now)};function ClockDate(...args){return new NativeDate(...(args.length?args:[window.__reservationNow]))}Object.setPrototypeOf(ClockDate,NativeDate);ClockDate.prototype=NativeDate.prototype;ClockDate.now=()=>window.__reservationNow;window.Date=ClockDate;})()`}]}}],server:{host:'127.0.0.1',port:0,strictPort:false}});await this.vite.listen();this.origin='http://127.0.0.1:'+(this.vite.httpServer!.address() as any).port
 }
 async stop(){await this.vite?.close();if(this.server){this.server.closeAllConnections();await new Promise<void>(resolve=>this.server.close(()=>resolve()))}}
}
export async function open(f:Fixture,path='/reservations',expectedPath=path.split('?')[0]){
 const p=await startBrowser()
 try {try{await p.goto(f.origin+path)}catch(e){if(!(e instanceof Error)||!e.message.startsWith('BROWSER_WAIT_TIMEOUT'))throw e;f.navigationNotes.push({path,reason:'document complete exceeded initial driver5s; extra actual ready wait15s'});await p.waitFor("!window.__campusPreviousDocument && document.readyState==='complete'",{timeoutMs:15000})}await p.waitFor(`location.pathname===${JSON.stringify(expectedPath)} && !!document.querySelector('h1')`,{timeoutMs:15000});return p}
 catch(e){await p.screenshot('/tmp/campus-reports/M1-open-error-'+randomUUID()+'.png').catch(()=>{});await p.close();throw e}
}
export async function ready(p:BrowserPage){expect(await p.evaluate(()=>!!document.querySelector('[data-reservations-page]')),'RESERVATION_UI_REQUIRED').toBe(true);await p.waitFor(()=>document.querySelector('[data-testid="reservation-history"]')?.getAttribute('aria-busy')==='false')}
export async function input(p:BrowserPage,id:string,value:string){await p.evaluate(`(()=>{const e=document.getElementById(${JSON.stringify(id)});e.value=${JSON.stringify(value)};e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));})()`)}
export async function search(p:BrowserPage,start='2030-06-03T09:00',end='2030-06-03T10:00'){await input(p,'reservation-start',start);await input(p,'reservation-end',end);await p.click('[data-testid="reservation-search"]');await p.waitFor(()=>!!document.querySelector('[data-testid="reservation-results"]')||!!document.querySelector('[data-testid="reservation-search-error"]'))}
export async function choose(p:BrowserPage,id=S1){await p.click('[data-testid="reservation-select-'+id+'"]');await p.waitFor(()=>!!document.querySelector('[data-testid="reservation-create"]'))}
export async function detail(p:BrowserPage,id=ID){await p.click('[data-testid="reservation-detail-'+id+'"]');await p.waitFor(()=>document.querySelector('[data-testid="reservation-detail"]')?.getAttribute('aria-busy')==='false')}
export async function refresh(p:BrowserPage){await p.waitFor(()=>{const button=document.querySelector('[data-testid="reservation-refresh"]') as HTMLButtonElement|null;return !!button&&!button.disabled});await p.click('[data-testid="reservation-refresh"]');await p.waitFor(()=>document.querySelector('[data-testid="reservation-detail"]')?.getAttribute('aria-busy')==='false')}
export async function language(p:BrowserPage){await p.evaluate(()=>{const el=document.querySelector('.language-switcher select') as HTMLSelectElement;el.value='en';el.dispatchEvent(new Event('change',{bubbles:true}))});await p.waitFor(()=>document.documentElement.lang==='en')}
export async function clock(p:BrowserPage,f:Fixture,value:string){f.now=Date.parse(value);await p.evaluate('window.__reservationNow='+JSON.stringify(f.now))}
export async function textIncludes(p:BrowserPage,selector:string,value:string){await p.waitFor(`document.querySelector(${JSON.stringify(selector)})?.textContent?.includes(${JSON.stringify(value)})`)}
export type {BrowserPage}
