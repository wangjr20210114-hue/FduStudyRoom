import { beforeAll, beforeEach, afterAll, afterEach, expect, it } from 'vitest'
import { createServer, type ViteDevServer } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'
import { startBrowser, type BrowserPage } from '../../shared/browser'

const userId = '00000000-0000-0000-0000-000000000001'
const otherId = '00000000-0000-0000-0000-000000000002'
const roomId = '00000000-0000-0000-0000-000000000003'
const stamp = '2026-01-01T09:00:00Z'
const uuid = (n: number) => `10000000-0000-0000-0000-${String(n).padStart(12, '0')}`
const fullPermissions = ['identity:read', 'notification:read', 'audit:read', 'statistics:read', 'resource:rooms:read']
const notification = (n = 1) => ({ id: uuid(n), eventId: uuid(n + 100), recipientId: userId, sourceModule: 'M1', sourceId: uuid(n + 200), title: `教室 R101 提醒 ${n}`, body: '09:00—10:00 自习预约', titleEn: `Room R101 reminder ${n}`, bodyEn: 'Study booking 09:00–10:00', locale: 'zh-CN', scheduledAt: stamp, status: 'SENT', emailStatus: 'NOT_CONFIGURED', lastErrorCode: 'SMTP_NOT_CONFIGURED', nextRetryAt: null as string | null, readAt: null as string | null })
const audit = (n = 1) => ({ id: uuid(n), eventKey: `case:${n}`, sourceService: 'workflow-service', actorId: userId, action: n % 2 ? 'APPROVAL_APPROVE' : 'RESOURCE_DENIED', sourceModule: n % 2 ? 'M2' : 'D', sourceId: uuid(n + 200), success: !!(n % 2), traceId: `trace-${n}`, occurredAt: stamp, beforeData: { status: 'PENDING' } as Record<string, unknown>, afterData: { status: n % 2 ? 'APPROVED' : '403' } as Record<string, unknown> })
const statistics = () => ({ status: 'OK', algorithm: 'P08-retained-seat-seconds-and-deadline-cohort-v1', from: stamp, to: '2026-01-01T12:00:00Z', asOf: '2026-01-01T12:00:00Z', roomId: null, capacitySeatSeconds: 28800, occupiedSeatSeconds: 10800, occupiedM1SeatSeconds: 3600, occupiedM2SeatSeconds: 7200, confirmedBookingCount: 2, confirmedM1BookingCount: 1, confirmedM2BookingCount: 1, checkedInCount: 1, noShowCount: 1, cohortCount: 2, checkInRate: 0.5, noShowRate: 0.5, occupancyRate: 0.375, pendingEvaluationCount: 0, excludedUnconfirmedCount: 0, excludedBeforeStartCancelledCount: 1, excludedSystemTerminatedCount: 0, notDueCount: 0, outcomesComplete: true, historyComplete: true, historyAvailableFrom: null, sources: [{ service: 'resource', algorithm: 'retained-capacity' }, { service: 'reservation', algorithm: 'deadline-cohort' }] })
let current: { id: string; loginName: string; displayName: string; role: string; permissions: string[]; authVersion: number; profileIncomplete: boolean }
let messages: ReturnType<typeof notification>[]; let audits: ReturnType<typeof audit>[]; let stats: Record<string, unknown>
let statuses: Record<string, number>; let lostReadAck = false; let requests: { method: string; url: URL; locale: string; csrf?: string }[]
let server: ViteDevServer; let origin: string; let page: BrowserPage | undefined
beforeEach(() => {
  current = { id: userId, loginName: 'fixture-user', displayName: 'Fixture User', role: 'USER', permissions: [...fullPermissions], authVersion: 1, profileIncomplete: false }
  messages = [notification(1), notification(2), { ...notification(3), recipientId: otherId }]
  audits = [audit(1), audit(2)]; stats = statistics(); statuses = {}; requests = []; lostReadAck = false
})
beforeAll(async () => {
  server = await createServer({ configFile: false, root: fileURLToPath(new URL('../../../', import.meta.url)), plugins: [vue(), { name: 'support-owned-http-fixture', configureServer(vite) {
    vite.middlewares.use((req, res, next) => {
      if (!req.url?.startsWith('/api/')) return next()
      const url = new URL(req.url, 'http://127.0.0.1'); const method = req.method ?? 'GET'; const locale = req.headers['accept-language']?.startsWith('en') ? 'en' : 'zh-CN'
      requests.push({ method, url, locale, csrf: req.headers['x-xsrf-token'] as string | undefined })
      const send = (status: number, value?: unknown) => { res.statusCode = status; res.setHeader('Content-Type', 'application/json'); res.end(status === 204 ? undefined : JSON.stringify(value)) }
      if (statuses[url.pathname]) return send(statuses[url.pathname]!, { code: statuses[url.pathname] === 403 ? 'FORBIDDEN' : 'STATISTICS_SOURCE_UNAVAILABLE' })
      const pagination = <T,>(items: T[]) => { const p = Number(url.searchParams.get('page') ?? 0); const size = Number(url.searchParams.get('size') ?? 20); return { items: items.slice(p * size, (p + 1) * size), total: items.length, page: p, size } }
      if (url.pathname === '/api/auth/config') return send(200, { mode: 'password', available: true, registrationOpen: true })
      if (url.pathname === '/api/auth/me') return send(200, current)
      if (url.pathname === '/api/auth/csrf') return send(200, { headerName: 'X-XSRF-TOKEN', token: 'fixture-csrf-token' })
      if (url.pathname === '/api/notifications' && method === 'GET') return send(200, pagination(messages.filter(n => n.recipientId === current.id).map(n => ({ ...n, title: locale === 'en' ? n.titleEn : n.title, body: locale === 'en' ? n.bodyEn : n.body, locale }))))
      if (/^\/api\/notifications\/[^/]+\/read$/.test(url.pathname) && method === 'POST') {
        if (req.headers['x-xsrf-token'] !== 'fixture-csrf-token') return send(403, { code: 'CSRF_INVALID' })
        const row = messages.find(n => n.id === url.pathname.split('/')[3] && n.recipientId === current.id)
        if (!row) return send(404, { code: 'NOTIFICATION_NOT_FOUND' })
        row.readAt ??= stamp
        if (lostReadAck) { lostReadAck = false; return send(503, { code: 'FIXTURE_LOST_ACK' }) }
        return send(204)
      }
      if (url.pathname === '/api/audit' && method === 'GET') return send(200, pagination(audits.filter(a => (!url.searchParams.get('traceId') || a.traceId === url.searchParams.get('traceId')) && (!url.searchParams.get('sourceModule') || a.sourceModule === url.searchParams.get('sourceModule')) && (!url.searchParams.get('sourceId') || a.sourceId === url.searchParams.get('sourceId')))))
      if (url.pathname === '/api/statistics' && method === 'GET') return send(200, { ...stats, ...Object.fromEntries(['from', 'to', 'asOf', 'roomId'].map(key => [key, url.searchParams.get(key)])) })
      if (url.pathname === '/api/resources/rooms') return send(200, [{ id: roomId, code: 'R101', name: 'R101', enabled: true }])
      return send(404, { code: 'FIXTURE_UNEXPECTED_PUBLIC_ENDPOINT' })
    })
  } }], resolve: { alias: { '@': fileURLToPath(new URL('../../../src', import.meta.url)) } }, server: { host: '127.0.0.1', port: 0, strictPort: false }, logLevel: 'error' })
  await server.listen(); const address = server.httpServer!.address(); if (!address || typeof address === 'string') throw new Error('FIXTURE_SERVER_ADDRESS')
  origin = `http://127.0.0.1:${address.port}`
}, 30000)
afterAll(async () => { await server?.close() })
afterEach(async context => {
  if (page) { if (context.task.result?.state === 'fail') await page.screenshot(`/tmp/campus-reports/support-${context.task.name.replace(/[^A-Za-z0-9_-]/g, '_')}-${Date.now()}.png`).catch(() => undefined); await page.close(); page = undefined }
})
async function open(path: 'notifications' | 'audit' | 'statistics', required = true) {
  page = await startBrowser(); await page.goto(origin + '/' + path); await page.waitFor("document.querySelector('#main-content h1')")
  if (required) expect(await page.evaluate(`!!document.querySelector('[data-${path}-page]')`), `${path} must expose the requested business page`).toBe(true)
  return page
}
async function content() { return page!.evaluate<string>("document.querySelector('#main-content')?.textContent ?? ''") }
async function rows(selector: string) { return page!.evaluate<string[]>(`Array.from(document.querySelectorAll(${JSON.stringify(selector)})).map(e=>e.getAttribute('data-id'))`) }
async function waitRows(selector: string, count: number) { await page!.waitFor(`document.querySelectorAll(${JSON.stringify(selector)}).length === ${count}`) }
async function language() { await page!.evaluate("(()=>{const s=document.querySelector('.language-switcher select');s.value='en';s.dispatchEvent(new Event('change',{bubbles:true}));})()") }
async function select(selector: string, value: string) { await page!.evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});e.value=${JSON.stringify(value)};e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));})()`) }

it('SUP-01 own messages and read confirmation', async () => {
  const p = await open('notifications'); await waitRows('[data-notification]', 2); expect(await rows('[data-notification]')).toEqual([uuid(1), uuid(2)])
  await p.click(`[data-notification][data-id="${uuid(1)}"] [data-mark-read]`); await p.waitFor(`document.querySelector('[data-notification][data-id="${uuid(1)}"] [data-read-at]')`)
  expect(messages[0]!.readAt).toBe(stamp); expect(messages[1]!.readAt).toBeNull(); expect(requests.filter(r => r.method === 'POST')).toHaveLength(1)
  expect(requests.find(r => r.method === 'POST')?.csrf).toBe('fixture-csrf-token'); expect(requests.every(r => !r.url.searchParams.has('recipientId') && !r.url.searchParams.has('role'))).toBe(true)
  await language(); await p.waitFor("document.querySelector('[data-notification]')?.textContent.includes('Room R101 reminder')"); expect(await content()).toContain('Study booking 09:00–10:00'); expect(await content()).not.toContain('提醒 3')
}, 30000)
it('SUP-01 delivery states and retry metadata', async () => {
  messages = ['NOT_CONFIGURED', 'PENDING', 'SENT', 'FAILED', 'UNKNOWN', 'CANCELLED'].map((emailStatus, i) => ({ ...notification(i + 1), emailStatus, lastErrorCode: emailStatus === 'FAILED' ? 'SMTP_REJECTED' : '', nextRetryAt: emailStatus === 'FAILED' ? stamp : null }))
  await open('notifications'); await waitRows('[data-notification]', 6)
  expect(await page!.text(`[data-id="${uuid(3)}"] [data-email-status]`)).toBe('邮件服务已接收')
  expect(await page!.text(`[data-id="${uuid(5)}"] [data-email-status]`)).toBe('待核实')
  expect(await page!.evaluate(`[...document.querySelectorAll('[data-email-status]')].map(e=>e.getAttribute('data-email-status'))`)).toEqual(messages.map(m => m.emailStatus))
  expect(await page!.evaluate(`document.querySelector('[data-id="${uuid(4)}"] [data-retry-at]')?.getAttribute('datetime')`)).toBe(stamp)
  expect(await content()).toContain('邮件服务拒绝接收'); expect(await content()).toContain('未配置'); await language(); await page!.waitFor("document.querySelector('[data-email-status=UNKNOWN]')?.textContent === 'Needs confirmation'")
}, 30000)
it('SUP-01 refresh preserves delivered and cancelled history', async () => {
  messages = [{ ...notification(1), emailStatus: 'SENT' }, { ...notification(2), status: 'CANCELLED', emailStatus: 'CANCELLED' }]
  const p = await open('notifications'); await waitRows('[data-notification]', 2); await p.click('[data-notification-refresh]'); await waitRows('[data-notification]', 2)
  expect(await rows('[data-notification]')).toEqual([uuid(1), uuid(2)]); expect(await content()).toContain('已撤销'); expect(requests.filter(r => r.method !== 'GET')).toHaveLength(0)
}, 30000)
it('SUP-01 uncertain delivery stays uncertain after refresh', async () => {
  messages = [{ ...notification(1), emailStatus: 'UNKNOWN', lastErrorCode: 'SMTP_DELIVERY_UNCERTAIN' }]
  const p = await open('notifications'); await waitRows('[data-notification]', 1); await p.click('[data-notification-refresh]'); await waitRows('[data-notification]', 1)
  expect(await p.text('[data-email-status]')).toBe('待核实'); expect(await content()).not.toContain('邮件服务已接收'); expect(await p.evaluate("document.querySelectorAll('[data-resend]').length")).toBe(0); expect(requests.filter(r => r.method !== 'GET')).toHaveLength(0)
}, 30000)
it('SUP-01 lost read acknowledgement recovers from server', async () => {
  messages = [notification(1)]; lostReadAck = true; const p = await open('notifications'); await waitRows('[data-notification]', 1)
  await p.click('[data-mark-read]'); await p.waitFor("document.querySelector('[data-notification-error]')"); expect(messages[0]!.readAt).toBe(stamp)
  await p.click('[data-notification-refresh]'); await p.waitFor("document.querySelector('[data-read-at]')"); expect(requests.filter(r => r.method === 'POST')).toHaveLength(1); expect(await rows('[data-notification]')).toEqual([uuid(1)])
}, 30000)
it('SUP-01 permission denial clears messages', async () => {
  const p = await open('notifications'); await waitRows('[data-notification]', 2); statuses['/api/notifications'] = 403
  await p.click('[data-notification-refresh]'); await p.waitFor("document.querySelector('[data-notification-error]')"); expect(await rows('[data-notification]')).toEqual([])
}, 30000)
it.each([19, 20, 21, 41])('SUP-01 pagination %i', async n => {
  messages = Array.from({ length: n }, (_, i) => notification(i + 1)); const p = await open('notifications'); await waitRows('[data-notification]', Math.min(20, n))
  expect(await p.evaluate("document.querySelector('[data-notification-prev]').disabled")).toBe(true)
  expect(await p.evaluate("document.querySelector('[data-notification-next]').disabled")).toBe(n <= 20)
  if (n > 20) { await p.click('[data-notification-next]'); await p.waitFor(`document.querySelector('[data-notification]')?.getAttribute('data-id') === '${uuid(21)}'`); expect((await rows('[data-notification]')).length).toBe(Math.min(20, n - 20)); await p.click('[data-notification-prev]'); await p.waitFor(`document.querySelector('[data-notification]')?.getAttribute('data-id') === '${uuid(1)}'`) }
}, 30000)
it('SUP-02 trace and source filters expose trusted results', async () => {
  const p = await open('audit'); await waitRows('[data-audit-event]', 2); const text = await content(); for (const expected of [userId, uuid(201), 'trace-1', 'trace-2']) expect(text).toContain(expected)
  expect(await p.evaluate("document.querySelectorAll('[data-audit-success=true]').length")).toBe(1); expect(await p.evaluate("document.querySelectorAll('[data-audit-success=false]').length")).toBe(1)
  await p.fill('[data-audit-trace]', 'trace-1'); await select('[data-audit-source]', 'M2'); await p.fill('[data-audit-source-id]', uuid(201)); await p.click('[data-audit-search]'); await waitRows('[data-audit-event]', 1)
  const req = requests.filter(r => r.url.pathname === '/api/audit').at(-1)!; expect(Object.fromEntries(req.url.searchParams)).toEqual({ page: '0', size: '20', traceId: 'trace-1', sourceModule: 'M2', sourceId: uuid(201) }); await language(); await p.waitFor("document.querySelector('[data-audit-page]')?.textContent.includes('Trace ID')")
}, 30000)
it('SUP-02 safe summaries omit secret request fields', async () => {
  audits = [{ ...audit(1), beforeData: { status: 'PENDING', password: 'DO_NOT_RENDER_PASSWORD' }, afterData: { status: 'APPROVED', score: 5, Cookie: 'DO_NOT_RENDER_COOKIE', signingKey: 'DO_NOT_RENDER_KEY', rawRequest: 'DO_NOT_RENDER_REQUEST', nested: { status: 'DO_NOT_RENDER_NESTED' } } }]
  const p = await open('audit'); await waitRows('[data-audit-event]', 1); await p.click('[data-audit-summary] summary'); const text = await content()
  for (const marker of ['DO_NOT_RENDER_', 'password', 'Cookie', 'signingKey', 'rawRequest']) expect(text).not.toContain(marker)
  expect(text).toContain('PENDING'); expect(text).toContain('APPROVED'); expect(text).toContain('5')
}, 30000)
it('SUP-02 unauthorized role never fetches audit', async () => {
  current.role = 'ADMIN'; current.permissions = ['identity:read']; await open('audit', false); expect(requests.filter(r => r.url.pathname === '/api/audit')).toHaveLength(0); expect(await page!.evaluate("location.pathname")).toBe('/forbidden')
}, 30000)
it('SUP-02 server denial removes stale audit', async () => {
  const p = await open('audit'); await waitRows('[data-audit-event]', 2); statuses['/api/audit'] = 403; await p.click('[data-audit-search]'); await p.waitFor("document.querySelector('[data-audit-error]')"); expect(await rows('[data-audit-event]')).toEqual([])
}, 30000)
it.each([19, 20, 21, 41])('SUP-02 pagination %i', async n => {
  audits = Array.from({ length: n }, (_, i) => audit(i + 1)); const p = await open('audit'); await waitRows('[data-audit-event]', Math.min(20, n)); expect(await p.evaluate("document.querySelector('[data-audit-prev]').disabled")).toBe(true); expect(await p.evaluate("document.querySelector('[data-audit-next]').disabled")).toBe(n <= 20)
  if (n > 20) { await p.click('[data-audit-next]'); await p.waitFor(`document.querySelector('[data-audit-event]')?.getAttribute('data-id') === '${uuid(21)}'`); await p.click('[data-audit-prev]'); await p.waitFor(`document.querySelector('[data-audit-event]')?.getAttribute('data-id') === '${uuid(1)}'`) }
}, 30000)
it('SUP-03 occupancy and outcome cohorts use aggregates', async () => {
  await open('statistics'); await page!.waitFor("document.querySelector('[data-stat=occupancyRate]')"); expect(await page!.text('[data-stat=occupancyRate]')).toBe('37.5%'); expect(await page!.text('[data-stat=checkInRate]')).toBe('50%'); expect(await page!.text('[data-stat=noShowRate]')).toBe('50%'); expect(await page!.text('[data-stat=capacityMinutes]')).toBe('480'); expect(await page!.text('[data-stat=occupiedMinutes]')).toBe('180'); expect(await content()).toContain('预约占用率'); expect(await page!.text('[data-stat=excludedBeforeStartCancelledCount]')).toBe('1')
}, 30000)
it('SUP-03 zero capacity and empty cohort keep null rates', async () => {
  stats = { ...statistics(), status: 'NO_CAPACITY', capacitySeatSeconds: 0, occupiedSeatSeconds: 0, occupancyRate: null, checkInRate: null, noShowRate: null, checkedInCount: 0, noShowCount: 0, cohortCount: 0 }
  await open('statistics'); await page!.waitFor("document.querySelector('[data-stat=occupancyRate]')"); for (const key of ['occupancyRate', 'checkInRate', 'noShowRate']) expect(await page!.text(`[data-stat=${key}]`)).toBe('—'); const text = await content(); expect(text).toContain('无可用容量'); expect(text).not.toMatch(/NaN|Infinity|100%/)
}, 30000)
it('SUP-03 pending and incomplete history keep null outcomes', async () => {
  stats = { ...statistics(), status: 'PENDING_EVALUATION', pendingEvaluationCount: 2, outcomesComplete: false, checkInRate: null, noShowRate: null }
  const p = await open('statistics'); await p.waitFor("document.querySelector('[data-stat=pendingEvaluationCount]')"); expect(await p.text('[data-stat=checkInRate]')).toBe('—'); expect(await p.text('[data-stat=pendingEvaluationCount]')).toBe('2')
  stats = { ...stats, status: 'HISTORY_UNAVAILABLE', historyComplete: false, historyAvailableFrom: stamp, capacitySeatSeconds: null, occupiedSeatSeconds: null, occupancyRate: null }; await p.click('[data-statistics-search]'); await p.waitFor("document.querySelector('[data-statistics-history]')"); expect(await p.text('[data-stat=capacityMinutes]')).toBe('—'); expect(await p.text('[data-stat=occupancyRate]')).toBe('—'); expect(await p.evaluate("document.querySelector('[data-statistics-history] time')?.getAttribute('datetime')")).toBe(stamp)
}, 30000)
it('SUP-03 source failure clears previous statistics', async () => {
  const p = await open('statistics'); await p.waitFor("document.querySelector('[data-stat=occupancyRate]')"); statuses['/api/statistics'] = 503; await p.click('[data-statistics-search]'); await p.waitFor("document.querySelector('[data-statistics-error]')"); expect(await p.evaluate("document.querySelectorAll('[data-stat]').length")).toBe(0)
  delete statuses['/api/statistics']; await p.click('[data-statistics-search]'); await p.waitFor("document.querySelector('[data-stat=occupancyRate]')"); expect(await p.text('[data-stat=occupancyRate]')).toBe('37.5%')
}, 30000)
it('SUP-03 permission is independent of role name', async () => {
  current.permissions = ['identity:read', 'statistics:read']; current.role = 'USER'; await open('statistics'); await page!.waitFor("document.querySelector('[data-stat=occupancyRate]')"); expect(requests.filter(r => r.url.pathname === '/api/resources/rooms')).toHaveLength(0)
  await page!.close(); page = undefined; requests = []; current.permissions = ['identity:read']; current.role = 'ADMIN'; await open('statistics', false); expect(await page!.evaluate("location.pathname")).toBe('/forbidden'); expect(requests.filter(r => r.url.pathname === '/api/statistics')).toHaveLength(0)
}, 30000)
it('SUP-03 room and interval filters preserve scope', async () => {
  const p = await open('statistics'); const initialAsOf = await p.evaluate<string>("document.querySelector('[data-statistics-asof]').value"); expect(Math.abs(Date.now() - Date.parse(initialAsOf + '+08:00')), 'P01 default input is Shanghai wall time').toBeLessThan(60000); await p.waitFor(`document.querySelector('[data-statistics-room] option[value="${roomId}"]')`)
  await select('[data-statistics-from]', '2026-01-01T08:00:00'); await select('[data-statistics-to]', '2026-01-01T12:00:00'); await select('[data-statistics-asof]', '2026-01-01T12:00:00'); await select('[data-statistics-room]', roomId); await p.click('[data-statistics-search]'); await p.waitFor("document.querySelector('[data-stat=occupancyRate]')")
  const req = requests.filter(r => r.url.pathname === '/api/statistics').at(-1)!; expect(Object.fromEntries(req.url.searchParams)).toEqual({ from: '2026-01-01T00:00:00.000Z', to: '2026-01-01T04:00:00.000Z', asOf: '2026-01-01T04:00:00.000Z', roomId })
}, 30000)
it('SUP-03 English labels preserve source values', async () => {
  const p = await open('statistics'); await p.waitFor("document.querySelector('[data-stat=occupancyRate]')"); await language(); await p.waitFor("document.querySelector('[data-statistics-page]')?.textContent.includes('Booking occupancy')"); expect(await p.text('[data-stat=occupancyRate]')).toBe('37.5%'); expect(await content()).toContain('Checked-in'); expect(await content()).toContain('Capacity')
}, 30000)
