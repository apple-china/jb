# 生产环境准备与交付

本文描述 `production` 分支的独立部署边界。第一阶段只准备仓库，不执行服务器部署、数据库创建或重置、数据迁移、反向代理修改、证书变更及域名切换。

## 环境边界

| 项目 | 测试环境 | 生产环境 |
|---|---|---|
| 分支 | `main` | `production` |
| 域名 | `https://jb.huixinghub.top` | `https://jbei.huixinghub.top` |
| Compose 项目 | 现有测试项目 | `jiabei-production` |
| 数据库 | `jiabei_dingtalk_test` | `jiabei_production` |
| 数据库用户 | 测试环境用户 | `jiabei_production` |
| 数据卷 | 测试专用卷 | `jiabei-production_postgres-data` |
| 上传卷 | 测试专用卷 | `jiabei-production_uploads-data` |
| Nginx 上游 | `frontend:80` | `jiabei-production-frontend:80` |

生产后端和前端不映射宿主机端口，只通过生产专用内部网络通信。Nginx 加入该网络，并通过唯一别名 `jiabei-production-frontend` 访问前端。生产数据库额外连接独立的 `db-access-net` 管理网络，并仅映射到服务器回环地址 `127.0.0.1:5433`，用于通过 SSH 隧道管理；该端口不得监听公网地址。

生产数据库图形客户端连接参数：

- SSH 服务器：生产服务器的 SSH 地址和密钥。
- 数据库主机：`127.0.0.1`。
- 数据库端口：`5433`。
- 初始数据库：`jiabei_production`。
- 数据库用户：`jiabei_production`。
- 密码：服务器 `.env.production` 中的 `PRODUCTION_DATABASE_PASSWORD`，不得写入仓库或连接导出文件。

钉钉和魔点集成使用独立开关 `DINGTALK_ENABLED`、`MOREDIAN_ENABLED`，默认均为 `false`。只有对应凭据和平台侧配置全部完成后才能单独启用；启用后缺少必要凭据时，后端必须启动失败，不允许静默降级。

生产后端同时连接内部 `backend-net` 和独立的 `outbound-net`：前者用于数据库及前端通信并保持 `internal: true`，后者仅提供访问钉钉、魔点等第三方 HTTPS API 所需的出站能力。数据库不连接 `outbound-net`，后端和前端仍不发布宿主机端口。

## 第一阶段仓库验证

复制示例文件只用于本地解析校验，不要填写真实凭据或提交生成的文件：

```bash
cp .env.production.example .env.production
# 使用临时非真实值填充所有必填项后，仅校验配置：
docker compose --env-file .env.production -f docker-compose.production.yml config --quiet
rm .env.production
```

也可执行生产配置契约测试：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/production-deployment.Tests.ps1
```

本阶段到此结束。禁止执行 `up`、数据库初始化、反向代理变更或回调注册。

## 第二阶段：需明确批准后执行

1. 在服务器创建独立目录 `/opt/stacks/jiabei-production`，并由受限账号保存权限为 `600` 的 `.env.production`。
2. 为数据库密码和首次超管密码生成独立强密码；不得复用测试环境凭据。
3. 先执行 `docker compose ... config --quiet`，核对项目名、端口、网络和卷，再构建生产镜像。
4. 创建空生产库并运行 `classpath:db/migration`；不得加载 `db/local` 或 `db/dingtalk-test`。
5. 在生产 Docker 网络内完成健康检查和业务验收。钉钉及魔点字段保持为空，真实集成继续禁用。
6. 验收后等待负责人确认，才可重置生产数据库、修改反向代理并将 `jbei.huixinghub.top` 切换到新环境。

## 回滚边界

- 应用回滚：恢复上一已验证镜像或提交，并继续使用相同生产卷；禁止通过删除卷回滚。
- 配置回滚：恢复服务器端 `.env.production` 备份后重新校验 Compose 配置。
- 域名切换回滚：恢复原反向代理上游；该操作属于第二阶段。
- 数据库回滚：只能从切换前备份恢复，且必须单独确认；禁止运行 `docker compose down --volumes`。

生产环境默认使用 `production` Spring Profile，关闭 Mock 登录并禁用真实钉钉/魔点适配器。第三方配置字段保留为空，待后续独立集成阶段再启用。
