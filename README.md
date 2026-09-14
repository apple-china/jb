# 加贝云·化妆预约 V1.0

当前代码已按 [V1.0 确认需求基线](docs/加贝云_V1.0_确认需求基线.md) 完成结构化重构。前端为 Vue 3 + TypeScript，后端为 Java 21 + Spring Boot 3，PostgreSQL 16 + Flyway 为数据事实源。钉钉登录、群卡片和魔点回调均采用可替换适配层；本地默认 Mock，真实第三方环境仍需联调。

## 本地启动

在项目根目录执行：

```powershell
.\scripts\start-local.ps1
```

脚本优先使用 Docker；Docker 不可用时，自动使用项目 `.tools` 中的便携 Java 和 PostgreSQL。服务地址：

- Web：http://127.0.0.1:5173/login
- 健康检查：http://127.0.0.1:8080/actuator/health
- Swagger：http://127.0.0.1:8080/swagger-ui/index.html

停止服务：`.\scripts\stop-local.ps1`。重置 Local/Test 数据前必须先执行安全预览；脚本只重建已验证本机数据库的 `public` schema，不会触碰上传文件、数据库本身或卷存储：

```powershell
.\scripts\reset-local.ps1 -Runtime Auto -Profile local -Database jiabei -DryRun
.\scripts\reset-local.ps1 -Runtime Auto -Profile local -Database jiabei
```

Docker、便携数据库、隔离测试库的访问命令、安全边界和只读验证查询见 [数据库访问、验证与 Mock 重置](docs/database-access.md)。

## 本地测试账号

登录页可直接选择五种 Mock 身份，也可使用账号密码：

| 角色 | 兼容 Mock 身份 / 登录 ID |
|---|---|
| 唯一超管 | `admin01` / `superadmin` |
| 运营 | `operator01` |
| 观察员 | `observer01` |
| 化妆师 | `makeup01` |
| 主播 | `streamer01`～`streamer04` |

快捷 Mock 登录仅用于本地验收；本文不记录密码或会话凭据。`streamer21`～`streamer30` 是未绑定钉钉的密码登录账号，可用于验证“可预约，但无法自动签到和接收 @ 提醒”。

当前唯一 Local/Test 数据源是 `V7__complex_mock_seed.sql`：包含 1 个超管、5 个运营、5 个观察员、10 个化妆师账号及资源、30 个主播和 10 个团队；今天、明天各 100 条预约，过去三天共 100 条。另有 50 名未注册候选同事仅存在于 Local/Test 钉钉目录接口，不写入账号表。

## 当前业务范围

- 五种固定单角色；运营修改/取消预约分别授权，观察员只读禁导出，妆造只能为本人代约且不能修改、取消。
- 仅预约今天、明天；10 分钟刻度、占用 20 分钟。主播新建/修改/取消恰好提前 20 分钟允许；管理员和妆造代约恰好提前 1 分钟允许。
- 同一主播同一预约日期最多一条有效预约。取消释放资格和化妆师时段，可反复重约；主播取消最多 2 次、修改最多 3 次，按主播＋预约日期跨记录累计。
- 所有人都必须通过主播出勤、化妆师启用/出勤/工作日/工作时间、团队启用校验；只有超管、运营、化妆师代约及管理员修改可例外接受化妆师时间冲突，并留下标记。
- 预约仅有有效、已取消；作废已从数据库、接口和页面删除。取消冻结签到，后续门禁回调不再改变该记录。
- V1.0 已包含魔点回调、四态签到、T＋10 分钟迟到判断、无有效打卡时每预约一次的迟到提醒。
- 已取消固定 09:00 发卡。预约日期首条有效预约自动触发安排卡；超管、运营可手动触发，后续变更更新原卡，失败由 Outbox 幂等重试。
- 超管、运营、观察员默认按今天/明天查看预约；高级筛选默认收起，可按本月、近 7 日、近 30 日或自定义日期区间及主播、化妆师、签到状态、预约状态查询，超过 30 条时显示 30/50/100 分页。超管、运营可导出当前条件下的全部记录为 Excel，观察员不可导出。

## 测试

常规回归：

```powershell
.\scripts\test-all.ps1
```

已启动后端的 API 冒烟：

```powershell
.\scripts\smoke-api.ps1
```

使用独立 PostgreSQL 测试库执行外部集成测试；数据库用户和密码由 PowerShell 在运行时提示输入，命令中不嵌入凭据，也不得复用开发库：

```powershell
$env:JIABEI_IT_JDBC_URL='jdbc:postgresql://127.0.0.1:55432/jiabei_mock_it'
$env:JIABEI_IT_DATABASE_USER = Read-Host '输入本机测试库用户'
$env:JIABEI_IT_DATABASE_PASSWORD = Read-Host '输入本机测试库密码'
cd src\backend
mvn '-Dtest=ComplexMockDataExternalIT,ExternalPostgreSqlIT,CardMockExternalIT,ProductionMigrationExternalIT' test
```

