# Day 16：Redis 幂等收尾与事务边界

日期：2026-09-11

## 今日目标

先理解报销提交幂等的完整调用链，再处理当前实现中真正影响正确性的事务问题。
今天不接入 RabbitMQ 和 MinIO，不使用 Docker，也不追求一次写完所有生产级优化。

## 当前已经完成

- 前端提交报销单时生成并发送 `Idempotency-Key`。
- Controller 从请求头接收幂等 key。
- Service 从登录态取得 `userId`，使用 `reportId` 作为 `businessId`。
- Redis 使用 `SET NX EX` 原子抢占处理权。
- Redis 不可用时使用 MySQL 唯一索引兜底。
- MySQL 保存 `PROCESSING`、`SUCCEEDED` 和 `FAILED` 状态。
- 第一次成功响应会序列化为 JSON，重复请求可以复用。
- `ownerToken` 会跟随 `ACQUIRED` 结果返回。
- 业务失败时使用 Lua 脚本比较 token 后释放自己的 Redis 锁。
- 幂等清理异常不会覆盖原始报销业务异常。
- Redis 抢锁成功但 MySQL 插入失败时，会安全释放当前请求的锁。
- 唯一索引冲突已经集中到独立方法处理，只有 `ACQUIRED` 会继续持有锁。
- 幂等服务已经完成缩进和注释整理，并通过后端干净编译。

## 今天先看懂的原理

### 1. 幂等不是判断接口有没有调用过

幂等要判断的是：同一个用户是否正在重复执行同一笔业务。

```text
userId + Idempotency-Key
        ↓ 找到请求记录
businessType + businessId
        ↓ 确认是同一笔业务
status
        ↓ 决定执行、等待、复用结果或重试
```

### 2. Redis 和 MySQL 的职责不同

- Redis 是快速门卫：尽早挡住同时到达的重复请求。
- MySQL 是最终依据：保存状态、响应快照，并用唯一索引处理 Redis 故障。
- 报销单和审批任务是否真正创建成功，仍以 MySQL 业务事务为准。

### 3. ownerToken 为什么必要

Redis 锁可能在旧请求结束前过期，之后被新请求重新获得。因此不能无条件删除 key，
只能在 Redis 中的 value 与当前请求的 `ownerToken` 相同时删除。

```text
GET key 的结果 == ownerToken
        ↓ 是
删除 key

GET key 的结果 != ownerToken
        ↓ 否
不做任何操作
```

比较和删除通过 Lua 脚本一次完成，避免两条 Redis 命令之间出现并发变化。

### 4. 今天的核心：事务回滚

`ExpenseReportService.submit()` 使用了 `@Transactional`。如果业务代码抛出异常，
当前事务中的报销更新、幂等记录更新和普通审计日志都会一起回滚。

```text
写入 PROCESSING
    -> 执行报销提交
    -> 写入 FAILED
    -> 重新抛出异常
    -> 外层事务整体回滚
```

因此，代码虽然调用了 `markFailed()`，`FAILED` 状态也不一定能留在数据库中。
Redis 锁释放不会被 MySQL 事务回滚，所以用户仍可重试，但失败记录可能消失。

## 今天的代码任务

### 第一项：画清当前调用链

重点阅读：

- `ExpenseReportController.submit()`
- `ExpenseReportService.submit()`
- `RedisDatabaseIdempotencyService.acquire()`
- `RedisDatabaseIdempotencyService.markSucceeded()`
- `RedisDatabaseIdempotencyService.markFailed()`

能够用自己的话回答：谁生成 key、谁取得 userId、谁判断是否执行、谁判断业务成功。

### 第二项：确定失败语义

今天采用适合当前阶段的简单语义：

- 业务成功：提交业务和 `SUCCEEDED` 状态一起提交。
- 业务失败：业务事务回滚，安全释放 Redis 锁，允许下一次请求重新执行。
- 暂不强求保留 `FAILED` 数据库记录；如果以后需要失败历史，再拆分独立事务。

这意味着 `markFailed()` 当前最关键的责任是安全释放 Redis 锁，数据库中的 `FAILED`
更新只能视为“事务允许时记录”，不能视为一定持久化。

### 第三项：检查异常是否被覆盖

业务原始异常必须最终抛给全局异常处理器。幂等清理或审计日志失败时，不应覆盖原始异常。

