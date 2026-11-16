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

it("M1-08.S01 只能看到自己的历史，翻页不漏记录也不重复",async()=>{currentCase="M1-08.S01 只能看到自己的历史，翻页不漏记录也不重复";await scenario("M1-08.S01",f,async(path='/reservations',expected=path.split('?')[0])=>{if(page){await page.close();page=undefined};page=await open(f,path,expected);return page})},90000)

it("M1-08.S02 再次预约会重新检查当前规则",async()=>{currentCase="M1-08.S02 再次预约会重新检查当前规则";await scenario("M1-08.S02",f,async(path='/reservations',expected=path.split('?')[0])=>{if(page){await page.close();page=undefined};page=await open(f,path,expected);return page})},90000)

it("M1-08.S03 日期按上海时间显示，没有记录就显示空",async()=>{currentCase="M1-08.S03 日期按上海时间显示，没有记录就显示空";await scenario("M1-08.S03",f,async(path='/reservations',expected=path.split('?')[0])=>{if(page){await page.close();page=undefined};page=await open(f,path,expected);return page})},90000)

it("M1-08.S04 有权限的管理员可以查看预约管理明细",async()=>{currentCase="M1-08.S04 有权限的管理员可以查看预约管理明细";await scenario("M1-08.S04",f,async(path='/reservations',expected=path.split('?')[0])=>{if(page){await page.close();page=undefined};page=await open(f,path,expected);return page})},90000)
