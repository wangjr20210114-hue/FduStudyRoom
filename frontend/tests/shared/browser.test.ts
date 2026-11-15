import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { createServer, type Server } from 'node:http'
import { readFile, stat } from 'node:fs/promises'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { startBrowser, type BrowserPage } from './browser'

let server: Server, origin: string, page: BrowserPage
const html = `<!doctype html><html lang="zh-CN"><body><div id="app">
  <label>Name <input id="name" v-model="name"></label>
  <button id="save" @click="saved = name">{{locale === 'en' ? 'Save' : '保存'}}</button>
  <button id="language" @click="locale = locale === 'en' ? 'zh-CN' : 'en'">Language</button>
  <output id="result">{{saved ? (locale === 'en' ? 'Hello ' : '你好，') + saved : ''}}</output>
</div><script type="module">
import {createApp, ref, watch} from '/vue.js';
createApp({setup() { const name=ref(''), saved=ref(''), locale=ref('zh-CN');
watch(locale, value => document.documentElement.lang=value);return {name,saved,locale}; }}).mount('#app');
</script></body></html>`

beforeAll(async () => {
  const vue = await readFile(new URL('../../node_modules/vue/dist/vue.esm-browser.js', import.meta.url))
  server = createServer((req, res) => {
    res.setHeader('Content-Type', req.url === '/vue.js' ? 'text/javascript' : 'text/html; charset=utf-8')
    res.end(req.url === '/vue.js' ? vue : html)
  })
  await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve))
  origin = 'http://127.0.0.1:' + (server.address() as { port: number }).port
  page = await startBrowser()
}, 20000)

afterAll(async () => {
  await page?.close()
  if (server) { server.closeAllConnections(); await new Promise<void>(resolve => server.close(() => resolve())) }
})

describe('real browser baseline', () => {
  it('types into Vue, clicks save, switches language and captures a real screenshot', async () => {
    await page.goto(origin)
    await page.waitFor(() => document.querySelector('#save')?.textContent === '保存')
    await page.fill('#name', '陈同学')
    await page.click('#save')
    await page.waitFor(() => document.querySelector('#result')?.textContent === '你好，陈同学')
    expect(await page.text('#result')).toBe('你好，陈同学')
    await page.click('#language')
    await page.waitFor(() => document.documentElement.lang === 'en')
    expect(await page.text('#save')).toBe('Save')
    expect(await page.text('#result')).toBe('Hello 陈同学')
    await page.fill('#name', 'second')
    await page.click('#save')
    await page.waitFor(() => document.querySelector('#result')?.textContent === 'Hello second')
    const screenshot = join(tmpdir(), 'campus-reports', 'browser-baseline.png')
    const png = await page.screenshot(screenshot)
    expect(png.subarray(0, 8).toString('hex')).toBe('89504e470d0a1a0a')
    expect((await stat(screenshot)).size).toBe(png.length)
    expect(page.metadata.version).toContain('Chrome/')
  })

  it('does not turn missing DOM or page exceptions into successful observations', async () => {
    await expect(page.waitFor(() => !!document.querySelector('#never-exists'), { timeoutMs: 50 })).rejects.toThrow('BROWSER_WAIT_TIMEOUT')
    await expect(page.evaluate(() => { throw new Error('controlled page failure') })).rejects.toThrow('controlled page failure')
    await expect(page.click('#never-exists')).rejects.toThrow('ELEMENT_MATCH_COUNT: 0')
  })

  it('rejects external navigation and nonprivate screenshot destinations', async () => {
    await expect(page.goto('https://example.com/')).rejects.toThrow('BROWSER_ORIGIN_NOT_LOCAL')
    await expect(page.screenshot('/workspace/frontend/public/unapproved.png')).rejects.toThrow('SCREENSHOT_REQUIRES_PRIVATE_OUTPUT_PATH')
    await expect(startBrowser({ executable: '/not-installed-campus-browser' })).rejects.toThrow('BROWSER_NOT_FOUND')
  })

  it('closes only its created profile and supports repeated cleanup', async () => {
    const profile = page.metadata.profile
    await page.close()
    await page.close()
    await expect(stat(profile)).rejects.toThrow()
  })
})
