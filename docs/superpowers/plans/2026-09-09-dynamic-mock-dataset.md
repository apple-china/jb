# 动态复杂 Mock 数据集实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用唯一最新的 Local/Test SQL 动态生成精确、复杂且可重复的账号、资源、预约和审计数据，并提供拒绝生产目标的一键重置与数据库访问说明。

**Architecture:** 删除旧 V2 数据迁移，以当前结构之后执行的 V7 作为唯一 Mock 数据事实源；Local/Test 重置脚本仅在本机目标、安全 Profile 和显式确认都通过后重建 `public` schema，再由 Flyway 完整迁移。Java 集成测试在隔离数据库中验证 V7 的数量、约束、状态覆盖及重复重建，Mock 钉钉目录在内存中稳定生成已注册员工和 50 名未注册同事。

**Tech Stack:** Java 21、Spring Boot 3.3、Spring JDBC、Flyway、PostgreSQL 16、JUnit 5、AssertJ、PowerShell 5.1+、Docker Compose。

**Spec:** `docs/superpowers/specs/2026-09-09-dynamic-mock-dataset-design.md`

## Global Constraints

- 生产迁移目录 `src/backend/src/main/resources/db/migration` 不得包含 Mock 数据。
- 删除旧 `db/local/V2__mock_seed.sql`，只保留 `db/local/V7__complex_mock_seed.sql` 作为 Local/Test 初始化 SQL。
- 今天、明天各 100 条预约，分别为 30 条 `ACTIVE` 与 70 条 `CANCELLED`。
- 过去三天分别为 34、33、33 条，共 100 条。
- 最终系统数据严格包含 1 超管、5 运营、5 观察员、10 化妆师账号、30 主播、10 化妆师资源和 10 团队。
- 50 名未注册钉钉员工只存在于 Local/Test 目录网关，不写入 `app_user`。
- 动态日期和时间统一使用数据库 `Asia/Shanghai` 时区。
- 重置只允许 `local` 或 `test` Profile 且数据库主机必须是本机/Docker `db`；永远拒绝生产和远程目标。
- 重置会重建 Local/Test `public` schema，但不得删除数据库、数据库角色、上传文件或修改环境变量。
- 保留既有兼容登录身份：`admin01`、`operator01`、`observer01`、`makeup01`、`streamer01` 至 `streamer04`。
- 不新增第三方依赖，不改变公开 API、生产数据库结构和业务约束。

---

### Task 1: 扩展 Local/Test 钉钉员工目录

**Files:**
- Create: `src/backend/src/test/java/com/jiabei/cloud/integration/MockDingTalkDirectoryGatewayTest.java`
- Modify: `src/backend/src/main/java/com/jiabei/cloud/integration/MockDingTalkDirectoryGateway.java`

**Interfaces:**
- Consumes: `DingTalkDirectoryGateway.Employee(String userId, String username)`
- Produces: `MockDingTalkDirectoryGateway.employees(String query)` 返回 41 名有钉钉身份的系统员工和恰好 50 名未注册候选员工；候选兼容 ID 包含 `user123456`、`user654321`、`user889900`、`user776655`。

- [ ] **Step 1: 写候选数量、唯一性和搜索失败测试**

创建测试，按 ID 前缀/兼容 ID 划分候选集合，而不是断言内部常量：

