# 报销提交幂等设计

## 业务目标

同一个用户对同一个报销提交请求重复点击、重复发送或因网络超时重试时，系统最终只创建一套审批任务，并且重复请求能够得到第一次处理结果。

幂等键来自请求头 `Idempotency-Key`。用户身份来自登录态。两者共同确定一次请求，不能只使用客户端传来的 key。

## 请求流程

```text
Controller 读取 Idempotency-Key
    -> 从 SecurityContext 取得 userId
    -> IdempotencyService.acquire()
       -> ACQUIRED：进入原有报销提交事务
       -> PROCESSING：返回 409，提示请求仍在处理
       -> SUCCEEDED：反序列化响应快照并直接返回
       -> RETRYABLE_FAILED：允许再次进入业务
    -> 报销提交事务成功
       -> markSucceeded(responseSnapshot)
    -> 报销提交事务失败
       -> markFailed(ownerToken, safeErrorSummary)
```

## Redis 与 MySQL 的职责

- Redis：使用 `SET NX EX` 快速抢占处理权，降低重复请求进入业务事务的概率。
- MySQL：使用已有的 `idempotency_record` 表和唯一键 `(idempotency_key, user_id)` 做最终兜底。
- 报销单、审批任务和审批记录仍然以 MySQL 事务为准，Redis 不能替代业务数据。

## 状态约定

| 状态 | 含义 | 重复请求行为 |
| --- | --- | --- |
| `PROCESSING` | 已经有请求取得处理权 | 返回 409 |
| `SUCCEEDED` | 业务已成功并保存响应摘要 | 返回第一次结果 |
| `FAILED` | 业务失败且可以重试 | 重新抢占或创建记录 |

## 需要补充的测试

- 同一用户、同一 key 连续提交，只创建一套审批任务。
- 同一用户使用不同 key，可以按业务规则分别处理。
- 不同用户使用相同 key，互不影响。
- 业务事务失败后，下一次请求可以重试。
- 20 个并发请求中只有一个获得处理权。
- Redis 不可用时，数据库唯一键仍然能阻止重复提交。

当前实现已经接入 Redis 原子抢占、MySQL 唯一索引兜底、响应快照复用和
ownerToken 校验释放。失败状态与业务事务的独立持久化仍需继续完善。
