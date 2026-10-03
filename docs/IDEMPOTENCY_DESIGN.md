# 报销提交幂等设计

唯一键为 `(idempotency_key, user_id)`，记录同时绑定业务类型和报销单 ID。同一 key 改用于另一张报销单返回 409。成功记录重放第一次响应，不执行第二次业务。

## 事务边界

1. Redis `SET NX` + 600 秒 TTL 快速拦截；不可用时退回数据库。
2. `REQUIRES_NEW + READ_COMMITTED` 协调事务创建 PROCESSING 并提交，不随业务失败回滚。
3. FAILED 或过期 PROCESSING 通过状态、version 和有效期条件的 CAS 更新接管，更新 owner_token。
4. 独立业务事务对幂等行 `SELECT FOR UPDATE`，校验 token、绑定关系和有效期；持有行锁直到业务数据与 SUCCEEDED JSON 快照一起提交。
5. 业务事务回滚完毕后，另一协调事务仅在 token 仍匹配时保存 FAILED，并清空响应快照。FAILED 没有可重放的成功响应，不能把普通文本写进 JSON 字段。
6. Lua 校验 Redis value 等于当前 token 才释放；旧请求无法删除后来者的 Redis 锁或覆盖其状态。

正在执行的业务持有 MySQL 行锁，租约到期也不会让另一个业务事务并行执行。接管 CAS 等待锁释放后仍要满足原状态与 version；已成功的请求不能再次取得处理权。

| 状态 | 重复请求 |
| --- | --- |
| PROCESSING 且未过期 | 409 |
| SUCCEEDED | 重放 JSON 快照 |
| FAILED / 过期 PROCESSING | CAS 竞争；只有获胜者取得新 token |

## 已验证与限制

真实 MySQL 回归覆盖失败状态跨事务保存、同 key 重试、Redis 不可用仍不重复创建任务、20 路过期租约竞争只有一个获胜者、旧 token 不能完成或失败覆盖、不同业务绑定被拒绝。

本实现面向提交报销链路，未把所有 API 通用化。成功快照会长期保留，尚未实现清理策略。Redis 锁是优化，MySQL 是正确性的依据；当前回归注入 Redis 故障，没有测试真实 Redis 网络分区。
