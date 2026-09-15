# Day 8：审批、付款与并发控制

日期：2026-09-02

## 今日目标

完成报销单从员工提交后的审批闭环：经理审批、财务审核和付款。使用数据库中的 `version` 字段和 MyBatis-Plus 乐观锁，防止同一张报销单被重复审批或重复付款。

## 今日状态流转

```text
APPROVING       --经理通过--> FINANCE_REVIEW
APPROVING       --经理驳回--> REJECTED
FINANCE_REVIEW  --财务通过--> PENDING_PAYMENT
FINANCE_REVIEW  --财务驳回--> REJECTED
PENDING_PAYMENT --财务付款--> COMPLETED
```

员工提交和撤回已在 Day 7 完成：

```text
DRAFT/REJECTED -> APPROVING
APPROVING      -> WITHDRAWN
```

## 现有数据库对象

### `approval_task`

保存当前审批任务：

```text
report_id
approval_round
node_code
approver_id
status
comment
acted_at
version
```

### `approval_record`

保存不可变的审批历史。每次通过、驳回或付款都只新增记录，不修改历史记录。

### `expense_report.version`

用于乐观锁。更新时必须带上读取时的旧版本号，只有版本未变化时才能更新成功。

## 今日实现任务

### 1. 创建审批任务实体和 Mapper

建议创建：

```text
approval/entity/ApprovalTask.java
approval/mapper/ApprovalTaskMapper.java
```

实体中的 `version` 使用 MyBatis-Plus 的 `@Version`，状态使用字符串或集中枚举定义：

```text
PENDING
APPROVED
REJECTED
CANCELLED
```

### 2. 实现经理审批

建议 Service 方法：

```java
@Transactional
public ExpenseReportVO managerApprove(
        Long reportId, String comment) {
    // 查询当前经理的待审批任务
    // 校验任务属于当前用户
    // 校验报销单状态为 APPROVING
    // 更新任务为 APPROVED
    // 将报销单改为 FINANCE_REVIEW
    // 新增 APPROVE 审批记录
}
```

经理驳回时：

```text
报销单：APPROVING -> REJECTED
任务：PENDING -> REJECTED
记录：action = REJECT
```

### 3. 实现财务审核

财务只能操作状态为 `FINANCE_REVIEW` 的报销单：

```text
通过：FINANCE_REVIEW -> PENDING_PAYMENT
驳回：FINANCE_REVIEW -> REJECTED
```

必须校验当前用户拥有 `FINANCE` 角色，并且任务确实分配给该用户。

### 4. 实现付款

付款只允许当前状态为：

```text
PENDING_PAYMENT
```

付款成功后：

```text
PENDING_PAYMENT -> COMPLETED
```

同时设置：

```text
completedAt
```

再次付款必须被拒绝，不能产生第二条成功付款记录。

### 5. 使用乐观锁防止并发操作

实体：

```java
@Version
private Integer version;
```

配置：

```java
new OptimisticLockerInnerInterceptor()
```

两个请求同时读取 `version = 0`：

```text
请求 A 更新成功：version 0 -> 1
请求 B 更新失败：旧版本 0 已过期
```

当 `updateById()` 返回 `0` 时，应抛出业务异常并回滚审批记录。

## 权限与数据校验

经理审批前检查：

```text
当前用户具有 MANAGER 角色
审批任务 approver_id 是当前用户
任务状态为 PENDING
报销单状态为 APPROVING
```

财务审核前检查：

```text
当前用户具有 FINANCE 角色
审批任务属于当前用户
报销单状态为 FINANCE_REVIEW
```

付款前检查：

```text
当前用户具有 FINANCE 角色
报销单状态为 PENDING_PAYMENT
```

## 审批记录示例

经理通过：

```text
action      = APPROVE
from_status = APPROVING
to_status   = FINANCE_REVIEW
```

财务驳回：

```text
action      = REJECT
from_status = FINANCE_REVIEW
to_status   = REJECTED
```

财务付款：

```text
action      = PAY
from_status = PENDING_PAYMENT
to_status   = COMPLETED
```

## 今日验收标准

- [ ] 经理可以通过分配给自己的审批任务。
- [ ] 经理可以驳回审批任务并保存意见。
- [ ] 非经理不能审批。
- [ ] 非当前任务处理人不能审批该任务。
- [ ] 财务可以审核通过或驳回。
- [ ] 只有 `PENDING_PAYMENT` 状态可以付款。
- [ ] 付款后状态变为 `COMPLETED` 并写入 `completedAt`。
- [ ] 已完成报销单不能再次付款。
- [ ] 非法状态转换被拒绝。
- [ ] 并发审批只有一个请求成功。
- [ ] 状态变更和审批记录在异常时整体回滚。
- [ ] 补充 Service、Controller 和并发场景测试。

## 今日暂不实现

- Redis 提交幂等
- 登录限流
- RabbitMQ 审批通知
- MinIO 发票上传

这些内容放到第 3 周的可靠性和增强任务中。

## 推荐执行顺序

1. 创建 `ApprovalTask` Entity 和 Mapper。
2. 完成审批任务查询和当前操作人校验。
3. 实现经理通过/驳回。
4. 实现财务审核通过/驳回。
5. 实现付款。
6. 检查 `@Version` 和乐观锁拦截器配置。
7. 补充非法状态、越权、重复付款和并发测试。
8. 使用四个角色走通完整流程：员工 -> 经理 -> 财务 -> 完成。

## 今日结果记录

实现状态：待开始。

测试结果：待执行 `mvn test` 后填写。

遇到的问题：

- 

明日衔接：实现我的报销单、待审批列表、详情和审批时间线查询。

## Day 8 实际完成补充

- 已完成 `ApprovalController`：待办查询、经理审批、财务审核和付款接口。
- Service 已加入角色、状态、任务处理人、事务和乐观锁校验。
- 已执行 Maven 编译验证并通过。
- Day 9 将继续进行审批历史查询、接口测试和前后端联调。
