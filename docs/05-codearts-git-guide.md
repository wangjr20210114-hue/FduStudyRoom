# CodeArts IDE 与 Gitee 操作指南

本项目使用 CodeArts IDE 或码道 IDE 的内置 Git 面板管理本地版本，远程托管平台采用 Gitee。Git 负责提交与分支历史，Gitee 保存团队远程仓库；IDE 通过仓库地址连接两者。[IDE 源代码管理说明](https://support.huaweicloud.com/usermanual-codeartsagent/codeartsagent_ug_0012.html)

项目仓库名称确定为 FduStudyRoom，由 wangjr2021 账号管理。最终远程 URL 以 Gitee 创建仓库后显示的 HTTPS 地址为准。

## 1 创建项目仓库

登录 Gitee，创建名为 FduStudyRoom 的私有空仓库。保留本地已准备的 README、gitignore 和代码，创建远端时不生成初始文件。复制仓库页面显示的 HTTPS 克隆地址。

vla-b 已保存另一个 VLA/Octo 项目，不作为自习室项目的目标仓库。

## 2 本地首次提交

在 IDE 打开整个 new-project 根目录，按 Ctrl+Shift+G 进入“源代码管理”。没有本地 Git 仓库时，点击“初始化仓库”。项目应包含 frontend、backend 和 docs。

查看“更改”中的差异，点击文件旁的“+”暂存，填写提交信息，再点击“提交”。暂存决定本次记录哪些文件，提交保存本地历史。当前任务已授权助手准备本地首次提交；以后可以按此步骤自己完成。

.gitignore 已排除 node_modules、dist、本地环境值和 .codeartsdoer 配置；package-lock.json 应当提交。Git 的作者名称与邮箱使用已有配置，需要修改时在项目终端设置本仓库的 user.name 与 user.email。

## 3 连接 origin

在源代码管理的“更多操作 …”中选择“远程 → 添加远程存储库”，粘贴新仓库的 HTTPS 地址，远程名称填写 origin。[IDE 添加远程仓库](https://support.huaweicloud.com/usermanual-codeartside/codeartside_01_0245.html)

若助手已经配置 origin，直接沿用该远程。仓库创建时若使用了不同的路径，以实际复制的 URL 为准，不按显示名称猜测路径。

## 4 推送与验证

首次点击“发布分支”，或使用“更多操作 … → 推送”，上传当前分支。按 Gitee 的账号安全设置，在 Git 或 IDE 的认证窗口完成 HTTPS 身份验证；已有 SSH 配置的成员也可使用 Gitee 显示的 SSH 地址。凭据只在认证窗口中填写，不写入仓库 URL、项目文件或聊天。

推送后在 Gitee 网页核对最新提交与文件。提交是本地记录，推送才上传远程。[Gitee Git 基础操作](https://help.gitee.com/Git%20%E7%9F%A5%E8%AF%86%E5%A4%A7%E5%85%A8/Git%E7%9A%84%E5%9F%BA%E6%9C%AC%E6%A6%82%E5%BF%B5%E3%80%81%E5%B8%B8%E7%94%A8%E5%91%BD%E4%BB%A4%E5%8F%8A%E5%AE%9E%E4%BE%8B)

## 5 四人协作与任务停点

开始任务前拉取最新代码，按任务创建分支。完成后查看差异、暂存、提交并推送，再通过 Gitee 的 Pull Request 进行同伴评审与合并。发生冲突时由团队确认处理，不强制覆盖其他人的历史。

每项开发完成后先按 [任务清单](04-login-implementation-tasks.md) 验收，再提交该任务的变更。当前只完成 T01，T02 公共样式与基础组件需等待用户继续指令。