```java
package com.jiabei.cloud.integration;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MockDingTalkDirectoryGatewayTest {
  private final MockDingTalkDirectoryGateway gateway = new MockDingTalkDirectoryGateway();

  @Test void exposesExactlyFiftyUnregisteredCandidatesWithUniqueIdentityAndName() {
    List<DingTalkDirectoryGateway.Employee> all = gateway.employees("");
    Set<String> legacy = Set.of("user123456", "user654321", "user889900", "user776655");
    List<DingTalkDirectoryGateway.Employee> candidates = all.stream()
        .filter(e -> legacy.contains(e.userId()) || e.userId().startsWith("candidate"))
        .toList();
    assertThat(candidates).hasSize(50);
    assertThat(new HashSet<>(candidates.stream().map(DingTalkDirectoryGateway.Employee::userId).toList())).hasSize(50);
    assertThat(new HashSet<>(candidates.stream().map(DingTalkDirectoryGateway.Employee::username).toList())).hasSize(50);
  }

  @Test void searchesCandidatesByTrimmedIdAndChineseName() {
    assertThat(gateway.employees(" candidate050 "))
        .extracting(DingTalkDirectoryGateway.Employee::userId)
        .containsExactly("candidate050");
    assertThat(gateway.employees("张三"))
        .extracting(DingTalkDirectoryGateway.Employee::userId)
        .containsExactly("user123456");
  }

  @Test void preservesExistingAutomationIdentities() {
    assertThat(gateway.employees(""))
        .extracting(DingTalkDirectoryGateway.Employee::userId)
        .contains("admin01", "operator01", "observer01", "makeup01",
            "streamer01", "streamer02", "streamer03", "streamer04");
  }
}
```

- [ ] **Step 2: 运行测试并确认因候选不足失败**

Run:

```powershell
cd src\backend
mvn '-Dtest=MockDingTalkDirectoryGatewayTest' test
```

Expected: FAIL；第一项得到 4 个兼容候选而不是 50，且 `candidate050` 搜索为空。

- [ ] **Step 3: 用构造函数生成稳定目录**

将硬编码 `List.of` 改为不可变构建结果：

```java
private static List<Employee> buildEmployees() {
  List<Employee> rows = new ArrayList<>();
  rows.add(new Employee("admin01", "超管"));
  IntStream.rangeClosed(1, 5).forEach(i -> rows.add(
      new Employee("operator%02d".formatted(i), "运营%02d".formatted(i))));
  IntStream.rangeClosed(1, 5).forEach(i -> rows.add(
      new Employee("observer%02d".formatted(i), "观察员%02d".formatted(i))));
  IntStream.rangeClosed(1, 10).forEach(i -> rows.add(
      new Employee("makeup%02d".formatted(i), "化妆师%02d".formatted(i))));
  IntStream.rangeClosed(1, 20).forEach(i -> rows.add(
      new Employee("streamer%02d".formatted(i), "主播%02d".formatted(i))));
  rows.addAll(List.of(
      new Employee("user123456", "张三"),
      new Employee("user654321", "李四"),
      new Employee("user889900", "王五"),
      new Employee("user776655", "赵六")));
  IntStream.rangeClosed(5, 50).forEach(i -> rows.add(
      new Employee("candidate%03d".formatted(i), "候选同事%03d".formatted(i))));
  return List.copyOf(rows);
}
```

保留现有 `trim().toLowerCase(Locale.ROOT)` 搜索逻辑。补充简短业务注释，解释“前 41 人与 V7 数据匹配，后 50 人故意不注册”。

- [ ] **Step 4: 运行目录相关测试**

Run:

```powershell
cd src\backend
mvn '-Dtest=MockDingTalkDirectoryGatewayTest,DingTalkDirectoryControllerTest' test
```

Expected: PASS。

- [ ] **Step 5: 提交目录变更**

```powershell
git add src/backend/src/main/java/com/jiabei/cloud/integration/MockDingTalkDirectoryGateway.java src/backend/src/test/java/com/jiabei/cloud/integration/MockDingTalkDirectoryGatewayTest.java
git commit -m "feat: expand local DingTalk directory fixtures"
```

---

### Task 2: 用集成测试定义最新 V7 数据合同

**Files:**
- Create: `src/backend/src/test/java/com/jiabei/cloud/integration/ComplexMockDataExternalIT.java`
- Delete: `src/backend/src/main/resources/db/local/V2__mock_seed.sql`
- Create: `src/backend/src/main/resources/db/local/V7__complex_mock_seed.sql`
- Modify: `src/backend/src/test/java/com/jiabei/cloud/integration/ExternalPostgreSqlIT.java`
- Modify: `src/backend/src/test/java/com/jiabei/cloud/integration/ProductionMigrationExternalIT.java`

