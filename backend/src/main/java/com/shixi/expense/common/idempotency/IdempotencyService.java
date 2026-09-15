package com.shixi.expense.common.idempotency;

/**
 * 幂等协调服务接口。
 *
 * <p>实现遵守以下处理顺序：</p>
 * <ol>
 *     <li>校验 key、用户和业务类型，拒绝空 key 和超长 key。</li>
 *     <li>先用 Redis SET NX EX 尝试抢占处理权。</li>
 *     <li>Redis 未抢到时，读取已有状态并返回 PROCESSING/SUCCEEDED/RETRYABLE_FAILED。</li>
 *     <li>Redis 不可用时，使用 idempotency_record 的唯一索引作为数据库兜底。</li>
 *     <li>业务事务成功后保存响应快照；异常时释放处理标记或标记 FAILED。</li>
 * </ol>
 */
public interface IdempotencyService {

    /**
     * 尝试获得一次请求的处理权。
     *
     * <p>该方法必须具备原子性。不能拆成“先 GET 再 SET”，否则并发请求可能同时通过。</p>
     */
    IdempotencyAcquireResult acquire(IdempotencyRequest request);

    /**
     * 业务事务成功后保存最终结果。
     *
     * @param request          原始幂等请求上下文
     * @param responseSnapshot 可重放给重复请求的最小响应快照
     */
    void markSucceeded(IdempotencyRequest request, String responseSnapshot);

    /**
     * 业务异常时释放或标记失败。
     *
     * <p>不能无条件删除别的请求已经接管的 key，释放时必须校验 ownerToken。</p>
     */
    void markFailed(IdempotencyRequest request, String ownerToken,String safeErrorSummary);
}
