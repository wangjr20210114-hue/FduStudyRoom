import { afterEach, describe, expect, it } from 'vitest'
import * as F from './fixture'
let f:Awaited<ReturnType<typeof F.fixture>>
afterEach(async()=>{if(f)await f.close()},60000)
describe('M3-06',()=>{

 it('actual acceptor saves integer boundary scores and optional empty or preserved text',async()=>{
  f=await F.fixture();f.state.rows=[F.completed(),F.completed({id:F.order2})];f.state.user=F.user(F.admin2,['identity:read','repair:history:read','repair:evaluate']);const p=await f.open('/maintenance/history');await F.detail(p)
  await p.fill('[data-test=rating]','1');await p.fill('[data-test=evaluation]','');await F.click(p,'[data-test=evaluate]');await F.waitText(p,'评分 1')
  await F.detail(p,F.order2);await p.fill('[data-test=rating]','5');await p.fill('[data-test=evaluation]',' 已修复 ');await F.click(p,'[data-test=evaluate]');await F.waitText(p,'评分 5')
  expect(F.posts(f,'/evaluation').map(c=>c.body.rating)).toEqual([1,5]);expect(F.posts(f,'/evaluation')[0]?.body.evaluation).toBe('');expect(F.posts(f,'/evaluation')[1]?.body.evaluation).toBe(' 已修复 ')
  expect(Object.keys(F.posts(f,'/evaluation')[0]!.body).sort()).toEqual(['evaluation','rating','version'])
 },60000)
 it('invalid ratings and oversized or blank only text never submit evaluation',async()=>{
  f=await F.fixture();f.state.rows=[F.completed()];f.state.user=F.user(F.admin2);const p=await f.open('/maintenance/history');await F.detail(p)
  for(const rating of ['0','6','1.5','1e0']){await p.fill('[data-test=rating]',rating);await F.click(p,'[data-test=evaluate]');await F.waitText(p,'请填写1至5的整数评分')}
  await p.fill('[data-test=rating]','5');await p.fill('[data-test=evaluation]','x'.repeat(501));await F.click(p,'[data-test=evaluate]');await F.waitText(p,'评价须为0至500字')
  await p.fill('[data-test=evaluation]','   ');await F.click(p,'[data-test=evaluate]');expect(F.posts(f,'/evaluation')).toHaveLength(0)
 },60000)
 it('permission actual acceptedBy and all repair participants govern evaluation controls',async()=>{
  f=await F.fixture();f.state.rows=[F.completed()];f.state.user=F.user(F.reporter);let p=await f.open();await F.detail(p);expect(await F.count(p,'[data-test=evaluate]')).toBe(0)
  f.state.user=F.user(F.admin2,['identity:read','repair:history:read'],'SYSTEM_ADMIN');p=await f.open('/maintenance/history');await F.detail(p);expect(await F.count(p,'[data-test=evaluate]')).toBe(0)
  f.state.rows=[F.completed({managed:false,acceptedBy:F.worker2})];f.state.user=F.user(F.worker2);f.state.histories.set(F.order1,[F.event({actorId:F.worker2,eventType:'START',toStatus:'IN_PROGRESS',orderVersion:3})]);p=await f.open('/maintenance/history');await F.detail(p)
  expect(await F.count(p,'[data-test=evaluate]')).toBe(0);expect(F.posts(f,'/evaluation')).toHaveLength(0);expect(await F.count(p,'[name=acceptedBy], [name=evaluatorId]')).toBe(0)
 },60000)
 it('all repair rejection acceptance and scoring rounds keep actor time reason and rating',async()=>{
  f=await F.fixture();f.state.rows=[F.completed({technicianId:F.worker2,rating:5,evaluation:'修复完成',evaluatedAt:'2030-06-03T09:30:00+08:00',version:9})];f.state.user=F.user(F.admin2)
  f.state.histories.set(F.order1,[F.event(),F.event({actorId:F.worker1,eventType:'FINISH',fromStatus:'IN_PROGRESS',toStatus:'PENDING_ACCEPTANCE',createdAt:'2030-06-03T08:20:00+08:00',note:'首次完工',orderVersion:4}),F.event({actorId:F.admin2,eventType:'REJECT',fromStatus:'PENDING_ACCEPTANCE',toStatus:'IN_PROGRESS',createdAt:'2030-06-03T08:30:00+08:00',note:'返工原因',orderVersion:5}),F.event({actorId:F.worker2,eventType:'FINISH',fromStatus:'IN_PROGRESS',toStatus:'PENDING_ACCEPTANCE',createdAt:'2030-06-03T08:50:00+08:00',note:'再次完工',orderVersion:6}),F.event({actorId:F.admin2,eventType:'ACCEPT',fromStatus:'PENDING_ACCEPTANCE',toStatus:'RESTORATION_PENDING',createdAt:'2030-06-03T09:00:00+08:00',note:'安全可用',orderVersion:7}),F.event({actorId:null,eventType:'RESOURCE_ACK',fromStatus:'RESTORATION_PENDING',toStatus:'COMPLETED',createdAt:'2030-06-03T09:01:00+08:00',orderVersion:8}),F.event({actorId:F.admin2,eventType:'EVALUATE',fromStatus:'COMPLETED',toStatus:'COMPLETED',createdAt:'2030-06-03T09:30:00+08:00',note:'修复完成',rating:5,orderVersion:9})])
  const p=await f.open('/maintenance/history');await F.detail(p);let history=await p.text('[data-test=history]');expect(history).toContain('返工原因');expect(history).toContain(F.worker1);expect(history).toContain(F.worker2);expect(history).toContain('2030/06/03 08:30');expect(await F.count(p,'[data-test=history] > li')).toBe(7)
  await F.english(p);await F.waitText(p,'Inspection rejected');history=await p.text('[data-test=history]');expect(history).toContain('Rating 5');expect(await F.text(p)).not.toContain('modules.repair.')
 },60000)
 it('evaluation subset response cannot erase full managed resource details',async()=>{
  f=await F.fixture();f.state.rows=[F.completed()];f.state.user=F.user(F.admin2);const p=await f.open('/maintenance/history');await F.detail(p);await p.fill('[data-test=rating]','4');await p.fill('[data-test=evaluation]','满意');await F.click(p,'[data-test=evaluate]');await F.waitText(p,'评分 4')
  expect(await p.text('[data-test=repair-detail]')).toContain(F.room1);expect(await F.text(p)).toContain('M3 / '+F.order1);expect(await F.count(p,'[data-test=source-released]')).toBe(1);expect(f.state.calls.filter(c=>c.method==='GET'&&c.path==='/api/repairs/'+F.order1).length).toBeGreaterThan(1)
 },60000)
 it('uncertain evaluation rechecks immutable result instead of duplicating or overwriting',async()=>{
  f=await F.fixture();f.state.rows=[F.completed(),F.completed({id:F.order2})];f.state.user=F.user(F.admin2);f.state.unknownAction='evaluation';const p=await f.open('/maintenance/history');await F.detail(p)
  await p.fill('[data-test=rating]','4');await p.fill('[data-test=evaluation]','满意');await F.click(p,'[data-test=evaluate]');await F.waitText(p,'评分 4');expect(F.posts(f,'/evaluation')).toHaveLength(1)
  f.state.failBeforeCommit='evaluation';await F.detail(p,F.order2);await p.fill('[data-test=rating]','5');await p.fill('[data-test=evaluation]','很好');await F.click(p,'[data-test=evaluate]');await F.waitText(p,'结果尚未确认');expect(await p.evaluate("document.querySelector('[data-test=rating]').disabled")).toBe(true)
  await F.click(p,'[data-test=retry-evaluation]');await F.waitText(p,'评分 5');expect(F.posts(f,'/evaluation')[2]?.body).toEqual(F.posts(f,'/evaluation')[1]?.body);expect(f.history(F.order2).filter(e=>e.eventType==='EVALUATE')).toHaveLength(1)
 },60000)
})