**Interfaces:**
- Consumes: Flyway locations `classpath:db/migration,classpath:db/local` 和环境变量 `JIABEI_IT_JDBC_URL`、`JIABEI_IT_DATABASE_USER`、`JIABEI_IT_DATABASE_PASSWORD`
- Produces: 空隔离库迁移后满足 `ComplexMockDataExternalIT` 的精确数据合同；生产迁移仍生成 0 个账号。

- [ ] **Step 1: 写隔离数据库保护和精确数量测试**

新增 `ComplexMockDataExternalIT`。在 `@BeforeAll` 中先连接数据库并要求 `current_database()` 以 `_mock_it` 结尾，否则抛出 `IllegalStateException("Complex Mock integration test requires an isolated *_mock_it database")`；随后执行 Flyway `clean()` 与 `migrate()`。

核心断言：

```java
@Test void createsExactPeopleAndResourceCounts() {
  assertThat(count("SELECT count(*) FROM app_user WHERE role='SUPER_ADMIN'")).isEqualTo(1);
  assertThat(count("SELECT count(*) FROM app_user WHERE role='OPERATOR'")).isEqualTo(5);
  assertThat(count("SELECT count(*) FROM app_user WHERE role='OBSERVER'")).isEqualTo(5);
  assertThat(count("SELECT count(*) FROM app_user WHERE role='MAKEUP'")).isEqualTo(10);
  assertThat(count("SELECT count(*) FROM app_user WHERE role='STREAMER'")).isEqualTo(30);
  assertThat(count("SELECT count(*) FROM makeup_artist")).isEqualTo(10);
  assertThat(count("SELECT count(*) FROM team")).isEqualTo(10);
}

@Test void createsExactDynamicAppointmentDistribution() {
  assertDateDistribution(0, 100, 30, 70);
  assertDateDistribution(1, 100, 30, 70);
  assertThat(count("""
      SELECT count(*) FROM appointment
      WHERE booking_date BETWEEN current_date - 3 AND current_date - 1
      """)).isEqualTo(100);
  assertThat(count("SELECT count(*) FROM appointment WHERE booking_date=current_date-1")).isEqualTo(34);
  assertThat(count("SELECT count(*) FROM appointment WHERE booking_date=current_date-2")).isEqualTo(33);
  assertThat(count("SELECT count(*) FROM appointment WHERE booking_date=current_date-3")).isEqualTo(33);
}
```

再分别断言：

- `source` 含 `STREAMER/SUPER_ADMIN/OPERATOR/MAKEUP`。
- `attendance_status` 含 `PENDING/ARRIVED/NOT_ARRIVED/LATE`。
- `audit_log` 含 AUTH 的成功/失败/拒绝、APPOINTMENT 的 CREATE/MODIFY/CANCEL、SYSTEM/TELEMETRY 事件。
- `integration_job.status` 含五种状态。
- `daily_card.first_delivered_at` 同时存在空值和非空值。
- 至少存在代预约：`created_by_user_id <> streamer_user_id`。
- 至少存在修改前后 JSON、取消原因、门禁证据、操作计数和冲突例外。

- [ ] **Step 2: 运行新集成测试并确认旧 V2 数据不满足合同**

在专用库（例如 `jiabei_mock_it`）设置：

```powershell
$env:JIABEI_IT_JDBC_URL='jdbc:postgresql://127.0.0.1:55432/jiabei_mock_it'
$env:JIABEI_IT_DATABASE_USER='jiabei'
$env:JIABEI_IT_DATABASE_PASSWORD='test'
cd src\backend
mvn '-Dtest=ComplexMockDataExternalIT' test
```

Expected: FAIL；旧 V2 只有少量账号/资源且没有 300 条目标预约。

- [ ] **Step 3: 删除旧 V2，创建唯一最新 V7**

删除 `V2__mock_seed.sql`。V7 顶部使用：

