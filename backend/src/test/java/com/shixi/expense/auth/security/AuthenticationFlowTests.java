package com.shixi.expense.auth.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shixi.expense.auth.controller.AuthController;
import com.shixi.expense.auth.dto.LoginRequestDTO;
import com.shixi.expense.auth.service.AuthService;
import com.shixi.expense.auth.vo.LoginResponseVO;
import com.shixi.expense.config.SecurityConfig;
import com.shixi.expense.support.HealthController;
import com.shixi.expense.user.controller.SysUserController;
import com.shixi.expense.user.service.SysUserService;
import com.shixi.expense.user.vo.UserVO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = {
        AuthController.class,
        SysUserController.class,
        HealthController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtTokenProvider.class,
        AuthenticationFlowTests.AdminTestController.class
})
@TestPropertySource(properties = {
        "security.jwt.secret=ZXhwZW5zZS1wbGF0Zm9ybS1qd3QtdGVzdC1zZWNyZXQtMzItYnl0ZXM=",
        "security.jwt.expiration-seconds=7200"
})
class AuthenticationFlowTests {

    private static final String TEST_SECRET =
            "ZXhwZW5zZS1wbGF0Zm9ybS1qd3QtdGVzdC1zZWNyZXQtMzItYnl0ZXM=";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private AuthService authService;

    @MockBean
    private SysUserService sysUserService;

    @Test
    void allowsHealthCheckWithoutToken() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void allowsLoginWithoutToken() throws Exception {
        LoginResponseVO response = new LoginResponseVO(
                "jwt-token",
                "Bearer",
                7200L,
                new LoginResponseVO.UserInfo(
                        1L, "admin", "Administrator", List.of("ADMIN")));
        when(authService.login(any(LoginRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.user.roles[0]").value("ADMIN"));
    }

    @Test
    void rejectsInvalidLoginParameters() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": " ",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any());
    }

    @Test
    void returnsUnauthorizedWhenCredentialsAreWrong() throws Exception {
        when(authService.login(any(LoginRequestDTO.class)))
                .thenThrow(new BadCredentialsException("用户名或密码错误"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsProtectedRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsProtectedRequestWithValidToken() throws Exception {
        when(sysUserService.queryPage(any())).thenReturn(new Page<UserVO>(1, 10));
        String token = jwtTokenProvider.generateToken(
                1L, "admin", List.of("ADMIN"));

        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current").value(1))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void rejectsTamperedToken() throws Exception {
        String token = jwtTokenProvider.generateToken(
                1L, "admin", List.of("ADMIN"));
        int signatureStart = token.lastIndexOf('.') + 1;
        int signatureIndex = signatureStart + 1;
        char original = token.charAt(signatureIndex);
        char replacement = original == 'a' ? 'b' : 'a';
        String tamperedToken = token.substring(0, signatureIndex)
                + replacement
                + token.substring(signatureIndex + 1);

        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        JwtTokenProvider expiredTokenProvider = new JwtTokenProvider(TEST_SECRET, -1);
        String expiredToken = expiredTokenProvider.generateToken(
                1L, "admin", List.of("ADMIN"));

        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsForbiddenWhenRoleIsInsufficient() throws Exception {
        String employeeToken = jwtTokenProvider.generateToken(
                2L, "employee", List.of("EMPLOYEE"));

        mockMvc.perform(get("/test/admin")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    /** 仅用于验证方法级角色不足时是否返回 403，不会进入生产业务路由。 */
    @RestController
    static class AdminTestController {

        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/test/admin")
        String adminOnly() {
            return "ok";
        }
    }
}
