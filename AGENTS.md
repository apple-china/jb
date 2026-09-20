# JiaBei 仓库协作规则

## 工作环境

- 默认使用 Windows 11、PowerShell 7；搜索文件和代码优先使用 `rg`。
- 后端位于 `src/backend`，前端位于 `src/frontend`。
- 固定使用 Java 21、Maven 3.9.16 和 pnpm 11.19.0；不得随意升级 pnpm 或改用其他包管理器。
- 保留用户已有的未提交修改，只改当前任务明确涉及的文件。
- Git 提交说明使用中文；未经明确要求不要提交、推送、建标签或部署。

## 分支与环境

- `main` 对应开发联调环境，`prod` 对应生产环境；生产只部署属于 `prod` 历史的固定 `v*` 标签。
- 未完成的功能不得合入 `main`；`prod` 仅通过 Pull Request 更新，禁止直接推送、强制推送和删除。
- 开发使用 Spring/Vite `dev`、`.env.dev.defaults`、`.env.dev.secrets` 和 `docker-compose.dev.yml`。
- 生产使用 Spring/Vite `prod`、`.env.prod.defaults`、`.env.prod.secrets` 和 `docker-compose.prod.yml`。
- 第一次生产切换保留现有数据库、用户、Docker Volume、服务器路径和端口；物理重命名单独处理。

## 配置与秘密

- 仓库保持 public；Client ID、Corp ID、App/Agent/模板/群/机构/设备标识及域名等非密钥允许写入 defaults，密码、Client Secret、Token、签名密钥和 SSH 私钥禁止提交。
- 提交的 defaults 只保存非敏感配置；真实密码、Client Secret、签名密钥和 Token 只放入被 Git 忽略的 secrets 文件。
- 运行密钥放在服务器 `.env.*.secrets`，部署凭据放在 GitHub Actions Secrets；服务器 secrets 文件权限必须为 `600`，同步部署不得覆盖。
- Compose 和部署按 defaults → secrets 顺序加载；不得在日志中输出秘密值。
- 钉钉统一使用 `DINGTALK_CLIENT_ID`、`DINGTALK_CORP_ID`、`DINGTALK_AUTO_LOGIN`；Mock 登录使用 `MOCK_LOGIN_ENABLED`。
- `DINGTALK_*` 与 `MOREDIAN_*` 必须按职责清晰分块，不得混放配置。
- Vite 只显式暴露获准的非敏感变量，禁止公开整个 `DINGTALK_*` 前缀或任何 Secret。
- dev/prod 的钉钉、魔点、卡片通知和超级管理员配置保持环境隔离，即使当前值相同。

## 数据库与 Flyway

- 不修改、删除或重命名任何已应用的 Flyway SQL，不得通过 repair、baseline 或清空历史解决冲突。
- 生产数据操作必须先备份、验证恢复、在隔离恢复库演练并获得单独确认。
- 清理测试用户必须同时使用 `app_user.id` 与 `dingtalk_user_id` 做 AND 精确匹配，不得宽泛删除关联业务数据。

## 验证

- 按修改范围执行最小必要验证：后端用 `mvn test`，前端逻辑用 `pnpm test`，TypeScript/Vite 用 `pnpm build`；只有运行容器、Testcontainers 或执行实际 Docker 验证时才先检查 Engine，静态 Compose 解析无需启动 Engine。
- 页面或钉钉交互只运行对应 Playwright spec/project；不要为通过认证测试修改真实登录业务逻辑。
- 未经明确授权，不操作 GitHub 设置、服务器、容器、生产数据库或第三方平台。
