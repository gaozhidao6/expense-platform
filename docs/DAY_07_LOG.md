# Day 7：报销单状态机与提交审批

日期：2026-09-01

## 今日目标

先完成昨天顺延的报销单草稿基本信息修改，再实现提交、撤回和驳回后重新提交。经理审批、财务审核和付款放到 Day 8。

## 今日任务

### 1. 修改草稿基本信息

实现 `PUT /api/reports/{id}`，只允许本人修改 `title` 和 `description`。不能修改申请人、编号、状态、总金额或明细。继续复用 `getOwnedDraft(reportId)` 做归属和状态校验。

### 2. 定义状态机

集中定义以下状态：

```text
DRAFT      草稿
APPROVING  审批中
REJECTED   已驳回
WITHDRAWN  已撤回
```

今天允许的转换：

```text
DRAFT      -> APPROVING   提交
APPROVING  -> WITHDRAWN   申请人撤回
REJECTED   -> APPROVING   驳回后重提
```

前端不能直接传入任意状态，状态只能由后端根据当前状态和操作决定。

### 3. 实现提交

在 `submit(reportId)` 中：

1. 查询并校验当前用户是申请人。
2. 只允许 `DRAFT` 或 `REJECTED`。
3. 查询有效明细，至少要有一条。
4. 设置状态为 `APPROVING`，写入 `submittedAt`。
5. 驳回后重提时递增 `approvalRound`。
6. 写入一条 `approval_record`，记录原状态和新状态。

### 4. 实现撤回

在 `withdraw(reportId)` 中：

1. 只允许申请人操作。
2. 只允许当前状态为 `APPROVING`。
3. 修改为 `WITHDRAWN`。
4. 写入撤回操作记录。

### 5. 状态转换校验

建议集中写一个校验方法，不要在每个接口里随意复制条件：

```java
private void assertStatus(ExpenseReport report, String expectedStatus) {
    // 判断当前状态是否允许执行本次操作
}
```

## 今日要理解的问题

1. 为什么状态不能由前端直接决定？
2. 为什么提交前必须存在有效费用明细？
3. 为什么状态变化需要保存操作记录？
4. 为什么审批记录应该只新增、不修改？
5. 为什么驳回后重提要增加审批轮次？

## 验收标准

- [x] 本人可以修改自己的草稿标题和说明。
- [x] 修改基本信息不会改变总金额、明细、申请人和状态。
- [x] 有效明细存在时，草稿可以提交为 `APPROVING`。
- [x] 没有有效明细时不能提交。
- [x] 申请人可以撤回 `APPROVING` 报销单。
- [x] `REJECTED` 报销单可以重新提交，并递增审批轮次。
- [x] 非本人不能提交或撤回。
- [x] 非法状态转换会被拒绝。
- [x] 状态转换测试全部通过。

## 今日暂不实现

- 经理审批通过或驳回
- 财务审核和付款
- 并发审批的乐观锁细节
- Redis 幂等和 RabbitMQ 通知

## 今日结果记录

实现状态：Day 7 已完成。已实现草稿基本信息修改、提交、撤回、驳回后重提、审批轮次递增、审批记录写入和状态/数据权限校验。

测试结果：`Tests run: 45, Failures: 0, Errors: 0, BUILD SUCCESS`。

测试覆盖：

```text
DRAFT + 有效明细提交       -> APPROVING
空明细提交                 -> 拒绝
REJECTED 重新提交          -> APPROVING，approvalRound + 1
APPROVING 撤回             -> WITHDRAWN
DRAFT 撤回                 -> 拒绝
非本人操作                 -> 拒绝/403
审批记录字段               -> action、fromStatus、toStatus、operatorId 正确
Controller 提交/撤回路由    -> 认证后可调用，无 Token 为 401
```

遇到的问题：

- 
- 为了让实体中的 `@Version` 正常工作，已注册 `OptimisticLockerInnerInterceptor`；并发审批的专项测试顺延到 Day 8。

明日衔接：实现经理审批、财务审核、付款和并发控制。
