import { beforeAll,beforeEach,afterEach,afterAll,it } from 'vitest'
import { Fixture,open,capture,type BrowserPage } from './fixture'
import { scenario } from './scenarios'
import { randomUUID } from 'node:crypto'
import { writeFile } from 'node:fs/promises'
const f=new Fixture();let page:BrowserPage;let currentCase=''
beforeAll(async()=>{await f.start()},30000)
beforeEach(()=>{f.reset();currentCase=''})
afterEach(async()=>{if(page){try{const name=currentCase.replace(/[^a-zA-Z0-9.-]/g,'_').slice(0,65)+'-'+randomUUID();await capture(page,name);await writeFile('/tmp/campus-reports/'+name+'.meta.json',JSON.stringify({test:currentCase,browser:page.metadata,kind:'realChrome product route with isolated HTTP fixture'}))}finally{await page.close();page=undefined as any}}})
afterAll(async()=>{await f.stop()},20000)

it("RES-05.S01 教室进入维修后，里面所有座位都不能新预约",async()=>{currentCase="RES-05.S01 教室进入维修后，里面所有座位都不能新预约";await scenario("RES-05.S01",f,async(path='/manage/rooms',expected=path)=>{page=await open(f,path,expected);return page})},30000)

it("RES-05.S02 处理完一张维修单，不会顺便解除其他限制",async()=>{currentCase="RES-05.S02 处理完一张维修单，不会顺便解除其他限制";await scenario("RES-05.S02",f,async(path='/manage/rooms',expected=path)=>{page=await open(f,path,expected);return page})},30000)

it("RES-05.S03 过时的恢复通知，不能把仍在维修的教室开放",async()=>{currentCase="RES-05.S03 过时的恢复通知，不能把仍在维修的教室开放";await scenario("RES-05.S03",f,async(path='/manage/rooms',expected=path)=>{page=await open(f,path,expected);return page})},30000)

it("RES-05.S04 维修中新增或启用的座位，也仍然不能预约",async()=>{currentCase="RES-05.S04 维修中新增或启用的座位，也仍然不能预约";await scenario("RES-05.S04",f,async(path='/manage/rooms',expected=path)=>{page=await open(f,path,expected);return page})},30000)

it("RES-05.S05 不把任何不符合条件的座位显示成可预约",async()=>{currentCase="RES-05.S05 不把任何不符合条件的座位显示成可预约";await scenario("RES-05.S05",f,async(path='/manage/rooms',expected=path)=>{page=await open(f,path,expected);return page})},30000)

it("RES-05.S06 上一笔预约结束后，还要确认有没有维修等其他限制",async()=>{currentCase="RES-05.S06 上一笔预约结束后，还要确认有没有维修等其他限制";await scenario("RES-05.S06",f,async(path='/manage/rooms',expected=path)=>{page=await open(f,path,expected);return page})},30000)
