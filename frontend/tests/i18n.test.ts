import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { i18n, setAppLocale } from '../src/shared/i18n'
import { localeStorageKey, readSavedLocale, saveLocale } from '../src/shared/i18n/locale'
import { ApiError } from '../src/shared/api/errors'
import { getApiErrorMessage } from '../src/shared/api/message'

beforeEach(() => { setAppLocale('zh-CN') })
afterEach(() => { vi.unstubAllGlobals(); setAppLocale('zh-CN') })

describe('language persistence and translated feedback', () => {
  it('defaults to Chinese when storage is absent or contains an unsupported value', () => {
    expect(readSavedLocale()).toBe('zh-CN')
    vi.stubGlobal('window', { localStorage: { getItem: () => 'fr' } })
    expect(readSavedLocale()).toBe('zh-CN')
  })

  it('reads the saved language and persists a new selection', () => {
    const setItem = vi.fn()
    vi.stubGlobal('window', { localStorage: { getItem: () => 'en', setItem } })
    expect(readSavedLocale()).toBe('en')
    saveLocale('zh-CN')
    expect(setItem).toHaveBeenCalledWith(localeStorageKey, 'zh-CN')
  })

  it('keeps language switching usable when access to browser storage is blocked', () => {
    vi.stubGlobal('window', { get localStorage() { throw new DOMException('Blocked', 'SecurityError') } })
    expect(readSavedLocale()).toBe('zh-CN')
    expect(() => setAppLocale('en')).not.toThrow()
    expect(i18n.global.t('common.retry')).toBe('Try again')
  })

  it('keeps language switching usable when saving fails', () => {
    vi.stubGlobal('window', { localStorage: { getItem: () => null, setItem: () => { throw new DOMException('Full', 'QuotaExceededError') } } })
    expect(() => setAppLocale('en')).not.toThrow()
    expect(i18n.global.locale.value).toBe('en')
  })

  it('translates an existing error using the currently selected language', () => {
    const error = new ApiError('timeout')
    expect(getApiErrorMessage(error)).toBe('请求超时，请稍后重试。')
    setAppLocale('en')
    expect(getApiErrorMessage(error)).toBe('The request timed out. Please try again.')
  })

  it.each([
    [401, 'Your session has expired. Please sign in again.'],
    [403, 'You do not have permission to perform this action.'],
    [409, 'The data has changed. Refresh and try again.'],
    [429, 'Too many requests. Please wait and try again.'],
    [503, 'The service is temporarily unavailable. Please try again later.'],
  ])('uses the shared message for HTTP %s', (status, message) => {
    setAppLocale('en')
    expect(getApiErrorMessage(new ApiError('http', { status }))).toBe(message)
  })

  it('uses a safe fallback for unknown failures', () => {
    expect(getApiErrorMessage(new Error('private server detail'))).toBe('操作未完成，请稍后重试。')
  })
})
