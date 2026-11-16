import { afterEach, describe, expect, it } from 'vitest'
import { fixture, request, requestId, count, text, click, waitText, posts } from './fixture'
let f:Awaited<ReturnType<typeof fixture>>
afterEach(async()=>{if(f)await f.close()},60000)
describe('M2-03',()=>{

 it('pending resource confirmation never promises whole room success',async()=>{
  f=await fixture();f.state.rows=[request({status:'RESOURCE_PENDING',resourceState:'PENDING'})];const p=await f.open();await click(p,'[data-open="'+requestId+'"]')
  expect(await text(p)).toContain('等待落实');expect(await text(p)).toContain('资源确认中');expect(await count(p,'[data-test=fulfilled]')).toBe(0);expect(await count(p,'[data-test=new-allocation]')).toBe(0)
 },60000)
 it('conflict and transport uncertainty retain original request while refreshing',async()=>{
  f=await fixture();f.state.rows=[request({status:'RESOURCE_PENDING',resourceState:'PENDING',deliveries:[{id:'notice-op',kind:'RESOURCE',state:'PENDING',attempts:2,lastError:'RESOURCE_CONFLICT',nextRetryAt:'2030-06-03T08:01:00+08:00'}]})]
  const p=await f.open();await click(p,'[data-open="'+requestId+'"]');expect(await text(p)).toContain('RESOURCE_CONFLICT');expect(await count(p,'[data-test=fulfilled]')).toBe(0)
  f.state.rows[0].deliveries[0].lastError='NETWORK_ERROR';await click(p,'[data-test=refresh]');await waitText(p,'NETWORK_ERROR')
  expect(posts(f,'/requests')).toHaveLength(0);expect(f.state.rows).toHaveLength(1)
 },60000)
 it('only approved and confirmed resource shows fulfilled interval and source',async()=>{
  f=await fixture();f.state.rows=[request({status:'APPROVED',resourceState:'CONFIRMED',resourceVersion:2})];const p=await f.open();await click(p,'[data-open="'+requestId+'"]')
  expect(await count(p,'[data-test=fulfilled]')).toBe(1);expect(await text(p)).toContain('2030/06/03 10:00');expect(await text(p)).toContain('2030/06/03 12:00');expect(await text(p)).toContain('M2 / '+requestId)
  f.state.rows[0].status='RESOURCE_PENDING';await click(p,'[data-test=refresh]');await waitText(p,'等待落实');expect(await count(p,'[data-test=fulfilled]')).toBe(0)
 },60000)
 it('out of order confirmation cannot revive cancelled details',async()=>{
  f=await fixture();f.state.rows[0].status='RESOURCE_PENDING';f.state.rows[0].resourceState='PENDING';const p=await f.open();await click(p,'[data-open="'+requestId+'"]')
  f.state.delayedDetail=true;await click(p,'[data-test=refresh]')
  f.state.rows[0]=request({status:'CANCELLED',resourceState:'RELEASE_PENDING',version:4});await click(p,'[data-test=refresh]')
  await waitText(p,'已撤销');await new Promise(r=>setTimeout(r,450));expect(await p.text('[data-test=request-status]')).toBe('已撤销');expect(await count(p,'[data-test=fulfilled]')).toBe(0)
 },60000)
})
