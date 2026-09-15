# 项目基本原理

本文是学习本项目时的“为什么”参考，代码实现以 `backend/src/main/java`、`frontend/src` 和 `db/migration/V1__init_schema.sql` 为准。

## 1. 总体架构与一次请求

前端 Vue 页面通过 Axios 调用 `/api`，Vite 在开发环境将请求代理到 Spring Boot。后端通常按 Controller -> Service -> Mapper -> MySQL 分层：Controller 负责 HTTP 和参数，Service 负责业务规则与事务，Mapper 负责持久化。响应使用 VO，避免把数据库实体和密码、内部状态直接暴露给浏览器。

一次请求大致经过：

```text
浏览器 -> 路由守卫/Axios -> SecurityFilterChain -> Controller
        -> 参数校验 -> Service(权限、状态、事务) -> Mapper -> MySQL
        -> 统一响应 -> 浏览器
```

## 2. 数据模型为什么这样设计

- `expense_report` 是报销单主表，`expense_item` 是明细表，形成一对多关系。
- `department_id` 在报销单中是申请部门快照，避免员工调部门后历史单据归属改变。
- 金额使用 MySQL `DECIMAL(15,2)` 和 Java `BigDecimal`，避免二进制浮点数的舍入误差。
- `approval_task` 表示当前及历史审批任务，`approval_record` 是不可变审计轨迹；任务状态会变，记录只追加。
- `version` 用于乐观锁：更新时附带旧版本号，影响行数为 0 就说明发生并发冲突。
- `deleted` 是逻辑删除标记，保留数据以便审计；查询必须默认过滤已删除记录。

## 3. 认证、授权与 RBAC

认证回答“你是谁”，授权回答“你能做什么”。登录时用 BCrypt 对密码哈希进行校验，成功后签发 JWT。之后每次请求由 Security 过滤器验证 token，并将用户身份放入 `SecurityContext`。

RBAC 用“用户-角色-权限”关系表达访问控制。本项目已有 `sys_user`、`sys_role`、`sys_user_role`，角色至少包括员工、经理、财务、管理员。没有身份或 token 无效是 401；身份有效但角色不足是 403。业务层仍需检查数据归属，角色判断不能替代“只能看自己的报销单”。

JWT 适合无服务端会话的 API，但注销、密钥轮换和主动踢下线需要额外的黑名单或短过期时间策略；不要把密码、银行卡等敏感信息放进 token。

## 4. 事务与审批状态机

提交报销单通常要同时完成：校验草稿、重算总额、改变主表状态、创建审批任务、写审批记录。它们必须在同一个 `@Transactional` 事务中执行，任一步失败都回滚，避免出现“状态已提交但没有任务”的半成品。

状态机把允许的转换显式列出，例如：

```text
DRAFT -> APPROVING -> FINANCE_REVIEW -> PENDING_PAYMENT -> COMPLETED
  |          |              |
  +-> WITHDRAWN   -> REJECTED -> APPROVING(重新提交)
```

Service 在转换前同时检查当前状态、操作者角色、操作者是否为当前审批人。数据库约束和代码校验要配合，不能只依赖前端按钮隐藏。

## 5. 并发、幂等与可靠性

幂等表示同一个业务请求执行一次或多次，最终结果相同。客户端为提交请求生成 `Idempotency-Key`；Redis 的 `SET key value NX EX seconds` 可抢占处理权，数据库 `idempotency_record` 的唯一索引是最终兜底。处理成功可保存响应快照，重复请求直接返回快照；处理失败要允许合理重试。

审批并发依靠 `version` 乐观锁或条件更新：`UPDATE ... SET status=?, version=version+1 WHERE id=? AND version=?`。只有影响一行才算成功，影响 0 行则返回“单据已被处理”。

## 6. RabbitMQ 通知

业务事务成功后发布事件，例如 `ReportApproved`。交换机依据 routing key 把消息路由到通知队列，消费者写入 `notification`。消息系统通常是“至少一次投递”，所以消费者必须用 `message_key` 加唯一索引去重，不能假设只消费一次。确认、重试和死信队列用于处理临时故障；重要场景可进一步学习 Outbox，解决“数据库提交成功但事件发布失败”的间隙。

## 7. 发票文件与 AI 识别

文件本体适合放 MinIO，对象 URL 和识别结果放 MySQL；`file_hash` 可用于检测重复文件。AI 返回结果必须经过 JSON 结构校验、日期/金额范围校验和人工确认，不能直接覆盖正式字段。调用应有超时、有限重试和失败状态；AI 完全不可用时，用户仍能手工填写发票信息，保证主业务可用。

## 8. 前端状态与错误处理

Pinia 适合保存登录用户和 token，Axios 拦截器统一附带 Authorization，并集中处理 401（清理登录态并跳转登录）和 403（提示无权限）。路由守卫只改善体验，真正的权限必须由后端执行。列表查询要保留分页、筛选和加载/空状态；表单提交要防重复点击并展示后端校验信息。

## 9. 测试应覆盖什么

- Service 单元测试：金额重算、状态转换、权限和非法输入。
- Controller/MockMvc：401、403、400、业务错误和成功响应格式。
- 数据库集成测试：事务回滚、唯一索引、逻辑删除、乐观锁冲突。
- 端到端冒烟：四角色完成一次报销全流程。

测试的重点不是追求行覆盖率，而是锁住业务不变量：总额等于明细之和、审批记录不可变、一次请求不产生重复任务、无权限用户永远不能越权。
