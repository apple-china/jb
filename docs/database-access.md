# 数据库访问、验证与 Mock 重置

本文说明如何查看 Docker Local、Portable Local、隔离 Test 和 Production 数据库。Local/Test 的数据事实源只有
`src/backend/src/main/resources/db/local/V7__complex_mock_seed.sql`；旧的小数据集和卷级初始化方式已经移除。

## 先区分“查看”与“重置”

| 场景 | 典型目标 | 允许操作 | 说明 |
|---|---|---|---|
| Docker Local | Compose 服务 `db:5432/jiabei` | 本机开发、查询、安全重置 | 从容器内连接，不需要在命令行暴露密码 |
| Portable Local | `127.0.0.1:55432/jiabei` | 本机开发、查询、安全重置 | 数据目录必须是本项目的 `.tools/pgdata` |
| Isolated Test | `127.0.0.1:5432` 或 `55432` 上的 `jiabei_mock_it` | 集成测试、查询、安全重置 | 必须独立于开发库，数据库名以 `_mock_it` 结尾 |
| Production | 由部署平台提供 | 经审批的只读查询 | 禁止运行 Mock 重置，不在仓库中保存连接值 |

这些限制不是操作习惯，而是数据保护边界：`reset-local.ps1` 会重建目标库的 `public` schema，所以它必须先证明数据库、运行时和进程都属于本项目。

## Docker Local

先在项目根目录启动服务：

```powershell
.\scripts\start-local.ps1
```

进入 Compose 内的 PostgreSQL：

```powershell
docker compose exec db psql -U jiabei -d jiabei
```

常用连接信息是 `db:5432/jiabei`（容器网络内）或 `127.0.0.1:5432/jiabei`（宿主机）。如果使用图形客户端，请从本机 Compose 配置读取 Local 凭据；不要把密码保存到文档、截图或连接导出文件。

## Portable Local

Docker 不可用时，`start-local.ps1` 会使用仓库内的便携运行时。直接打开数据库：

```powershell
.\.tools\postgresql-16.15\pgsql\bin\psql.exe -h 127.0.0.1 -p 55432 -U jiabei -d jiabei
```

JDBC 地址为：

```text
jdbc:postgresql://127.0.0.1:55432/jiabei
```

`psql` 或图形客户端提示输入密码时，请使用当前机器的 Local 配置值；本文不记录密码。

## 隔离 Test 数据库

集成测试不得复用 `jiabei` 开发库。先在本机 PostgreSQL 中创建 `jiabei_mock_it`，然后在当前 PowerShell 会话输入本机测试库凭据：

```powershell
$env:JIABEI_IT_JDBC_URL='jdbc:postgresql://127.0.0.1:55432/jiabei_mock_it'
$env:JIABEI_IT_DATABASE_USER = Read-Host '输入本机测试库用户'
$env:JIABEI_IT_DATABASE_PASSWORD = Read-Host '输入本机测试库密码'
cd src\backend
mvn '-Dtest=ComplexMockDataExternalIT' test
```

该测试会先确认 `current_database()` 以 `_mock_it` 结尾，再清理和迁移隔离库，并校验完整 V7 数据合同。连接 Docker 映射端口时，把 JDBC 端口改为 `5432`。

## 安全重置 Local/Test

先预览目标。DryRun 只读取并显示数据库身份与预计数量，不停止服务，也不写数据库：

```powershell
.\scripts\reset-local.ps1 -Runtime Auto -Profile local -Database jiabei -DryRun
```

确认预览无误后执行；脚本会要求输入目标数据库名：

```powershell
.\scripts\reset-local.ps1 -Runtime Auto -Profile local -Database jiabei
```

如自动选择结果不符合预期，可明确指定 `-Runtime Docker` 或 `-Runtime Portable`。自动化环境可使用 `-Force` 跳过交互确认，但 `-Force` **不会**跳过任何目标、配置、数据库所有者、运行进程或本机归属检查。

脚本只支持以下组合：

- `-Profile local -Database jiabei`：重建 schema，运行 Flyway 至 V7，重新启动本项目后端并等待健康检查通过。
- `-Profile test -Database jiabei_mock_it`：重建隔离 schema，通过 `ComplexMockDataExternalIT` 完成迁移和合同校验，随后退出；不会留下常驻或定时调度应用。

例如预览隔离测试库：

```powershell
.\scripts\reset-local.ps1 -Runtime Portable -Profile test -Database jiabei_mock_it -DryRun
```

