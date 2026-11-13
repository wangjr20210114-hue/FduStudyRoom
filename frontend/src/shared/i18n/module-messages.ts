type Messages = Record<string, unknown>
function keys(value: Messages, prefix = ''): string[] {
  return Object.entries(value).flatMap(([key, item]) => typeof item === 'string' ? [prefix + key] :
    item && typeof item === 'object' && !Array.isArray(item) ? keys(item as Messages, prefix + key + '.') : (() => { throw new Error('Invalid translation value') })())
}
export function moduleMessages(files: Record<string, { default: Messages }>) {
  const result: Record<'zh-CN' | 'en', Messages> = { 'zh-CN': {}, en: {} }
  for (const [path, content] of Object.entries(files)) {
    const match = path.match(/\/modules\/([a-z][a-z-]*)\/i18n\/(zh-CN|en)\.ts$/)
    if (!match) throw new Error('Invalid module dictionary path')
    const [, module, locale] = match
    result[locale as 'zh-CN' | 'en'][module!] = content.default
  }
  for (const name of new Set([...Object.keys(result['zh-CN']), ...Object.keys(result.en)])) {
    const zh = result['zh-CN'][name], en = result.en[name]
    if (!zh || !en || keys(zh as Messages).sort().join('\n') !== keys(en as Messages).sort().join('\n')) throw new Error('Module locale keys differ: ' + name)
  }
  return result
}
