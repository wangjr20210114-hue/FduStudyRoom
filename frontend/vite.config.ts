import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const environment = loadEnv(mode, process.cwd(), 'CAMPUS_')
  const target = process.env.CAMPUS_API_TARGET ?? environment.CAMPUS_API_TARGET ?? 'http://127.0.0.1:8080'
  const url = new URL(target)
  if (url.protocol !== 'http:' || !['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname)
      || url.username || url.password || url.pathname !== '/' || url.search || url.hash) {
    throw new Error('CAMPUS_API_TARGET must be a local HTTP origin.')
  }
  const workflow = new URL(process.env.CAMPUS_WORKFLOW_TARGET ?? environment.CAMPUS_WORKFLOW_TARGET ?? 'http://127.0.0.1:8081')
  if (workflow.protocol !== 'http:' || !['127.0.0.1', 'localhost', '[::1]'].includes(workflow.hostname) || workflow.username || workflow.password || workflow.pathname !== '/' || workflow.search || workflow.hash) throw new Error('CAMPUS_WORKFLOW_TARGET must be a local HTTP origin.')
  const workflowProxy = { target: workflow.origin, changeOrigin: false, timeout: 20000, proxyTimeout: 20000 }
  return {
    plugins: [vue()],
    resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
    server: {
      host: '127.0.0.1', port: 5173, strictPort: true,
      proxy: { '/api/approvals': workflowProxy, '/api/room-requests': workflowProxy, '/api/repairs': workflowProxy, '/api/maintenance': workflowProxy, '/api/notifications': workflowProxy, '/api/audit': workflowProxy, '/api/statistics': workflowProxy, '/api': { target: url.origin, changeOrigin: false, timeout: 20000, proxyTimeout: 20000 } },
    },
    preview: { host: '127.0.0.1', port: 4173, strictPort: true },
  }
})
