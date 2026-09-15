package com.shixi.expense.common.idempotency;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

/**
 * 报销提交的幂等控制服务。
 *
 * <p>Redis 用于快速抢占处理权，MySQL 用于保存最终状态和响应快照，
 * 数据库唯一索引 {@code (idempotency_key, user_id)} 负责并发兜底。</p>
 *
 * <p>完整流程：校验请求 -> Redis 抢占 -> MySQL 创建 PROCESSING 记录
 * -> 执行业务 -> 根据业务结果调用 markSucceeded 或 markFailed。</p>
 */
@Service
public class RedisDatabaseIdempotencyService implements IdempotencyService {

    private static final Logger log =
            LoggerFactory.getLogger(RedisDatabaseIdempotencyService.class);

    private static final long DEFAULT_TTL_SECONDS = 10 * 60;

    /**
     * 只有 Redis 中保存的 token 与当前请求的 ownerToken 相同，才删除锁。
     * 比较和删除由 Redis 一次完成，避免误删其他请求后来获得的锁。
     */
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    if redis.call('get', KEYS[1]) == ARGV[1] then
                        return redis.call('del', KEYS[1])
                    end
                    return 0
                    """,
                    Long.class);

    private final IdempotencyRecordMapper recordMapper;
    private final StringRedisTemplate redis;

    public RedisDatabaseIdempotencyService(
            IdempotencyRecordMapper recordMapper,
            StringRedisTemplate redis) {
        this.recordMapper = recordMapper;
        this.redis = redis;
    }

    /** 构造 Redis key；userId 用于隔离用户，key 必须已经通过参数校验。 */
    public String buildKey(IdempotencyRequest request) {
        return "idempotency:"
                + request.userId()
                + ":"
                + request.key().trim();
    }

    /**
     * 使用 Redis SET NX EX 原子抢占处理权。
     *
     * @return true 表示当前请求获得处理权；false 表示相同请求已经占用该 key
     */
    private boolean tryAcquireRedis(String redisKey, String ownerToken) {
        Boolean acquired = redis.opsForValue().setIfAbsent(
                redisKey,
                ownerToken,
                Duration.ofSeconds(DEFAULT_TTL_SECONDS));
        return Boolean.TRUE.equals(acquired);
    }

    /** 比较 ownerToken 后，安全释放当前请求持有的 Redis 锁。 */
    private void releaseRedisLock(
            IdempotencyRequest request,
            String ownerToken) {
        if (ownerToken == null || ownerToken.isBlank()) {
            return;
        }

        String redisKey = buildKey(request);
        try {
            redis.execute(
                    RELEASE_LOCK_SCRIPT,
                    Collections.singletonList(redisKey),
                    ownerToken);
        } catch (RuntimeException exception) {
            // Redis 清理失败不能覆盖业务异常；锁会在 TTL 到期后自动失效。
            log.warn(
                    "释放 Redis 幂等锁失败，等待锁自动过期，userId={}, businessId={}",
                    request.userId(),
                    request.businessId(),
                    exception);
        }
    }

    @Override
    public IdempotencyAcquireResult acquire(IdempotencyRequest request) {
        // userId 由上层从登录态取得，本服务只负责校验业务上下文。
        validateRequest(request);

        String normalizedKey = request.key().trim();
        String redisKey = buildKey(request);
        String ownerToken = UUID.randomUUID().toString();
        boolean redisLockAcquired = false;
        boolean redisAvailable = true;

        // Redis 只负责快速抢占；连接失败时继续使用 MySQL 唯一索引兜底。
        try {
            redisLockAcquired = tryAcquireRedis(redisKey, ownerToken);
        } catch (RuntimeException exception) {
            redisAvailable = false;
            log.warn(
                    "Redis 幂等抢占失败，改用 MySQL 兜底，userId={}, businessId={}",
                    request.userId(),
                    request.businessId(),
                    exception);
        }

        // Redis 正常但未抢到锁时，读取 MySQL 判断已有请求的真实状态。
        if (redisAvailable && !redisLockAcquired) {
            IdempotencyRecord existing = findRecord(
                    request.userId(),
                    normalizedKey);

            if (existing == null) {
                // 另一请求可能刚获得 Redis 锁，但数据库记录尚未提交。
                return new IdempotencyAcquireResult(
                        IdempotencyDecision.PROCESSING,
                        null,
                        null);
            }

            return handleExistingRecord(existing, request, null);
        }

        // Redis 抢占成功或不可用时，尝试在 MySQL 中创建 PROCESSING 记录。
        IdempotencyRecord processingRecord = new IdempotencyRecord();
        processingRecord.setIdempotencyKey(normalizedKey);
        processingRecord.setUserId(request.userId());
        processingRecord.setBusinessType(request.businessType().trim());
        processingRecord.setBusinessId(request.businessId());
        processingRecord.setStatus(IdempotencyStatus.PROCESSING.name());
        processingRecord.setExpiresAt(
                LocalDateTime.now().plusSeconds(DEFAULT_TTL_SECONDS));

        try {
            int rows = recordMapper.insert(processingRecord);
            if (rows != 1) {
                throw new IllegalStateException("创建幂等记录失败");
            }

            return new IdempotencyAcquireResult(
                    IdempotencyDecision.ACQUIRED,
                    null,
                    redisLockAcquired ? ownerToken : null);
        } catch (DuplicateKeyException exception) {
            return handleDuplicateRecord(
                    request,
                    normalizedKey,
                    ownerToken,
                    redisLockAcquired,
                    exception);
        } catch (RuntimeException exception) {
            // MySQL 普通异常意味着本次请求无法继续，释放已经获得的 Redis 锁。
            if (redisLockAcquired) {
                releaseRedisLock(request, ownerToken);
            }
            throw exception;
        }
    }

    /**
     * 处理 MySQL 唯一索引冲突。
     *
     * <p>处理失败或最终未获得业务处理权时，释放当前请求已经获得的 Redis 锁；
     * 只有返回 ACQUIRED 时才把锁继续交给业务代码。</p>
     */
    private IdempotencyAcquireResult handleDuplicateRecord(
            IdempotencyRequest request,
            String normalizedKey,
            String ownerToken,
            boolean redisLockAcquired,
            DuplicateKeyException duplicateException) {
        try {
            IdempotencyRecord concurrentRecord = findRecord(
                    request.userId(),
                    normalizedKey);

            if (concurrentRecord == null) {
                throw new IllegalStateException(
                        "幂等记录竞争后无法读取已有记录",
                        duplicateException);
            }

            IdempotencyAcquireResult result = handleExistingRecord(
                    concurrentRecord,
                    request,
                    redisLockAcquired ? ownerToken : null);

            if (result.decision() != IdempotencyDecision.ACQUIRED
                    && redisLockAcquired) {
                releaseRedisLock(request, ownerToken);
            }

            return result;
        } catch (RuntimeException exception) {
            if (redisLockAcquired) {
                releaseRedisLock(request, ownerToken);
            }
            throw exception;
        }
    }

    /** 根据已有记录的状态决定重复请求的行为。 */
    private IdempotencyAcquireResult handleExistingRecord(
            IdempotencyRecord record,
            IdempotencyRequest request,
            String ownerToken) {
        // 同一个 key 只能对应同一笔业务，不能把旧报销单结果复用给新报销单。
        boolean sameBusinessType = Objects.equals(
                record.getBusinessType(),
                request.businessType().trim());
        boolean sameBusinessId = Objects.equals(
                record.getBusinessId(),
                request.businessId());

        if (!sameBusinessType || !sameBusinessId) {
            throw new IllegalArgumentException(
                    "Idempotency-Key 已被用于其他业务请求");
        }

        String status = record.getStatus();

        if (IdempotencyStatus.PROCESSING.name().equals(status)) {
            // PROCESSING 过期后，通过带旧状态条件的更新尝试重新抢占。
            if (record.getExpiresAt() != null
                    && record.getExpiresAt().isBefore(LocalDateTime.now())) {
                return reclaimRecord(
                        record,
                        request,
                        IdempotencyStatus.PROCESSING.name(),
                        ownerToken);
            }

            return new IdempotencyAcquireResult(
                    IdempotencyDecision.PROCESSING,
                    null,
                    null);
        }

        if (IdempotencyStatus.SUCCEEDED.name().equals(status)) {
            // 已成功请求直接复用第一次保存的响应快照。
            return new IdempotencyAcquireResult(
                    IdempotencyDecision.SUCCEEDED,
                    record.getResponseSnapshot(),
                    null);
        }

        if (IdempotencyStatus.FAILED.name().equals(status)) {
            // 失败记录允许一个请求通过条件更新重新获得处理权。
            return reclaimRecord(
                    record,
                    request,
                    IdempotencyStatus.FAILED.name(),
                    ownerToken);
        }

        throw new IllegalStateException("未知的幂等状态：" + status);
    }

    /** 使用旧状态作为更新条件，避免两个重试请求同时获得处理权。 */
    private IdempotencyAcquireResult reclaimRecord(
            IdempotencyRecord record,
            IdempotencyRequest request,
            String expectedStatus,
            String ownerToken) {
        int rows = recordMapper.update(
                null,
                new LambdaUpdateWrapper<IdempotencyRecord>()
                        .eq(IdempotencyRecord::getId, record.getId())
                        .eq(IdempotencyRecord::getStatus, expectedStatus)
                        .set(
                                IdempotencyRecord::getStatus,
                                IdempotencyStatus.PROCESSING.name())
                        .set(
                                IdempotencyRecord::getBusinessId,
                                request.businessId())
                        .set(
                                IdempotencyRecord::getExpiresAt,
                                LocalDateTime.now()
                                        .plusSeconds(DEFAULT_TTL_SECONDS))
                        .set(
                                IdempotencyRecord::getResponseSnapshot,
                                null));

        if (rows == 1) {
            return new IdempotencyAcquireResult(
                    IdempotencyDecision.ACQUIRED,
                    null,
                    ownerToken);
        }

        // 影响行数为 0，说明另一个并发请求已经先完成状态更新。
        IdempotencyRecord latest = findRecord(
                request.userId(),
                request.key().trim());

        if (latest == null) {
            throw new IllegalStateException("重新抢占幂等记录失败");
        }

        return handleExistingRecord(latest, request, null);
    }

    private IdempotencyRecord findRecord(
            Long userId,
            String idempotencyKey) {
        return recordMapper.selectOne(
                new LambdaQueryWrapper<IdempotencyRecord>()
                        .eq(IdempotencyRecord::getUserId, userId)
                        .eq(
                                IdempotencyRecord::getIdempotencyKey,
                                idempotencyKey));
    }

    private void validateRequest(IdempotencyRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("幂等请求不能为空");
        }

        if (request.key() == null
                || request.key().trim().isEmpty()
                || request.key().length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency-Key 不能为空，长度不能超过 100");
        }

        if (request.userId() == null || request.userId() <= 0) {
            throw new IllegalArgumentException("userId 不合法");
        }

        if (request.businessType() == null
                || request.businessType().trim().isEmpty()
                || request.businessType().length() > 50) {
            throw new IllegalArgumentException("businessType 不合法");
        }

        // 当前服务用于报销提交，因此 businessId 必须是有效报销单 ID。
        if (request.businessId() == null || request.businessId() <= 0) {
            throw new IllegalArgumentException("businessId 不合法");
        }
    }

    @Override
    public void markSucceeded(
            IdempotencyRequest request,
            String responseSnapshot) {
        // 外层业务正常返回后调用本方法；这里不负责判断业务是否成功。
        int rows = recordMapper.update(
                null,
                new LambdaUpdateWrapper<IdempotencyRecord>()
                        .eq(
                                IdempotencyRecord::getUserId,
                                request.userId())
                        .eq(
                                IdempotencyRecord::getIdempotencyKey,
                                request.key().trim())
                        .eq(
                                IdempotencyRecord::getStatus,
                                IdempotencyStatus.PROCESSING.name())
                        .set(
                                IdempotencyRecord::getStatus,
                                IdempotencyStatus.SUCCEEDED.name())
                        .set(
                                IdempotencyRecord::getResponseSnapshot,
                                responseSnapshot));

        if (rows != 1) {
            throw new IllegalStateException(
                    "幂等记录不存在，或当前状态不允许标记成功");
        }

        // Redis 锁保留至 TTL 到期；重复请求根据 MySQL 状态复用响应。
    }

    @Override
    public void markFailed(
            IdempotencyRequest request,
            String ownerToken,
            String safeErrorSummary) {
        try {
            int rows = recordMapper.update(
                    null,
                    new LambdaUpdateWrapper<IdempotencyRecord>()
                            .eq(
                                    IdempotencyRecord::getUserId,
                                    request.userId())
                            .eq(
                                    IdempotencyRecord::getIdempotencyKey,
                                    request.key().trim())
                            .eq(
                                    IdempotencyRecord::getStatus,
                                    IdempotencyStatus.PROCESSING.name())
                            .set(
                                    IdempotencyRecord::getStatus,
                                    IdempotencyStatus.FAILED.name())
                            .set(
                                    IdempotencyRecord::getResponseSnapshot,
                                    safeErrorSummary));

            if (rows != 1) {
                throw new IllegalStateException(
                        "幂等记录不存在，或当前状态不允许标记失败");
            }
        } finally {
            // 数据库更新是否成功，都尝试安全释放当前请求自己的 Redis 锁。
            releaseRedisLock(request, ownerToken);
        }
    }
}
