# 协调基线变更：六人交付与站点部署

本次属于用户授权的公共基线优化，没有同时实现六模块业务需求，也未冒充成员工作单。

| 文件范围 | 变更 |
| --- | --- |
| spec/members.example.json | 六名示例成员 a—f，各负责一个领域的前后端 |
| scripts/team.py、tools/team/members.py、environment.py、workflow.py | 按层认证、独立工作树/状态/容器/卷，核对模块白名单 |
| tools/team/commits.py、scripts/check-commit.py、buffer_job.py | 校验类型、标题与分点正文，统一正式和开发提交格式 |
| tools/team/delivery.py | 上传后自动导出同一补丁到 GitHub；失败可按缓冲单重试 |
| tools/buffer/backend | 六人访问码与分层权限、原始交付单读取、提交描述校验 |
| .github/workflows/ci.yml | 推送后的工具、前后端测试/构建与制品保存；尚未推送运行 |
| infra/production、scripts/package-product.py | 版本化制品、摘要核验、固定容器配置、数据库备份与应用回退；未开启自动上线 |
| skills/campus-*、docs/14、docs/20 | 同步阶段、命令、当前能力和明确的受阻项 |

验证：工具回归50项通过，缓冲站Java测试28项通过，五个skill结构校验通过；提交正文与健康检查变异能被检出。真实模块容器、macOS、实际GitHub/Gitee推送、华为站点HTTPS和登录未在本次声称通过。命令和原始日志位置保存在忽略文件 .campus/delivery-verification.local。

服务器：已登录华为服务器完成只读检查并安装Docker/Compose，未写入新发布密钥，未运行应用容器，未修改其他项目。

待确认：此华为站点能否例外允许通过CI的GitHub分支推送自动更新。自动审批已拒绝无条件全分支自动上线及root发布入口；确认前保留人工审批规则，不绕过限制。
