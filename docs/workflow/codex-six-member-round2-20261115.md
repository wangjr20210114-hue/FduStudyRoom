# 六人 workflow 第二轮实跑

本轮使用 a—f 六个本机缓冲身份和独立 Codex 会话，分批开发六项完整后端需求。它们不是六个真实 OpenAI 或 Gitee 登录账号。

目前六份代码已完成开发验证、缓冲站收件与 GitHub 自动推送。GitHub CI 和体验站部署正在逐分支核验；用户已通过本轮后端人工验收；缓冲站独立CI正在运行，本次只验证Gitee功能分支推送，不创建MR。

## 六个人完成了什么

| 身份 | 本轮后端内容 | 新测试 | 缓冲站 | 代码与 CI |
| --- | --- | --- | --- | --- |
| a | IAM-02：角色新增、授权、撤权和停用 | 7 | DRAFT，待验收 | [代码](https://github.com/wangjr20210114-hue/FduStudyRoom/commit/71e7dc807072f3c45037a094619a9a453318c79d) · [CI：通过](https://github.com/wangjr20210114-hue/FduStudyRoom/actions/runs/43714251952) |
| b | RES-07：教室每日签到码及过期校验 | 12 | DRAFT，待验收 | [代码](https://github.com/wangjr20210114-hue/FduStudyRoom/commit/d7f98b847e88ce9afff8261c6a9a9a3d9e7e1aac) · [CI：通过](https://github.com/wangjr20210114-hue/FduStudyRoom/actions/runs/43714265656) |
| c | M1-06：开始十分钟仍未签到时提醒一次 | 8 | DRAFT，待验收 | [代码](https://github.com/wangjr20210114-hue/FduStudyRoom/commit/22b76127463f10a99354288030b026fba8142261) · [CI：处理中](https://github.com/wangjr20210114-hue/FduStudyRoom/actions/runs/43714279809) |
| d | M2-02：两级审批、禁止自审与重复决策 | 11 | DRAFT，待验收 | [代码](https://github.com/wangjr20210114-hue/FduStudyRoom/commit/bc481ac40f26cf4d5c0e3cd367029f301909962e) · [CI：处理中](https://github.com/wangjr20210114-hue/FduStudyRoom/actions/runs/43714294777) |
| e | M3-06：验收人评分及多轮过程查询 | 9 | DRAFT，待验收 | [代码](https://github.com/wangjr20210114-hue/FduStudyRoom/commit/f93b3b971592c97411a34eafcc9acb375c4df4bc) · [CI：处理中](https://github.com/wangjr20210114-hue/FduStudyRoom/actions/runs/43714310300) |
| f | SUP-01：本人通知、邮件投递与重试 | 10 | DRAFT，待验收 | [代码](https://github.com/wangjr20210114-hue/FduStudyRoom/commit/0b1a44b5bf54271af61ce28407f46da3c11c930d) · [CI：处理中](https://github.com/wangjr20210114-hue/FduStudyRoom/actions/runs/43714332857) |

六份补丁共 44 个文件，没有重叠，均干净应用到独立集成目录。GitHub 导出保留原模块提交，再叠加协调人的公共修复；六人的产品文件与 TDD 输入没有改变。缓冲站回读的完整证据与上传原文一致。

## 实际通过的检查

| 检查 | 结果 |
| --- | --- |
| 新业务 TDD | 57 项 Green；各自有有效 Red 和独立变异检出 |
| 六模块共同构建 | 81/81，通过真实 PostgreSQL 迁移，无失败、错误或跳过 |
| 共同服务 HTTP | 79/79；使用实际登录 Cookie、身份服务与业务服务 |
| 提醒失败恢复 | 首次已收件但丢确认，新 JVM 用同事件重试；三个 JVM 仍只有一条站内通知 |
| 容器默认配置 | 无通知地址覆盖，实际定时任务通过默认服务地址发送；至少六周期不重复 |
| 分支迁移与应用回退 | 六分支依次启动、旧版两服务启动，8/8 健康；表与迁移历史保持一致 |
| 公共工具 | 117 项：116 通过，1 项 Windows 专用在 Linux 跳过；Windows 原生另验通过 |
| 缓冲站空字符修复 | 原30项加3项真实PG往返测试，33/33；保留JSONB与原表结构、证据哈希 |

## 修复了哪些真实问题

- 支持按完整需求选择本轮范围，未选需求继续待办。
- 并发写同一收据改为短锁合并，拒绝陈旧覆盖；原失败记录保留。
- 修复 Windows 长路径与 Git 一次分配 500 MiB 的问题，宿主认证不进入成员容器。
- 后端只预热自己的 Maven 缓存，省去无关前端缓存。
- CI 增加独立邮件收件箱与分离的测试数据库，部署保持串行。
- 旧推送被新版本替代时记录 superseded，不报该提交已上线。
- 上传首次因日志空字符返回500；最小存储编码修复后六单成功，原JSONB、列表查询和原证据保持不变。

## 还没有完成的部分

- 本轮没有开发这六项的新前端页面，也没有完成全部预约、申请创建及维修生命周期。测试前态由协调环境构造，不能当成上游创建接口已完成。
- 用户已明确通过六项后端验收，正式Gitee提交仍等待本轮独立CI全部通过；本次按用户要求只验证功能分支推送，不创建MR，不直推main，不声称等待了72小时。
- 本次新缓冲站运行在本机 http://127.0.0.1:17783；公网缓冲站后端尚未同步本轮版本。现有访问码保留。
- macOS、新电脑首次安装、真实公网邮件提供商未在本轮验证。

## 代码与证据位置

本轮私有材料位于 `.campus/codex-round2-20261115-273201/`，不进入Git。

| 位置 | 内容 |
| --- | --- |
| members/a … members/f | 本人协调目录、工作树、原始TDD与交接 |
| six-member-verification.local、review-exports/ | 六份证据核验、补丁及文件边界 |
| integration-review/、integration-evidence/ | 干净集成代码与81项共同构建 |
| private/live-integration-result.local.json | 79项实际HTTP与跨JVM恢复 |
| private/default-routing-result.local.json | 实际定时任务默认地址验证 |
| migration-sequence/result.local | 8次真实启动、历史与回退边界 |
| private/metadata-codec-fix/summary.local.json | 最小收件修复的Red/Green/Mutation |
| submission-results.local、delivery-heads.local、delivery-actions.local | 真实单号、提交、分支与CI状态 |

用户可在缓冲站审阅六份差异后决定验收；本人访问码仍在成员的 `.campus/member.local`，不要复制到聊天或Git。
