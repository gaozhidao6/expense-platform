package com.shixi.expense.auth.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shixi.expense.auth.dto.LoginRequestDTO;
import com.shixi.expense.auth.service.AuthService;
import com.shixi.expense.auth.vo.LoginResponseVO;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;

/** 登录接口，只负责校验请求参数并委托认证服务。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService){
        this.authService = authService;
    }

    /** 使用用户名和密码登录，成功后返回 JWT 及当前用户信息。 */
    @PostMapping("/login")
    public LoginResponseVO login(@Valid @RequestBody LoginRequestDTO request) {
        return authService.login(request);
    }

    /** 当前用户信息以 JWT 和数据库为准，避免前端用户快照与 Token 不一致。 */
    @GetMapping("/me")
    public LoginResponseVO.UserInfo me() {
        return authService.getCurrentUserInfo();
    }
    
}
