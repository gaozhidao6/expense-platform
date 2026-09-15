package com.shixi.expense.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 系统用户实体，包含登录身份、组织归属和账户状态。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long departmentId;
    private String employeeNo;
    private String username;
    /** BCrypt 密码摘要，任何接口都不得直接返回该字段。 */
    private String passwordHash;
    private String realName;
    private String email;
    private String mobile;
    /** 1 表示启用，0 表示停用；禁用用户不能继续登录。 */
    private Integer status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 逻辑删除标记，避免直接物理删除历史业务关联用户。 */
    @TableLogic
    private Integer deleted;
}
