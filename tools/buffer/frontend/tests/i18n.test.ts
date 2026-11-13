import { describe, expect, it } from 'vitest'
import { messages } from '../src/i18n'
describe('buffer locale coverage',()=>{it('covers every UI message in both languages',()=>{expect(Object.keys(messages['zh-CN']).sort()).toEqual(Object.keys(messages.en).sort());expect(messages.en.AUTHENTICATION_REQUIRED).toContain('access code')})})