```sql
SET LOCAL TIME ZONE 'Asia/Shanghai';

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM app_user)
     OR EXISTS (SELECT 1 FROM appointment)
     OR EXISTS (SELECT 1 FROM makeup_artist)
     OR EXISTS (SELECT 1 FROM team) THEN
    RAISE EXCEPTION 'V7 complex mock seed requires an empty business schema';
  END IF;
END $$;
```

SQL 使用稳定 UUID 组：用户 `10000000-0000-0000-0000-%012s`、化妆师 `20000000-0000-0000-0000-%012s`、团队 `30000000-0000-0000-0000-%012s`、预约 `40000000-0000-0000-%04s-%012s`、审计 `50000000-0000-0000-%04s-%012s`。兼容账号必须沿用现有前四位 UUID 和登录 ID。

人员生成用 `generate_series`：

```sql
INSERT INTO team(id,name,logo_url,is_active,created_at,updated_at)
SELECT format('30000000-0000-0000-0000-%012s', i)::uuid,
       CASE WHEN i=1 THEN '星河一团' ELSE format('测试团队%02s', i) END,
       format('/brand/team-placeholder-%s.svg', ((i-1)%3)+1),
       i<>10, clock_timestamp()-(10-i)*interval '1 day', clock_timestamp()
FROM generate_series(1,10) AS s(i);
```

化妆师的 `work_days/work_start/work_end/schedule_enabled/is_attending/is_active` 用 `CASE i % n` 形成至少六种组合；`schedule_enabled=false` 时仍保存最近有效时间，排班开启的记录必须满足工作日非空和开始早于结束。

账号必须按 1/5/5/10/30 精确生成；前 20 个主播有钉钉 ID，后 10 个只分配密码账号；5 个运营和 10 个化妆师的 `can_create_appointments` 同时覆盖 true/false。密码使用已有 Local/Test `{noop}` 形式，超管保持 `Admin123!` 且 `must_change_password=true`。

- [ ] **Step 4: 生成五日预约及关联数据**

用统一规格 CTE：

```sql
WITH spec(day_offset,total,active_count) AS (
  VALUES (0,100,30),(1,100,30),(-1,34,18),(-2,33,17),(-3,33,16)
), rows AS (
  SELECT day_offset,total,active_count,n,
         current_date + day_offset AS booking_date,
         ((n-1)%30)+1 AS streamer_no,
         ((n-1)%10)+1 AS makeup_no,
         ((n-1)%10)+1 AS team_no
  FROM spec CROSS JOIN LATERAL generate_series(1,total) AS g(n)
)
SELECT day_offset,total,active_count,n,booking_date,streamer_no,makeup_no,team_no
FROM rows;
```

随后以这个 CTE 为 `INSERT INTO appointment` 的输入，列映射必须逐项遵守：

- `id`：`('40000000-0000-0000-' || lpad((day_offset+3)::text,4,'0') || '-' || lpad(n::text,12,'0'))::uuid`。
- `booking_date`：CTE 的 `booking_date`。
- `start_at`：`(booking_date + time '06:00' + ((n-1)%72)*interval '10 minutes') AT TIME ZONE 'Asia/Shanghai'`。
- `end_at`：`start_at + interval '20 minutes'`，`duration_minutes` 固定 20。
- `streamer_user_id`：连接 `app_user.username='streamer' || to_char(streamer_no,'FM00')`。
- `makeup_artist_id`：连接 `makeup_artist.id=('20000000-0000-0000-0000-' || lpad(makeup_no::text,12,'0'))::uuid`。
- `team_id`：连接 `team.id=('30000000-0000-0000-0000-' || lpad(team_no::text,12,'0'))::uuid`。
- 三个名称快照：分别取连接后的主播昵称、化妆师名称和团队名称。
- `status`：`n<=active_count` 时为 `ACTIVE`，否则为 `CANCELLED`。
- `source`：按 `n%4` 轮转 `STREAMER/SUPER_ADMIN/OPERATOR/MAKEUP`。
- `created_by_user_id`：STREAMER 使用当前主播；SUPER_ADMIN 使用 `admin01`；OPERATOR 使用 `operator01` 至 `operator05` 轮转；MAKEUP 使用当前 `makeup_artist_id` 关联的化妆师账号。
- `conflict_override`：仅管理员/运营/化妆师来源且 `n%37=0` 时为 true。
- `attendance_status`：今天、明天为 `PENDING`；过去日期按 `n%4` 轮转 `ARRIVED/LATE/NOT_ARRIVED/PENDING`。
- `attendance_frozen`：CANCELLED 为 true，ACTIVE 为 false。
- `cancelled_at/cancelled_by_user_id/cancel_reason`：只为 CANCELLED 设置，取消时间早于 `updated_at`，原因按三个中文原因轮转。
- `created_at/updated_at`：基于 `clock_timestamp()` 与 n 生成有序且不晚于当前时间的动态时间。

