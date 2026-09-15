# Day 01：环境与项目全链路

日期：2026-08-18

## 今日目标

- [x] 理解前端、后端、数据库和基础设施的职责
- [x] 完成当前环境的服务连通性检查
- [x] 启动 Spring Boot 后端并验证健康检查
- [x] 启动 Vue 前端并确认页面可访问
- [x] 画出一次请求从前端到数据库的链路

## 项目技术栈速记

| 部分 | 技术 | 今天的理解 |
|---|---|---|
| 前端 | Vue 3、TypeScript、Vite、Axios、Pinia |  |
| 后端 | Spring Boot 3、Spring MVC、Spring Security |  |
| 持久化 | MySQL、MyBatis-Plus、Flyway |  |
| 缓存 | Redis |  |
| 消息 | RabbitMQ |  |
| 文件 | MinIO |  |

## 阅读记录

今天阅读的文件：

- [ ] `README.md`
- [ ] `backend/pom.xml`
- [ ] `frontend/package.json`
- [ ] `backend/src/main/resources/application.yml`
- [ ] `compose.yaml`
- [ ] `backend/src/main/resources/db/migration/V1__init_schema.sql`
- [ ] `frontend/src/api/http.ts`
- [ ] `frontend/src/router/index.ts`
- [ ] `backend/src/main/java/com/shixi/expense/support/HealthController.java`

## 环境验证

### Docker 服务

执行命令：

```powershell
docker compose up -d mysql redis rabbitmq minio
docker compose ps
```

结果：已启动并确认前端页面显示“全部连接正常”。

```text
在这里粘贴 `docker compose ps` 的结果，或记录无法启动的原因。
```

### 数据库

- 数据库是否创建：
- Flyway 是否成功执行：
- 已确认的核心表：

### 后端

启动命令：

```powershell
cd backend
mvn spring-boot:run
```

健康检查：

```powershell
Invoke-WebRequest http://localhost:8080/api/health
```

结果：

```text
在这里记录 HTTP 状态码和返回内容。
```

Swagger 地址：`http://localhost:8080/swagger-ui.html`

- [x] 可以打开
- [x] 已查看现有接口

### 前端

启动命令：

```powershell
cd frontend
npm install
npm run dev
```

访问地址：`http://localhost:5173`

- [x] 页面可以打开
- [x] 没有阻塞性的编译错误
- [x] 已确认页面显示“全部连接正常”

## 我理解的请求链路

```text
Vue 页面
  -> Axios
  -> Vite 代理
  -> Spring Security
  -> Controller
  -> Service
  -> Mapper
  -> MySQL
```

用自己的话解释：

> 

## 今日问题与解决方式

| 问题 | 原因 | 解决方式 |
|---|---|---|
|  |  |  |

## 今日验收

- [x] 能说出前后端技术栈及各基础设施用途
- [x] 后端成功启动
- [x] `/api/health` 返回成功
- [x] Swagger 页面可访问
- [x] 前端开发服务器可访问
- [x] 能解释一次请求的完整路径

## 今日复盘

### 今天学会的 3 个概念

1. 前端通过 Axios 调用后端 `/api` 接口。
2. Spring Boot 负责提供后端接口，Flyway 负责数据库迁移。
3. Redis、RabbitMQ、MinIO 是后续可靠性、消息和文件能力的基础设施。

### 仍然不理解的概念

- 业务接口如何通过 Spring Security 区分 401 和 403。

### 明天计划

- 阅读用户、部门、角色相关设计
- 创建基础 entity、mapper、service、controller
- 实现第一个分页查询接口
