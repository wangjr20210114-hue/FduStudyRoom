# 前端公共样式与基础组件

T02 建立浅色主题和可复用组件，使用 Vue 3、TypeScript 与 Naive UI 2.45.3。公共样式与组件由前端 A 维护；业务页面通过属性和插槽组合使用。修改公共接口前先与调用方协调。

## 文件

以下路径相对 frontend/src。

| 文件 | 作用 |
| --- | --- |
| shared/styles/tokens.css | 颜色、字体、字号、间距、圆角、高度、阴影和动效变量 |
| shared/styles/base.css | 导入变量，设置基础排版、焦点、选中文字及减少动效支持 |
| shared/styles/theme.ts | 读取 CSS 变量并生成 Naive UI 主题 |
| shared/components/AppThemeProvider.vue | 为应用提供统一主题与组件库中文文案 |
| shared/components/AppButton.vue | 按钮变体、尺寸、加载、禁用和提交类型 |
| shared/components/AppPanel.vue | 带标题、说明、操作区和页脚的面板 |
| shared/components/AppTextField.vue | 文本输入、标签、提示、错误和双向绑定 |
| shared/components/AppStatusTag.vue | 带文字和颜色的状态标签 |
| shared/components/AppLoading.vue | 加载图标和状态文案 |
| shared/components/AppAlert.vue | 信息、成功、警告和错误提示 |
| shared/components/types.ts、index.ts | 公共类型和组件导出 |
| app/dev/ComponentPreview.vue | 开发用组件展示及交互示例 |
| app/App.vue | 接入主题，并在开发环境选择组件预览页 |

## 样式与主题

tokens.css 是公共视觉参数的唯一来源。页面使用 `var(--color-primary)`、`var(--space-4)` 等变量；修改公共配色、字号、圆角时，优先修改该文件。

base.css 由 main.ts 导入。App.vue 在根部使用一次 AppThemeProvider。theme.ts 在主题组件初始化时通过 getComputedStyle 读取变量，使原生元素和 Naive UI 控件使用相同参数。开发中修改主题变量后，完整刷新页面以重新计算组件库主题。

本阶段提供浅色主题。组件库使用中文区域设置；应用的中英文字典和语言切换在 T03 实现。

## 组件接口

除标为必填的属性外，其余均可省略。

| 组件 | 属性 | 插槽或事件 |
| --- | --- | --- |
| AppThemeProvider | 无 | 默认插槽：应用内容 |
| AppButton | variant：primary / secondary / ghost / danger，默认 secondary；size：small / medium / large，默认 medium；loading、disabled、block；htmlType：button / submit / reset，默认 button | 默认插槽：文字；icon：图标；click 事件 |
| AppPanel | title、description | 默认插槽：内容；actions：标题旁操作；footer：页脚 |
| AppTextField | modelValue、label 必填；id、name、placeholder、autocomplete、hint、error、required、disabled、clearable | v-model；blur 事件 |
| AppStatusTag | tone：neutral / info / success / warning / error，默认 neutral | 默认插槽：状态文字 |
| AppLoading | label 必填；size：small / medium / large，默认 medium | 无 |
| AppAlert | title 必填；description；type：info / success / warning / error，默认 info | 默认插槽：补充内容；actions：处理按钮 |

按钮加载时同时禁用，防止重复点击。表单提交按钮需明确设置 html-type="submit"，其他按钮默认不会触发表单提交。

输入框自动生成唯一 ID，将 label、原生 input 和提示文字关联起来。required 会设置原生必填属性；业务校验规则和 error 文案由调用页面提供。存在 error 时优先显示错误并设置 aria-invalid。

加载和提示组件带有状态播报属性；状态标签应提供可读文字。基础样式尊重系统的“减少动态效果”设置。

## 组合示例

以下为页面内的最小用法，文案由页面传入：

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { AppButton, AppPanel, AppTextField } from '@/shared/components'

const displayName = ref('')
const error = ref('')

function checkName() {
  error.value = displayName.value.trim() ? '' : '请输入展示名称'
}
</script>

<template>
  <AppPanel title="展示名称" description="本例只校验当前输入。">
    <form novalidate @submit.prevent="checkName">
      <AppTextField
        v-model="displayName"
        label="展示名称"
        name="displayName"
        autocomplete="nickname"
        :error="error"
        required
      />
      <AppButton variant="primary" html-type="submit">检查输入</AppButton>
    </form>
  </AppPanel>
</template>
```

该示例通过 novalidate 展示页面自定义校验。采用浏览器原生校验的页面可移除该属性。组件不内置业务规则；提示、标签、按钮文字由调用方提供，T03 接入字典时由页面传入翻译结果。

## 本地预览

在 frontend 执行 npm run dev 后，访问：

http://127.0.0.1:5173/dev/components

预览包含按钮变体与尺寸、禁用与加载、输入错误与成功反馈、状态标签、加载与重试。示例使用展示名称，在浏览器本地演示，不调用后端或保存数据。

App.vue 使用 import.meta.env.DEV 控制预览组件的动态导入；正式构建移除该组件及其示例逻辑。首页仍为项目起始页。

本次已验证类型检查、正式构建、Chrome 桌面交互与 375px 手机布局，记录见 [T02 交接](04-login-implementation-tasks.md)。macOS 待实际环境验收。
