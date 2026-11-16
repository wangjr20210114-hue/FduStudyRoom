import { afterEach, describe, expect, it } from 'vitest'
import * as F from './fixture'
let f:Awaited<ReturnType<typeof F.fixture>>
afterEach(async()=>{if(f)await f.close()},60000)
describe('M3-03',()=>{

 it('starting repair waits for real resource acknowledgement before showing in progress',async()=>{
  f=await F.fixture();f.state.rows=[F.order({status:'ASSIGNED',technicianId:F.worker1,version:2})];f.state.user=F.user(F.worker1,['identity:read','repair:history:read','repair:maintain'])
  const p=await f.open('/maintenance/tasks');await F.detail(p);await F.click(p,'[data-test=start]');await F.waitText(p,'等待维修限制生效')
  expect(F.posts(f,'/start')[0]?.body).toMatchObject({version:2});expect(await F.count(p,'[data-test=restriction-confirmed]')).toBe(0);expect(await F.count(p,'[data-test=finish]')).toBe(0)
 },60000)
 it('confirmed desired and applied versions show this work order restriction source',async()=>{
  f=await F.fixture();f.state.rows=[F.active()];const p=await f.open();await F.detail(p)
  expect(await F.count(p,'[data-test=restriction-confirmed]')).toBe(1);expect(await F.text(p)).toContain('M3 / '+F.order1);expect(await p.text('[data-test=order-status]')).toBe('维修中')
  f.state.rows[0].appliedVersion=2;await F.click(p,'[data-test=refresh]');await p.waitFor("document.querySelector('[data-test=restriction-confirmed]')===null");expect(await F.count(p,'[data-test=restriction-confirmed]')).toBe(0)
 },60000)
 it('resource dependency errors stay pending and retry never calls internal resource APIs',async()=>{
  f=await F.fixture();f.state.rows=[F.active({status:'RESTRICTION_PENDING',appliedVersion:0,appliedActive:false,resourceError:'REPAIR_DEPENDENCY_UNAVAILABLE'})];const p=await f.open();await F.detail(p)
  expect(await F.text(p)).toContain('等待维修限制生效');expect(await F.text(p)).toContain('REPAIR_DEPENDENCY_UNAVAILABLE');expect(await F.count(p,'[data-test=restriction-confirmed]')).toBe(0)
  await F.click(p,'[data-test=refresh]');expect(f.state.calls.some(c=>c.path.startsWith('/internal/'))).toBe(false);expect(F.posts(f,'/start')).toHaveLength(0)
  f.ack();await F.click(p,'[data-test=refresh]');await F.waitText(p,'维修中');expect(await F.count(p,'[data-test=restriction-confirmed]')).toBe(1)
 },60000)
 it('maintenance impact warning does not claim reservation termination or delivered notices',async()=>{
  f=await F.fixture();f.state.rows=[F.order({status:'ASSIGNED',technicianId:F.worker1,version:2})];f.state.user=F.user(F.worker1,['identity:read','repair:history:read','repair:maintain'])
  const p=await f.open('/maintenance/tasks');await F.detail(p);expect(await F.text(p)).toContain('不记违约');expect(await F.text(p)).toContain('整室申请')
  await F.click(p,'[data-test=start]');await F.waitText(p,'等待维修限制生效');expect(await F.text(p)).not.toContain('预约已全部取消');expect(await F.text(p)).not.toContain('邮件已发送')
  expect(f.state.calls.every(c=>!c.path.startsWith('/api/reservations')&&!c.path.startsWith('/internal/'))).toBe(true)
 },60000)
 it('late lower version detail cannot remove a newer repair restriction',async()=>{
  f=await F.fixture();f.state.rows=[F.active({version:7})];const p=await f.open();await F.detail(p);f.state.delayNextDetail=true;await F.click(p,'[data-test=refresh]')
  f.state.rows[0]=F.active({version:9,desiredVersion:9,appliedVersion:9});await F.click(p,'[data-test=refresh]');await F.waitText(p,'版本 9');await new Promise(r=>setTimeout(r,400))
  expect(await p.text('[data-test=repair-detail]')).toContain('版本 9');expect(await F.count(p,'[data-test=restriction-confirmed]')).toBe(1)
 },60000)
})
