package com.shixi.expense.auth.vo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 登录成功响应。
 *
 * <p>VO 只定义允许返回给前端的字段，不能直接返回包含 passwordHash 的 SysUser Entity。</p>
 */
@Getter
@AllArgsConstructor
public class LoginResponseVO {

    /** JWT 访问令牌。 */
    private String token;

    /**
     * 令牌类型，前端请求时应拼接为：Authorization: Bearer &lt;token&gt;。
     */
    private String tokenType;

    /** Token 剩余有效时间，单位为秒。 */
    private long expiresIn;

    /** 当前登录用户的安全展示信息。 */
    private UserInfo user;

    /**
     * 登录用户信息。
     *
     * <p>这里只保留前端展示和权限判断需要的字段，不包含密码哈希、逻辑删除标记等内部字段。</p>
     */
    @Getter
    @AllArgsConstructor
    public static class UserInfo {

        /** 用户主键 ID。 */
        private Long id;

        /** 登录用户名。 */
        private String username;

        /** 用户真实姓名。 */
        private String realName;

        /** 用户角色编码，例如 EMPLOYEE、MANAGER、FINANCE、ADMIN。 */
        private List<String> roles;
    }
}
