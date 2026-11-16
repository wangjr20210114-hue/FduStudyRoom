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

it("RES-04.S01 两个人同时抢同一个座位，只能成功一个",async()=>{currentCase="RES-04.S01 两个人同时抢同一个座位，只能成功一个";await scenario("RES-04.S01",f,async(path='/manage/rooms',expected=path)=>{page=await open(f,path,expected);return page})},30000)

it("RES-04.S02 同一个人不能同时占两个座位，但可以预约紧接的下一小时",async()=>{currentCase="RES-04.S02 同一个人不能同时占两个座位，但可以预约紧接的下一小时";await scenario("RES-04.S02",f,async(path='/manage/rooms',expected=path)=>{page=await open(f,path,expected);return page})},30000)

it("RES-04.S03 座位不可用时，不留下“占用成功”的记录",async()=>{currentCase="RES-04.S03 座位不可用时，不留下“占用成功”的记录";await scenario("RES-04.S03",f,async(path='/manage/rooms',expected=path)=>{page=await open(f,path,expected);return page})},30000)

it("RES-04.S04 整间教室预约成功后，所有座位都被占用",async()=>{currentCase="RES-04.S04 整间教室预约成功后，所有座位都被占用";await scenario("RES-04.S04",f,async(path='/manage/rooms',expected=path)=>{page=await open(f,path,expected);return page})},30000)

it("RES-04.S05 有一个座位已被订走，不能只订下剩余座位",async()=>{currentCase="RES-04.S05 有一个座位已被订走，不能只订下剩余座位";await scenario("RES-04.S05",f,async(path='/manage/rooms',expected=path)=>{page=await open(f,path,expected);return page})},30000)

it("RES-04.S06 个人和整室同时抢订时，不出现“半间教室订成了”",async()=>{currentCase="RES-04.S06 个人和整室同时抢订时，不出现“半间教室订成了”";await scenario("RES-04.S06",f,async(path='/manage/rooms',expected=path)=>{page=await open(f,path,expected);return page})},30000)

it("RES-04.S07 整室被订走期间，新加或重新启用的座位也不能单独预约",async()=>{currentCase="RES-04.S07 整室被订走期间，新加或重新启用的座位也不能单独预约";await scenario("RES-04.S07",f,async(path='/manage/rooms',expected=path)=>{page=await open(f,path,expected);return page})},30000)

it("RES-04.S08 不把任何不符合条件的座位显示成可预约",async()=>{currentCase="RES-04.S08 不把任何不符合条件的座位显示成可预约";await scenario("RES-04.S08",f,async(path='/manage/rooms',expected=path)=>{page=await open(f,path,expected);return page})},30000)

it("RES-04.S09 上一笔预约结束后，还要确认有没有维修等其他限制",async()=>{currentCase="RES-04.S09 上一笔预约结束后，还要确认有没有维修等其他限制";await scenario("RES-04.S09",f,async(path='/manage/rooms',expected=path)=>{page=await open(f,path,expected);return page})},30000)
