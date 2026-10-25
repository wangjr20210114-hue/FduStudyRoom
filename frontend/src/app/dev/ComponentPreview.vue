<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import {
  AppAlert,
  AppButton,
  AppLoading,
  AppPanel,
  AppStatusTag,
  AppTextField,
} from '@/shared/components'

const displayName = ref('')
const fieldError = ref('')
const formChecked = ref(false)
const buttonFeedback = ref('点击按钮，查看交互反馈。')
const loading = ref(false)
const retrying = ref(false)
const recovered = ref(false)
let actionTimer: ReturnType<typeof setTimeout> | undefined
let retryTimer: ReturnType<typeof setTimeout> | undefined

watch(displayName, () => {
  fieldError.value = ''
  formChecked.value = false
})

function checkInput() {
  fieldError.value = displayName.value.trim() ? '' : '请填写显示名称。'
  formChecked.value = !fieldError.value
}

function simulateLoading() {
  loading.value = true
  buttonFeedback.value = '正在演示加载状态，按钮暂时不可重复点击。'
  actionTimer = setTimeout(() => {
    loading.value = false
    buttonFeedback.value = '加载演示完成。'
  }, 1000)
}

function simulateRetry() {
  retrying.value = true
  retryTimer = setTimeout(() => {
    retrying.value = false
    recovered.value = true
  }, 800)
}

const rootStyles = getComputedStyle(document.documentElement)
const swatches = [
  { name: '主色', token: '--color-primary' },
  { name: '浅色背景', token: '--color-primary-soft' },
  { name: '成功', token: '--color-success' },
  { name: '警告', token: '--color-warning' },
  { name: '错误', token: '--color-error' },
].map((swatch) => ({
  ...swatch,
  value: rootStyles.getPropertyValue(swatch.token).trim().toUpperCase(),
}))

onBeforeUnmount(() => {
  clearTimeout(actionTimer)
  clearTimeout(retryTimer)
})
</script>

<template>
  <div class="component-preview">
    <a class="skip-link" href="#preview-main">跳到组件内容</a>
    <header class="preview-header">
      <a class="preview-brand" href="/" aria-label="FduStudyRoom 起始页">
        <img src="/favicon.svg" alt="" width="34" height="34" />
        <span>FduStudyRoom</span>
      </a>
      <a class="preview-back" href="/">返回起始页 <span aria-hidden="true">↗</span></a>
    </header>

    <main id="preview-main" class="preview-main">
      <section class="preview-intro" aria-labelledby="preview-title">
        <p class="preview-eyebrow">公共样式与基础组件</p>
        <h1 id="preview-title">清楚、一致的操作体验。</h1>
        <p class="preview-description">在这里检查按钮、输入与反馈的外观和交互。所有操作仅作用于本页预览。</p>
        <nav class="preview-links" aria-label="组件分类">
          <a href="#preview-actions">按钮与状态</a>
          <a href="#preview-form">表单输入</a>
          <a href="#preview-feedback">加载与反馈</a>
        </nav>
      </section>

      <div class="preview-grid">
        <AppPanel
          id="preview-actions"
          title="按钮与状态"
          description="操作层级清晰，加载与禁用状态明确。"
        >
          <template #actions><AppStatusTag tone="success">可交互预览</AppStatusTag></template>
          <div class="preview-stack">
            <div class="preview-button-row">
              <AppButton variant="primary" @click="buttonFeedback = '已点击主要操作。'">主要操作</AppButton>
              <AppButton @click="buttonFeedback = '已点击次要操作。'">次要操作</AppButton>
              <AppButton variant="ghost" @click="buttonFeedback = '已点击文字操作。'">文字操作</AppButton>
              <AppButton variant="danger" @click="buttonFeedback = '危险操作样式预览，没有执行删除。'">危险操作</AppButton>
            </div>
            <div class="preview-button-row">
              <AppButton size="small" @click="buttonFeedback = '已点击小号按钮。'">小号按钮</AppButton>
              <AppButton @click="buttonFeedback = '已点击标准按钮。'">标准按钮</AppButton>
              <AppButton size="large" @click="buttonFeedback = '已点击大号按钮。'">大号按钮</AppButton>
            </div>
            <div class="preview-button-row">
              <AppButton variant="primary" :loading="loading" @click="simulateLoading">演示加载</AppButton>
              <AppButton disabled>不可操作</AppButton>
            </div>
            <p class="preview-caption preview-event" role="status" aria-live="polite">{{ buttonFeedback }}</p>
            <div class="preview-divider" />
            <div class="preview-tag-row" aria-label="状态标签示例">
              <AppStatusTag>默认</AppStatusTag>
              <AppStatusTag tone="info">提示</AppStatusTag>
              <AppStatusTag tone="success">已完成</AppStatusTag>
              <AppStatusTag tone="warning">待处理</AppStatusTag>
              <AppStatusTag tone="error">失败</AppStatusTag>
            </div>
          </div>
        </AppPanel>

        <AppPanel id="preview-form" title="表单输入" description="标签、帮助和错误消息与输入框关联。">
          <form class="preview-stack" novalidate @submit.prevent="checkInput">
            <AppTextField
              v-model="displayName"
              label="显示名称"
              name="display-name"
              autocomplete="nickname"
              placeholder="怎么称呼你？"
              hint="试试留空后检查，再填写一个名称。"
              :error="fieldError"
              required
              clearable
            />
            <AppTextField
              model-value="此项暂不可编辑"
              label="禁用的输入框"
              hint="禁用状态不接受输入。"
              disabled
            />
            <AppButton variant="primary" html-type="submit" block>检查输入</AppButton>
            <AppAlert
              v-if="formChecked"
              type="success"
              title="输入检查通过"
              description="只更新当前预览，不会保存到服务器。"
            />
          </form>
        </AppPanel>

        <AppPanel id="preview-feedback" title="加载与反馈" description="说明正在发生什么，并提供下一步操作。">
          <div class="preview-stack">
            <AppLoading label="正在加载预览内容…" />
            <AppAlert type="info" title="消息提示" description="普通说明保持简短，不打断当前操作。" />
            <AppAlert type="warning" title="需要留意" description="用明确文字说明状态，颜色只作为辅助。" />
            <AppAlert
              v-if="!recovered"
              type="error"
              title="加载失败示例"
              description="可以重新尝试。这里不会发起实际网络请求。"
            >
              <template #actions>
                <AppButton size="small" :loading="retrying" @click="simulateRetry">重新尝试</AppButton>
              </template>
            </AppAlert>
            <AppAlert v-else type="success" title="重试演示完成" description="成功状态使用相同的消息结构。">
              <template #actions>
                <AppButton size="small" variant="ghost" @click="recovered = false">恢复错误示例</AppButton>
              </template>
            </AppAlert>
          </div>
        </AppPanel>

        <AppPanel title="统一的视觉基础" description="颜色、圆角和间距由公共样式维护。">
          <div class="preview-swatches">
            <div v-for="swatch in swatches" :key="swatch.token" class="preview-swatch">
              <span class="preview-swatch__color" :style="{ background: `var(${swatch.token})` }" aria-hidden="true" />
              <span>{{ swatch.name }}</span>
              <code>{{ swatch.value }}</code>
            </div>
          </div>
          <template #footer>
            <p class="preview-caption">输入框与按钮使用统一尺寸；组件文本由使用方传入，供后续国际化复用。</p>
          </template>
        </AppPanel>
      </div>
    </main>

    <footer class="preview-footer">组件预览仅在开发环境开放 · 不连接认证或预约服务</footer>
  </div>
