package com.shixi.expense.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求参数。
 *
 * <p>DTO 只负责接收和校验前端参数，不负责查询用户、校验密码或生成 JWT。</p>
 */
@Data
public class LoginRequestDTO {

    /**
     * 登录用户名，对应 sys_user.username。
     *
     * <p>用户名不能为空，长度限制与数据库 VARCHAR(50) 保持一致。</p>
     */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过 50 个字符")
    private String username;

    /**
     * 登录密码。
     *
     * <p>这里只接收本次请求中的明文密码，仅用于 BCrypt 校验，不能保存到数据库或写入日志。</p>
     */
    @NotBlank(message = "密码不能为空")
    @Size(max = 100, message = "密码长度不能超过 100 个字符")
    private String password;
}
