# 缓冲站服务器部署

目标为 Ubuntu 22.04 x86_64 的 124.223.110.193，入口 https://gitbuffer.seatraces.com。
GitHub 开发分支直接接收代码，Gitee wangjr2021/fdu-study-room 通过缓冲站正式交付。

## 初始运行范围

网页登录只填写个人访问码，系统自动识别成员；支持需求和变更查询、人工审核、RAM/磁盘/JVM/队列状态。成员只看自己的材料，协调人管理全队。
部署配置初始关闭本机 CI 与 Gitee 发布。现有约3.6GiB主机已运行多个项目，不在该机安装 Maven/Node 或构建应用。
预览包包含本机已构建的 JAR 和前端；服务器只组装运行镜像。API 内存上限512MiB，专用PostgreSQL18上限256MiB，日志轮转上限按容器配置。
主机RAM仅通过只读挂载 /proc/meminfo 采样；数据库使用独立内部网络，不复用其他项目的PG16、Redis或数据卷，不给API挂Docker socket。

## 安装

1. 添加 A 记录：gitbuffer → 124.223.110.193。
2. 下载部署预览包及 SHA256SUMS，校验后解压到新的独立目录，例如 /opt/fdu-gitbuffer-preview-20261113。
3. 以 root 在该目录执行 python3 initialize.py；已有配置保持不变，访问码在 private/member-credentials.local，本机查看并逐人分发，不提交Git。
4. docker compose up -d --build --wait --wait-timeout 180。
5. curl -fsS http://127.0.0.1:7780/health。
6. DNS生效后运行 python3 connect-caddy.py；它检查实际启动配置、追加站点、验证、备份并 reload。既有站点配置不重建，原文件绑定inode保持不变。
7. curl -fsS https://gitbuffer.seatraces.com/health，再打开网页验收。

API发布到主机127.0.0.1:7780，并以 fdu-gitbuffer-api 接入现有 docker_default 网络。公网入口是 docker-caddy-1；本机验证失败时不接入站点。
Caddy仅支持明确加载 /etc/caddy/Caddyfile 或 Caddyfile.iptest 的启动方式；其他方式停止并交人工检查，不猜测配置。

## 配置

state/.campus/buffer/config.local：memoryThresholdPercent 默认85、pressureCooldownSeconds 默认300、intervalSeconds 默认259200。
RAM告急只提前处理 VERIFIED / PUSH_FAILED 合格队列的首项；正常到期也逐项排空，失败保留重试，不批量将全部证据读入内存。
队列材料已持久化。成功推送后保留证据，不能把推送当作释放RAM或磁盘的保证。

启用正式交付前需要：批准并发布的冻结基线、真实成员授权、/data/repository 中对应Git仓库、Gitee受控推送凭据与MR权限，以及受信独立CI。
当前低资源配置不带Docker socket，不能在API容器内直接运行独立Docker CI；远程worker接入尚未实现。不能用网页审核通过冒充CI完成。
首次界面可以收件和审核，正式提交和定时推送保持关闭，直到上述条件实际满足。GitHub开发分支由workflow独立导出，不复用缓冲站的Gitee凭据。

## 验证与交付

本轮后端25项测试通过，包含有效Red、Green、迁移重号检出及独立副本RAM变异检出；前端4项SSR/字典测试、类型检查和构建通过。
独立PostgreSQL18和当前JAR实际启动，HTTP核对健康、网页、认证、资源、迁移、域名来源与禁用CI；大体积证据的列表只返回摘要。
Compose配置与初始化幂等检查通过。2026-11-13 已在目标服务器启动独立容器并接入Caddy；外部HTTPS、健康接口、首页及JS/CSS均返回成功，未登录资源接口返回401。Gitee发布和远程CI验收仍待完成。
公开代码不包含任务书PDF、个人凭据、原始运行日志、依赖、数据库卷或构建产物；部署预览产物单独提供。

## 访问码登录的基线变更

移除网页登录的身份输入和重复身份校验，保留个人访问码。成员归属和审核权限由 `/api/me` 返回的已认证成员决定；上传、审核和模块授权继续使用后端原有契约。数据库、服务端认证和业务模块边界无改动。

前端构建后可在 `tools/buffer/frontend` 执行 `npm run test:browser`，用已安装的Chrome/Chromium或 `CAMPUS_TEST_BROWSER` 指定的浏览器检查只填访问码登录、成员权限、退出及中英文错误提示。测试使用本地HTTP夹具，不使用真实访问码，也不代替生产环境的人工验收。

本次验证：5项真实Chrome交互测试、4项Vitest测试、类型检查和生产构建通过；在独立副本恢复必填身份框后，同一组5项浏览器测试全部检出失败。页面截图已核对仅有访问码输入。新版静态资源需另行更新到服务器后生效。

## 固定访问码与记住登录

每名成员的访问码保存在服务器私有配置中，不随刷新、构建或容器重启轮换。initialize.py 发现既有配置会直接复用。不要通过删除配置来更新前端。

2026-11-15 的前端修复在首次成功认证后保存本浏览器的访问码，再次打开时先向 /api/me 验证并恢复身份。主动退出或服务端返回401时清除；临时网络或503错误保留访问码以便重试。浏览器禁用本地存储时仍可使用当前会话。不同域名及隐私窗口不共享保存状态。

8项Chrome浏览器测试通过，覆盖原来的5项登录检查，以及浏览器重启恢复、失效访问码清除、服务暂时不可用。4项Vitest、类型检查和构建通过。固定码仍逐人分配，不把六人的身份合并成共用账号。

本机试跑站已更新。公网静态资源需要按本次发布包同步；包和命令不包含访问码，不重新初始化数据库，也不启用尚未接通的Gitee发布或远程CI。
