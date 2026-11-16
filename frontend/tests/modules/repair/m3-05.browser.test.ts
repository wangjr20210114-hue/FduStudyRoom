import { afterEach, describe, expect, it } from 'vitest'
import * as F from './fixture'
let f:Awaited<ReturnType<typeof F.fixture>>
afterEach(async()=>{if(f)await f.close()},60000)
describe('M3-05',()=>{

 it('assigned worker completion requires report and keeps restriction pending inspection',async()=>{
  f=await F.fixture();f.state.rows=[F.active()];f.state.user=F.user(F.worker1,['identity:read','repair:history:read','repair:maintain']);const p=await f.open('/maintenance/tasks');await F.detail(p)
  await F.click(p,'[data-test=finish]');await F.waitText(p,'请填写维修报告');expect(F.posts(f,'/finish')).toHaveLength(0)
  await p.fill('[data-test=repair-report]','已更换插座');await F.click(p,'[data-test=finish]');await F.waitText(p,'待验收');expect(f.state.rows[0].appliedActive).toBe(true);expect(await F.count(p,'[data-test=source-released]')).toBe(0)
 },60000)
 it('current and historical actual workers cannot inspect even with accept permission',async()=>{
  f=await F.fixture();f.state.rows=[F.active({status:'PENDING_ACCEPTANCE',version:5,repairReport:'已更换插座'})];f.state.user=F.user(F.worker1);let p=await f.open('/manage/repairs');await F.detail(p)
  expect(await F.count(p,'[data-test=accept]')).toBe(0)
  f.state.rows[0].technicianId=F.worker2;f.state.histories.set(F.order1,[F.event(),F.event({actorId:F.worker1,eventType:'START',fromStatus:'ASSIGNED',toStatus:'RESTRICTION_PENDING',createdAt:'2030-06-03T08:20:00+08:00',orderVersion:3})])
  p=await f.open('/manage/repairs');await F.detail(p);expect(await F.count(p,'[data-test=accept]')).toBe(0);expect(F.posts(f,'/acceptance')).toHaveLength(0)
 },60000)
 it('independent rejection requires comment and empty ACK refresh confirms continued repair',async()=>{
  f=await F.fixture();f.state.rows=[F.active({status:'PENDING_ACCEPTANCE',version:5,repairReport:'已更换插座'})];f.state.user=F.user(F.admin2);const p=await f.open('/manage/repairs');await F.detail(p)
  await F.click(p,'[data-test=return-repair]');await F.waitText(p,'请填写验收意见');expect(F.posts(f,'/acceptance')).toHaveLength(0)
  await p.fill('[data-test=inspection-comment]','插座仍松动');await F.click(p,'[data-test=return-repair]');await F.waitText(p,'维修中')
  expect(F.posts(f,'/acceptance')[0]?.body).toMatchObject({version:5,approved:false,comment:'插座仍松动'});expect(f.state.rows[0].status).toBe('IN_PROGRESS');expect(await F.count(p,'[data-test=source-released]')).toBe(0);expect(await p.text('[data-test=history]')).toContain('插座仍松动')
 },60000)
 it('independent approval waits for restoration then only releases this source',async()=>{
  f=await F.fixture();f.state.rows=[F.active({status:'PENDING_ACCEPTANCE',version:5,repairReport:'已更换插座'}),F.active({id:F.order2})];f.state.user=F.user(F.admin2)
  const p=await f.open('/manage/repairs');await F.detail(p);await F.english(p);await p.fill('[data-test=inspection-comment]','安全可用');await F.click(p,'[data-test=accept]');await F.waitText(p,'Restoring restriction')
  expect(await F.count(p,'[data-test=repair-completed]')).toBe(0);expect(await F.count(p,'[data-test=source-released]')).toBe(0);expect(F.posts(f,'/acceptance')[0]?.locale).toBe('en');expect(F.posts(f,'/acceptance')[0]?.cookie).toBe(true)
  f.ack(F.order1,false);await F.click(p,'[data-test=refresh]');await F.waitText(p,'Completed');expect(await F.count(p,'[data-test=repair-completed]')).toBe(1);expect(await F.count(p,'[data-test=source-released]')).toBe(1);expect(f.state.rows[1].appliedActive).toBe(true)
  expect(await F.text(p)).not.toContain('All seats are available');expect(f.state.calls.some(c=>c.path.startsWith('/internal/'))).toBe(false)
 },60000)
 it('accept only queue departure clears detail after empty ACK without inventing completion',async()=>{
  f=await F.fixture();f.state.rows=[F.active({status:'PENDING_ACCEPTANCE',version:5,repairReport:'已更换插座'})];f.state.user=F.user(F.admin2,['identity:read','repair:history:read','repair:accept'])
  const p=await f.open('/manage/repairs');await F.detail(p);await p.fill('[data-test=inspection-comment]','请继续修理');await F.click(p,'[data-test=return-repair]');await F.waitText(p,'已离开当前可办理范围')
  expect(await F.count(p,'[data-test=repair-detail]')).toBe(0);expect(await F.text(p)).not.toContain('插座损坏');expect(await F.count(p,'[data-test=repair-completed]')).toBe(0);expect(F.posts(f,'/acceptance')).toHaveLength(1);expect(f.state.rows[0].status).toBe('IN_PROGRESS')
 },60000)
 it('contradictory or uncertain acceptance ACK retains original operation until public confirmation',async()=>{
  f=await F.fixture();f.state.rows=[F.active({status:'PENDING_ACCEPTANCE',version:5,repairReport:'已更换插座'})];f.state.user=F.user(F.admin2);f.state.contradictAcceptance=true
  let p=await f.open('/manage/repairs');await F.detail(p);await p.fill('[data-test=inspection-comment]','安全可用');await F.click(p,'[data-test=accept]');await F.waitText(p,'结果尚未确认')
  expect(await F.count(p,'[data-test=repair-completed]')).toBe(0);expect(f.state.rows[0].status).toBe('PENDING_ACCEPTANCE');await F.click(p,'[data-test=retry-command]');await F.waitText(p,'等待解除本工单限制');expect(F.posts(f,'/acceptance')[1]?.body).toEqual(F.posts(f,'/acceptance')[0]?.body)
  f.state.rows.push(F.active({id:F.order2,status:'PENDING_ACCEPTANCE',version:5,repairReport:'已修复'}));f.state.unknownAction='acceptance';p=await f.open('/manage/repairs?orderId='+F.order2);await p.waitFor("!!document.querySelector('[data-test=accept]')");await p.fill('[data-test=inspection-comment]','安全可用');await F.click(p,'[data-test=accept]');await F.waitText(p,'结果尚未确认');await F.click(p,'[data-test=retry-command]');await expect.poll(()=>p.text('[data-test=order-status]')).toBe('等待解除本工单限制')
  expect(F.posts(f,'/acceptance')[3]?.body).toEqual(F.posts(f,'/acceptance')[2]?.body);expect(f.history(F.order2).filter(e=>e.eventType==='ACCEPT')).toHaveLength(1)
 },60000)
})