需要检查：

- `markFailed()` 自己抛异常时，会不会覆盖 `submitInternal()` 的异常。
- Redis 释放失败时，是否只记录 warning 并等待 TTL 到期。
- `recordSubmitFailure()` 失败时，是否会影响原业务异常。

完成情况：已增加安全包装方法。幂等清理失败只记录 warning，原业务异常会继续抛出。

### 第四项：整理当前幂等代码

- 修正缩进和变量命名，提高可读性。
- 删除已经完成的 TODO。
- 保留数据库冲突锁清理、事务拆分等真实待办。
- 完成后执行 `mvn -DskipTests clean compile`。

完成情况：已完成。MySQL 普通异常、唯一索引冲突处理异常和未获得业务处理权等路径，
都会按 ownerToken 安全释放当前请求持有的 Redis 锁；后端干净编译通过。

## 今天暂时不做

- 不安装或使用 Docker。
- 不接入 RabbitMQ。
- 不接入 MinIO。
- 不设计分布式事务。
- 不把 Redis 当成业务数据的最终来源。

## 今日验收标准

- 能解释 `SET NX EX` 的作用。
- 能解释为什么必须使用 `ownerToken`。
- 能区分 Redis 抢占失败和 Redis 服务故障。
- 能解释为什么外层事务回滚可能让 `FAILED` 记录消失。
- 业务失败后 Redis 锁可以安全释放，原始业务异常不会被清理异常覆盖。
- 后端干净编译通过。

## 今日新增完成：站内通知基础模块

当前数据库已经存在 `notification` 表和 `(message_key, recipient_id)` 唯一索引，
今天在不增加 Docker 和外部服务的情况下完成了通知模块基础代码。

### 为什么先不用 RabbitMQ

当前机器不使用 Docker，也不希望增加较重的本地依赖。先让业务代码直接向 MySQL
写入通知，可以完整学习通知的数据结构、权限和幂等；以后接入 RabbitMQ 时，
只需要把“直接创建通知”替换成“发布事件，由消费者创建通知”。

```text
当前版本：审批业务 -> NotificationService -> MySQL

未来版本：审批业务 -> RabbitMQ -> NotificationConsumer
                              -> NotificationService -> MySQL
```

### 已完成代码

- `Notification`：映射现有通知表。
- `NotificationMapper`：提供 MyBatis-Plus 单表访问。
- `NotificationCreateCommand`：承载后端业务创建通知所需数据。
- `NotificationPageQueryDTO`：支持页码、通知类型和已读状态筛选。
- `NotificationVO`：向前端返回安全的通知字段。
- `NotificationService.create()`：校验参数、创建默认未读通知，并利用唯一索引忽略重复消息。
- `NotificationService.queryMyNotifications()`：只查询当前登录用户的数据，支持筛选、倒序和分页。
- `NotificationService.countMyUnread()`：统计当前登录用户的未读通知。
- `NotificationService.markAsRead()`：校验通知归属并更新已读时间。
- `NotificationController`：提供通知分页、未读数量和标记已读接口。
- `CurrentUserService`：统一从 Spring Security 登录态查询当前启用用户。

### 通知幂等规则

`messageKey` 用来阻止同一业务事件重复产生通知。例如：

```text
SUBMIT_REPORT:{reportId}:{approvalRound}
```

数据库唯一索引同时约束 `message_key + recipient_id`。即使同一事件重复执行，
同一个接收人也只能得到一条通知。

### 构建结果

```text
mvn -q -DskipTests clean compile
BUILD SUCCESS
```

### 今日遗留

- 重复调用 `markAsRead()` 目前会再次更新 `readAt`；下一步应在已经为已读时直接返回。
- 通知模块尚未接入报销提交、审批、驳回和付款业务，因此数据库暂时不会自动产生业务通知。
- Service 中部分已经完成的 TODO 注释需要在接入业务前清理。

## 今日总结

今天完成了 Redis 幂等异常清理和站内通知基础模块。已经理解了请求幂等、ownerToken、
用户数据权限、消息唯一键、Entity 与 VO 分层，以及清理异常不能覆盖业务异常。

下一学习日进入“通知业务接入”，继续使用 MySQL 直接写入，不安装 RabbitMQ。
