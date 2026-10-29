# 登录后端使用与交接

T04 使用 Java 21、Spring Boot 4.1.2、Spring Security、PostgreSQL 与 Flyway。当前实现资源服务中的身份模块和 platform-common 安全基础；workflow-service 仍为后续业务预留目录。两个服务不互相依赖构建产物。

## 本阶段能力

| 入口 | 功能 |
| --- | --- |
| GET /api/auth/config | 返回认证模式、可用性及开发身份选项 |
| GET /api/auth/csrf | 设置 CSRF Cookie，返回提交用的 headerName 和 token |
| POST /api/auth/dev/login | 仅本地开发模式可用的固定身份演示 |
| GET /api/auth/me | 返回当前身份、角色和实时权限 |
| POST /api/auth/logout | 退出本系统，使该账号所有设备的旧凭据失效 |
| GET /api/admin/users、/roles | 管理权限下查询用户及角色；用户暂返回前 100 条 |
| PATCH /api/admin/users/{id}/access | 按版本修改角色及启用状态，立即撤销旧凭据 |
| POST /internal/auth/introspect | 提供给后续流程服务的服务端身份校验入口 |
| GET /api/auth/fudan/login、/callback | 当前返回 UIS_NOT_CONFIGURED，不接收浏览器自报身份 |

完整请求、响应和权限见 [身份 OpenAPI](../contracts/openapi/identity.yaml)。成功响应直接返回 JSON，与 T03 客户端一致；无内容操作返回 204。错误返回 code、message、traceId，响应头 X-Request-Id 与 traceId 一致。

## 运行前提与数据库

准备 JDK 21 和可用的 Docker/Compose。仓库内 Maven Wrapper 固定 Maven 3.9.16，并验证发行包 SHA-256；无需另行安装 Maven。

将 infra/.env.example 复制为 infra/.env，设置随机数据库密码。项目根目录执行：

```sh
docker compose --env-file infra/.env -f infra/compose.yaml up -d postgres
```

数据库仅映射本机 55432 端口，数据保存在本项目命名卷中。当前 Compose 只提供数据库；不会启动已有其他项目的容器。当前机器的 Docker 位于 CampusStudyRoom WSL，可在该发行版的 /opt/new-project 执行上述命令。

配置后端环境变量，参考 backend/.env.example。Spring 不会自动读取该文件，应在运行终端或 IDE 启动配置中设置。数据库密码需与 infra/.env 一致。

| 变量 | 用途 |
| --- | --- |
| CAMPUS_DB_URL | 本地示例：jdbc:postgresql://127.0.0.1:55432/fdu_study_room |
| CAMPUS_DB_USER | 本地示例：campus_resource |
| CAMPUS_DB_PASSWORD | 数据库连接密码 |
| CAMPUS_AUTH_SIGNING_KEY | 至少 32 个随机字节的 Base64 编码；用于本系统凭据签名 |
| CAMPUS_AUTH_SERVICE_KEY | 独立随机服务密钥，至少 32 字符；不交给前端 |
| SERVER_PORT | 可选，默认 8080 |
| SERVER_ADDRESS | 默认 127.0.0.1；开发登录必须绑定回环地址 |

数据库容器的账号用于本地开发。正式环境按服务配置限权账号，身份资源服务不向流程服务开放 iam 表访问。

## Windows 与 macOS

Windows PowerShell 可生成当前终端内的两份独立密钥：

```powershell
$campusKeyGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
foreach ($campusKeyName in @('CAMPUS_AUTH_SIGNING_KEY', 'CAMPUS_AUTH_SERVICE_KEY')) {
    $campusKeyBytes = New-Object byte[] 32
    $campusKeyGenerator.GetBytes($campusKeyBytes)
    [Environment]::SetEnvironmentVariable($campusKeyName, [Convert]::ToBase64String($campusKeyBytes), 'Process')
}
$campusKeyGenerator.Dispose()
$env:CAMPUS_DB_URL = 'jdbc:postgresql://127.0.0.1:55432/fdu_study_room'
$env:CAMPUS_DB_USER = 'campus_resource'
$env:CAMPUS_DB_PASSWORD = '<与 infra/.env 一致的密码>'
```

确认当前终端的 java -version 为 JDK 21。普通 Windows 路径下进入 backend，执行：

