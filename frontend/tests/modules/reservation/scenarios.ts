import { expect } from 'vitest'
import { Fixture,ready,input,search,choose,detail,refresh,language,clock,textIncludes,row,seat,iso,U1,U2,UA,R1,R2,S1,S2,ID,ordinary,admin,type BrowserPage } from './fixture'
type Visit=(path?:string,expected?:string)=>Promise<BrowserPage>
const post=(f:Fixture,path='/api/reservations')=>f.requests.filter(r=>r.method==='POST'&&r.path===path)
async function command(p:BrowserPage){await p.waitFor(()=>document.querySelector('[data-testid="reservation-command-state"]')?.getAttribute('aria-busy')==='false')}
async function create(p:BrowserPage){await p.click('[data-testid="reservation-create"]');await command(p)}
async function range(p:BrowserPage,start:string,end:string){await input(p,'reservation-start',start);await input(p,'reservation-end',end)}
async function state(p:BrowserPage,value:string){expect(await p.text('[data-testid="reservation-status"]')).toContain(value)}
async function historyIds(p:BrowserPage){return p.evaluate<string[]>(()=>Array.from(document.querySelectorAll('[data-reservation-id]')).map(el=>el.getAttribute('data-reservation-id')!))}
async function waitHistory(p:BrowserPage){await p.waitFor(()=>document.querySelector('[data-testid="reservation-history"]')?.getAttribute('aria-busy')==='false')}
const link=(seatId=S1)=>'/reservations?'+new URLSearchParams({roomId:R1,seatId,startsAt:iso(9),endsAt:iso(10)}).toString()
export async function scenario(id:string,f:Fixture,visit:Visit){
 if(id.startsWith('M1-01')||id.startsWith('M1-02'))f.rows=[]
 if(id.startsWith('M1-04'))f.now=Date.parse(iso(9))
 if(id==='M1-01.S02')f.available=[]
 if(id==='M1-02.S02')f.now=Date.parse(iso(8,1)),f.maxHours=2
 if(id==='M1-02.S04')f.createPending=true,f.dropCreates=2
 if(id==='M1-03.S01')f.now=Date.parse(iso(8,30)),f.rows=[row(),row('44444444-4444-4444-8444-444444444402',{roomId:R2,seatId:S2,seatCode:'B01',startsAt:iso(15),endsAt:iso(16)})]
 if(id==='M1-03.S03')f.cancelPending=true,f.dropCancels=2
 if(id==='M1-03.S04')f.now=Date.parse(iso(8,30)),f.rows=[row(ID,{status:'CANCEL_PENDING',resourceState:'RELEASE_PENDING',cancelReason:'USER',releaseProgress:{status:'PENDING',attempts:2,nextRetryAt:iso(8,31),errorCode:'DEPENDENCY_HTTP_503'}})]
 if(id==='M1-04.S02')f.now=Date.parse(iso(8,59))+59000
 if(id==='M1-04.S03')f.now=Date.parse(iso(9,14))+59000,f.rows=[row(ID,{status:'CHECKED_IN',checkedInAt:new Date(f.now).toISOString()})]
 if(id==='M1-05.S01')f.now=Date.parse(iso(8,44))+59000
 if(id==='M1-05.S02')f.rows=[row(ID,{status:'CANCELLED',resourceState:'RELEASED',cancelReason:'USER',cancelledAt:iso(8,30)}),row('44444444-4444-4444-8444-444444444402',{seatId:S2})]
 if(id==='M1-05.S03')f.now=Date.parse(iso(8,50)),f.notifications=[{eventId:'55555555-5555-4555-8555-555555555501',status:'DONE',receipt:{emailStatus:'UNKNOWN'}}]
 if(id==='M1-07.S01')f.now=Date.parse(iso(9,14))+59000
 if(['M1-07.S02','M1-07.S04'].includes(id))f.now=Date.parse(iso(9,20)),f.rows=[row(ID,{status:'NO_SHOW',resourceState:'RELEASED',cancelReason:'NO_SHOW',cancelledAt:iso(9,15),version:5})],f.notifications=[{eventId:'55555555-5555-4555-8555-555555555502',status:'DONE',receipt:{emailStatus:'PENDING'}}]
 if(id==='M1-07.S03')f.now=Date.parse(iso(9,15)),f.rows=[row(ID,{status:'CHECKED_IN',checkedInAt:new Date(Date.parse(iso(9,14))+59000).toISOString()}),row('44444444-4444-4444-8444-444444444402',{status:'CANCELLED',cancelReason:'SYSTEM_MAINTENANCE',resourceState:'RELEASED',cancelledAt:iso(9,10),seatId:S2})]
 if(id==='M1-07.S05')f.now=Date.parse(iso(9,15)),f.rows=[row(ID,{status:'COMPLETED',startsAt:iso(7),endsAt:iso(8),checkedInAt:iso(7)}),row('44444444-4444-4444-8444-444444444402',{status:'CANCELLED',resourceState:'RELEASED',startsAt:iso(8),endsAt:iso(9),cancelReason:'USER',cancelledAt:iso(7,30)})]
 if(id==='M1-08.S02')f.now=Date.parse(iso(8,0,'2030-06-04')),f.rows=[row(ID,{status:'COMPLETED',endsAt:iso(12)})],f.maxHours=2
 if(id==='M1-08.S03')f.rows=[row(ID,{status:'CANCELLED',startsAt:'2030-06-03T16:00:00Z',endsAt:'2030-06-03T17:00:00Z',cancelledAt:'2030-06-03T15:00:00Z',cancelReason:'USER',resourceState:'RELEASED'})]
 if(id==='M1-08.S04')f.management=true,f.currentUser=UA,f.permissions=[...admin],f.rows=[row(),row('44444444-4444-4444-8444-444444444402',{userId:U2,seatId:S2,status:'NO_SHOW',cancelReason:'NO_SHOW',resourceState:'RELEASED'})]
 const p=await visit(id==='M1-08.S04'?'/manage/reservations':'/reservations');await ready(p)
 switch(id){
 case 'M1-01.S01': {
  await input(p,'reservation-campus','邯郸');await input(p,'reservation-building','教学楼A');await p.click('#reservation-socket');await search(p)
  expect(await p.text('[data-testid="reservation-results"]')).toContain('R101');expect(await p.text('[data-testid="reservation-results"]')).toContain('A01');expect(await p.text('[data-testid="reservation-results"]')).not.toContain('A02');expect(await p.text('[data-testid="reservation-results"]')).toContain('插座')
  const q=f.requests.filter(r=>r.path.endsWith('/availability')).at(-1)!.query;expect(q).toMatchObject({campus:'邯郸',building:'教学楼A',hasSocket:'true'});expect(Date.parse(q.startsAt)).toBe(Date.parse(iso(9)));expect(Date.parse(q.endsAt)).toBe(Date.parse(iso(10)));await language(p);expect(await p.text('[data-testid="reservation-search"]')).toBe('Search')
  const prefilled=await visit(link());await ready(prefilled);expect(await prefilled.evaluate(()=> (document.getElementById('reservation-start') as HTMLInputElement).value)).toBe('2030-06-03T09:00');expect(post(f)).toHaveLength(0)
  const invalid=await visit(link('bad-id'));await ready(invalid);expect(await invalid.evaluate(()=>!!document.querySelector('[data-testid="reservation-prefill-error"]'))).toBe(true);expect(post(f)).toHaveLength(0);break
 }
 case 'M1-01.S02': {
  await search(p);expect(await p.text('[data-testid="reservation-results"]')).toContain('没有可用座位');expect(await p.evaluate(()=>document.querySelectorAll('[data-testid^="reservation-select-"]').length)).toBe(0)
  const prefilled=await visit(link());await ready(prefilled);await create(prefilled);expect(await prefilled.evaluate(()=>!!document.querySelector('[data-testid="reservation-write-error"]'))).toBe(true);expect(post(f)).toHaveLength(0);expect(f.requests.every(r=>!r.headers['x-service-key']&&!('userId'in r.query)&&!r.path.startsWith('/internal/'))).toBe(true);break
 }
 case 'M1-01.S03':
  await search(p);expect(await p.text('[data-testid="reservation-results"]')).toContain('A01');f.available=[];await search(p);expect(await p.text('[data-testid="reservation-results"]')).toContain('没有可用座位');f.failAvailability=true;await search(p);expect(await p.evaluate(()=>!!document.querySelector('[data-testid="reservation-search-error"]'))).toBe(true);expect(await p.evaluate(()=>!!document.querySelector('[data-testid="reservation-results"]'))).toBe(false);f.failAvailability=false;f.available=[seat()];await p.click('[data-testid="reservation-search-retry"]');await textIncludes(p,'[data-testid="reservation-results"]','A01');break
 case 'M1-02.S01':
  await search(p,'2030-06-03T09:00','2030-06-03T13:00');await choose(p);await create(p);await state(p,'预约已确认');expect(f.rows).toHaveLength(1)
  for(const [start,end]of [['09:30','10:30'],['09:00','14:00'],['10:00','09:00']]){await range(p,'2030-06-03T'+start,'2030-06-03T'+end);await create(p);expect(await p.evaluate(()=>!!document.querySelector('[data-testid="reservation-write-error"]'))).toBe(true);expect(f.rows).toHaveLength(1)}
  expect(Object.keys(post(f)[0].body).sort()).toEqual(['endsAt','operationId','seatId','startsAt']);break
 case 'M1-02.S02':
  await search(p);await choose(p)
  for(const[start,end]of [['08:00','09:00'],['21:00','23:00'],['09:00','12:00']]){await range(p,'2030-06-03T'+start,'2030-06-03T'+end);await create(p);expect(await p.evaluate(()=>!!document.querySelector('[data-testid="reservation-write-error"]'))).toBe(true);expect(f.rows).toHaveLength(0)}
  f.maxHours=6;await range(p,'2030-06-03T09:00','2030-06-03T14:00');await create(p);await state(p,'预约已确认');expect(f.rows[0].policySnapshot.maxReservationHours).toBe(6);break
 case 'M1-02.S03': {
  await search(p);await choose(p);await create(p);await state(p,'预约已确认');f.currentUser=U2;f.allocationConflict=true;const loser=await visit();await ready(loser);await search(loser);await choose(loser);await create(loser);expect(await loser.evaluate(()=>!!document.querySelector('[data-testid="reservation-write-error"]'))).toBe(true);expect(await loser.evaluate(()=>document.querySelector('[data-testid="reservation-status"]')?.textContent?.includes('预约已确认')??false)).toBe(false);expect(f.rows.filter(r=>r.status==='RESERVED')).toHaveLength(1);break
 }
 case 'M1-02.S04':
  await search(p);await choose(p);await create(p)
  for(let i=0;i<2;i++){await p.click('[data-testid="reservation-command-retry"]');await command(p)}
  expect(post(f)).toHaveLength(3);expect(new Set(post(f).map(r=>JSON.stringify(r.body))).size).toBe(1);expect(f.rows).toHaveLength(1);await state(p,'预约处理中');f.resolveAllocation=true;await refresh(p);await state(p,'预约已确认');expect(f.rows).toHaveLength(1);expect(await p.text('[data-testid="reservation-detail-id"]')).toContain(f.rows[0].id);const restored=await visit();await ready(restored);expect(post(f)).toHaveLength(3);await detail(restored,f.rows[0].id);await state(restored,'预约已确认');break
 case 'M1-03.S01': {
  const other=JSON.stringify(f.rows[1]);await detail(p);await p.click('[data-testid="reservation-cancel"]');await command(p);await state(p,'已取消');expect(f.rows[0].status).toBe('CANCELLED');expect(JSON.stringify(f.rows[1])).toBe(other);expect(post(f,'/api/reservations/'+ID+'/cancel')).toHaveLength(1);expect(f.requests.some(r=>r.path.startsWith('/internal/'))).toBe(false);break
 }
 case 'M1-03.S02': {
  f.currentUser=U2;let view=await visit('/reservations?reservationId='+ID);await ready(view);await textIncludes(view,'[data-testid="reservation-detail-error"]','');expect(await view.evaluate(()=>!!document.querySelector('[data-testid="reservation-cancel"]'))).toBe(false)
  f.currentUser=U1;for(const status of ['RESERVED','CHECKED_IN']){f.now=Date.parse(iso(9));f.rows=[row(ID,{status})];view=await visit();await ready(view);await detail(view);expect(await view.evaluate(()=>{const e=document.querySelector('[data-testid="reservation-cancel"]') as HTMLButtonElement|null;return !e||e.disabled})).toBe(true);expect(await view.text('[data-testid="reservation-detail"]')).toContain('无法取消')}
  f.management=true;f.currentUser=UA;f.permissions=['identity:read','reservation:admin-read'];f.rows=[row()];f.requests=[];view=await visit('/manage/reservations');await ready(view);await detail(view);expect(await view.evaluate(()=>!!document.querySelector('[data-testid="reservation-admin-cancel"]'))).toBe(false)
  f.permissions=[...admin];view=await visit('/manage/reservations');await ready(view);await detail(view);await pSafeAdminCancel(view);expect(post(f,'/api/reservations/'+ID+'/admin-cancel')).toHaveLength(1);expect(f.requests.some(r=>r.method==='GET'&&r.path==='/api/reservations/'+ID)).toBe(false);break
 }
 case 'M1-03.S03':
  await detail(p);await p.click('[data-testid="reservation-cancel"]');await command(p);for(let i=0;i<2;i++){await p.click('[data-testid="reservation-command-retry"]');await command(p)}expect(new Set(post(f,'/api/reservations/'+ID+'/cancel').map(r=>JSON.stringify(r.body))).size).toBe(1);await state(p,'取消处理中');f.resolveCancellation=true;await refresh(p);await state(p,'已取消');expect(f.requests.some(r=>r.path.startsWith('/internal/'))).toBe(false);break
 case 'M1-03.S04': {
  await detail(p);let progress=await p.text('[data-testid="reservation-release-progress"]');expect(progress).toContain('2');expect(progress).toContain('08:31');expect(progress).toContain('依赖服务暂不可用');await state(p,'取消处理中')
  Object.assign(f.rows[0].releaseProgress,{status:'CLAIMED',attempts:3,nextRetryAt:null});await refresh(p);expect(await p.text('[data-testid="reservation-release-progress"]')).toContain('处理中')
  Object.assign(f.rows[0].releaseProgress,{status:'DONE',errorCode:null});await refresh(p);expect(await p.text('[data-testid="reservation-release-progress"]')).not.toContain('释放完成');Object.assign(f.rows[0],{status:'CANCELLED',resourceState:'RELEASED',version:4});await refresh(p);expect(await p.text('[data-testid="reservation-release-progress"]')).toContain('释放完成')
  f.rows=[row('44444444-4444-4444-8444-444444444402',{status:'CANCEL_PENDING',resourceState:'RELEASE_PENDING'})];const legacy=await visit();await ready(legacy);await detail(legacy,f.rows[0].id);progress=await legacy.text('[data-testid="reservation-release-progress"]');expect(progress).toContain('待核实');expect(progress).not.toContain('0次');expect(f.requests.some(r=>r.method==='POST')).toBe(false);break
 }
 case 'M1-04.S01':
  await detail(p);await input(p,'reservation-checkin-code','123456');await p.evaluate(()=>{const b=document.querySelector('[data-testid="reservation-checkin"]') as HTMLButtonElement;b.click();b.click()});await command(p);await state(p,'已签到');expect(await p.text('[data-testid="reservation-detail"]')).toContain('09:00');expect(post(f,'/api/reservations/'+ID+'/check-in')).toHaveLength(1);expect(await p.evaluate(()=>location.href.includes('123456')||JSON.stringify(localStorage).includes('123456')||JSON.stringify(sessionStorage).includes('123456'))).toBe(false);expect(f.requests.some(r=>r.method==='GET'&&JSON.stringify(r.query).includes('123456'))).toBe(false);await refresh(p);await state(p,'已签到');break
 case 'M1-04.S02': {
  await detail(p);expect(await p.evaluate(()=>{const b=document.querySelector('[data-testid="reservation-checkin"]') as HTMLButtonElement|null;return !b||b.disabled})).toBe(true);await clock(p,f,iso(9));await p.waitFor(()=>!(document.querySelector('[data-testid="reservation-checkin"]') as HTMLButtonElement)?.disabled)
  for(const code of ['654321','111111']){await input(p,'reservation-checkin-code',code);await p.click('[data-testid="reservation-checkin"]');await command(p);expect(await p.evaluate(()=>!!document.querySelector('[data-testid="reservation-write-error"]'))).toBe(true);expect(f.rows[0].status).toBe('RESERVED');expect(f.rows[0].cancelReason).toBe(null)}
  f.currentUser=U2;const denied=await visit('/reservations?reservationId='+ID);await ready(denied);await textIncludes(denied,'[data-testid="reservation-detail-error"]','');expect(await denied.evaluate(()=>!!document.querySelector('[data-testid="reservation-checkin"]'))).toBe(false);break
 }
 case 'M1-04.S03': {
  await detail(p);const checked=await p.text('[data-testid="reservation-detail"]');await clock(p,f,iso(9,15));await refresh(p);await state(p,'已签到');expect(await p.text('[data-testid="reservation-detail"]')).toContain('09:14:59');expect(checked).toContain('09:14:59');f.rows=[row()];const late=await visit();await ready(late);await detail(late);expect(await late.evaluate(()=>{const b=document.querySelector('[data-testid="reservation-checkin"]') as HTMLButtonElement|null;return !b||b.disabled})).toBe(true);expect(post(f,'/api/reservations/'+ID+'/check-in')).toHaveLength(0);expect(f.rows[0].status).toBe('RESERVED');break
 }
 case 'M1-05.S01':
  await detail(p);expect(await p.text('[data-testid="reservation-reminder-time"]')).toContain('08:45');expect(await p.evaluate(()=>document.querySelectorAll('[data-notification-event]').length)).toBe(0);f.notifications=[{eventId:'55555555-5555-4555-8555-555555555501',status:'DONE',receipt:{emailStatus:'SENT'}}];await clock(p,f,iso(8,45));await refresh(p);expect(await p.text('[data-testid="reservation-notifications"]')).toContain('通知服务已接收');expect(await p.text('[data-testid="reservation-notifications"]')).toContain('邮件服务器已接收');expect(await p.text('[data-testid="reservation-notifications"]')).toContain(f.notifications[0].eventId);break
 case 'M1-05.S02':
  await detail(p);expect(await p.evaluate(()=>document.querySelectorAll('[data-notification-event]').length)).toBe(0);f.notifications=[{eventId:'55555555-5555-4555-8555-555555555501',status:'DONE',receipt:{emailStatus:'PENDING'}}];await detail(p,'44444444-4444-4444-8444-444444444402');await refresh(p);await refresh(p);expect(await p.evaluate(()=>document.querySelectorAll('[data-notification-event]').length)).toBe(1);expect(await p.text('[data-testid="reservation-notifications"]')).not.toContain('邮件服务器已接收');expect(f.requests.some(r=>r.method==='POST')).toBe(false);await p.click('[data-testid="reservation-notifications-link"]');await p.waitFor(()=>location.pathname==='/notifications');break
 case 'M1-05.S03':
  await detail(p);expect(await p.text('[data-testid="reservation-notifications"]')).toContain('邮件待核实');expect(await p.text('[data-testid="reservation-notifications"]')).not.toContain('邮件服务器已接收');f.notifications[0].receipt.emailStatus='NOT_CONFIGURED';await refresh(p);expect(await p.text('[data-testid="reservation-notifications"]')).toContain('邮件未配置');expect(f.requests.some(r=>r.method==='POST')).toBe(false);break
 case 'M1-07.S01':
  await detail(p);await state(p,'预约已确认');await clock(p,f,iso(9,15));await textIncludes(p,'[data-testid="reservation-deadline-wait"]','待核实');expect(f.rows[0].status).toBe('RESERVED');Object.assign(f.rows[0],{status:'NO_SHOW',resourceState:'RELEASED',cancelReason:'NO_SHOW',cancelledAt:iso(9,15),version:4});await refresh(p);await state(p,'未签到违约');expect(f.requests.some(r=>r.method==='POST')).toBe(false);break
 case 'M1-07.S02':
  await detail(p);await refresh(p);await refresh(p);await state(p,'未签到违约');expect(await p.evaluate(()=>document.querySelectorAll('[data-notification-event]').length)).toBe(1);expect(f.requests.some(r=>r.method==='POST')).toBe(false);break
 case 'M1-07.S03':
  await detail(p);await refresh(p);await state(p,'已签到');await detail(p,f.rows[1].id);await refresh(p);expect(await p.text('[data-testid="reservation-detail"]')).toContain('系统维修');expect(await p.text('[data-testid="reservation-detail"]')).toContain('不计违约');expect(f.requests.some(r=>r.method==='POST')).toBe(false);break
 case 'M1-07.S04':
  await detail(p);await state(p,'未签到违约');Object.assign(f.rows[0],{status:'RESERVED',resourceState:'CONFIRMED',cancelReason:null,version:2});await refresh(p);await state(p,'未签到违约');expect(f.requests.some(r=>r.method==='POST')).toBe(false);break
 case 'M1-07.S05': {
  const before=JSON.stringify(f.rows);await detail(p);await refresh(p);await state(p,'已结束');await detail(p,f.rows[1].id);await refresh(p);await state(p,'已取消');expect(JSON.stringify(f.rows)).toBe(before);expect(f.requests.some(r=>r.method==='POST')).toBe(false);break
 }
 case 'M1-08.S01': {
  for(const n of [19,20,21,41]){
   f.rows=Array.from({length:n},(_,i)=>row('66666666-6666-4666-8666-'+String(i+1).padStart(12,'0'),{startsAt:new Date(Date.parse(iso(9))+i*3600000).toISOString(),endsAt:new Date(Date.parse(iso(10))+i*3600000).toISOString()}));f.rows.push(row('77777777-7777-4777-8777-777777777777',{userId:U2,roomId:R2}));const view=await visit();await ready(view);const seen:string[]=[]
   for(let step=0;step<4;step++){seen.push(...await historyIds(view));const done=await view.evaluate(()=>{const b=document.querySelector('[data-testid="reservation-next"]') as HTMLButtonElement|null;return !b||b.disabled});if(done)break;await view.click('[data-testid="reservation-next"]');await waitHistory(view)}
   expect(seen).toHaveLength(n);expect(new Set(seen).size).toBe(n);expect(seen).toEqual(f.rows.filter(r=>r.userId===U1).sort((a,b)=>Date.parse(b.startsAt)-Date.parse(a.startsAt)||b.id.localeCompare(a.id)).map(r=>r.id));expect(await view.evaluate(()=>document.body.textContent?.includes('共41条'))).toBe(false)
   await input(view,'reservation-history-room',R2);await view.click('[data-testid="reservation-history-search"]');await waitHistory(view);expect(await historyIds(view)).toHaveLength(0);expect(f.requests.filter(r=>r.path==='/api/reservations'&&r.method==='GET').at(-1)?.query.cursor).toBeUndefined()
  }
  const denied=await visit('/reservations?reservationId=77777777-7777-4777-8777-777777777777');await ready(denied);await textIncludes(denied,'[data-testid="reservation-detail-error"]','');expect(await denied.evaluate(()=>!!document.querySelector('[data-testid="reservation-detail-id"]'))).toBe(false);break
 }
 case 'M1-08.S02': {
  await detail(p);await p.click('[data-testid="reservation-rebook"]');await p.waitFor(()=>!!document.querySelector('[data-testid="reservation-rebook-prefill"]'));expect(post(f)).toHaveLength(0);expect(await p.evaluate(()=> (document.getElementById('reservation-start') as HTMLInputElement).value)).toBe('');expect(await p.text('[data-testid="reservation-rebook-prefill"]')).toContain('3');await range(p,'2030-06-05T09:00','2030-06-05T12:00');await create(p);expect(await p.evaluate(()=>!!document.querySelector('[data-testid="reservation-write-error"]'))).toBe(true);expect(post(f).at(-1)!.body.operationId).not.toBe(ID);expect(f.rows).toHaveLength(1);expect(f.rows[0].status).toBe('COMPLETED');break
 }
 case 'M1-08.S03': {
  expect(await p.text('[data-testid="reservation-history-list"]')).toContain('2030-06-04');await input(p,'reservation-history-from','2030-06-04T00:00');await input(p,'reservation-history-to','2030-06-05T00:00');await p.click('[data-testid="reservation-history-search"]');await waitHistory(p);expect(Date.parse(f.requests.filter(r=>r.method==='GET'&&r.path==='/api/reservations').at(-1)!.query.from)).toBe(Date.parse('2030-06-03T16:00:00Z'));await detail(p);expect(await p.text('[data-testid="reservation-detail"]')).toContain('本人取消');f.rows=[row('44444444-4444-4444-8444-444444444402',{roomId:null,roomName:null,roomCode:null,seatCode:null,metadataAvailable:false,policySnapshot:{},checkinDeadlineAt:null,status:'COMPLETED',resourceState:'LEGACY'})];const legacy=await visit();await ready(legacy);await detail(legacy,f.rows[0].id);expect(await legacy.text('[data-testid="reservation-detail"]')).toContain('资料不完整');expect(await legacy.text('[data-testid="reservation-detail"]')).not.toContain('R101');f.rows=[];const empty=await visit();await ready(empty);expect(await empty.text('[data-testid="reservation-history"]')).toContain('本页0条');expect(await historyIds(empty)).toHaveLength(0);await language(empty);expect(await empty.text('[data-testid="reservation-history"]')).toContain('0 items on this page');break
 }
 case 'M1-08.S04': {
  await input(p,'reservation-history-room',R1);await input(p,'reservation-history-seat',S1);await input(p,'reservation-history-from','2030-06-03T00:00');await input(p,'reservation-history-to','2030-06-04T00:00');await p.click('[data-testid="reservation-history-search"]');await waitHistory(p);expect(await historyIds(p)).toEqual([ID]);await detail(p);expect(await p.text('[data-testid="reservation-detail"]')).toContain(U1);expect(f.requests.some(r=>r.method==='GET'&&r.path==='/api/reservations/'+ID)).toBe(false);f.management=false;f.currentUser=U1;f.permissions=[...ordinary];const reads=f.requests.filter(r=>r.path==='/api/admin/reservations').length;const denied=await visit('/manage/reservations','/forbidden');expect(await denied.evaluate(()=>!!document.querySelector('[data-reservations-page]'))).toBe(false);expect(f.requests.filter(r=>r.path==='/api/admin/reservations')).toHaveLength(reads);break
 }
 default:throw new Error('Unknown scene '+id)
 }
}
async function pSafeAdminCancel(p:BrowserPage){await p.click('[data-testid="reservation-admin-cancel"]');await command(p);expect(await p.evaluate(()=>!!document.querySelector('[data-testid="reservation-write-error"]'))).toBe(true);await input(p,'reservation-admin-reason','日常维护');await p.click('[data-testid="reservation-admin-cancel"]');await command(p);await state(p,'已取消')}
