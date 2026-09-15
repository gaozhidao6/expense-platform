package com.shixi.expense.operation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shixi.expense.operation.dto.OperationLogPageQueryDTO;
import com.shixi.expense.operation.entity.OperationLog;
import com.shixi.expense.operation.mapper.OperationLogMapper;
import com.shixi.expense.operation.vo.OperationLogVO;
import com.shixi.expense.user.entity.SysUser;
import com.shixi.expense.user.mapper.SysUserMapper;


import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** 操作日志查询服务。 */
@Service
public class OperationLogService {

    private final SysUserMapper sysUserMapper;
    private final OperationLogMapper operationLogMapper;

    public OperationLogService(
            SysUserMapper sysUserMapper,
            OperationLogMapper operationLogMapper) {
        this.sysUserMapper = sysUserMapper;
        this.operationLogMapper = operationLogMapper;
    }

    /** 查询操作日志分页列表。 */
    public IPage<OperationLogVO> queryPage(OperationLogPageQueryDTO query) {
        // 第一步：从 SecurityContext 中取得当前请求的登录信息。
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("用户未登录");
        }

        // 第二步：判断当前用户是否拥有管理员或审计角色。
        // JWT 过滤器放入 Spring Security 的角色格式为 ROLE_ + 角色编码。
        boolean hasLogPermission = authentication.getAuthorities().stream().anyMatch(authority ->
        "ROLE_ADMIN".equals(authority.getAuthority())|| "ROLE_AUDITOR".equals(authority.getAuthority()));
        if (!hasLogPermission) {
            throw new AccessDeniedException("只有管理员或审计人员可以查询操作日志");
        }

        // 第三步：确认令牌中的用户在数据库中仍然存在且处于启用状态。
        String username = authentication.getName();
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username)
                        .eq(SysUser::getStatus, 1));
        if (user == null) {
            throw new AccessDeniedException("当前用户不存在或已被禁用");
        }

        // 构造分页对象和可选筛选条件。
        Page<OperationLog> page = new Page<>(query.getPage(),query.getSize());
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<OperationLog>();
        if (query.getOperation() != null
                && !query.getOperation().trim().isEmpty()) {
            wrapper.eq(
                    OperationLog::getOperation,
                    query.getOperation().trim());
        }

        if (query.getOperatorId() != null) {
            wrapper.eq(OperationLog::getOperatorId, query.getOperatorId());
        }

        if (query.getBusinessType() != null
                && !query.getBusinessType().trim().isEmpty()) {
            wrapper.eq(
                    OperationLog::getBusinessType,
                    query.getBusinessType().trim());
        }

        if (query.getResult() != null
                && !query.getResult().trim().isEmpty()) {
            wrapper.eq(
                    OperationLog::getResult,
                    query.getResult().trim());
        }

        if (query.getStartTime() != null) {
            wrapper.ge(
                    OperationLog::getCreatedAt,
                    query.getStartTime());
        }

        if (query.getEndTime() != null) {
            wrapper.le(
                    OperationLog::getCreatedAt,
                    query.getEndTime());
        }
        wrapper.orderByDesc(OperationLog::getCreatedAt);
        // 转换 OperationLog 为 OperationLogVO。
        return operationLogMapper.selectPage(page, wrapper).convert(this::toVO);
    }

    /**
     * 将操作日志实体转换为前端返回对象。
     *
     * @param log 数据库中的操作日志实体
     * @return 脱离数据库实体的操作日志 VO
     */
    private OperationLogVO toVO(OperationLog log) {
        if (log == null) {
            return null;
        }

        OperationLogVO vo = new OperationLogVO();

        // 基础标识
        vo.setId(log.getId());

        // 操作人和操作内容
        vo.setOperatorId(log.getOperatorId());
        vo.setOperation(log.getOperation());

        // 业务对象信息
        vo.setBusinessType(log.getBusinessType());
        vo.setBusinessId(log.getBusinessId());
        vo.setRequestId(log.getRequestId());

        // 请求信息
        vo.setHttpMethod(log.getHttpMethod());
        vo.setRequestPath(log.getRequestPath());
        vo.setIpAddress(log.getIpAddress());

        // 执行结果
        vo.setResult(log.getResult());
        vo.setErrorMessage(log.getErrorMessage());
        vo.setDurationMs(log.getDurationMs());

        // 创建时间
        vo.setCreatedAt(log.getCreatedAt());

        return vo;
    }
}