ACTIVE 的 30 行通过化妆师/时间槽组合避免同一化妆师重叠；CANCELLED 历史允许复用时间。完成插入后执行查询断言 `end_at-start_at=interval '20 minutes'`、所有本地开始分钟为 10 的倍数，以及每个主播/日期最多一条 ACTIVE。

随后插入：

- 每条预约一条 CREATE 审计。
- 每 7 条一条 MODIFY 审计，JSON 明确包含 `startTime/makeupArtistId/teamId/conflictOverride` 前后值。
- 每条 CANCELLED 一条 CANCEL 审计。
- 至少 12 条 AUTH 登录/拒绝审计和 12 条 SYSTEM/TELEMETRY/ERROR 审计。
- 过去三天 ARRIVED/LATE 的门禁记录及 `attendance_event_id/evidence_at`。
- 有界 `appointment_operation_counter`，计数不超过 2/3。
- 两日 `daily_card`、成功/失败调用日志、迟到提醒、幂等记录、六种故障开关和五种任务状态。

- [ ] **Step 5: 在 V7 尾部增加数据库内断言**

使用带显式变量的 `DO` 块断言角色、资源、日期分布、ACTIVE 唯一性、代预约、审计和任务状态。每项错误信息包含实际值，例如：

```sql
DO $$
DECLARE
  actual_count integer;
BEGIN
SELECT count(*) INTO actual_count FROM appointment WHERE booking_date=current_date;
IF actual_count <> 100 THEN
  RAISE EXCEPTION 'today appointment count expected 100, got %', actual_count;
END IF;
END $$;
```

- [ ] **Step 6: 更新既有迁移测试期望**

`ExternalPostgreSqlIT.migrate()` 仍应断言 Local/Test 总迁移数为 6（5 个生产迁移 + 1 个 V7）。将引用旧 V2 固定姓名/ID的断言改为 V7 兼容 ID。生产测试继续只加载 `db/migration` 并断言 5 个迁移、0 个账号。

- [ ] **Step 7: 运行复杂数据、约束和生产隔离测试**

```powershell
cd src\backend
mvn '-Dtest=ComplexMockDataExternalIT,ExternalPostgreSqlIT,ProductionMigrationExternalIT,PostgreSqlConstraintIT' test
```

Expected: PASS。

- [ ] **Step 8: 提交最新 SQL 与集成测试**

```powershell
git add src/backend/src/main/resources/db/local src/backend/src/test/java/com/jiabei/cloud/integration
git commit -m "feat: replace legacy seed with dynamic complex dataset"
```

---

### Task 3: 重写本机安全的一键重置脚本

**Files:**
- Create: `scripts/lib/MockResetSafety.psm1`
- Create: `scripts/tests/reset-local.Tests.ps1`
- Modify: `scripts/reset-local.ps1`
- Modify: `scripts/test-all.ps1`

