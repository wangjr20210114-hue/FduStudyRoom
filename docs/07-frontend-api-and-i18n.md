# 公共请求与国际化

T03 提供公共 JSON 请求客户端、统一错误提示、中英文字典和语言选择。后续认证与业务模块复用这些入口；本阶段不调用复旦认证服务。

## 文件与归属

以下路径相对 frontend。

| 文件 | 作用 | 维护人 |
| --- | --- | --- |
| src/shared/api/client.ts | 请求参数、JSON 编解码、超时、取消和响应格式检查 | 前端 B |
| src/shared/api/errors.ts | ApiError 错误分类、HTTP 状态、业务码和追踪号 | 前端 B |
| src/shared/api/message.ts | 将错误转换为当前语言的公共提示 | 前端 B |
| src/shared/api/index.ts | 同站点 /api 客户端及公共导出 | 前端 B |
| src/shared/i18n/locales/zh-CN.ts | 中文字典，作为字段结构基准 | 前端 B 汇总，各模块维护自己的命名空间 |
| src/shared/i18n/locales/en.ts | 英文字典，使用中文结构进行 TypeScript 检查 | 同上 |
| src/shared/i18n/schema.d.ts | 为翻译函数提供字典结构类型 | 前端 B |
| src/shared/i18n/locale.ts | 支持语言、选择保存及存储不可用回退 | 前端 B |
| src/shared/i18n/index.ts | Vue I18n 实例，语言切换、文档语言及标题同步 | 前端 B |
| src/shared/components/LanguageSwitcher.vue | 带关联标签的语言选择控件 | 前端 A |
| tests/api-client.test.ts、tests/i18n.test.ts | 请求与国际化行为测试 | 对应模块维护人 |
| vitest.config.ts | 测试环境和测试文件范围 | 前端 B |

## 请求客户端

统一导入：

```ts
import { api, ApiError, getApiErrorMessage } from '@/shared/api'
```

客户端默认请求同站点的 /api，路径使用 / 开头的业务相对路径；筛选条件单独传入 query，由 URLSearchParams 编码。正式部署由反向代理将路径转发到相应后端。当前未配置后端代理，不能将前端启动成功当作 API 服务已可用。

调用形式如下，/rooms 是后续资源接口的使用示例：

```ts
const controller = new AbortController()
const rooms = await api.request<RoomSummary[]>('/rooms', {
  query: { campus: '邯郸', available: true },
  signal: controller.signal,
})
```

| 参数 | 约定 |
| --- | --- |
| method | 默认 GET；可选 POST、PUT、PATCH、DELETE |
| query | 字符串、数字和布尔值；null 与 undefined 忽略 |
| body | JSON 数据；GET 禁止传请求体 |
| headers | 允许调用方补充请求头，例如身份模块提供的 CSRF 令牌 |
| signal | 调用方可取消请求；页面卸载时可取消在途读取 |
| timeoutMs | 默认 15000 毫秒，可传正数覆盖；覆盖读取响应体的过程 |

请求自动发送 Accept: application/json，以及当前语言的 Accept-Language。JSON 请求体自动设置 Content-Type。凭据采用 same-origin；前端不读取或保存登录 Cookie。CSRF 的服务端校验和令牌取得流程在 T04 身份模块中落实。

2xx 成功响应返回解析后的 JSON；204 和 205 返回 undefined。成功但返回 HTML、空 JSON 或损坏 JSON 时，转换为 invalid_response。泛型只提供编译时类型约定，业务模块仍需按正式接口契约处理字段。

当前没有已落地的后端响应封装。客户端保留 JSON 原结构，不擅自解包 data；T04 定稿身份接口时统一约定封装。失败响应暂可读取顶层字符串 code 和 traceId，缺失时仍按 HTTP 状态处理。

客户端不自动重试请求，避免重复创建预约或重复提交审批。HTTP 401 交给身份模块统一处理；本阶段提供提示，不自动跳转登录页。请求不跟随重定向，避免将登录页面当作业务 JSON 接收。

## 错误处理

| ApiError.kind | 含义 |
| --- | --- |
| http | HTTP 非 2xx；status 保存响应状态，code 和 traceId 按可用字段保留 |
| network | 连接失败或读取响应时发生网络故障 |
| timeout | 请求超过设定时限 |
| cancelled | 调用方主动取消 |
| invalid_response | 成功响应不是约定的 JSON |
| invalid_request | 路径、请求体或超时参数不符合客户端约定 |

getApiErrorMessage(error) 按当前语言返回提示，包含未登录、无权限、不存在、状态冲突、输入无效、限流和服务不可用等分类。服务端原始 message 不直接展示；未知异常使用公共提示。

页面需要在切换语言后同步已有错误时，保存错误对象，再通过 computed 调用 getApiErrorMessage。主动取消的请求通常只结束加载，不向用户弹错误。业务错误 code 的细分文案在对应模块实现时补充。

## 国际化

应用使用 Vue I18n 的 Composition API，入口 main.ts 注册实例；页面通过 useI18n({ useScope: 'global' }) 获取 t。组件的标签、按钮、提示等文案由调用页面传入。

```ts
const { t } = useI18n({ useScope: 'global' })
```

```vue
<AppButton>{{ t('common.retry') }}</AppButton>
```

支持 zh-CN 与 en，首次使用默认中文。选择保存为 localStorage 的 fdu-study-room.locale；这里仅保存语言偏好。刷新、首页与组件预览之间跳转会沿用选择。存储受限或写入失败时，当前页面仍可切换；下次访问可能恢复默认中文。

切换语言同时更新 html 的 lang、页面标题及 Naive UI 的语言与日期区域设置。首页和组件预览中的文案、已出现的表单错误及按钮反馈随语言即时更新。

字典按 app、common、errors、preview 命名空间组织。后续模块增加 auth、reservation 等命名空间时，中英文一起更新；公共组件内不增加业务文案。英文文件通过 satisfies typeof zhCN 检查字段结构。

## 验证与运行

在 frontend 中执行：

```sh
npm test
npm run build
npm run dev
```

首页：http://127.0.0.1:5173/

组件预览：http://127.0.0.1:5173/dev/components

测试使用 Vitest，覆盖请求编码、当前语言请求头、JSON 提交、HTTP 错误、异常响应、网络故障、超时、取消、语言持久化与存储受限回退。本阶段的测试没有连接真实后端或校方服务。

Windows 与 WSL 共享路径的启动方式见 [README](../README.md)。类型检查和构建之外，需在真实浏览器检查中英文布局、刷新及切换效果。macOS 仍待实际环境验收。

实现参考：[Vue I18n Composition API](https://vue-i18n.intlify.dev/guide/advanced/composition)、[Vue I18n TypeScript](https://vue-i18n.intlify.dev/guide/advanced/typescript)、[Fetch API](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API/Using_Fetch)、[Vitest](https://vitest.dev/guide/)。
