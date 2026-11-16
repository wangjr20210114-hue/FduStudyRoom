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

it("M1-05.S01 没到提醒时间不发，到点后能在测试邮箱看到",async()=>{currentCase="M1-05.S01 没到提醒时间不发，到点后能在测试邮箱看到";await scenario("M1-05.S01",f,async(path='/reservations',expected=path.split('?')[0])=>{if(page){await page.close();page=undefined};page=await open(f,path,expected);return page})},90000)

it("M1-05.S02 已取消的不发，已经处理过的不重复生成",async()=>{currentCase="M1-05.S02 已取消的不发，已经处理过的不重复生成";await scenario("M1-05.S02",f,async(path='/reservations',expected=path.split('?')[0])=>{if(page){await page.close();page=undefined};page=await open(f,path,expected);return page})},90000)

it("M1-05.S03 临近开始才预约要补提醒，邮件结果不明要如实显示",async()=>{currentCase="M1-05.S03 临近开始才预约要补提醒，邮件结果不明要如实显示";await scenario("M1-05.S03",f,async(path='/reservations',expected=path.split('?')[0])=>{if(page){await page.close();page=undefined};page=await open(f,path,expected);return page})},90000)