**Interfaces:**
- Produces: `Assert-SafeMockResetTarget -DatabaseHost <string> -Port <int> -Database <string> -Profile <string> -ServerAddress <string>`
- Produces: `Resolve-MockRuntime -Requested Auto|Docker|Portable -ProjectRoot <string>`
- Produces: `reset-local.ps1 [-Runtime Auto|Docker|Portable] [-Database jiabei] [-Profile local|test] [-Force] [-DryRun]`
- Consumes: Docker Compose 或 `.tools/postgresql-16.15/pgsql/bin/psql.exe`，以及 `scripts/start-local.ps1`

- [ ] **Step 1: 写不连接数据库的安全规则失败测试**

`scripts/tests/reset-local.Tests.ps1` 导入尚不存在的模块，并实现轻量断言函数：

```powershell
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot '..\lib\MockResetSafety.psm1') -Force

function Assert-Throws([scriptblock]$Action, [string]$Expected) {
  try { & $Action; throw "Expected exception containing: $Expected" }
  catch {
    if (-not $_.Exception.Message.Contains($Expected)) { throw }
  }
}

Assert-SafeMockResetTarget -DatabaseHost '127.0.0.1' -Port 55432 -Database 'jiabei' -Profile 'local' -ServerAddress '127.0.0.1'
Assert-SafeMockResetTarget -DatabaseHost 'localhost' -Port 5432 -Database 'jiabei_mock_it' -Profile 'test' -ServerAddress '127.0.0.1'
Assert-Throws { Assert-SafeMockResetTarget -DatabaseHost 'db.prod.example' -Port 5432 -Database 'jiabei' -Profile 'local' -ServerAddress '10.0.0.4' } '本机'
Assert-Throws { Assert-SafeMockResetTarget -DatabaseHost '127.0.0.1' -Port 5432 -Database 'jiabei' -Profile 'production' -ServerAddress '127.0.0.1' } 'Profile'
Assert-Throws { Assert-SafeMockResetTarget -DatabaseHost '127.0.0.1' -Port 6432 -Database 'jiabei' -Profile 'test' -ServerAddress '127.0.0.1' } '端口'
Assert-Throws { Assert-SafeMockResetTarget -DatabaseHost '127.0.0.1' -Port 5432 -Database 'postgres' -Profile 'test' -ServerAddress '127.0.0.1' } '数据库名'
Write-Host 'PASS reset-local safety rules'
```

- [ ] **Step 2: 运行脚本测试并确认模块缺失**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\tests\reset-local.Tests.ps1
```

Expected: FAIL，无法加载 `MockResetSafety.psm1`。

- [ ] **Step 3: 实现纯安全模块**

模块必须避免使用 PowerShell 自动变量 `$Host`，只使用 `$DatabaseHost`。允许主机集合为 `127.0.0.1/localhost/db`，端口集合为 `5432/55432`，Profile 为 `local/test`，数据库名必须匹配 `^jiabei(?:_mock_it)?$`。查询到的 `inet_server_addr()` 必须为空（Unix socket）或回环地址；Docker 内部服务允许 `db:5432`。

`Resolve-MockRuntime` 的 Auto 规则与 `start-local.ps1` 一致：Docker daemon 可用则 Docker，否则必须检测到便携 `psql.exe` 和 PostgreSQL 数据目录。

- [ ] **Step 4: 运行安全规则测试**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\tests\reset-local.Tests.ps1
```

Expected: PASS。

- [ ] **Step 5: 重写 reset-local.ps1**

脚本流程必须固定为：

1. 解析参数并调用 `Resolve-MockRuntime`。
2. 构造目标但不打印密码。
3. 用 `psql -X -v ON_ERROR_STOP=1 -tAc` 查询 `current_database()|current_user|inet_server_addr()|current_setting('server_version_num')`。
4. 调用 `Assert-SafeMockResetTarget` 二次验证连接结果。
5. `-DryRun` 打印目标、最新迁移 `V7__complex_mock_seed.sql` 和预计数量后退出，禁止执行 DROP。
6. 未传 `-Force` 时要求输入数据库名；不匹配即取消。
7. 停止 backend 进程/容器，保持数据库运行。
8. 执行显式 SQL：`DROP SCHEMA public; CREATE SCHEMA public AUTHORIZATION <validated-current-user>;`。
9. Docker 执行 `docker compose up --build -d`；Portable 执行 `scripts/start-local.ps1`，由 Flyway 运行 V1/V3/V4/V5/V6/V7。
10. 等待健康检查，再用只读 SQL输出角色、资源、五日预约和审计摘要。

