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

it("M1-04.S01 预约开始后，输入正确签到码完成签到",async()=>{currentCase="M1-04.S01 预约开始后，输入正确签到码完成签到";await scenario("M1-04.S01",f,async(path='/reservations',expected=path.split('?')[0])=>{if(page){await page.close();page=undefined};page=await open(f,path,expected);return page})},90000)

it("M1-04.S02 提前签到、输错教室码或替别人签到都不成功",async()=>{currentCase="M1-04.S02 提前签到、输错教室码或替别人签到都不成功";await scenario("M1-04.S02",f,async(path='/reservations',expected=path.split('?')[0])=>{if(page){await page.close();page=undefined};page=await open(f,path,expected);return page})},90000)

it("M1-04.S03 截止前签到成功后，不会再被截止任务判成违约",async()=>{currentCase="M1-04.S03 截止前签到成功后，不会再被截止任务判成违约";await scenario("M1-04.S03",f,async(path='/reservations',expected=path.split('?')[0])=>{if(page){await page.close();page=undefined};page=await open(f,path,expected);return page})},90000)
