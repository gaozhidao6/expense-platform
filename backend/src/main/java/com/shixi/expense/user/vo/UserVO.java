package com.shixi.expense.user.vo;

import java.time.LocalDateTime;
import lombok.Data;

/** 用户接口返回对象，不包含 passwordHash、deleted 等内部字段。 */
@Data
public class UserVO {

    private Long id;
    private Long departmentId;
    private String employeeNo;
    private String username;
    private String realName;
    private String email;
    private String mobile;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
