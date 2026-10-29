import { createApp } from 'vue'
import App from '@/app/App.vue'
import { createAppRouter } from '@/app/router'
import { createLoginFlow } from '@/modules/auth/model/login-flow'
import { authSessionKey } from '@/modules/auth/model/auth-session'
import { i18n } from '@/shared/i18n'
import '@/shared/styles/base.css'

const session = createLoginFlow()
const router = createAppRouter(session)
createApp(App).provide(authSessionKey, session).use(i18n).use(router).mount('#app')
