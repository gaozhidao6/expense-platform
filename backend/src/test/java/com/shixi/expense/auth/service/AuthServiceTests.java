package com.shixi.expense.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shixi.expense.auth.dto.LoginRequestDTO;
import com.shixi.expense.auth.security.JwtTokenProvider;
import com.shixi.expense.auth.vo.LoginResponseVO;
import com.shixi.expense.role.mapper.SysRoleMapper;
import com.shixi.expense.user.entity.SysUser;
import com.shixi.expense.user.mapper.SysUserMapper;
import com.shixi.expense.common.security.CurrentUserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private SysRoleMapper sysRoleMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private CurrentUserService currentUserService;

    private AuthService authService;
    private LoginRequestDTO request;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                sysUserMapper,
                sysRoleMapper,
                passwordEncoder,
                jwtTokenProvider,
                currentUserService);

        request = new LoginRequestDTO();
        request.setUsername("admin");
        request.setPassword("123456");
    }

    @Test
    void returnsCurrentUserInfoFromAuthenticatedUserAndDatabaseRoles() {
        SysUser user = enabledUser();
        when(currentUserService.requireCurrentUser()).thenReturn(user);
        when(sysRoleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("MANAGER"));

        LoginResponseVO.UserInfo userInfo = authService.getCurrentUserInfo();

        assertEquals(1L, userInfo.getId());
        assertEquals("admin", userInfo.getUsername());
        assertEquals(List.of("MANAGER"), userInfo.getRoles());
    }

    @Test
    void returnsLoginResponseWhenCredentialsAreCorrect() {
        SysUser user = enabledUser();
        List<String> roles = List.of("ADMIN");

        when(sysUserMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("123456", "bcrypt-hash")).thenReturn(true);
        when(sysRoleMapper.selectRoleCodesByUserId(1L)).thenReturn(roles);
        when(jwtTokenProvider.generateToken(1L, "admin", roles)).thenReturn("jwt-token");
        when(jwtTokenProvider.getExpirationSeconds()).thenReturn(7200L);

        LoginResponseVO response = authService.login(request);

        assertEquals("jwt-token", response.getToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(7200L, response.getExpiresIn());
        assertEquals(1L, response.getUser().getId());
        assertEquals("admin", response.getUser().getUsername());
        assertEquals(List.of("ADMIN"), response.getUser().getRoles());
    }

    @Test
    void rejectsUnknownUser() {
        when(sysUserMapper.selectOne(any())).thenReturn(null);

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void rejectsWrongPassword() {
        SysUser user = enabledUser();
        when(sysUserMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("123456", "bcrypt-hash")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
        verify(jwtTokenProvider, never()).generateToken(any(), any(), any());
    }

    @Test
    void rejectsDisabledUser() {
        SysUser user = enabledUser();
        user.setStatus(0);
        when(sysUserMapper.selectOne(any())).thenReturn(user);

        assertThrows(DisabledException.class, () -> authService.login(request));
        verify(passwordEncoder, never()).matches(any(), any());
    }

    private SysUser enabledUser() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setRealName("Administrator");
        user.setPasswordHash("bcrypt-hash");
        user.setStatus(1);
        return user;
    }
}
