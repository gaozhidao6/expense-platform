# 智能报销与费用管理平台

这是一个基于 Spring Boot 3 和 Vue 3 的智能报销与费用管理学习项目，已经包含登录、报销、审批、付款、审计和提交幂等链路。

## 目录

- `backend/`：Spring Boot 3 后端
- `frontend/`：Vue 3 + TypeScript 前端
- `database/`：MySQL 数据库创建脚本
- `docs/`：任务边界和开发说明
- `compose.yaml`：保留的可选容器环境配置；当前项目不依赖 Docker 启动

## 本地启动

### JWT 密钥

JWT 密钥不写入源码或 Git。启动后端前，在项目根目录执行以下命令生成并设置当前 PowerShell 会话的密钥：

```powershell
. .\scripts\Set-JwtSecret.ps1
```

点号（`.`）不能省略，否则脚本设置的环境变量不会保留到当前终端。然后启动后端：

```powershell
cd backend
mvn spring-boot:run
```

密钥由 32 个随机字节生成并以 Base64 保存，满足 HMAC JWT 的最小安全长度。不同环境必须使用不同密钥；密钥丢失后，使用旧密钥签发的 Token 将无法继续验证。

### 1. 初始化 MySQL

使用 MySQL 管理员账户执行：

```powershell
mysql -u root -p < database/00_create_database.sql
```

脚本只创建开发数据库和开发用户。数据表由 Flyway 在后端首次启动时自动创建。

### 2. 启动后端

```powershell
cd backend
mvn spring-boot:run
```

默认连接参数：

- MySQL：`localhost:3306/expense_platform`
- 用户：`expense_app`
- 密码：`expense_app_dev`

可以通过 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 环境变量覆盖。提交幂等功能会尝试连接本机 Redis；Redis 不可用时自动使用 MySQL 唯一索引兜底。RabbitMQ 和 MinIO 已作为可选链路接入，默认本地模式仍可在它们不可用时完成主流程。

后端验证地址：

- `http://localhost:8080/api/health`
- `http://localhost:8080/swagger-ui.html`

### 3. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`。Vite 会把 `/api` 请求代理至后端。

## 本地依赖

本项目不要求安装 Docker。MySQL 和 Redis 可以直接使用本机服务，也可以通过环境变量连接已有服务；Redis 不可用时提交接口会降级到 MySQL 幂等兜底。

## 开发进度

登录、报销草稿、明细、提交、撤回、审批、付款、统计、导出、审计日志、站内通知、附件和发票识别建议均已有实现。发票识别结果必须由用户核对确认后才会写入正式费用明细。

## API 与审计日志

- API 快速说明见 [docs/API.md](docs/API.md)
- 核心表字段见 [docs/DATA_DICTIONARY.md](docs/DATA_DICTIONARY.md)
- 管理员或审计角色可从前端“操作日志”页面查询审计记录
- 请求可通过 `X-Request-Id` 关联前后端日志
- 完整演示路径和面试讲解提纲见 [docs/DEMO_GUIDE.md](docs/DEMO_GUIDE.md)

## 测试与增强规划

后端可执行 `mvn test`，前端可执行 `npm test` 进行检查，并使用 `npm run build`
验证生产构建。RabbitMQ、MinIO 和真实 AI 均可按配置启用；默认本地模式分别使用同步通知兜底、本地附件目录和 Mock 识别器。扩展设计见 [docs/ENHANCEMENT_PLAN.md](docs/ENHANCEMENT_PLAN.md)。
