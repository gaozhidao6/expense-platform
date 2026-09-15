package com.shixi.expense.user.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shixi.expense.user.dto.UserPageQueryDTO;
import com.shixi.expense.user.service.SysUserService;
import com.shixi.expense.user.vo.UserVO;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户查询接口。
 *
 * <p>Controller 只负责 HTTP 参数绑定、参数校验和调用 Service，
 * 不直接编写 SQL 或组合数据库查询条件。</p>
 */
@RestController
@RequestMapping("/api/users")
public class SysUserController {

    private final SysUserService sysUserService;

    public SysUserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    /** 仅管理员可以分页查询用户，角色限制由方法级授权执行。 */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public IPage<UserVO> queryPage(@Valid UserPageQueryDTO query){
        return sysUserService.queryPage(query);
    }

}
