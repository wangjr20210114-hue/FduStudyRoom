import { afterEach, describe, expect, it } from 'vitest'
import { fixture, user, applicant, approver1, approver2, approver3, requestId, count, text, click, waitText, posts } from './fixture'
let f:Awaited<ReturnType<typeof fixture>>
afterEach(async()=>{if(f)await f.close()},60000)
describe('M2-02',()=>{

 it('ordered tasks and expiry snapshot remain visible after configuration change',async()=>{
  f=await fixture();f.state.user=user(approver1);const p=await f.open('/manage/approvals');await click(p,'[data-open="'+requestId+'"]')
  expect(await text(p)).toContain(approver1);expect(await text(p)).toContain(approver2);expect(await text(p)).toContain('2030/06/03 09:00')
  expect(await count(p,'[data-test=approve]')).toBe(1)
  await click(p,'[data-remove="1"]');await click(p,'[data-add="'+approver3+'"]');await click(p,'[data-test=save-flow]');await waitText(p,'配置已保存')
  expect(f.state.configuration.approverIds).toEqual([approver1,approver3]);expect(f.state.rows[0].flowVersion).toBe(1)
  expect(await p.text('[data-test=task-list]')).toContain(approver2)
 },60000)
 it('configuration requires two distinct current candidates and preserves version',async()=>{
  f=await fixture();const p=await f.open('/manage/approvals');await click(p,'[data-remove="1"]');await click(p,'[data-test=save-flow]');await waitText(p,'至少选择两位不同审批人')
  expect(f.state.calls.filter(c=>c.method==='PUT')).toHaveLength(0)
  expect(await p.evaluate('document.querySelector(\'[data-add="'+approver1+'"]\').disabled')).toBe(true)
  await click(p,'[data-add="'+approver3+'"]');await click(p,'[data-test=save-flow]');await waitText(p,'配置已保存')
  expect(f.state.calls.find(c=>c.method==='PUT')?.body).toEqual({expectedVersion:1,approverIds:[approver1,approver3]})
  f.state.candidates=[];await click(p,'[data-test=reload-flow]');await waitText(p,'没有足够的有效审批人')
 },60000)
 it('only assigned non applicant non repeated pending approver can decide',async()=>{
  f=await fixture();f.state.user=user(approver2,['identity:read','approval:decide']);let p=await f.open('/manage/approvals');await click(p,'[data-open="'+requestId+'"]');expect(await count(p,'[data-test=approve]')).toBe(0)
  f.state.user=user(approver1,['identity:read','approval:decide']);f.state.rows[0].applicantId=approver1;p=await f.open('/manage/approvals');await click(p,'[data-open="'+requestId+'"]');expect(await count(p,'[data-test=approve]')).toBe(0)
  f.state.rows[0].applicantId=applicant;Object.assign(f.state.rows[0].tasks[0],{status:'APPROVED',decidedBy:approver1});Object.assign(f.state.rows[0].tasks[1],{status:'PENDING',approverId:approver1});p=await f.open('/manage/approvals');await click(p,'[data-open="'+requestId+'"]');expect(await count(p,'[data-test=approve]')).toBe(0)
  expect(posts(f,'/decision')).toHaveLength(0)
 },60000)
 it('rejection requires reason and stale decision refreshes instead of overwriting',async()=>{
  f=await fixture();f.state.user=user(approver1,['identity:read','approval:decide']);const p=await f.open('/manage/approvals');await click(p,'[data-open="'+requestId+'"]')
  await click(p,'[data-test=reject]');await waitText(p,'请填写驳回理由');expect(posts(f,'/decision')).toHaveLength(0)
  f.state.decisionConflict=true;await p.fill('[data-test=decision-comment]','用途不符');await click(p,'[data-test=reject]');await waitText(p,'资料已变化，请重新确认')
  expect(posts(f,'/decision')[0]?.body).toEqual({expectedVersion:1,decision:'REJECT',comment:'用途不符'});expect(f.state.rows[0].history).toHaveLength(1)
  await click(p,'[data-test=reject]');await waitText(p,'已驳回');expect(posts(f,'/decision')[1]?.body.expectedVersion).toBe(2)
  expect(await count(p,'[data-test=approve]')).toBe(0)
 },60000)
 it('final decision subset refreshes full tasks for decide only account',async()=>{
  f=await fixture();f.state.user=user(approver2,['identity:read','approval:decide']);f.state.rows[0].tasks[0].status='APPROVED';f.state.rows[0].tasks[0].decidedBy=approver1;f.state.rows[0].tasks[1].status='PENDING'
  const p=await f.open('/manage/approvals');await click(p,'[data-open="'+requestId+'"]');await click(p,'[data-test=approve]');await waitText(p,'等待落实')
  expect(await text(p)).toContain('课程讨论');expect(await count(p,'[data-test=fulfilled]')).toBe(0)
  expect(f.state.calls.some(c=>c.method==='GET'&&c.path==='/api/approvals/requests/'+requestId)).toBe(false)
  expect(f.state.calls.filter(c=>c.path.startsWith('/api/approvals/tasks')).length).toBeGreaterThan(1)
 },60000)
})