</template>

<style scoped>
.component-preview { min-height: 100vh; padding: 0 var(--space-8); }
.preview-header,
.preview-main,
.preview-footer { width: min(100%, 1120px); margin-inline: auto; }
.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 88px;
  gap: var(--space-4);
  border-bottom: 1px solid var(--color-border);
}
.preview-brand {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  color: var(--color-text);
  font-size: 17px;
  font-weight: 600;
  text-decoration: none;
}
.preview-back { font-size: var(--font-size-small); text-decoration: none; white-space: nowrap; }
.preview-intro { padding-block: var(--space-12) var(--space-8); }
.preview-eyebrow { margin: 0 0 var(--space-3); color: var(--color-primary); font-size: var(--font-size-small); font-weight: 600; }
h1 { margin: 0; font-size: clamp(27px, 3.5vw, 40px); font-weight: 600; letter-spacing: -0.03em; line-height: 1.35; }
.preview-description { max-width: 640px; margin: var(--space-4) 0 0; color: var(--color-text-secondary); }
.preview-links { display: flex; flex-wrap: wrap; gap: var(--space-5); margin-top: var(--space-6); font-size: var(--font-size-small); }
.preview-links a { text-decoration: none; }
.preview-links a:hover,
.preview-back:hover { text-decoration: underline; }
.preview-grid { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr); gap: var(--space-6); align-items: start; }
.preview-stack { display: grid; gap: var(--space-5); }
.preview-button-row,
.preview-tag-row { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3); }
.preview-caption { margin: 0; color: var(--color-text-secondary); font-size: var(--font-size-caption); line-height: 1.7; }
.preview-event { min-height: 2.5em; }
.preview-divider { height: 1px; background: var(--color-border); }
.preview-swatches { display: grid; gap: var(--space-5); }
.preview-swatch { display: grid; grid-template-columns: 32px 1fr auto; align-items: center; gap: var(--space-3); font-size: var(--font-size-small); }
.preview-swatch__color { width: 32px; height: 32px; border: 1px solid rgb(24 50 53 / 8%); border-radius: var(--radius-control); }
.preview-swatch code { color: var(--color-text-secondary); font-size: var(--font-size-caption); }
.preview-footer { padding-block: var(--space-8); color: var(--color-text-muted); font-size: var(--font-size-caption); }
.skip-link { position: fixed; top: var(--space-3); left: var(--space-3); z-index: 10; padding: var(--space-3); background: var(--color-surface); transform: translateY(-200%); }
.skip-link:focus { transform: translateY(0); }
@media (max-width: 840px) {
  .preview-grid { grid-template-columns: minmax(0, 1fr); }
}
@media (max-width: 480px) {
  .component-preview { padding-inline: var(--space-4); }
  .preview-header { min-height: 76px; gap: var(--space-2); }
  .preview-brand { font-size: var(--font-size-body); gap: var(--space-2); }
  .preview-brand img { width: 30px; height: 30px; }
  .preview-intro { padding-block: var(--space-8) var(--space-6); }
  .preview-description { font-size: var(--font-size-small); }
  .preview-grid { gap: var(--space-4); }
  .preview-links { gap: var(--space-4); }
  .preview-button-row { gap: var(--space-2); }
}
</style>
