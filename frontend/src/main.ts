import { createApp } from 'vue'
import App from '@/app/App.vue'
import { i18n } from '@/shared/i18n'
import '@/shared/styles/base.css'

createApp(App).use(i18n).mount('#app')
