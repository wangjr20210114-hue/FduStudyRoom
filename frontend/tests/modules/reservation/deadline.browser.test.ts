import { beforeAll,beforeEach,afterEach,afterAll,it } from 'vitest'
import { randomUUID } from 'node:crypto'
import { writeFile } from 'node:fs/promises'
import { Fixture,open,type BrowserPage } from './fixture'
import { scenario } from './scenarios'
const f=new Fixture();let page:BrowserPage|undefined;let currentCase=''
beforeAll(async()=>{await f.start()},30000)
beforeEach(()=>{f.reset();currentCase=''})
afterEach(async()=>{if(page){try{const name=currentCase.replace(/[^A-Za-z0-9.-]/g,'_').slice(0,65)+'-'+randomUUID();await page.screenshot('/tmp/campus-reports/'+name+'.png');await writeFile('/tmp/campus-reports/'+name+'.meta.json',JSON.stringify({test:currentCase,browser:page.metadata,navigation:f.navigationNotes,kind:'actualChrome full product route; synthetic loopback API and UI clock, not realDB/SMTP/scheduler acceptance'}))}finally{await page.close();page=undefined}}})
afterAll(async()=>{await f.stop()},20000)

it("M1-07.S01 到截止时间才取消，并释放这笔预约的座位",async()=>{currentCase="M1-07.S01 到截止时间才取消，并释放这笔预约的座位";await scenario("M1-07.S01",f,async(path='/reservations',expected=path.split('?')[0])=>{if(page){await page.close();page=undefined};page=await open(f,path,expected);return page})},90000)

it("M1-07.S02 服务晚恢复，也只处理一次，不补发已经没意义的提醒",async()=>{currentCase="M1-07.S02 服务晚恢复，也只处理一次，不补发已经没意义的提醒";await scenario("M1-07.S02",f,async(path='/reservations',expected=path.split('?')[0])=>{if(page){await page.close();page=undefined};page=await open(f,path,expected);return page})},90000)

it("M1-07.S03 已经签到或因维修被结束的预约，不算未签到违约",async()=>{currentCase="M1-07.S03 已经签到或因维修被结束的预约，不算未签到违约";await scenario("M1-07.S03",f,async(path='/reservations',expected=path.split('?')[0])=>{if(page){await page.close();page=undefined};page=await open(f,path,expected);return page})},90000)

it("M1-07.S04 已经因超时取消的预约，不会被迟到的成功消息恢复",async()=>{currentCase="M1-07.S04 已经因超时取消的预约，不会被迟到的成功消息恢复";await scenario("M1-07.S04",f,async(path='/reservations',expected=path.split('?')[0])=>{if(page){await page.close();page=undefined};page=await open(f,path,expected);return page})},90000)

it("M1-07.S05 已经结束或取消的历史，不重新处理",async()=>{currentCase="M1-07.S05 已经结束或取消的历史，不重新处理";await scenario("M1-07.S05",f,async(path='/reservations',expected=path.split('?')[0])=>{if(page){await page.close();page=undefined};page=await open(f,path,expected);return page})},90000)