## 配置与发布边界

### 钉钉测试环境

`dingtalk-test` 使用独立数据库 `jiabei_dingtalk_test` 和生产迁移，不加载 V7 Mock 数据；首次启动仅创建一个未绑定钉钉 ID 的超管。设置 `DINGTALK_CLIENT_ID`、`DINGTALK_CLIENT_SECRET` 后，后端启动时及每 5 分钟全量核对通讯录，离职员工逻辑删除并停用已关联账号。密钥只允许由进程环境注入。

前端构建设置 `VITE_ENABLE_MOCK_LOGIN=false`、`VITE_DINGTALK_AUTO_LOGIN=true`；仅验收环境可设置 `VITE_DINGTALK_TEST_DIAGNOSTICS=true`。该环境保留账号密码和钉钉免登，但不显示固定角色快捷登录，也不允许在设置页点击人员切换身份。

服务器首次切换到钉钉测试环境时，从示例复制一份不入库的配置文件，并填写数据库密码、钉钉公开标识、应用密钥及公网来源：

```bash
cp .env.example .env.dingtalk-test
# 编辑 .env.dingtalk-test，至少填写 DINGTALK_TEST_DATABASE_PASSWORD、
# DINGTALK_CORP_ID、DINGTALK_CLIENT_ID、DINGTALK_CLIENT_SECRET、
# DINGTALK_GROUP_OPEN_CONVERSATION_ID、DINGTALK_CARD_TEMPLATE_ID、APP_ENTRY_URL、
# VITE_DINGTALK_CORP_ID、VITE_DINGTALK_CLIENT_ID 和 ALLOWED_ORIGINS。
docker compose --env-file .env.dingtalk-test \
  -f docker-compose.yml -f docker-compose.dingtalk-test.yml config
docker compose --env-file .env.dingtalk-test \
  -f docker-compose.yml -f docker-compose.dingtalk-test.yml \
  up -d --build --force-recreate
```

覆盖文件把后端切换为 `dingtalk-test`，并挂载独立的 `jiabei_dingtalk_test` 数据卷；现有开发库卷不会被清除。新测试卷只运行生产迁移并引导一个 `superadmin`。前端镜像会关闭角色快捷登录、自动尝试钉钉免登并开启验收诊断。以后更新仍使用同一组 `--env-file/-f` 参数，避免误回到默认 `local` 环境；密钥文件不得提交。

真实群卡片使用 `DINGTALK_GROUP_OPEN_CONVERSATION_ID` 定位群（群名不能代替会话 ID），使用 `DINGTALK_CARD_TEMPLATE_ID` 指定安排模板；当前排班模板 ID 为 671d2cf1-a84f-4a2d-bab4-8450147c45ef.schema。模板使用 data_date、update_time、appointment_list、summary 公共参数，以及 my_visible、my_time、my_makeup_artist、my_team 和 login_button_* 私有参数。机器人需已加入目标群并具有互动卡片权限。`APP_ENTRY_URL` 必须填写测试站点的 HTTPS 预约页地址。实际网关成功后才记录首次送达，重发会更新同一业务卡片。

- 时区固定 `Asia/Shanghai`；身份、资格、次数、冲突和边界时间均由服务端判定。
- 会话使用 HttpOnly Cookie，写请求校验 CSRF 与 Origin；停用、解绑和改密会使旧会话失效。
- 图片仅允许真实 PNG/JPG，最大 2 MB、最大边长 4096，服务端生成随机文件名。
- `.env.example` 只保留空配置项；密钥不得写入代码、文档或提交记录。
- 钉钉内免登的前端构建只接收公开标识和功能开关；`VITE_DINGTALK_CLIENT_ID`、`VITE_DINGTALK_CORP_ID` 由 Docker Compose 作为构建参数传入。`DINGTALK_CLIENT_SECRET` 只能由后端运行环境注入，绝不能使用 `VITE_` 前缀。
- `production` 不暴露本地 Mock 登录/调试接口。当前生产适配器默认禁用，不会误发真实消息。
- 正式发布前仍须提供并验证钉钉测试企业、目标群/卡片模板、魔点 OrgID/AuthKey/设备号、真实脱敏回调样本和公网 HTTPS 回调地址。

实施状态见 [开发进度](docs/开发进度.md)，当前差异与外部阻塞见 [开发问题与假设](docs/开发问题与假设.md)，旧需求编号追溯见 [需求覆盖矩阵](docs/加贝云_需求覆盖矩阵_V1.0.md)。
