import { afterEach, describe, expect, it } from 'vitest'
import { fixture, user, applicant, roomId, count, click, waitText, fillApplication, posts } from './fixture'
let f:Awaited<ReturnType<typeof fixture>>
afterEach(async()=>{if(f)await f.close()},60000)
describe('M2-01',()=>{

 it('qualified submission stays pending and sends only approved fields',async()=>{
  f=await fixture();f.state.rows=[];const p=await f.open();await fillApplication(p);await click(p,'[data-test=submit-request]')
  await waitText(p,'待审批');expect(posts(f,'/requests')).toHaveLength(1)
  const call=posts(f,'/requests')[0]!
  expect(Object.keys(call.body).sort()).toEqual(['attendeeCount','endsAt','operationId','purpose','roomId','startsAt'])
  expect(call.body).toMatchObject({roomId,purpose:'课程讨论',attendeeCount:2,startsAt:'2030-06-03T10:00:00+08:00',endsAt:'2030-06-03T12:00:00+08:00'})
  expect(call.csrf).toBe('fixture-csrf');expect(await count(p,'[data-test=fulfilled]')).toBe(0)
 },60000)
 it('permission and server eligibility cannot be self granted',async()=>{
  f=await fixture();f.state.user=user(applicant,['identity:read','approval:read'],'SYSTEM_ADMIN');let p=await f.open()
  expect(await count(p,'[data-test=new-request]')).toBe(0);expect(posts(f,'/requests')).toHaveLength(0)
  f.state.user=user();f.state.eligible=false;p=await f.open();await fillApplication(p);await click(p,'[data-test=submit-request]')
  await waitText(p,'没有整室申请资格');expect(await count(p,'[name=canApplyRoom], [name=applicantId]')).toBe(0)
  expect(f.state.rows).toHaveLength(1);expect(await count(p,'[data-test=fulfilled]')).toBe(0)
 },60000)
 it('invalid purpose room times and attendance never submit',async()=>{
  f=await fixture();const p=await f.open();await fillApplication(p)
  await p.fill('[data-test=purpose]',' ');await click(p,'[data-test=submit-request]');await waitText(p,'请检查申请信息')
  await p.fill('[data-test=purpose]','课程讨论');await p.fill('[data-test=ends-at]','2030-06-03T09:00');await click(p,'[data-test=submit-request]')
  await p.fill('[data-test=ends-at]','2030-06-03T23:00');await click(p,'[data-test=submit-request]')
  await p.fill('[data-test=ends-at]','2030-06-03T12:00');await p.fill('[data-test=attendee-count]','3');await click(p,'[data-test=submit-request]')
  expect(posts(f,'/requests')).toHaveLength(0);expect(await count(p,'[name=roomId]')).toBe(0)
 },60000)
 it('unknown submission retry keeps original operation and frozen payload',async()=>{
  f=await fixture();f.state.rows=[];f.state.unknownSubmit=true;const p=await f.open();await fillApplication(p);await click(p,'[data-test=submit-request]')
  await waitText(p,'结果尚未确认');expect(await p.evaluate("document.querySelector('[data-test=purpose]').disabled")).toBe(true)
  await click(p,'[data-test=retry-submit]');await waitText(p,'待审批')
  expect(posts(f,'/requests')).toHaveLength(2);expect(posts(f,'/requests')[1]!.body).toEqual(posts(f,'/requests')[0]!.body);expect(f.state.rows).toHaveLength(1)
 },60000)
})
