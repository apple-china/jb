# dev/prod 部署与验收

本文是 JiaBei 当前有效的环境部署与第三方验收入口。历史 `dingtalk-test`、`production` Profile、组合式 Compose 和旧服务器目录不再用于发布。

## 环境边界

| 项目 | dev | prod |
|---|---|---|
| 分支 | `main` | `prod` |
| 工作流 | `.github/workflows/deploy-dev.yml` | `.github/workflows/deploy-prod.yml` |
| 触发 | `main` push 自动部署 | 从 `prod` 手动选择其历史中的固定 `v*` 标签 |
| 服务器目录 | `/opt/stacks/jiabei-dev` | `/opt/stacks/jiabei-prod` |
| Compose | `docker-compose.dev.yml` | `docker-compose.prod.yml` |
| Spring/Vite | `dev` | `prod` |
| 配置 | `.env.dev.defaults` → `.env.dev.secrets` | `.env.prod.defaults` → `.env.prod.secrets` |
| 域名 | `https://dev.jb.huixinghub.top` | `https://jb.huixinghub.top` |

defaults 只保存获准公开的非敏感值。密码、Client Secret、Token 和签名密钥只放权限为 `600` 的服务器 secrets 文件；部署连接凭据放 GitHub Environment Secrets。同步发布不得覆盖 secrets、部署备份或业务备份。

生产发布必须先生成 PostgreSQL 自定义格式备份并通过 `pg_restore --list`，再同步代码和更新服务。应用回滚使用上一已验证标签和相同 Volume；禁止通过删除 Volume、清空 Flyway 历史、repair 或 baseline 回滚。数据库恢复属于独立生产操作，必须另行审批。

## 钉钉验收

每个环境独立维护 Client ID、Corp ID、Client Secret、应用/Agent、群和卡片模板配置。前端只接收显式白名单中的公开标识与开关，不得暴露 Client Secret。

验收至少确认：关闭旧 WebView 后自动免登成功；用户身份和权限正确；刷新不循环跳转；受控业务操作发送到对应环境的卡片与通知目标。SDK 超时后应在约 10 秒退出遮罩，显示“重新免登”、固定错误码和脱敏诊断编号。

## 魔点回调与验收

dev 与 prod 分别使用 defaults 中的回调地址和各自 secrets。当前只订阅 `REC_SUCCESS`；生产地址为：

```text
https://jb.huixinghub.top/api/v1/integrations/moredian/recognition-events
```

优先在魔点开放平台“钉钉渠道云端接口应用 → 已授权机构 → 调试”中取得临时 Access Token，再到“回调接口 → 注册回调”填写对应环境 URL 和 `REC_SUCCESS`。不要调用旧企业内部应用的 `/org/getOrgAccessToken`。

需要使用仓库脚本时，在对应部署目录执行；脚本静默读取 Token，不保存或打印它：

```bash
export MOREDIAN_CALLBACK_URL='https://jb.huixinghub.top/api/v1/integrations/moredian/recognition-events'
./scripts/register-moredian-callback.sh
unset MOREDIAN_ACCESS_TOKEN
```

注册是覆盖式操作，新增事件类型时必须一次提交完整标签集合；应用或服务器重启不需要重新注册。验收时使用受控预约和唯一允许设备，确认首次合法事件为 `ACCEPTED`、平台重推为 `DUPLICATE`，且预约签到和卡片刷新属于正确环境。不得把人员标识、原始回调 Body、签名、Token 或密钥写入日志和验收材料。

门禁原始事件按 defaults 中的保留天数和最大条数清理；预约已经保存的签到证据不因原始事件清理而回退。异常回调当前记录脱敏结构化告警，外部告警平台仍需单独建设。
