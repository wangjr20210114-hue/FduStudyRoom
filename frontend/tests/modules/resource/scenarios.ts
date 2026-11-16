import { expect } from 'vitest'
import { Fixture, loaded, ready, select, input, query, language, saveRoom, R1,R2,S1,S2,userPermissions,type BrowserPage } from './fixture'
type Visit=(path?:string,expected?:string)=>Promise<BrowserPage>
const result='[data-testid="resource-availability-results"]'
async function absent(p:BrowserPage,code='A01'){await p.waitFor(`!document.querySelector(${JSON.stringify(result)})?.textContent?.includes(${JSON.stringify(code)})`);expect(await p.text(result)).not.toContain(code)}
async function present(p:BrowserPage,code='A01'){await p.waitFor(`document.querySelector(${JSON.stringify(result)})?.textContent?.includes(${JSON.stringify(code)})`)}
async function noLinks(p:BrowserPage){expect(await p.evaluate(()=>document.querySelectorAll('[data-testid^="resource-reserve-link-"]').length)).toBe(0)}
export async function scenario(id:string,f:Fixture,visit:Visit){
 if(id==='RES-01.S01')f.rooms=[],f.seats=[]
 if(['RES-03.S02','RES-04.S08'].includes(id))f.rooms[0].departmentCode='CS',f.departmentMatched=false
 if(id==='RES-03.S03')f.departmentMatched=false
 if(['RES-04.S01','RES-04.S02','RES-04.S04','RES-04.S06','RES-04.S07','RES-04.S08','RES-05.S01','RES-05.S02','RES-05.S03','RES-05.S04','RES-05.S05'].includes(id))f.availabilityIds=[]
 if(id==='RES-04.S05')f.availabilityIds=[S2]
 if(['RES-04.S09','RES-05.S06'].includes(id))f.availabilityIds=[S1]
 if(id==='RES-04.S03')f.rooms[0].enabled=false
 const manage=id.startsWith('RES-01')||id.startsWith('RES-02')||['RES-04.S07','RES-05.S04'].includes(id)
 const p=await visit(id.startsWith('RES-07')?'/rooms/'+R1+'/checkin-code':manage?'/manage/rooms':'/rooms')
 if(id.startsWith('RES-07'))await ready(p,'resource-code-display');else await loaded(p)
 switch(id){
 case 'RES-01.S01': {
  await p.click('[data-testid="resource-room-new"]');await ready(p,'resource-room-form');f.writeDelay=150;await saveRoom(p);await p.waitFor(()=>document.querySelector('[data-testid="resource-room-detail"]')?.textContent?.includes('R101'));expect(f.rooms).toHaveLength(1);expect(f.rooms[0]).toMatchObject({code:'R101',capacity:2,campus:'邯郸',building:'教学楼A'});await language(p);expect(await p.text('[data-testid="resource-room-new"]')).toBe('Add room');await p.close();f.permissions=[...userPermissions];const denied=await visit('/manage/rooms','/forbidden');expect(await denied.evaluate(()=>!!document.querySelector('[data-testid="resource-room-form"]'))).toBe(false);expect(f.requests.filter(x=>x.method==='POST'&&x.path==='/api/resources/rooms')).toHaveLength(1);break
 }
 case 'RES-01.S02':
  await p.click('[data-testid="resource-room-disable"]');await p.waitFor(()=>document.querySelector('[data-testid="resource-room-detail"] .app-status-tag')?.textContent?.trim()==='停用');expect(f.rooms[0].enabled).toBe(false);expect(f.rooms).toHaveLength(2);expect(f.rooms[1].enabled).toBe(true);expect(f.requests.some(x=>x.method==='DELETE')).toBe(false);await query(p);await absent(p);break
 case 'RES-01.S03':
  await p.click('[data-testid="resource-room-edit"]');await input(p,'resource-room-capacity','0');await p.click('[data-testid="resource-room-save"]');expect(f.rooms[0].capacity).toBe(4);expect(f.requests.filter(x=>x.method==='PUT')).toHaveLength(0);await input(p,'resource-room-capacity','2');f.rooms[0].version=4;await p.click('[data-testid="resource-room-save"]');await p.waitFor(()=>!!document.querySelector('[data-testid="resource-room-error"]'));expect(await p.text('[data-testid="resource-room-error"]')).toContain('资料');expect(f.rooms[0]).toMatchObject({version:4,capacity:4});expect(f.requests.filter(x=>x.method==='PUT').at(-1)?.body.expectedVersion).toBe(3);break
 case 'RES-02.S01':
  await p.click('[data-testid="resource-seat-new"]');await p.fill('#resource-seat-code','A01');await p.click('[data-testid="resource-seat-save"]');await p.waitFor(()=>!!document.querySelector('[data-testid="resource-seat-error"]'));expect(f.seats.filter(s=>s.roomId===R1&&s.code==='A01')).toHaveLength(1);await select(p,'resource-room-select',R2);await p.waitFor(()=>document.querySelector('[data-testid="resource-seat-list"]')?.textContent?.includes('B01'));await p.click('[data-testid="resource-seat-new"]');await p.fill('#resource-seat-code','A01');await p.click('[data-testid="resource-seat-save"]');await p.waitFor(()=>document.querySelector('[data-testid="resource-seat-list"]')?.textContent?.includes('A01'));expect(f.seats.filter(s=>s.code==='A01')).toHaveLength(2);expect(f.seats.find(s=>s.roomId===R2&&s.code==='A01')).toBeTruthy();break
 case 'RES-02.S02':
  await select(p,'resource-socket-filter','true');await p.waitFor(()=>document.querySelector('[data-testid="resource-seat-list"]')?.textContent?.includes('A01')&&!document.querySelector('[data-testid="resource-seat-list"]')?.textContent?.includes('A02'));expect(f.requests.filter(x=>x.path.endsWith('/seats')).at(-1)?.query.hasSocket).toBe('true');await select(p,'resource-socket-filter','false');await p.waitFor(()=>document.querySelector('[data-testid="resource-seat-list"]')?.textContent?.includes('A02')&&!document.querySelector('[data-testid="resource-seat-list"]')?.textContent?.includes('A01'));await language(p);expect(await p.text('[data-testid="resource-seat-list"]')).toContain('None');break
 case 'RES-02.S03':
  await p.click('[data-testid="resource-seat-toggle-'+S1+'"]');await p.waitFor(()=>document.querySelector('[data-testid="resource-seat-toggle-22222222-2222-4222-8222-222222222201"]')?.textContent?.trim()==='启用');expect(f.seats[0].enabled).toBe(false);expect(f.seats[1].enabled).toBe(true);expect(f.seats[0].code).toBe('A01');await query(p);await absent(p);break
 case 'RES-03.S01':
  expect(await p.text('[data-testid="resource-room-detail"]')).toContain('08:00');expect(await p.text('[data-testid="resource-room-detail"]')).toContain('22:00');for(const [start,end] of [['08:00','09:00'],['21:00','22:00']]){await query(p,'2030-06-03T'+start,'2030-06-03T'+end);await present(p)}await query(p,'2030-06-03T22:00','2030-06-03T23:00');await absent(p);expect(new Date(f.requests.filter(x=>x.path.endsWith('/availability')).at(-1)!.query.startsAt).getUTCHours()).toBe(14);break
 case 'RES-03.S02':
  expect(await p.text('[data-testid="resource-room-detail"]')).toContain('CS');await query(p);await absent(p);f.departmentMatched=true;await query(p);await present(p);expect(f.requests.filter(x=>x.path.endsWith('/availability')).every(x=>!('departmentCode' in x.query)&&!('userId' in x.query))).toBe(true);break
 case 'RES-03.S03':
  expect(await p.text('[data-testid="resource-room-detail"]')).toContain('公共');expect(await p.evaluate(()=>!!document.getElementById('resource-room-department'))).toBe(false);await query(p);await present(p);await language(p);expect(await p.text('[data-testid="resource-room-detail"]')).toContain('Public');break
 case 'RES-04.S01':
  await query(p);await absent(p);f.availabilityIds=[S1];await query(p);await p.waitFor(()=>!!document.querySelector('[data-testid="resource-reserve-link-22222222-2222-4222-8222-222222222201"]'));await p.click('[data-testid="resource-reserve-link-'+S1+'"]');await p.waitFor(()=>location.pathname==='/reservations');expect(await p.evaluate(()=>new URL(location.href).searchParams.get('seatId'))).toBe(S1);expect(f.requests.some(x=>x.path.startsWith('/internal/'))).toBe(false);break
 case 'RES-04.S02':
  await query(p);await absent(p,'A02');f.availabilityIds=[S2];await query(p,'2030-06-03T10:00','2030-06-03T11:00');await present(p,'A02');break
 case 'RES-04.S03':await query(p);await absent(p);await noLinks(p);expect(f.requests.some(x=>x.method==='POST'&&x.path.includes('/allocations'))).toBe(false);break
 case 'RES-04.S04':await query(p);await absent(p);await absent(p,'A02');await ready(p,'resource-request-link');await p.click('[data-testid="resource-request-link"]');await p.waitFor(()=>location.pathname==='/room-requests');break
 case 'RES-04.S05':await query(p);await present(p,'A02');await absent(p);expect(await p.evaluate(()=>!!document.querySelector('[data-testid="resource-reserve-link-22222222-2222-4222-8222-222222222201"]'))).toBe(false);break
 case 'RES-04.S06':await query(p);await noLinks(p);f.availabilityIds=[S1,S2];await query(p);await p.waitFor(()=>document.querySelectorAll('[data-testid^="resource-reserve-link-"]').length===2);expect(f.requests.some(x=>x.method!=='GET'&&x.path.includes('/reservations'))).toBe(false);break
 case 'RES-04.S07':case 'RES-05.S04':
  await p.click('[data-testid="resource-seat-new"]');await p.fill('#resource-seat-code','A04');await p.click('[data-testid="resource-seat-save"]');await p.waitFor(()=>document.querySelector('[data-testid="resource-seat-list"]')?.textContent?.includes('A04'));await query(p);await noLinks(p);expect(f.seats.find(x=>x.code==='A04')?.enabled).toBe(true);break
 case 'RES-04.S08':case 'RES-05.S05':
  await query(p);await noLinks(p);expect(f.requests.filter(x=>x.path.endsWith('/availability')).at(-1)?.query.roomId).toBe(R1);expect(f.requests.filter(x=>x.path.endsWith('/availability')).every(x=>!x.headers['x-service-key']&&!('userId' in x.query))).toBe(true);break
 case 'RES-04.S09':case 'RES-05.S06':
  await query(p,'2030-06-03T10:00','2030-06-03T11:00');await present(p);f.availabilityIds=[];await query(p,'2030-06-03T10:00','2030-06-03T11:00');await absent(p);await language(p);expect(await p.text(result)).toContain('No available seats');break
 case 'RES-05.S01':await query(p);await absent(p);await absent(p,'A02');await ready(p,'resource-repair-link');await p.click('[data-testid="resource-repair-link"]');await p.waitFor(()=>location.pathname==='/repairs');break
 case 'RES-05.S02':await query(p);await query(p);await noLinks(p);expect(f.rooms[0].enabled).toBe(true);f.rooms[0].enabled=false;await query(p);await noLinks(p);break
 case 'RES-05.S03':await query(p);await query(p);await query(p);await noLinks(p);expect(f.requests.filter(x=>x.path.includes('/restrictions')||x.path.startsWith('/internal/'))).toHaveLength(0);break
 case 'RES-07.S01': {
  await p.waitFor(()=>document.querySelector('[data-testid="resource-code-room"]')?.textContent==='R101');const first=await p.text('[data-testid="resource-code-value"]');expect(first).toMatch(/^\d{6}$/);await p.goto(f.origin+'/rooms/'+R2+'/checkin-code');await p.waitFor(()=>document.querySelector('[data-testid="resource-code-room"]')?.textContent==='R102');expect(await p.text('[data-testid="resource-code-value"]')).not.toBe(first);const reads=f.codeReads;await p.close();f.permissions=[...userPermissions];const denied=await visit('/rooms/'+R1+'/checkin-code','/forbidden');expect(await denied.evaluate(()=>!!document.querySelector('[data-testid="resource-code-value"]'))).toBe(false);expect(f.codeReads).toBe(reads);break
 }
 case 'RES-07.S02':
  await p.waitFor(()=>!!document.querySelector('[data-testid="resource-code-value"]')?.textContent);f.codeDate='2030-06-03';await p.evaluate(()=>{const RealDate=Date;(window as any).__resourceNow=RealDate.parse('2030-06-03T15:59:59Z');function MockDate(...args:any[]){return new (RealDate as any)(...(args.length?args:[(window as any).__resourceNow]))};Object.setPrototypeOf(MockDate,RealDate);MockDate.prototype=RealDate.prototype;MockDate.now=()=>(window as any).__resourceNow;(window as any).Date=MockDate});await p.click('[data-testid="resource-code-refresh"]');await p.waitFor(()=>document.querySelector('[data-testid="resource-code-date"]')?.textContent==='2030-06-03');f.codeDate='2030-06-04';await p.evaluate(()=>{(window as any).__resourceNow=Date.parse('2030-06-03T16:00:00Z')});await p.waitFor(()=>document.querySelector('[data-testid="resource-code-date"]')?.textContent==='2030-06-04',{timeoutMs:8000});expect(await p.text('[data-testid="resource-code-value"]')).toBe('410604');break
 case 'RES-07.S03': {
  await p.waitFor(()=>!!document.querySelector('[data-testid="resource-code-value"]')?.textContent);const first=await p.text('[data-testid="resource-code-value"]');for(let i=0;i<2;i++){await p.click('[data-testid="resource-code-refresh"]');await p.waitFor(`document.querySelector('[data-testid="resource-code-value"]')?.textContent===${JSON.stringify(first)}`)}expect(f.codeReads).toBeGreaterThanOrEqual(3);expect(await p.evaluate(()=>JSON.stringify(localStorage))).not.toContain(first);expect(f.requests.some(x=>x.path.startsWith('/internal/')||x.headers['x-service-key'])).toBe(false);await language(p);expect(await p.text('[data-testid="resource-code-refresh"]')).toBe('Refresh');break
 }
 default:throw new Error('Unknown scenario '+id)
 }
}
