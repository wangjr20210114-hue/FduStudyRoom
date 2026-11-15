/// <reference types="node" />
import { spawn, type ChildProcess } from 'node:child_process'
import { existsSync } from 'node:fs'
import { mkdtemp, readFile, mkdir, writeFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { basename, dirname, isAbsolute, join, resolve, sep } from 'node:path'

export interface BrowserOptions { executable?: string; timeoutMs?: number }
export interface WaitOptions { timeoutMs?: number; pollMs?: number }
type Expression<T> = string | (() => T | Promise<T>)
type Pending = { resolve: (value: any) => void; reject: (error: Error) => void; timer: ReturnType<typeof setTimeout> }
const pause = (ms: number) => new Promise<void>(resolve => setTimeout(resolve, ms))

/** A real Chrome page. HTTP fixtures and their assertions belong to the calling module. */
export class BrowserPage {
  private sequence = 0
  private pending = new Map<number, Pending>()
  private closed = false
  readonly metadata: { executable: string; profile: string; version: string; pid: number | undefined }

  constructor(private socket: WebSocket, private process: ChildProcess, executable: string, profile: string) {
    this.metadata = { executable, profile, version: '', pid: process.pid }
    socket.addEventListener('message', event => {
      const message = JSON.parse(String(event.data))
      const item = this.pending.get(message.id)
      if (!item) return
      this.pending.delete(message.id); clearTimeout(item.timer)
      if (message.error) item.reject(new Error('BROWSER_PROTOCOL_ERROR: ' + JSON.stringify(message.error)))
      else item.resolve(message.result)
    })
    socket.addEventListener('close', () => this.rejectPending('BROWSER_CONNECTION_CLOSED'))
    socket.addEventListener('error', () => this.rejectPending('BROWSER_CONNECTION_ERROR'))
  }

  private rejectPending(message: string) {
    for (const item of this.pending.values()) { clearTimeout(item.timer); item.reject(new Error(message)) }
    this.pending.clear()
  }

  private send<T = any>(method: string, params: Record<string, unknown> = {}): Promise<T> {
    if (this.socket.readyState !== WebSocket.OPEN) return Promise.reject(new Error('BROWSER_CONNECTION_CLOSED'))
    return new Promise((resolve, reject) => {
      const id = ++this.sequence
      const timer = setTimeout(() => { this.pending.delete(id); reject(new Error('BROWSER_COMMAND_TIMEOUT: ' + method)) }, 10000)
      this.pending.set(id, { resolve, reject, timer })
      this.socket.send(JSON.stringify({ id, method, params }))
    })
  }

  async evaluate<T = unknown>(expression: Expression<T>): Promise<T> {
    const source = typeof expression === 'function' ? `(${expression.toString()})()` : expression
    const value = await this.send('Runtime.evaluate', { expression: source, returnByValue: true, awaitPromise: true, userGesture: true })
    if (value.exceptionDetails) throw new Error('BROWSER_EVALUATION_FAILED: ' + (value.exceptionDetails.exception?.description ?? value.exceptionDetails.text))
    return value.result.value as T
  }

  async waitFor<T>(condition: Expression<T>, { timeoutMs = 5000, pollMs = 25 }: WaitOptions = {}): Promise<T> {
    const deadline = Date.now() + timeoutMs
    do {
      try {
        const value = await this.evaluate(condition)
        if (value) return value
      } catch (error) {
        // A navigation may briefly destroy the preceding document context.
        if (!(error instanceof Error) || !/Cannot find context|Execution context was destroyed/.test(error.message)) throw error
      }
      await pause(pollMs)
    } while (Date.now() < deadline)
    throw new Error('BROWSER_WAIT_TIMEOUT: condition did not become true')
  }

  async goto(address: string) {
    const url = new URL(address)
    if (!['http:', 'https:'].includes(url.protocol) || !['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname) || url.username || url.password) {
      throw new Error('BROWSER_ORIGIN_NOT_LOCAL')
    }
    await this.send('Page.enable')
    // A marker prevents the old document's readyState from satisfying navigation.
    await this.evaluate('window.__campusPreviousDocument = true')
    const result = await this.send('Page.navigate', { url: url.href })
    if (result.errorText) throw new Error('BROWSER_NAVIGATION_FAILED: ' + result.errorText)
    if (result.loaderId) await this.waitFor("!window.__campusPreviousDocument && document.readyState === 'complete'")
  }

  async text(selector: string): Promise<string> {
    return this.evaluate(`(() => {
      const elements = document.querySelectorAll(${JSON.stringify(selector)});
      if (elements.length !== 1) throw new Error('ELEMENT_MATCH_COUNT: ' + elements.length);
      return elements[0].textContent?.trim() ?? '';
    })()`)
  }

  async click(selector: string) {
    const point = await this.evaluate<{ x: number; y: number }>(`(() => {
      const elements = document.querySelectorAll(${JSON.stringify(selector)});
      if (elements.length !== 1) throw new Error('ELEMENT_MATCH_COUNT: ' + elements.length);
      const element = elements[0];
      if (element.disabled || element.getAttribute('aria-disabled') === 'true') throw new Error('ELEMENT_DISABLED');
      element.scrollIntoView({block:'center', inline:'center'});
      const rect = element.getBoundingClientRect();
      if (!rect.width || !rect.height) throw new Error('ELEMENT_NOT_VISIBLE');
      return {x:rect.x + rect.width/2, y:rect.y + rect.height/2};
    })()`)
    await this.send('Input.dispatchMouseEvent', { type: 'mouseMoved', ...point })
    await this.send('Input.dispatchMouseEvent', { type: 'mousePressed', button: 'left', clickCount: 1, ...point })
    await this.send('Input.dispatchMouseEvent', { type: 'mouseReleased', button: 'left', clickCount: 1, ...point })
  }

  async fill(selector: string, value: string) {
    await this.evaluate(`(() => {
      const elements = document.querySelectorAll(${JSON.stringify(selector)});
      if (elements.length !== 1) throw new Error('ELEMENT_MATCH_COUNT: ' + elements.length);
      const element = elements[0];
      if (!(element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement) || element.disabled || element.readOnly) throw new Error('ELEMENT_NOT_EDITABLE');
      element.focus();
    })()`)
    const selectAll = process.platform === 'darwin' ? 4 : 2
    await this.send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'a', code: 'KeyA', windowsVirtualKeyCode: 65, modifiers: selectAll })
    await this.send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'a', code: 'KeyA', windowsVirtualKeyCode: 65, modifiers: selectAll })
    await this.send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Backspace', code: 'Backspace', windowsVirtualKeyCode: 8 })
    await this.send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Backspace', code: 'Backspace', windowsVirtualKeyCode: 8 })
    if (value) await this.send('Input.insertText', { text: value })
  }

  async screenshot(destination?: string): Promise<Buffer> {
    const result = await this.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
    const image = Buffer.from(result.data, 'base64')
    if (destination) {
      const target = resolve(destination)
      const reports = resolve(tmpdir(), 'campus-reports')
      if (!isAbsolute(destination) || !(target.startsWith(reports + sep) || target.split(sep).includes('.campus'))) {
        throw new Error('SCREENSHOT_REQUIRES_PRIVATE_OUTPUT_PATH')
      }
      await mkdir(dirname(target), { recursive: true }); await writeFile(target, image)
    }
    return image
  }

  async close() {
    if (this.closed) return
    this.closed = true
    if (this.socket.readyState === WebSocket.OPEN) await this.send('Browser.close').catch(() => undefined)
    for (let i = 0; i < 60 && this.process.exitCode === null && this.process.signalCode === null; i++) await pause(50)
    if (this.process.exitCode === null && this.process.signalCode === null) this.process.kill('SIGKILL')
    this.socket.close(); this.rejectPending('BROWSER_CLOSED')
    if (dirname(resolve(this.metadata.profile)) !== resolve(tmpdir()) || !basename(this.metadata.profile).startsWith('campus-browser-')) throw new Error('BROWSER_PROFILE_PATH_INVALID')
    await rm(this.metadata.profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 })
  }
}

