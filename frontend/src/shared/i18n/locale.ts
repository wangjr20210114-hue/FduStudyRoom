export const supportedLocales = ['zh-CN', 'en'] as const
export type AppLocale = (typeof supportedLocales)[number]
export const defaultLocale: AppLocale = 'zh-CN'
export const localeStorageKey = 'fdu-study-room.locale'

export function isAppLocale(value: unknown): value is AppLocale {
  return value === 'zh-CN' || value === 'en'
}

export function getLocaleStorage(): Storage | undefined {
  try {
    return typeof window === 'undefined' ? undefined : window.localStorage
  } catch {
    return undefined
  }
}

export function readSavedLocale(): AppLocale {
  try {
    const saved = getLocaleStorage()?.getItem(localeStorageKey)
    return isAppLocale(saved) ? saved : defaultLocale
  } catch {
    return defaultLocale
  }
}

export function saveLocale(locale: AppLocale): void {
  try {
    getLocaleStorage()?.setItem(localeStorageKey, locale)
  } catch {
    // Storage can be blocked or full; switching language still works in memory.
  }
}