对 Docker 使用 `docker compose exec -T db psql`；Portable 使用绝对路径 `psql.exe`。所有外部进程调用后立即检查 `$LASTEXITCODE`，非零则抛错。schema 重建 SQL 中的 owner 必须来自已验证的 `current_user` 并通过 `^[A-Za-z_][A-Za-z0-9_]*$` 校验，防止 SQL 注入。

- [ ] **Step 6: 将安全测试加入常规测试入口**

在 `scripts/test-all.ps1` 的 Maven 前调用：

```powershell
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'scripts\tests\reset-local.Tests.ps1')
if ($LASTEXITCODE -ne 0) { throw 'reset-local safety tests failed.' }
```

- [ ] **Step 7: 验证 DryRun 与拒绝路径**

```powershell
.\scripts\reset-local.ps1 -Runtime Portable -Profile local -Database jiabei -DryRun
powershell -NoProfile -Command "Import-Module '.\scripts\lib\MockResetSafety.psm1'; Assert-SafeMockResetTarget -DatabaseHost 'prod.example.com' -Port 5432 -Database 'jiabei' -Profile production -ServerAddress '10.0.0.10'"
```

Expected: 第一条只显示计划且数据库计数不变；第二条以明确中文安全错误退出。

- [ ] **Step 8: 提交安全重置脚本**

```powershell
git add scripts/reset-local.ps1 scripts/test-all.ps1 scripts/lib/MockResetSafety.psm1 scripts/tests/reset-local.Tests.ps1
git commit -m "feat: add guarded local database reset"
```

---

### Task 4: 编写数据库访问与数据使用说明

**Files:**
- Create: `docs/database-access.md`
- Modify: `README.md`

**Interfaces:**
- Documents: Docker Local、Portable Local、隔离 Test、Production 四类访问方式
- Documents: 最新重置命令、DryRun、数据分布、测试账号和生产禁止事项

- [ ] **Step 1: 写数据库访问文档**

文档必须包含可复制命令：

```powershell
# Docker Local
docker compose exec db psql -U jiabei -d jiabei

# Portable Local
.\.tools\postgresql-16.15\pgsql\bin\psql.exe -h 127.0.0.1 -p 55432 -U jiabei -d jiabei

# 安全预览与确认后的重置
.\scripts\reset-local.ps1 -Runtime Auto -Profile local -Database jiabei -DryRun
.\scripts\reset-local.ps1 -Runtime Auto -Profile local -Database jiabei
```

隔离测试库示例使用 `jiabei_mock_it` 并列出三个 `JIABEI_IT_*` 环境变量。生产章节只提供原则：

- 从部署平台安全读取 `DATABASE_URL/USER/PASSWORD`。
- 经组织批准的 VPN 或 SSH 隧道连接。
- 建议创建只读账号并设置 `default_transaction_read_only=on`。
- 禁止对生产主机运行 `reset-local.ps1`，脚本本身也会拒绝。
- 文档不得包含真实密码、Cookie、Token 或第三方密钥。

增加验证查询：角色数量、五日预约数量/状态、代预约、修改/取消审计、集成任务和 50 名候选员工如何从接口查看。

- [ ] **Step 2: 更新 README**

删除“旧 V2 小数据集”和“删除 Docker 数据卷”的描述，将重置段落替换为最新命令，并链接 `docs/database-access.md`。本地账号表保留兼容身份并注明完整数据为 1/5/5/10/30；说明今天/明天各 100、过去三天 100。

- [ ] **Step 3: 检查文档无旧初始化残留与秘密**

