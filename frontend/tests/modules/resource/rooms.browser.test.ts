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

it("RES-01.S01 新增教室后，列表和详情都能找到",async()=>{currentCase="RES-01.S01 新增教室后，列表和详情都能找到";await scenario("RES-01.S01",f,async(path='/manage/rooms',expected=path)=>{page=await open(f,path,expected);return page})},30000)

it("RES-01.S02 教室停用后不再接受新预约，但保留过去记录",async()=>{currentCase="RES-01.S02 教室停用后不再接受新预约，但保留过去记录";await scenario("RES-01.S02",f,async(path='/manage/rooms',expected=path)=>{page=await open(f,path,expected);return page})},30000)

it("RES-01.S03 容量填错或拿旧资料保存，都不能覆盖现有资料",async()=>{currentCase="RES-01.S03 容量填错或拿旧资料保存，都不能覆盖现有资料";await scenario("RES-01.S03",f,async(path='/manage/rooms',expected=path)=>{page=await open(f,path,expected);return page})},30000)
