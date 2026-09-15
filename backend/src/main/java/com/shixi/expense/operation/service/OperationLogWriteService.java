package com.shixi.expense.operation.service;

import java.time.LocalDateTime;

import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.shixi.expense.common.web.RequestTraceFilter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shixi.expense.operation.entity.OperationLog;
import com.shixi.expense.operation.mapper.OperationLogMapper;
import com.shixi.expense.user.entity.SysUser;
import com.shixi.expense.user.mapper.SysUserMapper;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 操作日志写入服务。
 *
 * <p>这里集中准备审计日志的公共字段，避免每个业务方法重复读取当前用户和请求信息。
 * 成功和失败日志统一在本类中补充公共字段并写入数据库。</p>
 */
@Service
public class OperationLogWriteService {

    private final OperationLogMapper operationLogMapper;
    private final SysUserMapper sysUserMapper;

    public OperationLogWriteService(OperationLogMapper operationLogMapper,
            SysUserMapper sysUserMapper) {
        this.operationLogMapper = operationLogMapper;
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 记录成功操作的入口。
     *
     * @param operation 操作编码，例如 MANAGER_APPROVE
     * @param businessType 业务类型，例如 EXPENSE_REPORT
     * @param businessId 业务主键，例如 reportId
     */
    public void recordSuccess(String operation, String businessType,
            Long businessId) {
        OperationLog log = buildBaseLog(operation, businessType, businessId);
        log.setResult("SUCCESS");

        // 先确认业务状态更新成功，再写 SUCCESS；不要在业务更新前写成功日志。
        persist(log);
    }

    /**
     * 记录失败操作的入口；errorMessage 只能保存安全的错误摘要。
     *
     * <p>失败审计必须使用独立事务：外层业务事务随后回滚时，这条 FAILURE
     * 记录仍然要保留，方便排查状态冲突、越权和基础设施错误。</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String operation, String businessType,
            Long businessId, String errorMessage) {
        OperationLog log = buildBaseLog(operation, businessType, businessId);
        log.setResult("FAILURE");
        log.setErrorMessage(safeErrorMessage(errorMessage));
        // 失败日志也要落库；调用方应在捕获原始业务异常后再调用此方法。
        persist(log);
    }

    /** 拼装所有操作日志都会使用的公共请求字段。 */
    private OperationLog buildBaseLog(String operation, String businessType,
            Long businessId) {
        OperationLog log = new OperationLog();
        log.setOperation(operation);
        log.setBusinessType(businessType);
        log.setBusinessId(businessId);
        log.setRequestId(MDC.get(RequestTraceFilter.REQUEST_ID_MDC_KEY));
        log.setCreatedAt(LocalDateTime.now());

        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            log.setHttpMethod(request.getMethod());
            log.setRequestPath(request.getRequestURI());
            log.setIpAddress(resolveClientIp(request));
            Object startedAt = request.getAttribute(
                    RequestTraceFilter.REQUEST_STARTED_AT_NANOS);
            if (startedAt instanceof Long startedAtNanos) {
                log.setDurationMs((System.nanoTime() - startedAtNanos) / 1_000_000);
            } else {
                Object duration = request.getAttribute("requestDurationMs");
                if (duration instanceof Long durationMs) {
                    log.setDurationMs(durationMs);
                }
            }
        }

        // 当前用户必须来自 SecurityContext，不读取前端传入的 operatorId。
        Authentication authentication = SecurityContextHolder.getContext()
                .getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            SysUser user = sysUserMapper.selectOne(
                    new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getUsername, authentication.getName())
                            .eq(SysUser::getStatus, 1));
            if (user != null) {
                log.setOperatorId(user.getId());
            }
        }
        return log;
    }

    /** 防止把堆栈、密码、令牌或超长用户输入写入审计表。 */
    private String safeErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String normalized = message.replaceAll("[\\r\\n\\t]", " ").trim();
        return normalized.length() <= 1000
                ? normalized
                : normalized.substring(0, 1000);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** 将审计记录写入数据库；插入失败必须明确暴露，避免静默丢失审计数据。 */
    private void persist(OperationLog log) {
        int affectedRows = operationLogMapper.insert(log);
        if (affectedRows != 1) {
            throw new IllegalStateException("操作日志写入失败");
        }
    }
}
