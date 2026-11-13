# Stage 顺序

0. 定位工作区、读取AGENTS/manifest，确认目标是主应用或缓冲站，记录commit及脏状态。
1. 检查OS/架构/磁盘/端口，检测CodeArts/VS Code/IDEA；无法检测时提示用户自行确认IDE。
2. 检查Git、构建入口Python3.12+、Node24、Java21；受信源补缺，下载校验SHA256并安全解包。优先项目内工具，复用已有兼容版本。
3. 检查Docker/Compose及引擎。已有WSL引擎直接复用；缺引擎时安装或下载官方工具。虚拟化、重启和许可不能伪造通过。
4. 校验源码与技术栈，干净工作区才可ff-only更新。锁文件不能因本机平台随意改写。
5. 生成/复用本机配置、独立密码、共享服务认证键及可选初始化管理员，保存在`.campus/`或已忽略环境文件。
6. 按目标依次执行前端锁依赖、后端Maven Wrapper verify及前端测试/类型/build；依赖缓存按宿主OS/容器OS分离。
7. 启动独立持久化PostgreSQL并等待就绪；Flyway按模块历史迁移，正式种子不混演示业务记录。
8. 启动当前已实现的服务和前端，使用隐藏后台进程和私有日志，拒绝占用他人端口。未实现业务不伪装完成。
9. 真实健康/API冒烟，输出地址、版本、日志和受阻项；任何失败保留stage，不清理持久化数据。

官方来源：Node https://nodejs.org/en/download ，Temurin API https://adoptium.net/installation/ci-scripts ，Docker https://docs.docker.com/desktop/setup/install/ 。Git使用系统包管理器或 https://git-scm.com/downloads 。