```powershell
.\mvnw.cmd verify
java -jar resource-service/target/resource-service-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

当前项目是 UNC 共享路径，构建时在已设置变量的 PowerShell 终端中执行：

```powershell
cmd /c 'pushd "\\wsl.localhost\CampusStudyRoom\opt\new-project\backend" && mvnw.cmd verify'
```

macOS 终端设置对应变量后进入 backend：

```sh
export CAMPUS_AUTH_SIGNING_KEY="$(openssl rand -base64 32)"
export CAMPUS_AUTH_SERVICE_KEY="$(openssl rand -base64 32)"
export CAMPUS_DB_URL='jdbc:postgresql://127.0.0.1:55432/fdu_study_room'
export CAMPUS_DB_USER='campus_resource'
export CAMPUS_DB_PASSWORD='<与 infra/.env 一致的密码>'
./mvnw verify
java -jar resource-service/target/resource-service-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

启动后访问 http://127.0.0.1:8080/api/auth/config 检查模式。local profile 会初始化明确标记的开发数据，三个可登录选项为学生、管理员和维护人员；另有教职工普通账号、资格失效和停用账号用于拒绝测试。开发身份不代表真实复旦身份。

T05 已提供登录页面及同站点开发代理，访问 http://127.0.0.1:5173/login 可完成开发登录、刷新恢复与退出，见 [登录前端说明](09-login-frontend.md)。浏览器直接输入 /api/auth/me 在未登录时仍返回 401；正式复旦 UIS 继续等待 T07。

## 凭据与权限

凭据为短期 HS256 JWT，只放在 HttpOnly Cookie；JSON 不返回登录凭据。默认有效期 30 分钟，上限 2 小时。请求每次读取最新账号资格、角色与权限，并比较 auth_version。

生产 Cookie 使用 __Host- 前缀、Secure、HttpOnly 和主机范围；开发模式在回环 HTTP 下使用 FDU_SESSION。CSRF Cookie 同样为 HttpOnly，页面通过 GET /api/auth/csrf 获取掩码令牌，按响应中的 headerName 提交。登录和退出会清除旧 CSRF Cookie，下一次写请求前需重新获取令牌。

账号、角色停用或资格无效时拒绝访问。账号改权和退出会递增 auth_version；权限集合每次查询数据库，不信任浏览器传入角色。管理员改权同时提交 expectedAuthVersion，冲突返回 409。退出影响本系统所有设备，不执行学校全局退出。

数据库连接与 socket 等待设有上限；数据库故障时身份请求返回 503，恢复后可重新请求，不使用缓存身份放行。

## 迁移、种子与开发数据

| 文件 | 用途 |
| --- | --- |
| db/migration/V1__identity_schema.sql | 建立 iam 四张表及约束，Flyway 历史在 resource_meta |
| db/seed/R__identity_defaults.sql | 幂等建立默认角色与权限；仅为新建角色分配基线权限，不覆盖已有配置 |
| db/demo/identity.sql | 本地开发数据，独立于正式种子；重复执行保留已修改账号 |

默认模式为 uis。开发登录同时要求仅启用 local profile、mode=dev、回环地址绑定；混用 local/prod 或公开绑定会启动失败。生产没有开发控制器和开发数据初始化器。正式模式拒绝 DEVELOPMENT 数据和 dev 模式凭据。

数据库文档已同步删除 password_hash，并加入 identity_type、identity_source、identity_active；邮箱允许待补全。见 [数据库表结构](02-database-design.md)。仍保持 19 张应用表的总设计，本阶段仅迁移身份四表。

## 验证与后续

- Maven Wrapper verify：15 项单元测试及可运行 JAR 打包。
- tests/backend-auth-smoke.py：针对专用测试库执行真实 HTTP 验收；会修改并恢复开发学生的访问状态。
- PostgreSQL：验证迁移、重复启动及种子不覆盖管理员修改。
- Windows Wrapper 已实际验证；Unix 脚本完成语法检查，macOS 待实际运行。
- 正式 UIS、流程服务端接入及持久审计投递分别随后续任务完成；当前安全事件写结构化日志，不宣称完整审计链已完成。

HTTP 验收示例：

```sh
python tests/backend-auth-smoke.py --base-url http://127.0.0.1:8080
```

配置 CAMPUS_AUTH_SERVICE_KEY 后脚本还会验证内部凭据校验。--production-url 可指定另一份默认 UIS 模式测试服务，检查生产隔离；测试脚本不会连接学校服务。

正式 UIS 当前准确返回未配置。校方协议、回调、属性及首位正式管理员的受控绑定在 T07 完成，不能用开发登录测试替代。

实现依据：[Spring Boot 系统要求](https://docs.spring.io/spring-boot/system-requirements.html)、[Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)、[Maven Wrapper](https://maven.apache.org/wrapper/)。
