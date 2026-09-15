package com.shixi.expense.common.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shixi.expense.user.entity.SysUser;
import com.shixi.expense.user.mapper.SysUserMapper;

/**
 * 当前登录用户公共服务。
 *
 * <p>统一从 Spring Security 登录态读取身份。业务 Service 不应相信前端传入的
 * userId，否则用户可能通过修改请求参数访问其他人的数据。</p>
 */
@Service
public class CurrentUserService {

    private final SysUserMapper sysUserMapper;

    public CurrentUserService(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 获取当前登录且处于启用状态的用户。
     *
     * @throws AccessDeniedException 当前请求未登录，或数据库用户不存在、已被禁用
     */
    public SysUser requireCurrentUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AccessDeniedException("用户未登录");
        }

        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(
                                SysUser::getUsername,
                                authentication.getName())
                        .eq(SysUser::getStatus, 1));

        if (user == null) {
            throw new AccessDeniedException(
                    "当前用户不存在或已被禁用");
        }

        return user;
    }

    /** 获取当前登录用户 ID，适用于只需要做数据权限过滤的场景。 */
    public Long requireCurrentUserId() {
        return requireCurrentUser().getId();
    }
}
