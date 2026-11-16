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

it("M1-01.S01 选择时间和插座条件后，只显示符合条件的座位",async()=>{currentCase="M1-01.S01 选择时间和插座条件后，只显示符合条件的座位";await scenario("M1-01.S01",f,async(path='/reservations',expected=path.split('?')[0])=>{if(page){await page.close();page=undefined};page=await open(f,path,expected);return page})},90000)

it("M1-01.S02 不把无资格或正在占用、维修的座位列为可预约",async()=>{currentCase="M1-01.S02 不把无资格或正在占用、维修的座位列为可预约";await scenario("M1-01.S02",f,async(path='/reservations',expected=path.split('?')[0])=>{if(page){await page.close();page=undefined};page=await open(f,path,expected);return page})},90000)

it("M1-01.S03 没有空位和网络出错，要给出不同提示",async()=>{currentCase="M1-01.S03 没有空位和网络出错，要给出不同提示";await scenario("M1-01.S03",f,async(path='/reservations',expected=path.split('?')[0])=>{if(page){await page.close();page=undefined};page=await open(f,path,expected);return page})},90000)