export async function startBrowser({ executable, timeoutMs = 15000 }: BrowserOptions = {}): Promise<BrowserPage> {
  const browser = executable ?? process.env.CAMPUS_TEST_BROWSER ?? [
    '/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser',
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  ].find(existsSync)
  if (!browser || !existsSync(browser)) throw new Error('BROWSER_NOT_FOUND: install the approved browser image or set CAMPUS_TEST_BROWSER')
  const profile = await mkdtemp(join(tmpdir(), 'campus-browser-'))
  const child = spawn(browser, ['--headless=new', '--no-sandbox', '--disable-dev-shm-usage', '--no-first-run', '--no-default-browser-check',
    '--disable-background-networking', '--disable-extensions', '--no-proxy-server', '--remote-debugging-address=127.0.0.1',
    '--remote-debugging-port=0', '--window-size=1280,800', '--user-data-dir=' + profile, 'about:blank'], { stdio: ['ignore', 'ignore', 'pipe'], windowsHide: true })
  let diagnostic = '', spawnError: Error | undefined
  child.stderr?.on('data', chunk => { diagnostic = (diagnostic + chunk.toString()).slice(-4000) })
  child.on('error', error => { spawnError = error })
  try {
    const deadline = Date.now() + timeoutMs
    let port = 0
    while (Date.now() < deadline) {
      if (spawnError || child.exitCode !== null || child.signalCode !== null) throw new Error('BROWSER_START_FAILED: ' + (spawnError?.message ?? diagnostic))
      try { port = Number((await readFile(join(profile, 'DevToolsActivePort'), 'utf8')).split('\n')[0]); if (port > 0) break } catch { /* File appears when Chrome is ready. */ }
      await pause(25)
    }
    if (!port) throw new Error('BROWSER_START_TIMEOUT: ' + diagnostic)
    const response = await fetch(`http://127.0.0.1:${port}/json/list`, { signal: AbortSignal.timeout(3000) })
    if (!response.ok) throw new Error('BROWSER_TARGET_LIST_FAILED')
    const targets = await response.json() as { type: string; webSocketDebuggerUrl: string }[]
    const target = targets.find(page => page.type === 'page')
    if (!target) throw new Error('BROWSER_PAGE_MISSING')
    const socket = new WebSocket(target.webSocketDebuggerUrl)
    await new Promise<void>((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('BROWSER_CONNECTION_TIMEOUT')), 3000)
      socket.addEventListener('open', () => { clearTimeout(timer); resolve() }, { once: true })
      socket.addEventListener('error', () => { clearTimeout(timer); reject(new Error('BROWSER_CONNECTION_ERROR')) }, { once: true })
    })
    const page = new BrowserPage(socket, child, browser, profile)
    page.metadata.version = await page.evaluate('navigator.userAgent')
    return page
  } catch (error) {
    child.kill('SIGKILL')
    await rm(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 })
    throw error
  }
}
