import { afterEach, describe, expect, it } from 'vitest'
import * as F from './fixture'
let f:Awaited<ReturnType<typeof F.fixture>>
afterEach(async()=>{if(f)await f.close()},60000)
describe('M3-04',()=>{

 it('deadline alone never fabricates escalation or sends client upgrade writes',async()=>{
  f=await F.fixture();f.state.rows=[F.active({dueAt:'2020-01-01T10:00:00+08:00',assignedAt:'2020-01-01T08:00:00+08:00'})];const p=await f.open();await F.detail(p)
  expect(await F.text(p)).toContain('2020/01/01 10:00');expect(await F.count(p,'[data-test=escalated]')).toBe(0);expect(await F.text(p)).toContain('已超时，等待系统处理')
  expect(f.state.calls.filter(c=>c.method==='POST')).toHaveLength(0)
 },60000)
 it('recorded level one escalation retains actor time owner and one history row across refresh',async()=>{
  f=await F.fixture();f.state.rows=[F.active({escalationLevel:1,version:5})];f.state.histories.set(F.order1,[F.event(),F.event({actorId:null,eventType:'ESCALATE',fromStatus:'IN_PROGRESS',toStatus:'IN_PROGRESS',assignedTo:F.worker1,note:'SLA_EXCEEDED',createdAt:'2030-06-03T10:00:00+08:00',orderVersion:5})])
  const p=await f.open();await F.detail(p);expect(await F.count(p,'[data-test=escalated]')).toBe(1);expect(await p.text('[data-test=history]')).toContain('2030/06/03 10:00');expect(await F.text(p)).toContain(F.worker1)
  await F.click(p,'[data-test=refresh]');await F.click(p,'[data-test=refresh]');expect(f.history(F.order1).filter(e=>e.eventType==='ESCALATE')).toHaveLength(1);expect(await F.count(p,'[data-test=escalated]')).toBe(1);expect(F.posts(f,'/assignment')).toHaveLength(0)
 },60000)
 it('reassignment displays original SLA deadline without accepting a client extension',async()=>{
  f=await F.fixture();f.state.user=F.user(F.admin1);f.state.rows=[F.active()];const p=await f.open('/manage/repairs');await F.detail(p);await F.click(p,'[data-tech="'+F.worker2+'"]');await F.click(p,'[data-test=assign]');await F.waitText(p,'转派')
  expect(f.state.rows[0].dueAt).toBe('2030-06-03T10:00:00+08:00');expect(F.posts(f,'/assignment')[0]?.body).not.toHaveProperty('slaMinutes');expect(await F.count(p,'[name=dueAt], [name=slaMinutes]')).toBe(0);expect(await F.text(p)).toContain('2030/06/03 10:00')
 },60000)
 it('completed and safely cancelled orders do not claim active overdue work',async()=>{
  f=await F.fixture();f.state.rows=[F.completed({dueAt:'2020-01-01T10:00:00+08:00'}),F.active({id:F.order2,status:'CANCELLED',dueAt:'2020-01-01T10:00:00+08:00',desiredActive:false,appliedActive:false,desiredVersion:6,appliedVersion:6})]
  const p=await f.open();await F.detail(p);expect(await F.count(p,'[data-test=deadline-overdue]')).toBe(0)
  await F.detail(p,F.order2);expect(await F.count(p,'[data-test=deadline-overdue]')).toBe(0);expect(await F.count(p,'[data-test=escalated]')).toBe(0);expect(await F.count(p,'[data-test=history]')).toBe(1)
 },60000)
})
