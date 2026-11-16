import { afterEach, describe, expect, it } from 'vitest'
import * as F from './fixture'
let f:Awaited<ReturnType<typeof F.fixture>>
afterEach(async()=>{if(f)await f.close()},60000)
describe('M3-01',()=>{

 it('report actual room and optional seat stays submitted without body identity',async()=>{
  f=await F.fixture();f.state.rows=[];const p=await f.open();await F.reportForm(p);await F.click(p,'[data-test=submit-report]');await F.waitText(p,'待派单')
  const call=F.posts(f,'/repairs')[0]!;expect(call.body).toMatchObject({roomId:F.room1,seatId:F.seat1,description:'插座损坏'});expect(Object.keys(call.body).sort()).toEqual(['description','operationId','roomId','seatId'])
  expect(call.csrf).toBe('fixture-csrf');expect(call.cookie).toBe(true);expect(call.serviceKey).toBeUndefined();expect(await F.count(p,'[data-test=restriction-confirmed]')).toBe(0)
 },60000)
 it('invalid description room and cross room seats never create a work order',async()=>{
  f=await F.fixture();const p=await f.open();await F.reportForm(p);await p.fill('[data-test=description]',' ');await F.click(p,'[data-test=submit-report]');await F.waitText(p,'请填写故障说明')
  await p.fill('[data-test=description]','x'.repeat(4001));await F.click(p,'[data-test=submit-report]');expect(F.posts(f,'/repairs')).toHaveLength(0)
  await p.fill('[data-test=description]','桌面损坏');await F.click(p,'[data-room="'+F.room2+'"]');await p.waitFor('!!document.querySelector(\'[data-seat="'+F.seat2+'"]\')');expect(await F.count(p,'[data-seat="'+F.seat1+'"]')).toBe(0)
  await F.click(p,'[data-test=submit-report]');await expect.poll(()=>F.posts(f,'/repairs').length).toBe(1);expect(F.posts(f,'/repairs')[0]?.body).toMatchObject({roomId:F.room2});expect(F.posts(f,'/repairs')[0]?.body).not.toHaveProperty('seatId')
 },60000)
 it('report only account creates without forbidden history queries and role names grant nothing',async()=>{
  f=await F.fixture();f.state.user=F.user(F.reporter,['identity:read','resource:rooms:read','repair:report']);let p=await f.open();await F.reportForm(p,false);await F.english(p);await F.click(p,'[data-test=submit-report]');await F.waitText(p,'Submitted')
  expect(f.state.calls.some(c=>c.method==='GET'&&c.path.startsWith('/api/repairs'))).toBe(false);expect(F.posts(f,'/repairs')[0]?.body).not.toHaveProperty('seatId');expect(F.posts(f,'/repairs')[0]?.locale).toBe('en')
  f.state.user=F.user(F.reporter,['identity:read','repair:history:read'],'SYSTEM_ADMIN');p=await f.open();expect(await F.count(p,'[data-test=new-repair]')).toBe(0)
 },60000)
 it('own and dispatch lists reject foreign detail and clear expired private content',async()=>{
  f=await F.fixture();f.state.rows.push(F.order({id:F.order2,reporterId:F.admin2,description:'PRIVATE_OTHER'}));f.state.user=F.user(F.reporter,['identity:read','repair:history:read'])
  let p=await f.open();expect(await F.count(p,'[data-open="'+F.order2+'"]')).toBe(0);await F.detail(p)
  f.state.failDetail=403;await F.click(p,'[data-test=refresh]');await F.waitText(p,'无权查看此工单');expect(await F.count(p,'[data-test=repair-detail]')).toBe(0);expect(await F.text(p)).not.toContain('插座损坏')
  f.state.failDetail=0;p=await f.open('/repairs?orderId='+F.order2);await F.waitText(p,'无权查看此工单');expect(await F.text(p)).not.toContain('PRIVATE_OTHER')
  f.state.user=F.user(F.admin1);p=await f.open('/manage/repairs');await F.detail(p,F.order2);expect(await F.text(p)).toContain('PRIVATE_OTHER')
  f.state.failDetail=401;await F.click(p,'[data-test=refresh]');await expect.poll(()=>p.evaluate('location.pathname'),{timeout:5000}).toBe('/login');expect(await F.text(p)).not.toContain('PRIVATE_OTHER')
 },60000)
 it('uncertain report retries frozen original operation and payload without duplicates',async()=>{
  f=await F.fixture();f.state.rows=[];f.state.unknownAction='create';const p=await f.open();await F.reportForm(p);await F.click(p,'[data-test=submit-report]');await F.waitText(p,'结果尚未确认')
  expect(await p.evaluate("document.querySelector('[data-test=description]').disabled")).toBe(true);await F.click(p,'[data-test=retry-create]');await F.waitText(p,'待派单')
  expect(F.posts(f,'/repairs')).toHaveLength(2);expect(F.posts(f,'/repairs')[1]?.body).toEqual(F.posts(f,'/repairs')[0]?.body);expect(f.state.rows).toHaveLength(1)
 },60000)
})
