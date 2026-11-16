import { afterEach, describe, expect, it } from 'vitest'
import { fixture, user, request, applicant, approver1, approver2, approver3, requestId, otherId, count, text, click, waitText, fillApplication, posts } from './fixture'
let f:Awaited<ReturnType<typeof fixture>>
afterEach(async()=>{if(f)await f.close()},60000)
describe('M2-05',()=>{

 it('own list and assigned tasks use separate authorized endpoints and paginate',async()=>{
  f=await fixture();f.state.user=user(applicant,['identity:read','approval:read']);f.state.rows.push(request({requestId:otherId,applicantId:approver3,purpose:'PRIVATE_OTHER_PURPOSE'}));for(let i=0;i<20;i++)f.state.rows.push(request({requestId:'30000000-0000-4000-8000-'+String(100+i).padStart(12,'0')}))
  let p=await f.open();await p.waitFor("document.querySelectorAll('[data-open]').length===20");expect(await text(p)).not.toContain('PRIVATE_OTHER_PURPOSE');await click(p,'[data-test=next-page]');await p.waitFor("document.querySelectorAll('[data-open]').length===1")
  expect(f.state.calls.some(c=>c.path.includes('/approvals/requests?page=1'))).toBe(true)
  f.state.user=user(approver1,['identity:read','approval:decide']);p=await f.open('/manage/approvals');await click(p,'[data-open="'+requestId+'"]');expect(await text(p)).toContain('课程讨论')
  expect(f.state.calls.some(c=>c.path.startsWith('/api/approvals/tasks'))).toBe(true)
 },60000)
 it('forbidden or expired details clear sensitive previous content',async()=>{
  f=await fixture();const p=await f.open();await click(p,'[data-open="'+requestId+'"]');expect(await p.text('[data-test=request-detail]')).toContain('课程讨论')
  f.state.failDetail=403;await click(p,'[data-test=refresh]');await waitText(p,'无权查看此申请');expect(await count(p,'[data-test=request-detail]')).toBe(0)
  f.state.failDetail=0;await click(p,'[data-open="'+requestId+'"]');await waitText(p,'课程讨论');f.state.failDetail=401;await click(p,'[data-test=refresh]');await expect.poll(()=>p.evaluate("location.pathname"),{timeout:5000}).toBe('/login')
  expect(await text(p)).not.toContain('课程讨论')
 },60000)
 it('decision timeline preserves actor time result reason and versions',async()=>{
  f=await fixture();const row=f.state.rows[0];row.status='REJECTED';Object.assign(row.tasks[0],{status:'APPROVED',actedAt:'2030-06-03T08:10:00+08:00',decidedBy:approver1,comment:'同意'});Object.assign(row.tasks[1],{status:'REJECTED',actedAt:'2030-06-03T08:20:00+08:00',decidedBy:approver2,comment:'用途不符'});row.history.push({actorId:approver1,status:'PENDING',reason:'同意',occurredAt:'2030-06-03T08:10:00+08:00',version:2},{actorId:approver2,status:'REJECTED',reason:'用途不符',occurredAt:'2030-06-03T08:20:00+08:00',version:3})
  const p=await f.open();await click(p,'[data-open="'+requestId+'"]');const timeline=await p.text('[data-test=history]')
  expect(timeline).toContain(approver1);expect(timeline).toContain(approver2);expect(timeline).toContain('2030/06/03 08:10');expect(timeline).toContain('用途不符');expect(timeline).toContain('版本 3')
  expect(await p.text('[data-test=task-list]')).toContain('已通过')
 },60000)
 it('maintenance abnormal and durable delivery states do not promise normal use or email',async()=>{
  f=await fixture();f.state.rows=[request({status:'APPROVED',resourceState:'CONFIRMED',resourceAbnormal:true,deliveries:[{id:'notify-1',kind:'NOTICE',state:'DONE',attempts:1,lastError:null,nextRetryAt:null},{id:'audit-1',kind:'AUDIT',state:'PENDING',attempts:2,lastError:'NETWORK_ERROR',nextRetryAt:'2030-06-03T08:01:00+08:00'}]})]
  const p=await f.open();await click(p,'[data-open="'+requestId+'"]');expect(await text(p)).toContain('教室受维修影响');expect(await count(p,'[data-test=fulfilled]')).toBe(0)
  expect(await p.text('[data-test=deliveries]')).toContain('已交付通知服务');expect(await text(p)).not.toContain('邮件已发送');expect(await text(p)).toContain('NETWORK_ERROR')
  expect(await text(p)).toContain('M2 / '+requestId);expect(posts(f,'/cancel')).toHaveLength(0)
  f.state.rows[0].resourceAbnormal=false;await click(p,'[data-test=refresh]');await waitText(p,'已落实');expect(await count(p,'[data-test=fulfilled]')).toBe(1)
 },60000)
 it('locale switches all approval forms history errors and statuses to English',async()=>{
  f=await fixture();const p=await f.open();await click(p,'[data-open="'+requestId+'"]')
  await p.evaluate("document.querySelector('.language-switcher select').focus()")
  // Native keyboard changes the actual existing language select.
  await p.evaluate("document.querySelector('.language-switcher select').value='en';document.querySelector('.language-switcher select').dispatchEvent(new Event('change',{bubbles:true}))")
  await waitText(p,'Pending approval');expect(await text(p)).toContain('Approval history');expect(await text(p)).toContain('Flow version')
  await fillApplication(p);await p.fill('[data-test=purpose]','');await click(p,'[data-test=submit-request]');await waitText(p,'Check the application details')
  expect(await text(p)).not.toContain('modules.approval.');expect(await text(p)).toContain('Shanghai time')
 },60000)
})
