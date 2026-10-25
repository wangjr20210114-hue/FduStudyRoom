import type zhCN from './locales/zh-CN'

type MessageSchema = typeof zhCN

declare module 'vue-i18n' {
  export interface DefineLocaleMessage extends MessageSchema {}
}
