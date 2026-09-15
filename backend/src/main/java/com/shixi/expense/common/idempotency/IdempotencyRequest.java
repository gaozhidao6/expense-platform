package com.shixi.expense.common.idempotency;

/**
 * 幂等检查所需的业务上下文。
 *
 * @param key          客户端通过 Idempotency-Key 请求头传入的原始 key
 * @param userId       从登录态取得的用户 ID，不能从请求体取得
 * @param businessType 业务类型，例如 SUBMIT_REPORT
 * @param businessId   业务 ID；提交前可以为空，成功后再回填报销单 ID
 */
public record IdempotencyRequest(
        String key,
        Long userId,
        String businessType,
        Long businessId) {
}