重置会清空目标 Local/Test `public` schema 中的全部对象后按当前迁移重建，但不会触碰数据库本身、数据库角色、Docker 卷存储、上传文件或环境变量。以下情况会在任何破坏性 SQL 之前被拒绝：

- Profile 不是严格的 `local` 或 `test`；
- 数据库名不是严格的 `jiabei` 或 `jiabei_mock_it`，或 Profile/数据库组合不匹配；
- 主机、端口、服务器实际地址、Docker endpoint 或 Portable 数据目录不能证明属于本机项目；
- 当前环境含生产 Profile 或非白名单 JDBC 目标；
- 后端或数据库进程身份、端口占用情况不符合预期。

## 当前 V7 数据合同

每次迁移都以数据库的 `Asia/Shanghai` 当前日期动态生成：

- 账号共 51 个：1 个超管、5 个运营、5 个观察员、10 个化妆师、30 个主播。
- 资源：10 个化妆师资源、10 个团队。
- 今天与明天各 100 条预约，均为 30 条有效、70 条已取消。
- 过去三天依次为 34、33、33 条，共 100 条。
- 同时包含代预约、修改、取消、四种签到状态、门禁证据、认证与异常审计、卡片投递和五种集成任务状态。

兼容的本地身份/登录 ID 包含 `admin01`、`operator01`、`observer01`、`makeup01`、`streamer01` 至 `streamer04`。其中超管的密码登录账号名是 `superadmin`；本地登录页也提供 Mock 身份选择。本文不记录任何密码、Cookie、CSRF Token 或第三方密钥。

## 只读验证查询

建议在同一个只读事务中执行核对，避免误写：

```sql
BEGIN;
SET TRANSACTION READ ONLY;

SELECT role, count(*)
FROM app_user
GROUP BY role
ORDER BY role;

SELECT booking_date, status, count(*)
FROM appointment
WHERE booking_date BETWEEN
      (now() AT TIME ZONE 'Asia/Shanghai')::date - 3
  AND (now() AT TIME ZONE 'Asia/Shanghai')::date + 1
GROUP BY booking_date, status
ORDER BY booking_date, status;

SELECT source, count(*)
FROM appointment
GROUP BY source
ORDER BY source;

SELECT count(*) AS proxy_appointment_count
FROM appointment
WHERE created_by_user_id <> streamer_user_id;

SELECT action, count(*)
FROM audit_log
WHERE entity_type = 'APPOINTMENT'
  AND action IN ('CREATE', 'MODIFY', 'CANCEL')
GROUP BY action
ORDER BY action;

SELECT entity_type, action, count(*)
FROM audit_log
WHERE entity_type IN ('AUTH', 'SYSTEM', 'TELEMETRY', 'ERROR')
GROUP BY entity_type, action
ORDER BY entity_type, action;

SELECT status, count(*)
FROM integration_job
GROUP BY status
ORDER BY status;

SELECT business_date, status, delivered_version, first_delivered_at
FROM daily_card
ORDER BY business_date;

COMMIT;
```

50 名未注册钉钉候选同事**不在** `app_user` 表。Local/Test 的内存目录共有与 V7 对应的已注册员工和额外候选；受保护接口 `GET /api/v1/admin/dingtalk-employees?query=` 会过滤已注册身份，空查询返回恰好 50 名候选。请在本地管理页“添加账号”搜索框查看，或在已完成管理员登录的 Swagger 会话中调用；不要把会话 Cookie 复制到文档或脚本。

## Production 访问原则

Production 只允许遵循组织流程访问：

1. 从部署平台的安全密钥源读取 `DATABASE_URL`、`DATABASE_USER`、`DATABASE_PASSWORD`，不得写入仓库、文档、聊天、截图或命令历史。
2. 仅通过组织批准的 VPN 或 SSH 隧道连接，核对目标环境、主机、数据库名和角色后再执行查询。
3. 优先使用专用只读角色，并在会话中设置 `default_transaction_read_only=on`；涉及个人或审计数据时遵循最小权限和脱敏要求。
4. 禁止对生产主机、生产 Profile 或生产数据库运行 `scripts/reset-local.ps1`。脚本自身也会拒绝远程地址、生产配置和未知数据库目标。

Production 不提供“临时改为 local”或绕过安全检查的捷径。若需要数据修复，应另行评审迁移脚本、备份与回滚方案。
