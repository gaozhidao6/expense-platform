package com.shixi.expense.common.idempotency;

/**
 * 幂等服务的检查结果。
 *
 * @param decision          幂等决策
 * @param responseSnapshot  已成功请求的响应快照；其他决策通常为空
 * @param ownerToken        当前请求持有的 Redis 锁凭证；未获得处理权时为空
 */
public record IdempotencyAcquireResult(
        IdempotencyDecision decision,
        String responseSnapshot,
        String ownerToken) {
}
