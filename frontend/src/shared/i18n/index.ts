import { watch } from 'vue'
import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN'
import en from './locales/en'
import { moduleMessages } from './module-messages'
const modules = moduleMessages(import.meta.glob<{ default: Record<string, unknown> }>('../../modules/*/i18n/*.ts', { eager: true }))
import { defaultLocale, isAppLocale, readSavedLocale, saveLocale, type AppLocale } from './locale'

export const i18n = createI18n({
  legacy: false,
  globalInjection: false,
  locale: readSavedLocale(),
  fallbackLocale: defaultLocale,
  messages: { 'zh-CN': { ...zhCN, modules: modules['zh-CN'] }, en: { ...en, modules: modules.en } },
})

watch(i18n.global.locale, (locale) => {
  if (!isAppLocale(locale)) return
  if (typeof document !== 'undefined') {
    document.documentElement.lang = locale
    document.title = i18n.global.t('app.title')
  }
  saveLocale(locale)
}, { immediate: true, flush: 'sync' })

export function setAppLocale(locale: AppLocale): void {
  if (isAppLocale(locale)) i18n.global.locale.value = locale
}

export function getAppLocale(): AppLocale {
  const locale = i18n.global.locale.value
  return isAppLocale(locale) ? locale : defaultLocale
}

export type { AppLocale } from './locale'
