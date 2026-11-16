import { afterEach, describe, expect, it } from 'vitest'
import * as F from './fixture'
let f:Awaited<ReturnType<typeof F.fixture>>
afterEach(async()=>{if(f)await f.close()},60000)
describe('M3-07',()=>{

 it('reporter cancels unstarted order with reason and no invented resource release',async()=>{
  f=await F.fixture();f.state.user=F.user(F.reporter,['identity:read','repair:history:read']);const p=await f.open();await F.detail(p);await F.click(p,'[data-test=cancel]');await F.waitText(p,'请填写取消理由')
  expect(F.posts(f,'/cancel')).toHaveLength(0);await p.fill('[data-test=cancel-reason]','重复上报');await F.click(p,'[data-test=cancel]');await F.waitText(p,'已取消');expect(f.state.rows[0].desiredVersion).toBe(0);expect(await F.count(p,'[data-test=source-released]')).toBe(0)
  f.state.rows=[F.order({status:'ASSIGNED',technicianId:F.worker1,version:2})];f.state.user=F.user(F.worker2,['identity:read','repair:history:read','repair:assign'],'SYSTEM_ADMIN');const p2=await f.open('/manage/repairs');await F.detail(p2);expect(await F.count(p2,'[data-test=cancel]')).toBe(0)
 },60000)
 it('active cancellation waits for review and safety while restriction remains',async()=>{
  f=await F.fixture();f.state.rows=[F.active()];f.state.user=F.user(F.reporter,['identity:read','repair:history:read']);const p=await f.open();await F.detail(p);await p.fill('[data-test=cancel-reason]','申请停止处理');await F.click(p,'[data-test=cancel]');await F.waitText(p,'待取消审查')
  expect(f.state.rows[0].appliedActive).toBe(true);expect(await F.count(p,'[data-test=source-released]')).toBe(0);expect(await F.count(p,'[data-test=repair-cancelled]')).toBe(0);expect(F.posts(f,'/cancel-review')).toHaveLength(0)
 },60000)
 it('same independent administrator may review and safely inspect before confirmed cancellation',async()=>{
  f=await F.fixture();f.state.rows=[F.active({status:'CANCEL_REVIEW_PENDING',cancelReason:'停止处理',version:5}),F.active({id:F.order2})];f.state.user=F.user(F.admin2,['identity:read','repair:history:read','repair:admin-cancel','repair:accept']);let p=await f.open('/manage/repairs');await F.detail(p)
  await p.fill('[data-test=review-comment]','同意进入安全检查');await F.click(p,'[data-test=approve-cancel-review]');await F.waitText(p,'待安全验收');await p.fill('[data-test=safety-comment]','已确认安全');await F.click(p,'[data-test=approve-safety]');await F.waitText(p,'已离开当前可办理范围')
  expect(f.history(F.order1).filter(e=>['CANCEL_REVIEW','CANCEL_INSPECT'].includes(e.eventType)).map(e=>e.actorId)).toEqual([F.admin2,F.admin2]);expect(f.state.rows[0].status).toBe('RESTORATION_PENDING')
  f.state.user=F.user(F.reporter,['identity:read','repair:history:read']);p=await f.open();await F.detail(p);expect(await F.count(p,'[data-test=repair-cancelled]')).toBe(0);f.ack(F.order1,false);await F.click(p,'[data-test=refresh]');await F.waitText(p,'已取消');expect(await F.count(p,'[data-test=source-released]')).toBe(1);expect(f.state.rows[1].appliedActive).toBe(true)
 },60000)
 it('current or historical worker cannot self inspect and rejected cancellation resumes repair',async()=>{
  f=await F.fixture();f.state.rows=[F.active({status:'CANCEL_SAFETY_PENDING',version:6})];f.state.user=F.user(F.worker1);let p=await f.open('/manage/repairs');await F.detail(p);expect(await F.count(p,'[data-test=approve-safety]')).toBe(0)
  f.state.rows[0].technicianId=F.worker2;f.state.histories.set(F.order1,[F.event({actorId:F.worker1,eventType:'START',toStatus:'RESTRICTION_PENDING',orderVersion:3})]);p=await f.open('/manage/repairs');await F.detail(p);expect(await F.count(p,'[data-test=approve-safety]')).toBe(0)
  f.state.user=F.user(F.admin2);p=await f.open('/manage/repairs');await F.detail(p);await p.fill('[data-test=safety-comment]','仍不安全');await F.click(p,'[data-test=reject-safety]');await F.waitText(p,'维修中');expect(f.state.rows[0].appliedActive).toBe(true);expect(await F.count(p,'[data-test=source-released]')).toBe(0)
  f.state.rows[0].status='CANCEL_REVIEW_PENDING';f.state.rows[0].version++;await F.click(p,'[data-test=refresh]');await F.waitText(p,'待取消审查');await p.fill('[data-test=review-comment]','需要继续处理');await F.click(p,'[data-test=reject-cancel-review]');await F.waitText(p,'维修中');expect(f.state.rows[0].appliedActive).toBe(true)
 },60000)
 it('uncertain cancellation reuses exact operation and leaves other source restrictions intact',async()=>{
  f=await F.fixture();f.state.rows=[F.order(),F.active({id:F.order2})];f.state.unknownAction='cancel';const p=await f.open();await F.detail(p);await p.fill('[data-test=cancel-reason]','重复上报');await F.click(p,'[data-test=cancel]');await F.waitText(p,'结果尚未确认');await F.click(p,'[data-test=retry-command]');await F.waitText(p,'已取消')
  expect(F.posts(f,'/cancel')[1]?.body).toEqual(F.posts(f,'/cancel')[0]?.body);expect(f.history(F.order1).filter(e=>e.eventType==='CANCEL')).toHaveLength(1);expect(f.state.rows[1].appliedActive).toBe(true);expect(f.state.calls.some(c=>c.path.startsWith('/internal/'))).toBe(false)
 },60000)
 it('stale cancellation and delayed replies cannot overwrite a newer accepted result',async()=>{
  f=await F.fixture();f.state.user=F.user(F.admin2);f.state.rows=[F.active({status:'PENDING_ACCEPTANCE',version:5,repairReport:'已维修'})];const p=await f.open('/manage/repairs');await F.detail(p)
  await p.fill('[data-test=cancel-reason]','取消处理');f.state.conflictAction='cancel';await F.click(p,'[data-test=cancel]');await F.waitText(p,'资料已变化，请重新确认');expect(F.posts(f,'/cancel')).toHaveLength(1);expect(await F.count(p,'[data-test=repair-cancelled]')).toBe(0)
  f.state.delayNextDetail=true;await F.click(p,'[data-test=refresh]');f.ack(F.order1,false);await F.click(p,'[data-test=refresh]');await F.waitText(p,'已完成');await new Promise(r=>setTimeout(r,400));expect(await p.text('[data-test=order-status]')).toBe('已完成');expect(f.history(F.order1).filter(e=>e.eventType==='CANCEL')).toHaveLength(0)
 },60000)
})
