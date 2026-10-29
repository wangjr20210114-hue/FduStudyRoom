import { describe, expect, it } from 'vitest'
import { createSSRApp, h } from 'vue'
import { renderToString } from 'vue/server-renderer'
import AppTextField from '../src/shared/components/AppTextField.vue'

describe('shared input native constraints', () => {
  it('keeps password length constraints on the input rendered by Naive UI', async () => {
    const app=createSSRApp({render:()=>h(AppTextField, {
      modelValue:'',label:'Password',type:'password',minLength:8,maxLength:64,
    })})
    const html=await renderToString(app)
    const input=html.match(/<input\b[^>]*>/)?.[0]
    expect(input).toContain('type="password"')
    expect(input).toContain('minlength="8"')
    expect(input).toContain('maxlength="64"')
  })
})
