package com.shixi.expense.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shixi.expense.auth.dto.LoginRequestDTO;
import com.shixi.expense.auth.security.JwtTokenProvider;
import com.shixi.expense.auth.vo.LoginResponseVO;
import com.shixi.expense.role.mapper.SysRoleMapper;
import com.shixi.expense.user.entity.SysUser;
import com.shixi.expense.user.mapper.SysUserMapper;
import com.shixi.expense.operation.service.OperationLogWriteService;
import com.shixi.expense.common.security.CurrentUserService;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

/** 负责用户身份校验并生成登录响应。 */
@Service
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final CurrentUserService currentUserService;

    @Autowired(required = false)
    private OperationLogWriteService operationLogWriteService;

    public AuthService(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            CurrentUserService currentUserService) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.currentUserService = currentUserService;
    }

    /**
     * 根据当前请求携带的 JWT 返回用户信息。
     *
     * <p>角色重新从数据库查询，前端不需要保存另一份可能过期的用户快照。</p>
     */
    public LoginResponseVO.UserInfo getCurrentUserInfo() {
        SysUser user = currentUserService.requireCurrentUser();
        List<String> roles = sysRoleMapper.selectRoleCodesByUserId(user.getId());
        return new LoginResponseVO.UserInfo(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                roles == null ? List.of() : roles);
    }

    /** 查询并校验用户，登录成功后签发 JWT。 */
    public LoginResponseVO login(LoginRequestDTO request) {
        // 登录成功和失败都会写入安全审计摘要，不记录用户密码。
        try {
            SysUser user = findUser(request.getUsername());

        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new DisabledException("用户已被禁用");
        }

        boolean matched = passwordEncoder.matches(
                request.getPassword(), user.getPasswordHash());
        if (!matched) {
            throw new BadCredentialsException("用户名或密码错误");
        }

        // 通过用户角色关联表查询角色编码，角色编码会写入 JWT。
        List<String> roles = sysRoleMapper.selectRoleCodesByUserId(user.getId());
        if (roles == null) {
            roles = List.of();
        }
        String token = jwtTokenProvider.generateToken(
                user.getId(), user.getUsername(), roles);

        LoginResponseVO.UserInfo userInfo = new LoginResponseVO.UserInfo(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                roles);

        LoginResponseVO response = new LoginResponseVO(
                token,
                "Bearer",
                jwtTokenProvider.getExpirationSeconds(),
                userInfo);
        recordSuccessSafely(user.getId());
        return response;
        } catch (RuntimeException exception) {
            recordFailureSafely(exception.getMessage());
            throw exception;
        }
    }

    private void recordSuccessSafely(Long userId) {
        if (operationLogWriteService == null) return;
        try { operationLogWriteService.recordSuccess("LOGIN", "AUTH", userId); }
        catch (RuntimeException ignored) { }
    }

    private void recordFailureSafely(String message) {
        if (operationLogWriteService == null) return;
        try { operationLogWriteService.recordFailure("LOGIN", "AUTH", null, message); }
        catch (RuntimeException ignored) { }
    }

    /** 根据用户名查询未逻辑删除的用户。 */
    private SysUser findUser(String username) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username.trim()));

        if (user == null) {
            throw new BadCredentialsException("用户名或密码错误");
        }

        return user;
    }
}