```powershell
rg -n "V2__mock_seed|down --volumes|删.*数据卷" README.md docs scripts
rg -n "DINGTALK_CLIENT_SECRET=.+|DATABASE_PASSWORD=.+|Cookie:|X-CSRF-Token:.+" README.md docs
```

Expected: 第一条只允许在设计/迁移说明中以“已删除”语义出现；第二条无匹配。

- [ ] **Step 4: 提交文档**

```powershell
git add README.md docs/database-access.md docs/superpowers/specs/2026-09-09-dynamic-mock-dataset-design.md docs/superpowers/plans/2026-09-09-dynamic-mock-dataset.md
git commit -m "docs: explain mock reset and database access"
```

---

### Task 5: 执行隔离重建与全量验证

**Files:**
- Verify only: `src/backend/src/main/resources/db/local/V7__complex_mock_seed.sql`
- Verify only: `scripts/reset-local.ps1`
- Verify only: `docs/database-access.md`

**Interfaces:**
- Verifies: 规格的全部验收标准

- [ ] **Step 1: 在隔离库连续迁移两次**

创建或选用名称以 `_mock_it` 结尾的隔离数据库。第一次运行 `ComplexMockDataExternalIT` 后记录摘要；第二次再次运行（测试会 Flyway clean/migrate）并比较精确数量。

```powershell
cd src\backend
mvn '-Dtest=ComplexMockDataExternalIT' test
mvn '-Dtest=ComplexMockDataExternalIT' test
```

Expected: 两次均 PASS；日期都相对于第二次执行时数据库的上海当前日期。

- [ ] **Step 2: 运行全部后端单元与数据库集成测试**

```powershell
cd src\backend
mvn test
mvn '-Dtest=ExternalPostgreSqlIT,ComplexMockDataExternalIT,CardMockExternalIT,ProductionMigrationExternalIT' test
```

Expected: PASS，且生产迁移测试仍为 0 个 Mock 账号。

- [ ] **Step 3: 运行前端回归与构建**

```powershell
cd src\frontend
pnpm test
pnpm run build
pnpm test:e2e
```

Expected: Vitest、生产构建、桌面/360px/iPhone Safari/钉钉 Playwright 项目全部通过或只有项目条件定义的显式 skip。

- [ ] **Step 4: 对当前 Local 数据库执行一次真实安全重置**

先运行 `-DryRun` 并核对目标是 `127.0.0.1:5432/jiabei` 或 `127.0.0.1:55432/jiabei`；确认后执行：

```powershell
.\scripts\reset-local.ps1 -Runtime Auto -Profile local -Database jiabei
```

Expected: 用户确认后 schema 重建、Flyway 到 V7、后端健康为 UP，摘要显示精确数量。

- [ ] **Step 5: 用 SQL 和运行接口完成验收审计**

验证：

```sql
SELECT role,count(*) FROM app_user GROUP BY role ORDER BY role;
SELECT booking_date,status,count(*) FROM appointment
WHERE booking_date BETWEEN current_date-3 AND current_date+1
GROUP BY booking_date,status ORDER BY booking_date,status;
SELECT count(*) FROM appointment WHERE created_by_user_id<>streamer_user_id;
SELECT action,count(*) FROM audit_log GROUP BY action ORDER BY action;
SELECT status,count(*) FROM integration_job GROUP BY status ORDER BY status;
```

再检查：

- `GET /actuator/health` 返回 `UP`。
- Swagger 可打开。
- 管理页能分页查看今天/明天 100 条及详情中的修改/取消/代预约字段。
- 添加账号时搜索未注册同事能得到 50 人全集和模糊搜索结果。
- 浏览器控制台无数据解析错误。

- [ ] **Step 6: 最终状态与提交检查**

```powershell
git status --short
git log --oneline -5
```

Expected: 只有用户原有的无关未跟踪/未提交文件；本计划涉及文件已提交。若 Git 作者身份仍未配置，停止提交步骤并向用户索取仓库级 `user.name/user.email`，不得伪造身份。
