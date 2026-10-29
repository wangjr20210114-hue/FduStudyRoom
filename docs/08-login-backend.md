# 登录后端使用与交接

当前使用 Java 21、Spring Boot 4.1.2、Spring Security、PostgreSQL 与 Flyway。当前实现资源服务中的身份模块和 platform-common 安全基础；workflow-service 仍为后续业务预留目录。两个服务不互相依赖构建产物。

## 当前能力

T07 已改为普通账号密码登录和开放注册，详见 [登录注册规则](12-password-login.md)。不再接入学校认证，不使用一键开发身份。

| 入口 | 功能 |
| --- | --- |
| GET /api/auth/config | 返回 mode=password、available=true、registrationOpen=true |
| GET /api/auth/csrf | 设置 CSRF Cookie，返回 headerName/token |
| POST /api/auth/login | 账号密码登录 |
| POST /api/auth/register | 固定创建 USER，201 响应并自动建立会话 |
| GET /api/auth/me | 当前账号、角色和实时权限 |
| POST /api/auth/logout | 全设备退出，清除 Cookie |
| GET /api/admin/users、/roles | 有管理权限时查询用户及角色；用户暂返回前 100 条 |
| PATCH /api/admin/users/{id}/access | 按版本修改角色、启用状态，撤销旧会话 |
| POST /internal/auth/introspect | 流程服务预留的服务端身份校验 |

完整契约见 [身份 OpenAPI](../contracts/openapi/identity.yaml)。登录和注册仅接收 loginName/password，写请求先获取 CSRF。成功直接返回 JSON；无内容操作返回 204；错误返回 code/message/traceId。

## 运行前提与数据库

准备 JDK 21 和可用的 Docker/Compose。仓库内 Maven Wrapper 固定 Maven 3.9.16，并验证发行包 SHA-256；无需另行安装 Maven。

将 infra/.env.example 复制为 infra/.env，设置随机数据库密码。项目根目录执行：

```sh
docker compose --env-file infra/.env -f infra/compose.yaml up -d postgres
```

数据库仅映射本机 55432 端口，数据保存在本项目命名卷中，使用 unless-stopped 重启策略。本机预览已切换到这套持久化数据库；临时验收容器不再用于预览。当前 Compose 只提供数据库；不会启动已有其他项目的容器。当前机器的 Docker 位于 CampusStudyRoom WSL，可在该发行版的 /opt/new-project 执行上述命令。

配置后端环境变量，参考 backend/.env.example。Spring 不会自动读取该文件，应在运行终端或 IDE 启动配置中设置。数据库密码需与 infra/.env 一致。

| 变量 | 用途 |
| --- | --- |
| CAMPUS_DB_URL | 本地示例：jdbc:postgresql://127.0.0.1:55432/fdu_study_room |
| CAMPUS_DB_USER | 本地示例：campus_resource |
| CAMPUS_DB_PASSWORD | 数据库连接密码 |
| CAMPUS_AUTH_SIGNING_KEY | 至少 32 个随机字节的 Base64 编码；用于本系统凭据签名 |
| CAMPUS_AUTH_SERVICE_KEY | 独立随机服务密钥，至少 32 字符；不交给前端 |
| SERVER_PORT | 可选，默认 8080 |
| SERVER_ADDRESS | 默认 127.0.0.1；本地 HTTP Cookie 必须绑定回环地址 |
| CAMPUS_BOOTSTRAP_ADMIN_LOGIN | 可选首个管理员账号，须与密码同时提供 |
| CAMPUS_BOOTSTRAP_ADMIN_PASSWORD | 可选初始化密码，12—64 字符；只创建新管理员，不覆盖已有账号 |

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

若需首个管理员，启动前在 IDE 环境变量或当前终端设置 CAMPUS_BOOTSTRAP_ADMIN_LOGIN 和 CAMPUS_BOOTSTRAP_ADMIN_PASSWORD；不要把真实值保存到仓库。具体行为见 [管理员初始化](12-password-login.md)。

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

启动后访问 http://127.0.0.1:8080/api/auth/config，应返回 password 模式。local profile 仅调整本机 HTTP Cookie，不会创建固定密码或演示账号。

前端入口 http://127.0.0.1:5173/login，注册入口 /register，运行见 [前端说明](09-login-frontend.md)。未登录访问 /api/auth/me 返回 401。

## 凭据与权限

凭据为短期 HS256 JWT，只放在 HttpOnly Cookie；JSON 不返回登录凭据。默认有效期 30 分钟，上限 2 小时。请求每次读取最新账号状态、角色与权限，并比较 auth_version。

生产 Cookie 使用 __Host- 前缀、Secure、HttpOnly 和主机范围；开发模式在回环 HTTP 下使用 FDU_SESSION。CSRF Cookie 同样为 HttpOnly，页面通过 GET /api/auth/csrf 获取掩码令牌，按响应中的 headerName 提交。登录和退出会清除旧 CSRF Cookie，下一次写请求前需重新获取令牌。

账号或角色停用时拒绝访问。账号改权和退出会递增 auth_version；权限集合每次查询数据库，不信任浏览器传入角色。管理员改权同时提交 expectedAuthVersion，冲突返回 409。退出影响本系统所有设备。

数据库连接与 socket 等待设有上限；数据库故障时身份请求返回 503，恢复后可重新请求，不使用缓存身份放行。

## 迁移与种子

| 文件 | 用途 |
| --- | --- |
| db/migration/V1__identity_schema.sql | 原身份四表及约束，保持已发布版本 |
| db/migration/V2__password_accounts.sql | 新增密码哈希、LOCAL 来源、条件必填与本地账号大小写唯一约束 |
| db/seed/R__identity_defaults.sql | 幂等角色和权限种子，不覆盖已有配置，不创建密码账号 |

Flyway 历史在 resource_meta。V2 保留旧账号、字段值及启用状态；旧账号没有本地密码，不能直接密码登录。新账号的学校身份类型为空，不使用学校资格字段作为登录条件。字段约束见 [数据库设计](02-database-design.md)。

认证模式仅支持 password。未启用 local 时使用 Secure Cookie；需要同域 HTTPS 反向代理。本地 HTTP 仅支持单独 local profile 和回环地址，混合 profile 或公开绑定会启动失败。密钥必须独立且符合长度要求。

## 验证与后续

- 后端 clean verify：21 项测试通过，生成可运行 JAR。
- 真实 HTTP：33 项检查通过；详细覆盖见 [T07 记录](12-password-login.md)。
- Windows Wrapper 已实际运行；Unix 脚本做过语法检查，macOS 尚未实跑。
- workflow-service 身份接入和完整部署入口待后续实施。

针对专用测试库运行 HTTP 验证；先设置 CAMPUS_SMOKE_ADMIN_PASSWORD，再在项目根目录执行：

```sh
python tests/backend-auth-smoke.py --base-url http://127.0.0.1:8080 --admin-login <已初始化的管理员账号>
```

脚本创建随机 smoke- 前缀账号，并只变更这些测试账号的角色和状态；不会清空数据库。密码通过环境变量读取，不放在命令行参数。前后端测试和启动应依次执行，当前 Windows UNC 临时映射目录不支持多个 npm/Maven 进程交错退出。
