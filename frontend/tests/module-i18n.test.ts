import { describe, expect, it } from 'vitest'
import { moduleMessages } from '../src/shared/i18n/module-messages'
describe('module-owned dictionaries', () => {
  it('loads both locales without replacing another module', () => {
    const result = moduleMessages({
      '../../modules/resource/i18n/zh-CN.ts': { default: { title: '教室', action: { save: '保存' } } },
      '../../modules/resource/i18n/en.ts': { default: { title: 'Rooms', action: { save: 'Save' } } },
    })
    expect(result.en.resource).toEqual({ title: 'Rooms', action: { save: 'Save' } })
    expect(result['zh-CN'].resource).toEqual({ title: '教室', action: { save: '保存' } })
  })
  it('rejects missing languages and mismatched nested keys', () => {
    expect(() => moduleMessages({ '../../modules/resource/i18n/en.ts': { default: { title: 'Rooms' } } })).toThrow()
    expect(() => moduleMessages({ '../../modules/resource/i18n/en.ts': { default: { title: 'Rooms' } }, '../../modules/resource/i18n/zh-CN.ts': { default: { other: '教室' } } })).toThrow()
  })
})
