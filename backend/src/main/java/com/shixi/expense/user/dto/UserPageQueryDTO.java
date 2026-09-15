package com.shixi.expense.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** 用户分页查询参数。DTO 只接收和校验参数，不访问数据库。 */
@Data
public class UserPageQueryDTO {

    @Min(value = 1, message = "page must be greater than or equal to 1")
    private long page = 1;

    @Min(value = 1, message = "size must be greater than or equal to 1")
    @Max(value = 100, message = "size must be less than or equal to 100")
    private long size = 10;

    /** 可选关键字，匹配用户名、姓名或工号。 */
    private String keyword;
}
