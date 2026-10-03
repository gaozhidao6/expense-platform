package com.shixi.expense.common.idempotency;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shixi.expense.common.exception.BusinessException;
import com.shixi.expense.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Redis is an optimization; database leases and ownership are authoritative. */
@Service
public class RedisDatabaseIdempotencyService implements IdempotencyService {
    private static final Logger log = LoggerFactory.getLogger(RedisDatabaseIdempotencyService.class);
    private static final long TTL_SECONDS = 600;
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) end return 0", Long.class);
    private final IdempotencyRecordMapper records;
    private final StringRedisTemplate redis;
    private final TransactionTemplate coordination;

    public RedisDatabaseIdempotencyService(IdempotencyRecordMapper records,
            StringRedisTemplate redis, PlatformTransactionManager transactionManager) {
        this.records = records;
        this.redis = redis;
        coordination = new TransactionTemplate(transactionManager);
        coordination.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        coordination.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public String buildKey(IdempotencyRequest request) {
        return "idempotency:" + request.userId() + ":" + request.key().trim();
    }

    @Override
    public IdempotencyAcquireResult acquire(IdempotencyRequest request) {
        validate(request);
        String token = UUID.randomUUID().toString();
        boolean redisAcquired = false;
        boolean redisAvailable = true;
        try {
            redisAcquired = Boolean.TRUE.equals(redis.opsForValue()
                    .setIfAbsent(buildKey(request), token, Duration.ofSeconds(TTL_SECONDS)));
        } catch (RuntimeException exception) {
            redisAvailable = false;
            log.warn("Redis unavailable; using database idempotency, businessId={}", request.businessId());
        }
        final boolean canInsert = redisAcquired || !redisAvailable;
        try {
            IdempotencyAcquireResult result = coordination.execute(status -> acquireDatabase(request, token, canInsert));
            if (result.decision() != IdempotencyDecision.ACQUIRED && redisAcquired) release(request, token);
            return result;
        } catch (RuntimeException exception) {
            if (redisAcquired) release(request, token);
            throw exception;
        }
    }

    private IdempotencyAcquireResult acquireDatabase(IdempotencyRequest request, String token, boolean canInsert) {
        IdempotencyRecord existing = find(request);
        if (existing == null && canInsert) {
            IdempotencyRecord record = new IdempotencyRecord();
            record.setIdempotencyKey(request.key().trim());
            record.setUserId(request.userId());
            record.setBusinessType(request.businessType().trim());
            record.setBusinessId(request.businessId());
            record.setStatus("PROCESSING");
            record.setOwnerToken(token);
            record.setVersion(0L);
            record.setExpiresAt(LocalDateTime.now().plusSeconds(TTL_SECONDS));
            try {
                if (records.insert(record) != 1) throw new IllegalStateException("创建幂等记录失败");
                return acquired(token);
            } catch (DuplicateKeyException exception) {
                existing = find(request);
            }
        }
        if (existing == null) return processing();
        requireSameBusiness(existing, request);
        if ("SUCCEEDED".equals(existing.getStatus())) {
            return new IdempotencyAcquireResult(IdempotencyDecision.SUCCEEDED, existing.getResponseSnapshot(), null);
        }
        LocalDateTime now = LocalDateTime.now();
        boolean expired = "PROCESSING".equals(existing.getStatus())
                && existing.getExpiresAt() != null && !existing.getExpiresAt().isAfter(now);
        if (!expired && !"FAILED".equals(existing.getStatus())) return processing();
        LambdaUpdateWrapper<IdempotencyRecord> update = new LambdaUpdateWrapper<IdempotencyRecord>()
                .eq(IdempotencyRecord::getId, existing.getId())
                .eq(IdempotencyRecord::getStatus, existing.getStatus())
                .eq(IdempotencyRecord::getVersion, existing.getVersion())
                .set(IdempotencyRecord::getStatus, "PROCESSING")
                .set(IdempotencyRecord::getOwnerToken, token)
                .set(IdempotencyRecord::getVersion, existing.getVersion() + 1)
                .set(IdempotencyRecord::getExpiresAt, now.plusSeconds(TTL_SECONDS))
                .set(IdempotencyRecord::getResponseSnapshot, null);
        if (expired) update.le(IdempotencyRecord::getExpiresAt, now);
        return records.update(null, update) == 1 ? acquired(token) : processing();
    }

    @Override
    public void lockOwnership(IdempotencyRequest request, String token) {
        IdempotencyRecord record = records.selectOne(new LambdaQueryWrapper<IdempotencyRecord>()
                .eq(IdempotencyRecord::getUserId, request.userId())
                .eq(IdempotencyRecord::getIdempotencyKey, request.key().trim()).last("FOR UPDATE"));
        if (record == null || !"PROCESSING".equals(record.getStatus())
                || !Objects.equals(token, record.getOwnerToken())
                || record.getExpiresAt() == null || !record.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.CONFLICT, "请求处理权已过期或被接管，请重试");
        }
        requireSameBusiness(record, request);
    }

    @Override
    public void markSucceeded(IdempotencyRequest request, String token, String snapshot) {
        if (records.update(null, owned(request, token)
                .set(IdempotencyRecord::getStatus, "SUCCEEDED")
                .set(IdempotencyRecord::getResponseSnapshot, snapshot)) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "请求处理权已变更");
        }
    }

    @Override
    public void markFailed(IdempotencyRequest request, String token, String summary) {
        try {
            coordination.executeWithoutResult(status -> records.update(null, owned(request, token)
                    .set(IdempotencyRecord::getStatus, "FAILED")
                    // response_snapshot is JSON; failed requests have no successful response to replay.
                    .set(IdempotencyRecord::getResponseSnapshot, null)));
        } finally {
            release(request, token);
        }
    }

    private LambdaUpdateWrapper<IdempotencyRecord> owned(IdempotencyRequest request, String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("ownerToken 不能为空");
        return new LambdaUpdateWrapper<IdempotencyRecord>()
                .eq(IdempotencyRecord::getUserId, request.userId())
                .eq(IdempotencyRecord::getIdempotencyKey, request.key().trim())
                .eq(IdempotencyRecord::getOwnerToken, token).eq(IdempotencyRecord::getStatus, "PROCESSING");
    }

    private IdempotencyRecord find(IdempotencyRequest request) {
        return records.selectOne(new LambdaQueryWrapper<IdempotencyRecord>()
                .eq(IdempotencyRecord::getUserId, request.userId())
                .eq(IdempotencyRecord::getIdempotencyKey, request.key().trim()));
    }

    private void release(IdempotencyRequest request, String token) {
        try {
            redis.execute(RELEASE, Collections.singletonList(buildKey(request)), token);
        } catch (RuntimeException exception) {
            log.debug("Redis lease release unavailable; TTL will expire it");
        }
    }

    private void requireSameBusiness(IdempotencyRecord record, IdempotencyRequest request) {
        if (!Objects.equals(record.getBusinessType(), request.businessType().trim())
                || !Objects.equals(record.getBusinessId(), request.businessId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Idempotency-Key 已用于其他业务请求");
        }
    }

    private void validate(IdempotencyRequest request) {
        if (request == null || request.key() == null || request.key().isBlank() || request.key().length() > 100
                || request.userId() == null || request.userId() <= 0
                || request.businessId() == null || request.businessId() <= 0
                || request.businessType() == null || request.businessType().isBlank()
                || request.businessType().length() > 50) throw new IllegalArgumentException("幂等请求参数不合法");
    }

    private IdempotencyAcquireResult acquired(String token) {
        return new IdempotencyAcquireResult(IdempotencyDecision.ACQUIRED, null, token);
    }
    private IdempotencyAcquireResult processing() {
        return new IdempotencyAcquireResult(IdempotencyDecision.PROCESSING, null, null);
    }
}
