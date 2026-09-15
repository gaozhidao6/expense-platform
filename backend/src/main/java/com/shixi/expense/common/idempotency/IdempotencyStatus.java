package com.shixi.expense.common.idempotency;

/**
 * 幂等记录的持久化状态。
 *
 * <p>状态只描述一次请求的生命周期，不等同于报销单业务状态：</p>
 * <ul>
 *     <li>PROCESSING：已经抢到处理权，但业务事务尚未结束。</li>
 *     <li>SUCCEEDED：业务成功，responseSnapshot 保存第一次响应摘要。</li>
 *     <li>FAILED：业务失败；是否允许重试由幂等服务决定。</li>
 * </ul>
 */
public enum IdempotencyStatus {
    PROCESSING,
    SUCCEEDED,
    FAILED
}
