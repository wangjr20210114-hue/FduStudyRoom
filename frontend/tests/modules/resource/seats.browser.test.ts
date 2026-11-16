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

it("RES-02.S01 同一教室不能出现两个同编号座位",async()=>{currentCase="RES-02.S01 同一教室不能出现两个同编号座位";await scenario("RES-02.S01",f,async(path='/manage/rooms',expected=path)=>{page=await open(f,path,expected);return page})},30000)

it("RES-02.S02 只看有插座的座位时，不混入无插座座位",async()=>{currentCase="RES-02.S02 只看有插座的座位时，不混入无插座座位";await scenario("RES-02.S02",f,async(path='/manage/rooms',expected=path)=>{page=await open(f,path,expected);return page})},30000)

it("RES-02.S03 停用座位后不能新预约，过去记录还在",async()=>{currentCase="RES-02.S03 停用座位后不能新预约，过去记录还在";await scenario("RES-02.S03",f,async(path='/manage/rooms',expected=path)=>{page=await open(f,path,expected);return page})},30000)
