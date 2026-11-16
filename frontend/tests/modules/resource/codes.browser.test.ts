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

it("RES-07.S01 屏幕上的签到码，只能用于这间教室的当天签到",async()=>{currentCase="RES-07.S01 屏幕上的签到码，只能用于这间教室的当天签到";await scenario("RES-07.S01",f,async(path='/manage/rooms',expected=path)=>{page=await open(f,path,expected);return page})},30000)

it("RES-07.S02 上海时间过了零点，昨天的码就失效",async()=>{currentCase="RES-07.S02 上海时间过了零点，昨天的码就失效";await scenario("RES-07.S02",f,async(path='/manage/rooms',expected=path)=>{page=await open(f,path,expected);return page})},30000)

it("RES-07.S03 当天反复打开页面，签到码仍能正常使用",async()=>{currentCase="RES-07.S03 当天反复打开页面，签到码仍能正常使用";await scenario("RES-07.S03",f,async(path='/manage/rooms',expected=path)=>{page=await open(f,path,expected);return page})},30000)
