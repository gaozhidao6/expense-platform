# Day 17：通知业务接入与前端展示

日期：2026-09-12

## 今日目标

让站内通知形成完整的前后端链路：业务状态变化时创建通知，登录用户可以分页查看、筛选、统计未读数量，并将单条或全部通知标记为已读。

本阶段继续直接使用 MySQL，不使用 Docker，不接入 RabbitMQ、MinIO 或 WebSocket。

## 今日完成内容

### 1. 通知基础业务

- `NotificationService.create()` 校验参数并创建默认未读通知。
- 使用 `(message_key, recipient_id)` 唯一索引防止同一用户收到重复通知。
- `queryMyNotifications()` 只查询当前登录用户的数据，并支持 `type`、`isRead` 和分页条件。
- `countMyUnread()` 统计当前用户的未读数量。
- `markAsRead()` 校验通知归属；已经读过的通知直接返回，不覆盖首次 `readAt`。
- `markAllAsRead()` 一次性更新当前用户的全部未读通知。

### 2. 通知接入报销流程

已经在以下业务节点创建通知：

1. 报销单提交后，通知对应部门经理。
2. 经理审批通过后，通知申请人进入财务审核。
3. 经理驳回后，通知申请人修改并重新提交。
4. 财务审核通过后，通知申请人等待付款。
5. 财务驳回后，通知申请人修改并重新提交。
6. 财务付款后，通知申请人流程完成。

通知和主业务处于同一个 MySQL 事务中。主业务失败时，通知写入也会一起回滚。

每条业务通知使用稳定的 `messageKey`，并加入 `reportId` 和 `approvalRound`：

```text
SUBMIT_REPORT:{reportId}:{approvalRound}
MANAGER_APPROVED:{reportId}:{approvalRound}
MANAGER_REJECTED:{reportId}:{approvalRound}
FINANCE_APPROVED:{reportId}:{approvalRound}
FINANCE_REJECTED:{reportId}:{approvalRound}
REPORT_PAID:{reportId}:{approvalRound}
```

这样既能拦截同一轮的重复消息，也不会把驳回重提后的新一轮通知误判为重复。

### 3. 前端通知中心

完成了：

- `/notifications` 通知页面和路由。
- 分页、类型筛选和已读状态筛选。
- 单条标为已读和全部标为已读。
- 导航栏未读通知角标。
- 点击通知跳转到对应报销单。
- 请求加载状态、失败提示和基础移动端布局。

前端不传 `recipientId`。后端通过登录态取得当前用户 ID，避免用户伪造 ID 查询或更新其他人的通知。

### 4. 通知类型枚举化

新增 `NotificationType`：

```java
APPROVAL_TODO
APPROVED
REJECTED
PAID
```

`NotificationCreateCommand.type` 使用枚举。创建通知时通过 `.name()` 保存数据库；查询筛选时通过 `NotificationType.from()` 校验前端参数。这可以在编译阶段发现类型拼写错误。

### 5. 联调测试数据

已经向本机数据库添加 7 条带 `[联调测试]` 前缀的通知：

- `employee`：2 条未读、1 条已读。
- `manager`：1 条未读、1 条已读。
- `finance`：2 条未读。

测试数据的 `message_key` 均以 `TEST-FRONTEND-` 开头，可使用以下 SQL 清理：

```sql
DELETE FROM notification
WHERE message_key LIKE 'TEST-FRONTEND-%';
```

今天按计划不执行人工联调，数据保留给后续验证。

## 今天理解的代码原理

### `.eq()` 和 `.set()` 的区别

- `.eq()` 生成 `WHERE` 条件，决定哪些数据允许被修改。
- `.set()` 生成 `SET` 内容，决定字段要变成什么值。

全部标为已读对应：

```sql
UPDATE notification
SET is_read = 1,
    read_at = 当前时间
WHERE recipient_id = 当前用户ID
  AND is_read = 0;
```

`recipient_id = 当前用户ID` 是数据权限边界，`is_read = 0` 可以防止覆盖已有的首次阅读时间。

### 为什么用户 ID 不能由前端决定

前端参数可以被用户修改。后端必须通过 `CurrentUserService` 从可信登录态取得用户身份，不能允许前端指定通知接收人。

### 为什么通知也需要幂等

提交或审批请求可能因为重复点击、网络重试而执行多次。稳定的 `messageKey` 配合数据库唯一索引，可以保证同一业务事件不会给同一接收人创建多条重复通知。

## 今日验证结果

- 后端 Maven 编译通过。
- 前端 TypeScript 检查和 Vite 生产构建通过。
- Vite 仅提示构建包大于 500 kB，不是功能或编译错误。
- 人工登录联调暂缓，后续可直接使用已准备的测试数据验证。

## 明日计划

下一学习日开始“附件上传基础模块”，先使用本机目录存储文件，不安装 MinIO，也不依赖 Docker：

1. 理解文件上传请求和 `MultipartFile`。
2. 设计附件元数据表与上传响应 VO。
3. 编写本地文件存储接口，限制文件大小、扩展名和保存路径。
4. 将附件与报销单关联，并校验报销单归属和可编辑状态。
5. 最后接入前端上传组件。

明天先写：`backend/src/main/java/com/shixi/expense/attachment/`。先设计实体、DTO、VO 和存储接口骨架，再实现核心文件保存逻辑。
