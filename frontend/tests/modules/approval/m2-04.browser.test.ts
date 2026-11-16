import { afterEach, describe, expect, it } from 'vitest'
import { fixture, user, request, applicant, approver1, requestId, count, text, click, waitText, posts } from './fixture'
let f:Awaited<ReturnType<typeof fixture>>
afterEach(async()=>{if(f)await f.close()},60000)
describe('M2-04',()=>{

 it('owner cancels before start with version and stable idempotent retry',async()=>{
  f=await fixture();f.state.user=user(applicant,['identity:read','approval:read']);f.state.rows=[request({status:'APPROVED',resourceState:'CONFIRMED'})];f.state.unknownCancel=true
  const p=await f.open();await click(p,'[data-open="'+requestId+'"]');await click(p,'[data-test=cancel-request]');await waitText(p,'结果尚未确认')
  await click(p,'[data-test=retry-cancel]');await waitText(p,'已撤销');expect(posts(f,'/cancel')).toHaveLength(2);expect(posts(f,'/cancel')[1]?.body).toEqual(posts(f,'/cancel')[0]?.body)
  expect(posts(f,'/cancel')[0]?.body.expectedVersion).toBe(1);expect(await text(p)).toContain('资源释放中');expect(await count(p,'[data-test=fulfilled]')).toBe(0)
 },60000)
 it('started ended and foreign requests enforce cancellation boundary',async()=>{
  f=await fixture();f.state.user=user(applicant,['identity:read','approval:read']);f.state.rows=[request({startsAt:new Date(Date.now()+2200).toISOString(),endsAt:'2030-06-03T12:00:00+08:00',status:'APPROVED',resourceState:'CONFIRMED'})]
  let p=await f.open();await click(p,'[data-open="'+requestId+'"]');expect(await count(p,'[data-test=cancel-request]')).toBe(1);await new Promise(r=>setTimeout(r,2600));expect(await count(p,'[data-test=cancel-request]')).toBe(0)
  f.state.rows[0].startsAt='2020-01-01T08:00:00Z';p=await f.open();await click(p,'[data-open="'+requestId+'"]');expect(await count(p,'[data-test=cancel-request]')).toBe(0)
  f.state.user=user(approver1,['identity:read','approval:read','approval:decide']);p=await f.open('/manage/approvals');await click(p,'[data-open="'+requestId+'"]');expect(await count(p,'[data-test=cancel-request]')).toBe(0)
  f.state.user=user(approver1);f.state.rows[0].endsAt='2020-01-01T10:00:00Z';p=await f.open('/manage/approvals');await click(p,'[data-open="'+requestId+'"]');expect(await count(p,'[data-test=cancel-request]')).toBe(0);expect(posts(f,'/cancel')).toHaveLength(0)
 },60000)
 it('administrator cancellation requires permission and reason after start',async()=>{
  f=await fixture();f.state.user=user(approver1);f.state.rows=[request({startsAt:'2020-01-01T08:00:00Z',status:'APPROVED',resourceState:'CONFIRMED'})];const p=await f.open('/manage/approvals');await click(p,'[data-open="'+requestId+'"]')
  await click(p,'[data-test=cancel-request]');await waitText(p,'请填写撤销理由');expect(posts(f,'/cancel')).toHaveLength(0)
  await p.fill('[data-test=cancel-reason]','临时停用');await click(p,'[data-test=cancel-request]');await waitText(p,'已撤销')
  expect(posts(f,'/cancel')[0]?.body).toMatchObject({expectedVersion:1,reason:'临时停用'})
 },60000)
 it('expiry and start reached display server cancellation without client writes',async()=>{
  f=await fixture();const p=await f.open();await click(p,'[data-open="'+requestId+'"]')
  f.state.rows[0]=request({status:'EXPIRED',resourceState:'RELEASE_PENDING',cancelReason:'APPROVAL_EXPIRED',version:2});await click(p,'[data-test=refresh]');await waitText(p,'已过期')
  expect(posts(f,'/cancel')).toHaveLength(0)
  f.state.rows[0]=request({status:'CANCELLED',cancelReason:'START_REACHED',version:3});await click(p,'[data-test=refresh]');await waitText(p,'已到开始时间，申请未落实')
  expect(await text(p)).toContain('2030/06/03 09:00')
  f.state.rows[0]=request({status:'APPROVED',resourceState:'CONFIRMED',expiresAt:'2020-01-01T00:00:00Z',version:4});await click(p,'[data-test=refresh]');await waitText(p,'已落实');expect(await count(p,'[data-test=fulfilled]')).toBe(1)
 },60000)
 it('release pending and late confirmation do not claim seats are free',async()=>{
  f=await fixture();f.state.rows=[request({status:'CANCELLED',resourceState:'RELEASE_PENDING',version:3})];const p=await f.open();await click(p,'[data-open="'+requestId+'"]')
  expect(await text(p)).toContain('资源释放中');expect(await count(p,'[data-test=fulfilled]')).toBe(0)
  f.state.rows[0].resourceState='CONFIRMED';await click(p,'[data-test=refresh]');await waitText(p,'已撤销');expect(await count(p,'[data-test=fulfilled]')).toBe(0)
  f.state.rows[0].resourceState='RELEASED';await click(p,'[data-test=refresh]');await waitText(p,'资源已释放')
  expect(posts(f,'/cancel')).toHaveLength(0)
 },60000)
})
