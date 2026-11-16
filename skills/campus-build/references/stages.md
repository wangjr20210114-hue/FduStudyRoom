# Stage 顺序

0. 定位工作区、读取AGENTS/manifest，确认目标是主应用或缓冲站，记录commit及脏状态。
1. 检查OS/架构/磁盘/端口，检测CodeArts/VS Code/IDEA；无法检测时提示用户自行确认IDE。
2. 检查Git、构建入口Python3.12+、Node24、Java21；受信源补缺，下载校验SHA256并安全解包。优先项目内工具，复用已有兼容版本。
3. 检查Docker/Compose及引擎。已有WSL引擎直接复用；缺引擎时安装或下载官方工具。虚拟化、重启和许可不能伪造通过。
4. 校验源码与技术栈，干净工作区才可ff-only更新。锁文件不能因本机平台随意改写。
5. 生成/复用本机配置、独立密码、共享服务认证键及可选初始化管理员，保存在`.campus/`或已忽略环境文件。验证使用当前工作区独立测试库与测试邮箱，自动分配仅本机可访问的端口，不沿用应用业务库。
6. 安装前端锁定依赖，等待独立测试数据库及测试邮箱健康，再按目标执行后端Maven Wrapper verify及前端测试/类型/build；依赖缓存按宿主OS/容器OS分离。主应用先打包当前流程服务供身份黑盒测试，随后完整verify；测试内不递归Maven，不提交JAR。Maven Wrapper在Windows调用cmd入口，macOS/Linux通过sh调用，不依赖文件执行位。结束或失败均只释放本工作区的验证容器，保留数据卷和日志。只构建时不启动目标应用。
7. 启动独立持久化PostgreSQL并等待就绪；Flyway按模块历史迁移，正式种子不混演示业务记录。
8. 启动当前已实现的服务和前端，使用隐藏后台进程和私有日志，拒绝占用他人端口。未实现业务不伪装完成。
9. 真实健康/API冒烟，输出地址、版本、日志和受阻项；任何失败保留stage，不清理持久化数据。

官方来源：Node https://nodejs.org/en/download ，Temurin API https://adoptium.net/installation/ci-scripts ，Docker https://docs.docker.com/desktop/setup/install/ 。Git使用系统包管理器或 https://git-scm.com/downloads 。

成员沙箱仅修复所属层的私有依赖缓存：backend使用镜像内Maven缓存，frontend使用镜像内node_modules；仍使用原子校验修复，不挂载共享可写缓存或下载新增公共依赖。宿主Git入口按进程启用长路径和受限HTTP/pack内存，保留精确路径信任与宿主认证助手，不修改全局Git配置。Windows的worktree子进程会清除命令行配置，因此必须经过`policy.git`/`git_run`：它们创建一次性配置overlay，include原global文件并覆盖longpaths，命令结束删除；Git不支持全局配置路径查询时提示升级。失败查看`.campus/git-diagnostics/`私有脱敏诊断，冲突仍交人工处理。
