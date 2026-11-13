import { describe, expect, it } from 'vitest'
import { createSSRApp, h } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { createI18n } from 'vue-i18n'
import { messages } from '../src/i18n'
import ResourcePanel from '../src/ResourcePanel.vue'
const status={memory:{known:true,totalBytes:4*1024**3,availableBytes:1.6*1024**3},memoryUsedPercent:60,pressure:false,thresholdPercent:85,disk:{known:true,totalBytes:60*1024**3,availableBytes:11*1024**3},jvm:{usedBytes:100*1024**2,maxBytes:256*1024**2},queue:{pending_count:3,pending_bytes:2048,ready_count:1},verificationEnabled:false,syncEnabled:false,ciCanStart:false}
async function render(value:InstanceType<typeof ResourcePanel>['$props']['status'],locale='zh-CN'){
 const app=createSSRApp({render:()=>h(ResourcePanel,{status:value})})
 app.use(createI18n({legacy:false,locale,fallbackLocale:'en',messages}))
 return renderToString(app)
}
describe('buffer resource panel',()=>{
 it('shows available memory, threshold and the member queue',async()=>{
  const html=await render(status)
  expect(html).toContain('60.0%');expect(html).toContain('85%');expect(html).toContain('1.60 GiB');expect(html).toContain('2.00 KiB')
 })
 it('shows unavailable sampling instead of a healthy zero',async()=>{
  const html=await render({...status,memory:{known:false},memoryUsedPercent:null})
  expect(html).toContain('暂不可用');expect(html).not.toContain('0.0%')
 })
 it('shows pressure and receive-only state in English',async()=>{
  const html=await render({...status,pressure:true},'en')
  expect(html).toContain('Memory pressure');expect(html).toContain('CI disabled on this host')
 })
})
