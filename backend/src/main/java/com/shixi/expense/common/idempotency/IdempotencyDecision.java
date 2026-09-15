package com.shixi.expense.common.idempotency;

/**
 * 幂等检查返回给业务层的决定。
 *
 * <p>业务层不应该直接根据 Redis 的布尔值编写分支，而应根据这个明确的决定
 * 选择“继续处理、返回已有结果或返回冲突”。</p>
 */
public enum IdempotencyDecision {
    /** 当前请求成功抢到处理权，可以进入报销业务。 */
    ACQUIRED,
    /** 已有相同请求正在处理，通常映射为 HTTP 409。 */
    PROCESSING,
    /** 相同请求已经成功，应该返回第一次保存的响应。 */
    SUCCEEDED,
    /** 上一次失败且允许安全重试。 */
    RETRYABLE_FAILED
}
