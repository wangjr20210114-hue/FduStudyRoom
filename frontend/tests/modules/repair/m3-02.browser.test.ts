import { afterEach, describe, expect, it } from 'vitest'
import * as F from './fixture'
let f:Awaited<ReturnType<typeof F.fixture>>
afterEach(async()=>{if(f)await f.close()},60000)
describe('M3-02',()=>{

 it('candidate assignment snapshots deadline and transfer preserves dueAt and history',async()=>{
  f=await F.fixture();f.state.user=F.user(F.admin1);const p=await f.open('/manage/repairs');await F.detail(p);await F.click(p,'[data-tech="'+F.worker1+'"]');await F.click(p,'[data-test=assign]');await F.waitText(p,'已派单')
  expect(F.posts(f,'/assignment')[0]?.body).toMatchObject({version:1,technicianId:F.worker1});expect(F.posts(f,'/assignment')[0]?.body).not.toHaveProperty('dueAt')
  await F.click(p,'[data-tech="'+F.worker2+'"]');await F.click(p,'[data-test=assign]');await F.waitText(p,'转派')
  expect(f.state.rows[0].dueAt).toBe('2030-06-03T10:00:00+08:00');expect(await p.text('[data-test=history]')).toContain('转派');expect(f.history(F.order1).filter(e=>['ASSIGN','REASSIGN'].includes(e.eventType))).toHaveLength(2)
 },60000)
 it('disabled or no longer eligible candidates never become assigned',async()=>{
  f=await F.fixture();f.state.user=F.user(F.admin1);f.state.candidates=[{userId:F.worker1,displayName:'Worker One'}];const p=await f.open('/manage/repairs');await F.detail(p)
  expect(await F.count(p,'[data-tech="'+F.worker2+'"]')).toBe(0);await F.click(p,'[data-tech="'+F.worker1+'"]');f.state.invalidTechnician=true;await F.click(p,'[data-test=assign]');await F.waitText(p,'维修人员当前不可用')
  expect(f.state.rows[0].technicianId).toBeNull();expect(await p.text('[data-test=order-status]')).toBe('待派单')
 },60000)
 it('only actual assignee can work and transfer removes old worker actions',async()=>{
  f=await F.fixture();f.state.rows=[F.order({technicianId:F.worker1,status:'ASSIGNED',version:2})];f.state.user=F.user(F.worker2,['identity:read','repair:history:read','repair:maintain']);let p=await f.open('/maintenance/tasks')
  expect(await F.count(p,'[data-open="'+F.order1+'"]')).toBe(0);expect(F.posts(f,'/start')).toHaveLength(0)
  f.state.user=F.user(F.worker1,['identity:read','repair:history:read','repair:maintain']);p=await f.open('/maintenance/tasks');await F.detail(p);expect(await F.count(p,'[data-test=start]')).toBe(1)
  f.state.rows[0].technicianId=F.worker2;f.state.rows[0].version++;await F.click(p,'[data-test=refresh]');await F.waitText(p,'无权查看此工单');expect(await F.count(p,'[data-test=start]')).toBe(0)
 },60000)
 it('stale assignment version refreshes current owner without blind overwrite',async()=>{
  f=await F.fixture();f.state.user=F.user(F.admin1);f.state.rows=[F.order({status:'ASSIGNED',technicianId:F.worker1,version:4})];const p=await f.open('/manage/repairs');await F.detail(p);await F.click(p,'[data-tech="'+F.worker1+'"]');f.state.conflictAction='assignment';await F.click(p,'[data-test=assign]')
  await F.waitText(p,'资料已变化，请重新确认');expect(F.posts(f,'/assignment')).toHaveLength(1);expect(F.posts(f,'/assignment')[0]?.body.version).toBe(4);expect(f.state.rows[0].technicianId).toBe(F.worker2)
  await expect.poll(()=>p.text('[data-test=current-technician]')).toBe(F.worker2);expect(f.history(F.order1).filter(e=>e.eventType==='REASSIGN')).toHaveLength(1)
 },60000)
 it('uncertain assignment retains original operation technician and version',async()=>{
  f=await F.fixture();f.state.user=F.user(F.admin1);f.state.unknownAction='assignment';const p=await f.open('/manage/repairs');await F.detail(p);await F.click(p,'[data-tech="'+F.worker1+'"]');await F.click(p,'[data-test=assign]');await F.waitText(p,'结果尚未确认')
  await F.click(p,'[data-test=retry-command]');await F.waitText(p,'已派单');expect(F.posts(f,'/assignment')).toHaveLength(2);expect(F.posts(f,'/assignment')[1]?.body).toEqual(F.posts(f,'/assignment')[0]?.body)
  expect(f.history(F.order1).filter(e=>e.eventType==='ASSIGN')).toHaveLength(1)
 },60000)
})
