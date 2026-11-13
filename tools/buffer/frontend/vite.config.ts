import { fileURLToPath, pathToFileURL, URL } from 'node:url'
import { createRequire } from 'node:module'
const require = createRequire(new URL('../../../frontend/package.json', import.meta.url))
const { default: vue } = await import(pathToFileURL(require.resolve('@vitejs/plugin-vue')).href)
const dependency = (path: string) => fileURLToPath(new URL('../../../frontend/node_modules/' + path, import.meta.url))
export default { plugins: [vue()], resolve: { alias: { 'vue/server-renderer': dependency('vue/server-renderer/index.mjs'), vue: dependency('vue/dist/vue.runtime.esm-bundler.js'), 'vue-i18n': dependency('vue-i18n/dist/vue-i18n.mjs'), vitest: dependency('vitest/dist/index.js') } }, server: { host: '127.0.0.1', port: 5174, strictPort: true, proxy: { '/api': 'http://127.0.0.1:7780' } }, build: { outDir: 'dist', emptyOutDir: true } }
