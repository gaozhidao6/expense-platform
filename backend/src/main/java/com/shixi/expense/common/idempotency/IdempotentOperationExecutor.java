package com.shixi.expense.common.idempotency;

import java.util.function.Supplier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shixi.expense.common.exception.BusinessException;
import com.shixi.expense.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Separates durable request coordination from the atomic business transaction. */
@Service
public class IdempotentOperationExecutor {
    private final IdempotencyService idempotency;
    private final ObjectMapper json;
    private final TransactionTemplate businessTransaction;

    public IdempotentOperationExecutor(IdempotencyService idempotency, ObjectMapper json,
            PlatformTransactionManager transactionManager) {
        this.idempotency = idempotency;
        this.json = json;
        businessTransaction = new TransactionTemplate(transactionManager);
        businessTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T execute(IdempotencyRequest request, Supplier<T> operation, Class<T> resultType) {
        IdempotencyAcquireResult acquired = idempotency.acquire(request);
        if (acquired.decision() == IdempotencyDecision.SUCCEEDED) {
            try {
                return json.readValue(acquired.responseSnapshot(), resultType);
            } catch (JsonProcessingException | IllegalArgumentException exception) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取幂等响应快照失败");
            }
        }
        if (acquired.decision() != IdempotencyDecision.ACQUIRED) {
            throw new BusinessException(ErrorCode.CONFLICT, "相同请求正在处理中");
        }
        try {
            return businessTransaction.execute(status -> {
                idempotency.lockOwnership(request, acquired.ownerToken());
                T result = operation.get();
                try {
                    idempotency.markSucceeded(request, acquired.ownerToken(), json.writeValueAsString(result));
                } catch (JsonProcessingException exception) {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存幂等响应快照失败");
                }
                return result;
            });
        } catch (RuntimeException exception) {
            // Rollback is complete before the separate coordination transaction records failure.
            try {
                idempotency.markFailed(request, acquired.ownerToken(), "业务处理失败，可重试");
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }
}
