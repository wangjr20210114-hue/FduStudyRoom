import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { existsSync } from 'node:fs'
import { readFile, mkdtemp, rm } from 'node:fs/promises'
import { resolve, dirname, basename, join, extname } from 'node:path'
import { tmpdir } from 'node:os'
import { fileURLToPath } from 'node:url'
const execute = promisify(execFile)
const dist = resolve(process.env.CAMPUS_BUFFER_WEB_DIST ?? fileURLToPath(new URL('../dist', import.meta.url)))
const browser = process.env.CAMPUS_TEST_BROWSER ?? ['C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', '/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser', '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'].find(existsSync)
const profiles = {
  'fixture-member-code': { id: 'team-a', gitee: 'example-a', admin: false },
  'fixture-coordinator-code': { id: 'coordinator', gitee: '', admin: true },
}
const resources = { memory: { known: true, totalBytes: 4 * 1024 ** 3, availableBytes: 2 * 1024 ** 3 }, memoryUsedPercent: 50, pressure: false, thresholdPercent: 85, disk: { known: true, totalBytes: 60 * 1024 ** 3, availableBytes: 10 * 1024 ** 3 }, jvm: { usedBytes: 64 * 1024 ** 2, maxBytes: 256 * 1024 ** 2 }, queue: { pending_count: 0, pending_bytes: 0, ready_count: 0 }, verificationEnabled: false, syncEnabled: false, ciCanStart: false }
let server, origin
const requests = []
function harness(scenario) {
  return `<script type="module">(${async function run(scenario) {
    const observations = []
    const check = (ok, message) => { if (!ok) throw new Error(message); observations.push(message) }
    const wait = async (condition, message) => {
      for (let i = 0; i < 100; i++) { const value = condition(); if (value) return value; await new Promise(r => setTimeout(r, 20)) }
      throw new Error(message)
    }
    const fill = (input, value) => { input.value = value; input.dispatchEvent(new Event('input', { bubbles: true })) }
    const submit = () => document.querySelector('form.login button').click()
    let result
    try {
      const rememberedKey = 'fdu-buffer-access-code'
      if (scenario === 'remember-resume') {
        await wait(() => document.querySelector('header span')?.textContent === 'team-a', 'Opening the browser again must restore the member without typing a code')
        document.querySelector('header button').click()
        await wait(() => document.querySelector('form.login'), 'Sign out must return to login')
        check(localStorage.getItem(rememberedKey) === null, 'Sign out must remove the remembered code')
        check(document.querySelector('input[type="password"]').value === '', 'Sign out must clear the visible code')
      } else if (scenario === 'remember-invalid') {
        await wait(() => document.querySelector('[role="alert"]')?.textContent === '访问码无效', 'A revoked remembered code must return to login with an error')
        check(localStorage.getItem(rememberedKey) === null, 'An invalid remembered code must be forgotten')
        check(!document.querySelector('nav'), 'An invalid remembered code must not open the workspace')
      } else if (scenario === 'remember-unavailable') {
        await wait(() => document.querySelector('[role="alert"]'), 'An unavailable server must show an error')
        check(localStorage.getItem(rememberedKey) === 'fixture-member-code', 'A temporary server error must not forget the fixed code')
        check(!document.querySelector('nav'), 'An unavailable server must not invent an authenticated workspace')
      } else {
      await wait(() => document.querySelector('form.login input[type="password"]'), 'Login form must render')
      const password = document.querySelector('form.login input[type="password"]')
      if (scenario === 'form') {
        check(document.querySelectorAll('form.login input').length === 1, 'Login form must have exactly one access-code input')
        check(!document.querySelector('input[autocomplete="username"]'), 'Login must not ask for an identity')
        check(password.required, 'Access code must remain required')
        submit(); await new Promise(r => setTimeout(r, 80))
        check(!!document.querySelector('form.login'), 'Blank access code must keep the login form open')
      } else if (scenario === 'member' || scenario === 'coordinator' || scenario === 'remember-set') {
        const admin = scenario === 'coordinator'
        fill(password, admin ? 'fixture-coordinator-code' : 'fixture-member-code'); submit()
        const id = admin ? 'coordinator' : 'team-a'
        await wait(() => document.querySelector('header span')?.textContent === id, 'Access code alone must sign in as ' + id)
        await wait(() => document.querySelector('nav'), 'Signed-in navigation must render')
        check(!document.querySelector('form.login'), 'Successful login must close the login form')
        if (admin) await wait(() => document.querySelector('.review-form'), 'Coordinator acceptance controls must render')
        check(!!document.querySelector('.review-form') === admin, 'Review controls must match the recognized member role')
        if (scenario === 'remember-set') {
          check(localStorage.getItem(rememberedKey) === 'fixture-member-code', 'Successful login must remember the fixed code')
        } else {
        document.querySelector('header button').click()
        await wait(() => document.querySelector('form.login'), 'Sign out must reopen the login form')
        check(document.querySelector('input[type="password"]').value === '', 'Sign out must clear the access code')
        }
      } else {
        const english = scenario === 'invalid-en'
        if (english) {
          const language = document.querySelector('header select')
          language.value = 'en'; language.dispatchEvent(new Event('change', { bubbles: true }))
          await wait(() => document.documentElement.lang === 'en', 'Language must switch to English')
        }
        fill(password, 'fixture-invalid-code'); submit()
        const expected = english ? 'Invalid access code' : '访问码无效'
        await wait(() => document.querySelector('[role="alert"]')?.textContent === expected, 'Invalid code must show: ' + expected)
        check(!!document.querySelector('form.login'), 'Invalid code must keep the login form open')
        check(!document.querySelector('nav'), 'Invalid code must not open the workspace')
      }
      }
      result = { ok: true, scenario, observations }
    } catch (error) { result = { ok: false, scenario, observations, error: error.message } }
    const output = document.createElement('pre'); output.id = 'buffer-login-test-result'
    output.textContent = btoa(unescape(encodeURIComponent(JSON.stringify(result)))); document.body.append(output)
  }.toString()})(${JSON.stringify(scenario)})</script>`
}
before(async () => {
  assert.ok(browser && existsSync(browser), 'Install Chrome/Chromium or set CAMPUS_TEST_BROWSER')
  assert.ok(existsSync(join(dist, 'index.html')), 'Build the buffer frontend before running browser tests')
  server = createServer(async (req, res) => {
    try {
      const url = new URL(req.url, 'http://127.0.0.1')
      if (url.pathname.startsWith('/api/')) {
        const token = (req.headers.authorization ?? '').replace(/^Bearer /, '')
        const profile = profiles[token]
        requests.push({ path: url.pathname, token, referer: req.headers.referer ?? '' })
        res.setHeader('Content-Type', 'application/json')
        if (req.headers.referer?.includes('scenario=remember-unavailable')) { res.writeHead(503); res.end(JSON.stringify({ code: 'TEMPORARILY_UNAVAILABLE' })); return }
        if (!profile) { res.writeHead(401); res.end(JSON.stringify({ code: 'AUTHENTICATION_REQUIRED' })); return }
        const payload = url.pathname === '/api/me' ? profile : url.pathname === '/api/status' ? resources : url.pathname === '/api/schedule' ? { enabled: false, next_due: '2099-01-01T00:00:00Z', last_run: null } : []
        res.end(JSON.stringify(payload)); return
      }
      if (url.pathname === '/') {
        const html = await readFile(join(dist, 'index.html'), 'utf8')
        res.setHeader('Content-Type', 'text/html; charset=utf-8')
        const scenario = url.searchParams.get('scenario')
        const code = scenario === 'remember-invalid' ? 'fixture-invalid-code' : scenario === 'remember-unavailable' ? 'fixture-member-code' : null
        const bootstrap = code ? `<script>localStorage.setItem('fdu-buffer-access-code', ${JSON.stringify(code)})</script>` : ''
        res.end(html.replace('<head>', '<head>' + bootstrap).replace('</body>', harness(scenario) + '</body>')); return
      }
      if (!url.pathname.startsWith('/assets/') || url.pathname.includes('..')) { res.writeHead(404); res.end(); return }
      const file = resolve(dist, '.' + url.pathname)
      res.setHeader('Content-Type', extname(file) === '.js' ? 'text/javascript' : 'text/css')
      res.end(await readFile(file))
    } catch (error) { res.writeHead(500); res.end(String(error)) }
  })
  await new Promise(r => server.listen(0, '127.0.0.1', r)); origin = 'http://127.0.0.1:' + server.address().port
})
after(async () => { if (server) { server.closeAllConnections(); await new Promise(r => server.close(r)) } })
for (const [scenario, title] of [
  ['form', 'one required access-code field without identity'],
  ['member', 'member signs in with only a code and signs out'],
  ['coordinator', 'coordinator code identifies admin and preserves review controls'],
  ['invalid-zh', 'invalid access code stays on login with a Chinese error'],
  ['invalid-en', 'invalid access code stays on login with an English error'],
  ['remember-invalid', 'revoked remembered code is cleared without opening the workspace'],
  ['remember-unavailable', 'temporary server failure preserves a remembered code'],
]) {
  test(title, { timeout: 45000 }, async () => {
    const profile = await mkdtemp(join(tmpdir(), 'fdu-buffer-login-'))
    try {
      const { stdout, stderr } = await execute(browser, ['--headless=new', '--no-first-run', '--no-default-browser-check', '--user-data-dir=' + profile, '--virtual-time-budget=5000', '--dump-dom', origin + '/?scenario=' + scenario], { timeout: 35000, windowsHide: true, maxBuffer: 4 * 1024 * 1024 })
      const match = stdout.match(/<pre id="buffer-login-test-result">([^<]+)<\/pre>/)
      assert.ok(match, 'Browser must complete the UI checks: ' + stderr.slice(-1500))
      const result = JSON.parse(Buffer.from(match[1], 'base64').toString('utf8'))
      assert.equal(result.ok, true, JSON.stringify(result))
      if (scenario === 'member' || scenario === 'coordinator') assert.ok(requests.some(r => r.path === '/api/me' && r.referer.includes('scenario=' + scenario) && r.token === (scenario === 'member' ? 'fixture-member-code' : 'fixture-coordinator-code')), 'Login must authenticate using the access code')
      if (scenario === 'form') assert.ok(!requests.some(r => r.path === '/api/me' && r.referer.includes('scenario=form')), 'Browser validation must reject a blank code before requesting /me')
    } finally {
      assert.equal(dirname(resolve(profile)), resolve(tmpdir()), 'Only the created temporary profile may be removed')
      assert.ok(basename(profile).startsWith('fdu-buffer-login-'))
      await rm(profile, { recursive: true, force: true, maxRetries: 4, retryDelay: 200 })
    }
  })
}

test('fixed code survives browser restart and sign out removes it', { timeout: 90000 }, async () => {
  const profile = await mkdtemp(join(tmpdir(), 'fdu-buffer-login-'))
  try {
    for (const scenario of ['remember-set', 'remember-resume', 'form']) {
      const { stdout, stderr } = await execute(browser, ['--headless=new', '--no-first-run', '--no-default-browser-check', '--user-data-dir=' + profile, '--virtual-time-budget=5000', '--dump-dom', origin + '/?scenario=' + scenario], { timeout: 35000, windowsHide: true, maxBuffer: 4 * 1024 * 1024 })
      const match = stdout.match(/<pre id="buffer-login-test-result">([^<]+)<\/pre>/)
      assert.ok(match, 'Browser must complete remembered-login checks: ' + stderr.slice(-1000))
      const result = JSON.parse(Buffer.from(match[1], 'base64').toString('utf8'))
      assert.equal(result.ok, true, JSON.stringify(result))
    }
  } finally {
    assert.equal(dirname(resolve(profile)), resolve(tmpdir()))
    assert.ok(basename(profile).startsWith('fdu-buffer-login-'))
    await rm(profile, { recursive: true, force: true, maxRetries: 4, retryDelay: 200 })
  }
})
